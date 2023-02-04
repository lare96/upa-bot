package me.upa.discord.event.trivia;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import me.upa.service.DailyResetMicroService;
import me.upa.util.Util;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.awt.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TriviaQuestion implements Serializable {
    private static final long serialVersionUID = 741775192962239882L;
    private final String category;
    private final String difficulty;
    private final String question;
    private final String correctAnswer;
    private final ImmutableList<String> incorrectAnswers;

    private transient volatile boolean locked;

    public TriviaQuestion(String category, String difficulty, String question, String correctAnswer, ImmutableList<String> incorrectAnswers) {
        this.category = category;
        this.difficulty = difficulty;
        this.question = question;
        this.correctAnswer = correctAnswer;
        this.incorrectAnswers = incorrectAnswers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TriviaQuestion that = (TriviaQuestion) o;
        return Objects.equal(question, that.question);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(question);
    }

    public MessageCreateData toMessage(long answeredBy, Instant endAt) {
        EmbedBuilder eb = new EmbedBuilder().setDescription(question).
                setColor(locked ? Color.RED : Color.GREEN).
                addField("Category", category, false).
                addField("Difficulty", Util.capitalize(difficulty) + " (" + TriviaEventHandler.getReward(difficulty) + " PAC)", false);

        if (locked) {
            eb.addField("Answered by", "<@" + answeredBy + ">", false).
                    addField("Correct answer", correctAnswer, false).
                    addField("Next in", Util.computeDuration(endAt), false);
        }
        SelectMenu.Builder sm = SelectMenu.create("trivia_question");
        if (!locked) {
            List<String> answers = new ArrayList<>(incorrectAnswers);
            answers.add(correctAnswer);
            Collections.shuffle(answers);
            for (String s : answers) {
                sm.addOption(s, s);
            }
        }
        MessageCreateBuilder builder = new MessageCreateBuilder();
        builder.setEmbeds(eb.build());
        if (locked) {
            builder.setComponents();
        } else {
            builder.setComponents(ActionRow.of(sm.build()));
        }
        return builder.build();
    }

    public MessageCreateData toMessage() {
        return toMessage(0, null);
    }

    public String getCategory() {
        return category;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public String getQuestion() {
        return question;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public ImmutableList<String> getIncorrectAnswers() {
        return incorrectAnswers;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}