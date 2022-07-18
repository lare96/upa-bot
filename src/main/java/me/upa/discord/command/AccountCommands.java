package me.upa.discord.command;

import com.google.common.primitives.Longs;
import me.upa.UpaBot;
import me.upa.discord.DiscordService;
import me.upa.discord.UpaBuildRequest;
import me.upa.discord.UpaLottery;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaProperty;
import me.upa.game.Structure;
import me.upa.service.DatabaseCachingService;
import me.upa.service.SparkTrainMicroService;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu.Builder;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AccountCommands extends ListenerAdapter {



    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("account")) {
            handleOverviewCommand(event);
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null) {
            return;
        }
        switch (event.getButton().getId()) {
            /*   case "report_a_bug":
                event.replyModal(Modal.create("report_a_bug_form", "Report a bug/Send feedback").
                        addActionRow(TextInput.create("report_a_bug_form_subject", "Subject", TextInputStyle.SHORT).
                                setRequired(true).setPlaceholder("I love UPA!").build()).
                        addActionRow(TextInput.create("report_a_bug_form_description", "Description", TextInputStyle.PARAGRAPH).
                                setRequired(true).setPlaceholder("I love everything about this server, especially that dapper g.o.a.t! How is he so rich?").build()).
                        build()).queue();
                break;*/
            case "pac_store":
                PacCommands.openStore(event);
                event.editButton(event.getButton().asDisabled()).queue();
                break;
            case "mark_acknowledged":
                if (event.getTextChannel().getIdLong() == 984551707176480778L && event.getMember() != null && !event.getMember().getUser().isBot()) {
                    Message success = event.getMessage();
                    success.delete().queue();
                }
                break;
            case "link":
                event.replyModal(Modal.create("link_form", "Please enter your in-game name").
                        addActionRow(TextInput.create("link_form_ign", "In-game name", TextInputStyle.SHORT).
                                setRequired(true).setPlaceholder("rich_goat").build()).
                        build()).queue();
                break;
        }
    }

    public static Button linkButton() {
        return Button.of(ButtonStyle.PRIMARY, "link", "Create account", Emoji.fromUnicode("U+1F517"));
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        switch (event.getModalId()) {
            /*case "report_a_bug_form":
                event.deferReply(true).queue();
                String subject = event.getValue("report_a_bug_form_subject").getAsString();
                String description = event.getValue("report_a_bug_form_description").getAsString();
                Member requester = event.getMember();
                if (requester != null) {
                    UpaMember upaMember = UpaBot.getDatabaseCachingService().getMembers().get(requester.getIdLong());
                    if (upaMember != null)
                        UpaBot.getDiscordService().sendFeedbackOrBugMsg(upaMember, subject, description,
                                msg -> event.getHook().setEphemeral(true).editOriginal("Your feedback has been sent to the UPA team. If we find it useful we will award you with PAC!").queue());
                }
                break;*/
            case "link_form":
                String inGameName = event.getValue("link_form_ign").getAsString();
                LinkCommand.handleLinkCommand(event, inGameName, -1L);
                break;
        }
    }

    private void handleOverviewCommand(SlashCommandInteractionEvent event) {
        Role lotteryRole = UpaBot.getDiscordService().guild().getRoleById(983750455429570620L);
        DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
        long memberId = event.getMember().getIdLong();

        UpaMember upaMember = databaseCaching.getMembers().get(memberId);
        if (upaMember == null) {
            event.reply("It seems you are not a UPA member. Would you like to become one?").addActionRow(
                    linkButton()).setEphemeral(true).queue();
            return;
        }
        int propertiesOwned = databaseCaching.getMemberProperties().get(upaMember.getKey()).size();
        event.deferReply().setEphemeral(true).queue();

        String notification = null;

        if(event.getMember().getRoles().contains(lotteryRole) && !UpaBot.getPacLotteryMicroService().hasTicket(memberId)) {
            UpaLottery lottery = UpaBot.variables().lottery().getValue();
            notification = "A lottery with a total pot of **"+lottery.getPac().get()+" PAC** is ongoing, buy a ticket @ <#993201967096660069>!";
        }
        var sb = new StringBuilder(notification != null ? notification : "");
        sb.append("```\n");
        sb.append("! ~ Account ~ !").append('\n');
        sb.append("In-game name: ").append(upaMember.getInGameName()).append('\n');
        sb.append("Blockchain account ID: ").append(upaMember.getBlockchainAccountId()).append('\n');
        sb.append("PAC: ").append(upaMember.getCredit()).append('\n');
        sb.append("Referrals: ").append(upaMember.getReferrals().get()).append('\n');
        sb.append("Join date: ").append(DiscordService.DATE_FORMAT.format(upaMember.getJoinDate())).append("\n\n\n");
        sb.append("! ~ Node ~ !").append('\n');
        sb.append("Properties owned: ").append(propertiesOwned).append('\n');
        sb.append("Total UP2: ").append(DiscordService.COMMA_FORMAT.format(upaMember.getTotalUp2().get())).append('\n');
        int rank = 1;
        for (var next : UpaBot.getDiscordService().getStatisticsCommand().getStatisticsData().getTopOwners().entrySet()) {
            if (memberId == next.getKey()) {
                break;
            }
            rank++;
        }
        sb.append("Ownership ranking #").append(rank).append('\n');
        sb.append("Passenger #").append(upaMember.getSparkTrainPlace().get()).append(" with ").append(DiscordService.DECIMAL_FORMAT.format(upaMember.getTotalSsh())).append(" SSH").append("\n\n\n");
        sb.append("! ~ Contributions ~ !").append('\n');
        sb.append("Sends: ").append(upaMember.getSends().get()).append("\n");
        sb.append("Sponsored sends: ").append(upaMember.getSponsoredSends().get()).append("\n");
        rank = 1;
        for (var next : UpaBot.getDiscordService().getScholarshipCommands().computeLeaderboard()) {
            if (next.getMemberId() == memberId) {
                break;
            }
            rank++;
        }
        sb.append("Leaderboard position #").append(rank).append("\n");
        sb.append("```");
        event.getHook().setEphemeral(true);
        event.getHook().sendMessage(sb.toString()).addActionRow(
                Button.of(ButtonStyle.PRIMARY, "pac_store", "PAC store", Emoji.fromUnicode("U+1F3EA"))).queue();
    }
}
