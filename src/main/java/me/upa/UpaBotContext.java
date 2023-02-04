package me.upa;

import com.google.common.util.concurrent.Service;
import me.upa.discord.DiscordService;
import me.upa.discord.event.UpaEventMicroService;
import me.upa.service.DailyResetMicroService;
import me.upa.service.DatabaseCachingService;
import me.upa.service.ListingRemovalMicroService;
import me.upa.service.MemberVerificationMicroService;
import me.upa.service.MicroService;
import me.upa.service.MicroServiceProcessor;
import me.upa.service.PacLotteryMicroService;
import me.upa.service.PaidVisitsMicroService;
import me.upa.service.PropertyCachingMicroService;
import me.upa.service.PropertySynchronizationService;
import me.upa.service.SalesProcessorService;
import me.upa.service.SparkTrainMicroService;
import me.upa.service.TriviaQuestionFetcherService;
import me.upa.variable.SystemVariableRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Holds the core services of the UPA bot. Passed to lower level classes when they need information.
 *
 * @author lare96
 */
public final class UpaBotContext {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The global bot variables.
     */
    private final SystemVariableRepository variables = SystemVariableRepository.newInstance(this);

    /**
     * The discord handler.
     */
    private final DiscordService discordService = new DiscordService(this);

    /**
     * The member verification service.
     */
    private final MemberVerificationMicroService memberVerificationMs = new MemberVerificationMicroService(this);

    /**
     * The property synchronization service.
     */
    private final PropertySynchronizationService propertySynchronizationService = new PropertySynchronizationService(this);

    /**
     * The database caching service.
     */
    private final DatabaseCachingService databaseCachingService = new DatabaseCachingService(this);

    /**
     * The daily reset microservice.
     */
    private final DailyResetMicroService dailyResetMs = new DailyResetMicroService(this);

    /**
     * The paid visits' microservice.
     */
    private final PaidVisitsMicroService paidVisitsMs = new PaidVisitsMicroService(this);

    /**
     * The spark train microservice.
     */
    private final SparkTrainMicroService sparkTrainMs = new SparkTrainMicroService(this);

    /**
     * The PAC lottery microservice.
     */
    private final PacLotteryMicroService pacLotteryMs = new PacLotteryMicroService(this);

    /**
     * The microservice processor.
     */
    private final MicroServiceProcessor microServices = new MicroServiceProcessor();

    private final PropertyCachingMicroService propertyCachingMs = new PropertyCachingMicroService(this);
    private final ListingRemovalMicroService listingRemovalMs = new ListingRemovalMicroService(this);

    /**
     * Initializes persistent system variables.
     */
    public void loadVariables() {
        variables.loadAll();
    }

    /**
     * Initializes all {@link MicroService}s using the microservice processor.
     */
    public void loadMicroServices() {
        microServices.addService(dailyResetMs);
        microServices.addService(memberVerificationMs);
        microServices.addService(paidVisitsMs);
        microServices.addService(sparkTrainMs);
        microServices.addService(pacLotteryMs);
        //microServices.addService(propertyCachingMs);
        microServices.addService(listingRemovalMs);
        microServices.addService(new UpaEventMicroService(this));
    }


    /**
     * Starts all heavy duty {@link Service}s that are backed by their own threads.
     */
    public void startServices() {
        discordService.startAsync().awaitRunning();
        databaseCachingService.startAsync().awaitRunning();
        propertySynchronizationService.startAsync().awaitRunning();
        microServices.startAsync().awaitRunning();
        propertyCachingMs.startAsync().awaitRunning();
         //new TriviaQuestionFetcherService().startAsync().awaitRunning();
        // blockchainSynchronizationService.startAsync().awaitRunning();
    }

    /**
     * Saves {@code dataObject} to {@code filePath} in the form of binary data.
     *
     * @param filePath The path.
     * @param dataObject The serializable data object.
     */
    public boolean save(Path filePath, Serializable dataObject) {
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
    public <T extends Serializable> T load(Path filePath) {
        if (Files.exists(filePath)) {
            try (var fileIn = new FileInputStream(filePath.toFile()); var objIn = new ObjectInputStream(fileIn)) {
                return (T) objIn.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    /* Getters for system context fields. */
    public SystemVariableRepository variables() {
        return variables;
    }

    public DiscordService discord() {
        return discordService;
    }

    public DatabaseCachingService databaseCaching() {
        return databaseCachingService;
    }

    public SparkTrainMicroService sparkTrain() {
        return sparkTrainMs;
    }

    private final SalesProcessorService salesProcessorService = new SalesProcessorService(this);

    public SalesProcessorService salesProcessor() {
        return salesProcessorService;
    }

    public PacLotteryMicroService lottery() {
        return pacLotteryMs;
    }

    public PaidVisitsMicroService paidVisitsMs() {
        return paidVisitsMs;
    }

    public MemberVerificationMicroService memberVerification() {
        return memberVerificationMs;
    }

    public PropertySynchronizationService propertySync() {
        return propertySynchronizationService;
    }

    public ListingRemovalMicroService listingRemoval() {
        return listingRemovalMs;
    }

    UpaBotContext() {
    }
}
