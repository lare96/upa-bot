package me.upa.service;

import com.google.common.base.Objects;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import me.upa.UpaBot;
import me.upa.discord.CreditTransaction;
import me.upa.discord.CreditTransaction.CreditTransactionType;
import me.upa.discord.UpaMember;
import me.upa.discord.command.PacCommands;
import me.upa.fetcher.YieldDataFetcher;
import me.upa.game.PropertyYield;
import me.upa.game.PropertyYieldVisitor;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class PaidVisitsMicroService extends MicroService {

    private static final class PaidVisitor {
        private final UpaMember upaMember;
        private final Instant lastClaimed;

        private PaidVisitor(UpaMember upaMember, Instant lastClaimed) {
            this.upaMember = upaMember;
            this.lastClaimed = lastClaimed;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PaidVisitor that = (PaidVisitor) o;
            return Objects.equal(upaMember, that.upaMember);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(upaMember);
        }

        public UpaMember getUpaMember() {
            return upaMember;
        }

        public Instant getLastClaimed() {
            return lastClaimed;
        }
    }

    private static final class UpdatePurchaseHistoryTask extends SqlTask<Void> {

        private final Set<PaidVisitor> removals;
        private final Multiset<PaidVisitor> paidUpx;

        private UpdatePurchaseHistoryTask(Set<PaidVisitor> removals, Multiset<PaidVisitor> paidUpx) {
            this.removals = removals;
            this.paidUpx = paidUpx;
        }

        @Override
        public Void execute(Connection connection) throws Exception {
            try {
                connection.setAutoCommit(false);
                if (removals.size() > 0) {
                    try (PreparedStatement ps = connection.prepareStatement("DELETE FROM paid_sends WHERE blockchain_account_id = ?;")) {
                        for (PaidVisitor paidVisitor : removals) {
                            ps.setString(1, paidVisitor.upaMember.getBlockchainAccountId());
                            ps.addBatch();
                        }
                        if (ps.executeBatch().length != removals.size()) {
                            connection.rollback();
                            throw new Exception();
                        }
                    }
                }
                if (paidUpx.entrySet().size() > 0) {
                    try (PreparedStatement ps = connection.prepareStatement("INSERT INTO paid_sends (blockchain_account_id, upx_given, last_claimed) VALUES (?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE upx_given = upx_given + ?, last_claimed = ?;")) {
                        for (var next : paidUpx.entrySet()) {
                            String bcId = next.getElement().upaMember.getBlockchainAccountId();
                            ps.setString(1, bcId);
                            ps.setInt(2, next.getCount());
                            ps.setString(3, next.getElement().lastClaimed.toString());
                            ps.setInt(4, next.getCount());
                            ps.setString(5, next.getElement().lastClaimed.toString());
                            ps.addBatch();
                        }
                        if (ps.executeBatch().length != paidUpx.entrySet().size()) {
                            connection.rollback();
                            throw new RuntimeException("Could not update paid sends!");
                        }
                    }
                }
                connection.commit();
            } finally {
                connection.setAutoCommit(true);
            }
            return null;
        }
    }

    private final Multiset<PaidVisitor> lastVisitors = ConcurrentHashMultiset.create();

    public PaidVisitsMicroService() {
        super(Duration.ofMinutes(3));
    }

    @Override
    public void startUp() throws Exception {
        Map<String, UpaMember> eosMap = computeEosMap();
        try (Connection connection = SqlConnectionManager.getInstance().take()) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM paid_sends;");
                 ResultSet results = ps.executeQuery()) {
                while (results.next()) {
                    String eosId = results.getString(1);
                    int upx = results.getInt(2);
                    Instant lastClaimed = Instant.parse(results.getString(3));
                    UpaMember member = eosMap.get(eosId);
                    if (member != null) {
                        lastVisitors.add(new PaidVisitor(member, lastClaimed), upx);
                    }
                }
            }
        }
    }

    @Override
    public void run() throws Exception {
        Map<String, UpaMember> eosMap = computeEosMap();
        Multiset<PaidVisitor> newVisitors = HashMultiset.create();
        Multiset<PaidVisitor> paidUpx = HashMultiset.create();
        Map<PaidVisitor, Instant> lastYieldClaims = new HashMap<>();
        var fetcher = new YieldDataFetcher();
        List<PropertyYield> yields = fetcher.waitUntilDone();
        if(yields == null){
            return;
        }
        for (PropertyYield propertyYield : yields) {
            if (!PacCommands.UPA_VISIT_PROPERTY_IDS.contains(propertyYield.getPropertyId())) {
                continue;
            }
            for (PropertyYieldVisitor visitor : propertyYield.getVisitors()) {
                UpaMember upaMember = eosMap.get(visitor.getEosId());
                if (upaMember != null) {
                    var paidVisitor = new PaidVisitor(upaMember, propertyYield.getLastClaimed());
                    newVisitors.add(paidVisitor, visitor.getFee());
                    lastYieldClaims.put(paidVisitor, propertyYield.getLastClaimed());
                }
            }
        }

        Set<PaidVisitor> claimed = new HashSet<>();
        if (lastVisitors.isEmpty()) {
            paidUpx.addAll(newVisitors);
        } else {
            for (var next : lastVisitors.entrySet()) {
                PaidVisitor paidVisitor = next.getElement();
                int oldCount = next.getCount();
                int newCount = newVisitors.count(paidVisitor);
                if (newCount == 0) {
                    claimed.add(paidVisitor);
                    continue;
                }
                Instant matchingInstant = lastYieldClaims.get(paidVisitor);
                if (oldCount > newCount && matchingInstant.isAfter(paidVisitor.lastClaimed)) {
                    paidUpx.add(next.getElement(), newCount);
                } else if (newCount > oldCount) {
                    paidUpx.add(next.getElement(), newCount - oldCount);
                }
            }
        }
        if (claimed.size() > 0 || paidUpx.size() > 0) {
            // TODO do on same thread...
            // There are new payments, replace/update table with the new payments. Delete some claimed payments.
            SqlConnectionManager.getInstance().execute(new UpdatePurchaseHistoryTask(claimed, paidUpx), success -> {
                claimed.forEach(lastVisitors::remove);
                for (var next : paidUpx.entrySet()) {
                    lastVisitors.add(next.getElement(), next.getCount());
                }
                if (paidUpx.size() > 0) {
                    List<CreditTransaction> transactions = new ArrayList<>();
                    for (var next : paidUpx.entrySet()) {
                        transactions.add(new CreditTransaction(next.getElement().upaMember, next.getCount(), CreditTransactionType.PURCHASE, "visiting a designated UPA property"));
                    }
                    UpaBot.getDiscordService().sendCredit(transactions);
                }
            });
        }
    }

    private Map<String, UpaMember> computeEosMap() {
        return UpaBot.getDatabaseCachingService().getMembers().values().stream().collect(Collectors.toMap(UpaMember::getBlockchainAccountId, v -> v));
    }
}
