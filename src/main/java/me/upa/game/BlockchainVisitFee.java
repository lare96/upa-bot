package me.upa.game;

import java.time.Instant;

public final class BlockchainVisitFee  {

    private final long amount;
    private final Instant lastFee;

    public BlockchainVisitFee(long amount, Instant lastFee) {
        this.amount = amount;
        this.lastFee = lastFee;
    }

    public long getAmount() {
        return amount;
    }

    public Instant getLastTimestamp() {
        return lastFee;
    }
}
