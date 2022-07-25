package me.upa.fetcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.game.BlockchainMint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MintedBlockchainDataFetcher extends BlockchainDataFetcher<List<BlockchainMint>> {
    private final Instant resultsAfter;

    public MintedBlockchainDataFetcher(Instant resultsAfter) {
        this.resultsAfter = resultsAfter;
    }

    @Override
    public List<BlockchainMint> handleResponse(String link, JsonElement response) {
        JsonObject queryObject = response.getAsJsonObject();
        JsonArray actions = queryObject.get("actions").getAsJsonArray();


        List<BlockchainMint> mints = new ArrayList<>();

        return mints;
    }

    @Override
    public String getLink() {
        return null;
    }
}
