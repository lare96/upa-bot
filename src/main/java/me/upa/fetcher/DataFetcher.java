package me.upa.fetcher;

import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.Executors;

public abstract class DataFetcher {
    protected static HttpClient client = HttpClient.newBuilder().executor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() / 2)).build();


    /**
     * Invoked when a response is received from the server.
     *
     * @param link The link that the data request was sent to.
     * @param response The response received.
     * @throws Exception If any errors occur.
     */
    protected abstract void handleResponse(String link, String response);

    abstract void handleFetch(String link);

    /**
     * Invoked when determining what links to send GET requests to.
     *
     * @throws Exception If any errors occur.
     */
    protected List<String> computeFetchLinks() {
        return List.of();
    }

    /**
     * Invoked when data from all computed links have been fetched.
     */
    void handleCompletion()   {

    }

    /**
     * @throws Exception If any errors occur.
     */
    public final void fetch(List<String> links) {
        if (links.size() > 0) {
            for (String nextLink : links) {
                handleFetch(nextLink);
            }
            handleCompletion();
        }
    }

    /**
     * @throws Exception If any errors occur.
     */
    public final void fetch(String link)   {
        fetch(List.of(link));
    }

    public final void fetch()  {
        fetch(computeFetchLinks());
    }
}
