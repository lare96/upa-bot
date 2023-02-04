package me.upa.discord;

import com.google.common.base.Objects;
import me.upa.game.Node;
import org.checkerframework.checker.units.qual.A;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class UpaProperty {
    private final int memberKey;
    private final String address;
    private final long propertyId;
    private final AtomicReference<String> buildStatus = new AtomicReference<>();
    private final Node node;
    private final int up2;
    private final boolean genesis;

    private final AtomicBoolean active = new AtomicBoolean();

    public UpaProperty(int memberKey, String address, long propertyId, String buildStatus, Node node, int up2, boolean genesis, boolean active) {
        this.memberKey = memberKey;
        this.address = address;
        this.propertyId = propertyId;
        this.buildStatus.set(buildStatus);
        this.node = node;
        this.up2 = up2;
        this.genesis = genesis;
        this.active.set(active);
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

    public Node getNode() {
        return node;
    }

    public int getUp2() {
        return up2;
    }

    public boolean isGenesis() {
        return genesis;
    }

    public AtomicBoolean getActive() {
        return active;
    }
}
