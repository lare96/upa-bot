package me.upa.discord;

import com.google.common.base.Objects;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class Scholar {
    private final String username;
    private final String address;
    private final long propertyId;
    private final AtomicInteger netWorth = new AtomicInteger();
    private final long memberId;
    private final AtomicBoolean sponsored = new AtomicBoolean();

    private final AtomicReference<Instant> lastFetchInstant = new AtomicReference<>();

    public Scholar(String username, String address, long propertyId, int netWorth, long memberId, boolean sponsored, Instant lastFetchInstant) {
        this.username = username;
        this.address = address;
        this.propertyId = propertyId;
        this.netWorth.set(netWorth);
        this.memberId = memberId;
        this.sponsored.set(sponsored);
        this.lastFetchInstant.set(lastFetchInstant);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Scholar scholar = (Scholar) o;
        return Objects.equal(memberId, scholar.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(memberId);
    }

    public int getCompareValue() {
        return sponsored.get() ? 1 : 0;
    }

    public String getUsername() {
        return username;
    }

    public String getAddress() {
        return address;
    }

    public long getPropertyId() {
        return propertyId;
    }

    public AtomicInteger getNetWorth() {
        return netWorth;
    }

    public long getMemberId() {
        return memberId;
    }

    public AtomicBoolean getSponsored() {
        return sponsored;
    }

    public AtomicReference<Instant> getLastFetchInstant() {
        return lastFetchInstant;
    }
}