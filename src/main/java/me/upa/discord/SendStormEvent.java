package me.upa.discord;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import me.upa.UpaBot;
import me.upa.UpaBotContext;
import me.upa.discord.CreditTransaction.CreditTransactionType;
import me.upa.discord.command.EventCommands;
import me.upa.fetcher.VisitorsDataFetcher;
import me.upa.game.PropertyVisitor;
import me.upa.service.DatabaseCachingService;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SendStormEvent extends Event {
    private static final long serialVersionUID = -3387158997444412748L;

    private static final Logger logger = LogManager.getLogger();
    private final Multiset<Long> sends = ConcurrentHashMultiset.create();
    private final Set<Long> participants;
    private final long propertyId;

    private volatile long commentId;

    private volatile Instant lastChecked;
private final UpaBotContext ctx;
    public SendStormEvent(UpaBotContext ctx, Set<Long> participants, long propertyId) {
        super("Send Storm");
        this.ctx = ctx;
        this.participants = participants;
        this.propertyId = propertyId;
    }

    @Override
    public void handleConfirm(ButtonInteractionEvent event) {
        TextChannel channel = ctx.discord().guild().getTextChannelById("982800525386993694");
        Message message = channel.sendMessage(EventCommands.generateMessage(ctx, this)).complete();
        commentId = message.getIdLong();
        lastChecked = Instant.now();
    }

    @Override
    public void handleProcess() {
        try {
            if (sends.size() == participants.size()) {
                boolean done = true;
                for (Entry<Long> next : sends.entrySet()) {
                    if (next.getCount() < 10) {
                        done = false;
                        break;
                    }
                }
                if (done) {
                    ctx.eventProcessor().stop();
                    return;
                }
            }
            List<CreditTransaction> transactions = new ArrayList<>();
            DatabaseCachingService databaseCaching = ctx.databaseCaching();
            var visitorsFetcher = new VisitorsDataFetcher(propertyId);
            visitorsFetcher.fetch();
            List<PropertyVisitor> visitors = visitorsFetcher.waitUntilDone();
            if (visitors == null) {
                return;
            }
            try {
                for (PropertyVisitor next : visitors) {
                    if (next.isPending()) {
                        continue;
                    }
                    if (next.getVisitedAt().isAfter(lastChecked)) {
                        Long memberId = databaseCaching.getMemberNames().inverse().get(next.getUsername());
                        if (memberId == null || !participants.contains(memberId)) {
                            continue;
                        }
                        UpaMember upaMember = databaseCaching.getMembers().get(memberId);
                        if (upaMember == null) {
                            continue;
                        }
                        int count = sends.add(memberId, 1);
                        if (count >= 10) {
                            continue;
                        }
                        if (count == 9) {
                            transactions.add(new CreditTransaction(upaMember, 200, CreditTransactionType.EVENT, "the **Send Storm Event**"));
                        }
                    }
                }
            } finally {
                lastChecked = Instant.now();
            }
            if (transactions.size() > 0) {
                ctx.discord().sendCredit(transactions);
            }
            ctx.discord().guild().getTextChannelById(982800525386993694L).editMessageById(commentId, EventCommands.generateMessage(ctx, this)).queue();
        } catch (Exception e) {
            logger.catching(e);
        }
    }

    public Multiset<Long> getSends() {
        return sends;
    }

    public Set<Long> getParticipants() {
        return participants;
    }

    public long getPropertyId() {
        return propertyId;
    }

    public void setCommentId(long commentId) {
        this.commentId = commentId;
    }

    public void setLastChecked(Instant lastChecked) {
        this.lastChecked = lastChecked;
    }

    public Instant getLastChecked() {
        return lastChecked;
    }

    public long getCommentId() {
        return commentId;
    }

}
