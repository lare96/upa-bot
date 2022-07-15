package me.upa.discord;

import com.google.common.base.MoreObjects;
import me.upa.game.Sale;

import java.util.List;

public final class SaleNotificationRequest {
    private final Sale currentSale;
    private final Sale lastSale;
    private final List<Sale> oldSales;

    public SaleNotificationRequest(Sale currentSale, Sale lastSale, List<Sale> oldSales) {
        this.currentSale = currentSale;
        this.lastSale = lastSale;
        this.oldSales = oldSales;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("currentSale", currentSale)
                .add("lastSale", lastSale)
                .toString();
    }

    public Sale getCurrentSale() {
        return currentSale;
    }

    public Sale getLastSale() {
        return lastSale;
    }

    public List<Sale> getOldSales() {
        return oldSales;
    }
}