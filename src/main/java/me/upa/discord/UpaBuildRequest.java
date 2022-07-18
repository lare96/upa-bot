package me.upa.discord;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import org.checkerframework.checker.units.qual.A;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class UpaBuildRequest {

    private final long memberId;
    private final long propertyId;
    private final String structureName;

    private final AtomicBoolean notified = new AtomicBoolean();

    private final transient AtomicReference<String> address = new AtomicReference<>();

    public UpaBuildRequest(long memberId, long propertyId, String structureName) {
        this.memberId = memberId;
        this.propertyId = propertyId;
        this.structureName = structureName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpaBuildRequest that = (UpaBuildRequest) o;
        return memberId == that.memberId;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("member_id", memberId).add("structure_name", structureName).toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(memberId);
    }
    
    public long getMemberId() {
        return memberId;
    }

    public long getPropertyId() {
        return propertyId;
    }

    public String getStructureName() {
        return structureName;
    }

    public AtomicBoolean getNotified() {
        return notified;
    }

    public void setAddress(String newAddress) {
        address.compareAndSet(null, newAddress);
    }

    public String getAddress() {
        return address.get();
    }
}
