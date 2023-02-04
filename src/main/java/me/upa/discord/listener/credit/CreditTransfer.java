package me.upa.discord.listener.credit;

import me.upa.discord.UpaMember;

public class CreditTransfer extends CreditTransaction {
    private final UpaMember receiver;

    public CreditTransfer(UpaMember sender, UpaMember receiver, int amount, String reason) {
        super(sender, amount, CreditTransactionType.TRANSFER, reason);
        this.receiver = receiver;
    }
    public CreditTransfer(UpaMember sender, UpaMember receiver, CreditTransactionType type, int amount, String reason) {
        super(sender, amount, type, reason);
        this.receiver = receiver;
    }
    @Override
    public String getReason() {
        return super.getReason().replace("<other_username>", "<@" + receiver.getMemberId() + ">");
    }

    public String getHistoryReason() {
        return getReason().replace("<@"+getUpaMember().getMemberId()+">", getUpaMember().getDiscordName().get()+" ("+getUpaMember().getKey()+")").
                replace("<@"+receiver.getMemberId()+">", receiver.getDiscordName().get()+" ("+receiver.getKey()+")");
    }

    public UpaMember getReceiver() {
        return receiver;
    }
}
