package me.upa.discord.event.impl;

import me.upa.UpaBotContext;
import me.upa.discord.UpaMember;
import me.upa.discord.event.UpaEventHandler;
import me.upa.discord.event.UpaEventRarity;
import me.upa.discord.listener.credit.CreditTransaction;
import me.upa.discord.listener.credit.CreditTransaction.CreditTransactionType;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class FreePacEventHandler extends UpaEventHandler {

    private static final long serialVersionUID = 3450229747284893025L;
    private final Map<Long, Integer> claimed = new ConcurrentHashMap<>();

    @Override
    public UpaEventRarity rarity() {
        return UpaEventRarity.RARE;
    }

    @Override
    public String name() {
        return "Free PAC";
    }

    @Override
    public int durationDays() {
        return 1;
    }

    @Override
    public StringBuilder buildMessageContent() {
        return new StringBuilder().append("Click the button to get some free PAC as thanks for being an active UPA member!");
    }

    @Override
    public List<ActionRow> buildMessageComponents() {
        return List.of(ActionRow.of(
                Button.of(ButtonStyle.PRIMARY, "free_pac", "Claim your free PAC"),
                Button.of(ButtonStyle.PRIMARY, "claim_list", "View claim list")
        ));
    }

    public void givePac(UpaBotContext ctx, IReplyCallback event, UpaMember member) {
        if (claimed.containsKey(member.getMemberId())) {
            event.getHook().editOriginal("You have already claimed your free PAC.").queue();
            return;
        }
        int base = 400;
        int chance = ThreadLocalRandom.current().nextInt(100);
        if (chance < 5) {
            base *= ThreadLocalRandom.current().nextInt(6, 9);
        } else if (chance < 15) {
            base *= ThreadLocalRandom.current().nextInt(4, 8);
        } else if (chance < 30) {
            base *= ThreadLocalRandom.current().nextInt(2, 7);
        } else if (chance < 50) {
            base *= ThreadLocalRandom.current().nextInt(1, 6);
        } else if(chance < 75) {
            base = ThreadLocalRandom.current().nextBoolean() ? base * 2 : base;
        }
        int finalBase = base;
        ctx.discord().sendCredit(new CreditTransaction(member, finalBase, CreditTransactionType.OTHER, "Free PAC event") {
            @Override
            public void onSuccess() {
                claimed.put(member.getMemberId(), finalBase);
                ctx.variables().event().save();
                event.getHook().editOriginal("Free PAC successfully claimed!").queue();
            }
        });
    }

    public Map<Long, Integer> getClaimed() {
        return claimed;
    }
}
