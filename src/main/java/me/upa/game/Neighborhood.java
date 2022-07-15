package me.upa.game;

/**
 * Represents a neighborhood in Upland.
 */
public final class Neighborhood {

    /**
     * The neighborhood identifier.
     */
    private final int id;

    /**
     * The city identifier that this neighborhood is in.
     */
    private final int cityId;

    /**
     * The neighborhood's name.
     */
    private final String name;

    /**
     * The polygon representing this neighborhood.
     */
    private final double[][] neighborhoodArea;// TODO: point, polygon, multipolygon

    /**
     * Creates a new {@link Neighborhood}.
     * @param id The neighborhood identifier.
     * @param cityId The city identifier that this neighborhood is in.
     * @param name The neighborhood's name.
     * @param neighborhoodArea
     */
    public Neighborhood(int id, int cityId, String name, double[][] neighborhoodArea) {
        this.id = id;
        this.cityId = cityId;
        this.name = name;
        this.neighborhoodArea = neighborhoodArea;
    }

    public int getId() {
        return id;
    }

    public int getCityId() {
        return cityId;
    }

    public String getName() {
        return name;
    }

    public double[][] getNeighborhoodArea() {
        return neighborhoodArea;
    }
}
