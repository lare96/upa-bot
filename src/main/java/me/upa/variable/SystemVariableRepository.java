package me.upa.variable;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import me.upa.discord.PacHistoryService.PacHistoryStatement;
import me.upa.discord.UpaLottery;
import me.upa.discord.UpaStoreRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
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

    public static SystemVariableRepository newInstance() {
        if (initialized.compareAndSet(false, true)) {
            return new SystemVariableRepository().loadVariables();
        }
        throw new IllegalStateException("SystemVariableRepository already created.");
    }

    private final SystemVariable<Instant> lastDailyReset =
            SystemVariable.of(Instant.parse("2022-06-22T08:51:05.386325900Z"), "lastDailyReset");

    private final SystemVariable<Map<Long, UpaStoreRequest>> storeRequests =
            SystemVariable.of(new ConcurrentHashMap<>(), "storeRequests");
    private final SystemVariable<UpaLottery> lottery = SystemVariable.of(null, "lottery");

    private final SystemVariable<Map<Long, Instant>> slots = SystemVariable.of(new ConcurrentHashMap<>(), "slots");

    private final SystemVariable<ListMultimap<Long, PacHistoryStatement>> creditHistory =
            SystemVariable.of(Multimaps.synchronizedListMultimap(ArrayListMultimap.create()), "creditHistory");


    public SystemVariable<Instant> lastDailyReset() {
        return lastDailyReset;
    }

    public SystemVariable<Map<Long, UpaStoreRequest>> storeRequests() {
        return storeRequests;
    }

    public SystemVariable<UpaLottery> lottery() {
        return lottery;
    }

    public SystemVariable<Map<Long, Instant>> slots() {
        return slots;
    }

    public SystemVariable<ListMultimap<Long, PacHistoryStatement>> creditHistory() {
        return creditHistory;
    }

    private SystemVariableRepository loadVariables() {
      /*  ExecutorService service = Executors.newFixedThreadPool(2);
        service.execute(lastDailyReset::load);
        service.execute(storeRequests::load);
        service.execute(lottery::load);
        service.shutdown();
        Uninterruptibles.awaitTerminationUninterruptibly(service);*/
        // TODO load somewhere else
        lastDailyReset.load();
        storeRequests.load();
        lottery.load();
        return this;
    }

    private SystemVariableRepository() {
    }
}
