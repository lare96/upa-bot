package me.upa.discord;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Longs;
import me.upa.UpaBotContext;
import me.upa.game.Structure;
import me.upa.service.DatabaseCachingService;
import me.upa.service.SparkTrainMicroService.SparkTrainMember;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu.Builder;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static me.upa.discord.DiscordService.DECIMAL_FORMAT;

/**
 * A {@link ListenerAdapter} that sorts SSH changes from the Upland Data bot.
 */
public final class SparkTrainMessageListener extends ListenerAdapter {

    public SparkTrainMessageListener(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    private static final class PropertyRequest {
        private final long propertyId;
        private final boolean firstBuild;

        private PropertyRequest(long propertyId, boolean firstBuild) {
            this.propertyId = propertyId;
            this.firstBuild = firstBuild;
        }
    }

    /**
     * Users blacklisted from the spark train.
     */
    public static final ImmutableSet<String> BLACKLISTED = ImmutableSet.of("kcbc", "dlnlab");

    /**
     * The amount of expected messages from the Upland Data bot.
     */
    private static final int EXPECTED_MESSAGES = 2;

    /**
     * The context.
     */
    private final UpaBotContext ctx;
    private final Map<Long, PropertyRequest> propertyRequests = new ConcurrentHashMap<>();
    private final AtomicReference<String> listeningFor = new AtomicReference<>();
    private final AtomicInteger currentMessage = new AtomicInteger();
    private final SortedSet<SparkTrainMember> members = new ConcurrentSkipListSet<>();
    private final Queue<Message> messages = new ConcurrentLinkedQueue<>();

    private volatile String lastSparkTrain;

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        switch (event.getButton().getId()) {
            case "confirm_cancel_a_build":
                if (ctx.sparkTrain().getBuildRequests().containsKey(event.getMember().getIdLong())) {
                    event.deferReply(true).queue();
                    SqlConnectionManager.getInstance().execute(new SqlTask<Void>() {
                        @Override
                        public Void execute(Connection connection) throws Exception {
                            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM build_requests WHERE member_id = ?;")) {
                                ps.setLong(1, event.getMember().getIdLong());
                                if (ps.executeUpdate() != 1) {
                                    throw new RuntimeException("Build was not cancelled.");
                                }
                            }
                            return null;
                        }
                    }, success -> {
                        ctx.sparkTrain().getBuildRequests().remove(event.getMember().getIdLong());
                        event.getHook().setEphemeral(true).editOriginal("Your build request has successfully been cancelled.").queue();
                        event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                        ctx.discord().guild().getTextChannelById(963112034726195210L).sendMessage(event.getMember().getAsMention() + " has cancelled their build request!").queue();
                    });
                }
                break;
            case "cancel_a_build":
                event.reply("Are you absolutely sure you would like to cancel your build request?").
                        addActionRow(Button.of(ButtonStyle.DANGER, "confirm_cancel_a_build", "Yes", Emoji.fromUnicode("U+2705"))).setEphemeral(true).queue();
                break;
            case "request_a_build":
                UpaMember requester = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
                if (requester == null) {
                    event.reply("You must be a member to request a build.").setEphemeral(true).queue();
                    return;
                } else if (ctx.sparkTrain().hasActiveBuild(event.getMember().getIdLong())) {
                    event.reply("You have already requested a build. Please cancel first before requesting another.").setEphemeral(true).queue();
                    return;
                }
                Set<UpaProperty> propertiesOwned = ctx.databaseCaching().getMemberProperties().get(requester.getKey());
                if (propertiesOwned.isEmpty()) {
                    event.reply("You must own at least 1 property in Hollis to request a build.").setEphemeral(true).queue();
                } else {
                    if (propertiesOwned.size() > SelectMenu.OPTIONS_MAX_AMOUNT) {
                        event.replyModal(Modal.create("select_property_form", "Property you wish to build on").
                                addActionRow(TextInput.create("select_property_form_link", "Property link", TextInputStyle.SHORT).
                                        setRequired(true).setPlaceholder("https://play.upland.me/?prop_id=77296058553954").build()).build()).queue();
                    } else {
                        ReplyCallbackAction pendingReply = event.reply("Please select which property you'd like to build on.");
                        SelectMenu.Builder propertyListBldr = SelectMenu.create("select_property");
                        StringBuilder sb = new StringBuilder();
                        for (UpaProperty property : propertiesOwned) {
                            if (!property.getBuildStatus().get().equals("Completed") && property.getUp2() >= Structure.MIN_UP2) {
                                propertyListBldr.addOption(property.getAddress() + ", Queens", String.valueOf(property.getPropertyId()), sb.append(property.getUp2()).append(" UP2").append(property.isGenesis() ? " | Genesis" : "").toString());
                            }
                            sb.setLength(0);
                        }
                        pendingReply.addActionRow(propertyListBldr.build()).setEphemeral(true).queue();
                    }
                }
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "spark_train_position":
                long memberId = event.getMember().getIdLong();
                UpaMember upaMember = ctx.databaseCaching().getMembers().get(memberId);
                if (upaMember == null) {
                    event.reply("You must link with UPA using /account before you can do this.").setEphemeral(true).queue();
                    return;
                }
                int place = upaMember.getSparkTrainPlace().get();
                if (place == 0) {
                    event.reply("You must stake spark on one of the structures in <#963108957784772659> to join the spark train. Your SSH will remain even if you unstake your spark.").setEphemeral(true).queue();
                    return;
                }
                event.reply("Passenger #" + place + " with " + DiscordService.DECIMAL_FORMAT.format(upaMember.getTotalSsh()) + " SSH").setEphemeral(true).queue();
                break;
            case "view_build_requests":
                var requests = ctx.sparkTrain().getBuildRequests().values();
                if (requests.isEmpty()) {
                    event.reply("That's odd, there are no active build requests at the moment.").setEphemeral(true).queue();
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (UpaBuildRequest buildRequest : requests) {
                    UpaProperty property = ctx.databaseCaching().getProperties().get(buildRequest.getPropertyId());
                    UpaMember member = ctx.databaseCaching().getMembers().get(buildRequest.getMemberId());
                    if (member == null) {
                        continue;
                    }
                    sb.append(property.getAddress()).append(" | ").append(buildRequest.getStructureName().toLowerCase()).
                            append(" | ").append("<@").append(member.getMemberId()).append(">").append("\n\n");
                }
                event.reply(sb.toString()).setEphemeral(true).queue();
                break;
            case "manage_build_request":
                memberId = event.getMember().getIdLong();
                upaMember = ctx.databaseCaching().getMembers().get(memberId);
                if (upaMember == null) {
                    event.reply("Please become a member by using /account first.").setEphemeral(true).queue();
                    return;
                }
                if (ctx.sparkTrain().hasActiveBuild(memberId)) {
                    event.reply("You already have a build being staked on by the train.").setEphemeral(true).queue();
                    return;
                }
                double totalSsh = upaMember.getTotalSsh();
                int leastRequiredSsh = ctx.sparkTrain().getLeastRequiredSsh();
                UpaBuildRequest buildRequest = ctx.sparkTrain().getBuildRequests().get(event.getMember().getIdLong());
                MessageBuilder mb = new MessageBuilder();
                if (buildRequest != null) {
                    mb.append("You already have an active build request for **").
                            append(buildRequest.getStructureName()).append("** @ **").append(buildRequest.getAddress()).append("**.").setActionRows(ActionRow.of(
                                    Button.of(ButtonStyle.DANGER, "cancel_a_build", "Cancel build request", Emoji.fromUnicode("U+1F6A7"))
                            ));
                } else if (totalSsh >= leastRequiredSsh) {
                    mb.append("You meet the SSH threshold to get a build! You can do so using the \"Request a build\" button below.**").setActionRows(ActionRow.of(
                            Button.of(ButtonStyle.SUCCESS, "request_a_build", "Request a build", Emoji.fromUnicode("U+1F6A7"))
                    ));
                } else {
                    mb.append("You need an additional **").
                            append(String.valueOf(leastRequiredSsh - totalSsh)).
                            append("** SSH in order to get a build.");
                }
                event.reply(mb.build()).setEphemeral(true).queue();
                break;
        }
    }

