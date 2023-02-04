package me.upa.service;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import me.upa.UpaBotConstants;
import me.upa.UpaBotContext;
import me.upa.discord.UpaBuildRequest;
import me.upa.discord.UpaBuildRequest.BuildRequestResponse;
import me.upa.discord.UpaBuildRequest.UpaBuildRequestComparator;
import me.upa.discord.UpaBuildSlot;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaProperty;
import me.upa.fetcher.DataFetcherManager;
import me.upa.fetcher.PropertyDataFetcher;
import me.upa.fetcher.StructureDataFetcher;
import me.upa.game.City;
import me.upa.game.Neighborhood;
import me.upa.game.Node;
import me.upa.game.Property;
import me.upa.game.Structure;
import me.upa.sql.SqlConnectionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.Serializable;
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
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static me.upa.discord.DiscordService.DECIMAL_FORMAT;

/**
 * A {@link MicroService} implementation that manages both the Hollis and Global spark trains.
 */
public final class SparkTrainMicroService extends MicroService {

    /**
     * The possible states this service can be in.
     */
    private enum State {
        INITIAL_LOAD,
        UPDATE_HOLLIS_SPARK,

        UPDATE_HOLLIS_STRUCTURES,

        UPDATE_GLOBAL_SPARK,

        UPDATE_GLOBAL_STRUCTURES,

        HOLLIS_REQUESTS,

        UPDATE_ROLES
    }

    private enum SparkTrainType {
        HOLLIS,
        GLOBAL
    }

    private static final class SparkTrain {
        private static final EnumMap<SparkTrainType, SparkTrain> sparkTrainMap = new EnumMap<>(SparkTrainType.class);
    }

    /**
     * A class representing a member of the spark train.
     */
    public static final class SparkTrainMember implements Comparable<SparkTrainMember>, Serializable {

        private static final long serialVersionUID = 1665547923172420893L;
        /**
         * Their in-game name.
         */
        private final String name;

        /**
         * The amount they're staking.
         */
        private final double staking;

        /**
         * The amount of tracked buildings they've completed.
         */
        private final int completedCount;

        /**
         * The amount of tracked buildings they have in progress.
         */
        private final int buildingCount;

        /**
         * Their total spark hours given.
         */
        private final double sparkHoursGiven;

        /**
         * Their total spark hours received.
         */
        private final double sparkHoursReceived;

        /**
         * The UPA member instance.
         */
        private transient final UpaMember upaMember;

        private final boolean global;
        private final long memberId;

        /**
         * Creates a new {@link SparkTrainMember}.
         */
        public SparkTrainMember(String name, double staking, int completedCount, int buildingCount, double sparkHoursGiven,
                                double sparkHoursReceived, UpaMember upaMember, boolean global) {
            this.name = name;
            this.staking = staking;
            this.completedCount = completedCount;
            this.buildingCount = buildingCount;
            this.sparkHoursGiven = sparkHoursGiven;
            this.sparkHoursReceived = sparkHoursReceived;
            this.upaMember = upaMember;
            this.global = global;
            memberId = upaMember.getMemberId();
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
            return upaMember.getTotalSsh(global);
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

        public long getMemberId() {
            return memberId;
        }
    }

    private static final int PREVIOUS_BUILDS_TRACKED = 10;
    private static final int PREVIOUS_BUILDS_LIMIT = 6;
    private static final Path HOLLIS_BUILDS_PATH = Paths.get("data", "hollis_builds.bin");
    private static final Path GLOBAL_BUILDS_PATH = Paths.get("data", "global_builds.bin");
    private static final Path HOLLIS_PREVIOUS_PATH = Paths.get("data", "last_hollis_builds.bin");
    private static final Path GLOBAL_PREVIOUS_PATH = Paths.get("data", "last_global_builds.bin");

    private static final Path BUILD_REQUEST_RESPONSES_PATH = Paths.get("data", "build_request_responses.bin");

    private static final Logger logger = LogManager.getLogger();

    private final Map<String, Structure> structureData = new ConcurrentHashMap<>();

