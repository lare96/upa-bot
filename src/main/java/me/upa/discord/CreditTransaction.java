package me.upa.discord;

import net.dv8tion.jda.api.entities.Emoji;

public class CreditTransaction {

    public enum CreditTransactionType {
        // each transaction type should be able to have different effects like rules to go through, and actions on completion, etc.
        VOTING("**<username>** received **<amount> PAC** for voting in a poll: **<reason>**."),
        PURCHASE("**<username>** purchased **<amount> PAC** through <reason>."),
        REDEEM("**<username>** lost **<amount> PAC** for <reason>."),
        SENDS("**<username>** received **<amount> PAC** for visiting a scholar's property."),
        DAILY("**<username>** claimed a dividend of **<amount> PAC**."),
        EVENT("**<username>** claimed **<amount> PAC** for participating in <reason>."),
        GIFTED("SYSTEM: **<username>** received **<amount> PAC**. Reason: **<reason>**"),

        OTHER("**<username>** received **<amount> PAC**. Reason: **<reason>**"),
        REFERRAL("**<username>** received **<amount> PAC** for a referral. **Total referrals: <referral_amount>**."),
        TRANSFER("**<username>** sent **<amount> PAC** to <other_username>. Reason: **<reason>**"),
        LOTTERY("**<username> received **<amount> PAC** for winning the lottery!"),
        TIP("**<username>** tipped **<other_username>** **<amount> PAC**! " + Emoji.fromUnicode("U+1F37B").getAsMention());

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
            formattedReason = transactionType.getMessage().replace("<total_referrals>", DiscordService.COMMA_FORMAT.format(upaMember.getReferrals().get()));
        }
        if (reason != null) {
            formattedReason = formattedReason.replace("<reason>", reason);
        }
        message = formattedReason;
        return formattedReason;
    }

    public UpaMember getUpaMember() {
        return upaMember;
    }
}
