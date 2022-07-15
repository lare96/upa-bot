package me.upa.game;

import java.io.Serializable;
import java.util.Set;

public final class Profile implements Serializable {


    private static final long serialVersionUID = 6138346668791302717L;
    private String ownerUsername;
    private final int netWorth;
    private final Set<Long> properties;
    private final boolean jailed;

    public Profile(String ownerUsername, int netWorth, Set<Long> properties, boolean jailed) {
        this.ownerUsername = ownerUsername;
        this.netWorth = netWorth;
        this.properties = properties;
        this.jailed = jailed;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public int getNetWorth() {
        return netWorth;
    }

    public Set<Long> getProperties() {
        return properties;
    }

    public boolean isJailed() {
        return jailed;
    }
}
