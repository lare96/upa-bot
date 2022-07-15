package me.upa.discord;

import com.google.common.base.CaseFormat;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import me.upa.UpaBot;
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
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static me.upa.discord.DiscordService.DECIMAL_FORMAT;

/**
 * A {@link ListenerAdapter} that sorts SSH changes from the Upland Data bot.
 */
public final class SparkTrainMessageListener extends ListenerAdapter {

    /**
     * Users blacklisted from the spark train.
     */
    public static final ImmutableSet<String> BLACKLISTED = ImmutableSet.of("kcbc", "dlnlab");

    /**
     * The amount of expected messages from the Upland Data bot.
     */
    private static final int EXPECTED_MESSAGES = 2;

    /**
     * The amount of SSH required to become a member.
     */
    private static final int SPARK_HOURS_THROTTLE = 100;

    private final AtomicReference<String> listeningFor = new AtomicReference<>();
    private final AtomicInteger currentMessage = new AtomicInteger();
    private final SortedSet<SparkTrainMember> members = new ConcurrentSkipListSet<>();
    private final Queue<Message> messages = new ConcurrentLinkedQueue<>();

    private volatile String lastSparkTrain;

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        switch (event.getButton().getId()) {
            case "spark_train_position":
                long memberId = event.getMember().getIdLong();
                if (DiscordService.THROTTLER.needsThrottling(memberId)) {
                    event.reply("Please wait before using this again.").setEphemeral(true).queue();
                    return;
                }
                UpaMember upaMember = UpaBot.getDatabaseCachingService().getMembers().get(memberId);
                if (upaMember == null) {
                    event.reply("You must link with UPA using /account before you can do this.").setEphemeral(true).queue();
                    return;
                }
                int place = upaMember.getSparkTrainPlace().get();
                double sparkTrainSsh = upaMember.getSparkTrainSsh().get();
                if (place == 0) {
                    if (sparkTrainSsh == 0) {
                        event.reply("You must stake spark on one of the structures in <#963108957784772659> to join the spark train. Your SSH will remain even if you unstake your spark.").setEphemeral(true).queue();
                    } else {
                        event.reply("New UPA members must first earn " + SparkTrainMessageListener.SPARK_HOURS_THROTTLE).setEphemeral(true).queue();
                    }
                    return;
                }
                event.reply("Passenger #" + place + " with " + DiscordService.DECIMAL_FORMAT.format(upaMember.getTotalSsh()) + " SSH").setEphemeral(true).queue();
                break;
            case "view_build_requests":
                var requests = UpaBot.getSparkTrainMicroService().getBuildRequests().values();
                if (requests.isEmpty()) {
                    event.reply("That's odd, there are no active build requests at the moment.").setEphemeral(true).queue();
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (UpaBuildRequest buildRequest : requests) {
                    UpaProperty property = UpaBot.getDatabaseCachingService().getProperties().get(buildRequest.getPropertyId());
                    UpaMember member = UpaBot.getDatabaseCachingService().getMembers().get(buildRequest.getMemberId());
                    if (member == null) {
                        continue;
                    }
                    sb.append(property.getAddress()).append(" | ").append(buildRequest.getStructureName().toLowerCase()).
                            append(" | ").append("<@").append(member.getMemberId()).append(">").append("\n\n");
                }
                event.reply(sb.toString()).setEphemeral(true).queue();
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
                    String currentStatus = UpaBot.getDatabaseCachingService().getConstructionStatus().get(address);
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
                    Long memberId = UpaBot.getDatabaseCachingService().getMemberNames().inverse().get(inGameName);
                    if (memberId == null) {
                        return;
                    }
                    UpaMember upaMember = UpaBot.getDatabaseCachingService().getMembers().get(memberId);
                    upaMember.getSparkTrainSsh().set(sh2o - shfo);
                    SparkTrainMember nextMember = new SparkTrainMember(inGameName, stake, building, completed, sh2o, shfo, upaMember);
                    members.add(nextMember);
                } else {
                    throw new IllegalStateException("Invalid token count.");
                }
            };
            finished = () -> UpaBot.getSparkTrainMicroService().editComment(buildMessage(), success -> {
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
                        DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
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
        DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
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
        if(stopLength == 0) {
            stopLength = sb.length();
        }
        String fullTrain = sb.toString();
        lastSparkTrain = fullTrain;
        return new MessageBuilder().append("Stake your spark at <#963108957784772659>\n").appendCodeBlock(fullTrain.substring(0, stopLength), "").setActionRows(ActionRow.of(
                Button.of(ButtonStyle.PRIMARY, "spark_train_position", "View spark train position", Emoji.fromUnicode("U+1F689")),
                Button.of(ButtonStyle.PRIMARY, "view_build_requests", "View build requests", Emoji.fromUnicode("U+1F477"))
        )).build();
    }

    public AtomicReference<String> getListeningFor() {
        return listeningFor;
    }

    public String getLastSparkTrain() {
        return lastSparkTrain;
    }
}
