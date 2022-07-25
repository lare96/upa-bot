package me.upa.service;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import me.upa.UpaBot;
import me.upa.UpaBotContext;
import me.upa.discord.DiscordService;
import me.upa.discord.UpaBuildRequest;
import me.upa.discord.UpaBuildSlot;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaProperty;
import me.upa.fetcher.PropertyDataFetcher;
import me.upa.fetcher.StructureDataFetcher;
import me.upa.game.Property;
import me.upa.game.Structure;
import me.upa.sql.SqlConnectionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.MessageBuilder.Formatting;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import static me.upa.discord.DiscordService.DECIMAL_FORMAT;

public final class SparkTrainMicroService extends MicroService {

    private enum State {
        SPARK_TRAIN,

        STRUCTURES,

        ROLES,

        REQUESTS
    }

    public static final class SparkTrainMember implements Comparable<SparkTrainMember> {
        private final String name;
        private final double staking;
        private final int completedCount;
        private final int buildingCount;
        private final double sparkHoursGiven;
        private final double sparkHoursReceived;

        private final UpaMember upaMember;

        public SparkTrainMember(String name, double staking, int completedCount, int buildingCount, double sparkHoursGiven, double sparkHoursReceived, UpaMember upaMember) {
            this.name = name;
            this.staking = staking;
            this.completedCount = completedCount;
            this.buildingCount = buildingCount;
            this.sparkHoursGiven = sparkHoursGiven;
            this.sparkHoursReceived = sparkHoursReceived;
            this.upaMember = upaMember;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("staking", staking)
                    .add("contribution", DECIMAL_FORMAT.format(sparkHoursGiven - sparkHoursReceived))
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SparkTrainMember member = (SparkTrainMember) o;
            return Objects.equal(name, member.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }

        @Override
        public int compareTo(@NotNull SparkTrainMember o) {
            return Double.compare(o.computeScore(), computeScore());
        }

        public double computeScore() {
            return (sparkHoursGiven - sparkHoursReceived) + upaMember.getSsh().get();
        }

        public String getName() {
            return name;
        }

        public double getStaking() {
            return staking;
        }

        public int getBuildingCount() {
            return buildingCount;
        }

        public int getCompletedCount() {
            return completedCount;
        }

        public double getSparkHoursGiven() {
            return sparkHoursGiven;
        }

        public double getSparkHoursReceived() {
            return sparkHoursReceived;
        }

        public UpaMember getUpaMember() {
            return upaMember;
        }
    }

    private static final Path BUILDS_PATH = Paths.get("data", "builds.bin");
    private static final String COMMENT_ID = "990519031301832725";
    private static final Logger logger = LogManager.getLogger();

    private final Map<String, Structure> structureData = new ConcurrentHashMap<>();

    private final Map<Long, UpaBuildRequest> buildRequests = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<UpaBuildSlot> buildSlots = new ConcurrentLinkedQueue<>();
    private volatile State currentState = State.SPARK_TRAIN;
    private volatile Instant lastAnnouncement = Instant.now();
    private final UpaBotContext ctx;

    public SparkTrainMicroService(UpaBotContext ctx) {
        super(Duration.ofMinutes(1));
        this.ctx = ctx;
    }

    @Override
    public void startUp() throws Exception {
        ConcurrentLinkedQueue<UpaBuildSlot> loadedBuildQueue = Files.exists(BUILDS_PATH) ? ctx.load(BUILDS_PATH) : null;
        if (loadedBuildQueue != null) {
            buildSlots.addAll(loadedBuildQueue);
        }
        DiscordService discordService = ctx.discord();
        Message message = discordService.guild().getTextChannelById(990518630129221652L).retrieveMessageById(COMMENT_ID).complete();
        if (message == null) {
            throw new Exception("No spark train comment.");
        }
        var structureFetcher = new StructureDataFetcher();
        structureFetcher.waitUntilDone().forEach(next -> structureData.put(next.getName(), next));
        try (Connection connection = SqlConnectionManager.getInstance().take();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM build_requests;");
             ResultSet results = ps.executeQuery()) {
            while (results.next()) {
                long memberId = results.getLong(1);
                long propertyId = results.getLong(2);
                String structureName = results.getString(3);
                String address = ctx.databaseCaching().getProperties().get(propertyId).getAddress();
                UpaBuildRequest request = new UpaBuildRequest(memberId, propertyId, structureName);
                request.setAddress(address);
                buildRequests.put(memberId, request);
            }
        }
        computeLeastRequiredSsh();
        run();
    }

