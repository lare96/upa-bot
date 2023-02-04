package me.upa.service;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.util.concurrent.AbstractIdleService;
import me.upa.UpaBotContext;
import me.upa.discord.PacHistoryStatement;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaPoolProperty;
import me.upa.discord.UpaProperty;
import me.upa.fetcher.CityDataFetcher;
import me.upa.fetcher.CollectionDataFetcher;
import me.upa.fetcher.NeighborhoodDataFetcher;
import me.upa.game.CachedProperty;
import me.upa.game.Node;
import me.upa.game.Profile;
import me.upa.sql.SqlConnectionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.utils.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caches basic static member and property data.
 *
 * @author lare96
 */
public final class DatabaseCachingService extends AbstractIdleService {

    private static final Path PROFILE_PATH = Paths.get("data", "profiles.bin");

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    private final UpaBotContext ctx;

    /**
     * The cached members (member_id -> UpaMember).
     */
    private final Map<Long, UpaMember> members = new ConcurrentHashMap<>(100);

    /**
     * The cached members (member_id -><- in_game_name).
     */
    private final BiMap<Long, String> memberNames = Maps.synchronizedBiMap(HashBiMap.create());

    /**
     * The cached properties (property_id -> UpaProperty).
     */
    private final Map<Long, UpaProperty> properties = new ConcurrentHashMap<>(5000);

    /**
     * The cached members to properties (member_key -> Set[UpaProperty]).
     */
    private final SetMultimap<Integer, UpaProperty> memberProperties = Multimaps.synchronizedSetMultimap(HashMultimap.create());

    /**
     * The cached lookup properties.
     */
    private final Map<Long, CachedProperty> propertyLookup = new ConcurrentHashMap<>();

    /**
     * The cached property construction status (address -> build status).
     */
    private final Map<String, String> constructionStatus = new ConcurrentHashMap<>();

    /**
     * The cached UPA pool properties.
     */
    private final Map<Long, UpaPoolProperty> poolProperties = new ConcurrentHashMap<>();

    /**
     * The total PAC in circulation.
     */
    private final AtomicLong totalPac = new AtomicLong();

    private final Map<Long, Profile> profiles = new ConcurrentHashMap<>();

