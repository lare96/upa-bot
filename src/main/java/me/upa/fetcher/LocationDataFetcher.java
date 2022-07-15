package me.upa.fetcher;

import com.google.common.collect.ArrayListMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.game.City;
import me.upa.game.CityCollection;
import me.upa.game.Neighborhood;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A {@link ApiDataFetcher} implementation that fetches city and neighborhood data.
 *
 * @author lare96
 */
public final class LocationDataFetcher extends ApiDataFetcher<Void> {

    /**
     * UPXLand API link for city data.
     */
    private static final String CITIES_API_LINK = "https://api.upland.me/feature/city";

    /**
     * UPXLand API link for neighborhood data.
     */
    private static final String NEIGHBORHOODS_API_LINK = "https://api.upland.me/neighborhood";
    private static final String COLLECTIONS_API_LINK = "https://api.upland.me/collections";

    /**
     * The logger.
     */
    private static final Logger logger = Logger.getGlobal();

    public LocationDataFetcher() {
        super(true);
    }

    @Override
    protected void handleResponse(String link, String response)  {
        JsonElement data = ApiDataFetcher.GSON.fromJson(response, JsonElement.class);
        switch (link) {
            case CITIES_API_LINK:
                loadCities(data.getAsJsonObject().get("cities").getAsJsonArray());
                break;
            case NEIGHBORHOODS_API_LINK:
                loadNeighborhoods(data.getAsJsonArray());
                break;
            case COLLECTIONS_API_LINK:
                loadCollections(data.getAsJsonArray());
                break;
        }
    }

    @Override
    void handleCompletion()  {
        logger.info("Cities, neighbourhoods, and collections have been updated.");
    }

    @Override
    protected List<String> computeFetchLinks()  {
        return List.of(CITIES_API_LINK, NEIGHBORHOODS_API_LINK, COLLECTIONS_API_LINK);
    }

    /**
     * Loads JSON city data into local memory.
     */
    private void loadCities(JsonArray data) {
        Map<Integer, City> cities = new HashMap<>();
        for (JsonElement element : data) {
            JsonObject object = element.getAsJsonObject();
            int id = object.get("city_id").getAsInt();
            String name = object.get("city_name").getAsString();
            cities.put(id, new City(id, name));
        }
        DataFetcherManager.setCityMap(cities);
    }

    /**
     * Loads JSON neighborhoods into local memory.
     */
    private void loadNeighborhoods(JsonArray data) {
        Map<Integer, Neighborhood> neighborhoods = new HashMap<>();
        ArrayListMultimap<Integer, Neighborhood> cityNeighborhoods = ArrayListMultimap.create();
        for (JsonElement element : data) {
            JsonObject object = element.getAsJsonObject();
            int id = object.get("id").getAsInt();
            String name = object.get("name").getAsString();
            int cityId = object.get("city_id").getAsInt();
            List<double[]> coordinates = new ArrayList<>();
            if (id == 1745) {
                for (JsonElement coordinatesElement : object.get("boundaries").getAsJsonObject().get("coordinates").getAsJsonArray()) {
                    for (JsonElement next : coordinatesElement.getAsJsonArray()) {
                        JsonArray jsonPos = next.getAsJsonArray();
                        double[] pos = {jsonPos.get(0).getAsDouble(), jsonPos.get(1).getAsDouble()};
                        coordinates.add(pos);
                    }
                }
            }
            var n = new Neighborhood(id, cityId, name, coordinates.toArray(new double[coordinates.size()][2]));
            neighborhoods.put(id, n);
            cityNeighborhoods.put(cityId, n);
        }
        DataFetcherManager.setNeighborhoodMap(neighborhoods);
        DataFetcherManager.setCityNeighborhoodMap(cityNeighborhoods);
    }

    /**
     * Loads JSON neighborhoods into local memory.
     */
    private void loadCollections(JsonArray data) {
        Map<String, CityCollection> collections = new HashMap<>();
        for (JsonElement element : data) {
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
    public Void getResult() {
        return null;
    }

    @Override
    public Void getDefaultResult() {
        return null;
    }
}
