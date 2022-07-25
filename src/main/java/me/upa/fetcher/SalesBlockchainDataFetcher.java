package me.upa.fetcher;

import com.google.common.primitives.Doubles;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.game.BlockchainSale;
import me.upa.game.BlockchainSale.BlockchainSaleType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SalesBlockchainDataFetcher extends BlockchainDataFetcher<List<BlockchainSale>> {

    private final Instant resultsAfter;

    public SalesBlockchainDataFetcher(Instant resultsAfter) {
        this.resultsAfter = resultsAfter;
    }

    @Override
    public List<BlockchainSale> handleResponse(String link, JsonElement response) {
        List<BlockchainSale> sales = new ArrayList<>();
        JsonObject responseObject = response.getAsJsonObject();
        JsonArray actions = responseObject.get("actions").getAsJsonArray();
        for (JsonElement nextAction : actions) {
            JsonObject nextActionObject = nextAction.getAsJsonObject();
            long blockNumber = nextActionObject.get("block_num").getAsLong();
            String transactionId = nextActionObject.get("trx_id").getAsString();
            Instant timestamp = Instant.parse(nextActionObject.get("timestamp").getAsString() + "Z");
            JsonObject act = nextActionObject.get("act").getAsJsonObject();
            String account = act.get("account").getAsString();
            if (!account.equals("playuplandme")) {
                continue;
            }
            JsonObject data = act.get("data").getAsJsonObject();
            String blockchainAccountId = data.get("a54").getAsString();
            long propertyId = Long.parseLong(data.get("a45").getAsString());
            Double amount = Doubles.tryParse(data.get("p11").getAsString().replace("UPX", "").trim());
            if (amount == null || amount == 0) {
                continue;
            }
            sales.add(new BlockchainSale(blockNumber, transactionId, propertyId, amount.intValue(), BlockchainSaleType.UPX, blockchainAccountId, timestamp));
        }
        return sales;
    }

    @Override
    public String getLink() {
        String link = resultsAfter.toString();
        return "https://eos.hyperion.eosrio.io/v2/history/get_actions?after=" + link.substring(0, link.length() - 1) + "&skip=0&limit=5&sort=desc&act.name=n2";
    }
}
