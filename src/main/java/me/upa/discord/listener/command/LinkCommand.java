package me.upa.discord.listener.command;

import me.upa.UpaBotContext;
import me.upa.discord.DiscordService;
import me.upa.discord.PendingUpaMember;
import me.upa.discord.UpaMember;
import me.upa.service.DatabaseCachingService;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
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
        for(UpaMember upaMember : ctx.databaseCaching().getMembers().values()) {
            if(upaMember.getActive().compareAndSet(false, true) && upaMember.getMemberId() == memberId) {
                MessageCreateBuilder mcb = new MessageCreateBuilder();
                mcb.addContent("We're glad to have you back at UPA! Your previous account data has been loaded and your properties will begin synchronizing shortly.\n\n");
                mcb.addContent("A lot might have changed since you were last here. Check out <#957201886337974272> for all of the latest updates.");
                event.reply(mcb.build()).setEphemeral(true).queue();
                SqlConnectionManager.getInstance().execute(new SqlTask<Void>() {
                    @Override
                    public Void execute(Connection connection) throws Exception {
                        try(PreparedStatement setActive = connection.prepareStatement("UPDATE members SET active = 1 WHERE member_id = ?;");
                        PreparedStatement setPropertiesActive = connection.prepareStatement("UPDATE node_properties SET active = 1 WHERE member_key = ?;")) {
                                setActive.setLong(1, upaMember.getMemberId());
                                setPropertiesActive.setInt(1, upaMember.getKey());
                                setActive.executeUpdate(); // TODO transaction?
                                setPropertiesActive.executeUpdate();
                        }
                        return null;
                    }
                });
                ctx.memberVerification().welcomeNewMember(memberId, databaseCaching);
                return;
            }
        }
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