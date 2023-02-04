package me.upa.discord;

import com.google.common.collect.Sets;
import me.upa.UpaBotContext;
import me.upa.fetcher.VisitFeeBlockchainDataFetcher;
import me.upa.game.BlockchainVisitFee;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

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
    //TODO save last fetched date even if nothing is there

    public void process(UpaBotContext ctx) throws ExecutionException, InterruptedException {
        boolean ready = isReady();
        if (ready && !reports.isEmpty()) {
            Iterator<PendingWeeklyReport> it = reports.values().iterator();
            while (it.hasNext()) {
                PendingWeeklyReport pendingReport = it.next();
                if (pendingReport.isReady()) {
                    it.remove();
                    long memberId = pendingReport.getMemberId();
                    MessageEmbed embed = pendingReport.generate(pendingReport.getOrigin());
                 //   ctx.discord().guild().retrieveMemberById(memberId).queue(success ->
          //                  success.getUser().openPrivateChannel().queue(privateChannel ->
            //                        privateChannel.sendMessageEmbeds(embed).queue()));
                 //   ctx.discord().guild().getTextChannelById(984570735282491452L).sendMessage(new MessageBuilder().
                //            append("<@").append(String.valueOf(memberId)).append(">").setEmbeds(embed).build()).queue();
                }
            }
            if (reports.isEmpty()) {
                setNextMarker();
                return;
            }
        }

   //    Guild guild = ctx.discord().guild();
        //Role vip = guild.getRoleById(963449135485288479L);
        Instant origin = computeOrigin();
        for (UpaMember upaMember : ctx.databaseCaching().getMembers().values()) {
            if(!upaMember.getActive().get()) {
                continue;
            }
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
            Instant startFrom = pendingReport.getLastQuery().compareAndExchange(null, pendingReport.getOrigin());
            VisitFeeBlockchainDataFetcher fetcher = new VisitFeeBlockchainDataFetcher(pendingReport.getBlockchainAccountId(), startFrom, sendMarker);
            BlockchainVisitFee visitFee = fetcher.fetch().get();
            if(visitFee == null) {
                System.out.println("here");
                break;
            }
            Instant last = pendingReport.getLastQuery().get();
            if (visitFee.getLastTimestamp() == null || visitFee.getAmount() < 1 || Objects.equals(visitFee.getLastTimestamp(), last)) {
                if (ready) {
                    pendingReport.setFinished();
                }
                break;
            }
            pendingReport.getVisitFees().addAndGet(visitFee.getAmount());
            pendingReport.getLastQuery().set(visitFee.getLastTimestamp());
            System.out.println("here2");
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
        return Instant.now().atOffset(ZoneOffset.UTC).with(TemporalAdjusters.previous(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS).toInstant().plusMillis(1);
    }

    private void setNextMarker() {
        sendMarker = Instant.now().atOffset(ZoneOffset.UTC).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS).toInstant().plusMillis(1);
    }

    public Map<Long, PendingWeeklyReport> getReports() {
        return reports;
    }

    public Instant getSendMarker() {
        return sendMarker;
    }
}
