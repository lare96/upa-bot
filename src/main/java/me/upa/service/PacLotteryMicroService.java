package me.upa.service;

import me.upa.UpaBot;
import me.upa.discord.CreditTransaction;
import me.upa.discord.CreditTransaction.CreditTransactionType;
import me.upa.discord.DiscordService;
import me.upa.discord.UpaLottery;
import me.upa.discord.UpaMember;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

import javax.print.DocFlavor.READER;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class PacLotteryMicroService extends MicroService {

    /**
     * Creates a new {@link PacLotteryMicroService}.
     */
    public PacLotteryMicroService() {
        super(Duration.ofHours(1));
    }

    @Override
    public void startUp() throws Exception {
        UpaBot.variables().lottery().access(lottery ->
                lottery.compareAndSet(null, new UpaLottery()));
        sendMessage();
    }

    @Override
    public void run() throws Exception {
        UpaBot.variables().lottery().accessValue(currentLottery -> {
            // if only one person delay one more week
            if (Instant.now().isAfter(currentLottery.getFinishedAt().get())) {
                int jackpot = currentLottery.getPac().get();
                long winnerId = currentLottery.draw();
                if (jackpot == 0 || winnerId == -1) {
                    return false;
                }
                UpaMember upaMember = UpaBot.getDatabaseCachingService().getMembers().get(winnerId);
                if (upaMember == null) {
                    currentLottery.getContestants().remove(winnerId);
                    return false;
                }
                UpaBot.getDiscordService().guild().getTextChannelById(963112034726195210L).sendMessage("Congratulations to <@"+winnerId+"> for winning the lottery!").queue();
                UpaBot.getDiscordService().sendCredit(new CreditTransaction(upaMember, jackpot, CreditTransactionType.LOTTERY));
                currentLottery.getContestants().clear();
                currentLottery.getLastWinner().set(winnerId);
                currentLottery.getPac().set(0);
                currentLottery.getFinishedAt().set(Instant.now().plus(7, ChronoUnit.DAYS));
                sendMessage();
                return true;
            }
            return false;
        });
    }

    public boolean hasTicket(long memberId) {
        UpaLottery currentLottery = UpaBot.variables().lottery().getValue();
        return currentLottery != null && currentLottery.getContestants().contains(memberId);
    }


    public void addTicket(long memberId) {
        UpaBot.variables().lottery().accessValue(currentLottery -> {
            if (currentLottery != null && currentLottery.getContestants().add(memberId)) {
                currentLottery.getPac().addAndGet(250);
                Role role = UpaBot.getDiscordService().guild().getRoleById(983750455429570620L);
                UpaBot.getDiscordService().guild().addRoleToMember(UserSnowflake.fromId(memberId), role).queue();
                sendMessage();
                return true;
            }
            return false;
        });
    }

    public void sendMessage() {
        UpaBot.getDiscordService().guild().getTextChannelById(993201967096660069L).retrieveMessageById(993202507188797550L).complete().editMessage(buildMessage()).queue();
    }

    private Message buildMessage() {
        UpaLottery currentLottery = UpaBot.variables().lottery().getValue();
        long endDays = Instant.now().until(currentLottery.getFinishedAt().get(), ChronoUnit.DAYS);
        String endsIn = endDays == 0 ? "Less than 24h!" : endDays == 1 ? "1 day" : endDays + " days";
        long lastWinnerId = currentLottery.getLastWinner().get();
        String lastWinner = lastWinnerId == 0 ? "No one!" : "<@" + lastWinnerId + ">";
        return new MessageBuilder().setContent("\n").setEmbeds(new EmbedBuilder().
                        setTitle("UPA Lottery").
                        setDescription("Buy a ticket using the button below for a chance to win the jackpot.").
                        addField("Last winner", lastWinner, false).
                        addField("Total jackpot", DiscordService.COMMA_FORMAT.format(currentLottery.getPac().get()) + " PAC", false).
                        addField("Ends in", endsIn, false).
                        setColor(Color.YELLOW).
                        setImage("https://i.imgur.com/KMJH8yi.pnghttps://i.imgur.com/KMJH8yi.png").build()).
                setActionRows(ActionRow.of(Button.of(ButtonStyle.PRIMARY, "buy_ticket", "Buy ticket (250 PAC)", Emoji.fromUnicode("U+1F3AB")),
                       // Button.of(ButtonStyle.PRIMARY, "try_the_slots", "View participants", Emoji.fromUnicode("U+1F4CB")),
                        Button.of(ButtonStyle.PRIMARY, "view_participants", "View participants", Emoji.fromUnicode("U+1F4CB")))).build();
    }
}
