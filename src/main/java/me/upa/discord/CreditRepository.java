package me.upa.discord;

import com.google.common.util.concurrent.AbstractIdleService;
import me.upa.UpaBot;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class CreditRepository extends AbstractIdleService {
    private final Path STORE_FILE = Paths.get("data", "pac_repository.bin");
    private static final long INITIAL_STORE = 100_000_000;
    private volatile long currentStore = INITIAL_STORE;

    public synchronized boolean take(int amount) {
        int newAmount = (int) (amount * getDistributionRate());
        if(currentStore - newAmount < 0) {
            return false;
        }
        currentStore -= newAmount;
        UpaBot.save(STORE_FILE, currentStore);
        return true;
    }

    public synchronized boolean put(int amount) {
        currentStore += amount;
        UpaBot.save(STORE_FILE, currentStore);
        return true;
    }

    public double getDistributionRate() {
        double current = currentStore;
        double maximum = INITIAL_STORE;
        int storePercent = (int) ((current / maximum) * 100.0);
        if (storePercent > 90) {
            return 1.0;
        } else if (storePercent > 75) {
            return 0.9;
        } else if (storePercent > 50) {
            return 0.7;
        } else if (storePercent > 25) {
            return 0.5;
        } else if (storePercent > 10) {
            return 0.3;
        } else if (storePercent >= 1) {
            return 0.1;
        }
        return 0;
    }

    @Override
    protected void startUp() throws Exception {
        synchronized (this) {
            currentStore = UpaBot.load(STORE_FILE);
        }
    }

    @Override
    protected void shutDown() throws Exception {

    }
}
