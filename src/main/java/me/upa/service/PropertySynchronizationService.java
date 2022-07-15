package me.upa.service;

import com.google.common.base.Objects;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.Uninterruptibles;
import me.upa.UpaBot;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaProperty;
import me.upa.fetcher.DataFetcherManager;
import me.upa.fetcher.ProfileDataFetcher;
import me.upa.fetcher.PropertyDataFetcher;
import me.upa.game.Neighborhood;
import me.upa.game.Profile;
import me.upa.game.Property;
import me.upa.selector.CityPropertySelector;
import me.upa.sql.SqlConnectionManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.geom.Path2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static java.util.Objects.*;
import static me.upa.fetcher.ProfileDataFetcher.DUMMY;

public class PropertySynchronizationService extends AbstractScheduledService {

    //TODO Change 'sync' from BIT to VARCHAR (instant) in order to sync player profiles only every 12 hours
    // Then an idle state is no longer needed
    private static final int MAX_REQUESTS = 5;
    private static final Path CURRENT_SYNC_FILE = Paths.get("data", "sync.bin");

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

    private static final class NodeProperty {
        private final long propertyId;
        private final int memberKey;

        private NodeProperty(long propertyId, int memberKey) {
            this.propertyId = propertyId;
            this.memberKey = memberKey;
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
    private volatile Profile lastUser;

    private final AtomicBoolean wakeUp = new AtomicBoolean();
    private final Set<UpaProperty> removals = Sets.newConcurrentHashSet();
    private final Set<NodeProperty> additions = Sets.newConcurrentHashSet();
    private final Set<Long> lookups = Sets.newConcurrentHashSet();

    @Override
    protected void startUp() throws Exception {
        if (Files.exists(CURRENT_SYNC_FILE)) {
            lastUser = UpaBot.load(CURRENT_SYNC_FILE);
            state = State.SYNCHRONIZE;
            if (lastUser == null || lastUser.getOwnerUsername() == null) {
                lastUser = null;
                state = State.SELECT;
                return;
            } else if (lastUser.getNetWorth() == -1) {
                Instant now = Instant.now();
                Instant wakeUpAt = Instant.parse(lastUser.getOwnerUsername());
                if (now.isAfter(wakeUpAt)) {
                    lastUser = null;
                    state = State.SELECT;
                    Files.deleteIfExists(CURRENT_SYNC_FILE);
                    return;
                }
                long hours = now.until(wakeUpAt, ChronoUnit.HOURS);
                long minutes = now.until(wakeUpAt, ChronoUnit.MINUTES) % 60;
                state = State.IDLE;
                logger.info("Resuming IDLE state for {}:{}.", hours, minutes);
                return;
            }
            long memberId = UpaBot.getDatabaseCachingService().getMemberNames().inverse().get(lastUser.getOwnerUsername());
            UpaMember upaMember = UpaBot.getDatabaseCachingService().getMembers().get(memberId);
            logger.info("Resuming sync for @" + upaMember.getDiscordName());
        } else {
            state = State.SELECT;
        }
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
                    while (!wakeUp.compareAndSet(true, false)) {
                        Uninterruptibles.sleepUninterruptibly(Duration.of(1, ChronoUnit.MINUTES));
                    }
                    logger.info("Property synchronization service waking up!");
                    Files.deleteIfExists(CURRENT_SYNC_FILE);
                    lastUser = null;
                    state = State.SELECT;
                    break;
            }
        } catch (Exception e) {
            logger.warn("Error synchronizing properties.", e);
        }
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(1, 1, TimeUnit.MINUTES);
    }

    public void wakeUp() {
        if (state == State.IDLE) {
            wakeUp.set(true);
        }
    }

