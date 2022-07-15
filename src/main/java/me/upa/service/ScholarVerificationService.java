package me.upa.service;

import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.AbstractScheduledService;
import me.upa.UpaBot;
import me.upa.discord.CreditTransaction;
import me.upa.discord.CreditTransaction.CreditTransactionType;
import me.upa.discord.Scholar;
import me.upa.discord.UpaMember;
import me.upa.fetcher.ProfileDataFetcher;
import me.upa.fetcher.VisitorsDataFetcher;
import me.upa.game.Profile;
import me.upa.game.PropertyVisitor;
import me.upa.sql.AddSendsTask;
import me.upa.sql.AddSendsTask.SendAddition;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNullElse;
import static me.upa.fetcher.ProfileDataFetcher.DUMMY;

public final class ScholarVerificationService extends AbstractScheduledService {
    private static final Logger logger = LogManager.getLogger();

    private final class UpdateNetWorthTask extends SqlTask<Void> {

        private final Map<Long, Integer> netWorthUpdates;

        private UpdateNetWorthTask(Map<Long, Integer> netWorthUpdates) {
            this.netWorthUpdates = netWorthUpdates;
        }

        @Override
        public Void execute(Connection connection) throws Exception {
            try (PreparedStatement updateScholar = connection.prepareStatement("UPDATE scholars SET net_worth = ? WHERE discord_id = ?;")) {
                for (Entry<Long, Integer> next : netWorthUpdates.entrySet()) {
                    updateScholar.setInt(1, next.getValue());
                    updateScholar.setLong(2, next.getKey());
                    updateScholar.addBatch();
                }
                updateScholar.executeBatch();
            }
            return null;
        }
    }

    private final class GraduateScholarsTask extends SqlTask<Set<Long>> {

        private final Queue<Scholar> removals;
        private final Set<Long> graduates = new HashSet<>();

        private GraduateScholarsTask(Queue<Scholar> removals) {
            this.removals = removals;
        }

        @Override
        public Set<Long> execute(Connection connection) throws Exception {
            try (PreparedStatement deleteScholar = connection.prepareStatement("DELETE FROM scholars WHERE discord_id = ?;")) {
                for (; ; ) {
                    Scholar scholar = removals.poll();
                    if (scholar == null) {
                        break;
                    }

                    graduates.add(scholar.getMemberId());
                    deleteScholar.setLong(1, scholar.getMemberId());
                    deleteScholar.addBatch();
                }
                deleteScholar.executeBatch();
            }
            return graduates;
        }
    }

    private final LoadingCache<String, Profile> cachedScholars = CacheBuilder.newBuilder().expireAfterWrite(6, TimeUnit.HOURS).build(new CacheLoader<>() {
        @Override
        public Profile load(String key) throws Exception {
            var profileDataFetcher = new ProfileDataFetcher(key);
            profileDataFetcher.fetch();
            Profile fetchedProfile = profileDataFetcher.waitUntilDone();
            return requireNonNullElse(fetchedProfile, DUMMY);
        }
    });

    @Override
    protected void runOneIteration() throws Exception {
        try {
            Multiset<UpaMember> credits = HashMultiset.create();
            Stopwatch stopwatch = Stopwatch.createStarted();
            DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
            Map<Long, Integer> netWorthUpdates = new HashMap<>();
            Queue<Scholar> removals = new ArrayDeque<>();
            Multiset<String> additionsCount = HashMultiset.create();
            Multiset<String> additionsCountSponsored = HashMultiset.create();
            Set<SendAddition> additions = new HashSet<>();
            Map<String, String> lastFetchInstants = new HashMap<>();
            Set<String> names = new HashSet<>();
            for (Scholar scholar : databaseCaching.getScholars().values()) {
                var visitorsFetcher = new VisitorsDataFetcher(scholar.getPropertyId());
                visitorsFetcher.fetch();
                List<PropertyVisitor> visitors = visitorsFetcher.waitUntilDone();
                if (visitors == null) {
                    continue;
                }
                if (visitors.size() > 0) {
                    Instant newVisitorInstant = visitors.get(0).getVisitedAt();
                    Instant lastVisitorInstant = scholar.getLastFetchInstant().getAndSet(newVisitorInstant);
                    for (PropertyVisitor next : visitors) {
                        if (next.isPending()) {
                            continue;
                        }
                        if (next.getVisitedAt().isAfter(lastVisitorInstant)) {
                            Long nextMemberId = databaseCaching.getMemberNames().inverse().get(next.getUsername());
                            if (nextMemberId == null) {
                                continue;
                            }
                            UpaMember nextMember = databaseCaching.getMembers().get(nextMemberId);
                            if (nextMember == null) {
                                continue;
                            }
                            if (scholar.getSponsored().get()) {
                                credits.add(nextMember, (int) (next.getPrice() * 0.75));
                                nextMember.getSponsoredSends().incrementAndGet();
                                additionsCountSponsored.add(nextMember.getInGameName());
                            } else {
                                credits.add(nextMember, (int) (next.getPrice() * 0.5));
                                nextMember.getSends().incrementAndGet();
                                additionsCount.add(nextMember.getInGameName());
                            }
                            names.add(next.getUsername());
                        }
                    }
                    if (newVisitorInstant.isAfter(lastVisitorInstant)) {
                        lastFetchInstants.put(scholar.getUsername(), newVisitorInstant.toString());
                    }
                }

                Profile profile = cachedScholars.get(scholar.getUsername());
                if (profile == DUMMY) {
                    logger.warn("Could not load profile for {}.", scholar.getUsername());
                    continue;
                }
                int netWorth = profile.getNetWorth();
                if (netWorth >= 10_000) {
                    removals.add(scholar);
                    continue;
                }
                if (scholar.getNetWorth().get() != netWorth) {
                    netWorthUpdates.put(scholar.getMemberId(), netWorth);
                }
            }

            for (String next : names) {
                int add = additionsCount.count(next);
                int sponsoredAdd = additionsCountSponsored.count(next);
                additions.add(new SendAddition(next, add, sponsoredAdd));
            }

            if (removals.size() > 0) {
                SqlConnectionManager.getInstance().execute(new GraduateScholarsTask(removals),
                        success ->
                                success.forEach(next -> databaseCaching.getScholars().remove(next)));
            }
            if (netWorthUpdates.size() > 0) {
                SqlConnectionManager.getInstance().execute(new UpdateNetWorthTask(netWorthUpdates),
                        success -> {
                            for (var next : netWorthUpdates.entrySet()) {
                                Scholar scholar = databaseCaching.getScholars().get(next.getKey());
                                scholar.getNetWorth().set(next.getValue());
                            }
                        });
            }
            if (additions.size() > 0 || lastFetchInstants.size() > 0) {
                SqlConnectionManager.getInstance().execute(new AddSendsTask(additions, lastFetchInstants));
            }
            if (credits.size() > 0) {
                for (var next : credits.entrySet()) {
                    UpaBot.getDiscordService().sendCredit(new CreditTransaction(next.getElement(), next.getCount(), CreditTransactionType.SENDS));
                }
            }
            logger.info("Scholar verifications completed in {}s.", stopwatch.elapsed(TimeUnit.SECONDS));
        } catch (Exception e) {
            logger.catching(e);
        }
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(75, 75, TimeUnit.SECONDS);
    }
}
