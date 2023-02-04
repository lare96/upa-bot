package me.upa.discord.event;

import com.google.common.collect.Multiset.Entry;
import me.upa.UpaBotContext;
import me.upa.discord.DiscordService;
import me.upa.discord.UpaMember;
import me.upa.discord.event.impl.BonusSshEventHandler;
import me.upa.discord.event.impl.BonusSshEventHandler.BonusSsh;
import me.upa.discord.event.impl.FreePacEventHandler;
import me.upa.discord.event.trivia.TriviaEventHandler;
import me.upa.discord.event.trivia.TriviaQuestion;
import me.upa.discord.listener.credit.CreditTransaction;
import me.upa.discord.listener.credit.CreditTransaction.CreditTransactionType;
import me.upa.service.DailyResetMicroService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Set;

public final class UpaEventDiscordListener extends ListenerAdapter {

    private final UpaBotContext ctx;

    public UpaEventDiscordListener(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null)
            return;
        switch (event.getButton().getId()) {
            case "trivia_past_winners":
                UpaEvent.forEvent(ctx, TriviaEventHandler.class, handler -> {
                    Set<Entry<Long>> pastWinners = handler.getWinners().entrySet();
                    MessageCreateBuilder mb = new MessageCreateBuilder();
                    for (var entry : pastWinners) {
                        long memberId = entry.getElement();
                        int pacWon = entry.getCount();
                        mb.addContent("<@" + memberId + ">").addContent(" **").
                                addContent(String.valueOf(pacWon)).addContent(" PAC**\n");
                    }
                    event.reply(mb.build()).setEphemeral(true).queue();
                }, () -> event.reply("Event invalid.").setEphemeral(true).queue());
                break;
            case "free_pac":
                UpaMember upaMember = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
                if (upaMember == null) {
                    event.reply("You must be a UPA member to claim your free PAC.").setEphemeral(true).queue();
                    return;
                }
                if (!upaMember.getActive().get()) {
                    event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
                    return;
                }
                UpaEvent.forEvent(ctx, FreePacEventHandler.class, handler -> handler.givePac(ctx, event, upaMember),
                        () -> event.reply("Event invalid.").setEphemeral(true).queue());
                break;
            case "claim_list":
                UpaEvent.forEvent(ctx, FreePacEventHandler.class, handler -> {
                            MessageCreateBuilder cb = new MessageCreateBuilder();
                            for (var entry : handler.getClaimed().entrySet()) {
                                cb.addContent("<@").addContent(String.valueOf(entry.getKey())).
                                        addContent("> **").addContent(String.valueOf(entry.getValue())).addContent(" PAC**\n");
                            }
                            event.reply(cb.build()).setEphemeral(true).queue();
                        },
                        () -> event.reply("Event invalid.").setEphemeral(true).queue());
                break;
            case "view_bonus_ssh":
                UpaEvent.forEvent(ctx, BonusSshEventHandler.class, handler -> {
                            BonusSsh bonusSsh = handler.getTotalSshGained().get(event.getMember().getIdLong());
                            if (bonusSsh == null) {
                                event.reply("You haven't accrued any bonus SSH from this event yet. Next payout in " + DailyResetMicroService.checkIn(ctx) + ".").setEphemeral(true).queue();
                            } else {
                                event.replyEmbeds(new EmbedBuilder().setDescription("Here are your current earnings for this event.").setColor(Color.GREEN).
                                        addField("Hollis bonus SSH", DiscordService.DECIMAL_FORMAT.format(bonusSsh.getHollis()), false).
                                        addField("Global bonus SSH", DiscordService.DECIMAL_FORMAT.format(bonusSsh.getGlobal()), false).
                                                addField("Next payout in", DailyResetMicroService.checkIn(ctx), false).build()).setEphemeral(true).queue();
                            }
                        },
                        () -> event.reply("Event invalid.").setEphemeral(true).queue());
                break;
        }
    }

    @Override
    public void onSelectMenuInteraction(@NotNull SelectMenuInteractionEvent event) {
        long memberId = event.getMember().getIdLong();
        UpaMember upaMember = ctx.databaseCaching().getMembers().get(memberId);
        if (upaMember == null) {
            event.reply("You must be a member to do this.").setEphemeral(true).queue();
            return;
        }
        if (!upaMember.getActive().get()) {
            event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
            return;
        }
        switch (event.getSelectMenu().getId()) {
            case "trivia_question":
                String selectedAnswer = event.getSelectedOptions().get(0).getValue();
                UpaEvent.forEvent(ctx, TriviaEventHandler.class, handler -> {
                    if (event.getMessage().getIdLong() != handler.getMessageId()) {
                        event.reply("This question has expired.").setEphemeral(true).queue();
                        return;
                    }
                    TriviaQuestion question = handler.getCurrentQuestion();
                    if (question == null) {
                        event.reply("No question active at the moment.").setEphemeral(true).queue();
                        return;
                    }
                    if (question.isLocked()) {
                        event.reply("This question has already been answered.").setEphemeral(true).queue();
                        return;
                    }
                    if (!handler.getAttempts().add(memberId)) {
                        event.reply("You only get one attempt at answering each question.").setEphemeral(true).queue();
                        return;
                    }
                    if (question.getCorrectAnswer().equals(selectedAnswer)) {
                        int pacAmount = TriviaEventHandler.getReward(question.getDifficulty());
                        event.reply("You chose the correct answer! **" + pacAmount + " PAC** has been credited to your account.").setEphemeral(true).queue();
                        handler.getWinners().add(memberId, pacAmount);
                        question.setLocked(true);
                        handler.answerQuestion(ctx, memberId);
                        ctx.variables().triviaRepository().accessValue(repo -> {
                            repo.markAnswered(question);
                            return true;
                        });
                        ctx.discord().sendCredit(new CreditTransaction(upaMember, pacAmount, CreditTransactionType.EVENT, "trivia"));
                    } else {
                        event.reply("You selected an incorrect answer. Better luck next time!").setEphemeral(true).queue();
                    }
                    ctx.variables().event().save();
                });
                break;
        }
    }
}
