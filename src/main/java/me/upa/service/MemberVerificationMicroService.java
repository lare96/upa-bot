package me.upa.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.upa.UpaBot;
import me.upa.UpaBotContext;
import me.upa.discord.CreditTransaction;
import me.upa.discord.CreditTransaction.CreditTransactionType;
import me.upa.discord.PendingUpaMember;
import me.upa.discord.SparkTrainMessageListener;
import me.upa.discord.UpaMember;
import me.upa.fetcher.PropertyDataFetcher;
import me.upa.fetcher.UserPropertiesDataFetcher;
import me.upa.fetcher.UserPropertiesDataFetcher.UserProperty;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class MemberVerificationMicroService extends MicroService {

    private final UpaBotContext ctx;

    public MemberVerificationMicroService(UpaBotContext ctx) {
        super(Duration.ofMinutes(1));
        this.ctx = ctx;
    }

    private final class AddMemberTask extends SqlTask<UpaMember> {

        private final long memberId;
        private final String inGameName;
        private final String blockchainAccountId;

        private AddMemberTask(long memberId, String inGameName, String blockchainAccountId) {
            this.memberId = memberId;
            this.inGameName = inGameName;
            this.blockchainAccountId = blockchainAccountId;
        }

        @Override
        public UpaMember execute(Connection connection) throws Exception {
            try {
                connection.setAutoCommit(false);
                int memberKey = -1;
                Instant claimedDailyAt = Instant.now().minus(1, ChronoUnit.DAYS);
                LocalDate now = LocalDate.now();
                try (PreparedStatement insertMember = connection.prepareStatement("INSERT INTO members (member_id, in_game_name, blockchain_account_id, claimed_daily_at, join_date) VALUES (?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insertMember.setLong(1, memberId);
                    insertMember.setString(2, inGameName);
                    insertMember.setString(3, blockchainAccountId);
                    insertMember.setString(4, claimedDailyAt.toString());
                    insertMember.setDate(5, Date.valueOf(now));
                    insertMember.executeUpdate();
                    try (var results = insertMember.getGeneratedKeys()) {
                        if (results.next()) {
                            memberKey = results.getInt(1);
                        }
                    }
                    if (memberKey == -1) {
                        connection.rollback();
                        throw new RuntimeException("Could not insert new member " + inGameName);
                    }
                }
                connection.commit();
                return new UpaMember(memberKey, memberId, inGameName, ctx.discord().guild().retrieveMemberById(memberId).complete().getEffectiveName(), blockchainAccountId, 0, 0, 0, 0, 0, 0, 0, claimedDailyAt, false, now);
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static final Logger logger = LogManager.getLogger();

    private final Cache<Long, PendingUpaMember> pendingRegistrations = CacheBuilder.newBuilder().
            expireAfterWrite(1, TimeUnit.HOURS).build();
    private final Queue<Long> removals = new ConcurrentLinkedQueue<>();

    @Override
    public void run() throws Exception {
        try {
            DatabaseCachingService databaseCaching = ctx.databaseCaching();

            for (Entry<Long, PendingUpaMember> entry : pendingRegistrations.asMap().entrySet()) {
                PendingUpaMember nextMember = entry.getValue();
                long memberId = entry.getKey();
                var userPropertiesFetcher = new UserPropertiesDataFetcher(nextMember.getUsername());
                Map<Long, UserProperty> userProperties = null;
                try {
                    userPropertiesFetcher.fetch();
                    userProperties = userPropertiesFetcher.waitUntilDone();
                } catch (Exception e) {
                    logger.warn("Error while fetching user properties.", e);
                }
                if (userProperties == null || userProperties.isEmpty()) {
                    logger.warn("Could not find any properties for username {}.", nextMember.getUsername());
                    continue;
                }
                for (UserProperty property : userProperties.values()) {
                    if (SparkTrainMessageListener.BLACKLISTED.contains(property.getOwnerUsername())) {
                        removals.add(memberId);
                        continue;
                    }
                    if ((property.getStatus().equals("For sale") && property.getUpxSalePrice() == nextMember.getPrice()) || nextMember.getPrice() == -1) {
                        removals.add(memberId);
                        PropertyDataFetcher.fetchProperty(property.getPropId(), morePropertyData ->
                                SqlConnectionManager.getInstance().execute(new AddMemberTask(memberId, property.getOwnerUsername(), morePropertyData.getOwner()),
                                        success -> {
                                            if (nextMember.getPrice() != -1) {
                                                nextMember.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("Successfully linked '" + property.getOwnerUsername() + "' with UPA! Your node properties will begin synchronizing shortly.").queue());
                                            }
                                            databaseCaching.getMembers().put(memberId, success);
                                            databaseCaching.getMemberNames().put(memberId, property.getOwnerUsername());
                                            var guild = ctx.discord().guild();
                                            guild.getTextChannelById(956790034097373204L).sendMessage("Welcome to the newest UPA member <@" + memberId + ">! We are now at **" + databaseCaching.getMembers().size() + "** members.").queue();
                                            guild.retrieveMemberById(memberId).queue(loadedMember -> guild.addRoleToMember(UserSnowflake.fromId(memberId), guild.getRoleById(999013596111581284L)).queue());
                                            ctx.propertySync().wakeUp();
                                        },
                                        failure -> {
                                            if (nextMember.getPrice() != -1) {
                                                nextMember.getMember().getUser().openPrivateChannel().queue(channel -> channel.sendMessage("Failed to link account with UPA. Please contact unruly_cj.").queue());
                                            }
                                            logger.error(new ParameterizedMessage("Failed to link {} -> {}.", nextMember.getMember().getNickname(), property.getOwnerUsername()), failure);
                                        }));
                        break;
                    }
                }
            }
            for (; ; ) {
                Long nextRemoval = removals.poll();
                if (nextRemoval == null) {
                    break;
                }
                pendingRegistrations.invalidate(nextRemoval);
            }
        } catch (Exception e) {
            logger.catching(e);
        }
    }

    public Cache<Long, PendingUpaMember> getPendingRegistrations() {
        return pendingRegistrations;
    }
}
