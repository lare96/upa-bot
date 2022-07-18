package me.upa.discord;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import me.upa.UpaBot;
import me.upa.discord.CreditTransaction.CreditTransactionType;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;

public final class ReferralMessageListener extends ListenerAdapter {

    private static final class UpdateReferralsTask extends SqlTask<Void> {

        private final long memberId;
        private final int referrals;

        public UpdateReferralsTask(long memberId, int referrals) {
            this.memberId = memberId;
            this.referrals = referrals;
        }

        @Override
        public Void execute(Connection connection) throws Exception {
            try (PreparedStatement ps = connection.prepareStatement("UPDATE members SET referrals = ? WHERE member_id = ?;")) {
                ps.setInt(1, referrals);
                ps.setLong(2, memberId);
                ps.executeUpdate();
            }
            return null;
        }
    }

    private static final Logger logger = LogManager.getLogger();
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().getIdLong() == 499595256270946326L) {
            String[] sections = event.getMessage().getContentStripped().split(" ");
         logger.error(new IllegalStateException(String.valueOf(sections.length)));
            if (sections.length == 14) {
                logger.info("Entered");
                String invitedBy = sections[7];
                Long invitedById = Longs.tryParse(invitedBy.substring(2, invitedBy.length() - 1));
                if (invitedById != null) {
                    logger.info("invitedby");
                    Integer invites = Ints.tryParse(sections[11]);
                    UpaMember upaMember = UpaBot.getDatabaseCachingService().getMembers().get(invitedById);
                    if (invites != null && upaMember != null) {
                        int storedInvites = upaMember.getReferrals().get();
                        int diff = invites - storedInvites;
                        if (diff > 0) {
                            SqlConnectionManager.getInstance().execute(new UpdateReferralsTask(invitedById, invites), success -> {
                                upaMember.getReferrals().set(invites);
                                UpaBot.getDiscordService().sendCredit(new CreditTransaction(upaMember, 200, CreditTransactionType.REFERRAL));
                            });
                        }
                    }
                }
            }
        }
    }
}
