package me.upa.discord;

import me.upa.UpaBot;
import me.upa.discord.command.AccountCommands;
import me.upa.discord.command.PacCommands;
import me.upa.discord.command.ScholarshipCommand;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.MessageBuilder.Formatting;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Option;
import net.dv8tion.jda.api.interactions.commands.Command.Subcommand;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FaqButtonListener extends ListenerAdapter {

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null)
            return;
        switch (event.getButton().getId()) {
            case "joining_spark_train":
                event.reply(new MessageBuilder().
                        append("Stake with us and receive a build on your property with no UPX cost! ").
                        append("Once you have accrued enough Spark Share Hours (SSH) by staking your way to the top of the train, all of our members will contribute spark to your build!\n\n").
                        append("To get started\n", Formatting.BOLD, Formatting.UNDERLINE).
                        append("1. Ensure that you have the <@&956793551230996502> role\n").
                        append("2. Stake your spark at whichever building has the green card in <#963108957784772659>. You will then receive the <@&965427810707595394> role\n").
                        append("3. Once you have the role, you can View your place in the spark train by going to the <#990518630129221652> channel\n\n").
                        append("Once you get near the top of the train, you can request a build of your choice using the button in /account! ").
                        append("As your structure gets built, you will lose SSH which will require you to continue staking in order to get another build.").
                        build()).setEphemeral(true).queue();
                break;
            case "getting_started":
                Role visitor = UpaBot.getDiscordService().guild().getRoleById(983122250964271124L);
                if (event.getMember().getRoles().contains(visitor)) {
                    event.reply(new MessageBuilder().append("Becoming a scholar\n", Formatting.BOLD, Formatting.UNDERLINE).
                            append("1. Use the \"Become a scholar\" button below\n").
                            append("2. Follow the steps in order to register\n").
                            append("3. UPA members can now get rewarded for visiting your property!\n\n").
                            append("Advisors\n", Formatting.BOLD, Formatting.UNDERLINE).
                            append("Please use these tags when reaching out for advice on certain topics. Inquire with Tier 1 members about earning an advisor role.\n\n").
                            append("<@&978148612339019877> for treasure hunting advice\n").
                            append("<@&982486805192507412> for property flipping adv ice\n").
                            append("<@&982504114107842620> for buy n' hold earnings advice\n").
                            append("<@&956795779241111612>/<@&976348618833428560> for general questions, errors, and bugs").
                            setActionRows(ActionRow.of(ScholarshipCommand.becomeAScholarButton())).build()).setEphemeral(true).queue();
                } else {
                    event.reply(new MessageBuilder().
                            append("In order to gain the <@&956793551230996502> role and all the access included, you must own at least 1 property in any of our nodes.\n\n").
                            append("1. Click on \"Create Account\" below\n").
                            append("2. Follow the steps in order to register\n\n").
                            append("3. Verify that the registration was successful by using /account").
                            append("Once you are registered, your property data will begin synchronizing and I will auto-assign you the role if you qualify.\n\n").
                            append("Advisors\n", Formatting.BOLD, Formatting.UNDERLINE).
                            append("Please use these tags when reaching out for advice on certain topics. Inquire with Tier 1 members about earning an advisor role.\n\n").
                            append("<@&978148612339019877> for treasure hunting advice\n").
                            append("<@&982486805192507412> for property flipping advice\n").
                            append("<@&982504114107842620> for buy n' hold earnings advice\n").
                            append("<@&956795779241111612>/<@&976348618833428560> for general questions, suggestions, and bugs").
                            setActionRows(ActionRow.of(AccountCommands.linkButton())
                            ).build()).setEphemeral(true).queue();
                }
                break;
            case "understanding_pac":
                event.reply(new MessageBuilder().
                        append("PAC (Property advisor credit) is used to reward active and longtime UPA members. It can be used to redeem rewards, participate in events, and can even be transferred to other members! ").
                        append("All instances of credit being awarded, redeemed, or transferred will be logged for everyone to see in <#983628894919860234>\n\n").
                        append("You can view anyone's PAC by holding down the target's @ name on mobile or right clicking on desktop and going to 'Apps.' ").
                        append("In that same menu there's an option for tipping PAC to fellow members, don't be afraid to use it!\n\n").
                        append("PAC will only be modified by the admins in the case of a bug or for events, and these will appear in the transactions channel as well. ").
                        append("For more info on the PAC store click the button below or use the /pac store command.")
                        .setActionRows(ActionRow.of(PacCommands.openStoreInfoButton())).build()).setEphemeral(true).queue();
                break;
            case "command_list":
                event.deferReply().setEphemeral(true).queue();
                UpaBot.getDiscordService().guild().retrieveCommands().queue(success -> {
                    MessageBuilder mb = new MessageBuilder();
                    for (Command cmd : success) {
                        if (cmd.getName().equals("admin") || cmd.getName().equals("View credit")) {
                            continue;
                        }
                        if (cmd.getSubcommands().size() > 0) {
                            for (Subcommand subcmd : cmd.getSubcommands()) {
                                writeCommand(cmd.getName(), subcmd.getName(), subcmd.getDescription(), subcmd.getOptions(), mb);
                            }
                        } else {
                            writeCommand(cmd.getName(), "", cmd.getDescription(), cmd.getOptions(), mb);
                        }
                    }
                    mb.append("**!help** - Gets a list of all commands from the <@876671849352814673> bot");
                    event.getHook().setEphemeral(true).editOriginal(mb.build()).queue();
                });
                break;
            case "videos":
                event.reply("https://youtu.be/GcoQGmz4b1A").setEphemeral(true).queue();
                break;
        }
    }

    private void writeCommand(String name, String subName, String description, List<Option> options, MessageBuilder mb) {
        mb.append("**/").append(name);
        if (subName.length() > 0) {
            mb.append(" ").append(subName);
        }
        mb.append("** ");
        for (Option option : options) {
            mb.append("[").append(option.getName()).append("]").append(" ");
        }
        mb.append("- ").append(description).append("\n\n");
    }
}
