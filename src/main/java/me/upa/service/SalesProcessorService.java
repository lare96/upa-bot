package me.upa.service;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.AbstractScheduledService;
import me.upa.discord.DiscordService;
import me.upa.fetcher.DataFetcherManager;
import me.upa.game.City;
import me.upa.selector.NeighborhoodPropertySelector;
import me.upa.selector.PropertySelector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Handles the scheduling of sales fetching.
 */
public final class SalesProcessorService extends AbstractIdleService {

    private static final Logger logger = LogManager.getLogger();

    private static final ImmutableSet<String> CITIES_TRACKED = ImmutableSet.of("Queens");

    private static final class SalesDataWorker extends AbstractScheduledService {
        private final int cityId;
        //private final Stopwatch stopwatch = Stopwatch.createUnstarted();
        private final PropertySelector selector = new NeighborhoodPropertySelector();

        public SalesDataWorker(int cityId) {
            this.cityId = cityId;
        }

        @Override
        protected void startUp() throws Exception {
            execute();
        }

        @Override
        protected void runOneIteration() throws Exception {
            execute();
        }

        @Override
        protected void shutDown() throws Exception {
           logger.warn("Service [{}] shutting down.", getClass().getSimpleName());
        }

        @Override
        protected Scheduler scheduler() {
            return Scheduler.newFixedRateSchedule(3, 3, TimeUnit.SECONDS);
        }

        public void execute() throws Exception {
            //stopwatch.reset().start();
            DataFetcherManager.getSalesFetcher().fetch("https://upx-spark.exchange/cities/download/" + cityId);
            City city = DataFetcherManager.getCityMap().get(cityId);
            if (city == null) {
                return;
            }
            selector.select(city, DataFetcherManager.getSalesMap().get(cityId));
            DataFetcherManager.refreshSalesMap(cityId);
            var list = DataFetcherManager.getSalesMap().get(cityId);
            //   if (list != null)
            //Logger.getGlobal().info("[" + city.getName() + "] Loaded " + list.size() + " sales in " + DiscordService.COMMA_FORMAT.format(stopwatch.elapsed().toMillis()) + " ms");
        }
    }

    /**
     * The selection method to use.
     */
    public static final PropertySelector SELECTOR = new NeighborhoodPropertySelector();


    @Override
    protected void startUp() throws Exception {
        DataFetcherManager.getLocationFetcher().fetch();
        int counter = 0;
        for (City city : DataFetcherManager.getCityMap().values()) {
            String name = city.getName();
            if (!CITIES_TRACKED.contains(name))
                continue;
            SalesDataWorker worker = new SalesDataWorker(city.getId());
           // worker.startAsync();
            counter++;
        }
       logger.info("Loaded {} sales data workers", counter);
    }

    @Override
    protected void shutDown() throws Exception {
    }
}
