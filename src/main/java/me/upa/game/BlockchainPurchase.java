package me.upa.game;

import com.google.common.base.MoreObjects;

import java.time.Instant;

public class BlockchainPurchase {

    private final long propertyId;
    private final String address;

    private final String cityName;
    private final Instant timestamp;

    public BlockchainPurchase(long propertyId, String address, String cityName, Instant timestamp) {
        this.propertyId = propertyId;
        this.address = address;
        this.cityName = cityName;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("property_id", propertyId).
                add("address", address).add("city_name", cityName).toString();
    }

    public long getPropertyId() {
        return propertyId;
    }

    public String getAddress() {
        return address;
    }

    public String getCityName() {
        return cityName;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
