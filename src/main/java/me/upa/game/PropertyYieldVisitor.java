package me.upa.game;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public final class PropertyYieldVisitor {

    private final int fee;
    private final long propertyId;
    private final String eosId;

    public PropertyYieldVisitor(int fee, long propertyId, String eosId) {
        this.fee = fee;
        this.propertyId = propertyId;
        this.eosId = eosId;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("fee", fee)
                .add("propertyId", propertyId)
                .add("eosId", eosId)
                .toString();
    }

    public int getFee() {
        return fee;
    }

    public long getPropertyId() {
        return propertyId;
    }

    public String getEosId() {
        return eosId;
    }
}