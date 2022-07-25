package me.upa.selector;

import com.google.common.collect.Sets;
import me.upa.UpaBotContext;
import me.upa.discord.DiscordService;
import me.upa.discord.SaleNotification;
import me.upa.discord.SaleNotificationRequest;
import me.upa.discord.SaleNotificationType;
import me.upa.game.City;
import me.upa.game.Sale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an algorithm that determine undervalued properties.
 *
 * @author lare96
 */
public abstract class PropertySelector {


    protected PropertySelector(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Compares sales for the lowest price.
     */
    private static final class LowestPriceComparator implements Comparator<Sale> {
        @Override
        public int compare(Sale o1, Sale o2) {
            return Double.compare(o1.getPrice(), o2.getPrice());
        }
    }

    private static final Logger logger = LogManager.getLogger();

    protected static final Comparator<Sale> COMPARATOR = new LowestPriceComparator();

    public static final Map<City, Integer> floorPrices = new ConcurrentHashMap<>();
    public static final Set<City> floorPriceChanges = Sets.newConcurrentHashSet();
    /**
     * The context.
     */
    private final UpaBotContext ctx;
    public final void select(City city, Collection<Sale> sales) {
        if (sales == null || sales.isEmpty())
            return;
        DiscordService discordService = ctx.discord();
        List<SaleNotificationRequest> requests = computeNotificationRequests(city, sales);
        try {
            for (SaleNotificationRequest nextRequest : requests) {
                SaleNotification notification = buildNotifications(nextRequest.getCurrentSale(), nextRequest.getLastSale(), nextRequest.getOldSales());
                if (notification != null) {
                    discordService.sendNotification(notification);
                }
            }
        } catch (Exception e) {
            logger.warn("Error while building/sending notifications.", e);
        } finally {
            updateOldSales(city);
        }
    }

    private SaleNotification buildNotifications(Sale currentSale, Sale lastSale, List<Sale> lastCitySales) {
        if (lastCitySales.contains(currentSale)) {
            return null;
        }
        if (currentSale.getPrice() < currentSale.getMintPrice()) {
            double currentPrice = currentSale.getPrice();
            double currentMint = currentSale.getMintPrice();
            int marginPrice = (int) (currentMint - currentPrice);
            double marginPercentage = 100 - (currentPrice * 100 / currentMint);
            return new SaleNotification(currentSale, marginPrice, marginPercentage, -1, SaleNotificationType.UNDER_MINT);
        } else if (currentSale.getPrice() < lastSale.getPrice()) {
            double currentPrice = currentSale.getPrice();
            double lastPrice = lastSale.getPrice();

            int marginPrice = (int) (lastPrice - currentPrice);
            double marginPercentage = 100 - (currentPrice * 100 / lastPrice);

            if (marginPrice > currentPrice) {
                return null;
            }
            int rating = computeRatingForMargin((int) marginPercentage);
            if (rating == 0) {
                return null;
            }
            if (marginPrice < 2500) {
                return new SaleNotification(currentSale, marginPrice, marginPercentage, rating, SaleNotificationType.SOFT_DEAL);
            } else if (rating == 3 && marginPrice > 7500) {
                return new SaleNotification(currentSale, marginPrice, marginPercentage, rating, SaleNotificationType.HUGE_DEAL);
            } else {
                return new SaleNotification(currentSale, marginPrice, marginPercentage, rating, SaleNotificationType.CITY_DEAL);
            }
        }
        return null;
    }

    abstract List<SaleNotificationRequest> computeNotificationRequests(City city, Collection<Sale> sales);

    abstract void updateOldSales(City city);

    abstract int computeRatingForMargin(int marginPercentage);
}