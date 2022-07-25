package me.upa.discord;

import com.google.common.collect.ImmutableSet;
import me.upa.UpaBotContext;
import me.upa.discord.CreditTransaction.CreditTransactionType;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class PacLotteryButtonListener extends ListenerAdapter {
    private static final ImmutableSet<Long> BANNED = ImmutableSet.of(
            373218455937089537L,
            220622659665264643L
    );

    /**
     * The context.
     */
    private final UpaBotContext ctx;

    public PacLotteryButtonListener(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId().equals("buy_ticket")) {
            var currentLottery = ctx.variables().lottery().getValue();
            if (currentLottery == null) {
                event.reply("A lottery is not running at the moment. Please try again later.").setEphemeral(true).queue();
                return;
            }
            long memberId = event.getMember().getIdLong();
            UpaMember upaMember = ctx.databaseCaching().getMembers().get(memberId);
            if (upaMember == null) {
                event.reply("You must create an account using /account before buying a lottery ticket.").setEphemeral(true).queue();
                return;
            }
            if (upaMember.getCredit().get() < 250) {
                event.reply("You must have at least 250 PAC to buy a lottery ticket.").setEphemeral(true).queue();
                return;
            }
            if (ctx.lottery().hasTicket(event.getMember().getIdLong())) {
                event.reply("You have already bought a ticket.").setEphemeral(true).queue();
                return;
            }

            event.deferReply(true).queue();
            ctx.discord().sendCredit(new CreditTransaction(upaMember, -250, CreditTransactionType.REDEEM, "buying a lottery ticket") {
                @Override
                public void onSuccess() {
                    ctx.lottery().addTicket(event.getMember().getIdLong());
                    event.getHook().setEphemeral(true).editOriginal("You have successfully bought a lottery ticket for **250 PAC**.").queue();
                }
            });
        } else if (event.getButton().getId().equals("view_participants")) {
            var currentLottery = ctx.variables().lottery().getValue();
            if (currentLottery == null) {
                event.reply("A lottery is not running at the moment. Please try again later.").setEphemeral(true).queue();
                return;
            }
            if (currentLottery.getContestants().isEmpty()) {
                event.reply("Seems like there are no contestants. Buy a ticket now for free PAC!").setEphemeral(true).queue();
                return;
            }
            MessageBuilder mb = new MessageBuilder();
            for (long id : currentLottery.getContestants()) {
                mb.append("<@").append(id).append(">\n");
            }
            event.reply(mb.build()).setEphemeral(true).queue();
        }
    }
}
