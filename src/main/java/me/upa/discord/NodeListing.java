package me.upa.discord;

import com.google.common.base.Objects;
import me.upa.game.Node;

import java.io.Serializable;
import java.time.Instant;

public final class NodeListing implements Serializable {
    private static final long serialVersionUID = -3872574343440854252L;

    private final long propertyId;
    private final String address;
    private final String currency;
    private final long mintPrice;
    private final int area;
    private final Node node;
    private final int price;
    private final long memberId;
    private final String description;
    private final String imageLink;

    private final Instant listedOn;
    public NodeListing(long propertyId, String address, String currency, long mintPrice, int area, Node node, int price, long memberId, String description, String imageLink, Instant listedOn) {
        this.propertyId = propertyId;
        this.address = address;
        this.currency = currency;
        this.mintPrice = mintPrice;
        this.area = area;
        this.node = node;
        this.price = price;
        this.memberId = memberId;
        this.description = description;
        this.imageLink = imageLink;
        this.listedOn = listedOn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeListing that = (NodeListing) o;
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

    public String getCurrency() {
        return currency;
    }

    public long getMintPrice() {
        return mintPrice;
    }

    public int getArea() {
        return area;
    }

    public Node getNode() {
        return node;
    }

    public int getPrice() {
        return price;
    }

    public long getMemberId() {
        return memberId;
    }

    public String getDescription() {
        return description;
    }

    public String getImageLink() {
        return imageLink;
    }

    public Instant getListedOn() {
        return listedOn;
    }
}