    private final Map<Long, UpaBuildRequest> hollisBuildRequests = new ConcurrentHashMap<>();
    private final Map<Long, UpaBuildRequest> globalBuildRequests = new ConcurrentHashMap<>();

    private final ConcurrentLinkedQueue<UpaBuildSlot> hollisBuildSlots = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<UpaBuildSlot> globalBuildSlots = new ConcurrentLinkedQueue<>();

    private final ConcurrentLinkedQueue<UpaBuildSlot> lastHollisBuilds = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<UpaBuildSlot> lastGlobalBuilds = new ConcurrentLinkedQueue<>();

    private final AtomicReference<Instant> nextRolesUpdate = new AtomicReference<>();
    private volatile State currentState = State.INITIAL_LOAD;
    private final UpaBotContext ctx;

    private volatile boolean paused;

    public void pauseQueries() {
        paused = true;
    }

    public void resumeQueries() {
        paused = false;
    }

    public SparkTrainMicroService(UpaBotContext ctx) {
        super(Duration.ofMinutes(1));
        this.ctx = ctx;
    }


    @Override
    public void startUp() throws Exception {
        nextRolesUpdate.set(Instant.now().plusSeconds(7200));
        loadQueue(HOLLIS_BUILDS_PATH, hollisBuildSlots);
        loadQueue(GLOBAL_BUILDS_PATH, globalBuildSlots);
        loadQueue(HOLLIS_PREVIOUS_PATH, lastHollisBuilds);
        loadQueue(GLOBAL_PREVIOUS_PATH, lastGlobalBuilds);
        var structureFetcher = new StructureDataFetcher();
        structureFetcher.waitUntilDone().forEach(next -> structureData.put(next.getName(), next));
        try (Connection connection = SqlConnectionManager.getInstance().take();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM build_requests;");
             ResultSet results = ps.executeQuery()) {
            while (results.next()) {
                long memberId = results.getLong(1);
                long propertyId = results.getLong(2);
                String structureName = results.getString(4);
                boolean isGlobal = results.getBoolean(5);
                String address = results.getString(3);
                UpaBuildRequest request = new UpaBuildRequest(memberId, propertyId, structureName, address);
                if (isGlobal) {
                    globalBuildRequests.put(memberId, request);
                } else {
                    hollisBuildRequests.put(memberId, request);
                }
            }
        }
        run();
    }

    @Override
    public void run() throws Exception {
        nextRolesUpdate.getAndUpdate(last -> {
            if (Instant.now().isAfter(last)) { // Update staking roles once every 2 hours.
                ctx.discord().execute(this::updateRoles);
                return last.plusSeconds(7200);
            }
            return last;
        });
        switch (currentState) {
            case INITIAL_LOAD:
                checkAllBuildRequests(globalBuildSlots, lastGlobalBuilds, globalBuildRequests);
                checkAllBuildRequests(hollisBuildSlots, lastHollisBuilds, hollisBuildRequests);
                currentState = State.UPDATE_HOLLIS_SPARK;
                break;
            case UPDATE_HOLLIS_SPARK:
                if(paused) {
                    return;
                }
                queryUplandData("!statistics:hollis-queens");
                currentState = State.UPDATE_GLOBAL_SPARK;
                break;
            case UPDATE_GLOBAL_SPARK:
                if(paused) {
                    return;
                }
                queryUplandData("!statistics");
                handleRequests(HOLLIS_BUILDS_PATH, hollisBuildSlots, hollisBuildRequests);
                currentState = State.UPDATE_HOLLIS_STRUCTURES;
                break;
            case UPDATE_HOLLIS_STRUCTURES:
                //  queryUplandData("!list all:hollis-queens");
                handleRequests(GLOBAL_BUILDS_PATH, globalBuildSlots, globalBuildRequests);
                currentState = State.UPDATE_GLOBAL_STRUCTURES;
                break;
            case UPDATE_GLOBAL_STRUCTURES:
                //  queryUplandData("!list all");
                currentState = State.UPDATE_HOLLIS_SPARK;
                break;
        }
    }

