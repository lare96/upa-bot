package me.upa.fetcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.game.BlockchainVisitFee;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.slf4j.ILoggerFactory;

import java.time.Instant;

public final class VisitFeeBlockchainDataFetcher extends BlockchainDataFetcher<BlockchainVisitFee> {

    private final String blockchainId;
    private final Instant after;

    private final Instant before;

    public VisitFeeBlockchainDataFetcher(String blockchainId, Instant after, Instant before) {
        this.blockchainId = blockchainId;
        this.after = after;
        this.before = before;
    }

    private static final Logger logger = LogManager.getLogger();
    @Override
    public BlockchainVisitFee handleResponse(String link, JsonElement response) {
        try {
            double amount = 0;
            JsonObject responseObject = response.getAsJsonObject();
            JsonArray actions = responseObject.get("actions").getAsJsonArray();
            if (actions.isEmpty()) {
                return new BlockchainVisitFee(0, null);
            }
            int lastIndex = actions.size() - 1;
            int index = 0;
            Instant lastFee = null;
            for (JsonElement nextAction : actions) {
                JsonObject nextActionObject = nextAction.getAsJsonObject();
                long blockNumber = nextActionObject.get("block_num").getAsLong();
                String transactionId = nextActionObject.get("trx_id").getAsString();
                Instant timestamp = Instant.parse(nextActionObject.get("timestamp").getAsString() + "Z");
                JsonObject act = nextActionObject.get("act").getAsJsonObject();
                String account = act.get("account").getAsString();
                String name = act.get("name").getAsString();
                if (index++ == lastIndex) {
                    lastFee = timestamp;
                }
                if (!account.equals("upxtokenacct") || !name.equals("transfer")) {
                    continue;
                }
                JsonObject data = act.get("data").getAsJsonObject();
                String symbol =data.get("symbol").getAsString();
                String memo = data.get("memo").getAsString();
                double fee = data.get("amount").getAsDouble();
                if (!symbol.equals("UPX") || !memo.equals("n24")) {
                    continue;
                }
                amount += fee;
            }
            if (lastFee == null) {
                throw new IllegalStateException("Last fee is null.");
            }

            return new BlockchainVisitFee((long) Math.floor(amount), lastFee);
        }catch (Exception e) {
            logger.catching(e);
        }
        return new BlockchainVisitFee(0, null);
    }

    @Override
    public String getLink() {
        String link = after.toString();
        String beforeLink = before.toString();
        return "https://eos.hyperion.eosrio.io/v2/history/get_actions?after=" + link.substring(0, link.length() - 1) + "&before=" + beforeLink.substring(0, beforeLink.length() - 1) + "&account=" + blockchainId + "&limit=50&sort=asc&act.name=transfer&act.account=upxtokenacct&act.authorization.actor=playuplandme";
    }
}
