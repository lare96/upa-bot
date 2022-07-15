package me.upa.game;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import me.upa.discord.DiscordService;
import me.upa.fetcher.DataFetcherManager;

import java.util.OptionalInt;

/**
 * Represents a single property sale in Upland.
 *
 * @author lare96
 */
public final class Sale {

    /**
     * The city identifier.
     */
    private final int cityId;

    /**
     * The neighborhood identifier.
     */
    private final OptionalInt neighborhoodId;

    /**
     * The property identifier.
     */
    private final String propertyId;

    /**
     * The current sale price.
     */
    private final int price;

    /**
     * The mint price.
     */
    private final int mintPrice;

    /**
     * The size of the property in UP2.
     */
    private final int size;

    /**
     * The markup from the mint price.
     */
    private final int markup;

    /**
     * The collection name, if any.
     */
    private final String collectionName;

    /**
     * The address of the property.
     */
    private final String address;

    /**
     * The owner of the property.
     */
    private final String owner;

    /**
     * Creates a new {@link Sale}.
     *
     * @param cityId The city identifier.
     * @param neighborhoodId The neighborhood identifier.
     * @param propertyId The property identifier.
     * @param price The current sale price.
     * @param mintPrice The mint price.
     * @param size The size of the property in UP2.
     * @param markup The markup from the mint price.
     * @param address The address of the property.
     * @param owner The owner of the property.
     */
    public Sale(int cityId, int neighborhoodId, String propertyId, int price, int mintPrice, int size,
                String collectionName, int markup, String address, String owner) {
        this.neighborhoodId = neighborhoodId == -1 ? OptionalInt.empty() : OptionalInt.of(neighborhoodId);
        this.cityId = cityId;
        this.propertyId = propertyId;
        this.price = price;
        this.mintPrice = mintPrice;
        this.size = size;
        this.collectionName = collectionName;
        this.markup = markup;
        this.address = address;
        this.owner = owner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Sale sale = (Sale) o;
        return Objects.equal(address, sale.address); // TODO change back to property id
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(address);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("cityId", cityId)
                .add("neighborhoodId", neighborhoodId)
                .add("propertyId", propertyId)
                .add("price", DiscordService.COMMA_FORMAT.format(price))
                .add("mintPrice", DiscordService.COMMA_FORMAT.format(mintPrice))
                .add("size", size)
                .add("markup", markup)
                .add("address", address)
                .add("owner", owner)
                .toString();
    }

    public OptionalInt getNeighborhoodId() {
        return neighborhoodId;
    }

    public Neighborhood getNeighborhood() {
        if (neighborhoodId.isEmpty()) {
            return null;
        }
        return DataFetcherManager.getNeighborhoodMap().get(neighborhoodId.getAsInt());
    }

    public String getNeighborhoodName() {
        Neighborhood neighborhood = getNeighborhood();
        if(neighborhood == null) {
            City city = DataFetcherManager.getCityMap().get(cityId);
            if (city == null) {
                throw new IllegalStateException("No city found for sale [" + propertyId + "].");
            }
            return city.getName();
        }
        return neighborhood.getName();
    }

    public int getCityId() {
        return cityId;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public int getPrice() {
        return price;
    }

    public int getMintPrice() {
        return mintPrice;
    }

    public int getSize() {
        return size;
    }

    public int getMarkup() {
        return markup;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public String getAddress() {
        return address;
    }

    public String getOwner() {
        return owner;
    }
}
