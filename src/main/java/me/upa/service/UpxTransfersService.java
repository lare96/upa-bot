package me.upa.service;

import com.google.common.collect.Sets;
import com.google.common.primitives.Doubles;
import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.UpaBotContext;
import me.upa.discord.UpaMember;
import me.upa.discord.event.UpaEvent;
import me.upa.discord.event.impl.BonusPacEventHandler;
import me.upa.discord.listener.credit.CreditTransaction;
import me.upa.discord.listener.credit.CreditTransaction.CreditTransactionType;
import me.upa.fetcher.TransactionBlockchainFetcher;
import me.upa.fetcher.UpxTransferBlockchainFetcher;
import me.upa.game.UpxTransfer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;


public final class UpxTransfersService extends AbstractScheduledService {

    private enum State {
        FETCH_ACTIONS,
        FETCH_TRACES
    }

    public static final class UpxTransferData implements Serializable {
        private static final long serialVersionUID = -7461065903953430010L;
        private final Set<String> checkedTransactions = Sets.newConcurrentHashSet();
        private final Set<String> transactions = Sets.newConcurrentHashSet();

        public Set<String> getCheckedTransactions() {
            return checkedTransactions;
        }

        public Set<String> getTransactions() {
            return transactions;
        }
    }

    private static final class UpxTransferTransactionFetcher extends TransactionBlockchainFetcher<UpxTransfer> {

        public UpxTransferTransactionFetcher(String transactionId) {
            super(transactionId);
        }

        @Override
        public UpxTransfer handleResponse(String link, JsonElement response) {
            JsonObject responseObject = response.getAsJsonObject();
            JsonArray actions = responseObject.get("actions").getAsJsonArray();
            for (JsonElement nextAction : actions) {
                JsonObject nextActionObject = nextAction.getAsJsonObject();
                JsonObject act = nextActionObject.get("act").getAsJsonObject();
                String timestamp = nextActionObject.get("timestamp").getAsString();
                if (!act.has("name")) {
                    continue;
                }
                String actName = act.get("name").getAsString();
                JsonObject data = act.get("data").getAsJsonObject();
                if (!actName.equals("n111") || !data.has("p1") || !data.has("p45")) {
                    continue;
                }
                String sender = data.get("p1").getAsString();
                Double parsedAmount = Doubles.tryParse(data.get("p45").getAsString().replace(" UPX", ""));
                if (parsedAmount == null) {
                    continue;
                }
                int amount = parsedAmount.intValue();
                return new UpxTransfer(sender, amount, Instant.parse(timestamp + "Z"));
            }
            return null;
        }
    }

    private final Logger logger = LogManager.getLogger();
    private final UpaBotContext ctx;
    private volatile State state = State.FETCH_ACTIONS;

    public UpxTransfersService(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    protected void runOneIteration() throws Exception {
        try {
            switch (state) {
                case FETCH_ACTIONS:
                    ctx.variables().upxTransferData().accessValue(data -> {
                        try {
                            for (String id : new UpxTransferBlockchainFetcher().fetch().get()) {
                                if (!data.getCheckedTransactions().contains(id)) {
                                    data.getTransactions().add(id);
                                }
                            }
                            return true;
                        } catch (Exception e) {
                            logger.catching(e);
                            return false;
                        }
                    });
                    state = State.FETCH_TRACES;
                    break;
                case FETCH_TRACES:
                    ctx.variables().upxTransferData().accessValue(data -> {
                        try {
                            Iterator<String> it = data.getTransactions().iterator();
                            if (it.hasNext()) {
                                String id = it.next();
                                UpxTransfer transfer = new UpxTransferTransactionFetcher(id).fetch().get();
                                it.remove();
                                if (data.getCheckedTransactions().contains(id)) {
                                    return true;
                                }
                                String eosId = transfer.getFromBlockchainId();
                                UpaMember member = ctx.databaseCaching().getMembers().values().stream().filter(upaMember ->
                                        upaMember.getBlockchainAccountId().equals(eosId)).findFirst().orElse(null);
                                if (member == null) {
                                    return true;
                                }
                                int rate = UpaEvent.isActive(ctx, BonusPacEventHandler.class) ? 4 : 2;
                                ctx.discord().sendCredit(new CreditTransaction(member, transfer.getAmount() * rate, CreditTransactionType.PURCHASE, "UPX transfer"));
                                data.getCheckedTransactions().add(id);
                                return true;
                            }
                        } catch (Exception e) {
                            logger.catching(e);
                        }
                        return false;
                    });
                    state = State.FETCH_ACTIONS;
                    break;
            }
        } catch (Exception e) {
            logger.catching(e);
        }
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(2, 2, TimeUnit.MINUTES);
    }
}
