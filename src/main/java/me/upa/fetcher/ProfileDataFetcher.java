package me.upa.fetcher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.game.Profile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProfileDataFetcher extends ApiDataFetcher<Profile> {
    public static final Profile DUMMY = new Profile("", -1, Set.of(), true);

    private final String ownerUsername;
    private volatile Profile profile;

    public ProfileDataFetcher(String ownerUsername) {
        super(true);
        this.ownerUsername = ownerUsername;
    }

    @Override
    protected void handleResponse(String link, String response) {
        JsonObject data = GSON.fromJson(response, JsonObject.class);
        if (!data.has("networth")) {
            return;
        }
        int netWorth = data.get("networth").getAsInt();
        List<Long> properties = new ArrayList<>();
        for (JsonElement next : data.get("properties").getAsJsonArray()) {
            properties.add(next.getAsJsonObject().get("property_id").getAsLong());
        }
        boolean isInJail = data.get("is_in_jail").getAsBoolean();
        profile = new Profile(ownerUsername, netWorth, new HashSet<>(properties), isInJail);
    }

    @Override
    public Profile getResult() {
        return profile;
    }

    @Override
    public Profile getDefaultResult() {
        return DUMMY;
    }

    @Override
    protected List<String> computeFetchLinks() {
        return List.of("http://api.upland.me/profile/" + ownerUsername);
    }
}