    @Override
    public void run() throws Exception {
        switch (currentState) {
            case SPARK_TRAIN:
                if (ctx.discord().getSparkTrain().getListeningFor().compareAndSet(null, "!statistic all")) {
                    ctx.discord().guild().getTextChannelById(979640542805782568L).sendMessage("!statistic all").queue();
                }
                currentState = State.STRUCTURES;
                break;
            case STRUCTURES:
                if (ctx.discord().getSparkTrain().getListeningFor().compareAndSet(null, "!list all:hollis-queens")) {
                    ctx.discord().guild().getTextChannelById(979640542805782568L).sendMessage("!list all:hollis-queens").queue();
                }
                currentState = State.ROLES;
                break;
            case ROLES:
                Role staker = ctx.discord().guild().getRoleById(965427810707595394L);
                for (UpaMember upaMember : ctx.databaseCaching().getMembers().values()) {
                    if (upaMember.getSparkTrainPlace().get() > 0) {
                        ctx.discord().guild().addRoleToMember(UserSnowflake.fromId(upaMember.getMemberId()), staker).queue();
                    } else {
                        ctx.discord().guild().removeRoleFromMember(UserSnowflake.fromId(upaMember.getMemberId()), staker).queue();
                    }
                }
                currentState = State.REQUESTS;
                break;
            case REQUESTS:
                // Update existing build slots, announce when completed.
                updateBuildSlots();

                // Attempt to add to the build queue.
                Collection<UpaBuildRequest> existingRequests = ctx.sparkTrain().getBuildRequests().values();
                if (!existingRequests.isEmpty()) {
                    // Sort all requests from highest -> lowest SSH.
                    List<UpaBuildRequest> requests = new ArrayList<>(existingRequests);
                    requests.sort((o1, o2) -> {
                        double o1Ssh = ctx.databaseCaching().getMembers().get(o1.getMemberId()).getTotalSsh();
                        double o2Ssh = ctx.databaseCaching().getMembers().get(o2.getMemberId()).getTotalSsh();
                        return Double.compare(o2Ssh, o1Ssh);
                    });

                    try {
                        // Genesis properties come first.
                        for (UpaBuildRequest buildRequest : requests) {
                            UpaProperty property = ctx.databaseCaching().getProperties().get(buildRequest.getPropertyId());
                            if (property != null && property.isGenesis()) {
                                if (!addBuildSlot(buildRequest, property)) {
                                    break;
                                }
                            }
                        }

                        // No genesis properties were found, check all others.
                        for (UpaBuildRequest buildRequest : requests) {
                            UpaProperty property = ctx.databaseCaching().getProperties().get(buildRequest.getPropertyId());
                            if (property != null) {
                                if (!addBuildSlot(buildRequest, property)) {
                                    break;
                                }
                            }
                        }
                    } finally {
                        ctx.save(BUILDS_PATH, buildSlots);
                    }
                }
                currentState = State.SPARK_TRAIN;
                announceBuildSlots();
                break;
        }
    }

    public void editComment(Message content, Consumer<Message> onSuccess) {
        ctx.discord().guild().getTextChannelById(990518630129221652L).retrieveMessageById(COMMENT_ID).queue(success -> success.editMessage(content).queue(onSuccess));
    }

    private volatile Structure leastRequiredSsh;

