package me.upa.game.selector;

import me.upa.UpaBotContext;
import me.upa.discord.SaleNotificationRequest;
import me.upa.game.City;
import me.upa.game.Sale;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class CityPropertySelector extends PropertySelector {

    private final List<Sale> oldSales = new ArrayList<>();
    private List<Sale> newSales;

    public CityPropertySelector(UpaBotContext ctx) {
        super(ctx);
    }

    @Override
    List<SaleNotificationRequest> computeNotificationRequests(City city, Collection<Sale> sales) {
        newSales = new ArrayList<>(sales);
        newSales.sort(COMPARATOR);

        if (oldSales.isEmpty()) {
            return List.of();
        }

        Sale lastSale = oldSales.get(0);
        Sale currentSale = newSales.get(0);
        Integer floorPrice = floorPrices.get(city);
        if (floorPrice == null || currentSale.getPrice() != floorPrice) {
            floorPrices.put(city, currentSale.getPrice());
        }
        return List.of(new SaleNotificationRequest(currentSale, lastSale, oldSales));
    }

    @Override
    void updateOldSales(City city) {
        oldSales.clear();
        oldSales.addAll(newSales);
        newSales.clear();
        newSales = null;
    }

    @Override
    int computeRatingForMargin(int marginPercentage) {
        if (marginPercentage >= 24) {
            return 3;
        } else if (marginPercentage >= 12) {
            return 2;
        } else if (marginPercentage >= 6) {
            return 1;
        } else {
            return 0;
        }
    }
}