package me.upa.discord.event.trivia;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import me.upa.UpaBotContext;
import me.upa.discord.event.UpaEventHandler;
import me.upa.discord.event.UpaEventRarity;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TriviaEventHandler extends UpaEventHandler {

    private static final int TICKS = 6;

    private static final long serialVersionUID = -8177293051073662966L;
    private final Set<Long> attempts = new HashSet<>();
    private final Multiset<Long> winners = HashMultiset.create();
    private volatile long messageId;
    private volatile TriviaQuestion currentQuestion;

    private int timer;

    private long lastAnsweredBy = 540938577572397059L;
    public static int getReward(String difficulty) {
        switch (difficulty) {
            case "easy":
                return 25;
            case "medium":
                return 50;
            case "hard":
                return 100;
        }
        return 0;
    }

    @Override
    public void onStart(UpaBotContext ctx) {
        TriviaRepository repository = ctx.variables().triviaRepository().get();
        currentQuestion = repository.getNextQuestion();
        messageId = ctx.discord().bot().getTextChannelById(1001694147490619453L).
                sendMessage(currentQuestion.toMessage()).
                complete().getIdLong();
    }

    @Override
    public void onEnd(UpaBotContext ctx) {
        if (currentQuestion != null && messageId != 0) {
            ctx.discord().bot().getTextChannelById(1001694147490619453L).deleteMessageById(messageId).queue();
        }
    }

    @Override
    public void onLoop(UpaBotContext ctx) {
        super.onLoop(ctx);
        if (currentQuestion != null) {
            if (++timer >= TICKS && currentQuestion.isLocked()) {
                sendNewQuestion(ctx);
                timer = 0;
            } else if(messageId != 0) {
                ctx.discord().bot().getTextChannelById(1001694147490619453L).
                        editMessageById(messageId, MessageEditData.fromCreateData(currentQuestion.toMessage(lastAnsweredBy, computeEndAt()))).queue();
            }
        }
    }

    @Override
    public UpaEventRarity rarity() {
        return UpaEventRarity.COMMON;
    }

    @Override
    public String name() {
        return "UPA Trivia";
    }

    @Override
    public int durationDays() {
        return 4;
    }

    @Override
    public StringBuilder buildMessageContent() {
        return new StringBuilder().append("Pick the correct answer and earn some extra PAC! Trivia questions will appear below this message.\n\n").
                append("The first person to pick the correct answer will win the PAC prize, picking the wrong answer will lock you out of answering the question again. Resets every 30 minutes.");
    }

    @Override
    public List<ActionRow> buildMessageComponents() {
        return List.of(ActionRow.of(
                Button.of(ButtonStyle.PRIMARY, "trivia_past_winners", "View past winners", Emoji.fromUnicode("U+1F91D"))
        ));
    }

    public void sendNewQuestion(UpaBotContext ctx) {
        TriviaRepository repository = ctx.variables().triviaRepository().get();
        currentQuestion = repository.getNextQuestion();
        ctx.discord().bot().getTextChannelById(1001694147490619453L).
                editMessageById(messageId, MessageEditData.fromCreateData(currentQuestion.toMessage())).
                complete();
        attempts.clear();
    }

    public void answerQuestion(UpaBotContext ctx, long answeredBy) {
        lastAnsweredBy = answeredBy;
        ctx.discord().bot().getTextChannelById(1001694147490619453L).editMessageById(messageId, MessageEditData.fromCreateData(currentQuestion.toMessage(answeredBy, computeEndAt()))).queue();
    }

    private Instant computeEndAt() {
        Instant now = Instant.now();
        long seconds = ((TICKS - timer) * 5L) * 60L;
        if(seconds < 1) {
            return now;
        }
        return now.plusSeconds(seconds);
    }
    public void forceNewQuestion(UpaBotContext ctx) {
        sendNewQuestion(ctx);
        timer = 0;
    }

    public TriviaQuestion getCurrentQuestion() {
        return currentQuestion;
    }

    public Multiset<Long> getWinners() {
        return winners;
    }

    public Set<Long> getAttempts() {
        return attempts;
    }

    public long getMessageId() {
        return messageId;
    }

}
