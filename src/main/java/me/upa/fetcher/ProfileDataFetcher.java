package me.upa.fetcher;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.UpaBotContext;
import me.upa.game.CachedProperty;
import me.upa.game.Profile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ProfileDataFetcher extends ApiDataFetcher<Profile> {
    private static final Logger logger = LogManager.getLogger();
    private static final Cache<String, Profile> profiles = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();
    public static volatile UpaBotContext ctx;

    public static Profile fetchProfileSynchronous(String username, boolean forced) {
        try {
            if (forced) {
                profiles.invalidate(username);
            }
            return profiles.get(username, () -> {
                Profile profile = new ProfileDataFetcher(username).waitUntilDone();
                if(profile == null) {
                    return DUMMY;
                }
                return profile;
            });
        } catch (Exception e) {
            logger.catching(e);
        }
        return DUMMY;
    }

    public static Profile fetchProfileSynchronous(String username) {
        return fetchProfileSynchronous(username, false);
    }

    public static CompletableFuture<Profile> fetchProfile(String username) {
        try {
            ProfileDataFetcher profileDataFetcher = new ProfileDataFetcher(username);
            profileDataFetcher.fetch();
            return profileDataFetcher.getTask().thenApply(prof -> {
                profiles.put(username, prof);
                return prof;
            });
        } catch (Exception e) {
            logger.catching(e);
        }
        return CompletableFuture.completedFuture(DUMMY);
    }

    public static final Profile DUMMY = new Profile("", -1, Set.of(), true);

    private final String ownerUsername;
    private volatile Profile profile;

    private ProfileDataFetcher(String ownerUsername) {
        super(true);
        this.ownerUsername = ownerUsername;
    }

    @Override
    protected void handleResponse(String link, String response) {
        JsonObject data = GSON.fromJson(response, JsonObject.class);
        if(data == null) {
            logger.error("Response for profile [{}] was null.", ownerUsername);
            return;
        }
        if (!data.has("networth")) {
            logger.error("Field 'networth' for [{}] was null.", ownerUsername);
            return;
        }
        int netWorth = (int) Math.floor(data.get("networth").getAsDouble());
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
        return List.of("https://api.prod.upland.me/api/profile/" + ownerUsername);
    }
}
