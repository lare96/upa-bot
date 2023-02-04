package me.upa.game;

import com.google.common.base.Objects;

public final class CachedProperty {

    private final long propertyId;
    private final String address;
    private final int area;
    private final int neighborhoodId;
    private final int cityId;

    private final long mintPrice;
    public CachedProperty(long propertyId, String address, int area, int neighborhoodId, int cityId, long mintPrice) {
        this.propertyId = propertyId;
        this.address = address;
        this.area = area;
        this.neighborhoodId = neighborhoodId;
        this.cityId = cityId;
        this.mintPrice = mintPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CachedProperty that = (CachedProperty) o;
        return propertyId == that.propertyId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(propertyId);
    }

    public long getPropertyId() {
        return propertyId;
    }

    public String getAddress() {
        return address;
    }

    public int getArea() {
        return area;
    }

    public int getNeighborhoodId() {
        return neighborhoodId;
    }

    public int getCityId() {
        return cityId;
    }

    public long getMintPrice() {
        return mintPrice;
    }
}
