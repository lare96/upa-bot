package me.upa.discord.command;

import com.google.common.primitives.Ints;
import me.upa.UpaBot;
import me.upa.discord.CreditTransaction;
import me.upa.discord.CreditTransaction.CreditTransactionType;
import me.upa.discord.UpaMember;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.jetbrains.annotations.NotNull;

/**
 * Handles the command /admin for administrative commands.
 *
 * @author lare96
 */
public final class AdminCommands extends ListenerAdapter {


    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("admin")) {
            switch (event.getSubcommandName()) {
                case "give_credit":
                    handleGiveCredit(event, event.getOptions().get(0).getAsMember(),
                            event.getOptions().get(1).getAsLong(),
                            event.getOptions().get(2).getAsString().trim());
                    break;
                case "force_link":
                    LinkCommand.handleLinkCommand(event, event.getOptions().get(0).getAsString(), event.getOptions().get(1).getAsMember().getIdLong());
                    break;
                case "send_message":
                    handleSendMessage(event);
                    break;
            }
        }
    }

    public static void handleGiveCredit(IReplyCallback event, Member discordMember, long enteredAmount, String enteredReason) {
        if (discordMember == null) {
            event.reply("Member does not exist on this server.").setEphemeral(true).queue();
            return;
        }
        int amount = Ints.checkedCast(enteredAmount);
        UpaMember member = UpaBot.getDatabaseCachingService().getMembers().get(discordMember.getIdLong());
        if (member == null) {
            event.reply("Member ID is invalid or the member hasn't used /account yet. You can force link them using /admin force_link.").setEphemeral(true).queue();
            return;
        }
        event.reply("Sending " + amount + " PAC to " + member.getInGameName() + ". Please verify if it was successful in <#983628894919860234>.").setEphemeral(true).queue();
        UpaBot.getDiscordService().sendCredit(new CreditTransaction(member, amount, CreditTransactionType.GIFTED, enteredReason));
    }

    private void handleSendMessage(SlashCommandInteractionEvent event) {
        if (event.getChannelType() != ChannelType.TEXT) {
            event.reply("You can only send a message to a text channel.").setEphemeral(true).queue();
            return;
        }
        event.getTextChannel().sendMessage(event.getOptions().get(0).getAsString()).queue();
        event.reply("Message sent.").setEphemeral(true).queue();
    }
}
