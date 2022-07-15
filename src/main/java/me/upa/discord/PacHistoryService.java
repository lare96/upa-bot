package me.upa.discord;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.AbstractIdleService;
import me.upa.UpaBot;
import me.upa.discord.CreditTransaction.CreditTransactionType;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class PacHistoryService extends AbstractIdleService {

    public final class PacHistoryStatement implements Serializable {
        private static final long serialVersionUID = 8313044548237827121L;
        private final long memberId;
        private final int amount;

        private final CreditTransactionType type;
        private final String reason;
        private final Instant timestamp;

        public PacHistoryStatement(long memberId, int amount, CreditTransactionType type, String reason, Instant timestamp) {
            this.memberId = memberId;
            this.amount = amount;
            this.type = type;
            this.reason = reason;
            this.timestamp = timestamp;
        }

        public long getMemberId() {
            return memberId;
        }

        public int getAmount() {
            return amount;
        }

        public CreditTransactionType getType() {
            return type;
        }

        public String getReason() {
            return reason;
        }

        public Instant getTimestamp() {
            return timestamp;
        }
    }

    public static void main(String[] args) {

    }

    private static final Path BASE_PATH = Paths.get("data", "transactions");
    private final ListMultimap<Long, PacHistoryStatement> statements = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());

    @Override
    protected void startUp() throws Exception {
    }

    @Override
    protected void shutDown() throws Exception {
    }

    public void log(long memberId, CreditTransaction transaction, String message) {
        Instant now = Instant.now();
        UpaBot.getDiscordService().execute(() -> {
            UpaBot.save(transaction.getUpaMember().getPacHistoryKey(),
                    new PacHistoryStatement(memberId, transaction.getAmount(), transaction.getTransactionType(), message, now));
        });
    }
}
