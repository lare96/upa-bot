package me.upa.discord;

import java.util.List;

public class SaleNotificationRequestPair {
    private final List<SaleNotificationRequest> requests;
    private final int floorPrice;

    public SaleNotificationRequestPair(List<SaleNotificationRequest> requests, int floorPrice) {
        this.requests = requests;
        this.floorPrice = floorPrice;
    }

    public List<SaleNotificationRequest> getRequests() {
        return requests;
    }

    public int getFloorPrice() {
        return floorPrice;
    }
}
