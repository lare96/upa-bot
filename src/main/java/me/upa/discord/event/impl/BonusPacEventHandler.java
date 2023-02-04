package me.upa.discord.event.impl;

import me.upa.discord.event.UpaEventHandler;
import me.upa.discord.event.UpaEventRarity;
import me.upa.discord.listener.command.PacCommands;
import net.dv8tion.jda.api.interactions.components.ActionRow;

import java.util.List;

public final class BonusPacEventHandler extends UpaEventHandler {

    private static final long serialVersionUID = -4647185084594419803L;

    @Override
    public UpaEventRarity rarity() {
        return UpaEventRarity.COMMON;
    }

    @Override
    public String name() {
        return "Bonus Purchased PAC";
    }

    @Override
    public int durationDays() {
        return 7;
    }

    @Override
    public StringBuilder buildMessageContent() {
        return new StringBuilder().append("All PAC purchases through UPX transfers and visits will now give a bonus of 25%! ").
                append("As usual, your PAC will be auto-awarded after a few minutes. ").
                append("Please go to <#986638348418449479> if you have any issues.\n\n").
                append("UPX Transfers: Send a UPX transfer to unruly_cj\n").
                append("Normal rate -> You get 2 PAC for every 1 UPX transferred\n").
                append("**Event period rate -> You get 2.5 PAC for every 1 UPX transferred\n\n**").
                append("Visits: Visit any of the properties listed below\n").
                append("Normal rate -> You get 100% of the visit fee as PAC\n").
                append("**Event period rate -> You get 125% of the visit fee as PAC\n\n**");
    }

    @Override
    public List<ActionRow> buildMessageComponents() {
        return List.of(ActionRow.of(
                PacCommands.computePurchaseWithVisits()
        ));
    }
}