    private void loadQueue(Path queuePath, ConcurrentLinkedQueue<UpaBuildSlot> buildSlots) {
        ConcurrentLinkedQueue<UpaBuildSlot> loadedBuildQueue = Files.exists(queuePath) ? ctx.load(queuePath) : null;
        if (loadedBuildQueue != null) {
            buildSlots.addAll(loadedBuildQueue);
        }
    }

    private void loadMap(Map<Long, BuildRequestResponse> responsesMap) {
        ConcurrentHashMap<Long, BuildRequestResponse> loadedResponsesMap = Files.exists(SparkTrainMicroService.BUILD_REQUEST_RESPONSES_PATH) ?
                ctx.load(SparkTrainMicroService.BUILD_REQUEST_RESPONSES_PATH) : null;
        if (loadedResponsesMap != null) {
            responsesMap.putAll(loadedResponsesMap);
        }
    }

    private void queryUplandData(String query) {
        if (ctx.discord().getSparkTrain().getListeningFor().compareAndSet(null, query)) {
            ctx.discord().guild().getTextChannelById(979640542805782568L).sendMessage(query).queue();
        }
    }

    private void handleRequests(Path dbPath,
                                ConcurrentLinkedQueue<UpaBuildSlot> buildSlots,
                                Map<Long, UpaBuildRequest> buildRequests) throws Exception {
        // Update existing build slots, announce when completed.
        boolean global = buildSlots == globalBuildSlots;
        ConcurrentLinkedQueue<UpaBuildSlot> lastBuildSlots = global ? lastGlobalBuilds : lastHollisBuilds;
        Path lastBuildPath = global ? GLOBAL_PREVIOUS_PATH : HOLLIS_PREVIOUS_PATH;
        updateBuildSlots(buildSlots, lastBuildSlots, lastBuildPath);

        // Attempt to add to the build queue.
        Collection<UpaBuildRequest> existingRequests = buildRequests.values();
        if (!existingRequests.isEmpty()) {
            // Sort all requests from highest -> lowest SSH.
            List<UpaBuildRequest> requests = new ArrayList<>(existingRequests);

            requests.sort(new UpaBuildRequestComparator(ctx, global));
            try {
                // Genesis properties come first.
                if (!global) {
                    for (UpaBuildRequest buildRequest : requests) {
                        UpaProperty property = ctx.databaseCaching().getProperties().get(buildRequest.getPropertyId());
                        if (property != null && property.isGenesis()) {
                            if (!addBuildSlot(buildSlots, lastBuildSlots, buildRequests, buildRequest)) {
                                break;
                            }
                        }
                    }
                }

                // No genesis properties were found, check all others.
                for (UpaBuildRequest buildRequest : requests) {
                    if (!addBuildSlot(buildSlots, lastBuildSlots, buildRequests, buildRequest)) {
                        break;
                    }
                }
            } finally {
                ctx.save(dbPath, buildSlots);
            }
        }
        announceBuildSlots(buildSlots);
    }

    public boolean hasActiveBuild(ConcurrentLinkedQueue<UpaBuildSlot> buildSlots, long memberId) {
        for (UpaBuildSlot slot : buildSlots) {
            if (slot.getMemberId() == memberId) {
                return true;
            }
        }
        return false;
    }

    private void updateBuildSlots(ConcurrentLinkedQueue<UpaBuildSlot> slots, ConcurrentLinkedQueue<UpaBuildSlot> lastSlots, Path lastSlotsPath) {
        boolean removed = false;
        Iterator<UpaBuildSlot> it = slots.iterator();
        while (it.hasNext()) {
            UpaBuildSlot slot = it.next();
            try {
                Property property = PropertyDataFetcher.fetchPropertySynchronous(slot.getPropertyId());
                if (property != null) {
                    slot.getCompletionPercent().set(property.getBuildPercentage());
                    slot.getSparkStaked().set(property.getStakedSpark());
                    slot.getFinishedAt().set(property.getFinishedAt());
                    City city = DataFetcherManager.getCityMap().get(property.getCityId());
                    if (city != null)
                        slot.getCityName().set(city.getName());
                    Neighborhood neighborhood = PropertySynchronizationService.getNeighborhood(property);
                    if (neighborhood != null)
                        slot.getNeighborhoodName().set(neighborhood.getName());
                }
                if (slot.getCompletionPercent().get() >= 100.0) {
                    removed = true;
                    it.remove();
                    lastSlots.add(slot);
                    if (lastSlots.size() > PREVIOUS_BUILDS_TRACKED) {
                        lastSlots.poll();
                    }
                }
            } catch (Exception e) {
                logger.catching(e);
            }
        }
        if (removed) {
            ctx.save(lastSlotsPath, lastSlots);
        }
    }

