package me.upa.discord;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Uninterruptibles;
import me.upa.UpaBotContext;
import me.upa.fetcher.VisitFeeBlockchainDataFetcher;
import me.upa.game.BlockchainVisitFee;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalUnit;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public final class WeeklyReportData implements Serializable {

    private static final long serialVersionUID = 7297236110046611447L;
    private volatile Instant sendMarker;

    private final Set<Long> searched = Sets.newConcurrentHashSet();
    private final Map<Long, PendingWeeklyReport> reports = new ConcurrentHashMap<>();

    public WeeklyReportData() {
        if (sendMarker == null) {
            setNextMarker();
        }
    }

    public void process(UpaBotContext ctx) throws ExecutionException, InterruptedException {
        Instant origin = computeOrigin();
        boolean ready = isReady();
        if (ready && !reports.isEmpty()) {
            Iterator<PendingWeeklyReport> it = reports.values().iterator();
            while (it.hasNext()) {
                PendingWeeklyReport pendingReport = it.next();
                if (pendingReport.isReady()) {
                    it.remove();
                    long memberId = pendingReport.getMemberId();
                    MessageEmbed embed = pendingReport.generate(origin);
                    ctx.discord().guild().retrieveMemberById(memberId).queue(success ->
                            success.getUser().openPrivateChannel().queue(privateChannel ->
                                    privateChannel.sendMessageEmbeds(embed).queue()));
                }
            }
            if (reports.isEmpty()) {
                setNextMarker();
                return;
            }
        }

        Guild guild = ctx.discord().guild();
        Role vip = guild.getRoleById(956795779241111612L);
        for (UpaMember upaMember : ctx.databaseCaching().getMembers().values()) {
            long memberId = upaMember.getMemberId();
            Member member = ctx.discord().guild().retrieveMemberById(memberId).complete();
            if (/*member.getRoles().contains(vip)*/ member.getIdLong() == 220622659665264643L ||
                    member.getIdLong() == 373218455937089537L ||
                    member.getIdLong() == 200653175127146501L) {
                reports.putIfAbsent(memberId, new PendingWeeklyReport(upaMember.getBlockchainAccountId(), memberId, upaMember.getInGameName(), origin));
            }
        }

        for (PendingWeeklyReport pendingReport : reports.values()) {
            long memberId = pendingReport.getMemberId();
            if(!searched.add(memberId)) {
                continue;
            }
            Instant startFrom = pendingReport.getLastQuery().compareAndExchange(null, origin);
            startFrom = startFrom == null ? origin : startFrom;
            VisitFeeBlockchainDataFetcher fetcher = new VisitFeeBlockchainDataFetcher(pendingReport.getBlockchainAccountId(), startFrom, sendMarker);
            BlockchainVisitFee visitFee = fetcher.fetch().get();
            if (visitFee.getLastTimestamp() == null || visitFee.getAmount() < 1) {
                if (ready) {
                    pendingReport.setFinished();
                }
                break;
            }
            pendingReport.getVisitFees().addAndGet(visitFee.getAmount());
            pendingReport.getLastQuery().set(visitFee.getLastTimestamp());
            break;
        }

        if(reports.keySet().equals(searched)) {
            searched.clear();
        }
    }

    public boolean isReady() {
        return Instant.now().isAfter(sendMarker);
    }

    private Instant computeOrigin() {
        return Instant.now().atOffset(ZoneOffset.UTC).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS).toInstant().plusMillis(1);
    }

    private void setNextMarker() {
        sendMarker = Instant.now().atOffset(ZoneOffset.UTC).with(TemporalAdjusters.next(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS).toInstant().plusMillis(1);
    }

    public Map<Long, PendingWeeklyReport> getReports() {
        return reports;
    }
}
