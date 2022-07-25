package me.upa.discord;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import me.upa.UpaBotContext;
import me.upa.discord.CreditTransaction.CreditTransactionType;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;

public final class ReferralMessageListener extends ListenerAdapter {

    public ReferralMessageListener(UpaBotContext ctx) {
        this.ctx = ctx;
    }

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

    /**
     * The context.
     */
    private final UpaBotContext ctx;

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().getIdLong() == 499595256270946326L) {
            String[] sections = event.getMessage().getContentStripped().split(" ");
            if (sections.length >= 14) {
                List<Member> mentions = event.getMessage().getMentions().getMembers();
                if(mentions.size() < 2) {
                    logger.warn(mentions);
                    return;
                }
                Member invitedBy = mentions.get(1);
                Long invitedById = invitedBy.getIdLong();
                Integer invites = Ints.tryParse(sections[11]);
                UpaMember upaMember = ctx.databaseCaching().getMembers().get(invitedById);
                logger.warn("{},{},{}", invitedById, invites, upaMember);
                if (invites != null && upaMember != null) {
                    int storedInvites = upaMember.getReferrals().get();
                    int diff = invites - storedInvites;
                    if (diff > 0) {
                        SqlConnectionManager.getInstance().execute(new UpdateReferralsTask(invitedById, invites), success -> {
                            upaMember.getReferrals().set(invites);
                            ctx.discord().sendCredit(new CreditTransaction(upaMember, 200 * diff, CreditTransactionType.REFERRAL));
                        });
                    }
                }
            }
        }
    }
}
