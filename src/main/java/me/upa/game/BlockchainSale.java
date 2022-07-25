package me.upa.game;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import java.time.Instant;

public final class BlockchainSale {


    public enum BlockchainSaleType {
        UPX, FIAT
    }

    private final long blockNumber;

    private final String transactionId;
    private final long propertyId;
    private final int amount;
    private final BlockchainSaleType type;
    private final String blockchainAccountId;
    private final Instant timestamp;
    public BlockchainSale(long blockNumber, String transactionId, long propertyId, int amount, BlockchainSaleType type,
                          String blockchainAccountId, Instant timestamp) {
        this.blockNumber = blockNumber;
        this.transactionId = transactionId;
        this.propertyId = propertyId;
        this.amount = amount;
        this.type = type;
        this.blockchainAccountId = blockchainAccountId;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("propertyId", propertyId)
                .add("amount", amount)
                .add("type", type)
                .add("blockchainAccountId", blockchainAccountId)
                .add("timestamp", timestamp)
                .toString();
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public long getPropertyId() {
        return propertyId;
    }

    public int getAmount() {
        return amount;
    }

    public BlockchainSaleType getType() {
        return type;
    }

    public String getBlockchainAccountId() {
        return blockchainAccountId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
