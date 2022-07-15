package me.upa.fetcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.game.Property;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class PropertyDataFetcher extends ApiDataFetcher<Property> {
    private static final Logger logger = LogManager.getLogger();
    public static Property fetchPropertySynchronous(long propertyId) throws Exception {
        PropertyDataFetcher propertyFetcher = new PropertyDataFetcher();
        propertyFetcher.fetch("https://api.upland.me/properties/" + propertyId);
        return propertyFetcher.waitUntilDone();
    }

    public static Property fetchPropertySynchronous(String propertyId) throws Exception {
        PropertyDataFetcher propertyFetcher = new PropertyDataFetcher();
        propertyFetcher.fetch("https://api.upland.me/properties/" + propertyId);
        return propertyFetcher.waitUntilDone();
    }

    public static void fetchProperty(long propertyId, Consumer<Property> success) {
        fetchProperty(propertyId, success, t ->
             logger.warn("Error fetching property data.", t));
    }

    public static void fetchProperty(long propertyId, Consumer<Property> success, Consumer<Throwable> failure) {
        fetchProperty(propertyId).whenComplete((property, throwable) -> {
            if (throwable != null) {
                failure.accept(throwable);
            } else {
                success.accept(property);
            }
        });
    }

    public static CompletableFuture<Property> fetchProperty(long propertyId) {
        PropertyDataFetcher propertyFetcher = new PropertyDataFetcher();
        propertyFetcher.fetch("https://api.upland.me/properties/" + propertyId);
        return propertyFetcher.getTask();
    }

    private PropertyDataFetcher() {
    }

    private volatile Property property;

    @Override
    protected void handleResponse(String link, String response) {
        if (response.length() < 50) {
            return;
        }
        try {
            JsonObject data = ApiDataFetcher.GSON.fromJson(response, JsonObject.class);
            long propId = data.get("prop_id").getAsLong();
            int cityId = data.get("city").getAsJsonObject().get("id").getAsInt();
            String fullAddress = data.get("full_address").getAsString();
            int area = data.get("area").getAsInt();
            String status = data.get("status").getAsString();
            JsonElement buildingData = data.get("building");
            int price = data.get("price").getAsInt();
            double yieldPerHour = data.get("yield_per_hour").getAsDouble();
            int mintPrice = (int) Math.floor((yieldPerHour * 24 * 30) * 82.671);
            List<double[]> coordinates = new ArrayList<>();
            String unformatted = data.get("boundaries").getAsString();
            String formatted = unformatted.replace("//", "").substring(0, unformatted.length());
            JsonObject boundaries = ApiDataFetcher.GSON.fromJson(formatted, JsonObject.class);
            String polygonType = boundaries.get("type").getAsString();
            JsonArray coordinatesArray = boundaries.get("coordinates").getAsJsonArray();
            if (polygonType.equals("MultiPolygon")) {
                while (coordinatesArray.isJsonArray()) {
                    if (coordinatesArray.get(0).isJsonArray()) {
                        if (coordinatesArray.get(0).getAsJsonArray().get(0).isJsonArray()) {
                            if (coordinatesArray.get(0).getAsJsonArray().get(0).getAsJsonArray().get(0).isJsonPrimitive()) {
                                JsonArray loopArray = coordinatesArray.get(0).getAsJsonArray();
                                for (JsonElement next : loopArray) {
                                    JsonArray jsonPos = next.getAsJsonArray();
                                    double[] pos = new double[]{jsonPos.get(0).getAsDouble(), jsonPos.get(1).getAsDouble()};
                                    coordinates.add(pos);
                                }
                                break;
                            }
                        }
                    }
                    coordinatesArray = coordinatesArray.getAsJsonArray();
                }
            } else {
                for (JsonElement nextCoordinate : coordinatesArray) {
                    for (JsonElement next : nextCoordinate.getAsJsonArray()) {
                        JsonArray jsonPos = next.getAsJsonArray();
                        double[] pos = new double[]{jsonPos.get(0).getAsDouble(), jsonPos.get(1).getAsDouble()};
                        coordinates.add(pos);
                    }
                }
            }

            String owner = data.get("owner").getAsString();
            String ownerUsername = data.get("owner_username").getAsString();
            String buildStatus;
            double buildPercentage;
            double stakedSpark = 0.0;
            int totalSparksRequired = 0;
            double progressInSpark = 0.0;
            int maxStakedSpark = 0;
            Instant finishedAt = null;
            if (buildingData.isJsonNull()) {
                buildPercentage = 0.0;
                buildStatus = "Not started";
            } else {
                JsonObject buildDataObject = buildingData.getAsJsonObject();
                JsonElement constructionData = buildDataObject.get("construction");
                if (!constructionData.isJsonNull()) {
                    JsonObject constructionDataObject = constructionData.getAsJsonObject();
                    stakedSpark = constructionDataObject.get("stackedSparks").getAsDouble();
                    totalSparksRequired = constructionDataObject.get("totalSparksRequired").getAsInt();
                    progressInSpark = constructionDataObject.get("progressInSparks").getAsDouble();
                    finishedAt = Instant.parse(constructionDataObject.get("finishedAt").getAsString());
                }
                JsonElement detailsData = buildDataObject.get("details");
                if (!detailsData.isJsonNull()) {
                    maxStakedSpark = detailsData.getAsJsonObject().get("maxStackedSparks").getAsInt();
                }

                String fetchedBuildStatus = buildDataObject.get("constructionStatus").getAsString();
                if (fetchedBuildStatus.equals("processing")) {
                    JsonObject construction = buildDataObject.getAsJsonObject("construction");
                    double shNeeded = construction.get("totalSparksRequired").getAsDouble();
                    double shProgress = construction.get("progressInSparks").getAsDouble();
                    buildPercentage = (shProgress / shNeeded) * 100.0;
                    buildStatus = "In progress";
                } else if (fetchedBuildStatus.equals("completed") || fetchedBuildStatus.equals("can-watch-ceremony")) {
                    buildPercentage = 100.0;
                    buildStatus = "Completed";
                } else {
                    throw new IllegalStateException("Invalid build status.");
                }
            }
            property = new Property(propId, cityId, fullAddress, area, status, buildStatus, buildPercentage, price, mintPrice, coordinates.toArray(new double[coordinates.size()][2]), ownerUsername, owner, stakedSpark, totalSparksRequired, progressInSpark, maxStakedSpark, finishedAt);
        } catch (Exception e) {
            property = null;
           logger.error(new ParameterizedMessage("Error reading property data for [{}]", link), e);
        }

    }

    @Override
    public Property getResult() {
        return property;
    }

    @Override
    public Property getDefaultResult() {
        return null;
    }
}
