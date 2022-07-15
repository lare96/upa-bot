package me.upa.discord.command;

import com.google.common.primitives.Longs;
import me.upa.UpaBot;
import me.upa.discord.SendStormEvent;
import me.upa.discord.UpaProperty;
import me.upa.service.DatabaseCachingService;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class EventCommands extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("events")) {
            switch (event.getSubcommandName()) {
                case "poll":
                    handlePoll(event);
                    break;
                case "send_storm":
                    handleSendStorm(event);
                    break;
            }
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if(event.getButton().getId() == null)
            return;
        switch (event.getButton().getId()) {
            case "start_event":
                handleConfirm(event);
                event.editButton(event.getButton().asDisabled()).queue();
                break;
        }
    }

    private void handlePoll(SlashCommandInteractionEvent event) {

    }

    private void handleSendStorm(SlashCommandInteractionEvent event) {
        if (UpaBot.getEventProcessorService().isEventActive()) {
            event.reply("Please end the current '" + UpaBot.getEventProcessorService().getEventName() + "' event before starting a new one.").queue();
            return;
        }
        event.deferReply().setEphemeral(true).queue();
        UpaBot.getDiscordService().execute(() -> {
            Long propertyId = Longs.tryParse(event.getOptions().get(0).getAsString());
            if(propertyId == null) {
                event.getHook().setEphemeral(true).editOriginal("Invalid property ID "+propertyId+".").queue();
                return;
            }
            UpaProperty property = UpaBot.getDatabaseCachingService().getProperties().get(propertyId);
            if (property == null) {
                event.getHook().setEphemeral(true).editOriginal("Property ID " + propertyId + " could not be found.").queue();
                return;
            }
            Set<Long> participants = Arrays.stream(event.getOptions().get(1).getAsString().split(",")).map(String::trim).map(Long::parseLong).
                    collect(Collectors.toSet());
            StringBuilder sb = new StringBuilder();
            Set<Member> members = participants.stream().map(next -> UpaBot.getDiscordService().guild().retrieveMemberById(next).complete()).collect(Collectors.toSet());
            members.forEach(next ->
                    sb.append(next.getIdLong()).append(','));
            sb.setLength(sb.length() - 1);
            event.getHook().setEphemeral(true).sendMessage("This is a preview of what r.i.c.h goat will post about the event. If there are any issues you can overwrite this by using '/events send_storm' again. Use the green checkmark reaction to start the event.").queue();
            event.getHook().setEphemeral(true).sendMessage("```\n[Input]\nproperty_id = " + propertyId + "\nparticipants = " + participants + "\n```").queue();


            var sendStormEvent = new SendStormEvent(participants, propertyId);
            sb.setLength(0);
            sb.append("**PREVIEW**\n\n").append(generateMessage(sendStormEvent));
            event.getHook().setEphemeral(true).sendMessage(sb.toString()).addActionRow(Button.of(ButtonStyle.SUCCESS, "start_event", "Start", Emoji.fromUnicode("U+1F3E0"))).queue();
            UpaBot.getEventProcessorService().setCurrentEvent(sendStormEvent);
        });
    }

    private void handleConfirm(ButtonInteractionEvent event) {
        UpaBot.getEventProcessorService().confirm(event);
    }

    public static StringBuilder generateMessage(SendStormEvent event) {
        DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
        UpaProperty upaProperty = databaseCaching.getProperties().get(event.getPropertyId());
        var sb = new StringBuilder();
        sb.append("The send storm event has officially started! Please send 10 times to ").
                append(upaProperty.getAddress()).append(" to be officially counted in the draw. There will be a green checkmark beside your name once your visits are recorded.\n\n");
        for (long next : event.getParticipants()) {
            int count = event.getSends().count(next);
            String emoji = count >= 10 ? ":white_check_mark:" : ":no_entry_sign:";
            sb.append("<@").append(next).append("> ").append(emoji).append(" (").append(count).append("/10 visits)\n\n");
        }
        return sb;
    }
}
