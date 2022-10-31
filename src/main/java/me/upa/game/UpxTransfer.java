package me.upa.game;

import java.time.Instant;

public final class UpxTransfer {

    private final String fromBlockchainId;
    private final int amount;
    private final Instant timestamp;

    public UpxTransfer(String fromBlockchainId,  int amount, Instant timestamp) {
        this.fromBlockchainId = fromBlockchainId;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public String getFromBlockchainId() {
        return fromBlockchainId;
    }

    public int getAmount() {
        return amount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
