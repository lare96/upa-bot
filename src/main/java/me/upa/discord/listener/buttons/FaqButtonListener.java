package me.upa.discord.listener.buttons;

import me.upa.UpaBotContext;
import me.upa.discord.UpaInformationRepository;
import me.upa.discord.UpaInformationRepository.UpaInformationType;
import me.upa.discord.listener.command.AccountCommands;
import me.upa.discord.listener.command.PacCommands;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Option;
import net.dv8tion.jda.api.interactions.commands.Command.Subcommand;
import net.dv8tion.jda.api.interactions.commands.Command.Type;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FaqButtonListener extends ListenerAdapter {
    /**
     * The context.
     */
    private final UpaBotContext ctx;

    public FaqButtonListener(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null)
            return;
        UpaInformationRepository repository = ctx.variables().information().get();
        switch (event.getButton().getId()) {
            case "joining_spark_train":
                event.reply(new MessageCreateBuilder().
                        addContent(repository.get(UpaInformationType.INFO_JOINING_THE_SPARK_TRAIN)).build()).setEphemeral(true).queue();
                break;
            case "getting_started":
                event.reply(new MessageCreateBuilder().
                        addContent(repository.get(UpaInformationType.INFO_GETTING_STARTED)).
                        setComponents(ActionRow.of(AccountCommands.linkButton())
                        ).build()).setEphemeral(true).queue();
                break;
            case "understanding_pac":
                event.reply(new MessageCreateBuilder().
                        addContent("__**What is PAC?\n**__").
                        addContent("PAC (Property advisor credit) is the in-house utility token here at UPA. It can be used to redeem rewards, participate in events, and can even be transferred to other members! ").
                        addContent("All instances of credit being awarded, redeemed, or transferred will be logged for everyone to see in <#1058028231388835840>\n\n").
                        addContent("__**Tokenomics\n**__").
                        addContent("One important facet of PAC that members should know is that it can be redeemed for UPX. <@220622659665264643> and <@373218455937089537> have made a commitment to back PAC at a rate of 5 PAC -> 1 UPX which means that; aside from utility, your PAC has value in pure UPX as well.\n\n").
                        addContent("PAC is minted\n").
                        addContent("- When a member purchases PAC\n").
                        addContent("- When a member participates in an event\n").
                        addContent("- When a member claims their daily dividend\n").
                        addContent("- When a member visits a scholar's (UPA member of Visitor rank) property\n").
                        addContent("- When a member mints a property in our node areas\n").
                        addContent("- When a member refers a unique member to our server through invite links\n\n").
                        addContent("PAC is burned whenever a member redeems their PAC for rewards.\n\n").
                        addContent("__**Transparency\n**__").
                        addContent("You can view anyone's PAC by holding down the target's @ name on mobile or right clicking on desktop and going to 'Apps.' ").
                        addContent("In that same menu there's an option for tipping PAC to fellow members, don't be afraid to use it!\n\n").
                        addContent("PAC will only be explicitly minted/burned by the admins in the case of a bug or for events, and these changes will appear in the transactions channel as well.\n\n").
                        addContent("For more info on the PAC store see <#1000173221326364722>.")
                        .setComponents(ActionRow.of(PacCommands.openStoreInfoButton())).build()).setEphemeral(true).queue();
                break;
            case "command_list":
                event.deferReply().setEphemeral(true).queue();
                ctx.discord().guild().retrieveCommands().queue(success -> {
                    List<Command> userCommands = new ArrayList<>();
                    StringBuilder sb = new StringBuilder();
                    for (Command cmd : success) {
                        if (cmd.getName().equals("admin") || cmd.getName().equals("View credit")) {
                            continue;
                        }
                        if (cmd.getSubcommands().size() > 0) {
                            for (Subcommand subcmd : cmd.getSubcommands()) {
                                writeCommand(cmd.getName(), subcmd.getName(), subcmd.getDescription(), subcmd.getOptions(), sb);
                            }
                        } else if (cmd.getType() == Type.USER) {
                            userCommands.add(cmd);
                        } else {
                            writeCommand(cmd.getName(), "", cmd.getDescription(), cmd.getOptions(), sb);
                        }
                    }
                    sb.append("**!help** - Gets a list of all commands from the <@876671849352814673> bot, only usable in <#967096154607194232>\n\n");
                    sb.append("__**\nUser menu commands\n**__");
                    sb.append("Use these commands by right clicking/pressing the member's @ name then going to 'Apps' https://i.imgur.com/hAtvtSL.gif\n\n");
                    for (Command cmd : userCommands) {
                        if (cmd.getName().equals("Give PAC [Admin]")) {
                            continue;
                        }
                        sb.append("**").append(cmd.getName()).append("**\n");
                    }
                    event.getHook().setEphemeral(true).editOriginal(new MessageEditBuilder().setContent(sb.toString()).build()).queue();
                });
                break;
            case "videos":
                event.reply("https://youtu.be/GcoQGmz4b1A").setEphemeral(true).queue();
                break;
        }
    }

    private void writeCommand(String name, String subName, String description, List<Option> options, StringBuilder mb) {
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
