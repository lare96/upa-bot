package me.upa.discord;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.util.concurrent.AtomicDouble;
import net.dv8tion.jda.api.entities.Member;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class UpaMember {
    private final int key;
    private final long memberId;
    private final String inGameName;

    private final AtomicReference<String> discordName = new AtomicReference<>();
    private final String blockchainAccountId;

    private final AtomicInteger netWorth = new AtomicInteger();
    private final AtomicInteger credit = new AtomicInteger();

    private final AtomicInteger ssh = new AtomicInteger();

    private final AtomicInteger sends = new AtomicInteger();
    private final AtomicInteger sponsoredSends = new AtomicInteger();
    private final AtomicInteger referrals = new AtomicInteger();

    private final AtomicReference<Instant> claimedDailyAt = new AtomicReference<>();

    private final AtomicBoolean sync = new AtomicBoolean();

    private final LocalDate joinDate;


    public UpaMember(int key, long memberId, String inGameName, String discordName, String blockchainAccountId, int netWorth, int credit, int ssh, int sends, int sponsoredSends, int referrals, Instant claimedDailyAt, boolean sync, LocalDate joinDate) {
        this.key = key;
        this.memberId = memberId;
        this.inGameName = inGameName;
        this.discordName.set(discordName);
        this.blockchainAccountId = blockchainAccountId;
        this.netWorth.set(netWorth);
        this.credit.set(credit);
        this.ssh.set(ssh);
        this.sends.set(sends);
        this.sponsoredSends.set(sponsoredSends);
        this.referrals.set(referrals);
        this.claimedDailyAt.set(claimedDailyAt);
        this.sync.set(sync);
        this.joinDate = joinDate;

        pacHistoryKey = Paths.get(memberId + ".bin");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UpaMember upaMember = (UpaMember) o;
        return Objects.equal(memberId, upaMember.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(memberId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("inGameName", inGameName)
                .add("credit", credit)
                .toString();
    }

    public int getKey() {
        return key;
    }

    public long getMemberId() {
        return memberId;
    }

    public String getInGameName() {
        return inGameName;
    }

    public AtomicReference<String> getDiscordName() {
        return discordName;
    }

    public String getBlockchainAccountId() {
        return blockchainAccountId;
    }

    public AtomicInteger getNetWorth() {
        return netWorth;
    }

    public AtomicInteger getCredit() {
        return credit;
    }

    public AtomicInteger getSsh() {
        return ssh;
    }

    public AtomicInteger getSends() {
        return sends;
    }

    public AtomicInteger getSponsoredSends() {
        return sponsoredSends;
    }

    public AtomicInteger getReferrals() {
        return referrals;
    }

    public AtomicReference<Instant> getClaimedDailyAt() {
        return claimedDailyAt;
    }

    public AtomicBoolean getSync() {
        return sync;
    }

    public int getTotalSends() {
        return sponsoredSends.get() + sends.get();
    }

    public double getTotalSsh() {
        return ssh.get() + sparkTrainSsh.get();
    }
    public LocalDate getJoinDate() {
        return joinDate;
    }


    // temp attributes
    private final AtomicDouble sparkTrainSsh = new AtomicDouble();

    private final AtomicInteger sparkTrainPlace = new AtomicInteger();

    private final AtomicInteger totalUp2 = new AtomicInteger();

    private final AtomicReference<Member> pendingTransaction = new AtomicReference<>();

    private volatile Path pacHistoryKey;
    public AtomicDouble getSparkTrainSsh() {
        return sparkTrainSsh;
    }

    public AtomicInteger getSparkTrainPlace() {
        return sparkTrainPlace;
    }

    public AtomicInteger getTotalUp2() {
        return totalUp2;
    }

    public AtomicReference<Member> getPendingTransactionTarget() {
        return pendingTransaction;
    }

    public Path getPacHistoryKey() {
        return pacHistoryKey;
    }
}
