package me.upa.service;

import com.google.common.util.concurrent.AbstractScheduledService;
import me.upa.UpaBot;
import me.upa.UpaBotContext;
import me.upa.discord.Event;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class EventProcessorMicroService extends MicroService {
    private static final Logger logger = LogManager.getLogger();

    private static final Path EVENT_FILE = Paths.get("data", "event.bin");
    private volatile Event currentEvent;
    private final UpaBotContext ctx;

    /**
     * Creates a new {@link EventProcessorMicroService}.
     */
    public EventProcessorMicroService(UpaBotContext ctx) {
        super(Duration.ofMinutes(1));
        this.ctx = ctx;
    }

    @Override
    public void startUp() throws Exception {
        if (Files.exists(EVENT_FILE)) {
            currentEvent = ctx.load(EVENT_FILE);
        }
    }

    @Override
    public void run() throws Exception {
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
                    ctx.save(EVENT_FILE, currentEvent);
                }
            }
        }
    }

    public boolean confirm(ButtonInteractionEvent event) {
        if (currentEvent != null && currentEvent.getPending().getAndSet(false)) {
            currentEvent.handleConfirm(event);
            ctx.save(EVENT_FILE, currentEvent);
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
