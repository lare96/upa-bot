package me.upa.variable;

import com.google.common.collect.Sets;
import me.upa.UpaBotContext;
import me.upa.discord.UpaLottery;
import me.upa.discord.PendingWeeklyReport;
import me.upa.discord.UpaStoreRequest;
import me.upa.discord.WeeklyReportData;

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
    private final SystemVariable<Set<Long>> propertyLookup;
    private final SystemVariable<WeeklyReportData> reports;
    public SystemVariable<Instant> lastDailyReset() {
        return lastDailyReset;
    }

    public SystemVariable<Map<Long, UpaStoreRequest>> storeRequests() {
        return storeRequests;
    }

    public SystemVariable<UpaLottery> lottery() {
        return lottery;
    }

    public SystemVariable<Set<Long>> propertyLookup() {
        return propertyLookup;
    }

    public SystemVariable<Instant> lastBlockchainFetch() {
        return lastBlockchainFetch;
    }

    public SystemVariable<WeeklyReportData> reports() {
        return reports;
    }

    public void loadAll() {
        if (loaded.compareAndSet(false, true)) {
            lastDailyReset.load();
            storeRequests.load();
            lottery.load();
            lastBlockchainFetch.load();
            propertyLookup.load();
            reports.load();
        }
    }

    private SystemVariableRepository(UpaBotContext ctx) {
        lastDailyReset = SystemVariable.of(ctx, Instant.parse("2022-06-22T08:51:05.386325900Z"), "lastDailyReset");
        storeRequests = SystemVariable.of(ctx, new ConcurrentHashMap<>(), "storeRequests");
        lottery = SystemVariable.of(ctx, null, "lottery");
        lastBlockchainFetch = SystemVariable.of(ctx, Instant.parse("2019-08-19T13:45:44.000Z"), "lastBlockchainFetch");
        propertyLookup = SystemVariable.of(ctx, Sets.newConcurrentHashSet(), "propertyLookup");
        reports = SystemVariable.of(ctx, new WeeklyReportData(), "reports");
    }
}
