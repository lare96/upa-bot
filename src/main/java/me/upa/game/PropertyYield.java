package me.upa.game;

import java.time.Instant;
import java.util.List;

public final class PropertyYield {

    private final String propertyId;

    /**
     * The hours since the last yield was claimed.
     */
    private final Instant lastClaimed;


    private final List<PropertyYieldVisitor> visitors;

    public PropertyYield(String propertyId, Instant lastClaimed, List<PropertyYieldVisitor> visitors) {
        this.propertyId = propertyId;
        this.lastClaimed = lastClaimed;
        this.visitors = visitors;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public Instant getLastClaimed() {
        return lastClaimed;
    }

    public List<PropertyYieldVisitor> getVisitors() {
        return visitors;
    }
}
