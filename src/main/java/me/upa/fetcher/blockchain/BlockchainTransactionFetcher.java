package me.upa.fetcher.blockchain;

import com.google.common.collect.Range;
import com.google.gson.JsonElement;
import me.upa.fetcher.BlockchainDataFetcher;

import java.time.Instant;
import java.util.List;

public final class BlockchainTransactionFetcher<T extends BlockchainAction> extends BlockchainDataFetcher<List<T>> {

    public static <V extends BlockchainAction> BlockchainTransactionFetcherBuilder<V> newBuilder() {
        return new BlockchainTransactionFetcherBuilder<>();
    }

    private static final class BlockchainTransactionFetcherBuilder<T extends BlockchainAction> {
        private String blockchainAccountId;
        private int limit = 25;
        private Instant fromBefore;
        private Instant fromAfter;
        private boolean recentOrdering = true;

        public BlockchainTransactionFetcherBuilder setBlockchainAccountId(String blockchainAccountId) {
            this.blockchainAccountId = blockchainAccountId;
            return this;
        }

        public BlockchainTransactionFetcherBuilder setLimit(int limit) {
            this.limit = limit;
            return this;
        }

        public BlockchainTransactionFetcherBuilder setFromBefore(Instant fromBefore) {
            this.fromBefore = fromBefore;
            return this;
        }

        public BlockchainTransactionFetcherBuilder setFromAfter(Instant fromAfter) {
            this.fromAfter = fromAfter;
            return this;
        }

        public BlockchainTransactionFetcherBuilder setRecentOrdering(boolean recentOrdering) {
            this.recentOrdering = recentOrdering;
            return this;
        }

        public BlockchainTransactionFetcher build() {
            return new BlockchainTransactionFetcher(blockchainAccountId, limit, fromBefore, fromAfter, recentOrdering);
        }
    }
    private final String blockchainAccountId;
    private final int limit;
    private final Instant fromBefore;
    private final Instant fromAfter;
    private final boolean recentOrdering;

    private BlockchainTransactionFetcher(String blockchainAccountId, int limit, Instant fromBefore, Instant fromAfter, boolean recentOrdering) {
        this.blockchainAccountId = blockchainAccountId;
        this.limit = limit;
        this.fromBefore = fromBefore;
        this.fromAfter = fromAfter;
        this.recentOrdering = recentOrdering;
    }

    @Override
    public List<T> handleResponse(String link, JsonElement response) {
        return null;
    }

    @Override
    public String getLink() {
        String str = "https://eos.hyperion.eosrio.io/v2/history/get_actions?account=" + blockchainAccountId + "&limit=" + limit + "&sort=" + (recentOrdering ? "desc" : "asc");
        if (fromBefore != null) {
            str += "&before=" + fromBefore;
        }
        if(fromAfter != null) {
            str += "&after=" + fromAfter;
        }
        return str;
    }
}
