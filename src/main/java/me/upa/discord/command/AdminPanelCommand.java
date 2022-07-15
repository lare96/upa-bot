package me.upa.discord.command;

import com.google.common.primitives.Ints;
import me.upa.UpaBot;
import me.upa.discord.Scholar;
import me.upa.discord.UpaMember;
import me.upa.service.DatabaseCachingService;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;

public final class AdminPanelCommand extends ListenerAdapter {

    private static final Emoji SLOT_MACHINE_EMOJI = Emoji.fromUnicode("U+1F3B0");
    private static final Emoji WHITE_SQUARE_EMOJI = Emoji.fromUnicode("U+25FB");
    private static final Emoji BLUE_SQUARE_EMOJI = Emoji.fromUnicode("U+1F7E6");

    private static final Logger logger = LogManager.getLogger();

    // 0 cubes = lose all of your reward
    private static final class SortedUpaMember implements Comparable<SortedUpaMember> {

        private final String discordName;
        private final int credit;

        private SortedUpaMember(String discordName, int credit) {
            this.discordName = discordName;
            this.credit = credit;
        }

        @Override
        public int compareTo(@NotNull SortedUpaMember o) {
            return Integer.compare(o.credit, credit);
        }
    }

    private static final class SponsorScholarTask extends SqlTask<Void> {

        private final long memberId;

        private SponsorScholarTask(long memberId) {
            this.memberId = memberId;
        }

        @Override
        public Void execute(Connection connection) throws Exception {
            try (PreparedStatement updateScholar = connection.prepareStatement("UPDATE scholars SET sponsored = 1 WHERE discord_id = ?;")) {
                updateScholar.setLong(1, memberId);
                if (updateScholar.executeUpdate() < 1) {
                    logger.warn("Scholar could not be sponsored.");
                    return null;
                }
            }
            return null;
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getSubcommandName() != null && event.getSubcommandName().equals("panel")) {
            event.reply("Please select an action.").setEphemeral(true).addActionRows(
                    ActionRow.of(Button.of(ButtonStyle.PRIMARY, "award_scholar", "Award scholar", Emoji.fromUnicode("U+1F393")),
                            Button.of(ButtonStyle.PRIMARY, "credit_balance", "View credit balances", Emoji.fromUnicode("U+1F4B3")),
                            Button.of(ButtonStyle.PRIMARY, "change_store_values", "Change store values", Emoji.fromUnicode("U+1F4D2")),
                            Button.of(ButtonStyle.PRIMARY, "update_commands", "Update commands", Emoji.fromUnicode("U+2B07")),
                            Button.of(ButtonStyle.PRIMARY, "set_lottery_jackpot", "Set lottery jackpot amount", Emoji.fromUnicode("U+1F3B0"))),
                    ActionRow.of(Button.of(ButtonStyle.PRIMARY, "full_spark_train", "Full spark train", Emoji.fromUnicode("U+1F688")),
                            Button.of(ButtonStyle.PRIMARY, "create_send_storm", "Create send storm", Emoji.fromUnicode("U+1F32A")),
                            Button.of(ButtonStyle.DANGER, "test_feature", "Test feature", Emoji.fromUnicode("U+1F9EA")))
            ).queue();
        }
    }

