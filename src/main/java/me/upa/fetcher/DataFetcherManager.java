package me.upa.fetcher;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import me.upa.game.City;
import me.upa.game.CityCollection;
import me.upa.game.Neighborhood;
import me.upa.game.Sale;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Stores all fetched data from UPXLand.
 *
 * @author lare96
 */
public final class DataFetcherManager {

    private static final ListeningExecutorService fetchPool = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));



    /**
     * The sales data fetcher.
     */
    private static final DataFetcher SALES_FETCHER = new CsvSalesDataFetcher();

    /**
     * The mutable multimap of sales.
     */
    private static final ListMultimap<Integer, Sale> mutableSalesMap =
            Multimaps.synchronizedListMultimap(ArrayListMultimap.create());

    private static final Map<Integer, List<Sale>> salesMap = new ConcurrentHashMap<>();

    /**
     * A map of city identifiers to their respective cities.
     */
    private static volatile ImmutableMap<Integer, City> cityMap = ImmutableMap.of();

    /**
     * A map of neighborhood identifiers to their respective neighborhoods.
     */
    private static volatile ImmutableMap<Integer, Neighborhood> neighborhoodMap = ImmutableMap.of();

    /**
     * A map of city identifiers to their respective neighborhoods.
     */
    private static volatile ImmutableListMultimap<Integer, Neighborhood> cityNeighborhoodMap = ImmutableListMultimap.of();


    private static volatile ImmutableMap<String, CityCollection> collectionMap = ImmutableMap.of();

    /**
     * Refreshes the immutable copy of the sales map. All data passed to {@link #addSale(int, Sale)} is erased.
     */
    public static void refreshSales(int cityId) {
        salesMap.put(cityId, mutableSalesMap.removeAll(cityId));
    }
    public static void refreshAllSales() {
        Iterator<Entry<Integer, Collection<Sale>>> it = mutableSalesMap.asMap().entrySet().iterator();
        while (it.hasNext()) {
            var next = it.next();
            it.remove();
            salesMap.put(next.getKey(), List.copyOf(next.getValue()));
        }
    }
    /**
     * Stores a sale in temporary map to later be added to the sales map with
     */
    public static void addSale(int cityId, Sale sale) {
        mutableSalesMap.put(cityId, sale);
    }

    public static int getCityId(String name) {
        if (cityMap == null)
            return -1;
        return cityMap.values().stream().filter(next -> next.getName().equalsIgnoreCase(name)).mapToInt(City::getId).findFirst().orElse(-1);
    }

    public static int getNeighborhoodId(String name) {
        if (neighborhoodMap == null)
            return -1;
        return neighborhoodMap.values().stream().filter(next -> next.getName().equalsIgnoreCase(name)).mapToInt(Neighborhood::getId).findFirst().orElse(-1);
    }
    public static List<Neighborhood> getNeighborhoods(int cityId) {
        if (neighborhoodMap == null)
            return List.of();
        return neighborhoodMap.values().stream().filter(next -> next.getCityId() == cityId).collect(Collectors.toList());
    }

    // Getters and setters.
    static void setCityMap(Map<Integer, City> newMap) {
        cityMap = ImmutableMap.copyOf(newMap);
    }

    static void setNeighborhoodMap(Map<Integer, Neighborhood> newMap) {
        neighborhoodMap = ImmutableMap.copyOf(newMap);
    }

    static void setCityNeighborhoodMap(ListMultimap<Integer, Neighborhood> newMap) {
        cityNeighborhoodMap = ImmutableListMultimap.copyOf(newMap);
    }

    static void setCollectionMap(Map<String, CityCollection> newMap) {
        collectionMap = ImmutableMap.copyOf(newMap);
    }

    public static DataFetcher getSalesFetcher() {
        return SALES_FETCHER;
    }


    public static ImmutableMap<Integer, City> getCityMap() {
        return cityMap;
    }

    public static ImmutableMap<Integer, Neighborhood> getNeighborhoodMap() {
        return neighborhoodMap;
    }

    public static ImmutableMap<String, CityCollection> getCollectionMap() {
        return collectionMap;
    }

    public static ImmutableListMultimap<Integer, Neighborhood> getCityNeighborhoodMap() {
        return cityNeighborhoodMap;
    }

    public static Map<Integer, List<Sale>> getSalesMap() {
        return salesMap;
    }

    public static ListeningExecutorService getFetchPool() {
        return fetchPool;
    }
}
