package me.upa.game.selector;

import me.upa.UpaBotContext;
import me.upa.discord.SaleNotificationRequest;
import me.upa.fetcher.DataFetcherManager;
import me.upa.game.City;
import me.upa.game.Neighborhood;
import me.upa.game.Sale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NeighborhoodPropertySelector extends PropertySelector {
    private static final Logger logger = LogManager.getLogger();

    private final Map<Integer, List<Sale>> oldNeghborhoodSales = new HashMap<>();
    private Map<Integer, List<Sale>> newNeighborhoodSales;

    public NeighborhoodPropertySelector(UpaBotContext ctx) {
        super(ctx);
    }

    @Override
    List<SaleNotificationRequest> computeNotificationRequests(City city, Collection<Sale> sales) {
        // Set floor price.
        List<Sale> allNewSales = new ArrayList<>(sales);
        allNewSales.sort(COMPARATOR);
        int newFloorPrice = allNewSales.get(0).getPrice();
        Integer floorPrice = floorPrices.get(city);
        if (floorPrice == null || newFloorPrice != floorPrice) {
            floorPrices.put(city, newFloorPrice);
        }
      //  StringBuilder sb =new StringBuilder();

        // Organize sales by neighborhood.
        newNeighborhoodSales = new HashMap<>();
        for (Sale nextSale : sales) {
            int neighborhoodId = nextSale.getNeighborhoodId().orElse(-1);
            List<Sale> neighborhoodSales = newNeighborhoodSales.computeIfAbsent(neighborhoodId, id -> new ArrayList<>());
            neighborhoodSales.add(nextSale);
        }

        // Sort them by cheapest -> most expensive.
        for (List<Sale> saleList : newNeighborhoodSales.values()) {
            saleList.sort(COMPARATOR);
        }

        /*for(Entry<Integer, List<Sale>> entry : newNeighborhoodSales.entrySet()) {
            String name = DataFetcherManager.getNeighborhoodMap().get(entry.getKey()).getName();
            sb.append(name).append("--------------------------------").append('\n');
            for (var n : entry.getValue()) {
                sb.append(n).append('\n');
            }
        }
        try {
            Files.writeString(Paths.get("test.txt"), sb.toString(), StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        // Send notification requests.
        List<SaleNotificationRequest> requests = new ArrayList<>();
        if (!oldNeghborhoodSales.isEmpty()) {
            for (var next : newNeighborhoodSales.entrySet()) {
                int neighborhoodId = next.getKey();
                Neighborhood neighborhood = DataFetcherManager.getNeighborhoodMap().get(neighborhoodId);
                if (neighborhoodId != -1 && neighborhood == null) {
                   logger.warn("Neighborhood with ID [{}] could not be found.", neighborhoodId);
                    continue;
                }
                List<Sale> oldSales = oldNeghborhoodSales.get(neighborhoodId);
                if (oldSales == null) {
                    continue;
                }
                Sale currentSale = next.getValue().get(0);
                Sale lastSale = oldSales.get(0);
                requests.add(new SaleNotificationRequest(currentSale, lastSale, oldSales));
            }
        }
        return requests;
    }

    @Override
    void updateOldSales(City city) {
        oldNeghborhoodSales.clear();
        oldNeghborhoodSales.putAll(newNeighborhoodSales);
        newNeighborhoodSales.clear();
        newNeighborhoodSales = null;
    }

    @Override
    int computeRatingForMargin(int marginPercentage) {
        if (marginPercentage >= 30) {
            return 3;
        } else if (marginPercentage >= 15) {
            return 2;
        } else if (marginPercentage >= 7.5) {
            return 1;
        } else {
            return 0;
        }
    }

}