    @Override
    public void onSelectMenuInteraction(@NotNull SelectMenuInteractionEvent event) {
        switch (event.getSelectMenu().getId()) {
            case "select_property":
                Long propertyId = Longs.tryParse(event.getInteraction().getValues().get(0));
                if (propertyId == null) {
                    propertyId = -1L;
                }
                selectProperty(propertyId, event.getMember().getIdLong(), (msg, menu) -> {
                    event.getInteraction().editSelectMenu(menu.build()).queue();
                    event.getHook().setEphemeral(true).editOriginal(msg).queue();
                }, failedMsg -> event.reply(failedMsg).setEphemeral(true).queue());
                break;
            case "select_structure":
                PropertyRequest request = propertyRequests.get(event.getMember().getIdLong());
                String structureName = event.getInteraction().getValues().get(0);
                Structure structure = ctx.sparkTrain().getStructureData().get(structureName);

                if (request == null) {
                    event.reply("Bot has been restarted. Please try again.").setEphemeral(true).queue();
                    return;
                }
                if (structure == null) {
                    event.reply("Invalid structure selected.").setEphemeral(true).queue();
                    return;
                }
                UpaMember mem = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
                if (mem == null) {
                    event.reply("Invalid member ID.").setEphemeral(true).queue();
                    return;
                }
                double ssh = mem.getTotalSsh();
                int sshNeeded = structure.getSshRequired();
                if (request.firstBuild && structure.getName().equals("Small Town House")) {
                    sshNeeded = structure.getSshRequired() / 2;
                }
                if (ssh < sshNeeded) {
                    event.reply("You need an additional **" + DiscordService.COMMA_FORMAT.format(sshNeeded - ssh) + " SSH** in order to request this kind of build.").setEphemeral(true).queue();
                    return;
                }
                UpaProperty buildRequestProp = ctx.databaseCaching().getProperties().get(request.propertyId);
                if (buildRequestProp == null) {
                    event.reply("Your requested property could not be found in our databases.").setEphemeral(true).queue();
                    return;
                }
                if (Objects.equals(buildRequestProp.getBuildStatus().get(), "Completed")) {
                    event.reply("This property already has a build on it.").setEphemeral(true).queue();
                    return;
                }
                event.deferReply(true).queue();
                SqlConnectionManager.getInstance().execute(new SqlTask<Void>() {
                    @Override
                    public Void execute(Connection connection) throws Exception {
                        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO build_requests(member_id, property_id,structure_name) VALUES (?,?,?);")) {
                            ps.setLong(1, event.getMember().getIdLong());
                            ps.setLong(2, request.propertyId);
                            ps.setString(3, structureName);
                            if (ps.executeUpdate() != 1) {
                                throw new RuntimeException("Build request not inserted into database!");
                            }
                        }
                        return null;
                    }
                }, success -> {
                    propertyRequests.remove(event.getMember().getIdLong());
                    event.getHook().setEphemeral(true).editOriginal("You have successfully secured your build request. It should appear in <#990518630129221652> and <#963112034726195210> shortly.\n\nPlease start your build as soon as possible, or you may be skipped!").queue();
                    event.getInteraction().editSelectMenu(null).queue();
                    ctx.sparkTrain().getBuildRequests().put(event.getMember().getIdLong(), new UpaBuildRequest(mem.getMemberId(), request.propertyId, structureName));
                    ctx.discord().guild().getTextChannelById(963112034726195210L).sendMessage("<@" + mem.getMemberId() + "> has requested structure '" + structureName + "' on **" + buildRequestProp.getAddress() + "**.").queue();
                });
                break;
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case "select_property_form":
                String propertyLink = event.getValue("select_property_form_link").getAsString().trim();
                if (propertyLink.isEmpty() || propertyLink.isBlank() || !propertyLink.startsWith("https://play.upland.me/?prop_id=")) {
                    event.reply("Invalid property link. Please try again.").setEphemeral(true).queue();
                    return;
                }
                Long propertyId = Longs.tryParse(propertyLink.replace("https://play.upland.me/?prop_id=", "").trim());
                if (propertyId == null) {
                    propertyId = -1L;
                }
                selectProperty(propertyId, event.getMember().getIdLong(), (msg, menu) -> {
                    event.reply(msg).addActionRow(menu.build()).setEphemeral(true).queue();
                }, failedMsg -> event.reply(failedMsg).setEphemeral(true).queue());
                break;
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromType(ChannelType.TEXT)) {
            return;
        }
        TextChannel msgChannel = event.getTextChannel();
        User user = event.getMessage().getAuthor();
        if (msgChannel.getIdLong() != 979640542805782568L || user.getIdLong() != 876671849352814673L) {
            return;
        }
        String listeningCommand = listeningFor.get();
        if (listeningCommand == null) {
            return;
        }
        int skipInitial;
        int expectedMessages;
        Consumer<StringTokenizer> tokenizerConsumer;
        Runnable finished;
        if (listeningCommand.equals("!list all:hollis-queens")) {
            skipInitial = 2;
            expectedMessages = 2;
            Map<String, String> statusMap = new ConcurrentHashMap<>();
            tokenizerConsumer = st -> {
                if (st.countTokens() >= 7) {
                    int id = Integer.parseInt(st.nextToken());
                    String nextToken = st.nextToken();
                    StringBuilder sb = new StringBuilder();
                    while (nextToken.chars().noneMatch(Character::isLowerCase)) {
                        sb.append(nextToken).append(' ');
                        nextToken = st.nextToken();
                    }
                    sb.setLength(sb.length() - 1);
                    String address = sb.toString();
                    String city = nextToken;
                    String owner = st.nextToken();
                    String type = st.nextToken();
                    String status = st.nextToken();
                    double stake = Double.parseDouble(st.nextToken());
                    if (BLACKLISTED.contains(owner)) {
                        return;
                    }
                    status = status.equals("completed") ? "Completed" : status.equals("building") ? "In progress" : "Not started";
                    String currentStatus = ctx.databaseCaching().getConstructionStatus().get(address);
                    if (currentStatus == null || !currentStatus.equals(status)) {
                        statusMap.put(address, status);
                    }
                }
            };
            finished = () -> newBuildStatus(statusMap);
        } else if (listeningCommand.equals("!statistic all")) {
            skipInitial = 7;
            expectedMessages = 2;
            tokenizerConsumer = st -> {
                if (st.countTokens() == 7) {
                    String inGameName = st.nextToken();
                    double stake = Double.parseDouble(st.nextToken());
                    int building = Integer.parseInt(st.nextToken());
                    int completed = Integer.parseInt(st.nextToken());
                    st.nextToken();
                    double sh2o = Double.parseDouble(st.nextToken());
                    double shfo = Double.parseDouble(st.nextToken());
                    if (BLACKLISTED.contains(inGameName)) {
                        return;
                    }
                    Long memberId = ctx.databaseCaching().getMemberNames().inverse().get(inGameName);
                    if (memberId == null) {
                        return;
                    }
                    UpaMember upaMember = ctx.databaseCaching().getMembers().get(memberId);
                    upaMember.getSparkTrainSsh().set(sh2o - shfo);
                    SparkTrainMember nextMember = new SparkTrainMember(inGameName, stake, building, completed, sh2o, shfo, upaMember);
                    members.add(nextMember);
                } else {
                    throw new IllegalStateException("Invalid token count.");
                }
            };
            finished = () -> ctx.sparkTrain().editComment(buildMessage(), success -> {
            });
        } else {
            listeningFor.set(null);
            return;
        }
        int newCurrentMessage = currentMessage.incrementAndGet();
        String[] lines = event.getMessage().getContentStripped().split("\\r?\\n");
        for (int index = 0; index < lines.length; index++) {
            if (newCurrentMessage == 1 && index < skipInitial) {
                continue;
            }
            tokenizerConsumer.accept(new StringTokenizer(lines[index]));
        }
        messages.add(event.getMessage());
        if (newCurrentMessage >= expectedMessages) {
            finished.run();
            members.clear();
            listeningFor.set(null);
            currentMessage.set(0);
            for (; ; ) {
                Message m = messages.poll();
                if (m == null) {
                    break;
                }
                m.delete().queue();
            }
        }
    }

    private void newBuildStatus(Map<String, String> statusMap) {
        if (statusMap.isEmpty()) {
            return;
        }
        SqlConnectionManager.getInstance().execute(new SqlTask<Void>() {
            @Override
            public Void execute(Connection connection) throws Exception {
                try (PreparedStatement updateProperty = connection.prepareStatement("UPDATE node_properties SET build_status = ? WHERE address = ?;")) {
                    for (var next : statusMap.entrySet()) {
                        updateProperty.setString(1, next.getValue());
                        updateProperty.setString(2, next.getKey());
                        updateProperty.addBatch();
                    }
                    if (updateProperty.executeBatch().length == statusMap.size()) {
                        DatabaseCachingService databaseCaching = ctx.databaseCaching();
                        for (var nextEntry : statusMap.entrySet()) {
                            databaseCaching.getConstructionStatus().put(nextEntry.getKey(), nextEntry.getValue());
                            databaseCaching.getProperties().values().stream().
                                    filter(next -> next.getAddress().equals(nextEntry.getKey())).
                                    findFirst().ifPresent(property -> property.getBuildStatus().set(nextEntry.getValue()));
                        }
                    }
                }
                return null;
            }
        });
    }

    private Message buildMessage() {
        int place = 1;
        DatabaseCachingService databaseCaching = ctx.databaseCaching();
        StringBuilder sb = new StringBuilder();
        int stopLength = 0;
        for (SparkTrainMember nextMember : members) {
            Long memberId = databaseCaching.getMemberNames().inverse().get(nextMember.getName());
            if (memberId == null) {
                continue;
            }
            double score = nextMember.computeScore();
            nextMember.getUpaMember().getSparkTrainPlace().set(place);
            sb.append(place++).append(". ").
                    append(nextMember.getName()).
                    append(" (").append(DECIMAL_FORMAT.format(score)).
                    append(" SSH)\n\n");
            if (place > 15 && stopLength == 0) {
                stopLength = sb.length();
            }
        }
        if (stopLength == 0) {
            stopLength = sb.length();
        }
        String fullTrain = sb.toString();
        lastSparkTrain = fullTrain;
        return new MessageBuilder().append("Stake your spark at <#963108957784772659>\n").appendCodeBlock(fullTrain.substring(0, stopLength), "").setActionRows(ActionRow.of(
                Button.of(ButtonStyle.PRIMARY, "spark_train_position", "View spark train position", Emoji.fromUnicode("U+1F689"))
        )).build();
    }

    private void selectProperty(long propertyId, long memberId, BiConsumer<String, Builder> finish, Consumer<String> failed) {
        if (propertyId == -1) {
            failed.accept("Invalid property ID. Please try again.");
            return;
        }
        UpaProperty upaProp = ctx.databaseCaching().getProperties().get(propertyId);
        if (upaProp == null) {
            failed.accept("Must be a registered Hollis property that you own. Please try again.");
            return;
        }
        UpaMember upaMember = ctx.databaseCaching().getMembers().get(memberId);
        if (upaMember == null) {
            failed.accept("Please become a UPA member by using /account first.");
            return;
        }
        if (upaMember.getKey() != upaProp.getMemberKey()) {
            failed.accept("You do not own this property.");
            return;
        }
        boolean firstBuild = ctx.databaseCaching().getMemberProperties().get(upaProp.getMemberKey()).stream().
                noneMatch(next -> next.getBuildStatus().get().equals("Completed"));
        SelectMenu.Builder selectStructureBldr = SelectMenu.create("select_structure");
        for (Structure structure : ctx.sparkTrain().getSuitableStructures(upaProp.getUp2())) {
            selectStructureBldr.addOption(structure.getName() + " (" + (firstBuild && structure.getName().equals("Small Town House") ? (structure.getSshRequired() / 2) : structure.getSshRequired()) + " SSH)", structure.getName());
        }

        propertyRequests.put(memberId, new PropertyRequest(propertyId, firstBuild));
        String message = "Please select which structure you'd like to build on **" + upaProp.getAddress() + "**.";
        if (firstBuild) {
            message += " If this is your first build, you only require half the amount of SSH for small townhouses.";
        }
        finish.accept(message, selectStructureBldr);
    }

    public AtomicReference<String> getListeningFor() {
        return listeningFor;
    }

    public String getLastSparkTrain() {
        return lastSparkTrain;
    }
}
