package me.upa.game;

import com.google.common.base.Objects;

/**
 * Represents a city in Upland.
 *
 * @author lare96
 */
public final class City {

    /**
     * The city identifier.
     */
    private final int id;

    /**
     * The city name.
     */
    private final String name;

    /**
     * Creates a new {@link City}.
     *
     * @param id The city identifier.
     * @param name The city name.
     */
    public City(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        City city = (City) o;
        return id == city.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
