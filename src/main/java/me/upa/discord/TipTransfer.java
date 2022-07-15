package me.upa.discord;

public final class TipTransfer extends CreditTransfer {

    public TipTransfer(UpaMember sender, UpaMember receiver, int amount) {
        super(sender, receiver, CreditTransactionType.TIP, amount, null);
    }
}
