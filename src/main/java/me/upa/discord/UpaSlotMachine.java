package me.upa.discord;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

import java.awt.*;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

public final class UpaSlotMachine implements Serializable {

    private static final long serialVersionUID = 4569212860452132166L;

    private volatile long lastWinner;
    private volatile long lastLoser;

    private volatile int lastWinAmount;
    private volatile int lastLoseAmount;

    private final Multiset<Long> allTimePacWon = ConcurrentHashMultiset.create();
    private final Multiset<Long> playedToday = ConcurrentHashMultiset.create();
    private final AtomicInteger todaysLosses = new AtomicInteger();

    public void open( IReplyCallback event) {
        event.replyEmbeds(new EmbedBuilder().
                setTitle("UPA Slot Machine").
                setColor(Color.GREEN).
                setDescription("Please select the amount of PAC you would like to wager below. Your PAC will either be doubled, or you will lose your wager. Good luck!").
                addField("Last winner", lastWinner == 0 ? "No one yet!" : "<@" + lastWinner + "> (" + DiscordService.COMMA_FORMAT.format(lastWinAmount) + " PAC)", false).
                addField("All-time PAC won", DiscordService.COMMA_FORMAT.format(allTimePacWon.size()) + " PAC", false).build()).
                addActionRow(
                        Button.of(ButtonStyle.PRIMARY, "wager_500", "Wager 500 PAC", Emoji.fromUnicode("U+1F4B5")),
                        Button.of(ButtonStyle.PRIMARY, "wager_1000", "Wager 1000 PAC", Emoji.fromUnicode("U+1F4B0")),
                        Button.of(ButtonStyle.PRIMARY, "wager_2000", "Wager 2000 PAC", Emoji.fromUnicode("U+1F911"))
                ).
                setEphemeral(true).queue();
    }

    public long getLastWinner() {
        return lastWinner;
    }

    public void setLastWinner(long lastWinner) {
        this.lastWinner = lastWinner;
    }

    public long getLastLoser() {
        return lastLoser;
    }

    public void setLastLoser(long lastLoser) {
        this.lastLoser = lastLoser;
    }

    public int getLastWinAmount() {
        return lastWinAmount;
    }

    public void setLastWinAmount(int lastWinAmount) {
        this.lastWinAmount = lastWinAmount;
    }

    public int getLastLoseAmount() {
        return lastLoseAmount;
    }

    public void setLastLoseAmount(int lastLoseAmount) {
        this.lastLoseAmount = lastLoseAmount;
    }

    public Multiset<Long> getAllTimePacWon() {
        return allTimePacWon;
    }

    public Multiset<Long> getPlayedToday() {
        return playedToday;
    }

    public AtomicInteger getTodaysLosses() {
        return todaysLosses;
    }
}