    public int getLeastRequiredSsh() {
        return leastRequiredSsh.getSshRequired();
    }

    public Structure getLeastRequiredSshStructure() {
        return leastRequiredSsh;
    }

    private void computeLeastRequiredSsh() {
        Structure lastStructure = null;
        for (Structure structure : structureData.values()) {
            if (lastStructure == null || structure.getSshRequired() < lastStructure.getSshRequired()) {
                lastStructure = structure;
            }
        }
        leastRequiredSsh = lastStructure;
    }

    public boolean hasActiveBuild(long memberId) {
        for (UpaBuildSlot slot : buildSlots) {
            if (slot.getMemberId() == memberId) {
                return true;
            }
        }
        return false;
    }

    public void updateBuildSlots() {
        Iterator<UpaBuildSlot> it = buildSlots.iterator();
        while (it.hasNext()) {
            UpaBuildSlot slot = it.next();
            try {
                Property property = PropertyDataFetcher.fetchPropertySynchronous(slot.getPropertyId());
                if (property != null) {
                    slot.getCompletionPercent().set(property.getBuildPercentage());
                    slot.getSparkStaked().set(property.getStakedSpark());
                    slot.getFinishedAt().set(property.getFinishedAt());
                }
                if (slot.getCompletionPercent().get() >= 100.0) {
                    it.remove();
                }
            } catch (Exception e) {
                logger.catching(e);
            }
        }
    }

    public boolean addBuildSlot(UpaBuildRequest request, UpaProperty requestProperty) throws Exception {
        boolean addSlot = true;
        if (buildSlots.size() > 0) {
            List<UpaBuildSlot> slotList = new ArrayList<>(buildSlots);
            UpaBuildSlot slot = slotList.get(slotList.size() - 1);
            if (slot.getFillPercent() >= 80.0) {
                lastAnnouncement = Instant.now().minus(1, ChronoUnit.DAYS);
            } else {
                addSlot = false;
            }
        }

        if (addSlot) {
            Property property = PropertyDataFetcher.fetchPropertySynchronous(Long.toString(request.getPropertyId()));
            if (property == null) {
                throw new IllegalStateException("Could not load next property for build slot.");
            }
            if (property.getFinishedAt() == null) {
                if (request.getNotified().compareAndSet(false, true)) {
                    String msg = "Please start construction of **" + request.getStructureName() + "** on **" + property.getFullAddress() + "** to be accepted onto the build queue.";
                    ctx.discord().guild().retrieveMemberById(request.getMemberId()).queue(success -> {
                        success.getUser().openPrivateChannel().queue(privateChannel ->
                                privateChannel.sendMessage(msg).queue());
                        ctx.discord().guild().getTextChannelById(963112034726195210L).sendMessage(success.getAsMention() + " " + msg).queue();
                    });
                }
                return true;
            }
            try (Connection connection = SqlConnectionManager.getInstance().take();
                 PreparedStatement ps = connection.prepareStatement("DELETE FROM build_requests WHERE member_id = ?;")) {
                ps.setLong(1, request.getMemberId());
                if (ps.executeUpdate() == 1) {
                    buildSlots.add(new UpaBuildSlot(request.getMemberId(), request.getPropertyId(), request.getStructureName(), property.getFullAddress(), property.getMaxStakedSpark(), property.getFinishedAt(), property.getStakedSpark(), property.getBuildPercentage()));
                    buildRequests.remove(request.getMemberId());
                    ctx.discord().guild().getTextChannelById(979640542805782568L).sendMessage("!add:hollis-queens:" + property.getFullAddress() + ",Queens").queue();
                    return true;
                }
            } catch (Exception e) {
                logger.catching(e);
            }
        }
        return false;
    }

