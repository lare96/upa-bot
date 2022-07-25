package me.upa.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.io.Serializable;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static me.upa.discord.DiscordService.PURPLE;

public class PendingWeeklyReport implements Serializable {

    private static final long serialVersionUID = -1016745814779473809L;

    private final String blockchainAccountId;
    private final long memberId;
    private final String username;

    private final AtomicLong visitFees = new AtomicLong();
    private final AtomicInteger pacSpent = new AtomicInteger();
    private final AtomicInteger pacGained = new AtomicInteger();

    private volatile boolean ready;

    private final AtomicReference<Instant> lastQuery = new AtomicReference<>();

    public PendingWeeklyReport(String blockchainAccountId, long memberId, String username, Instant lastQuery) {
        this.blockchainAccountId = blockchainAccountId;
        this.memberId = memberId;
        this.username = username;
        this.lastQuery.set(lastQuery);
    }

    public MessageEmbed generate(Instant origin) {
        String formattedOrigin = DiscordService.DATE_FORMAT.format(origin);
        return new EmbedBuilder().setDescription("Here is your UPA status report for the week of **" + formattedOrigin + "**.").
                addField("Incoming visit fees", "+" + DiscordService.COMMA_FORMAT.format(visitFees.get()) + " UPX", false).
                addField("PAC earned", "+" + DiscordService.COMMA_FORMAT.format(pacGained.get()) + " PAC", false).
                addField("PAC spent", "-" + DiscordService.COMMA_FORMAT.format(pacSpent.get()) + " PAC", false).
                setFooter("Still in beta, suggest changes in #support. You can turn this off in your /account overview").setColor(PURPLE).build();
    }

    public String getBlockchainAccountId() {
        return blockchainAccountId;
    }

    public long getMemberId() {
        return memberId;
    }

    public String getUsername() {
        return username;
    }

    public AtomicLong getVisitFees() {
        return visitFees;
    }

    public boolean isReady() {
        return ready;
    }

    public void setFinished() {
        ready = true;
    }

    public AtomicReference<Instant> getLastQuery() {
        return lastQuery;
    }

    public AtomicInteger getPacGained() {
        return pacGained;
    }

    public AtomicInteger getPacSpent() {
        return pacSpent;
    }
}
