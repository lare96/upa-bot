package me.upa.discord.listener.credit;

import com.google.common.primitives.Ints;
import me.upa.discord.DiscordService;
import me.upa.discord.UpaMember;
import net.dv8tion.jda.api.entities.emoji.Emoji;

public class CreditTransaction {

    public enum CreditTransactionType {
        // each transaction type should be able to have different effects like rules to go through, and actions on completion, etc.
        VOTING("**<username>** received **<amount> PAC** for voting in a poll: **<reason>**."),
        PURCHASE("**<username>** purchased **<amount> PAC** through <reason>."),
        REDEEM("**<username>** lost **<amount> PAC** for <reason>."),
        DAILY("**<username>** claimed a dividend of **<amount> PAC**."),
        EVENT("**<username>** claimed **<amount> PAC** for participating in <reason>."),
        GIFTED("SYSTEM: **<username>** received **<amount> PAC**. Reason: **<reason>**"),
        MINTED("**<username>** received **<amount> PAC** for minting <mint>."),
        OTHER("**<username>** received **<amount> PAC**. Reason: **<reason>**"),
        REFERRAL("**<username>** received **<amount> PAC** for referrals. **Total referrals: <referral_amount>**."),
        TRANSFER("**<username>** sent **<amount> PAC** to <other_username>. Reason: **<reason>**"),
        LOTTERY("**<username>** received **<amount> PAC** for winning the lottery!"),

        SLOTS("**<username>** received **<amount> PAC** for <outcome> at the slots!"),
        TIP("**<username>** tipped **<other_username>** **<amount> PAC**! " + Emoji.fromUnicode("U+1F37B").getFormatted());

        private final String message;

        CreditTransactionType(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private final UpaMember upaMember;
    private final int amount;
    private final CreditTransactionType transactionType;
    private final String reason;
    private volatile String message;

    public CreditTransaction(UpaMember upaMember, int amount, CreditTransactionType transactionType, String reason) {
        this.upaMember = upaMember;
        this.amount = amount;
        this.transactionType = transactionType;
        this.reason = reason;
    }

    public CreditTransaction(UpaMember upaMember, int amount, CreditTransactionType transactionType) {
        this(upaMember, amount, transactionType, null);
    }

    public CreditTransaction withAmount(int newAmount) {
        return new CreditTransaction(upaMember, newAmount, transactionType, reason);
    }
    public void onSuccess() {

    }

    public int getAmount() {
        return amount;
    }

    public CreditTransactionType getTransactionType() {
        return transactionType;
    }

    public String getReason() {
        String originalMessage = transactionType.getMessage();
        if (amount < 0) {
            originalMessage = originalMessage.replace("received", "lost");
        }
        String formattedReason = originalMessage.replace("<username>", "<@" + upaMember.getMemberId() + ">").replace("<amount>", amount < 0 ? DiscordService.COMMA_FORMAT.format(Math.abs(amount)) :
                DiscordService.COMMA_FORMAT.format(amount));
        if (transactionType == CreditTransactionType.REFERRAL) {
            formattedReason = formattedReason.replace("<referral_amount>", DiscordService.COMMA_FORMAT.format(upaMember.getReferrals().get()));
        }
        if (transactionType == CreditTransactionType.MINTED) {
            int mintAmount = Ints.tryParse(reason);
            String msg;
            if (mintAmount > 1) {
                msg = mintAmount + " node properties";
            } else {
                msg = "a node property";
            }
            formattedReason = formattedReason.replace("<mint>", msg);
        }
        if(transactionType == CreditTransactionType.SLOTS) {
            formattedReason = formattedReason.replace("<outcome>", amount > 0 ? "winning" : "losing");
        }
        if (reason != null) {
            formattedReason = formattedReason.replace("<reason>", reason);
        }
        message = formattedReason;
        return formattedReason;
    }

    public String getHistoryReason() {
        return getReason().replace("**", "").replace("<@"+upaMember.getMemberId()+">", upaMember.getDiscordName()+" ("+upaMember.getKey()+")");
    }

    public UpaMember getUpaMember() {
        return upaMember;
    }

    public String getMessage() {
        return message;
    }
}
