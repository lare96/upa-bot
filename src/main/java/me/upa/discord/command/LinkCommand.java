package me.upa.discord.command;

import me.upa.UpaBot;
import me.upa.UpaBotContext;
import me.upa.discord.DiscordService;
import me.upa.discord.PendingUpaMember;
import me.upa.service.DatabaseCachingService;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles the command /link that lets a Discord member link their in-game account with UPA.
 *
 * @author lare96
 */
public final class LinkCommand extends ListenerAdapter {

    /**
     * Handles the link command.
     */
    public static void handleLinkCommand(UpaBotContext ctx, IReplyCallback event, String ign, long forceMemberId) {
        String username = ign.toLowerCase().trim();
        if (username.isBlank()) {
            event.reply("Please enter a valid username.").setEphemeral(true).queue();
            return;
        }
        DatabaseCachingService databaseCaching = ctx.databaseCaching();
        Member member = event.getMember();
        long memberId = forceMemberId != -1 ? forceMemberId : member.getIdLong();
        String inGameName = databaseCaching.getMemberNames().get(memberId);
        if (inGameName != null) {
            event.reply("Your in-game account '" + inGameName + "' has already been linked to UPA.").setEphemeral(true).queue();
            return;
        }
        int randomSellPrice;
        if (forceMemberId != -1) {
            randomSellPrice = -1;
            event.reply("Attempting to force link... Please confirm database results with /statistics in a few minutes.").setEphemeral(true).queue();
        } else {
            randomSellPrice = ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
            event.reply("In order to verify your identity, please set any property on the account **" + username + "** on sale for **" + DiscordService.COMMA_FORMAT.format(randomSellPrice) + "** UPX. Expires in one hour or if /account is used again.").setEphemeral(true).queue();
        }
       ctx.memberVerification().getPendingRegistrations().put(memberId, new PendingUpaMember(member, randomSellPrice, username));
    }
}