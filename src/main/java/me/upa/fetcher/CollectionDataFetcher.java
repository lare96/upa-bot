package me.upa.fetcher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.game.CityCollection;
import org.checkerframework.framework.qual.LiteralKind;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CollectionDataFetcher extends ApiDataFetcher<Void>{
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
        Map<String, CityCollection> collections = new HashMap<>();
        for (JsonElement element : data.getAsJsonArray()) {
            JsonObject object = element.getAsJsonObject();
            int id = object.get("id").getAsInt();
            String name = object.get("name").getAsString();
            int category = object.get("category").getAsInt();
            String requirements = object.get("name").getAsString();
            collections.put(name, new CityCollection(id, name, requirements, category));
        }
        DataFetcherManager.setCollectionMap(collections);
    }

    @Override
    protected List<String> computeFetchLinks() {
        return List.of("https://api.upland.me/collections");
    }
}
