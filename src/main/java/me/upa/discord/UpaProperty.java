package me.upa.discord;

import com.google.common.base.Objects;

import java.util.concurrent.atomic.AtomicReference;

public final class UpaProperty {
    private final int memberKey;
    private final String address;
    private final long propertyId;
    private final AtomicReference<String> buildStatus = new AtomicReference<>();
    private final String node;
    private final int up2;
    private final boolean genesis;

    public UpaProperty(int memberKey, String address, long propertyId, String buildStatus, String node, int up2, boolean genesis) {
        this.memberKey = memberKey;
        this.address = address;
        this.propertyId = propertyId;
        this.buildStatus.set(buildStatus);
        this.node = node;
        this.up2 = up2;
        this.genesis = genesis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpaProperty property = (UpaProperty) o;
        return propertyId == property.propertyId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(propertyId);
    }

    public boolean hasBuild() {
        return !buildStatus.get().equals("Not started");
    }

    public int getMemberKey() {
        return memberKey;
    }

    public String getAddress() {
        return address;
    }

    public long getPropertyId() {
        return propertyId;
    }

    public AtomicReference<String> getBuildStatus() {
        return buildStatus;
    }

    public String getNode() {
        return node;
    }

    public int getUp2() {
        return up2;
    }

    public boolean isGenesis() {
        return genesis;
    }
}
