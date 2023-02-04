package me.upa.discord.event;

import me.upa.UpaBotContext;
import net.dv8tion.jda.api.interactions.components.ActionRow;

import java.io.Serializable;
import java.util.List;

public abstract class UpaEventHandler implements Serializable {

    private static final long serialVersionUID = 8960027224445817785L;

    public void onStart(UpaBotContext ctx) {

    }
    public void onEnd(UpaBotContext ctx) {

    }
    public void onLoop(UpaBotContext ctx) {
        ctx.variables().event().accessValue(event -> {
            event.rebuildMessage(ctx);
            return false;
        });
    }
    public abstract UpaEventRarity rarity();
    public abstract String name();
    public abstract int durationDays();
    public abstract StringBuilder buildMessageContent();
    public abstract List<ActionRow> buildMessageComponents();

}
