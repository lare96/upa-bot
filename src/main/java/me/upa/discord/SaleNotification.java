package me.upa.discord;

import com.google.common.base.MoreObjects;
import me.upa.game.Sale;

/**
 * Represents a pending undervalued property Discord notification.
 *
 * @author lare96
 */
public final class SaleNotification {

    private final Sale sale;
    private final int marginPrice;
    private final double marginPercentage;
    private final int rating;
    private final SaleNotificationType type;

    public SaleNotification(Sale sale, int marginPrice, double marginPercentage, int rating, SaleNotificationType type) {
        this.sale = sale;
        this.marginPrice = marginPrice;
        this.marginPercentage = marginPercentage;
        this.rating = rating;
        this.type = type;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("sale", sale)
                .add("marginPrice", marginPrice)
                .add("marginPercentage", marginPercentage)
                .add("rating", rating)
                .add("type", type)
                .toString();
    }

    public Sale getSale() {
        return sale;
    }

    public int getMarginPrice() {
        return marginPrice;
    }

    public double getMarginPercentage() {
        return marginPercentage;
    }
    public int getRating() {
        return rating;
    }

    public SaleNotificationType getType() {
        return type;
    }

}