   // private final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
    private void gamble(ButtonInteractionEvent event, int amount) {
        boolean won = ThreadLocalRandom.current().nextInt(100) < 35;
        String imageUrl = won ? "https://i.imgur.com/KMJH8yi.pnghttps://i.imgur.com/KMJH8yi.png" : "https://i.imgur.com/tcPHHjn.jpeg";
        String message = won ? "Congratulations! You have won **"+ (amount * 2) +" PAC** at the slots!" : "Oof, you've lost **"+amount+" PAC** at the slots. Better luck next time.";

        event.getInteraction().editMessage(new MessageBuilder().setEmbeds(new EmbedBuilder().setTitle(SLOT_MACHINE_EMOJI.getAsMention()).setImage(imageUrl).setDescription(message).setColor(Color.YELLOW).build()).
                setActionRows(ActionRow.of(Button.of(ButtonStyle.SUCCESS, "try_again", "Try again", Emoji.fromUnicode("U+2705")))).build()).queue();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null)
            return;
        switch (event.getButton().getId()) {
            case "test_feature":
                event.reply("How much PAC would you like to wager? (PAC will not be taken while in TEST mode)\n\n"+
                        BLUE_SQUARE_EMOJI.getAsMention()+""+BLUE_SQUARE_EMOJI.getAsMention()+""+BLUE_SQUARE_EMOJI.getAsMention()+" = Win double your PAC!\nAnything else=").addActionRow(
                        Button.of(ButtonStyle.PRIMARY, "wager_100", "Wager 100 PAC", Emoji.fromUnicode("U+1F4B5")),
                        Button.of(ButtonStyle.PRIMARY, "wager_250", "Wager 250 PAC", Emoji.fromUnicode("U+1F4B0")),
                        Button.of(ButtonStyle.PRIMARY, "wager_500", "Wager 500 PAC", Emoji.fromUnicode("U+1F911"))
                ).setEphemeral(true).queue();
                break;
            case "wager_100":
                gamble(event, 100);
                break;
            case "wager_250":
                gamble(event, 250);
                break;
            case "wager_500":
                gamble(event, 500);
                break;
        }
        switch (event.getButton().getId()) {
            case "create_send_storm":
                UpaBot.getDiscordService().guild().getTextChannelById(982800525386993694L).sendMessage("").queue();
                break;
            case "update_commands":
                event.reply("Done. Please give up to 10 minutes for commands to synchronize.").setEphemeral(true).queue();
                UpaBot.getDiscordService().updateCommands();
                break;
            case "confirm_award_scholar":
                event.reply("Done.").setEphemeral(true).queue();
                handleAwardScholar(event);
                break;
            case "award_scholar":
                event.reply("Are you sure you want to award the scholar?").setEphemeral(true).addActionRow(
                        Button.of(ButtonStyle.SUCCESS, "confirm_award_scholar", "Yes!", Emoji.fromUnicode("U+2705"))
                ).queue();
                break;
            case "credit_balance":
                event.deferReply().setEphemeral(true).queue();
                var memberList = UpaBot.getDatabaseCachingService().getMembers().values();
                List<SortedUpaMember> sortedUpaMembers = new ArrayList<>(memberList.size());
                for (UpaMember upaMember : memberList) {
                    int credit = upaMember.getCredit().get();
                    if (credit > 0) {
                        sortedUpaMembers.add(new SortedUpaMember(upaMember.getDiscordName().get(), credit));
                    }
                }
                Collections.sort(sortedUpaMembers);
                StringBuilder sb = new StringBuilder("```\n");
                for (SortedUpaMember member : sortedUpaMembers) {
                    sb.append('@').append(member.discordName).append(" (").append(member.credit).append(" credit)").append("\n\n");
                }
                sb.append("```");
                event.getHook().setEphemeral(true).sendMessage(sb.toString()).queue();
                break;
            case "set_lottery_jackpot":
                if (UpaBot.variables().lottery().getValue() == null) {
                    event.reply("No lottery active atm.").setEphemeral(true).queue();
                    break;
                }
                event.replyModal(Modal.create("set_lottery_jackpot_form", "Set the jackpot amount.").
                        addActionRow(TextInput.create("set_lottery_jackpot_form_amount", "Amount", TextInputStyle.SHORT).setRequired(true).setPlaceholder("500").build()).build()).queue();
                break;
            case "full_spark_train":
                String lastTrain = UpaBot.getDiscordService().getSparkTrain().getLastSparkTrain();
                if (lastTrain == null) {
                    event.reply("No train string generated?").setEphemeral(true).queue();
                    return;
                }
                event.reply(new MessageBuilder().appendCodeBlock(lastTrain, "").build()).setEphemeral(true).queue();
                break;
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getModalId().equals("set_lottery_jackpot_form")) {
            Integer amount = Ints.tryParse(event.getValue("set_lottery_jackpot_form_amount").getAsString());
            if (amount == null) {
                event.reply("Incorrect amount entered.").setEphemeral(true).queue();
                return;
            }
            UpaBot.variables().lottery().accessValue(currentLottery -> {
                currentLottery.getPac().set(amount);
                UpaBot.getPacLotteryMicroService().sendMessage();
                event.reply("Success!").setEphemeral(true).queue();
                return true;
            });
        }
    }

    private void handleAwardScholar(ButtonInteractionEvent event) {
        DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
        event.deferReply().setEphemeral(true).queue();
        Collection<Scholar> scholars = databaseCaching.getScholars().values();
        Set<Scholar> eligible = new HashSet<>();
        for (Scholar next : scholars) {
            int netWorth = next.getNetWorth().get();
            if (!next.getSponsored().get() && netWorth > 5000 && netWorth < 10_000) {
                eligible.add(next);
            }
        }
        int random = ThreadLocalRandom.current().nextInt(eligible.size());
        int counter = 0;
        Scholar awarded = null;
        synchronized (databaseCaching.getScholars()) {
            Iterator<Scholar> it = eligible.iterator();
            while (it.hasNext()) {
                Scholar next = it.next();
                if (counter++ == random) {
                    awarded = next;
                    it.remove();
                    break;
                }
            }
        }
        if (awarded == null) {
            event.getHook().editOriginal("No scholar could be found.").queue();
            return;
        }

        long memberId = awarded.getMemberId();

        SqlConnectionManager.getInstance().execute(new SponsorScholarTask(memberId),
                success -> {
                    Guild guild = UpaBot.getDiscordService().guild();
                    guild.getTextChannelById(975506360231948288L).
                            sendMessage("The scholarship program for this month has concluded. The sponsored scholar is <@" + memberId + ">, congratulations! All linked members will receive bonus PAC for sending to the sponsor.").queue();
                    event.getHook().editOriginal("Success!").queue();
                    Scholar scholar = databaseCaching.getScholars().get(memberId);
                    scholar.getSponsored().set(true);
                },
                failure -> {
                    event.getHook().editOriginal("Error adding scholar " + memberId + " to database. Please check console.").queue();
                    logger.warn(new ParameterizedMessage("Error adding scholar {} to database.", memberId),  failure);
                });
    }

}
