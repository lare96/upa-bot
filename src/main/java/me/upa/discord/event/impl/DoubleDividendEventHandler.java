package me.upa.discord.event.impl;

import me.upa.discord.event.UpaEventHandler;
import me.upa.discord.event.UpaEventRarity;
import net.dv8tion.jda.api.interactions.components.ActionRow;

import java.util.List;

public final class DoubleDividendEventHandler extends UpaEventHandler {
    private static final long serialVersionUID = -8136890316050099749L;

    @Override
    public UpaEventRarity rarity() {
        return UpaEventRarity.UNCOMMON;
    }

    @Override
    public String name() {
        return "Double Dividends";
    }

    @Override
    public int durationDays() {
        return 4;
    }

    @Override
    public StringBuilder buildMessageContent() {
        return new StringBuilder().append("For the duration of this event you will receive double dividends when using /pac daily!\n");
    }

    @Override
    public List<ActionRow> buildMessageComponents() {
        return List.of();
    }
}
