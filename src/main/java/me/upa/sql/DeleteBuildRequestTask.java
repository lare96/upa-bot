package me.upa.sql;

import me.upa.discord.UpaBuildRequest;
import net.dv8tion.jda.api.entities.Member;

import java.sql.Connection;
import java.sql.PreparedStatement;

public final class DeleteBuildRequestTask extends SqlTask<Void> {

    private final Member member;
    private final boolean global;

    public DeleteBuildRequestTask(Member member, boolean global) {
        this.member = member;
        this.global = global;
    }

    @Override
    public Void execute(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM build_requests WHERE member_id = ? AND global_train = ?;")) {
            ps.setLong(1, member.getIdLong());
            ps.setBoolean(2, global);
            if (ps.executeUpdate() != 1) {
                throw new RuntimeException("Build was not cancelled.");
            }
        }
        return null;
    }
}
