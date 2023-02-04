package me.upa.service;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.AbstractScheduledService;
import me.upa.UpaBotContext;
import me.upa.fetcher.DataFetcherManager;
import me.upa.game.BlockchainSale;
import me.upa.game.City;
import me.upa.game.selector.NeighborhoodPropertySelector;
import me.upa.game.selector.PropertySelector;
import net.dv8tion.jda.api.EmbedBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Handles the scheduling of sales fetching.
 */
public final class SalesProcessorService extends AbstractScheduledService {

    private static final Logger logger = LogManager.getLogger();

    private static final ImmutableSet<String> CITIES_TRACKED = ImmutableSet.of("Queens");

    private static final class SalesDataWorker extends AbstractScheduledService {
        private final int cityId;
        //private final Stopwatch stopwatch = Stopwatch.createUnstarted();
        private final PropertySelector selector;

        public SalesDataWorker(UpaBotContext ctx, int cityId) {
            this.cityId = cityId;
            selector = new NeighborhoodPropertySelector(ctx);
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
            DataFetcherManager.refreshSales(cityId);
            var list = DataFetcherManager.getSalesMap().get(cityId);
            //   if (list != null)
            //Logger.getGlobal().info("[" + city.getName() + "] Loaded " + list.size() + " sales in " + DiscordService.COMMA_FORMAT.format(stopwatch.elapsed().toMillis()) + " ms");
        }
    }

    /**
     * The selection method to use.
     */
    public final PropertySelector selector;

    private final UpaBotContext ctx;

    private volatile Instant lastRequest = Instant.now().minus(3, ChronoUnit.SECONDS);

    public SalesProcessorService(UpaBotContext ctx) {
        this.ctx = ctx;
        selector = new NeighborhoodPropertySelector(ctx);
    }

    @Override
    protected void startUp() throws Exception {
    }

    private final Map<Long, Integer> recordedSales = new HashMap<>();

    @Override
    protected void runOneIteration() throws Exception {
        try {
          //  SalesBlockchainDataFetcher salesFetcher = new SalesBlockchainDataFetcher(lastRequest);
            List<BlockchainSale> sales =List.of();// salesFetcher.fetch().get();
            for (BlockchainSale bs : sales) {
                long propertyId = bs.getPropertyId();
                int amount = bs.getAmount();
                Integer recordedAmount = recordedSales.get(propertyId);
                if(recordedAmount != null && amount >= recordedAmount) {
                    continue;
                }
                String propertyIdStr = Long.toString(propertyId);
                String city = "<could_not_estimate>";
                int amountThreshold = 5000;

                // fresno, san fran, manhattan
                if(propertyIdStr.startsWith("789") ||
                        propertyIdStr.startsWith("788")) {
                    continue;
                } else if (propertyIdStr.startsWith("823") ||
                        propertyIdStr.startsWith("824")) {
                    amountThreshold = 3600;
                    city = "Detroit";
                } else if (propertyIdStr.startsWith("813") ||
                        propertyIdStr.startsWith("814")) {
                    amountThreshold = 5500;
                    city = "Queens";
                } else if (propertyIdStr.startsWith("784") ||
                        propertyIdStr.startsWith("785")) {
                    amountThreshold = 8000;
                    city = "Las Vegas";
                } else if (propertyIdStr.startsWith("820")) {
                    amountThreshold = 5700;
                    city = "Chicago";
                } else if (propertyIdStr.startsWith("746") ||
                        propertyIdStr.startsWith("747")) {
                    amountThreshold = 5700;
                    city = "New Orleans";
                }else if (propertyIdStr.startsWith("818")) {
                    amountThreshold = 7400;
                    city = "Cleveland";
                } else if (propertyIdStr.startsWith("772") ||
                        propertyIdStr.startsWith("773")) {
                    amountThreshold = 7000;
                    city = "Los Angeles";
                }  else if (propertyIdStr.startsWith("795")) {
                    amountThreshold = 9300;
                    city = "Oakland";
                }else if (propertyIdStr.startsWith("802") ||
                        propertyIdStr.startsWith("803")) {
                    amountThreshold = 7300;
                    city = "Kansas City";
                }else {
                    logger.warn("https://play.upland.me/?prop_id=" + propertyIdStr);
                }
                if (amount < amountThreshold) {
                    ctx.discord().guild().getTextChannelById(966244394644701194L).sendMessageEmbeds(new EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setDescription("**" + bs.getPropertyId() + "** listed for sale @ **" + bs.getAmount() + " UPX**")
                            .addField("City", city, false)
                            .addField("Property link", "https://play.upland.me/?prop_id=" + bs.getPropertyId(), false)
                            .setThumbnail("https://i.imgur.com/yNQfOcc.gif")
                            .build()).queue();
                    recordedSales.put(propertyId, amount);
                }
            }
            recordedSales.clear();
        } catch (Exception e) {
            logger.catching(e);
        } finally {
            lastRequest = Instant.now();
        }
    }

    @Override
    protected void shutDown() throws Exception {
        logger.warn("Service [{}] shutting down.", getClass().getSimpleName());
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(1, 5, TimeUnit.SECONDS);
    }
}
