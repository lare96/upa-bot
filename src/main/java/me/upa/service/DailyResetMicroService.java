package me.upa.service;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import me.upa.UpaBot;
import me.upa.UpaBotContext;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaProperty;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

public final class DailyResetMicroService extends MicroService {

    private final UpaBotContext ctx;

    public DailyResetMicroService(UpaBotContext ctx) {
        super(Duration.ofMinutes(5));
        this.ctx = ctx;
    }

    @Override
    public void startUp() throws Exception {
        run();
    }

    @Override
    public void run() throws Exception {
        ctx.variables().lastDailyReset().access(lastDailyReset -> {
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
