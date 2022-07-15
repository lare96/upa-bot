package me.upa.fetcher.blockchain;

import com.google.gson.JsonElement;

import java.time.Instant;

public abstract class BlockchainAction {

    private final String transactionId;
    private final Instant timestamp;

    public BlockchainAction(String transactionId, Instant timestamp) {
        this.transactionId = transactionId;
        this.timestamp = timestamp;
    }

    public abstract boolean matches(JsonElement data);

    public String getTransactionId() {
        return transactionId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}