    public void announceBuildSlots() {
        int size = buildSlots.size();
        if (size > 0) {
            int place = 1;
            List<MessageEmbed> embedList = new ArrayList<>();
            for (UpaBuildSlot slot : buildSlots) {
                Color color = Color.RED;
                if (place == size) {
                    color = Color.GREEN;
                }
                Instant now = Instant.now();
                long hoursLeft = now.until(slot.getFinishedAt().get(), ChronoUnit.HOURS);
                long minutesLeft = now.until(slot.getFinishedAt().get(), ChronoUnit.MINUTES);
                String formatTimeLeft;
                double daysLeft = hoursLeft / 24.0;
                if (daysLeft >= 1) {
                    formatTimeLeft = DECIMAL_FORMAT.format(daysLeft) + " day(s)";
                } else if (hoursLeft > 0) {
                    formatTimeLeft = hoursLeft + " hour(s)";
                } else if (minutesLeft > 0) {
                    formatTimeLeft = minutesLeft + " minute(s)";
                } else {
                    formatTimeLeft = "Under a minute";
                }
                embedList.add(new EmbedBuilder().setTitle(getPlaceEmoji(place++).getAsMention() + " " + slot.getAddress()).
                        addField("Time left", formatTimeLeft, false).
                        addField("Owner", "<@" + slot.getMemberId() + ">", false).
                        addField("Spark total", slot.getSparkStaked() + "/" + slot.getMaxSparkStaked(), false).
                        addField("Property link", "https://play.upland.me/?prop_id=" + slot.getPropertyId(), false).
                        setColor(color).
                        build());
            }
            ctx.discord().guild().getTextChannelById(963108957784772659L).
                    retrieveMessageById(992885758581035181L).
                    queue(success -> success.editMessage(new MessageBuilder().append(":zap: :zap: ").
                            append("Where do I stake my spark?", Formatting.BOLD, Formatting.UNDERLINE).
                            append(" :zap: :zap:").setEmbeds(embedList).setActionRows(ActionRow.of(
                                    Button.of(ButtonStyle.PRIMARY, "manage_build_request", "Manage build request", Emoji.fromUnicode("U+1F3D7")),
                                    Button.of(ButtonStyle.PRIMARY, "view_build_requests", "View build requests", Emoji.fromUnicode("U+1F477"))
                            )).build()).queue());
            lastAnnouncement = Instant.now();
        }
    }

    private Emoji getPlaceEmoji(int place) {
        switch (place) {
            case 1:
                return Emoji.fromUnicode("U+0031 U+20E3");
            case 2:
                return Emoji.fromUnicode("U+0032 U+20E3");
            case 3:
                return Emoji.fromUnicode("U+0033 U+20E3");
            case 4:
                return Emoji.fromUnicode("U+0034 U+20E3");
            case 5:
                return Emoji.fromUnicode("U+0035 U+20E3");
            case 6:
                return Emoji.fromUnicode("U+0036 U+20E3");
            case 7:
                return Emoji.fromUnicode("U+0037 U+20E3");
            case 8:
                return Emoji.fromUnicode("U+0038 U+20E3");
            case 9:
                return Emoji.fromUnicode("U+0039 U+20E3");
            case 10:
                return Emoji.fromUnicode("U+1F51F U+20E3");
        }
        throw new IllegalStateException("Invalid place number.");
    }

    public Map<String, Structure> getStructureData() {
        return structureData;
    }

    public List<Structure> getSuitableStructures(int up2) {
        boolean sth = false;
        List<Structure> structures = new ArrayList<>();
        for (Structure next : structureData.values()) {
            if (up2 >= next.getMinUp2() && up2 <= next.getMaxUp2()) {
                if (next.getName().equals("Small Town House")) {
                    if (!sth) {
                        structures.add(next);
                        sth = true;
                    }
                    continue;
                }
                structures.add(next);
            }
        }
        return structures;
    }

    public Map<Long, UpaBuildRequest> getBuildRequests() {
        return buildRequests;
    }

    public ConcurrentLinkedQueue<UpaBuildSlot> getBuildSlots() {
        return buildSlots;
    }
}
