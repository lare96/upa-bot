package me.upa.discord;

import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public final class RulesButtonListener extends ListenerAdapter {
    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null)
            return;
        switch (event.getButton().getId()) {
            case "member_guidelines":
                event.reply(new MessageBuilder().append("1. Genesis block properties should be traded amongst UPA members rather than on the public secondary market\n\n").
                                append("2. You're encouraged to send a few times to the newly built spark train properties if you can\n\n").
                                append("3. If you have an issue with something on the server, post why in <#963112034726195210> or DM <@220622659665264643>/<@373218455937089537>. We are always open to new ideas.").build()).
                        setEphemeral(true).queue();
                break;
            case "st_guidelines":
                event.reply(new MessageBuilder().
                        append("1. It is encouraged to stick with smaller structures for your first build\n").
                        append("2. Try and prioritize genesis block properties over neighborhood wide properties when requesting a build\n").
                        append("3. When your build is receiving spark from the train, it's a encouraged to stake on your own in-progress build rather than someone elses\n").build()).
                        setEphemeral(true).queue();
                break;
        }
    }
}
