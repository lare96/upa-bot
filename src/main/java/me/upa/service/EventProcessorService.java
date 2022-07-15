package me.upa.service;

import com.google.common.util.concurrent.AbstractScheduledService;
import me.upa.UpaBot;
import me.upa.discord.Event;
import me.upa.discord.SendStormEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public final class EventProcessorService extends AbstractScheduledService {
    private static final Logger logger = LogManager.getLogger();

    private static final Path EVENT_FILE = Paths.get("data", "event.bin");
    private volatile Event currentEvent;

    @Override
    protected void startUp() throws Exception {
        if (Files.exists(EVENT_FILE)) {
            currentEvent = UpaBot.load(EVENT_FILE);
        }
    }

    @Override
    protected void runOneIteration() throws Exception {
        if (currentEvent != null) {
            if (currentEvent.getPending().get()) {
                return;
            }
            try {
                currentEvent.handleProcess();
            } catch (Exception e) {
                logger.catching(e);
            } finally {
                if (currentEvent != null) {
                    UpaBot.save(EVENT_FILE, currentEvent);
                }
            }
        }
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(30, 30, TimeUnit.SECONDS);
    }

    public boolean confirm(ButtonInteractionEvent event) {
        if (currentEvent != null && currentEvent.getPending().getAndSet(false)) {
            currentEvent.handleConfirm(event);
            UpaBot.save(EVENT_FILE, currentEvent);
            return true;
        }
        return false;
    }

    public void stop() throws IOException {
        currentEvent = null;
        Files.deleteIfExists(EVENT_FILE);
    }

    public void setCurrentEvent(Event currentEvent) {
        this.currentEvent = currentEvent;
    }

    public boolean isEventActive() {
        return currentEvent != null;
    }

    public String getEventName() {
        if (currentEvent == null) {
            throw new IllegalStateException("No event is active.");
        }
        return currentEvent.getName();
    }

}
