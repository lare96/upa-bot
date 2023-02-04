package me.upa.fetcher;

import com.google.common.primitives.Doubles;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.game.BlockchainPurchase;
import me.upa.game.BlockchainSale;
import me.upa.game.BlockchainSale.BlockchainSaleType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PurchasesBlockchainDataFetcher extends BlockchainDataFetcher<List<BlockchainPurchase>> {
    private final Logger logger = LogManager.getLogger();
    private final Instant resultsAfter;

    public PurchasesBlockchainDataFetcher(Instant resultsAfter) {
        this.resultsAfter = resultsAfter;
    }

    @Override
    public List<BlockchainPurchase> handleResponse(String link, JsonElement response) {
        List<BlockchainPurchase> purchases = new ArrayList<>();
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
            String memo = data.get("memo").getAsString();
            String originalMemo = memo;
            memo = memo.replace("This transaction notarizes that Upland user ", "");
            memo = memo.substring(memo.indexOf(" ") + 1);
            memo = memo.replace("with EOS account ", "");
            memo = memo.substring(memo.indexOf(" ") + 1);
            memo = memo.replace("owns ", "");
            String[] memoData = memo.split(",");
            String address = null;
            if (memoData.length != 2 && memoData.length != 3) {
                if (memo.contains("Rio de Janeiro")) {
                    StringBuilder sb = new StringBuilder();
                    for (String s : memoData) {
                        if (s.trim().equals("Rio de Janeiro"))
                            break;
                        sb.append(s.trim()).append(", ");
                    }
                    sb.setLength(sb.length() - 2);
                    address = sb.toString().toUpperCase();
                } else {
                    logger.warn(data.get("memo").getAsString());
                    continue;
                }
            }
            if(address == null) {
                address = memoData[0];
            }
            String cityName = memoData[1].trim();
            long propertyId = Long.parseLong(data.get("a45").getAsString());
            purchases.add(new BlockchainPurchase(propertyId, address, cityName, timestamp));
        }
        return purchases;
    }

    @Override
    public String getLink() {
        String link = resultsAfter.toString();
        return "https://eos.hyperion.eosrio.io/v2/history/get_actions?after=" + link.substring(0, link.length() - 1) + "&filter=*%3An5&skip=0&limit=100&sort=asc&noBinary=true";
    }
}
