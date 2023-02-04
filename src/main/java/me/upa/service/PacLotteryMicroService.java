package me.upa.service;

import me.upa.UpaBotContext;
import me.upa.discord.DiscordService;
import me.upa.discord.UpaLottery;
import me.upa.discord.UpaMember;
import me.upa.discord.event.UpaEvent;
import me.upa.discord.event.impl.FreeLotteryEventHandler;
import me.upa.discord.listener.credit.CreditTransaction;
import me.upa.discord.listener.credit.CreditTransaction.CreditTransactionType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class PacLotteryMicroService extends MicroService {

    /**
     * The context.
     */
    private final UpaBotContext ctx;

    /**
     * Creates a new {@link PacLotteryMicroService}.
     */
    public PacLotteryMicroService(UpaBotContext ctx) {
        super(Duration.ofHours(1));
        this.ctx = ctx;
    }

    @Override
    public void startUp() throws Exception {
        ctx.variables().lottery().access(lottery ->
                lottery.compareAndSet(null, new UpaLottery()));
    }

    //private static final Emoji
    @Override
    public void run() throws Exception {
        ctx.variables().lottery().accessValue(currentLottery -> {
            // if only one person delay one more week
            if (Instant.now().isAfter(currentLottery.getFinishedAt().get())) {
                int jackpot = currentLottery.getPac().get();
                long winnerId = currentLottery.draw();
                if (jackpot == 0 || winnerId == -1) {
                    return false;
                }
                UpaMember upaMember = ctx.databaseCaching().getMembers().get(winnerId);
                if (upaMember == null || !upaMember.getActive().get()) {
                    currentLottery.getContestants().remove(winnerId);
                    return false;
                }
                StringBuilder sb = new StringBuilder();
                currentLottery.getContestants().forEach(next -> sb.append("<@").append(next).append("> "));
                ctx.discord().guild().getTextChannelById(956790034097373204L).sendMessageEmbeds(new EmbedBuilder().
                        setDescription("Congratulations to <@" + winnerId + "> for winning the lottery! Buy your tickets for the next one at <#993201967096660069>").
                        addField("Pot", "**" + jackpot + " PAC**", false).
                        addField("Contestants", sb.toString(), false).
                        setColor(Color.YELLOW).build()).queue();
                ctx.discord().sendCredit(new CreditTransaction(upaMember, jackpot, CreditTransactionType.LOTTERY));
                currentLottery.getContestants().clear();
                currentLottery.getLastWinner().set(winnerId);
                currentLottery.getPac().set(0);
                currentLottery.getFinishedAt().set(Instant.now().plus(7, ChronoUnit.DAYS));
                return true;
            }
            return false;
        });
    }

    public boolean hasTicket(long memberId) {
        UpaLottery currentLottery = ctx.variables().lottery().get();
        return currentLottery != null && currentLottery.getContestants().contains(memberId);
    }


    public void addTicket(long memberId, int fee) {
        ctx.variables().lottery().accessValue(currentLottery -> {
            if (currentLottery != null && currentLottery.getContestants().add(memberId)) {
                currentLottery.getPac().addAndGet((int) Math.floor(fee * 0.75));
                Role role = ctx.discord().guild().getRoleById(983750455429570620L);
                ctx.discord().guild().addRoleToMember(UserSnowflake.fromId(memberId), role).queue();
                return true;
            }
            return false;
        });
    }

    public void sendMessage(IReplyCallback callback) {
        callback.reply(buildMessage()).setEphemeral(true).queue();
    }

    private MessageCreateData buildMessage() {
        UpaLottery currentLottery = ctx.variables().lottery().get();
        long endDays = Instant.now().until(currentLottery.getFinishedAt().get(), ChronoUnit.DAYS);
        String endsIn = endDays == 0 ? "Less than 24h!" : endDays == 1 ? "1 day" : endDays + " days";
        long lastWinnerId = currentLottery.getLastWinner().get();
        String lastWinner = lastWinnerId == 0 ? "No one!" : "<@" + lastWinnerId + ">";
        String ticketPrice = UpaEvent.isActive(ctx, FreeLotteryEventHandler.class) ? "Buy ticket (FREE)" : "Buy ticket (250 PAC)";
        return new MessageCreateBuilder().setContent("\n").setEmbeds(new EmbedBuilder().
                        setTitle("UPA Lottery").
                        setDescription("Buy a ticket using the button below for a chance to win the jackpot.").
                        addField("Last winner", lastWinner, false).
                        addField("Total jackpot", DiscordService.COMMA_FORMAT.format(currentLottery.getPac().get()) + " PAC", false).
                        addField("Ends in", endsIn, false).
                        setColor(Color.YELLOW).
                        setImage("https://i.imgur.com/KMJH8yi.png").build()).
                setComponents(ActionRow.of(Button.of(ButtonStyle.PRIMARY, "buy_ticket", ticketPrice, Emoji.fromUnicode("U+1F3AB")),
                        // Button.of(ButtonStyle.PRIMARY, "try_the_slots", "View participants", Emoji.fromUnicode("U+1F4CB")),
                        Button.of(ButtonStyle.PRIMARY, "view_participants", "View participants", Emoji.fromUnicode("U+1F4CB")))).build();
    }
}
