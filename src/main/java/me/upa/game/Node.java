package me.upa.game;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import me.upa.UpaBotConstants;

import java.util.Arrays;

public enum Node {
    HOLLIS(4, 1745, 4383, "Hollis, Queens"),
    SUNRISE(35, 1838, 3912, "Sunrise, Las Vegas"),

    CIDADE_DE_DEUS(36, 1865, 4328, "Cidade de Deus, Rio de Janeiro"),

    GREENFIELD(33, 1586, 3353, "Greenfield, Detroit"),

    MANCHESTER_SQUARE(32, 1443, 3207, "Manchester Square, Los Angeles");

    public static final ImmutableSet<Node> ALL = Arrays.stream(values()).
            collect(ImmutableSet.toImmutableSet());
    public static final ImmutableMap<Integer, Node> NEIGHBORHOOD_IDS;

    static {
        NEIGHBORHOOD_IDS = Arrays.stream(values()).
                collect(ImmutableMap.<Node, Integer, Node>toImmutableMap(t -> t.neighborhoodId, t -> t));
    }

    private final int cityId;
    private final int neighborhoodId;
    private final int totalProperties;

    private final String displayName;

    Node(int cityId, int neighborhoodId, int totalProperties, String displayName) {
        this.cityId = cityId;
        this.neighborhoodId = neighborhoodId;
        this.totalProperties = totalProperties;
        this.displayName = displayName;
    }

    public static Node getNodeForChannel(long channelId) {
        if (channelId == UpaBotConstants.HOLLIS_TRAIN_CHANNEL) {
            return Node.HOLLIS;
        }
        return null;
    }

    public static boolean isValidNeighborhood(Neighborhood neighborhood) {
        if (neighborhood == null) {
            return false;
        }
        return NEIGHBORHOOD_IDS.containsKey(neighborhood.getId());
    }

    public static Node getNode(Neighborhood neighborhood) {
        if (neighborhood == null) {
            return null;
        }
        return NEIGHBORHOOD_IDS.get(neighborhood.getId());
    }

    public int getCityId() {
        return cityId;
    }

    public int getNeighborhoodId() {
        return neighborhoodId;
    }

    public int getTotalProperties() {
        return totalProperties;
    }

    public String getDisplayName() {
        return displayName;
    }
}
