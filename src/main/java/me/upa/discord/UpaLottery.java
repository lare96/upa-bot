package me.upa.discord;

import com.google.common.collect.Sets;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class UpaLottery implements Serializable {

    private static final long serialVersionUID = -3820371883415899735L;
    private final AtomicInteger pac = new AtomicInteger();

    private final AtomicLong lastWinner = new AtomicLong();
    private final Set<Long> contestants = Sets.newConcurrentHashSet();
    private final AtomicReference<Instant> finishedAt = new AtomicReference<>();

    {
        finishedAt.compareAndSet(null, Instant.now().plus(7, ChronoUnit.DAYS));
    }
    public long draw() {
        int winnerIndex = ThreadLocalRandom.current().nextInt(0, contestants.size());
        int currentIndex = 0;
        if(contestants.isEmpty()) {
            return -1;
        } else if(contestants.size() == 1) {
            return contestants.stream().findFirst().get();
        }
        for(long id : contestants) {
            if(currentIndex++ == winnerIndex) {
                return id;
            }
        }
        throw new IllegalStateException("Could not find winner.");
    }
    public AtomicInteger getPac() {
        return pac;
    }

    public AtomicLong getLastWinner() {
        return lastWinner;
    }

    public Set<Long> getContestants() {
        return contestants;
    }

    public AtomicReference<Instant> getFinishedAt() {
        return finishedAt;
    }
}
