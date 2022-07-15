package me.upa.discord;

import me.upa.sql.SqlTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.ExecutionException;

public class CreditTransferTask extends SqlTask<Void> {

    private static final Logger logger = LogManager.getLogger();

    private final CreditTransfer transfer;

    public CreditTransferTask(CreditTransfer transfer) {
        this.transfer = transfer;
    }

    @Override
    public Void execute(Connection connection) throws Exception {
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("UPDATE members SET credit = credit - ? WHERE member_id = ?;")) {
                ps.setInt(1, transfer.getAmount());
                ps.setLong(2, transfer.getUpaMember().getMemberId());
                if (ps.executeUpdate() != 1) {
                    logger.warn("Could not update sender in database.");
                    connection.rollback();
                    return null;
                }
            }

            try (PreparedStatement ps = connection.prepareStatement("UPDATE members SET credit = credit + ? WHERE member_id = ?;")) {
                ps.setInt(1, transfer.getAmount());
                ps.setLong(2, transfer.getReceiver().getMemberId());
                if (ps.executeUpdate() != 1) {
                    logger.warn("Could not update receiver in database.");
                    connection.rollback();
                    return null;
                }
            }
            connection.commit();
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            connection.setAutoCommit(true);
        }
    }
}
