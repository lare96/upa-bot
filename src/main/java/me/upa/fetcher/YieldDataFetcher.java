package me.upa.fetcher;

import com.google.common.primitives.Doubles;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.game.PropertyYield;
import me.upa.game.PropertyYieldVisitor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public final class YieldDataFetcher extends ApiDataFetcher<List<PropertyYield>> {
    private static final Logger logger = LogManager.getLogger();

    private final List<PropertyYield> yields = new ArrayList<>();

    @Override
    public List<PropertyYield> getResult() {
        return yields;
    }

    @Override
    public List<PropertyYield> getDefaultResult() {
        return yields;
    }

    @Override
    protected void handleResponse(String link, String response) {
        try {
            JsonArray object = ApiDataFetcher.GSON.fromJson(response, JsonElement.class).getAsJsonArray();
            for (JsonElement propertyElement : object) {
                JsonObject propertyObject = propertyElement.getAsJsonObject();
                Instant lastYieldTime = Instant.parse(propertyObject.get("last_yield_time").getAsString());
                long propertyId = propertyObject.get("prop_id").getAsLong();
                List<PropertyYieldVisitor> visitors = new ArrayList<>();
                for (JsonElement data : propertyObject.getAsJsonArray("teleport_fees")) {
                    JsonObject visitorData = data.getAsJsonObject();
                    Double fee = Doubles.tryParse(visitorData.get("fee").getAsString().replace(" UPX", ""));
                    if (fee == null) {
                        continue;
                    }
                    String eosId = visitorData.get("visitor_eos_id").getAsString();
                    visitors.add(new PropertyYieldVisitor(fee.intValue(), propertyId, eosId));
                }
                yields.add(new PropertyYield(propertyId, lastYieldTime, visitors));
            }
        } catch (Exception e) {
           logger.catching(e);
        }
    }

    @Override
    protected List<String> computeFetchLinks() {
        return List.of("https://api.upland.me/yield/mine");
    }
}