    private final ListMultimap<Long, PacHistoryStatement> pacHistory = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());

    public DatabaseCachingService(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    protected void startUp() throws Exception {
        // Load neighborhoods anad cities.
        CityDataFetcher cityFetcher = new CityDataFetcher();
        NeighborhoodDataFetcher neighborhoodFetcher = new NeighborhoodDataFetcher();
        CollectionDataFetcher collectionFetcher = new CollectionDataFetcher();
        cityFetcher.waitUntilDone();
        neighborhoodFetcher.waitUntilDone();
        collectionFetcher.waitUntilDone();

        Map<Long, Profile> loadedProfiles = ctx.load(PROFILE_PATH);
        if (loadedProfiles != null) {
            profiles.putAll(loadedProfiles);
        }

        try (Connection connection = SqlConnectionManager.getInstance().take()) {
            Map<Integer, UpaMember> memberKeys = new HashMap<>();

            // All linked Discord members.
            Set<Long> memberIds = new HashSet<>();
            try (PreparedStatement selectMembers = connection.prepareStatement("SELECT `key`, member_id, in_game_name, blockchain_account_id, networth, credit, hollis_ssh, global_ssh, sends, sponsored_sends, referrals, minted, claimed_daily_at, sync, active, join_date FROM members;");
                 ResultSet memberResults = selectMembers.executeQuery()) {
                while (memberResults.next()) {
                    int memberKey = memberResults.getInt(1);
                    long memberId = memberResults.getLong(2);
                    String inGameName = memberResults.getString(3);
                    String blockchainAccountId = memberResults.getString(4);
                    int netWorth = memberResults.getInt(5);
                    int credit = memberResults.getInt(6);
                    int ssh = memberResults.getInt(7);
                    int globalSsh = memberResults.getInt(8);
                    int sends = memberResults.getInt(9);
                    int sponsoredSends = memberResults.getInt(10);
                    int referrals = memberResults.getInt(11);
                    int minted = memberResults.getInt(12);
                    Instant claimedDailyAt = Instant.parse(memberResults.getString(13));
                    boolean sync = memberResults.getBoolean(14);
                    boolean active = memberResults.getBoolean(15);
                    LocalDate joinDate = memberResults.getDate(16).toLocalDate();
                    var upaMember = new UpaMember(memberKey, memberId, inGameName, "<not_yet_loaded>", blockchainAccountId, netWorth, credit, ssh, globalSsh, sends, sponsoredSends, referrals, minted, claimedDailyAt, sync, active, joinDate);
                    memberIds.add(memberId);
                    members.put(memberId, upaMember);
                    memberNames.put(memberId, inGameName);
                    memberKeys.put(memberKey, upaMember);
                }
            }

            // Cache discord names.
            while(!memberIds.isEmpty()) {
                Set<Long> shortMemberIds = new HashSet<>();
                for (Iterator<Long> memberIdsIterator = memberIds.iterator();memberIdsIterator.hasNext();) {
                    shortMemberIds.add(memberIdsIterator.next());
                    memberIdsIterator.remove();
                    if(shortMemberIds.size() == 100) {
                        break;
                    }
                }
                Task<List<Member>> retrieveMembersTask = ctx.discord().guild().retrieveMembersByIds(shortMemberIds);
                List<Member> discordUpaMembers = retrieveMembersTask.get();
                for (Member nextMember : discordUpaMembers) {
                    UpaMember upaMember = members.get(nextMember.getIdLong());
                    upaMember.getDiscordName().set(nextMember.getEffectiveName());
                }
            }

            // Unlink any members that have left.
            Set<UpaMember> unlink = new HashSet<>();
            for (UpaMember upaMember : members.values()) {
                if (upaMember.getDiscordName().get().equals("<not_yet_loaded>") && upaMember.getActive().get()) {
                    unlink.add(upaMember);
                } else {
                    totalPac.addAndGet(upaMember.getCredit().get());
                }
            }
            int unlinkSize = unlink.size();
            if (unlinkSize > 0) {
                try (PreparedStatement setInactive = connection.prepareStatement("UPDATE members SET active = 0 WHERE `key` = ?;");
                     PreparedStatement setPropertiesInactive = connection.prepareStatement("UPDATE node_properties SET active = 0 WHERE `member_key` = ?;")) {
                    for (UpaMember upaMember : unlink) {
                        setInactive.setInt(1, upaMember.getKey());
                        setInactive.addBatch();
                        setPropertiesInactive.setInt(1, upaMember.getKey());
                        setPropertiesInactive.addBatch();
                        upaMember.getActive().set(false);
                        ctx.databaseCaching().getMemberProperties().get(upaMember.getKey()).forEach(property -> property.getActive().set(false));
                    }
                    setInactive.executeBatch();
                    setPropertiesInactive.executeBatch();
                    logger.info("Unlinked {} members.", unlinkSize);
                }
            }
            logger.info("Loaded {} members.", members.size());

            // All linked node properties.
            try (PreparedStatement selectProperties = connection.prepareStatement("SELECT member_key, address, property_id, build_status, node, size, in_genesis, active FROM node_properties;");
                 ResultSet propertyResults = selectProperties.executeQuery()) {
                while (propertyResults.next()) {
                    int memberKey = propertyResults.getInt(1);
                    String address = propertyResults.getString(2);
                    long propertyId = propertyResults.getLong(3);
                    String buildStatus = propertyResults.getString(4);
                    Node node = Node.valueOf(propertyResults.getString(5));
                    int size = propertyResults.getInt(6);
                    boolean inGenesis = propertyResults.getBoolean(7);
                    boolean active = propertyResults.getBoolean(8);
                    var upaProperty = new UpaProperty(memberKey, address, propertyId, buildStatus, node, size, inGenesis, active);
                    properties.put(propertyId, upaProperty);
                    memberProperties.put(memberKey, upaProperty);
                    constructionStatus.put(address, buildStatus);

                    UpaMember upaMember = memberKeys.get(memberKey);
                    if (upaMember != null) {
                        upaMember.getTotalUp2().addAndGet(size);
                    }
                }
            }
            logger.info("Loaded {} properties.", properties.size());

            // All property lookups.
            try (PreparedStatement selectProperties = connection.prepareStatement("SELECT property_id, address, area, neighborhood_id, city_id, mint_price FROM property_lookup;");
                 ResultSet propertyLookupResults = selectProperties.executeQuery()) {
                while (propertyLookupResults.next()) {
                    long propertyId = propertyLookupResults.getLong(1);
                    String address = propertyLookupResults.getString(2);
                    int area = propertyLookupResults.getInt(3);
                    int neighborhoodId = propertyLookupResults.getInt(4);
                    int cityId = propertyLookupResults.getInt(5);
                    long mintPrice = propertyLookupResults.getLong(6);
                    propertyLookup.put(propertyId, new CachedProperty(propertyId, address, area, neighborhoodId, cityId, mintPrice));
                }
            }
            logger.info("Loaded {} property lookups.", propertyLookup.size());

            try (PreparedStatement selectPoolProperties = connection.prepareStatement("SELECT * FROM pool_properties;");
                 ResultSet poolPropertyResults = selectPoolProperties.executeQuery()) {
                while (poolPropertyResults.next()) {
                    long propertyId = poolPropertyResults.getLong(1);
                    String address = poolPropertyResults.getString(2);
                    String cityName = poolPropertyResults.getString(3);
                    int mintPrice = poolPropertyResults.getInt(4);
                    int up2 = poolPropertyResults.getInt(5);
                    long donorMemberId = poolPropertyResults.getLong(6);
                    boolean verified = poolPropertyResults.getBoolean(7);
                    int cost = poolPropertyResults.getInt(8);
                    LocalDate listedOn = poolPropertyResults.getDate(9).toLocalDate();
                    UpaPoolProperty poolProperty = new UpaPoolProperty(propertyId, address, cityName, mintPrice, cost, up2, donorMemberId, listedOn);
                    poolProperty.getVerified().set(verified);
                    poolProperties.put(propertyId, poolProperty);
                }
            }
        }
    }

    @Override
    protected void shutDown() throws Exception {
        logger.warn("Database caching service shutting down...");
        members.clear();
        memberNames.clear();
        properties.clear();
        propertyLookup.clear();
        constructionStatus.clear();
    }

    public Map<Long, UpaMember> getMembers() {
        return members;
    }

    public BiMap<Long, String> getMemberNames() {
        return memberNames;
    }

    public Map<Long, UpaProperty> getProperties() {
        return properties;
    }

    public SetMultimap<Integer, UpaProperty> getMemberProperties() {
        return memberProperties;
    }

    public Map<Long, CachedProperty> getPropertyLookup() {
        return propertyLookup;
    }

    public Map<String, String> getConstructionStatus() {
        return constructionStatus;
    }

    public Map<Long, UpaPoolProperty> getPoolProperties() {
        return poolProperties;
    }

    public AtomicLong getTotalPac() {
        return totalPac;
    }

    public Map<Long, Profile> getProfiles() {
        return profiles;
    }
}
