package me.upa.variable;

import com.google.common.collect.Sets;
import me.upa.UpaBotContext;
import me.upa.discord.NodeListingRepository;
import me.upa.discord.SparkTrainRepository;
import me.upa.discord.SparkTrainSnapshot;
import me.upa.discord.UpaInformationRepository;
import me.upa.discord.UpaLottery;
import me.upa.discord.UpaSlotMachine;
import me.upa.discord.UpaStoreRequest;
import me.upa.discord.WeeklyReportData;
import me.upa.discord.event.UpaEvent;
import me.upa.discord.event.trivia.TriviaRepository;
import me.upa.discord.history.PacTransactionRepository;
import me.upa.service.PaidVisitsMicroService.PropertyVisitData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles the access and persistence of global variables.
 *
 * @author lare96
 */
public final class SystemVariableRepository {

    static final Path BASE_PATH = Paths.get("data", "variables");

    static {
        if (!Files.isDirectory(BASE_PATH)) {
            try {
                Files.createDirectory(BASE_PATH);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean loaded = new AtomicBoolean();

    public static SystemVariableRepository newInstance(UpaBotContext ctx) {
        if (initialized.compareAndSet(false, true)) {
            return new SystemVariableRepository(ctx);
        }
        throw new IllegalStateException("SystemVariableRepository already created.");
    }

    private final SystemVariable<Instant> lastDailyReset;
    private final SystemVariable<Map<Long, UpaStoreRequest>> storeRequests;
    private final SystemVariable<UpaLottery> lottery;
    private final SystemVariable<Instant> lastBlockchainFetch;
    private final SystemVariable<WeeklyReportData> reports;

    private final SystemVariable<NodeListingRepository> listings;
    private final SystemVariable<Set<Long>> fastListings;
    private final SystemVariable<PropertyVisitData> yields;
    private final SystemVariable<UpaEvent> event;
    private final SystemVariable<UpaSlotMachine> slotMachine;

    private final SystemVariable<TriviaRepository> triviaRepository;

    private final SystemVariable<SparkTrainRepository> sparkTrainRepository;

    private final SystemVariable<SparkTrainSnapshot<?>> sparkTrainSnapshot;

    private final SystemVariable<UpaInformationRepository> information;
private final SystemVariable<PacTransactionRepository> pacTransactions;
    public SystemVariable<Instant> lastDailyReset() {
        return lastDailyReset;
    }

    public SystemVariable<Map<Long, UpaStoreRequest>> storeRequests() {
        return storeRequests;
    }

    public SystemVariable<UpaLottery> lottery() {
        return lottery;
    }


    public SystemVariable<Instant> lastBlockchainFetch() {
        return lastBlockchainFetch;
    }

    public SystemVariable<WeeklyReportData> reports() {
        return reports;
    }

    public SystemVariable<PropertyVisitData> yields() {
        return yields;
    }

    public SystemVariable<NodeListingRepository> listings() {
        return listings;
    }

    public SystemVariable<Set<Long>> fastListings() {
        return fastListings;
    }

    public SystemVariable<UpaEvent> event() {
        return event;
    }

    public SystemVariable<UpaSlotMachine> slotMachine() {
        return slotMachine;
    }

    public SystemVariable<TriviaRepository> triviaRepository() {
        return triviaRepository;
    }

    public SystemVariable<SparkTrainRepository> sparkTrainRepository() {
        return sparkTrainRepository;
    }
    public SystemVariable<SparkTrainSnapshot<?>> sparkTrainSnapshot() {
        return sparkTrainSnapshot;
    }

    public SystemVariable<UpaInformationRepository> information() {
        return information;
    }

    public SystemVariable<PacTransactionRepository> pacTransactions() {
        return pacTransactions;
    }

    public void loadAll() {
        if (loaded.compareAndSet(false, true)) {
            lastDailyReset.load();
            storeRequests.load();
            lottery.load();
            lastBlockchainFetch.load();
            reports.load();
            listings.load();
            fastListings.load();
            yields.load();
            event.load();
            slotMachine.load();
            triviaRepository.load();
            sparkTrainRepository.load();
            sparkTrainSnapshot.load();
            information.load();
            pacTransactions.load();
        }
    }

    private SystemVariableRepository(UpaBotContext ctx) {
        lastDailyReset = SystemVariable.of(ctx, Instant.parse("2022-06-22T08:51:05.386325900Z"), "lastDailyReset");
        storeRequests = SystemVariable.of(ctx, new ConcurrentHashMap<>(), "storeRequests");
        lottery = SystemVariable.of(ctx, null, "lottery");
        lastBlockchainFetch = SystemVariable.of(ctx, Instant.parse("2021-12-08T19:45:44Z").plusMillis(72770400000L), "lastBlockchainFetch");
        reports = SystemVariable.of(ctx, new WeeklyReportData(), "reports");
        listings = SystemVariable.of(ctx, new NodeListingRepository(), "listings");
        fastListings = SystemVariable.of(ctx, Sets.newConcurrentHashSet(), "fastListings");
        yields = SystemVariable.of(ctx, new PropertyVisitData(), "yields");
        event = SystemVariable.of(ctx, new UpaEvent(), "event");
        slotMachine = SystemVariable.of(ctx, new UpaSlotMachine(), "slot_machine");
        triviaRepository = SystemVariable.of(ctx, new TriviaRepository(), "trivia_repository");
        sparkTrainRepository = SystemVariable.of(ctx, new SparkTrainRepository(), "spark_train_repository");
        sparkTrainSnapshot = SystemVariable.of(ctx, SparkTrainSnapshot.DEFAULT_SNAPSHOT, "spark_train_snapshot");
        information = SystemVariable.of(ctx, new UpaInformationRepository(ctx), "information_repository");
        pacTransactions = SystemVariable.of(ctx, new PacTransactionRepository(), "pac_transaction_repository");
    }
}
