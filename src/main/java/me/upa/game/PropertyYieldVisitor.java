package me.upa.game;

public final class PropertyYieldVisitor {

    private final int fee;
    private final String propertyId;
    private final String eosId;

    public PropertyYieldVisitor(int fee, String propertyId, String eosId) {
        this.fee = fee;
        this.propertyId = propertyId;
        this.eosId = eosId;
    }

    public int getFee() {
        return fee;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public String getEosId() {
        return eosId;
    }
}