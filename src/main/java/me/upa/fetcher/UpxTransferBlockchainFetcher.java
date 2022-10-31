package me.upa.fetcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.game.BlockchainPurchase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class UpxTransferBlockchainFetcher extends BlockchainDataFetcher<Set<String>> {

    @Override
    public Set<String> handleResponse(String link, JsonElement response) {
        Set<String> transactions = new HashSet<>();
        JsonObject responseObject = response.getAsJsonObject();
        JsonArray actions = responseObject.get("actions").getAsJsonArray();
        for (JsonElement nextAction : actions) {
            JsonObject nextActionObject = nextAction.getAsJsonObject();
            String transactionId = nextActionObject.get("trx_id").getAsString();
            JsonObject act = nextActionObject.get("act").getAsJsonObject();
            String account = act.get("account").getAsString();
            if (!account.equals("upxtokenacct")) {
                continue;
            }
            JsonObject data = act.get("data").getAsJsonObject();
            String memo = data.get("memo").getAsString();
            if(!memo.isEmpty()) {
                continue;
            }
            transactions.add(transactionId);
        }
        return transactions;
    }

    @Override
    public String getLink() {
        return "https://eos.hyperion.eosrio.io/v2/history/get_actions?account=dqaiankghiqu&sort=desc&act.name=transfer&act.account=upxtokenacct&act.authorization.actor=playuplandme";
    }
}
