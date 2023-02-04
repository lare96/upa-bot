package me.upa.fetcher;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static me.upa.fetcher.ApiDataFetcher.GSON;

public final class TriviaDataFetcher {

    private static final Path TRIVIA_PATH = Paths.get("data", "trivia_temp");
    private static final Logger logger = LogManager.getLogger();
    private static final HttpClient client = HttpClient.newBuilder().executor(Executors.newCachedThreadPool()).build();
int triviaCount = 1;
    public void fetchAndStore() {
        String link ="https://opentdb.com/api.php?amount=50&type=multiple&encode=url3986";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(link))
                .headers("authority", "opentdb.com",
                        "accept", "application/json")
                .build();
      client.sendAsync(request, BodyHandlers.ofString()).
                handleAsync((response, throwable) -> {
                    if (throwable != null) {
                        logger.catching(throwable);
                        return null;
                    }
                    if (response == null) {
                        return null;
                    }
                    try {
                        Path writePath = TRIVIA_PATH.resolve("trivia_fetch_"+triviaCount+++".json");
                        Files.createFile(writePath);
                        Files.writeString(writePath, response.body());
                    } catch (IOException e) {
                        logger.catching(e);
                    }
                    return null;
                });
    }
}
