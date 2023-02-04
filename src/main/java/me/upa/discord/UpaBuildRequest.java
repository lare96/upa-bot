package me.upa.discord;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import me.upa.UpaBotContext;
import me.upa.game.Property;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpaBuildRequest {

    public static final class UpaBuildRequestComparator implements Comparator<UpaBuildRequest> {

        private final UpaBotContext ctx;
        private final boolean global;

        public UpaBuildRequestComparator(UpaBotContext ctx, boolean global) {
            this.ctx = ctx;
            this.global = global;
        }

        @Override
        public int compare(UpaBuildRequest o1, UpaBuildRequest o2) {
            UpaMember o1Mem = ctx.databaseCaching().getMembers().get(o1.getMemberId());
            UpaMember o2Mem = ctx.databaseCaching().getMembers().get(o2.getMemberId());
            double o1Ssh = o1Mem.getTotalSsh(global);
            double o2Ssh = o2Mem.getTotalSsh(global);
            if(o1.hasBadResponse() || !o1Mem.getActive().get()) {
                o1Ssh = Double.MIN_VALUE;
            }
            if(o2.hasBadResponse() || !o2Mem.getActive().get()) {
                o2Ssh = Double.MIN_VALUE;
            }
            return Double.compare(o2Ssh, o1Ssh);
        }
    }

    public enum BuildRequestResponse {
        HAS_ACTIVE_BUILD("Already have an active build."),
        TOO_MANY_BUILDS("Too many recent builds (More than 50% of last 10 builds)."),
        HAS_STRUCTURE("Already has structure."),
        ERROR("Backend error."),
        NOT_ENOUGH_SSH("Not enough SSH."),

        NOT_STARTED("Build has not been started."),

        NORMAL("Next to be added to the queue.");

        private final String formattedName;

        BuildRequestResponse(String formattedName) {
            this.formattedName = formattedName;
        }

        public String getFormattedName() {
            return formattedName;
        }
    }

    private final long memberId;
    private final long propertyId;
    private final String structureName;

    private final AtomicBoolean notified = new AtomicBoolean();

    private final String address;

    private volatile BuildRequestResponse response = BuildRequestResponse.NORMAL;
private volatile Property loadedProperty;
    public UpaBuildRequest(long memberId, long propertyId, String structureName, String address) {
        this.memberId = memberId;
        this.propertyId = propertyId;
        this.structureName = structureName;
        this.address = address;
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

    public boolean hasBadResponse() {
        switch (response) {
            case NORMAL:
            case NOT_ENOUGH_SSH:
            case ERROR:
                return false;
        }
        return true;
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

    public String getAddress() {
        if (address == null)
            return propertyId + "";
        return address;
    }

    public BuildRequestResponse getResponse() {
        return response;
    }

    public void setResponse(BuildRequestResponse response) {
        this.response = response;
    }

    public Property getLoadedProperty() {
        return loadedProperty;
    }

    public void setLoadedProperty(Property loadedProperty) {
        this.loadedProperty = loadedProperty;
    }
}
