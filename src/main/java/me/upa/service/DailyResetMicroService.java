package me.upa.service;

import me.upa.UpaBotConstants;
import me.upa.UpaBotContext;
import me.upa.discord.SparkTrainMessageListener;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaPoolProperty;
import me.upa.discord.event.UpaEvent;
import me.upa.discord.event.impl.BonusSshEventHandler;
import me.upa.discord.listener.command.PacCommands;
import me.upa.service.SparkTrainMicroService.SparkTrainMember;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class DailyResetMicroService extends MicroService {

    private static final Logger logger = LogManager.getLogger();
    private final UpaBotContext ctx;

    public DailyResetMicroService(UpaBotContext ctx) {
        super(Duration.ofMinutes(5));
        this.ctx = ctx;
    }

    @Override
    public void startUp() throws Exception {
        ctx.variables().sparkTrainRepository().accessValue(repo -> {
            DatabaseCachingService databaseCaching = ctx.databaseCaching();
            int place = 1;
            for (SparkTrainMember member : repo.getHollisTrain()) {
                UpaMember upaMember = databaseCaching.getMembers().get(member.getMemberId());
                if (upaMember != null && upaMember.getActive().get()) {
                    upaMember.getHollisSparkTrainShGiven().set(member.getSparkHoursGiven());
                    upaMember.getHollisSparkTrainSsh().set(member.getSparkHoursGiven() - member.getSparkHoursReceived());
                    upaMember.getHollisSparkTrainStaked().set(member.getStaking());
                    upaMember.getHollisSparkTrainPlace().set(place);
                }
                place++;
            }
            place = 1;
            for (SparkTrainMember member : repo.getGlobalTrain()) {
                UpaMember upaMember = databaseCaching.getMembers().get(member.getMemberId());
                if (upaMember != null && upaMember.getActive().get()) {
                    upaMember.getGlobalSparkTrainShGiven().set(member.getSparkHoursGiven());
                    upaMember.getGlobalSparkTrainSsh().set(member.getSparkHoursGiven() - member.getSparkHoursReceived());
                    upaMember.getGlobalSparkTrainStaked().set(member.getStaking());
                    upaMember.getGlobalSparkTrainPlace().set(place);
                }
                place++;
            }
            SparkTrainMessageListener trainListener = ctx.discord().getSparkTrain();
            trainListener.setLastGlobalPartialTrain(repo.getPartialSparkTrainGlobal());
            trainListener.setLastGlobalSparkTrain(repo.getSparkTrainGlobal());
            trainListener.setLastHollisPartialTrain(repo.getPartialSparkTrainHollis());
            trainListener.setLastHollisSparkTrain(repo.getSparkTrainHollis());
            return false;
        });
        run();
    }

    @Override
    public void run() throws Exception {
        ctx.variables().lastDailyReset().access(lastDailyReset -> {
            Instant old = lastDailyReset.get();
            Instant next = wait(old);
            if (!Instant.now().isAfter(next)) {
                return false;
            }

            // Reset dailies.
            lastDailyReset.set(next);

            // reset slots
            ctx.variables().slotMachine().accessValue(slotMachine -> {
                slotMachine.getPlayedToday().clear();
                slotMachine.getTodaysLosses().getAndSet(0);
                return true;
            });

            ctx.discord().schedule(() -> UpaEvent.forEvent(ctx, BonusSshEventHandler.class, handler -> handler.loadSsh(ctx)), 5, TimeUnit.MINUTES);

            Map<Long, Long> removals = new HashMap<>();
            for (UpaPoolProperty poolProperty : ctx.databaseCaching().getPoolProperties().values()) {
                long memberId = poolProperty.getDonorMemberId();
                if (!removals.containsKey(memberId) && UpaBotConstants.ADMINS.contains(memberId)) {
                    LocalDate then = poolProperty.getListedOn();
                    LocalDate now = LocalDate.now();
                    Period period = then.until(now);
                    boolean cond = period.get(ChronoUnit.MONTHS) >= 1 || (period.get(ChronoUnit.DAYS) >= 15 && memberId == UpaBotConstants.UNRULY_CJ_MEMBER_ID);
                    if(cond) {
                        removals.put(memberId, poolProperty.getPropertyId());
                    }
                }
            }
            SqlConnectionManager.getInstance().execute(new SqlTask<Void>() {
                @Override
                public Void execute(Connection connection) throws Exception {
                    try(PreparedStatement statement = connection.prepareStatement("DELETE FROM pool_properties WHERE property_id = ?;")) {
                        for(long propertyId : removals.values()) {
                            statement.setLong(1, propertyId);
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                    return null;
                }
            });

            Role vip = ctx.discord().guild().getRoleById(963449135485288479L);
            for (UpaMember upaMember : ctx.databaseCaching().getMembers().values()) {
                if(!upaMember.getActive().get()) {
                    continue;
                }
                try {
                    Member member = ctx.discord().guild().retrieveMemberById(upaMember.getMemberId()).complete();
                    if (member.getRoles().contains(vip)) {
                        PacCommands.handleDailyCommand(ctx, upaMember.getMemberId(), null);
                    }
                } catch (ErrorResponseException e) {
                    if(e.getErrorResponse() != ErrorResponse.UNKNOWN_MEMBER) {
                        logger.catching(e);
                    }
                }
            }
            return true;
        });
    }

    public static Instant wait(Instant instant) {
        ZoneId zoneId = ZoneId.of("Z");
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant.plus(24, ChronoUnit.HOURS), zoneId);
        localDateTime = localDateTime
                .withHour(5)
                .withMinute(0)
                .withSecond(0);
        return localDateTime.atZone(zoneId).toInstant();
    }

    public static String checkIn(UpaBotContext ctx) {
        String claimIn;
        Instant now = Instant.now();
        Instant resetAt = DailyResetMicroService.wait(ctx.variables().lastDailyReset().get());
        long hoursUntil = now.until(resetAt, ChronoUnit.HOURS);
        long minutesUntil = now.until(resetAt, ChronoUnit.MINUTES);
        if (hoursUntil > 0) {
            claimIn = hoursUntil + " hour(s)";
        } else if (minutesUntil > 0) {
            claimIn = minutesUntil + " minute(s)";
        } else {
            claimIn = "a few seconds";
        }
        return claimIn;
    }

}
