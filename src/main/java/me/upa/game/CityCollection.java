package me.upa.game;

public final class CityCollection {

    private final int id;
    private final String name;
    private final String requirements;
    private final int category;

    public CityCollection(int id, String name, String requirements, int category) {
        this.id = id;
        this.name = name;
        this.requirements = requirements;
        this.category = category;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRequirements() {
        return requirements;
    }

    public int getCategory() {
        return category;
    }
}
