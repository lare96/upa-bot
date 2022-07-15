package me.upa.fetcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.game.Structure;

import java.util.ArrayList;
import java.util.List;

public final class StructureDataFetcher extends ApiDataFetcher<List<Structure>> {

    private final List<Structure> structures = new ArrayList<>();

    @Override
    public List<Structure> getResult() {
        return structures;
    }

    @Override
    public List<Structure> getDefaultResult() {
        return List.of();
    }

    @Override
    protected void handleResponse(String link, String response) {
        JsonArray data = GSON.fromJson(response, JsonArray.class);
        for (JsonElement next : data.getAsJsonArray()) {
            JsonObject object = next.getAsJsonObject();
            int id = object.get("id").getAsInt();
            String name = object.get("name").getAsString();
            int sparkPrice = object.get("constructionPrice").getAsInt();
            int maxStackedSparks = object.get("maxStackedSparks").getAsInt();
            structures.add(new Structure(id, name, sparkPrice, maxStackedSparks));
        }
    }

    @Override
    protected List<String> computeFetchLinks() {
        return List.of("https://business.upland.me/models/available-for-building/81372285607172");
    }
}