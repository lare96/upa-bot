package me.upa.discord.listener.buttons;

import com.google.common.util.concurrent.Uninterruptibles;
import me.upa.UpaBotContext;
import me.upa.discord.UpaMember;
import me.upa.discord.listener.credit.CreditTransaction;
import me.upa.discord.listener.credit.CreditTransaction.CreditTransactionType;
import me.upa.service.DailyResetMicroService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public final class CasinoButtonListener extends ListenerAdapter {
    private static final Emoji SLOT_MACHINE_EMOJI = Emoji.fromUnicode("U+1F3B0");

    private final UpaBotContext ctx;

    public CasinoButtonListener(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null)
            return;
        if (event.getButton().getId().startsWith("wager_")) {
            UpaMember upaMember = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
            if (upaMember == null) {
                event.reply("You need to be a registered UPA member to participate in the casino.").setEphemeral(true).queue();
                return;
            }
            if(!upaMember.getActive().get()) {
                event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
                return;
            }
            if (ctx.variables().slotMachine().get().getPlayedToday().contains(upaMember.getMemberId())) {
                event.reply("You can only play the slots once per day. Please wait " + DailyResetMicroService.checkIn(ctx) + ".").setEphemeral(true).queue();
                return;
            }

            int wagerAmount = Integer.parseInt(event.getButton().getId().replace("wager_", ""));
            if (upaMember.getCredit().get() < wagerAmount) {
                event.reply("You don't have enough PAC to wager this amount.").setEphemeral(true).queue();
                return;
            }
                event.deferReply(true).queue();
                int baseChance = 30;
                double reduceRate = 0.005;
                int percentReduce = baseChance- ((int) (wagerAmount * reduceRate));
                if (percentReduce > baseChance) {
                    percentReduce = baseChance;
                } else if (percentReduce < 5) {
                    percentReduce = 5;
                }
                boolean won = ThreadLocalRandom.current().nextInt(100) < percentReduce;
                String imageUrl = won ? "https://i.imgur.com/KMJH8yi.png" : "https://i.imgur.com/tcPHHjn.jpeg";
                String message = won ? "Congratulations! You have won **" + wagerAmount + " PAC** at the slots!" : "Oof, you've lost **" + wagerAmount + " PAC** at the slots. Better luck next time.";
                ctx.discord().execute(() -> {
                        Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(ThreadLocalRandom.current().nextInt(3, 7)));
                        event.getHook().editOriginal(new MessageEditBuilder().setEmbeds(new EmbedBuilder().setTitle(SLOT_MACHINE_EMOJI.getFormatted()).setImage(imageUrl).setDescription(message).setColor(Color.YELLOW).build()).build()).queue();
                        if (won) {
                            ctx.discord().sendCredit(new CreditTransaction(upaMember, wagerAmount, CreditTransactionType.SLOTS));
                            ctx.variables().slotMachine().accessValue(slotMachine -> {
                                slotMachine.setLastWinner(upaMember.getMemberId());
                                slotMachine.setLastWinAmount(wagerAmount);
                                slotMachine.getAllTimePacWon().add(upaMember.getMemberId(), wagerAmount);
                                slotMachine.getPlayedToday().add(upaMember.getMemberId());
                                return true;
                            });
                        } else {
                            ctx.discord().sendCredit(new CreditTransaction(upaMember, -wagerAmount, CreditTransactionType.SLOTS));
                            ctx.variables().slotMachine().accessValue(slotMachine -> {
                                slotMachine.setLastLoser(upaMember.getMemberId());
                                slotMachine.setLastLoseAmount(wagerAmount);
                                slotMachine.getTodaysLosses().addAndGet(wagerAmount);
                                slotMachine.getPlayedToday().add(upaMember.getMemberId());
                                return true;
                            });
                        }
                });
            return;
        }
        switch (event.getButton().getId()) {
            case "lottery":
                ctx.lottery().sendMessage(event);
                break;
            case "slot_machine":
                ctx.variables().slotMachine().get().open(event);
                break;
            case "bets":
                event.reply("Coming soon!").setEphemeral(true).queue();
                break;
        }
    }
}
