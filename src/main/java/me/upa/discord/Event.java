package me.upa.discord;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Event implements Serializable {

    private static final long serialVersionUID = 6833522799748892967L;
    private final AtomicBoolean pending = new AtomicBoolean(true);
    private final String name;


    protected Event(String name) {
        this.name = name;
    }

    public void startUp() {

    }

    public abstract void handleConfirm(ButtonInteractionEvent event);

    public abstract void handleProcess();

    public AtomicBoolean getPending() {
        return pending;
    }

    public String getName() {
        return name;
    }
}
