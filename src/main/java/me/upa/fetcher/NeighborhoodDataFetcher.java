package me.upa.fetcher;

import com.google.common.collect.ArrayListMultimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.game.Neighborhood;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NeighborhoodDataFetcher extends ApiDataFetcher<Void> {
    @Override
    public Void getResult() {
        return null;
    }

    @Override
    public Void getDefaultResult() {
        return null;
    }

    @Override
    protected void handleResponse(String link, String response) {
        JsonElement data = ApiDataFetcher.GSON.fromJson(response, JsonElement.class);
        Map<Integer, Neighborhood> neighborhoods = new HashMap<>();
        ArrayListMultimap<Integer, Neighborhood> cityNeighborhoods = ArrayListMultimap.create();
        for (JsonElement element : data.getAsJsonArray()) {
            JsonObject object = element.getAsJsonObject();
            int id = object.get("id").getAsInt();
            String name = object.get("name").getAsString();
            int cityId = object.get("city_id").getAsInt();
            double[][] polygon = PropertyDataFetcher.getPolygon(object, false);
            if(polygon == null) {
                continue;
            }
            var n = new Neighborhood(id, cityId, name, polygon);
            neighborhoods.put(id, n);
            cityNeighborhoods.put(cityId, n);
        }
        DataFetcherManager.setNeighborhoodMap(neighborhoods);
        DataFetcherManager.setCityNeighborhoodMap(cityNeighborhoods);
    }

    @Override
    protected List<String> computeFetchLinks() {
        return List.of("https://api.upland.me/neighborhood");
    }
}
