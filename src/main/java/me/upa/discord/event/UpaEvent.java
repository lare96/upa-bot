package me.upa.discord.event;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import me.upa.UpaBotContext;
import me.upa.discord.event.impl.BonusPacEventHandler;
import me.upa.discord.event.impl.BonusSshEventHandler;
import me.upa.discord.event.impl.DoubleDividendEventHandler;
import me.upa.discord.event.impl.FreeLotteryEventHandler;
import me.upa.discord.event.impl.FreePacEventHandler;
import me.upa.discord.event.trivia.TriviaEventHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.units.qual.A;

import javax.management.ReflectionException;
import java.awt.*;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class UpaEvent implements Serializable {

    public static final ImmutableList<Supplier<UpaEventHandler>> EVENT_HANDLERS;
    public static final ImmutableMap<String, Supplier<UpaEventHandler>> EVENT_HANDLER_MAPPINGS;

    public static boolean isActive(UpaBotContext ctx, Class<? extends UpaEventHandler> eventType) {
        return eventType.isInstance(ctx.variables().event().get().getHandler());
    }

    public static <T extends UpaEventHandler> void forEvent(UpaBotContext ctx, Class<T> eventType, Consumer<T> success, Runnable failure) {
        UpaEvent event = ctx.variables().event().get();
        if (event != null) {
            UpaEventHandler eventHandler = event.getHandler();
            if (eventType.isInstance(eventHandler)) {
                success.accept((T) eventHandler);
                return;
            }
        }
        failure.run();
    }

    public static <T extends UpaEventHandler> void forEvent(UpaBotContext ctx, Class<T> eventType, Consumer<T> success) {
        forEvent(ctx, eventType, success, () -> {
        });
    }

    static {
        ImmutableList.Builder<Supplier<UpaEventHandler>> handlerList = ImmutableList.builder();
        handlerList.add(BonusPacEventHandler::new);
        handlerList.add(FreePacEventHandler::new);
        handlerList.add(BonusSshEventHandler::new);
        handlerList.add(FreeLotteryEventHandler::new);
        handlerList.add(DoubleDividendEventHandler::new);
        handlerList.add(TriviaEventHandler::new);
        // eventHandlers.add(WinAPropertyEventHandler::new);
        EVENT_HANDLERS = handlerList.build();

        ImmutableMap.Builder<String, Supplier<UpaEventHandler>> handlerMap = ImmutableMap.builder();
        for(var next : EVENT_HANDLERS) {
            var obj = next.get();
            handlerMap.put(obj.name(), next);
        }
        EVENT_HANDLER_MAPPINGS = handlerMap.build();
    }


    private static final long serialVersionUID = -6168910050793772274L;
    private static final Logger logger = LogManager.getLogger();
    private Instant startAt;
    private Instant endAfter;
    private UpaEventHandler handler;
    private volatile long messageId;

    private volatile Class<?> lastEvent = BonusSshEventHandler.class;

    private AtomicReference<UpaEventHandler> nextEvent = new AtomicReference<>(new BonusSshEventHandler());

    public boolean start(UpaBotContext ctx) {
        if (messageId == 0) {
            handler = computeNextEvent();
            startAt = Instant.now();
            endAfter = startAt.plus(handler.durationDays(), ChronoUnit.DAYS);
            List<Message> history = ctx.discord().guild().getTextChannelById(1001694147490619453L).getHistory().retrievePast(100).complete();
            for (Message msg : history) {
                if (msg.getAuthor().getIdLong() == 956871679584403526L) {
                    msg.delete().complete();
                }
            }
            Message msg = ctx.discord().guild().getTextChannelById(1001694147490619453L).
                    sendMessage(computeEventMsg()).complete();
            messageId = msg.getIdLong();
            handler.onStart(ctx);
            return true;
        }
        return false;
    }

    public void end(UpaBotContext ctx) {
        messageId = 0;
        lastEvent = handler.getClass();
        handler.onEnd(ctx);
        start(ctx);
        ctx.variables().event().save();
    }

    public void rebuildMessage(UpaBotContext ctx, MessageEditData editMsg, MessageCreateData createMsg) {
        if (messageId != 0) {
            ctx.discord().guild().getTextChannelById(1001694147490619453L).editMessageById(messageId, editMsg == null ? computeEventEditMsg() : editMsg).queue();
        } else {
            messageId = ctx.discord().guild().getTextChannelById(1001694147490619453L).sendMessage(createMsg == null ? computeEventMsg() : createMsg).complete().getIdLong();
        }
    }

    public void rebuildMessage(UpaBotContext ctx) {
        rebuildMessage(ctx, null, null);
    }

    public void resetMessageId() {
        messageId = 0;
    }

    private MessageCreateData computeEventMsg() {
        return new MessageCreateBuilder().setContent("<@&999013596111581284> New event!").setEmbeds(new EmbedBuilder().
                        setTitle(handler.name()).setDescription(handler.buildMessageContent()).setColor(Color.GREEN).
                        addField("Ends in", computeDuration(), false).build()).
                setComponents(handler.buildMessageComponents()).build();
    }

    private MessageEditData computeEventEditMsg() {
        return new MessageEditBuilder().setEmbeds(new EmbedBuilder().
                        setTitle(handler.name()).setDescription(handler.buildMessageContent()).setColor(Color.GREEN).
                        addField("Ends in", computeDuration(), false).build()).
                setComponents(handler.buildMessageComponents()).build();
    }

    private UpaEventHandler computeNextEvent() {
        UpaEventHandler nextEventHandler = nextEvent.getAndSet(null);
        if (nextEventHandler != null) {
            return nextEventHandler;
        }
        List<UpaEventHandler> selection = new ArrayList<>();
        int rand = ThreadLocalRandom.current().nextInt(100);
        Stream<Supplier<UpaEventHandler>> eventHandlerStream = EVENT_HANDLERS.stream();
        if (rand > 94) {
            eventHandlerStream.map(Supplier::get).filter(handler -> handler.getClass() != lastEvent).
                    filter(handler -> handler.rarity() == UpaEventRarity.RARE).forEach(selection::add);
        } else if (rand > 60) {
            eventHandlerStream.map(Supplier::get).filter(handler -> handler.getClass() != lastEvent).
                    filter(handler -> handler.rarity() == UpaEventRarity.UNCOMMON).forEach(selection::add);
        } else {
            eventHandlerStream.map(Supplier::get).filter(handler -> handler.getClass() != lastEvent).
                    filter(handler -> handler.rarity() == UpaEventRarity.COMMON).forEach(selection::add);
        }
        return selection.get(ThreadLocalRandom.current().nextInt(selection.size()));
    }

    private String computeDuration() {
        String finishedIn;
        Instant now = Instant.now();
        long daysUntil = now.until(endAfter, ChronoUnit.DAYS);
        long hoursUntil = now.until(endAfter, ChronoUnit.HOURS);
        long minutesUntil = now.until(endAfter, ChronoUnit.MINUTES);
        if (daysUntil > 0) {
            finishedIn = daysUntil + " day(s)";
        } else if (hoursUntil > 0) {
            finishedIn = hoursUntil + " hour(s)";
        } else if (minutesUntil > 0) {
            finishedIn = minutesUntil + " minute(s)";
        } else {
            finishedIn = "a few seconds";
        }
        return finishedIn;
    }

    public void setNextEvent(UpaEventHandler nextEvent) {
        if(this.nextEvent == null) {
            this.nextEvent = new AtomicReference<>(nextEvent);
            return;
        }
        this.nextEvent.set(nextEvent);
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAfter() {
        return endAfter;
    }

    public UpaEventHandler getHandler() {
        return handler;
    }

    public long getMessageId() {
        return messageId;
    }

}
