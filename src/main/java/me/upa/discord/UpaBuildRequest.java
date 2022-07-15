package me.upa.discord;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpaBuildRequest implements Serializable {

    private static final long serialVersionUID = -4138812405341941388L;
    private final long memberId;
    private final long propertyId;
    private final String structureName;

    private final AtomicBoolean notified = new AtomicBoolean();

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
}
