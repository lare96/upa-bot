package me.upa.game;

import java.time.Instant;
import java.util.List;

public final class PropertyYield {

    private final long propertyId;

    /**
     * The hours since the last yield was claimed.
     */
    private final Instant lastClaimed;


    private final List<PropertyYieldVisitor> visitors;

    public PropertyYield(long propertyId, Instant lastClaimed, List<PropertyYieldVisitor> visitors) {
        this.propertyId = propertyId;
        this.lastClaimed = lastClaimed;
        this.visitors = visitors;
    }

    public long getPropertyId() {
        return propertyId;
    }

    public Instant getLastClaimed() {
        return lastClaimed;
    }

    public List<PropertyYieldVisitor> getVisitors() {
        return visitors;
    }
}
