package me.upa.discord.history;

import java.io.Serializable;
import java.time.Instant;

public class PacTransaction implements Serializable {

    private static final long serialVersionUID = 468468548635519079L;
    private final int amount;
    private final String reason;

    private final long memberId;

    private final Instant timestamp;

    public PacTransaction(int amount, String reason, long memberId, Instant timestamp) {
        this.amount = amount;
        this.reason = reason;
        this.memberId = memberId;
        this.timestamp = timestamp;
    }

    public int getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public long getMemberId() {
        return memberId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
