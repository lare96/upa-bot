package me.upa.service;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import me.upa.UpaBotContext;
import me.upa.discord.listener.credit.CreditTransaction;
import me.upa.discord.listener.credit.CreditTransaction.CreditTransactionType;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaProperty;
import me.upa.fetcher.DataFetcherManager;
import me.upa.fetcher.ProfileDataFetcher;
import me.upa.fetcher.PropertyDataFetcher;
import me.upa.game.CachedProperty;
import me.upa.game.Neighborhood;
import me.upa.game.Node;
import me.upa.game.Profile;
import me.upa.game.Property;
import me.upa.sql.SqlConnectionManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.geom.Path2D;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static me.upa.fetcher.ProfileDataFetcher.DUMMY;

public class PropertySynchronizationService extends AbstractScheduledService {


    private static final int MAX_REQUESTS = 10;
    private static final Path CURRENT_SYNC_FILE = Paths.get("data", "sync.bin");

    // TODO EASIEST WAY... CHECK BLOCKCHAIN!!! AND ONLY VERIFY IF NEEDED. ONLY DO "INITIAL" SYNC ONCE THIS WILL SAVE HUGE BANDWITH
    private enum State {

        /**
         * Select next user to synchronize, save their profile. Or pick up where you left off.
         */
        SELECT,

        /**
         * Determine which properties need to be added or removed and upload bit-by-bit.
         */
        SYNCHRONIZE,

        /**
         * Once synchronization is complete, verify if the user is an actual member still.
         */
        VERIFY,

        /**
         * No players left to synchronize. Take a rest for a while.
         */
        IDLE
    }

    private static class SynchronizationData implements Serializable {

        private static final long serialVersionUID = 322647125391910293L;
        private Profile lastUser;
        private Instant wakeUpAt;
        private final Set<Long> genesisIds = new HashSet<>();

        {
            genesisIds.addAll(Set.of(78551397018254L, 78551380241018L, 78551363463770L, 78551380241014L, 78551363463774L, 78551380241010L,
                    78551363463778L, 78551363463782L, 78551346686542L, 78551346686537L, 78551397018248L, 78551397018241L, 78551413795477L,
                    78551413795484L, 78551363463762L, 78551363463766L, 78551380241006L, 78551380241002L, 78550944033398L, 78551178914382L,
                    78550927256158L, 78551195691618L, 78551212468854L, 78551195691622L, 78551178914398L, 78550927256162L, 78551178914386L,
                    78551195691626L, 78550910478938L, 78550927256178L, 78550910478934L, 78550927256166L, 78550910478930L, 78550910478926L,
                    78550927256174L, 78551178914390L, 78551178914394L, 78551195691634L, 78550927256170L, 78551195691630L, 78551178914376L,
                    78550910478920L, 78551212468872L, 78550944033409L, 78550960810645L, 78551229246101L, 78551212468858L, 78551229246094L,
                    78550960810638L, 78550944033416L, 78550960810651L, 78550944033402L, 78551212468865L, 78551229246107L, 78550491048550L,
                    78550491048546L, 78550759484014L, 78550742706778L, 78550742706774L, 78550491048554L, 78550759484018L, 78550759484010L,
                    78550474271309L, 78550742706790L, 78550474271313L, 78550491048558L, 78550491048566L, 78550742706782L, 78550759484022L,
                    78550742706786L, 78550491048562L, 78550742706765L, 78550474271317L, 78550474271322L, 78550474271326L, 78550474271304L,
                    78550725929545L, 78550759484033L, 78550507825806L, 78550507825792L, 78550776261269L, 78550507825786L, 78550524603028L,
                    78550776261262L, 78550776261255L, 78550793038491L, 78550524603035L, 78550507825799L, 78550759484026L, 78550289721928L,
                    78550289721933L, 78550306499153L, 78550306499157L, 78550306499161L, 78550306499165L, 78550306499169L, 78550306499174L,
                    78550306499182L, 78550323276402L, 78550323276406L, 78550323276410L, 78550323276416L, 78550323276423L, 78550340053646L,
                    78550340053652L, 78550340053659L));
        }

