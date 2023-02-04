package me.upa.sql;

import me.upa.game.Node;

import java.sql.Connection;
import java.sql.PreparedStatement;

public final class SshTransferTask extends SqlTask<Void> {

    private final long memberId;
    private final Node fromTrain;
    private final Node toTrain;
    private final int amount;

    public SshTransferTask(long memberId, Node fromTrain, Node toTrain, int amount) {
        this.memberId = memberId;
        this.fromTrain = fromTrain;
        this.toTrain = toTrain;
        this.amount = amount;
    }


    @Override
    public Void execute(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE members SET " + computeSetSql() + " WHERE member_id = ?;")) {
            ps.setInt(1, amount);
            ps.setInt(2, amount);
            ps.setLong(3, memberId);
            if (ps.executeUpdate() != 1) {
                throw new RuntimeException("SSH was not transferred.");
            }
        }
        return null;
    }

    private String computeSetSql() {
        String hollis = "hollis_ssh";
        String global = "global_ssh";
        String from = fromTrain == Node.HOLLIS ? hollis : global;
        String to = toTrain == Node.HOLLIS ? hollis : global;
        return from + " = " + from + " - ?," +
                to + " = " + to + " + ?";
    }
}
