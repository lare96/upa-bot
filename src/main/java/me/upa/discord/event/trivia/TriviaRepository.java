package me.upa.discord.event.trivia;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.discord.DiscordService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static me.upa.fetcher.ApiDataFetcher.GSON;

public final class TriviaRepository implements Serializable {

    private static final Logger logger = LogManager.getLogger();
    private static final long serialVersionUID = 7835826768600356365L;
    private final Set<TriviaQuestion> questions = Sets.newConcurrentHashSet();
    private final Set<TriviaQuestion> answeredQuestions = Sets.newConcurrentHashSet();

    public boolean load() {
        try {
            if (questions.isEmpty()) {
                Path triviaDb = Paths.get("data", "trivia.json");
                JsonArray jsonArray = GSON.fromJson(Files.readString(triviaDb), JsonArray.class);
                for (JsonElement element : jsonArray) {
                    JsonObject object = element.getAsJsonObject();
                    int responseCode = object.get("response_code").getAsInt();
                    if (responseCode != 0) {
                        throw new RuntimeException("Invalid response code!");
                    }
                    JsonArray results = object.get("results").getAsJsonArray();
                    for (JsonElement nextResult : results) {
                        JsonObject resultObject = nextResult.getAsJsonObject();
                        String category = resultObject.get("category").getAsString();
                        String difficulty = resultObject.get("difficulty").getAsString();
                        String question = resultObject.get("question").getAsString();
                        String correctAnswer = resultObject.get("correct_answer").getAsString();
                        String[] incorrectAnswers = GSON.fromJson(resultObject.get("incorrect_answers"), String[].class);
                        category = new URI(category).getPath();
                        difficulty = new URI(difficulty).getPath();
                        question = new URI(question).getPath();
                        correctAnswer = new URI(correctAnswer).getPath();
                        ImmutableList<String> incorrectAnswerList = Arrays.stream(incorrectAnswers).map(str -> {
                            try {
                                return new URI(str).getPath();
                            } catch (URISyntaxException e) {
                                throw new RuntimeException(e);
                            }
                        }).collect(ImmutableList.toImmutableList());
                        questions.add(new TriviaQuestion(category, difficulty, question, correctAnswer, incorrectAnswerList));
                    }
                }
                GSON.toJson(jsonArray, Files.newBufferedWriter(triviaDb, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
                return true;
            }
            logger.info("Loaded {} trivia questions", questions.size());
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public TriviaQuestion getNextQuestion() {
        List<TriviaQuestion> possible = new ArrayList<>(questions);
        possible.removeAll(answeredQuestions);
        return possible.get(ThreadLocalRandom.current().nextInt(questions.size()));
    }

    public void markAnswered(TriviaQuestion question) {
        answeredQuestions.add(question);
    }

    public boolean add(TriviaQuestion newQuestion) {
        return questions.add(newQuestion);
    }
}