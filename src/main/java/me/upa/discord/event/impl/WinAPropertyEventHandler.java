package me.upa.discord.event.impl;

import me.upa.discord.event.UpaEventHandler;
import me.upa.discord.event.UpaEventRarity;
import net.dv8tion.jda.api.interactions.components.ActionRow;

import java.util.List;

public class WinAPropertyEventHandler extends UpaEventHandler {

    public static final long GIVEAWAY_PROPERTY = -1;
    @Override
    public UpaEventRarity rarity() {
        return UpaEventRarity.RARE;
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public int durationDays() {
        return 0;
    }

    @Override
    public StringBuilder buildMessageContent() {
        return null;
    }

    @Override
    public List<ActionRow> buildMessageComponents() {
        return null;
    }
}
