package me.upa.service;

import com.fasterxml.jackson.databind.ser.std.StdArraySerializers.IntArraySerializer;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AbstractIdleService;
import me.upa.UpaBot;
import me.upa.discord.Scholar;
import me.upa.discord.SendStormEvent;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaPoolProperty;
import me.upa.discord.UpaProperty;
import me.upa.sql.SqlConnectionManager;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.utils.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches basic static member and property data.
 *
 * @author lare96
 */
public final class DatabaseCachingService extends AbstractIdleService {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

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
     * The cached scholars (member_id -> Scholar).
     */
    private final Map<Long, Scholar> scholars = new ConcurrentHashMap<>();

    /**
     * The cached lookup properties.
     */
    private final Set<Long> propertyLookup = ConcurrentHashMap.newKeySet(10_000);

    /**
     * The cached property construction status (address -> build status).
     */
    private final Map<String, String> constructionStatus = new ConcurrentHashMap<>();

    /**
     * The cached UPA pool properties.
     */
    private final Map<Long, UpaPoolProperty> poolProperties = new ConcurrentHashMap<>();

    @Override
    protected void startUp() throws Exception {
        try (Connection connection = SqlConnectionManager.getInstance().take()) {
            Map<Integer, UpaMember> memberKeys = new HashMap<>();

            // All linked Discord members.
            Set<Long> memberIds = new HashSet<>();
            try (PreparedStatement selectMembers = connection.prepareStatement("SELECT `key`, member_id, in_game_name, blockchain_account_id, networth, credit, ssh, sends, sponsored_sends, referrals, claimed_daily_at, sync, join_date FROM members;");
                 ResultSet memberResults = selectMembers.executeQuery()) {
                while (memberResults.next()) {
                    int memberKey = memberResults.getInt(1);
                    long memberId = memberResults.getLong(2);
                    String inGameName = memberResults.getString(3);
                    String blockchainAccountId = memberResults.getString(4);
                    int netWorth = memberResults.getInt(5);
                    int credit = memberResults.getInt(6);
                    int ssh = memberResults.getInt(7);
                    int sends = memberResults.getInt(8);
                    int sponsoredSends = memberResults.getInt(9);
                    int referrals = memberResults.getInt(10);
                    Instant claimedDailyAt = Instant.parse(memberResults.getString(11));
                    boolean sync = memberResults.getBoolean(12);
                    LocalDate joinDate = memberResults.getDate(13).toLocalDate();
                    var upaMember = new UpaMember(memberKey, memberId, inGameName, "<not_yet_loaded>", blockchainAccountId, netWorth, credit, ssh, sends, sponsoredSends, referrals, claimedDailyAt, sync, joinDate);
                    memberIds.add(Long.valueOf(memberId));
                    members.put(memberId, upaMember);
                    memberNames.put(memberId, inGameName);
                    memberKeys.put(memberKey, upaMember);
                }
            }
            // Cache discord names.
            Task<List<Member>> retrieveMembersTask = UpaBot.getDiscordService().guild().retrieveMembersByIds(memberIds);
            List<Member> discordUpaMembers = retrieveMembersTask.get(); // TODO async, don't block db
            for (Member nextMember : discordUpaMembers) {
                UpaMember upaMember = members.get(nextMember.getIdLong());
                upaMember.getDiscordName().set(nextMember.getEffectiveName());
            }

            // Unlink any members that have left.
            Set<UpaMember> unlink = new HashSet<>();
            for (UpaMember upaMember : members.values()) {
                if (upaMember.getDiscordName().get().equals("<not_yet_loaded>")) {
                    unlink.add(upaMember);
                }
            }
            if (unlink.size() > 0) {
                try (PreparedStatement unlinkMembers = connection.prepareStatement("DELETE members, node_properties FROM members INNER JOIN node_properties ON members.`key` = node_properties.member_key WHERE members.`key` = ?;")) {
                    for (UpaMember upaMember : unlink) {
                        unlinkMembers.setInt(1, upaMember.getKey());
                        unlinkMembers.addBatch();
                        members.remove(upaMember.getMemberId());
                        memberKeys.remove(upaMember.getKey());
                    }
                    logger.info("Unlinked {} members.", unlinkMembers.executeBatch().length);
                }
            }
            logger.info("Loaded {} members.", members.size());

            // All linked node properties.
            try (PreparedStatement selectProperties = connection.prepareStatement("SELECT member_key, address, property_id, build_status, node, size, in_genesis FROM node_properties;");
                 ResultSet propertyResults = selectProperties.executeQuery()) {
                while (propertyResults.next()) {
                    int memberKey = propertyResults.getInt(1);
                    String address = propertyResults.getString(2);
                    long propertyId = propertyResults.getLong(3);
                    String buildStatus = propertyResults.getString(4);
                    String node = propertyResults.getString(5);
                    int size = propertyResults.getInt(6);
                    boolean inGenesis = propertyResults.getBoolean(7);
                    var upaProperty = new UpaProperty(memberKey, address, propertyId, buildStatus, node, size, inGenesis);
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

            // All scholars.
            try (PreparedStatement selectProperties = connection.prepareStatement("SELECT username, address, property_id, discord_id, net_worth, sponsored, last_fetch FROM scholars;");
                 ResultSet propertyResults = selectProperties.executeQuery()) {
                while (propertyResults.next()) {
                    String username = propertyResults.getString(1);
                    String address = propertyResults.getString(2);
                    long propertyId = propertyResults.getLong(3);
                    long discordId = propertyResults.getLong(4);
                    int netWorth = propertyResults.getInt(5);
                    boolean sponsored = propertyResults.getBoolean(6);
                    Instant lastFetchInstant = Instant.parse(propertyResults.getString(7));

                    Scholar scholar = new Scholar(username, address, propertyId, netWorth, discordId, sponsored, lastFetchInstant);
                    scholars.put(discordId, scholar);
                    scholar.getSponsored().set(sponsored);
                }
            }
            logger.info("Loaded {} scholars.", scholars.size());

            // All property lookups.
            try (PreparedStatement selectProperties = connection.prepareStatement("SELECT property_id FROM property_lookup;");
                 ResultSet propertyLookupResults = selectProperties.executeQuery()) {
                while (propertyLookupResults.next()) {
                    propertyLookup.add(propertyLookupResults.getLong(1));
                }
            }

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
                    UpaPoolProperty poolProperty = new UpaPoolProperty(propertyId, address, cityName, mintPrice, cost, up2, donorMemberId);
                    poolProperty.getVerified().set(verified);
                    poolProperties.put(propertyId, poolProperty);
                }
            }
        }
        logger.info("Loaded {} property lookups.", propertyLookup.size());
    }

    @Override
    protected void shutDown() throws Exception {
        logger.warn("Database caching service shutting down...");
        members.clear();
        memberNames.clear();
        properties.clear();
        scholars.clear();
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

    public Map<Long, Scholar> getScholars() {
        return scholars;
    }

    public Set<Long> getPropertyLookup() {
        return propertyLookup;
    }

    public Map<String, String> getConstructionStatus() {
        return constructionStatus;
    }

    public Map<Long, UpaPoolProperty> getPoolProperties() {
        return poolProperties;
    }
}