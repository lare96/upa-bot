package me.upa.fetcher;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static me.upa.fetcher.ApiDataFetcher.GSON;

public abstract class BlockchainDataFetcher<T> {

    private static final Logger logger = LogManager.getLogger();

    private static final HttpClient client = HttpClient.newBuilder().executor(Executors.newCachedThreadPool()).build();

    public CompletableFuture<T> fetch() {
        String link = getLink();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(link))
                .headers("authority", "eos.hyperion.eosrio.io",
                        "accept", "application/json")
                .build();
        logger.info("Blockchain API request {}", link);
        CompletableFuture<T> task = client.sendAsync(request, BodyHandlers.ofString()).
                handleAsync((response, throwable) -> {
                    if (throwable != null) {
                        logger.catching(throwable);
                        return null;
                    }
                    if (response == null) {
                        return null;
                    }
                    String body = response.body();
                    try {
                        JsonElement element = GSON.fromJson(body, JsonElement.class);
                        return handleResponse(link, element);
                    } catch (Exception e) {
                        logger.error(new ParameterizedMessage("Response was [null] when processing JSON output\n\n {}", body), e);
                    }
                    return null;
                });
        return task;
    }

    public abstract T handleResponse(String link, JsonElement response);

    public abstract String getLink();
}
