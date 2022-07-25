package me.upa.discord;

import com.google.common.primitives.Ints;
import me.upa.UpaBotContext;
import me.upa.discord.CreditTransaction.CreditTransactionType;
import me.upa.discord.command.AdminCommands;
import me.upa.discord.command.PacCommands;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Set;

public final class CreditViewerContextMenu extends ListenerAdapter {
    /**
     * The context.
     */
    private final UpaBotContext ctx;

    public CreditViewerContextMenu(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onUserContextInteraction(@NotNull UserContextInteractionEvent event) {
        if (event.getName().equals("View PAC")) {
            UpaMember upaMember = ctx.databaseCaching().getMembers().get(event.getTargetMember().getIdLong());
            if (upaMember == null) {
                event.reply("This player is not a member.").setEphemeral(true).queue();
                return;
            }
            event.reply("<@" + event.getTargetMember().getIdLong() + "> has **" + DiscordService.COMMA_FORMAT.format(upaMember.getCredit().get()) + " PAC**.").setEphemeral(true).queue();
            return;
        }
        UpaMember upaMember = ctx.databaseCaching().getMembers().get(event.getTargetMember().getIdLong());
        if (upaMember == null) {
            event.reply("This player is not a member.").setEphemeral(true).queue();
            return;
        }
        switch (event.getName()) {
            case "Give PAC [Admin]":
                event.replyModal(getCreditModal("give", "Give PAC")).
                        queue(success -> upaMember.getPendingTransactionTarget().set(event.getTargetMember()));
                break;
            case "Tip 250 PAC":
                if (Objects.equals(event.getMember(), event.getTargetMember())) {
                    event.reply("You cannot use this on yourself.").setEphemeral(true).queue();
                    return;
                }
                PacCommands.handleTransferCommand(ctx, event, event.getTargetMember(), 250, null, CreditTransactionType.TIP);
                break;
            case "View node properties":
                Set<UpaProperty> properties = ctx.databaseCaching().getMemberProperties().get(upaMember.getKey());
                StringBuilder sb = new StringBuilder();
                sb.append("Total properties ~ ").append(properties.size()).append("\n\n\n");
                for(UpaProperty property : properties) {
                    sb.append(property.getAddress()).append(" (").append(property.getUp2()).append(" UP2)").append(" | https://play.upland.me/?prop_id=").append(property.getPropertyId()).append("\n\n");
                }
                event.replyFile(sb.toString().getBytes(), upaMember.getInGameName()+"_node_properties.txt").setEphemeral(true).queue();
                break;
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case "give_pac_modal":
                doChecks(event, "give", (tm, amt, rsn) -> AdminCommands.handleGiveCredit(ctx, event, tm, amt, rsn));
                break;
        }
    }

    private void doChecks(ModalInteractionEvent event, String keyword, TriConsumer<Member, Integer, String> onSuccess) {
        UpaMember upaMember = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
        Member targetMember = upaMember.getPendingTransactionTarget().get();
        if (targetMember == null) {
            event.reply("Bot restarted, target member could not be found. Try again.").setEphemeral(true).queue();
            return;
        }
        String enteredAmount = event.getValue(keyword + "_pac_modal_amount").getAsString().replace(",", "").trim();
        Integer amount = Ints.tryParse(enteredAmount);
        if (amount == null) {
            event.reply("Amount '" + amount + "' is not a valid number.").setEphemeral(true).queue();
            return;
        }
        String enteredReason = event.getValue(keyword + "pac_modal_reason").getAsString().trim();
        onSuccess.accept(targetMember, amount, enteredReason);
    }

    private Modal getCreditModal(String id, String title) {
        return Modal.create(id, title).addActionRow(
                        TextInput.create(id + "_pac_modal_amount", "Amount", TextInputStyle.SHORT).setPlaceholder("1000").build(),
                        TextInput.create(id + "_pac_modal_reason", "Reason", TextInputStyle.SHORT).setPlaceholder("You are very cool.").setMinLength(2).build()).
                build();
    }
}
