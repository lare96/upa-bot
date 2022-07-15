package me.upa.fetcher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.net.URL;

public abstract class CsvDataFetcher extends DataFetcher {
private static final Logger logger = LogManager.getLogger();
    @Override
    final void handleFetch(String link) {
        fetchCsv(link);
    }

    private void fetchCsv(String link) {
        try (BufferedInputStream in = new BufferedInputStream(new URL(link).openStream())) {
            handleResponse(link, new String(in.readAllBytes()));
        } catch (FileNotFoundException e) {
            // Catch and discard silently.
        } catch (Exception e) {
           logger.error(link, e);
        }
    }
}