package me.upa.game;

import java.time.Instant;

public final class Property {

    private final long propId;
    private final int cityId;
    private final String fullAddress;
    private final int area;
    private final String status;
    private final String buildStatus;
    private final int price;

    private final int mintPrice;
    private double[][] coordinates;
    private final String ownerUsername;
    private final String owner;
    private final double buildPercentage;
    private final double stakedSpark;
    private final int totalSparksRequired;
    private final double progressInSpark;
    private final int maxStakedSpark;
    private final Instant finishedAt;

    public Property(long propId, int cityId, String fullAddress, int area, String status, String buildStatus, double buildPercentage, int price, int mintPrice, double[][] coordinates, String ownerUsername, String owner, double stakedSpark, int totalSparksRequired, double progressInSpark, int maxStakedSpark, Instant finishedAt) {
        this.propId = propId;
        this.cityId = cityId;
        this.fullAddress = fullAddress;
        this.area = area;
        this.status = status;
        this.buildStatus = buildStatus;
        this.buildPercentage = buildPercentage;
        this.price = price;
        this.mintPrice = mintPrice;
        this.coordinates = coordinates;
        this.ownerUsername = ownerUsername;
        this.owner = owner;
        this.stakedSpark = stakedSpark;
        this.totalSparksRequired = totalSparksRequired;
        this.progressInSpark = progressInSpark;
        this.maxStakedSpark = maxStakedSpark;
        this.finishedAt = finishedAt;
    }

    public long getPropId() {
        return propId;
    }

    public int getCityId() {
        return cityId;
    }

    public String getFullAddress() {
        return fullAddress;
    }

    public int getArea() {
        return area;
    }

    public String getStatus() {
        return status;
    }

    public int getPrice() {
        return price;
    }

    public int getMintPrice() {
        return mintPrice;
    }

    public double[][] getCoordinates() {
        return coordinates;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public String getOwner() {
        return owner;
    }

    public String getBuildStatus() {
        return buildStatus;
    }

    public double getBuildPercentage() {
        return buildPercentage;
    }

    public double getStakedSpark() {
        return stakedSpark;
    }

    public int getTotalSparksRequired() {
        return totalSparksRequired;
    }

    public double getProgressInSpark() {
        return progressInSpark;
    }

    public int getMaxStakedSpark() {
        return maxStakedSpark;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
