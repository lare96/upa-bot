package me.upa.discord;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Longs;
import me.upa.UpaBotConstants;
import me.upa.UpaBotContext;
import me.upa.discord.UpaBuildRequest.UpaBuildRequestComparator;
import me.upa.discord.listener.credit.CreditTransaction;
import me.upa.discord.listener.credit.CreditTransaction.CreditTransactionType;
import me.upa.fetcher.PropertyDataFetcher;
import me.upa.game.CachedProperty;
import me.upa.game.Node;
import me.upa.game.Property;
import me.upa.game.Structure;
import me.upa.service.DatabaseCachingService;
import me.upa.service.PropertyCachingMicroService;
import me.upa.service.SparkTrainMicroService.SparkTrainMember;
import me.upa.sql.DeleteBuildRequestTask;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu.Builder;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
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
import java.util.function.Function;

import static me.upa.discord.DiscordService.DECIMAL_FORMAT;

/**
 * A {@link ListenerAdapter} that sorts SSH changes from the Upland Data bot.
 */
public final class SparkTrainMessageListener extends ListenerAdapter {

    private static final Logger logger = LogManager.getLogger();
    private static final StringBuilder HOLLIS_GUIDELINES = new StringBuilder().
            append("- You must accrue up to 25% of the spark hours for the build you want (12.5% for first builds, only applies to micro house and small town houses)\n").
            append("- Builds should be placed evenly and correctly, facing the correct way whenever possible\n").
            append("- There is no PAC fee for the Hollis spark train");
    private static final StringBuilder GLOBAL_GUIDELINES = new StringBuilder().
            append("__**For requests in UPA node areas**__\n").
            append("- 0 PAC Fee\n").
            append("- You must accrue up to 25% of the spark hours your structure requires for your build to be accepted\n").
            append("- Builds should be placed evenly and correctly, facing the correct way whenever possible\n").
            append("- See <#975123222506917919> for the full list of UPA node areas\n\n").
            append("__**For all other requests**__\n").
            append("- 500 PAC Fee\n").
            append("- You must accrue up to 50% of the spark hours your structure requires for your build to be accepted");
    private static final int GLOBAL_BUILD_COST = 500;

    public SparkTrainMessageListener(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    private static final class PropertyRequest {
        private final long propertyId;
        private final boolean global;

        private final boolean nodePropertyGlobal;

        private PropertyRequest(long propertyId, boolean global, boolean nodePropertyGlobal) {
            this.propertyId = propertyId;
            this.global = global;
            this.nodePropertyGlobal = nodePropertyGlobal;
        }
    }

    /**
     * Users blacklisted from the spark train.
     */
    public static final ImmutableSet<String> BLACKLISTED = ImmutableSet.of("kcbc", "dlnlab");

    /**
     * The amount of expected messages from the Upland Data bot.
     */
    private static final int EXPECTED_MESSAGES = 3;

    /**
     * The context.
     */
    private final UpaBotContext ctx;

    private final Map<Long, PropertyRequest> propertyRequests = new ConcurrentHashMap<>();
    private final AtomicReference<String> listeningFor = new AtomicReference<>();
    private final AtomicInteger currentMessage = new AtomicInteger();
    private final SortedSet<SparkTrainMember> members = new ConcurrentSkipListSet<>();
    private final Queue<Message> messages = new ConcurrentLinkedQueue<>();

    private volatile String lastHollisSparkTrain;
    private volatile String lastHollisPartialTrain;
    private volatile String lastGlobalSparkTrain;
    private volatile String lastGlobalPartialTrain;

    // Why is this here?? move somewhere else
    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        long memberId = event.getUser().getIdLong();
        UpaMember upaMember = ctx.databaseCaching().getMembers().get(memberId);
        if (upaMember == null) {
            return;
        }
        SqlConnectionManager.getInstance().execute(new SqlTask<Void>() {
            @Override
            public Void execute(Connection connection) throws Exception {
                try (PreparedStatement setInactive = connection.prepareStatement("UPDATE members SET active = 0 WHERE `key` = ?;");
                     PreparedStatement setPropertiesInactive = connection.prepareStatement("UPDATE node_properties SET active = 0 WHERE `member_key` = ?;")) {
                    setInactive.setInt(1, upaMember.getKey());
                    setPropertiesInactive.setInt(1, upaMember.getKey());
                    setInactive.executeUpdate();
                    setPropertiesInactive.executeUpdate();
                    upaMember.getActive().set(false);
                    ctx.databaseCaching().getMemberProperties().get(upaMember.getKey()).forEach(property -> property.getActive().set(false));
                }
                return null;
            }
        });
    }

