package me.upa;

import me.upa.discord.event.UpaEvent;
import me.upa.discord.event.impl.BonusSshEventHandler;
import me.upa.discord.event.trivia.TriviaRepository;
import me.upa.fetcher.ProfileDataFetcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

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
    public static void main(String[] args) throws IOException {
        logger.info("Starting UpaBot v1.0 services.");

        Path origin = Paths.get("data");
        Path destination = Paths.get("backups");

        Files.copy(origin, destination, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);

        UpaBotContext ctx = new UpaBotContext();
        ProfileDataFetcher.ctx = ctx;
        ctx.loadVariables();
        ctx.loadMicroServices();
        ctx.startServices();
        ctx.discord().getStatisticsCommand().load();
        ctx.variables().triviaRepository().accessValue(TriviaRepository::load);
    }

    /* Discourage instantiation. */
    private UpaBot() {
    }
}