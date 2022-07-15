package me.upa.sql;

import com.google.common.base.Objects;
import me.upa.discord.CreditTransaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public final class AddSendsTask extends SqlTask<List<CreditTransaction>> {

    public static final class SendAddition {
        private final String username;
        private final int additions;
        private final int sponsoredAdditions;

        public SendAddition(String username, int additions, int sponsoredAdditions) {
            this.username = username;
            this.additions = additions;
            this.sponsoredAdditions = sponsoredAdditions;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SendAddition that = (SendAddition) o;
            return Objects.equal(username, that.username);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(username);
        }
    }
    private static final Logger logger = LogManager.getLogger();

    private final Set<SendAddition> additions;
    private final Map<String, String> lastFetchInstants;

    public AddSendsTask(Set<SendAddition> additions, Map<String, String> lastFetchInstants) {
        this.additions = additions;
        this.lastFetchInstants = lastFetchInstants;
    }

    @Override
    public List<CreditTransaction> execute(Connection connection) throws Exception {
        List<CreditTransaction> transactions = new ArrayList<>();
        try {
            if (additions.size() > 0) {
                try (PreparedStatement updateMember = connection.prepareStatement("UPDATE members SET sends = sends + ?, sponsored_sends = sponsored_sends + ? WHERE in_game_name = ?;")) {
                    for (SendAddition next : additions) {
                        updateMember.setInt(1, next.additions);
                        updateMember.setInt(2, next.sponsoredAdditions);
                        updateMember.setString(3, next.username);
                        updateMember.addBatch();
                    }
                    updateMember.executeBatch();
                }
                additions.clear();
            }
            if (lastFetchInstants.size() > 0) {
                try (PreparedStatement updateScholar = connection.prepareStatement("UPDATE scholars SET last_fetch = ? WHERE username = ?;")) {
                    for (Entry<String, String> next : lastFetchInstants.entrySet()) {
                        updateScholar.setString(1, next.getValue());
                        updateScholar.setString(2, next.getKey());
                        updateScholar.addBatch();
                    }
                    updateScholar.executeBatch();
                }
                lastFetchInstants.clear();
            }
        } catch (Exception e) {
            logger.catching(e);
        }
        return null;
    }
}
