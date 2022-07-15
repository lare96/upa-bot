package me.upa.fetcher;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.game.PropertyVisitor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class VisitorsDataFetcher extends ApiDataFetcher<List<PropertyVisitor>> {

    private static final Logger logger = LogManager.getLogger();


    private final ImmutableList<Long> propertyIdList;
    private final List<PropertyVisitor> visitors = new ArrayList<>();

    public VisitorsDataFetcher(ImmutableList<Long> propertyIdList) {
        this.propertyIdList = propertyIdList;
    }

    public VisitorsDataFetcher(long propertyId) {
        this(ImmutableList.of(propertyId));
    }

    public VisitorsDataFetcher() {
        this(ImmutableList.of());
    }

    @Override
    protected void handleResponse(String link, String response) {
        try {
            JsonArray arrayData = ApiDataFetcher.GSON.fromJson(response, JsonElement.class).getAsJsonArray();
            for (JsonElement data : arrayData) {
                JsonObject visitorData = data.getAsJsonObject();
                int price = visitorData.get("price").getAsInt();
                Instant createdAt = Instant.parse(visitorData.get("created_at").getAsString());
                boolean pending = visitorData.get("pending").getAsBoolean();
                String username = visitorData.get("username").getAsString();
                visitors.add(new PropertyVisitor(price, createdAt, username, pending));
            }
        } catch (Exception e) {
           logger.catching(e);
        }
    }

    @Override
    protected List<String> computeFetchLinks() {
        String baseLink = "http://api.upland.me/teleports/visitors/";
        StringBuilder linkBuilder = new StringBuilder(baseLink);
        return propertyIdList.stream().map(id -> {
            String fullLink = linkBuilder.append(id).toString();
            linkBuilder.setLength(0);
            linkBuilder.append(baseLink);
            return fullLink;
        }).collect(Collectors.toList());
    }

    @Override
    public List<PropertyVisitor> getResult() {
        return visitors;
    }

    @Override
    public List<PropertyVisitor> getDefaultResult() {
        return visitors;
    }
}
