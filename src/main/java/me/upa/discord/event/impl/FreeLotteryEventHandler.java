package me.upa.discord.event.impl;

import me.upa.discord.event.UpaEventHandler;
import me.upa.discord.event.UpaEventRarity;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

import java.util.List;

public final class FreeLotteryEventHandler extends UpaEventHandler {
    private static final long serialVersionUID = -3110915544137663928L;

    public UpaEventRarity rarity() {
        return UpaEventRarity.UNCOMMON;
    }

    @Override
    public String name() {
        return "Free Lottery Tickets";
    }

    @Override
    public int durationDays() {
        return 3;
    }

    @Override
    public StringBuilder buildMessageContent() {
        return new StringBuilder("For the duration of this event, buying a lottery ticket is free!");
    }

    @Override
    public List<ActionRow> buildMessageComponents() {
        return List.of(ActionRow.of(Button.of(ButtonStyle.PRIMARY, "buy_ticket", "Buy ticket (FREE)", Emoji.fromUnicode("U+1F3AB"))));
    }
}
