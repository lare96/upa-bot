package me.upa.game;

import com.fasterxml.jackson.core.json.UTF8DataInputJsonParser;

public final class Structure {

    public static final int MIN_UP2 = 17;
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
            case "Town House":
                this.minUp2 = 30;
                this.maxUp2 = 70;
                break;
            case "Ranch House":
                this.minUp2 = 60;
                this.maxUp2 = 70;
                break;
            case "Small Town House":
                this.minUp2 = MIN_UP2;
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
            default:
                throw new IllegalStateException();

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

    public int getSshRequired() {
        return (int) (sparkPrice * 0.25);
    }

    public int getMinUp2() {
        return minUp2;
    }

    public int getMaxUp2() {
return maxUp2;
    }
}