    private void updateRoles() {
        Role staker = ctx.discord().guild().getRoleById(965427810707595394L);
        for (UpaMember upaMember : ctx.databaseCaching().getMembers().values()) {
            if (!upaMember.getActive().get()) {
                continue;
            }
            try {
                if (upaMember.getHollisSparkTrainPlace().get() > 0 ||
                        upaMember.getGlobalSparkTrainPlace().get() > 0) {
                    ctx.discord().guild().addRoleToMember(UserSnowflake.fromId(upaMember.getMemberId()), staker).complete();
                } else {
                    ctx.discord().guild().removeRoleFromMember(UserSnowflake.fromId(upaMember.getMemberId()), staker).complete();
                }
            } catch (ErrorResponseException e) {
                if (e.getErrorResponse() != ErrorResponse.UNKNOWN_MEMBER) {
                    logger.error(e);
                }
            } catch (Exception e) {
                logger.error(e);
            }
        }
    }

    private boolean hasHighestSsh(Map<Long, UpaBuildRequest> buildRequests, long memberId) {
        double highest = 0;
        long highestMemberId = 0;
        boolean global = buildRequests == globalBuildRequests;
        for (UpaBuildRequest request : buildRequests.values()) {
            UpaMember checkMember = ctx.databaseCaching().getMembers().get(request.getMemberId());
            if (checkMember == null || !checkMember.getActive().get()) {
                continue;
            }
            double check = checkMember.getTotalSsh(global);
            if (check > highest) {
                highest = check;
                highestMemberId = checkMember.getMemberId();
            }
        }
        return highestMemberId == memberId;
    }

    public boolean addBuildSlot(ConcurrentLinkedQueue<UpaBuildSlot> buildSlots,
                                ConcurrentLinkedQueue<UpaBuildSlot> lastBuildSlots,
                                Map<Long, UpaBuildRequest> buildRequests,
                                UpaBuildRequest request) throws Exception {
        boolean addSlot = true;
        if (buildSlots.size() > 0) {
            List<UpaBuildSlot> slotList = new ArrayList<>(buildSlots);
            UpaBuildSlot slot = slotList.get(slotList.size() - 1);
            Instant now = Instant.now();
            Instant then = slot.getFinishedAt().get();
            Duration between = then == null ? Duration.ofDays(2) : Duration.between(now, then);
            if (slot.getFillPercent() < 80.0 && between.toHours() > 48) {
                addSlot = false;
            }
        }

        if (addSlot) {
            boolean isHollis = buildSlots == hollisBuildSlots;
            BuildRequestResponse response = checkBuildRequest(buildSlots, lastBuildSlots, buildRequests, request);
            request.setResponse(response);
            switch (response) {
                case NOT_STARTED:
                    if (request.getNotified().compareAndSet(false, true)) {
                        String msg = "Please start construction of **" + request.getStructureName() + "** on **" + request.getAddress() + "** to be accepted onto the **" + (isHollis ? "Hollis" : "global") + "** build queue.";
                        ctx.discord().guild().retrieveMemberById(request.getMemberId()).queue(success -> {
                            success.getUser().openPrivateChannel().queue(privateChannel ->
                                    privateChannel.sendMessage(msg).queue());
                            ctx.discord().guild().getTextChannelById(isHollis ? 963112034726195210L :
                                    956790034097373204L).sendMessage(success.getAsMention() + " " + msg).queue();
                        });
                    }
                case TOO_MANY_BUILDS:
                    if (request.getNotified().compareAndSet(false, true)) {
                        String msg = "You have exceeded the threshold of having 6 of the last 10 builds on the train. Please make build requests for bigger structures to avoid this problem in the future.";
                        ctx.discord().guild().retrieveMemberById(request.getMemberId()).queue(success -> {
                            success.getUser().openPrivateChannel().queue(privateChannel ->
                                    privateChannel.sendMessage(msg).queue());
                        });
                    }
                    return true;
                case NORMAL:
                    Property property = request.getLoadedProperty();
                    if (property == null) {
                        return true;
                    }
                    try (Connection connection = SqlConnectionManager.getInstance().take();
                         PreparedStatement ps = connection.prepareStatement("DELETE FROM build_requests WHERE member_id = ? AND global_train = ?;")) {
                        ps.setLong(1, request.getMemberId());
                        ps.setBoolean(2, !isHollis);
                        if (ps.executeUpdate() == 1) {
                            buildSlots.add(new UpaBuildSlot(request.getMemberId(), request.getPropertyId(), request.getStructureName(), property.getFullAddress(), property.getMaxStakedSpark(), property.getFinishedAt(), property.getStakedSpark(), property.getBuildPercentage()));
                            buildRequests.remove(request.getMemberId());
                            String query = isHollis ? "!add:hollis-queens:" : "!add:";
                            String cityName = DataFetcherManager.getCityMap().get(property.getCityId()).getName();
                            ctx.discord().guild().getTextChannelById(979640542805782568L).sendMessage(query + property.getFullAddress() + "," + cityName).queue();
                            ctx.discord().guild().retrieveMemberById(request.getMemberId()).queue(member ->
                                    member.getUser().openPrivateChannel().queue(channel ->
                                            channel.sendMessage("Your build request for **" + request.getStructureName() + "** on **" + request.getAddress() + "** has been accepted and is now on the **" + (isHollis ? "Hollis" : "global") + "** queue!").queue()));
                            return false;
                        }
                    } catch (Exception e) {
                        logger.catching(e);
                    }
                    return true;
            }
        }
        return true;
    }

