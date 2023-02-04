package me.upa.discord.listener.command;

import me.upa.UpaBotContext;
import me.upa.discord.DiscordService;
import me.upa.discord.UpaLottery;
import me.upa.discord.UpaMember;
import me.upa.discord.history.PacTransaction;
import me.upa.service.DatabaseCachingService;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AccountCommands extends ListenerAdapter {

    private final UpaBotContext ctx;

    public AccountCommands(UpaBotContext ctx) {
        this.ctx = ctx;
    }


    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("account")) {
            handleOverviewCommand(ctx, event.getMember().getIdLong(), event, false);
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
            case "mark_acknowledged":
                if (event.getChannel().asTextChannel().getIdLong() == 984551707176480778L && event.getMember() != null && !event.getMember().getUser().isBot()) {
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
                    UpaMember upaMember = ctx.databaseCaching().getMembers().get(requester.getIdLong());
                    if (upaMember != null)
                        ctx.discord().sendFeedbackOrBugMsg(upaMember, subject, description,
                                msg -> event.getHook().setEphemeral(true).editOriginal("Your feedback has been sent to the UPA team. If we find it useful we will award you with PAC!").queue());
                }
                break;*/
            case "link_form":
                String inGameName = event.getValue("link_form_ign").getAsString();
                LinkCommand.handleLinkCommand(ctx, event, inGameName, -1L);
                break;
        }
    }

    @Override
    public void onSelectMenuInteraction(@NotNull SelectMenuInteractionEvent event) {
        switch (event.getSelectedOptions().get(0).getValue()) {
            case "account_request_pac_history":
                showPacHistory(ctx, event.getMember(), event);
                break;
            case "account_become_a_vip":
                event.reply("For now, our VIP program is in private access until we have a proper set of standardized features. Until then, we will award trial VIP ranks in order to test features related to it.").setEphemeral(true).queue();
                break;

        }
    }

    public static void showPacHistory(UpaBotContext ctx, Member member, IReplyCallback event) {
        try {
            byte[] transactionData = ctx.variables().pacTransactions().get().getArchive(member.getIdLong());
            event.replyFiles(FileUpload.fromData(transactionData, event.getMember().getIdLong() + ".csv")).setEphemeral(true).queue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void handleOverviewCommand(UpaBotContext ctx, long memberId, IReplyCallback event, boolean admin) {
        Role lotteryRole = ctx.discord().guild().getRoleById(983750455429570620L);
        DatabaseCachingService databaseCaching = ctx.databaseCaching();

        UpaMember upaMember = databaseCaching.getMembers().get(memberId);
        if (upaMember == null || !upaMember.getActive().get()) {
            if (admin) {
                event.reply("This member is not registered with UPA.").setEphemeral(true).queue();
            } else {
                event.reply("It seems you are not a UPA member. Would you like to become one?").addActionRow(
                        linkButton()).setEphemeral(true).queue();
            }
            return;
        }
        int propertiesOwned = databaseCaching.getMemberProperties().get(upaMember.getKey()).size();
        String notification = null;

        if (!admin && event.getMember().getRoles().contains(lotteryRole) && !ctx.lottery().hasTicket(memberId)) {
            UpaLottery lottery = ctx.variables().lottery().get();
            notification = "A lottery with a total pot of **" + lottery.getPac().get() + " PAC** is ongoing, buy a ticket @ <#993201967096660069>!\n";
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
        for (var next : ctx.discord().getStatisticsCommand().getStatisticsData().getTopOwners().entrySet()) {
            if (memberId == next.getKey()) {
                break;
            }
            rank++;
        }
        sb.append("Ownership ranking #").append(rank).append("\n\n\n");
        sb.append("! ~ Spark train ~ !").append('\n');
        sb.append("Hollis passenger #").append(upaMember.getHollisSparkTrainPlace().get()).append(" with ").append(DiscordService.DECIMAL_FORMAT.format(upaMember.getTotalSsh(false))).append(" SSH").append("\n");
        sb.append("Global passenger #").append(upaMember.getGlobalSparkTrainPlace().get()).append(" with ").append(DiscordService.DECIMAL_FORMAT.format(upaMember.getTotalSsh(true))).append(" SSH").append("\n\n\n");
        sb.append("```");
        SelectMenu.Builder selectMenu = SelectMenu.create("account_select_menu");
        selectMenu.addOption("Become a VIP", "account_become_a_vip", Emoji.fromUnicode("U+2B50")).
                addOption("Request PAC History", "account_request_pac_history", Emoji.fromUnicode("U+1F4DC"));
        event.reply(sb.toString()).setComponents(ActionRow.of(selectMenu.build())).setEphemeral(true).queue();
    }
}
