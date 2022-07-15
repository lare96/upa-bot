package me.upa.service;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import me.upa.UpaBot;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaProperty;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public final class DailyResetMicroService extends MicroService {

    public DailyResetMicroService() {
        super(Duration.ofMinutes(5));
    }

    @Override
    public void startUp() throws Exception {
        run();
    }

    @Override
    public void run() throws Exception {
        UpaBot.variables().lastDailyReset().access(lastDailyReset -> {
            // TODO compare and set with retry loop for thread safety
            Instant old = lastDailyReset.get();
            Instant next = wait(old);
            if (!Instant.now().isAfter(next)) {
                return false;
            }
            // Reset dailies.
            lastDailyReset.set(next);
            return true;
        });
    }

    public static Instant wait(Instant instant) {
        return instant.plus(24, ChronoUnit.HOURS);
    }
}