    private void checkAllBuildRequests(ConcurrentLinkedQueue<UpaBuildSlot> buildSlots,
                                       ConcurrentLinkedQueue<UpaBuildSlot> lastBuildSlots,
                                       Map<Long, UpaBuildRequest> buildRequests) throws Exception {
        for (UpaBuildRequest request : buildRequests.values()) {
            request.setResponse(checkBuildRequest(buildSlots, lastBuildSlots, buildRequests, request));
        }
    }

    public boolean hasBuildRequest(long memberId) {
        return globalBuildRequests.containsKey(memberId) || hollisBuildRequests.containsKey(memberId);
    }

    public BuildRequestResponse checkBuildRequest(ConcurrentLinkedQueue<UpaBuildSlot> buildSlots,
                                                  ConcurrentLinkedQueue<UpaBuildSlot> lastBuildSlots,
                                                  Map<Long, UpaBuildRequest> buildRequests,
                                                  UpaBuildRequest request) throws Exception {
        boolean isHollis = buildSlots == hollisBuildSlots;
        UpaMember upaMember = ctx.databaseCaching().getMembers().get(request.getMemberId());
        if (upaMember == null || !upaMember.getActive().get()) {
            return BuildRequestResponse.ERROR;
        }
        Guild guild = ctx.discord().guild();
        Role role = guild.getRoleById(963449135485288479L);
        boolean isVip = guild.retrieveMemberById(upaMember.getMemberId()).complete().getRoles().contains(role);
        if (!isVip && hasActiveBuild(buildSlots, upaMember.getMemberId())) {
            return BuildRequestResponse.HAS_ACTIVE_BUILD;
        }
        Structure structure = structureData.get(request.getStructureName());
        if (structure == null) {
            logger.error("Structure " + request.getStructureName() + " invalid.");
            return BuildRequestResponse.ERROR;
        }
        int count = 0;
        for (UpaBuildSlot slot : lastBuildSlots) {
            if (slot.getMemberId() == request.getMemberId()) {
                count++;
            }
        }
        if (count >= PREVIOUS_BUILDS_LIMIT) {
            return BuildRequestResponse.TOO_MANY_BUILDS;
        }
        boolean firstBuild = ctx.databaseCaching().getMemberProperties().get(upaMember.getKey()).stream().
                noneMatch(next -> next.getBuildStatus().get().equals("Completed"));
        double ssh = upaMember.getTotalSsh(!isHollis);
        boolean inNode = ctx.databaseCaching().getProperties().containsKey(request.getPropertyId());
        boolean hasSsh = ssh >= structure.getSshRequired(firstBuild, !isHollis, inNode);
        boolean freePass = !hasSsh && hasHighestSsh(buildRequests, upaMember.getMemberId());
        if (!hasSsh && !freePass) {
            return BuildRequestResponse.NOT_ENOUGH_SSH;
        }
        Property property = PropertyDataFetcher.fetchPropertySynchronous(request.getPropertyId());
        if (property == null) {
            throw new IllegalStateException("Could not load next property for build slot.");
        }
        request.setLoadedProperty(property);
        boolean hasStructure = property.getBuildStatus().equals("Completed");
        if (hasStructure) {
            return BuildRequestResponse.HAS_STRUCTURE;
        } else if (property.getFinishedAt() == null) {
            return BuildRequestResponse.NOT_STARTED;
        }
        return BuildRequestResponse.NORMAL;
    }

