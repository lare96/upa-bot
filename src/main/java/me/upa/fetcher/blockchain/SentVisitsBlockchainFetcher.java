package me.upa.fetcher.blockchain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.fetcher.BlockchainDataFetcher;
import me.upa.fetcher.blockchain.SentVisitsBlockchainFetcher.SentVisitsBlockchainData;

public final class SentVisitsBlockchainFetcher extends BlockchainDataFetcher<SentVisitsBlockchainData> {

    public static final class SentVisitsBlockchainData {
        private final String userBlockchainId;
        private final long propertyId;
        private final int feePaid;

        public SentVisitsBlockchainData(String userBlockchainId, long propertyId, int feePaid) {
            this.userBlockchainId = userBlockchainId;
            this.propertyId = propertyId;
            this.feePaid = feePaid;
        }

        public String getUserBlockchainId() {
            return userBlockchainId;
        }

        public long getPropertyId() {
            return propertyId;
        }

        public int getFeePaid() {
            return feePaid;
        }
    }

    private final String transactionId;

    public SentVisitsBlockchainFetcher(String transactionId) {
        this.transactionId = transactionId;
    }

    @Override
    public SentVisitsBlockchainData handleResponse(String link, JsonElement response) {
        String userBlockchainId = null;
        long propertyId = -1;
        int feePaid = -1;

        JsonObject object = response.getAsJsonObject();
        if (object.has("actions")) {
            JsonArray actions = object.get("actions").getAsJsonArray();
            for (JsonElement element : actions) {
                if (!element.isJsonObject() || !element.getAsJsonObject().has("action_ordinal")) {
                    continue;
                }
                int actionOrdinal = element.getAsJsonObject().get("action_ordinal").getAsInt();
                JsonObject act = element.getAsJsonObject().get("act").getAsJsonObject();
                if (actionOrdinal == 3) {
                    String name = act.get("name").getAsString();
                    if (!name.equals("n41")) {
                        return null;
                    }
                }
                JsonObject data = element.getAsJsonObject().get("act").getAsJsonObject().get("data").getAsJsonObject();
                switch (actionOrdinal) {
                    case 2:
                        userBlockchainId = data.get("from").getAsString();
                        feePaid = data.get("amount").getAsInt();
                        break;
                    case 3:
                        propertyId = data.get("a45").getAsLong();
                        break;
                }
            }
        }
        if (userBlockchainId == null || propertyId == -1 || feePaid == -1) {
            return null;
        }
        return new SentVisitsBlockchainData(userBlockchainId, propertyId, feePaid);
    }

    @Override
    public String getLink() {
        return "https://eos.hyperion.eosrio.io/v2/history/get_transaction?id=" + transactionId;
    }
}