    private void sendSparkTrain(IReplyCallback event, boolean global) {
        long memberId = event.getMember().getIdLong();
        UpaMember upaMember = ctx.databaseCaching().getMembers().get(memberId);
        if (upaMember == null || !upaMember.getActive().get()) {
            event.reply("You must link with UPA using /account before you can do this.").setEphemeral(true).queue();
            return;
        }
        long channelId = global ? UpaBotConstants.GLOBAL_TRAIN_CHANNEL : UpaBotConstants.HOLLIS_TRAIN_CHANNEL;
        int place = global ? upaMember.getGlobalSparkTrainPlace().get() : upaMember.getHollisSparkTrainPlace().get();
        if (place == 0) {
            event.reply("You must stake spark on one of the structures in <#" + channelId + "> to join the spark train. Your SSH will remain even if you unstake your spark.").setEphemeral(true).queue();
            return;
        }
        StringBuilder sb = new StringBuilder().append("You are passenger #").
                append(place).
                append(" with **").
                append(DiscordService.DECIMAL_FORMAT.format(upaMember.getTotalSsh(global))).
                append(" SSH**.\n").append("```\n").
                append(global ? lastGlobalPartialTrain : lastHollisPartialTrain).
                append("```");
        event.reply(new MessageCreateBuilder().setContent(sb.toString()).
                        setComponents(ActionRow.of(Button.of(ButtonStyle.PRIMARY, global ? "st_full_global" : "st_full_hollis", "View entire train", Emoji.fromUnicode("U+1F686")))).build()).
                setEphemeral(true).queue();
    }
  /*  public void forceCancelABuild(, boolean global) {
        var buildRequests = global ? ctx.sparkTrain().getGlobalBuildRequests() :
                ctx.sparkTrain().getHollisBuildRequests();
        UpaBuildRequest buildRequest = buildRequests.get(memberId);
        if (buildRequest != null) {
            event.deferReply(true).queue();
            UpaMember upaMember = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
            long propertyId = buildRequest.getPropertyId();
            if (global && upaMember != null && upaMember.getActive().get() && !ctx.databaseCaching().getProperties().containsKey(propertyId)) {
                ctx.discord().sendCredit(new CreditTransaction(upaMember, GLOBAL_BUILD_COST, CreditTransactionType.OTHER, "Refund for cancelling global build."));
            }
            event.getInteraction().editButton(event.getButton().asDisabled()).queue();
            SqlConnectionManager.getInstance().execute(new SqlTask<Void>() {
                @Override
                public Void execute(Connection connection) throws Exception {
                    try (PreparedStatement ps = connection.prepareStatement("DELETE FROM build_requests WHERE member_id = ? AND global_train = ?;")) {
                        ps.setLong(1, event.getMember().getIdLong());
                        ps.setBoolean(2, global);
                        if (ps.executeUpdate() != 1) {
                            throw new RuntimeException("Build was not cancelled.");
                        }
                    }
                    return null;
                }
            }, success -> {
                String train = global ? "on the **global** train" : "on the **Hollis** train";
                buildRequests.remove(event.getMember().getIdLong());
                event.getHook().setEphemeral(true).editOriginal("Your build request has successfully been cancelled.").queue();
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                ctx.discord().guild().getTextChannelById(UpaBotConstants.BUILD_REQUESTS_CHANNEL_ID).sendMessage(event.getMember().getAsMention() + " has cancelled their build request " + train + "!").queue();
            });
        }
    }*/
    private void confirmCancelABuild(ButtonInteractionEvent event, boolean global) {
        var buildRequests = global ? ctx.sparkTrain().getGlobalBuildRequests() :
                ctx.sparkTrain().getHollisBuildRequests();
        UpaBuildRequest buildRequest = buildRequests.get(event.getMember().getIdLong());
        if (buildRequest != null) {
            event.deferReply(true).queue();
            UpaMember upaMember = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
            long propertyId = buildRequest.getPropertyId();
            if (global && upaMember != null && upaMember.getActive().get() && !ctx.databaseCaching().getProperties().containsKey(propertyId)) {
                ctx.discord().sendCredit(new CreditTransaction(upaMember, GLOBAL_BUILD_COST, CreditTransactionType.OTHER, "Refund for cancelling global build."));
            }
            event.getInteraction().editButton(event.getButton().asDisabled()).queue();
            SqlConnectionManager.getInstance().execute(new DeleteBuildRequestTask(event.getMember(), global), success -> {
                String train = global ? "on the **global** train" : "on the **Hollis** train";
                buildRequests.remove(event.getMember().getIdLong());
                event.getHook().setEphemeral(true).editOriginal("Your build request has successfully been cancelled.").queue();
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                ctx.discord().guild().getTextChannelById(UpaBotConstants.BUILD_REQUESTS_CHANNEL_ID).sendMessage(event.getMember().getAsMention() + " has cancelled their build request " + train + "!").queue();
            });
        }
    }

