package me.upa.discord;

import com.mysql.cj.x.protobuf.MysqlxExpr.Object;
import me.upa.UpaBot;
import me.upa.UpaBotContext;

import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class SparkTrainSnapshot<T extends Serializable> implements Serializable {

    public static final SparkTrainSnapshot<Object> DEFAULT_SNAPSHOT = new SparkTrainSnapshot<>(Duration.ofDays(1), 0, null);

    private static final long serialVersionUID = 3937498836140424327L;
    private final Duration duration;
    private final AtomicLong timestamp = new AtomicLong();
    private final AtomicInteger snapshotsLeft = new AtomicInteger();

    private final T data;

    public SparkTrainSnapshot(Duration duration, int frequency,  T data) {
        this.duration = duration;
        this.data = data;
        timestamp.set(System.nanoTime() + duration.toNanos());
        snapshotsLeft.set(frequency);
    }

    public void onSnapshot(UpaBotContext ctx) {
    }

    public void onFinish(UpaBotContext ctx) {

    }

    public boolean isDefault() {
        return this == DEFAULT_SNAPSHOT;
    }

    public boolean update(UpaBotContext ctx) {
        long currentNanos = System.nanoTime();
        if (currentNanos >= timestamp.get()) {
            onSnapshot(ctx);
            timestamp.set(System.nanoTime() + duration.toNanos());
            boolean finished = snapshotsLeft.decrementAndGet() <= 0;
            if (finished) {
                onFinish(ctx);
            }
            return finished;
        }
        return false;
    }

    public T getData() {
        return data;
    }
}
