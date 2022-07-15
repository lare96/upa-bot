package me.upa.discord;

import com.google.common.base.Objects;
import com.google.common.util.concurrent.AtomicDouble;

import java.io.Serializable;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A build slot on the spark train.
 *
 * @author lare96
 */
public final class UpaBuildSlot implements Serializable {

    private static final long serialVersionUID = -7029883570987012063L;
    private final long memberId;
    private final long propertyId;
    private final String structureName;
    private final String address;

    private final int maxSparkStaked;

    private final AtomicReference<Instant> finishedAt = new AtomicReference<>();

    private final AtomicDouble sparkStaked = new AtomicDouble();
    private final AtomicDouble completionPercent = new AtomicDouble();


    public UpaBuildSlot(long memberId, long propertyId, String structureName, String address, int maxSparkStaked, Instant finishedAt, double sparkStaked, double completionPercent) {
        this.memberId = memberId;
        this.propertyId = propertyId;
        this.structureName = structureName;
        this.address = address;
        this.maxSparkStaked = maxSparkStaked;
        this.finishedAt.set(finishedAt);
        this.sparkStaked.set(sparkStaked);
        this.completionPercent.set(completionPercent);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpaBuildSlot that = (UpaBuildSlot) o;
        return memberId == that.memberId;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(memberId);
    }

    public double getFillPercent() {
        return (sparkStaked.get() / maxSparkStaked) * 100.0;
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

    public String getAddress() {
        return address;
    }

    public int getMaxSparkStaked() {
        return maxSparkStaked;
    }

    public AtomicReference<Instant> getFinishedAt() {
        return finishedAt;
    }

    public AtomicDouble getSparkStaked() {
        return sparkStaked;
    }

    public AtomicDouble getCompletionPercent() {
        return completionPercent;
    }
}
