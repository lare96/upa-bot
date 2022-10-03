package me.upa;

import me.upa.fetcher.ProfileDataFetcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class that starts the application.
 *
 * @author lare96
 */
public final class UpaBot {

    static {
        // Asynchronous logging.
        System.setProperty("log4j.skipJansi", "true");
        System.setProperty("Log4jContextSelector",
                "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
    }

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The main function that the application will be started from.
     */
    public static void main(String[] args) {
        logger.info("Starting UpaBot v1.0 services.");

        UpaBotContext ctx = new UpaBotContext();
        ProfileDataFetcher.ctx = ctx;
        ctx.loadVariables();
        ctx.loadMicroServices();
        ctx.startServices();
        ctx.discord().getStatisticsCommand().load();
    }

    /* Discourage instantiation. */
    private UpaBot() {
    }
}