        public void reset() {
            lastUser = null;
            wakeUpAt = null;
        }

        public String getUsername() {
            return lastUser.getOwnerUsername();
        }
    }

    private static final class NewProperty {
        private final Property property;
        private final Neighborhood neighborhood;

        private NewProperty(Property property, Neighborhood neighborhood) {
            this.property = property;
            this.neighborhood = neighborhood;
        }
    }

    private static final class NodeProperty {
        private final long propertyId;
        private final int memberKey;
        private final CachedProperty cachedProperty;

        private NodeProperty(long propertyId, int memberKey, CachedProperty cachedProperty) {
            this.propertyId = propertyId;
            this.memberKey = memberKey;
            this.cachedProperty = cachedProperty;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NodeProperty that = (NodeProperty) o;
            return propertyId == that.propertyId;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(propertyId);
        }
    }

    private static final Logger logger = LogManager.getLogger();

    private volatile State state;
    private volatile SynchronizationData syncData;

    private final AtomicReference<ListenableScheduledFuture<?>> resumeSyncRef = new AtomicReference<>();

    private final Phaser idleMonitor = new Phaser(2);
    private final Set<UpaProperty> removals = Sets.newConcurrentHashSet();
    private final Set<NodeProperty> additions = Sets.newConcurrentHashSet();
    private final UpaBotContext ctx;

