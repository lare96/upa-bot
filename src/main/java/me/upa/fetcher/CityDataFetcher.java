package me.upa.fetcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.game.City;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CityDataFetcher extends ApiDataFetcher<Void> {


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
        Map<Integer, City> cities = new HashMap<>();
        for (JsonElement element : data.getAsJsonObject().get("cities").getAsJsonArray()) {
            JsonObject object = element.getAsJsonObject();
            int id = object.get("city_id").getAsInt();
            String name = object.get("city_name").getAsString();
            cities.put(id, new City(id, name));
        }
        DataFetcherManager.setCityMap(cities);
    }

    @Override
    protected List<String> computeFetchLinks() {
        return List.of("https://api.upland.me/feature/city");
    }
}
