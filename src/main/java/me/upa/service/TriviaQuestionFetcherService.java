package me.upa.service;

import com.google.common.util.concurrent.AbstractScheduledService;
import me.upa.fetcher.TriviaDataFetcher;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.EnumSet;

public final class TriviaQuestionFetcherService extends AbstractScheduledService {
    private final TriviaDataFetcher fetcher = new TriviaDataFetcher();

    @Override
    protected void runOneIteration() throws Exception {
        //   fetcher.fetchAndStore();
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(Duration.ofSeconds(7), Duration.ofSeconds(7));
    }

    public static void main(String[] args) throws IOException {
        combineTrivia();
    }

    public static void combineTrivia() throws IOException {
        System.out.println("Starting...");
        StringBuilder sb = new StringBuilder("[");
        Files.walkFileTree(Paths.get("data", "trivia_temp"), new FileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String contents = Files.readString(file);
                sb.append(contents).append(",");
                System.out.println("Completed file " + file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                System.out.println("Failure: "+file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
        sb.setLength(sb.length() - 1);
        sb.append("]");
        final Path data = Paths.get("data", "trivia.json");
        Files.createFile(data);
        Files.writeString(data, sb.toString());
        System.out.println("Finished");
    }
}