    public PropertySynchronizationService(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    protected void startUp() throws Exception {
        ctx.discord().cjNetworth = ProfileDataFetcher.fetchProfileSynchronous("unruly_cj").getNetWorth();
        ctx.discord().highroadNetworth = ProfileDataFetcher.fetchProfileSynchronous("highroad").getNetWorth();
        ctx.discord().updatePacStats();

        syncData = !Files.exists(CURRENT_SYNC_FILE) ? new SynchronizationData() : ctx.load(CURRENT_SYNC_FILE);
        List<Long> updateSql = new ArrayList<>();
        for (long propertyId : ctx.databaseCaching().getProperties().keySet()) {
            if (syncData.genesisIds.remove(propertyId)) {
                updateSql.add(propertyId);
            }
        }
        try (var connection = SqlConnectionManager.getInstance().take();
             PreparedStatement statement = connection.prepareStatement("UPDATE node_properties SET in_genesis = 1 WHERE property_id = ?;")) {
            for (long next : updateSql) {
                statement.setLong(1, next);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        state = State.SYNCHRONIZE;
        if (syncData.lastUser == null) { // There was no cached user.
            state = State.SELECT;
        } else if (syncData.wakeUpAt != null) { // Property sync is sleeping, check if ready to wake up.
            Instant now = Instant.now();
            if (now.isAfter(syncData.wakeUpAt)) { // Ready to wakeup.
                syncData.reset();
                state = State.SELECT;
            } else { // Resume idle state.
                long hours = now.until(syncData.wakeUpAt, ChronoUnit.HOURS);
                long minutes = now.until(syncData.wakeUpAt, ChronoUnit.MINUTES) % 60;
                state = State.IDLE;
                logger.info("Resuming IDLE state for {}h{}m.", hours, minutes);
            }
        } else { // Resume previous synchronization state.
            long memberId = ctx.databaseCaching().getMemberNames().inverse().get(syncData.lastUser.getOwnerUsername());
            UpaMember upaMember = ctx.databaseCaching().getMembers().get(memberId);
            if (upaMember == null || !upaMember.getActive().get()) {
                state = State.SELECT;
                syncData.reset();
            } else {
                int propertyCount = syncData.lastUser.getProperties().size();
                logger.info("Resuming sync for @{} ({} properties; Est. time {} minutes).", upaMember.getDiscordName(), propertyCount, TimeUnit.SECONDS.toMinutes((15L * (propertyCount / 5))));
            }
        }
        ctx.save(CURRENT_SYNC_FILE, syncData);
    }

    @Override
    protected void runOneIteration() throws Exception {
        try {
            switch (state) {
                case SELECT:
                    selectNextUser();
                    break;
                case SYNCHRONIZE:
                    synchronizeUser();
                    break;
                case VERIFY:
                    verifyUserMembership();
                    break;
                case IDLE:
                    resumeSyncRef.set(ctx.discord().schedule(() -> {
                        idleMonitor.arrive();
                        resumeSyncRef.set(null);
                        return null;
                    }, 4, TimeUnit.HOURS));
                    idleMonitor.arriveAndAwaitAdvance();
                    logger.info("Property synchronization service waking up!");
                    syncData.reset();
                    state = State.SELECT;
                    break;
            }
        } catch (Exception e) {
            logger.warn("Error synchronizing properties.", e);
        }
    }

    @Override

    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(15, 15, TimeUnit.SECONDS);
    }

    public void wakeUp() {
        resumeSyncRef.updateAndGet(resumeSync -> {
            if (state == State.IDLE && resumeSync != null && resumeSync.cancel(false)) {
                idleMonitor.arrive();
            }
            return null;
        });
    }

    private void selectNextUser() throws Exception {
        DatabaseCachingService databaseCaching = ctx.databaseCaching();
        for (UpaMember upaMember : databaseCaching.getMembers().values()) {
            if (!upaMember.getActive().get()) {
                continue;
            }
            if (!upaMember.getSync().get()) {
                try {
                    Profile nextProfile = ProfileDataFetcher.fetchProfileSynchronous(upaMember.getInGameName());
                    if (nextProfile == DUMMY) {
                        continue;
                    }
                    if (nextProfile.getOwnerUsername().equals("unruly_cj")) {
                        ctx.discord().cjNetworth = nextProfile.getNetWorth();
                    } else if (nextProfile.getOwnerUsername().equals("highroad")) {
                        ctx.discord().highroadNetworth = nextProfile.getNetWorth();
                    }
                    if (nextProfile.getNetWorth() < 100_000) {
                        ctx.discord().guild().retrieveMemberById(upaMember.getMemberId()).queue(success -> {
                            ctx.discord().guild().addRoleToMember(UserSnowflake.fromId(success.getIdLong()),
                                    ctx.discord().guild().getRoleById(982481423581716532L)).queue();
                        });
                    } else {
                        ctx.discord().guild().retrieveMemberById(upaMember.getMemberId()).queue(success -> {
                            ctx.discord().guild().removeRoleFromMember(UserSnowflake.fromId(success.getIdLong()),
                                    ctx.discord().guild().getRoleById(982481423581716532L)).queue();
                        });
                    }
                    // Next member to sync was found.
                    int propertyCount = nextProfile.getProperties().size();
                    logger.info("Starting property synchronization for @{} (" + propertyCount + " properties; Est. time " + TimeUnit.SECONDS.toMinutes((15L * (propertyCount / 5))) + " minutes).", upaMember.getDiscordName());
                    syncData.lastUser = nextProfile;
                    ctx.save(CURRENT_SYNC_FILE, syncData);
                    state = State.SYNCHRONIZE;
                } catch (Exception e) {
                    logger.catching(e);
                }
                return;
            }
        }

        // No members were found. Reset and try again later.
        try (Connection connection = SqlConnectionManager.getInstance().take();
             PreparedStatement statement = connection.prepareStatement("UPDATE members SET sync = 0;")) {
            if (statement.executeUpdate() > 0) {
                databaseCaching.getMembers().values().stream().filter(next -> next.getActive().get()).forEach(next -> next.getSync().set(false));
                state = State.IDLE;
                syncData.wakeUpAt = Instant.now().plus(6, ChronoUnit.HOURS);
                logger.info("No members require synchronization, going into idle mode.");
            }
        }
    }

    public boolean isIdle() {
        return state == State.IDLE;
    }

    private void synchronizeUser() throws Exception {
        if (syncData.lastUser == null) {
            return;
        }
        DatabaseCachingService databaseCaching = ctx.databaseCaching();
        long memberId = databaseCaching.getMemberNames().inverse().get(syncData.lastUser.getOwnerUsername());
        UpaMember upaMember = databaseCaching.getMembers().get(memberId);
        if (upaMember == null || !upaMember.getActive().get()) {
            syncData.reset();
            state = State.SELECT;
            return;
        }
        Set<UpaProperty> cachedUserProperties = databaseCaching.getMemberProperties().get(upaMember.getKey());
        boolean completed = true;
        synchronized (databaseCaching.getMemberProperties()) {
            int total = 0;

            // Analyze for possible removals (contained in DB, but not in-game).
            for (UpaProperty upaProperty : cachedUserProperties) {
                if (total >= MAX_REQUESTS) {
                    completed = false;
                    break;
                }
                int memberKey = upaProperty.getMemberKey();
                long propertyId = upaProperty.getPropertyId();
                if (!syncData.lastUser.getProperties().contains(propertyId) && upaMember.getKey() == memberKey) {
                    removals.add(upaProperty);
                    total++;
                }
            }

            // Analyze for possible additions (not in DB, but owned in-game).
            Set<Long> cachedUserPropertyIds = cachedUserProperties.stream().map(UpaProperty::getPropertyId).collect(Collectors.toSet());
            for (long propertyId : syncData.lastUser.getProperties()) {
                if (total >= MAX_REQUESTS) {
                    completed = false;
                    break;
                }
                if (databaseCaching.getProperties().containsKey(propertyId)) {
                    continue;
                }
                if (!cachedUserPropertyIds.contains(propertyId)) {
                    CachedProperty cachedProperty = databaseCaching.getPropertyLookup().get(propertyId);
                    additions.add(new NodeProperty(propertyId, upaMember.getKey(), cachedProperty));
                    if (cachedProperty == null) {
                        total++;
                    }
                }
            }
        }
        int removalSize = removals.size();
        if (removalSize > 0) {
            logger.info("Processing {} unowned node properties.", removalSize);
            try (Connection connection = SqlConnectionManager.getInstance().take();
                 PreparedStatement deleteUnowned = connection.prepareStatement("DELETE FROM node_properties WHERE " +
                         "property_id = ?;")) {
                Iterator<UpaProperty> removalsIterator = removals.iterator();
                while (removalsIterator.hasNext()) {
                    UpaProperty property = removalsIterator.next();
                    removalsIterator.remove();
                    databaseCaching.getMemberProperties().remove(property.getMemberKey(), property);
                    databaseCaching.getProperties().remove(property.getPropertyId());
                    upaMember.getTotalUp2().addAndGet(-property.getUp2());
                    deleteUnowned.setLong(1, property.getPropertyId());
                    deleteUnowned.addBatch();
                }
                deleteUnowned.executeBatch();
            }
        }
        if (additions.size() > 0) {
            Set<Long> mintedByOwner = new HashSet<>();
            List<UpaProperty> newProperties = new ArrayList<>();

            Iterator<NodeProperty> additionsIterator = additions.iterator();
            while (additionsIterator.hasNext()) {
                NodeProperty next = additionsIterator.next();
                additionsIterator.remove();
                CachedProperty cachedProperty = next.cachedProperty;
                Property fetchedProperty = null;
                Neighborhood neighborhood;
                String address;
                long propId;
                String buildStatus = null;
                int area;
                if (cachedProperty == null || cachedProperty.getNeighborhoodId() < 1) {
                    fetchedProperty = PropertyDataFetcher.fetchPropertySynchronous(next.propertyId);
                    if (fetchedProperty == null) {
                        continue;
                    }
                    neighborhood = getNeighborhood(fetchedProperty);
                    address = fetchedProperty.getFullAddress();
                    propId = fetchedProperty.getPropId();
                    buildStatus = fetchedProperty.getBuildStatus();
                    area = fetchedProperty.getArea();
                    PropertyCachingMicroService.addCachedProperty(ctx, fetchedProperty);
                } else {
                    neighborhood = DataFetcherManager.getNeighborhoodMap().get(cachedProperty.getNeighborhoodId());
                    address = cachedProperty.getAddress();
                    propId = cachedProperty.getPropertyId();
                    area = cachedProperty.getArea();
                }
                Node node = Node.getNode(neighborhood);
                if (node != null) {
                    if (fetchedProperty == null) {
                        fetchedProperty = PropertyDataFetcher.fetchPropertySynchronous(next.propertyId);
                        buildStatus = fetchedProperty.getBuildStatus();
                    }
                    if (fetchedProperty.isMintedByOwner()) {
                        mintedByOwner.add(fetchedProperty.getPropId());
                    }
                    if (buildStatus == null) {
                        buildStatus = "Not started";
                    }
                    if (address == null) {
                        logger.error("Address is null! [mem_key={},name={},prop_id={},build_status={},node={},area={},genesis=false,active=true]",
                                upaMember.getKey(), upaMember.getDiscordName(), propId, buildStatus, node, area);
                        continue;
                    }
                    newProperties.add(new UpaProperty(upaMember.getKey(), address, propId, buildStatus, node, area, false, true));
                }
            }
            if (newProperties.size() > 0) {
                logger.info("Processing {} new node properties.", newProperties.size());

                int minted = 0;
                try (Connection connection = SqlConnectionManager.getInstance().take();
                     PreparedStatement insertProperty = connection.prepareStatement("INSERT INTO node_properties (member_key, address, property_id, build_status, node, size, in_genesis) VALUES (?, ?, ?, ?, ?, ?, ?);");
                     PreparedStatement updateMints = connection.prepareStatement("UPDATE members SET minted = minted + ? WHERE member_id = ?;")) {
                    List<UpaProperty> properties = new ArrayList<>();
                    for (UpaProperty property : newProperties) {
                        insertProperty.setInt(1, property.getMemberKey());
                        insertProperty.setString(2, property.getAddress());
                        insertProperty.setLong(3, property.getPropertyId());
                        insertProperty.setString(4, property.getBuildStatus().get());
                        insertProperty.setString(5, property.getNode().name());
                        insertProperty.setInt(6, property.getUp2());
                        insertProperty.setBoolean(7, syncData.genesisIds.remove(property.getPropertyId()));
                        insertProperty.addBatch();
                        if (mintedByOwner.contains(property.getPropertyId())) {
                            minted++;
                        }
                        properties.add(property);
                    }
                    if (insertProperty.executeBatch().length == newProperties.size()) {
                        for (UpaProperty upaProperty : properties) {
                            databaseCaching.getMemberProperties().put(upaProperty.getMemberKey(), upaProperty);
                            databaseCaching.getProperties().put(upaProperty.getPropertyId(), upaProperty);
                            upaMember.getTotalUp2().addAndGet(upaProperty.getUp2());
                        }
                        if (minted > 0) {
                            upaMember.getMinted().addAndGet(minted);
                            ctx.discord().sendCredit(new CreditTransaction(upaMember, minted * 200, CreditTransactionType.MINTED, String.valueOf(minted)));
                        }
                        updateMints.setInt(1, minted);
                        updateMints.setLong(2, memberId);
                        updateMints.executeUpdate();
                    }
                }
            }
        }
        if (completed) {
            try (Connection connection = SqlConnectionManager.getInstance().take();
                 PreparedStatement ps = connection.prepareStatement("UPDATE members SET sync = 1 WHERE member_id = ?;")) {
                ps.setLong(1, memberId);
                if (ps.executeUpdate() == 1) {
                    upaMember.getSync().set(true);
                }
            }
            state = State.VERIFY;
        }
        ctx.save(CURRENT_SYNC_FILE, syncData);
    }


    private void verifyUserMembership() throws Exception {
        try {
            DatabaseCachingService databaseCaching = ctx.databaseCaching();
            long memberId = databaseCaching.getMemberNames().inverse().get(syncData.lastUser.getOwnerUsername());
            UpaMember upaMember = databaseCaching.getMembers().get(memberId);
            if (upaMember == null || !upaMember.getActive().get()) {
                return;
            }
            long hollisPropertyCount = databaseCaching.getMemberProperties().get(upaMember.getKey()).stream().filter(next -> next.getNode() == Node.HOLLIS).count();
            long sunrisePropertyCount = databaseCaching.getMemberProperties().get(upaMember.getKey()).stream().filter(next -> next.getNode() == Node.SUNRISE).count();
            Guild guild = ctx.discord().guild();
            Role hollisMemberRole = guild.getRoleById(956793551230996502L);
            Role sunriseMemberRole = guild.getRoleById(1049704843528388608L);

            if (hollisPropertyCount == 0) {
                guild.removeRoleFromMember(UserSnowflake.fromId(memberId), hollisMemberRole).queue();
            } else {
                guild.addRoleToMember(UserSnowflake.fromId(memberId), hollisMemberRole).queue();
            }
            if (sunrisePropertyCount == 0) {
                guild.removeRoleFromMember(UserSnowflake.fromId(memberId), sunriseMemberRole).queue();
            } else {
                guild.addRoleToMember(UserSnowflake.fromId(memberId), sunriseMemberRole).queue();
            }
            logger.info("Finished property synchronization for @{}.", upaMember.getDiscordName());
        } finally {
            syncData.reset();
            state = State.SELECT;
        }
    }

    /**
     * Checks if {@code property} is within the neighborhood represented by {@code neighborhoodName}.
     *
     * @param property The property.
     * @return If the property is within the neighborhood.
     */
    public static List<Neighborhood> getNeighborhoods(Property property) {
        if (property == null) {
            return List.of();
        }
        List<Neighborhood> neighborhoods = new ArrayList<>();
        List<Neighborhood> potentialNeighborhoods = DataFetcherManager.getNeighborhoods(property.getCityId());
        for (Neighborhood neighborhood : potentialNeighborhoods) {
            if (neighborhood == null) {
                continue;
            }
            List<Double> neighborhoodX = new ArrayList<>();
            List<Double> neighborhoodY = new ArrayList<>();
            List<Double> propertyX = new ArrayList<>();
            List<Double> propertyY = new ArrayList<>();
            for (int index = 0; index < neighborhood.getNeighborhoodArea().length; index++) {
                neighborhoodX.add(neighborhood.getNeighborhoodArea()[index][0]);
                neighborhoodY.add(neighborhood.getNeighborhoodArea()[index][1]);
            }
            for (int index = 0; index < property.getCoordinates().length; index++) {
                propertyX.add(property.getCoordinates()[index][0]);
                propertyY.add(property.getCoordinates()[index][1]);
            }

            Path2D neighborhoodPath = new Path2D.Double();
            Path2D propertyPath = new Path2D.Double();
            neighborhoodPath.moveTo(neighborhoodX.isEmpty() ? 0 : neighborhoodX.get(0), neighborhoodY.isEmpty() ? 0 : neighborhoodY.get(0));
            for (int i = 1; i < neighborhoodX.size(); ++i) {
                neighborhoodPath.lineTo(neighborhoodX.get(i), neighborhoodY.get(i));
            }
            neighborhoodPath.closePath();
            if (propertyX.isEmpty() || propertyY.isEmpty())
                continue;
            propertyPath.moveTo(propertyX.get(0), propertyY.get(0));
            for (int i = 1; i < propertyX.size(); ++i) {
                propertyPath.lineTo(propertyX.get(i), propertyY.get(i));
            }
            propertyPath.closePath();
            if (neighborhoodPath.contains(propertyPath.getCurrentPoint())) {
                neighborhoods.add(neighborhood);
            }
        }
        return neighborhoods;
    }

    /**
     * Checks if {@code property} is within the neighborhood represented by {@code neighborhoodName}.
     *
     * @param property The property.
     * @return If the property is within the neighborhood.
     */
    public static Neighborhood getNeighborhood(Property property) {
        return getNeighborhoods(property).stream().findFirst().orElse(null);
    }

}