    private void cancelABuild(ButtonInteractionEvent event, boolean global) {
        event.reply("Are you absolutely sure you would like to cancel your build request?").
                addActionRow(Button.of(ButtonStyle.DANGER, global ? "confirm_cancel_a_build_global" :
                        "confirm_cancel_a_build", "Yes", Emoji.fromUnicode("U+2705"))).setEphemeral(true).queue();
    }

    private void sendFullSparkTrain(ButtonInteractionEvent event, boolean global) {
        String lastSparkTrain = global ? lastGlobalSparkTrain : lastHollisSparkTrain;
        long memberId = event.getMember().getIdLong();
        UpaMember upaMember = ctx.databaseCaching().getMembers().get(memberId);
        if (upaMember == null) {
            event.reply("You must link with UPA using /account before you can do this.").setEphemeral(true).queue();
            return;
        }
        if (!upaMember.getActive().get()) {
            event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
            return;
        }
        if (lastSparkTrain == null) {
            event.reply("Spark train is currently down for maintenance. Please try again later.").setEphemeral(true).queue();
            return;
        }
        event.replyFiles(FileUpload.fromData(lastSparkTrain.getBytes(), "spark_train.txt")).setEphemeral(true).queue();
    }

    private void viewBuildRequests(ButtonInteractionEvent event, boolean global) {
        var requests = global ? ctx.sparkTrain().getGlobalBuildRequests().values() :
                ctx.sparkTrain().getHollisBuildRequests().values();
        if (requests.isEmpty()) {
            event.reply("That's odd, there are no active build requests at the moment.").setEphemeral(true).queue();
            return;
        }
        List<UpaBuildRequest> sortedRequests = new ArrayList<>(requests);
        sortedRequests.sort(new UpaBuildRequestComparator(ctx, global));

        StringBuilder sb = new StringBuilder();
        int place = 1;
        for (UpaBuildRequest buildRequest : sortedRequests) {
            UpaMember member = ctx.databaseCaching().getMembers().get(buildRequest.getMemberId());
            if (member == null || !member.getActive().get()) {
                continue;
            }
            sb.append(place++).append(". ").append(buildRequest.getAddress()).append(" (").append(buildRequest.getPropertyId()).append(")").append(" | ").append(buildRequest.getStructureName().toLowerCase()).
                    append(" | ").append("<@").append(member.getMemberId()).append(">");
            if (buildRequest.hasBadResponse()) {
                sb.append("\nStatus ~ **").append(buildRequest.getResponse().getFormattedName()).append("**");
            }
            sb.append("\n\n");
        }
        event.reply(sb.toString()).setEphemeral(true).queue();
    }

