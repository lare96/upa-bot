package me.upa.service;

import com.google.common.util.concurrent.AbstractScheduledService;
import me.upa.UpaBotContext;
import me.upa.fetcher.DataFetcherManager;
import me.upa.fetcher.PurchasesBlockchainDataFetcher;
import me.upa.game.BlockchainPurchase;
import me.upa.game.CachedProperty;
import me.upa.sql.SqlConnectionManager;
import me.upa.variable.SystemVariable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.units.qual.C;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class BlockchainSynchronizationService extends AbstractScheduledService {
    private static final Logger logger = LogManager.getLogger();
    private final UpaBotContext ctx;

    public BlockchainSynchronizationService(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    protected void startUp() throws Exception {
        logger.info("Starting blockchain synchronization from " + ctx.variables().lastBlockchainFetch().getValue());
    }

    @Override
    protected void runOneIteration() throws Exception {
        ctx.variables().lastBlockchainFetch().access(lastBlockchainFetch -> {
            try {
                PurchasesBlockchainDataFetcher purchasesFetcher = new PurchasesBlockchainDataFetcher(lastBlockchainFetch.get());
                List<BlockchainPurchase> sales = purchasesFetcher.fetch().get();
                if(sales == null || sales.isEmpty()) {
                    return false;
                }
                try (Connection connection = SqlConnectionManager.getInstance().take();
                     PreparedStatement ps = connection.prepareStatement("INSERT INTO property_lookup(property_id, address, city_id) VALUES(?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE address = ?, city_id = ?;")) {
                    for (BlockchainPurchase bp : sales) {
                        int cityId = DataFetcherManager.getCityId(bp.getCityName());
                        CachedProperty property = new CachedProperty(bp.getPropertyId(), bp.getAddress(), -1, -1, cityId);
                        if (ctx.databaseCaching().getPropertyLookup().putIfAbsent(bp.getPropertyId(), property) == null) {
                            String address = bp.getAddress().toUpperCase();
                            ps.setLong(1, bp.getPropertyId());
                            ps.setString(2, address);
                            ps.setInt(3, cityId);
                            ps.setString(4, address);
                            ps.setInt(5, cityId);
                            ps.addBatch();
                        }
                    }
                    lastBlockchainFetch.set(sales.get(sales.size() - 1).getTimestamp());
                    ps.executeBatch();
                    return true;
                }
            } catch (Exception e) {
                logger.catching(e);
            }
            return false;
        });
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(5, 30, TimeUnit.SECONDS);
    }
}
