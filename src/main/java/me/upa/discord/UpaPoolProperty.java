package me.upa.discord;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class UpaPoolProperty {
    private final long propertyId;
    private final String address;

    private final String cityName;

    private final String descriptiveAddress;

    private final int mintPrice;

    private final AtomicInteger cost = new AtomicInteger();
    private final int up2;
    private final long donorMemberId;

    private final AtomicBoolean verified = new AtomicBoolean();

    public UpaPoolProperty(long propertyId, String address, String cityName, int mintPrice, int cost, int up2, long donorMemberId) {
        this.propertyId = propertyId;
        this.address = address;
        this.mintPrice = mintPrice;
        this.cost.set(cost);
        this.up2 = up2;
        this.donorMemberId = donorMemberId;
        this.cityName = cityName;
        descriptiveAddress = address + ", " + cityName;
    }

    public long getPropertyId() {
        return propertyId;
    }

    public String getAddress() {
        return address;
    }

    public String getDescriptiveAddress() {
        return descriptiveAddress;
    }

    public int getMintPrice() {
        return mintPrice;
    }

    public AtomicInteger getCost() {
        return cost;
    }

    public int getUp2() {
        return up2;
    }

    public long getDonorMemberId() {
        return donorMemberId;
    }

    public AtomicBoolean getVerified() {
        return verified;
    }
}