    private void manageBuildRequests(ButtonInteractionEvent event, boolean global) {
        UpaMember requester = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
        if (requester == null) {
            event.reply("You must be a member to request a build.").setEphemeral(true).queue();
            return;
        }
        if (!requester.getActive().get()) {
            event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
            return;
        }
        var requests = global ? ctx.sparkTrain().getGlobalBuildRequests() :
                ctx.sparkTrain().getHollisBuildRequests();
        if (requests.containsKey(requester.getMemberId())) {
            event.reply("You already have a build request listed. Please cancel it before listing another one.").addActionRow(
                    Button.of(ButtonStyle.DANGER, global ? "cancel_a_build_global" : "cancel_a_build", "Cancel build request", Emoji.fromUnicode("U+26A0"))
            ).setEphemeral(true).queue();
            return;
        }
        Set<UpaProperty> propertiesOwned = ctx.databaseCaching().getMemberProperties().get(requester.getKey());
        if (propertiesOwned.isEmpty() && !global) {
            event.reply("You must own at least 1 node property to request a build.").setEphemeral(true).queue();
        } else {
            if (propertiesOwned.size() > SelectMenu.OPTIONS_MAX_AMOUNT || global) {
                event.replyModal(Modal.create(global ? "select_property_form_global" : "select_property_form", "Property you wish to build on").
                        addActionRow(TextInput.create(global ? "select_property_form_link_global" : "select_property_form_link", "Property link", TextInputStyle.SHORT).
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
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        switch (event.getButton().getId()) {
            case "st_hollis":
                sendSparkTrain(event, false);
                break;
            case "st_global":
                sendSparkTrain(event, true);
                break;
            case "st_hollis_guidelines":
                event.reply(HOLLIS_GUIDELINES.toString()).setEphemeral(true).queue();
                break;
            case "st_global_guidelines":
                event.reply(GLOBAL_GUIDELINES.toString()).setEphemeral(true).queue();
                break;
            case "confirm_cancel_a_build":
                confirmCancelABuild(event, false);
                break;
            case "confirm_cancel_a_build_global":
                confirmCancelABuild(event, true);
                break;
            case "cancel_a_build":
                cancelABuild(event, false);
                break;
            case "cancel_a_build_global":
                cancelABuild(event, true);
                break;
            case "st_full_hollis":
                sendFullSparkTrain(event, false);
                break;
            case "st_full_global":
                sendFullSparkTrain(event, true);
                break;
            case "view_build_requests":
                viewBuildRequests(event, false);
                break;
            case "view_build_requests_global":
                viewBuildRequests(event, true);
                break;
            case "manage_build_request":
                manageBuildRequests(event, false);
                break;
            case "manage_build_request_global":
                manageBuildRequests(event, true);
                break;
        }
    }

    private void selectStructure(SelectMenuInteractionEvent event) {
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
        if (!mem.getActive().get()) {
            event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
            return;
        }
        Function<String, Boolean> asyncAction = address -> {
            try {
                try (Connection connection = SqlConnectionManager.getInstance().take();
                     PreparedStatement ps = connection.prepareStatement("INSERT INTO build_requests(member_id, property_id,structure_name,global_train,address) VALUES (?,?,?,?,?);")) {
                    ps.setLong(1, event.getMember().getIdLong());
                    ps.setLong(2, request.propertyId);
                    ps.setString(3, structureName);
                    ps.setBoolean(4, request.global);
                    ps.setString(5, address);
                    UpaBuildRequest buildRequest = new UpaBuildRequest(mem.getMemberId(), request.propertyId, structureName, address);
                    if (ps.executeUpdate() == 1) {
                        String train = request.global ? "on the **global** train" : "on the **Hollis** train";
                        propertyRequests.remove(event.getMember().getIdLong());
                        event.getHook().setEphemeral(true).editOriginal("You have successfully secured your build request. It should appear in <#963112034726195210> shortly.\n\nPlease start your build as soon as possible, or you may be skipped!").queue();
                        event.getInteraction().editSelectMenu(null).queue();
                        String msg = "<@" + mem.getMemberId() + "> has requested structure '" + structureName + "' on **" + address + "** " + train + ".";
                        if (request.global) {
                            ctx.sparkTrain().getGlobalBuildRequests().put(event.getMember().getIdLong(), buildRequest);
                        } else {
                            ctx.sparkTrain().getHollisBuildRequests().put(event.getMember().getIdLong(), buildRequest);
                        }
                        ctx.discord().guild().getTextChannelById(UpaBotConstants.BUILD_REQUESTS_CHANNEL_ID).sendMessage(msg).queue();
                        return true;
                    } else {
                        return false;
                    }
                }
            } catch (Exception e) {
                logger.catching(e);
                return false;
            }
        };
        Runnable failure = () -> event.getHook().editOriginal("Your property data could not be fetched. Please open a ticket in <#986638348418449479>.").complete();
        String address;
        if (request.global) {
            event.deferReply(true).queue();
            CachedProperty cachedProperty = ctx.databaseCaching().getPropertyLookup().get(request.propertyId);
            if (cachedProperty != null) {
                address = cachedProperty.getAddress();
            } else {
                address = null;
            }
            Consumer<String> action = finalAddress -> {
                ctx.discord().execute(() -> {
                    String newAddress = finalAddress;
                    if (newAddress == null) {
                        try {
                            Property property = PropertyDataFetcher.fetchPropertySynchronous(request.propertyId);
                            newAddress = property.getFullAddress();
                            PropertyCachingMicroService.addCachedProperty(ctx, property);
                        } catch (Exception e) {
                            logger.catching(e);
                            return;
                        }
                    }
                    if (!asyncAction.apply(newAddress)) {
                        failure.run();
                    }
                });
            };
            if (!request.nodePropertyGlobal) {
                int pac = mem.getCredit().get();
                if (pac < GLOBAL_BUILD_COST) {
                    event.reply("Submitting a build request to the global train costs **500 PAC** (Your have **" + pac + " PAC**.").setEphemeral(true).queue();
                    return;
                }
                String finalAddress1 = address;
                ctx.discord().sendCredit(new CreditTransaction(mem, -GLOBAL_BUILD_COST, CreditTransactionType.REDEEM, "requesting a global build") {
                    @Override
                    public void onSuccess() {
                        action.accept(finalAddress1);
                    }
                });
            } else {
                if (address == null) {
                    UpaProperty upaProperty = ctx.databaseCaching().getProperties().get(request.propertyId);
                    if (upaProperty != null) {
                        address = upaProperty.getAddress();
                    }
                }
                action.accept(address);
            }
        } else {
            UpaProperty buildRequestProp = ctx.databaseCaching().getProperties().get(request.propertyId);
            if (buildRequestProp == null) {
                event.reply("Your requested property could not be found in our databases.").setEphemeral(true).queue();
                return;
            }
            if (Objects.equals(buildRequestProp.getBuildStatus().get(), "Completed")) {
                event.reply("This property already has a build on it.").setEphemeral(true).queue();
                return;
            }
            address = buildRequestProp.getAddress();
            event.deferReply(true).queue();
            String finalAddress2 = address;
            ctx.discord().execute(() -> {
                if (!asyncAction.apply(finalAddress2)) {
                    failure.run();
                }
            });
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
                }, failedMsg -> event.reply(failedMsg).setEphemeral(true).queue(), false);
                break;
            case "select_structure":
                selectStructure(event);
                break;
        }
    }


    private void selectPropertyForm(ModalInteractionEvent event, boolean global) {
        String propertyLink = event.getValue(global ? "select_property_form_link_global" : "select_property_form_link").getAsString().trim();
        if (propertyLink.isEmpty() || propertyLink.isBlank() || !propertyLink.startsWith("https://play.upland.me/?prop_id=")) {
            event.reply("Invalid property link. Please try again.").setEphemeral(true).queue();
            return;
        }
        Long propertyId = Longs.tryParse(propertyLink.replace("https://play.upland.me/?prop_id=", "").trim());
        if (propertyId == null) {
            propertyId = -1L;
        }
        event.deferReply(true).queue();
        selectProperty(propertyId, event.getMember().getIdLong(), (msg, menu) -> {
            event.getHook().setEphemeral(true).editOriginal(new MessageEditBuilder().setContent(msg).setComponents(ActionRow.of(menu.build())).build()).queue();
        }, failedMsg -> event.reply(failedMsg).setEphemeral(true).queue(), global);
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case "select_property_form":
                selectPropertyForm(event, false);
                break;
            case "select_property_form_global":
                selectPropertyForm(event, true);
                break;
        }
    }

    private void parseListAll(StringTokenizer st, Map<String, String> statusMap) {
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
            sb.setLength(0);
            System.out.println(nextToken);
            while (nextToken.chars().noneMatch(Character::isLowerCase)) {
                sb.append(nextToken).append(' ');
                nextToken = st.nextToken();
            }
            System.out.println(nextToken);
            String city = sb.toString();
            String owner = nextToken;
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
    }

    private void parseStatistics(StringTokenizer st, String str, boolean global) {
        int tokenCount = st.countTokens();
        if (st.countTokens() == 6) {
            String inGameName = st.nextToken();
            double stake = Double.parseDouble(st.nextToken());
            int building = Integer.parseInt(st.nextToken());
            int completed = Integer.parseInt(st.nextToken());
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
            if (upaMember == null) {
                logger.error("UPA MEMBER NULL BUT MEMBER ID CONTAINED?? " + memberId + " {" + inGameName + "}");
                return;
            }
            if (!upaMember.getActive().get()) {
                return;
            }
            if (global) {
                upaMember.getGlobalSparkTrainShGiven().set(sh2o);
                upaMember.getGlobalSparkTrainSsh().set(sh2o - shfo);
                upaMember.getGlobalSparkTrainStaked().set(stake);
            } else {
                upaMember.getHollisSparkTrainShGiven().set(sh2o);
                upaMember.getHollisSparkTrainSsh().set(sh2o - shfo);
                upaMember.getHollisSparkTrainStaked().set(stake);
            }
            SparkTrainMember nextMember = new SparkTrainMember(inGameName, stake, building, completed, sh2o, shfo, upaMember, global);
            members.add(nextMember);
        } else {
            logger.error("Invalid token count [{}, {}]", tokenCount, str);
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        try {
            if (!event.isFromType(ChannelType.TEXT)) {
                return;
            }
            TextChannel msgChannel = event.getChannel().asTextChannel();
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
            BiConsumer<String, StringTokenizer> tokenizerConsumer;
            Runnable finished;
            Map<String, String> statusMap = new ConcurrentHashMap<>();
            if (listeningCommand.equals("!list all:hollis-queens")) {
                skipInitial = 2;
                expectedMessages = 3;
                tokenizerConsumer = (str, st) -> parseListAll(st, statusMap);
                finished = () -> newBuildStatus(statusMap);
            } else if (listeningCommand.equals("!statistics:hollis-queens")) {
                skipInitial = 7;
                expectedMessages = 3;
                tokenizerConsumer = (str, st) -> parseStatistics(st, str, false);
                finished = () -> updateSparkTrain(false);
            } else if (listeningCommand.equals("!list all")) {
                skipInitial = 2;
                expectedMessages = 1;
                tokenizerConsumer = (str, st) -> parseListAll(st, statusMap);
                finished = () -> {
                };
            } else if (listeningCommand.equals("!statistics")) {
                skipInitial = 7;
                expectedMessages = 3;
                tokenizerConsumer = (str, st) -> {
                    parseStatistics(st, str, true);
                    ctx.variables().sparkTrainSnapshot().access(atomicSnapshot -> {
                        SparkTrainSnapshot<?> snapshot = atomicSnapshot.get();
                        if (!snapshot.isDefault()) {
                            if (snapshot.update(ctx)) {
                                atomicSnapshot.set(SparkTrainSnapshot.DEFAULT_SNAPSHOT);
                            }
                            return true;
                        }
                        return false;
                    });
                };
                finished = () -> updateSparkTrain(true);
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
                String line = lines[index];
                tokenizerConsumer.accept(line, new StringTokenizer(line));
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
        } catch (Exception e) {
            e.printStackTrace();
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

    private void updateSparkTrain(boolean global) {
        int place = 1;
        DatabaseCachingService databaseCaching = ctx.databaseCaching();
        StringBuilder sb = new StringBuilder();
        for (SparkTrainMember nextMember : members) {
            Long memberId = databaseCaching.getMemberNames().inverse().get(nextMember.getName());
            if (memberId == null) {
                continue;
            }
            double score = nextMember.computeScore();
            if (global) {
                nextMember.getUpaMember().getGlobalSparkTrainPlace().set(place);
            } else {
                nextMember.getUpaMember().getHollisSparkTrainPlace().set(place);
            }
            sb.append(place++).append(". ").
                    append(nextMember.getName()).
                    append(" (").append(DECIMAL_FORMAT.format(score)).
                    append(" SSH | ").
                    append(DECIMAL_FORMAT.format(nextMember.getStaking())).
                    append(" Staked) \n\n");
            if (place == 6) {
                if (global) {
                    lastGlobalPartialTrain = sb.toString();
                } else {
                    lastHollisPartialTrain = sb.toString();
                }
            }
        }
        String fullTrain;
        String partialTrain;
        if (global) {
            lastGlobalSparkTrain = sb.toString();
            if (lastGlobalPartialTrain == null) {
                lastGlobalPartialTrain = lastGlobalSparkTrain;
            }
            partialTrain = lastGlobalPartialTrain;
            fullTrain = lastGlobalSparkTrain;
        } else {
            lastHollisSparkTrain = sb.toString();
            if (lastHollisPartialTrain == null) {
                lastHollisPartialTrain = lastHollisSparkTrain;
            }
            partialTrain = lastHollisPartialTrain;
            fullTrain = lastHollisSparkTrain;
        }
        ctx.variables().sparkTrainRepository().accessValue(repo -> repo.update(members, fullTrain, partialTrain, global));
    }

    private void selectProperty(long propertyId, long memberId, BiConsumer<String, Builder> finish, Consumer<String> failed, boolean global) {
        if (propertyId == -1) {
            failed.accept("Invalid property ID. Please try again.");
            return;
        }
        UpaMember upaMember = ctx.databaseCaching().getMembers().get(memberId);
        if (upaMember == null) {
            failed.accept("Please become a UPA member by using /account first.");
            return;
        }
        if (!upaMember.getActive().get()) {
            failed.accept("Please use '/account/' in <#967096154607194232> to reactivate your account.");
            return;
        }
        boolean globalNodeProp = false;
        int up2 = 0;
        String address = null;
        if (!global) {
            UpaProperty upaProp = ctx.databaseCaching().getProperties().get(propertyId);
            if (upaProp == null || upaProp.getNode() != Node.HOLLIS) {
                failed.accept("Must be a registered Hollis property that you own. Please try again.");
                return;
            }
            if (upaMember.getKey() != upaProp.getMemberKey()) {
                failed.accept("You do not own this property.");
                return;
            }
            up2 = upaProp.getUp2();
            address = upaProp.getAddress();
        } else {
            String msg = "Failed to lookup property. Please try again later.";
            UpaProperty upaProp = ctx.databaseCaching().getProperties().get(propertyId);
            if (upaProp == null) {
                CachedProperty cachedProperty = ctx.databaseCaching().getPropertyLookup().get(propertyId);
                if (cachedProperty != null && cachedProperty.getArea() > 0 && cachedProperty.getAddress() != null) {
                    up2 = cachedProperty.getArea();
                    address = cachedProperty.getAddress();
                } else {
                    try {
                        Property property = PropertyDataFetcher.fetchPropertySynchronous(propertyId);
                        if (property == null) {
                            failed.accept(msg);
                            return;
                        }
                        up2 = property.getArea();
                        address = property.getFullAddress();
                    } catch (Exception e) {
                        logger.catching(e);
                        failed.accept(msg);
                        return;
                    }
                }
            } else {
                globalNodeProp = true;
                if (upaMember.getKey() != upaProp.getMemberKey()) {
                    failed.accept("You do not own this property.");
                    return;
                }
                up2 = upaProp.getUp2();
                address = upaProp.getAddress();
            }
        }
        boolean firstBuild = !global && ctx.databaseCaching().getMemberProperties().get(upaMember.getKey()).stream().
                noneMatch(next -> next.getBuildStatus().get().equals("Completed"));
        SelectMenu.Builder selectStructureBldr = SelectMenu.create("select_structure");
        for (Structure structure : ctx.sparkTrain().getSuitableStructures(up2)) {
            selectStructureBldr.addOption(structure.getName(), structure.getName());
        }
        propertyRequests.put(memberId, new PropertyRequest(propertyId, global, globalNodeProp));
        String message = "Please select which structure you'd like to build on **" + address + "**.";
        if (firstBuild) {
            message += "If this is your first build, you only require half the amount of SSH for micro houses and small townhouses.";
        }
        finish.accept(message, selectStructureBldr);
    }

    public AtomicReference<String> getListeningFor() {
        return listeningFor;
    }

    public String getLastHollisSparkTrain() {
        return lastHollisSparkTrain;
    }

    public String getLastHollisPartialTrain() {
        return lastHollisPartialTrain;
    }

    public void setLastGlobalPartialTrain(String lastGlobalPartialTrain) {
        this.lastGlobalPartialTrain = lastGlobalPartialTrain;
    }

    public void setLastHollisSparkTrain(String lastHollisSparkTrain) {
        this.lastHollisSparkTrain = lastHollisSparkTrain;
    }

    public void setLastGlobalSparkTrain(String lastGlobalSparkTrain) {
        this.lastGlobalSparkTrain = lastGlobalSparkTrain;
    }

    public void setLastHollisPartialTrain(String lastHollisPartialTrain) {
        this.lastHollisPartialTrain = lastHollisPartialTrain;
    }
}
