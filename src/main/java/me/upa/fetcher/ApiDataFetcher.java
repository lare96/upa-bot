package me.upa.fetcher;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.upa.sql.SqlTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles the fetching of data.
 *
 * @author lare96
 */
public abstract class ApiDataFetcher<T> extends DataFetcher {

    /*
     https://business.upland.me/contributions/1065375
     Shows all spark contributions on a structure, an array of
        {
      "id":"e60931b0-a99b-11ec-8c25-edbfc384f5d2",
      "is_in_jail":false,
      "pending":false,
      "avatar_image":"https://static.upland.me/avatars/visitors/_Modern+Sunglasses+1.svg",
      "avatar_color":"#FFA34D",
      "price":0.03,
      "username":"swear2jah",
      "created_at":"2022-06-30T18:00:46.349Z"
   },
     */

    private static final Logger logger = LogManager.getLogger();

    private static volatile boolean idle;

    public static boolean isIdle() {
        return idle;
    }

    private static final ImmutableList<String> TOKENS = ImmutableList.of(
            //    "eyJhbGciOiJIUzI1NiIsInR5cCI6ImFjY2VzcyJ9.eyJ1c2VySWQiOiJlNjA5MzFiMC1hOTliLTExZWMtOGMyNS1lZGJmYzM4NGY1ZDIiLCJ2YWxpZGF0aW9uVG9rZW4iOiJPMXk3Rkc4bjVETXdsbzJieW5ub1U2a2VjQVpKMFlQUzVpZjJXVldyYUVXIiwiaWF0IjoxNjU0NzE2MDQ0LCJleHAiOjE2ODYyNzM2NDQsImlzcyI6ImZlYXRoZXJzIiwic3ViIjoiZTYwOTMxYjAtYTk5Yi0xMWVjLThjMjUtZWRiZmMzODRmNWQyIiwianRpIjoiMDNiZTkzNmItZmQwNy00NmNlLWFmMmQtY2Y3ZDJiNzZkNGRjIn0.qWxmD5IBmsyFR727jST6yA56FPYsIr0okvfhUcjNaUQ",
            "eyJhbGciOiJIUzI1NiIsInR5cCI6ImFjY2VzcyJ9.eyJ1c2VySWQiOiJiMzlkNmNkMC1hNjNjLTExZWMtYWNlMi02OWIxNjAyMjYyMzUiLCJ2YWxpZGF0aW9uVG9rZW4iOiI3VkszSFlORDRyMGtaSWlvdk9OVnpra0puNzdSZnhFZlRiRGZiTXA4RlJrIiwiaWF0IjoxNjU0MjczMjQyLCJleHAiOjE2ODU4MzA4NDIsImlzcyI6ImZlYXRoZXJzIiwic3ViIjoiYjM5ZDZjZDAtYTYzYy0xMWVjLWFjZTItNjliMTYwMjI2MjM1IiwianRpIjoiYWM3Y2QzMzAtMTliNi00YjVmLWE3NTItOWQ2YTFkMGQ2ODhkIn0.4fZtmzl7dAgcSe5poeVniZ1XQRaEscu8X8lEMBKITjw"
            //  "eyJhbGciOiJIUzI1NiIsInR5cCI6ImFjY2VzcyJ9.eyJ1c2VySWQiOiJkZjlhZjA1MC1hN2MyLTExZWMtYWNlMi02OWIxNjAyMjYyMzUiLCJ2YWxpZGF0aW9uVG9rZW4iOiJaVWtCRUxTRTF6TFp4d21yZlFlcVZZbE1mWWh6c3cwUHZlUW9jZ3hHdFlpIiwiaWF0IjoxNjU1MTc2NjE1LCJleHAiOjE2ODY3MzQyMTUsImlzcyI6ImZlYXRoZXJzIiwic3ViIjoiZGY5YWYwNTAtYTdjMi0xMWVjLWFjZTItNjliMTYwMjI2MjM1IiwianRpIjoiMmJjMTVjMTEtYTBhZi00NzkwLTk3YmQtNWJiNDk0NjhiYTJiIn0.o-JB9eNULxWJKHU9MEdUopxr7nTtKXuKq3Kd8WrKLbw"
    );

    /**
     * A general purpose {@link Gson} instance.
     */
    public static final Gson GSON = new GsonBuilder().create();

    /**
     * Services all HTTP requests.
     */

    private volatile CompletableFuture<T> task;

    private final boolean authorization;
    private final AtomicInteger jwtSlot = new AtomicInteger();

    protected ApiDataFetcher(boolean publicFetcher) {
        this.authorization = publicFetcher;
        jwtSlot.set(ThreadLocalRandom.current().nextInt(TOKENS.size()));
    }

    protected ApiDataFetcher() {
        this(true);
    }

    @Override
    final void handleFetch(String link) {
        try {
            HttpRequest request;
            if (link.contains("mine")) {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(link))
                        .headers("authority", "api.upxland.me",
                                "origin", "https://play.upland.me",
                                "referer", "https://play.upland.me/",
                                "authorization", TOKENS.get(0),
                                "accept", "application/json")
                        .build();
            } else {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(link))
                        .headers("authority", "api.upxland.me",
                                "origin", "https://play.upland.me",
                                "referer", "https://play.upland.me/",
                                "authorization", TOKENS.get(0),
                                "accept", "application/json")
                        .build();
            }
            logger.debug("API request {}", link);
            task = client.sendAsync(request, BodyHandlers.ofString()).
                    handleAsync((response, throwable) -> {
                        if (throwable != null) {
                            logger.catching(throwable);
                            return null;
                        }
                        if (response == null) {
                            return null;
                        }
                        String body = response.body();
                        if (body.contains("Service is temporarily unavailable")) {
                            idle = true;
                            return null;
                        }
                        idle = false;
                        try {
                            handleResponse(link, body);
                        } catch (Exception e) {
                           logger.catching(e);
                        }
                        return getResult();
                    });
        } catch (Exception e) {
           logger.catching(e);
        }
    }

    public abstract T getResult();

    public abstract T getDefaultResult();

    public boolean isDone() {
        if (task != null) {
            return task.isDone();
        }
        return false;
    }

    public T waitUntilDone() throws ExecutionException, InterruptedException {
        try {
            if (task == null) {
                fetch();
            }
            return task.get(1, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            return getDefaultResult();
        }
    }

    public CompletableFuture<T> getTask() {
        return task;
    }

    private String getNextToken() {
        for (; ; ) {
            int current = jwtSlot.get();
            int next;
            if (current == (TOKENS.size() - 1)) {
                next = 0;
            } else {
                next = current + 1;
            }
            if (jwtSlot.compareAndSet(current, next))
                return TOKENS.get(next);
        }
    }
}