    public void announceBuildSlots(ConcurrentLinkedQueue<UpaBuildSlot> buildSlots) {
        boolean isHollis = buildSlots == hollisBuildSlots;
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
                Emoji placeEmoji = getPlaceEmoji(place++);
                String str = placeEmoji == null ? "" : placeEmoji.getFormatted();
                EmbedBuilder bldr = new EmbedBuilder();
                bldr.setTitle(str + " " + slot.getAddress() + (!isHollis ? ", " + slot.getCityName().get() : "")).
                        addField("Structure", slot.getStructureName(), false).
                        addField("Time left", formatTimeLeft, false).
                        addField("Owner", "<@" + slot.getMemberId() + ">", false);
                if (!isHollis) {
                    bldr.addField("Neighborhood", slot.getNeighborhoodName().get(), false); // TODO slot should have property cached initially
                }
                bldr.addField("Spark total", slot.getSparkStaked() + "/" + slot.getMaxSparkStaked(), false).
                        addField("Property link", "https://play.upland.me/?prop_id=" + slot.getPropertyId(), false).
                        setColor(color);
                embedList.add(bldr.build());
            }
            ctx.discord().guild().getTextChannelById(isHollis ? UpaBotConstants.HOLLIS_TRAIN_CHANNEL : UpaBotConstants.GLOBAL_TRAIN_CHANNEL).
                    retrieveMessageById(isHollis ? 1025484301053210664L : 1026889215792926810L).
                    queue(success -> success.editMessage(new MessageEditBuilder().
                            setContent(":zap: :zap: __**Where do I stake my spark?**__ :zap: :zap:").
                            setEmbeds(embedList).setComponents(ActionRow.of(
                                    Button.of(ButtonStyle.PRIMARY, isHollis ? "manage_build_request" : "manage_build_request_global", "Manage build request", Emoji.fromUnicode("U+1F3D7")),
                                    Button.of(ButtonStyle.PRIMARY, isHollis ? "view_build_requests" : "view_build_requests_global", "View build requests", Emoji.fromUnicode("U+1F477")),
                                    Button.of(ButtonStyle.PRIMARY, isHollis ? "st_hollis" : "st_global", "Spark train", Emoji.fromUnicode("U+1F682")),
                                    Button.of(ButtonStyle.PRIMARY, isHollis ? "st_hollis_guidelines" : "st_global_guidelines", "Guidelines", Emoji.fromUnicode("U+1F4DC"))
                            )).build()).queue());
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
            default:
                return null;
        }
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

    public Map<Long, UpaBuildRequest> getHollisBuildRequests() {
        return hollisBuildRequests;
    }

    public Map<Long, UpaBuildRequest> getGlobalBuildRequests() {
        return globalBuildRequests;
    }

    public ConcurrentLinkedQueue<UpaBuildSlot> getHollisBuildSlots() {
        return hollisBuildSlots;
    }

    public ConcurrentLinkedQueue<UpaBuildSlot> getGlobalBuildSlots() {
        return globalBuildSlots;
    }
}
