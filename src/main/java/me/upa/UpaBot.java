package me.upa;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Service;
import me.upa.variable.SystemVariableRepository;
import me.upa.discord.DiscordService;
import me.upa.service.DailyResetMicroService;
import me.upa.service.DatabaseCachingService;
import me.upa.service.EventProcessorService;
import me.upa.service.MemberVerificationMicroService;
import me.upa.service.MicroServiceProcessor;
import me.upa.service.PacLotteryMicroService;
import me.upa.service.PaidVisitsMicroService;
import me.upa.service.PropertySynchronizationService;
import me.upa.service.ScholarVerificationService;
import me.upa.service.SalesProcessorService;
import me.upa.service.SparkTrainMicroService;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.print.DocFlavor.READER;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Class that starts the application.
 *
 * @author lare96
 */
public final class UpaBot {

    /**
     * The global bot variables.
     */
    private static final SystemVariableRepository variables;

    /* All top level services and microservices. */
    private static final SalesProcessorService salesService;
    private static final DiscordService discordService;
    private static final MemberVerificationMicroService memberVerificationService;
    private static final PropertySynchronizationService propertySynchronizationService;
    private static final DatabaseCachingService databaseCachingService;
    private static final ScholarVerificationService scholarVerificationService;
    private static final EventProcessorService eventProcessorService;
    private static final MicroServiceProcessor microServiceProcessor;
    private static final DailyResetMicroService dailyResetService;
    private static final PaidVisitsMicroService paidVisitsService;
    private static final SparkTrainMicroService sparkTrainMicroService;
    private static final PacLotteryMicroService pacLotteryMicroService;
    private static final ImmutableList<Service> SERVICES;

    static {
        // Asynchronous logging.
        System.setProperty("log4j.skipJansi", "true");
        System.setProperty("Log4jContextSelector",
                "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");

        // UpaBotContext context = new UpaBotContext();
        variables = SystemVariableRepository.newInstance();

        /* Initialize and start services and microservices. */
        discordService = new DiscordService();
        salesService = new SalesProcessorService();
        propertySynchronizationService = new PropertySynchronizationService();
        databaseCachingService = new DatabaseCachingService();
        scholarVerificationService = new ScholarVerificationService();
        eventProcessorService = new EventProcessorService();
        microServiceProcessor = new MicroServiceProcessor();
        dailyResetService = new DailyResetMicroService();
        memberVerificationService = new MemberVerificationMicroService();
        paidVisitsService = new PaidVisitsMicroService();
        sparkTrainMicroService = new SparkTrainMicroService();
        pacLotteryMicroService = new PacLotteryMicroService();
        microServiceProcessor.addService(dailyResetService);
        microServiceProcessor.addService(memberVerificationService);
        microServiceProcessor.addService(paidVisitsService);
        microServiceProcessor.addService(sparkTrainMicroService);
        microServiceProcessor.addService(pacLotteryMicroService);
        SERVICES = ImmutableList.of(discordService, databaseCachingService, salesService, propertySynchronizationService, scholarVerificationService, eventProcessorService, microServiceProcessor);
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

        var timer = Stopwatch.createStarted();
        for (Service nextService : SERVICES) {
            nextService.startAsync().awaitRunning();
            String serviceName = nextService.getClass().getSimpleName();
            logger.info("Service [" + serviceName + "] started in " + timer.elapsed().toMillis() + "ms.");
            timer.reset().start();
        }

        discordService.getStatisticsCommand().load();
    }

    /**
     * Saves {@code dataObject} to {@code filePath} in the form of binary data.
     *
     * @param filePath The path.
     * @param dataObject The serializable data object.
     */
    public static boolean save(Path filePath, Serializable dataObject) {
        try (var fileOut = new FileOutputStream(filePath.toFile()); var objOut = new ObjectOutputStream(fileOut)) {
            objOut.writeObject(dataObject);
        } catch (IOException e) {
            logger.warn("File at " + filePath + " could not be serialized.", e);
            return false;
        }
        return true;
    }

    /**
     * Loads binary data from {@code filePath} to obtain a {@link Serializable} instance.
     *
     * @param filePath The path.
     * @return The serializable instance.
     */
    public static <T extends Serializable> T load(Path filePath) {
        if (Files.exists(filePath)) {
            try (var fileIn = new FileInputStream(filePath.toFile()); var objIn = new ObjectInputStream(fileIn)) {
                return (T) objIn.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    /**
     * @return A global instance of the system variables.
     */
    public static SystemVariableRepository variables() {
        return variables;
    }

    /* Service and microservice getters.*/
    public static DiscordService getDiscordService() {
        return discordService;
    }

    public static MemberVerificationMicroService getMemberVerificationService() {
        return memberVerificationService;
    }

    public static PropertySynchronizationService getPropertySynchronizationService() {
        return propertySynchronizationService;
    }

    public static DatabaseCachingService getDatabaseCachingService() {
        return databaseCachingService;
    }

    public static EventProcessorService getEventProcessorService() {
        return eventProcessorService;
    }

    public static SparkTrainMicroService getSparkTrainMicroService() {
        return sparkTrainMicroService;
    }

    public static PacLotteryMicroService getPacLotteryMicroService() {
        return pacLotteryMicroService;
    }

    /* Discourage instantiation. */
    private UpaBot() {
    }
}