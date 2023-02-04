package me.upa.game;

import com.fasterxml.jackson.core.json.UTF8DataInputJsonParser;

public final class Structure {

    public static final int MIN_UP2 = 10;
    private final int id;
    private final String name;
    private final int sparkPrice;

    private final int maxStackedSparks;
    private final int minUp2;
    private final int maxUp2;

    public Structure(int id, String name, int sparkPrice, int maxStackedSparks) {
        this.id = id;
        this.name = name;
        this.sparkPrice = sparkPrice;
        this.maxStackedSparks = maxStackedSparks;
        switch (name) {
            case "Micro House":
                this.minUp2 = MIN_UP2;
                this.maxUp2 = 40;
                break;
            case "Town House":
                this.minUp2 = 30;
                this.maxUp2 = 70;
                break;
            case "Ranch House":
                this.minUp2 = 60;
                this.maxUp2 = 70;
                break;
            case "Small Town House":
                this.minUp2 = 17;
                this.maxUp2 = 70;
                break;
            case "Apartment Building":
                this.minUp2 = 60;
                this.maxUp2 = Integer.MAX_VALUE;
                break;
            case "Luxury Ranch House":
            case "Luxury Modern House":
                this.minUp2 = 80;
                this.maxUp2 = Integer.MAX_VALUE;
                break;
            case "Small Factory I":
            case "Small Factory II":
            case "Small Showroom I":
            case "Small Showroom II":
                this.minUp2 = 100;
                this.maxUp2 = Integer.MAX_VALUE;
                break;
            case "Medium Showroom I":
            case "Medium Showroom II":
            case "Medium Factory I":
            case "Medium Factory II":
                this.minUp2 = 200;
                this.maxUp2 = Integer.MAX_VALUE;
                break;
            case "Large Showroom I":
            case "Large Showroom II":
            case "Large Factory I":
            case "Large Factory II":
                this.minUp2 = 300;
                this.maxUp2 = Integer.MAX_VALUE;
                break;
            default:
                throw new IllegalStateException(name);

        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getSparkPrice() {
        return sparkPrice;
    }

    public int getMaxStackedSparks() {
        return maxStackedSparks;
    }

    public int getSshRequired(boolean firstBuild, boolean global, boolean nodeProperty) {
        double threshold = global && !nodeProperty ? 0.5 : 0.25;
        double req = sparkPrice * threshold;
        if (!global && firstBuild && (name.equals("Small Town House") || name.equals("Micro House"))) {
            req /= 2;
        }
        return (int) Math.floor(req);
    }

    public int getMinUp2() {
        return minUp2;
    }

    public int getMaxUp2() {
        return maxUp2;
    }
}
