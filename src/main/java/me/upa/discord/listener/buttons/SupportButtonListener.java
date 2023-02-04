package me.upa.discord.listener.buttons;

import com.google.common.primitives.Longs;
import me.upa.UpaBotConstants;
import me.upa.UpaBotContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class SupportButtonListener extends ListenerAdapter {

    private final UpaBotContext ctx;
    private final Map<Long, Instant> lastSuggestion = new ConcurrentHashMap<>();

    public SupportButtonListener(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null)
            return;
        switch (event.getButton().getId()) {
            case "request_help":
                event.deferReply(true).queue();
                ctx.discord().execute(() -> {
                    List<ThreadChannel> channelList = ctx.discord().guild().getTextChannelById(1025947323836137523L).getThreadChannels();
                    for (ThreadChannel channel : channelList) {
                        String memberId = channel.getName().replace("help-", "").trim();
                        if (event.getMember().getId().equals(memberId)) {
                            event.getHook().editOriginal("You already have an open ticket at " + channel.getAsMention() + ". Please add your additional concerns there.").complete();
                            return;
                        }
                    }
                    event.getHook().editOriginal(new MessageEditBuilder().
                            setContent("Spam check passed. Click the button below to send us a ticket.").
                            setComponents(ActionRow.of(
                                    Button.of(ButtonStyle.PRIMARY, "send_a_ticket", "Send a ticket", Emoji.fromUnicode("U+1F4E9"))
                            )).build()).complete();
                });
                break;
            case "send_a_ticket":
                event.replyModal(Modal.create("support_modal", "Request Help").addActionRows(ActionRow.of(
                        TextInput.create("support_modal_description", "Description", TextInputStyle.PARAGRAPH).setMinLength(10).build()
                )).build()).queue();
                break;
            case "make_a_suggestion":
                event.replyModal(Modal.create("suggestion_modal", "Make a Suggestion").addActionRows(ActionRow.of(
                        TextInput.create("suggestion_modal_description", "Description", TextInputStyle.PARAGRAPH).setMinLength(10).build()
                )).build()).queue();
                break;
            case "close_thread":
                long memberId = event.getMember().getIdLong();
                Long originalMemberId = Longs.tryParse(event.getChannel().getName().replace("help-", ""));
                if (!UpaBotConstants.ADMINS.contains(memberId) && !Objects.equals(memberId, originalMemberId)) {
                    event.reply("Only admins or the person that opened the ticket can close it.").setEphemeral(true).queue();
                    return;
                }
                event.reply("Are you sure you would like to close the thread?").addActionRow(
                        Button.of(ButtonStyle.SUCCESS, "confirm_close_thread", "Yes", Emoji.fromUnicode("U+2705"))
                ).queue();
                break;
            case "confirm_close_thread":
                event.reply("Closing thread...").queue(after ->
                        event.getChannel().asThreadChannel().getManager().setArchived(true).setLocked(true).queue());
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
        }

    }

    public static void openTicket(UpaBotContext ctx, Member member, String description, BiConsumer<Channel, Message> after) {
        ctx.discord().bot().getTextChannelById(1025947323836137523L).createThreadChannel("help-" + member.getIdLong()).setAutoArchiveDuration(AutoArchiveDuration.TIME_1_WEEK).queue(channel -> {
            channel.addThreadMemberById(member.getIdLong()).queue();
            channel.addThreadMemberById(UpaBotConstants.UNRULY_CJ_MEMBER_ID).queue();
            channel.addThreadMemberById(UpaBotConstants.HIGH_ROAD_MEMBER_ID).queue();
            channel.sendMessageEmbeds(new EmbedBuilder().setDescription(description).
                    addField("Sent by", member.getAsMention(), false).build()).setActionRow(
                    Button.of(ButtonStyle.PRIMARY, "close_thread", "Close thread", Emoji.fromUnicode("U+1F517"))
            ).queue(then -> after.accept(channel, then));
        });
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case "suggestion_modal":
                event.deferReply(true).queue();
                String description = event.getValue("suggestion_modal_description").getAsString().trim();
                event.getGuild().getTextChannelById(1025872699093942392L).
                        sendMessageEmbeds(new EmbedBuilder().setDescription(description).
                                addField("Sent by", event.getMember().getAsMention(), false).build()).queue(then -> {
                            event.getHook().editOriginal("Your suggestion has been sent for review.").queue();
                            lastSuggestion.put(event.getMember().getIdLong(), Instant.now());
                        });
                break;
            case "support_modal":
                event.deferReply(true).queue();
                description = event.getValue("support_modal_description").getAsString().trim();
                openTicket(ctx, event.getMember(), description, (channel, then) ->
                        event.getHook().editOriginal("A ticket has been opened for your request at " + channel.getAsMention() + ".").queue());
                break;
        }
    }
}
