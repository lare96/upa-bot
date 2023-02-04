package me.upa.discord;

import me.upa.discord.listener.credit.CreditTransaction.CreditTransactionType;

import java.time.Instant;

public final class PacHistoryStatement {
    private final long memberId;
    private final int amount;

    private final CreditTransactionType type;
    private final String reason;
    private final Instant timestamp;

    public PacHistoryStatement(long memberId, int amount, CreditTransactionType type, String reason, Instant timestamp) {
        this.memberId = memberId;
        this.amount = amount;
        this.type = type;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public long getMemberId() {
        return memberId;
    }

    public int getAmount() {
        return amount;
    }

    public CreditTransactionType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
