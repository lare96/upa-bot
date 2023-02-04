package me.upa.service;

import com.google.common.base.MoreObjects;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import me.upa.UpaBotContext;
import me.upa.discord.UpaMember;
import me.upa.discord.event.UpaEvent;
import me.upa.discord.event.UpaEventHandler;
import me.upa.discord.event.impl.BonusPacEventHandler;
import me.upa.discord.listener.command.PacCommands;
import me.upa.discord.listener.credit.CreditTransaction;
import me.upa.discord.listener.credit.CreditTransaction.CreditTransactionType;
import me.upa.fetcher.YieldDataFetcher;
import me.upa.game.PropertyYield;
import me.upa.game.PropertyYieldVisitor;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class PaidVisitsMicroService extends MicroService {

    public static final class PropertyVisitData implements Serializable {
        private static final long serialVersionUID = -2578762170336130291L;
        private final Map<Long, VisitData> properties = new ConcurrentHashMap<>();

        public Map<Long, VisitData> getProperties() {
            return properties;
        }
    }

    public static final class VisitData implements Serializable {
        private static final long serialVersionUID = -9191391430924788483L;
        private final long propertyId;
        private volatile int lastIndex;
        private volatile Instant lastClaimed;

        public VisitData(long propertyId, int lastIndex, Instant lastClaimed) {
            this.propertyId = propertyId;
            this.lastIndex = lastIndex;
            this.lastClaimed = lastClaimed;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("property_id", propertyId).add("last_index", lastIndex).toString();
        }

        public long getPropertyId() {
            return propertyId;
        }

        public int getLastIndex() {
            return lastIndex;
        }

        public void setLastIndex(int lastIndex) {
            this.lastIndex = lastIndex;
        }

        public Instant getLastClaimed() {
            return lastClaimed;
        }

        public void setLastClaimed(Instant lastClaimed) {
            this.lastClaimed = lastClaimed;
        }
    }

    private final UpaBotContext ctx;

    private volatile boolean paused;

    public PaidVisitsMicroService(UpaBotContext ctx) {
        super(Duration.ofMinutes(1));
        this.ctx = ctx;
    }

    @Override
    public void run() throws Exception {
        if (paused) {
            return;
        }
        List<PropertyYield> newYieldData = new YieldDataFetcher().waitUntilDone();
        if(newYieldData == null) {
            return;
        }
        Map<String, UpaMember> eosMap = computeEosMap();
        Multiset<UpaMember> payouts = HashMultiset.create();
        ctx.variables().yields().accessValue(oldYieldData -> {
            for (PropertyYield newYield : newYieldData) {
                long propertyId = newYield.getPropertyId();
                if (!PacCommands.UPA_VISIT_PROPERTY_IDS.contains(propertyId)) {
                    continue;
                }

                VisitData oldYield = oldYieldData.getProperties().get(propertyId);
                if (oldYield == null) { // Yield entry is brand new, check for payouts.
                    int index = -1;
                    for (PropertyYieldVisitor visitor : newYield.getVisitors()) {
                        index++;
                        UpaMember upaMember = eosMap.get(visitor.getEosId());
                        if (upaMember != null) {
                            payouts.add(upaMember, visitor.getFee());
                        }
                    }
                    VisitData newYieldEntry = new VisitData(propertyId, index, newYield.getLastClaimed());
                    oldYieldData.getProperties().put(propertyId, newYieldEntry);
                    continue;
                }
                if (!newYield.getLastClaimed().equals(oldYield.lastClaimed)) {
                    oldYield.lastIndex = -1;
                    oldYield.lastClaimed = newYield.getLastClaimed();
                }
                int index = 0;
                for (PropertyYieldVisitor visitor : newYield.getVisitors()) {
                    if (index++ <= oldYield.lastIndex) {
                        continue;
                    }
                    UpaMember upaMember = eosMap.get(visitor.getEosId());
                    if (upaMember != null) {
                        payouts.add(upaMember, visitor.getFee());
                    }
                }
                oldYield.lastIndex = index - 1;
            }
            return true;
        });

        List<CreditTransaction> transactions = new ArrayList<>();
        for (var nextPayout : payouts.entrySet()) {
            int amount = nextPayout.getCount();
            if (UpaEvent.isActive(ctx, BonusPacEventHandler.class)) {
                amount *= 1.25;
            }
            transactions.add(new CreditTransaction(nextPayout.getElement(), amount, CreditTransactionType.PURCHASE, "visiting a designated UPA property"));
        }
        ctx.discord().sendCredit(transactions);
    }

    private Map<String, UpaMember> computeEosMap() {
        return ctx.databaseCaching().getMembers().values().stream().filter(next -> next.getActive().get()).collect(Collectors.toMap(UpaMember::getBlockchainAccountId, v -> v));
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }
}
