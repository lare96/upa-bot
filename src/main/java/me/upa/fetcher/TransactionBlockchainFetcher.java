package me.upa.fetcher;

import com.google.gson.JsonElement;

public abstract class TransactionBlockchainFetcher<T> extends BlockchainDataFetcher<T> {

    private final String transactionId;

    public TransactionBlockchainFetcher(String transactionId) {
        this.transactionId = transactionId;
    }

    @Override
    public String getLink() {
        return "https://eos.hyperion.eosrio.io/v2/history/get_transaction?id=" + transactionId;
    }
}