    private void selectNextUser() throws Exception {
        DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
        for (UpaMember upaMember : databaseCaching.getMembers().values()) {
            if (!upaMember.getSync().get()) {
                try {
                    var profileFetcher = new ProfileDataFetcher(upaMember.getInGameName());
                    Profile nextProfile = profileFetcher.waitUntilDone();
                    if (nextProfile == DUMMY) {
                        continue;
                    }
               /*     if(nextProfile.getNetWorth() < 100_000) {
                        UpaBot.getDiscordService().guild().retrieveMemberById(upaMember.getMemberId()).queue(success -> {
                            UpaBot.getDiscordService().guild().addRoleToMember(UserSnowflake.fromId(success.getIdLong()),
                                    UpaBot.getDiscordService().guild().getRoleById(875511923305754725L)).queue();
                        });
                    } else {
                        UpaBot.getDiscordService().guild().retrieveMemberById(upaMember.getMemberId()).queue(success -> {
                            UpaBot.getDiscordService().guild().removeRoleFromMember(UserSnowflake.fromId(success.getIdLong()),
                                    UpaBot.getDiscordService().guild().getRoleById(875511923305754725L)).queue();
                        });
                    }*/
                    // Next member to sync was found.
                    logger.info("Starting property synchronization for @{}.", upaMember.getDiscordName());
                    lastUser = nextProfile;
                    UpaBot.save(CURRENT_SYNC_FILE, nextProfile);
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
                databaseCaching.getMembers().values().forEach(next -> next.getSync().set(false));
                state = State.IDLE;
                UpaBot.save(CURRENT_SYNC_FILE, new Profile(Instant.now().plus(2, ChronoUnit.HOURS).toString(), -1, Set.of(), true));
                logger.info("No members require synchronization, going into idle mode.");
            }
        }
    }

    private void synchronizeUser() throws Exception {
        if (lastUser == null) {
            return;
        }
        DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
        long memberId = databaseCaching.getMemberNames().inverse().get(lastUser.getOwnerUsername());
        UpaMember upaMember = databaseCaching.getMembers().get(memberId);
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
                if (!lastUser.getProperties().contains(propertyId) && upaMember.getKey() == memberKey) {
                    removals.add(upaProperty);
                    total++;
                }
            }

            // Analyze for possible additions (not in DB, but owned in-game).
            Set<Long> cachedUserPropertyIds = cachedUserProperties.stream().map(UpaProperty::getPropertyId).collect(Collectors.toSet());
            for (long propertyId : lastUser.getProperties()) {
                if (total >= MAX_REQUESTS) {
                    completed = false;
                    break;
                }
                if (databaseCaching.getProperties().containsKey(propertyId) ||
                        databaseCaching.getPropertyLookup().contains(propertyId)) {
                    continue;
                }
                if (!cachedUserPropertyIds.contains(propertyId)) {
                    additions.add(new NodeProperty(propertyId, upaMember.getKey()));
                    total++;
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
            Map<Long, Integer> memberKeys = new HashMap<>();
            List<Property> newProperties = new ArrayList<>();

            Iterator<NodeProperty> additionsIterator = additions.iterator();
            while (additionsIterator.hasNext()) {
                NodeProperty next = additionsIterator.next();
                additionsIterator.remove();
                Property cachedProperty = PropertyDataFetcher.fetchPropertySynchronous(next.propertyId);
                if (cachedProperty == null) {
                    continue;
                }
                if (withinBounds(cachedProperty, "Hollis")) {
                    newProperties.add(cachedProperty);
                    memberKeys.put(cachedProperty.getPropId(), next.memberKey);
                } else if (databaseCaching.getPropertyLookup().add(cachedProperty.getPropId())) {
                    lookups.add(cachedProperty.getPropId());
                }
            }
            if (newProperties.size() > 0) {
                logger.info("Processing {} new node properties.", newProperties.size());

                try (Connection connection = SqlConnectionManager.getInstance().take();
                     PreparedStatement insertProperty = connection.prepareStatement("INSERT INTO node_properties (member_key, address, property_id, build_status, node, size) VALUES (?, ?, ?, ?, ?, ?);")) {
                    List<UpaProperty> properties = new ArrayList<>();
                    for (Property property : newProperties) {
                        int key = memberKeys.get(property.getPropId());
                        insertProperty.setInt(1, key);
                        insertProperty.setString(2, property.getFullAddress());
                        insertProperty.setLong(3, property.getPropId());
                        insertProperty.setString(4, property.getBuildStatus());
                        insertProperty.setString(5, "HOLLIS");
                        insertProperty.setInt(6, property.getArea());
                        insertProperty.addBatch();
                        properties.add(new UpaProperty(key, property.getFullAddress(), property.getPropId(), property.getBuildStatus(), "HOLLIS", property.getArea(), false));
                    }
                    if (insertProperty.executeBatch().length == newProperties.size()) {
                        for (UpaProperty upaProperty : properties) {
                            databaseCaching.getMemberProperties().put(upaProperty.getMemberKey(), upaProperty);
                            databaseCaching.getProperties().put(upaProperty.getPropertyId(), upaProperty);
                            upaMember.getTotalUp2().addAndGet(upaProperty.getUp2());
                        }
                    }
                }
            }
        }
        if (lookups.size() > 0) {
            logger.info("Caching {} new lookup property IDs.", lookups.size());

            try (Connection connection = SqlConnectionManager.getInstance().take();
                 PreparedStatement insertProperty = connection.prepareStatement("INSERT INTO property_lookup (property_id) VALUES (?);")) {
                Iterator<Long> lookupsIterator = lookups.iterator();
                while (lookupsIterator.hasNext()) {
                    long nextId = lookupsIterator.next();
                    lookupsIterator.remove();
                    insertProperty.setLong(1, nextId);
                    insertProperty.addBatch();
                }
                insertProperty.executeBatch();
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
    }


    private void verifyUserMembership() throws Exception {
        try {
            DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
            long memberId = databaseCaching.getMemberNames().inverse().get(lastUser.getOwnerUsername());
            UpaMember upaMember = databaseCaching.getMembers().get(memberId);
            int propertyCount = databaseCaching.getMemberProperties().get(upaMember.getKey()).size();
            Guild guild = UpaBot.getDiscordService().guild();
            Role nodeMemberRole = guild.getRoleById(956793551230996502L);

            if (propertyCount == 0) {
                guild.removeRoleFromMember(UserSnowflake.fromId(memberId), nodeMemberRole).queue();
            } else {
                guild.addRoleToMember(UserSnowflake.fromId(memberId), nodeMemberRole).queue();
            }
            logger.info("Finished property synchronization for @{}.", upaMember.getDiscordName());
        } finally {
            lastUser = null;
            Files.deleteIfExists(CURRENT_SYNC_FILE);
            state = State.SELECT;
        }
    }

    /**
     * Checks if {@code property} is within the neighborhood represented by {@code neighborhoodName}.
     *
     * @param property The property.
     * @param neighborhoodName The neighborhood name.
     * @return If the property is within the neighborhood.
     */
    public static boolean withinBounds(Property property, String neighborhoodName) {
        if (property == null || (neighborhoodName.equals("Hollis") && property.getCityId() != 4))
            return false;
        Neighborhood neighborhood = DataFetcherManager.getNeighborhoodMap().get(DataFetcherManager.getNeighborhoodId(neighborhoodName));
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
        neighborhoodPath.moveTo(neighborhoodX.get(0), neighborhoodY.get(0));
        for (int i = 1; i < neighborhoodX.size(); ++i) {
            neighborhoodPath.lineTo(neighborhoodX.get(i), neighborhoodY.get(i));
        }
        neighborhoodPath.closePath();
        propertyPath.moveTo(propertyX.get(0), propertyY.get(0));
        for (int i = 1; i < propertyX.size(); ++i) {
            propertyPath.lineTo(propertyX.get(i), propertyY.get(i));
        }
        propertyPath.closePath();
        return neighborhoodPath.contains(propertyPath.getCurrentPoint());
    }
}