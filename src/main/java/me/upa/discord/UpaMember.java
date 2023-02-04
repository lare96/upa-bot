package me.upa.discord;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.util.concurrent.AtomicDouble;
import me.upa.game.Node;
import net.dv8tion.jda.api.entities.Member;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class UpaMember implements Serializable {
    private static final long serialVersionUID = 3704515143032833401L;
    private final int key;
    private final long memberId;
    private final String inGameName;


    private final AtomicReference<String> discordName = new AtomicReference<>();
    private final String blockchainAccountId;

    private final AtomicInteger netWorth = new AtomicInteger();
    private final AtomicInteger credit = new AtomicInteger();

    private final AtomicInteger hollisSsh = new AtomicInteger();

    private final AtomicInteger globalSsh = new AtomicInteger();


    private final AtomicInteger sends = new AtomicInteger();
    private final AtomicInteger sponsoredSends = new AtomicInteger();
    private final AtomicInteger referrals = new AtomicInteger();

    private final AtomicInteger minted = new AtomicInteger();
    private final AtomicReference<Instant> claimedDailyAt = new AtomicReference<>();

    private final AtomicBoolean sync = new AtomicBoolean();
    private final AtomicBoolean active = new AtomicBoolean();

    private final LocalDate joinDate;


    public UpaMember(int key, long memberId, String inGameName, String discordName, String blockchainAccountId, int netWorth, int credit, int hollisSsh, int globalSsh, int sends, int sponsoredSends, int referrals, int minted, Instant claimedDailyAt, boolean sync, boolean active, LocalDate joinDate) {
        this.key = key;
        this.memberId = memberId;
        this.inGameName = inGameName;
        this.discordName.set(discordName);
        this.blockchainAccountId = blockchainAccountId;
        this.netWorth.set(netWorth);
        this.credit.set(credit);
        this.hollisSsh.set(hollisSsh);
        this.globalSsh.set(globalSsh);
        this.sends.set(sends);
        this.sponsoredSends.set(sponsoredSends);
        this.referrals.set(referrals);
        this.minted.set(minted);
        this.claimedDailyAt.set(claimedDailyAt);
        this.sync.set(sync);
        this.active.set(active);
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

    public AtomicInteger getHollisSsh() {
        return hollisSsh;
    }

    public AtomicInteger getGlobalSsh() {
        return globalSsh;
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

    public AtomicInteger getMinted() {
        return minted;
    }

    public AtomicReference<Instant> getClaimedDailyAt() {
        return claimedDailyAt;
    }

    public AtomicBoolean getSync() {
        return sync;
    }

    public AtomicBoolean getActive() {
        return active;
    }

    public int getTotalSends() {
        return sponsoredSends.get() + sends.get();
    }

    private double getTotalHollisSsh() {
        return hollisSsh.get() + hollisSparkTrainSsh.get();
    }
    private double getTotalGlobalSsh() {
        return globalSsh.get() + globalSparkTrainSsh.get();
    }

    public double getTotalSsh(boolean global) {
        return global ? getTotalGlobalSsh() : getTotalHollisSsh();
    }
    public LocalDate getJoinDate() {
        return joinDate;
    }


    // temp attributes
    private final AtomicDouble hollisSparkTrainSsh = new AtomicDouble();
    private final AtomicDouble globalSparkTrainSsh = new AtomicDouble();

    private final AtomicInteger hollisSparkTrainPlace = new AtomicInteger();
    private final AtomicInteger globalSparkTrainPlace = new AtomicInteger();

    private final AtomicInteger totalUp2 = new AtomicInteger();

    private final AtomicReference<Member> pendingTransaction = new AtomicReference<>();

    private volatile Path pacHistoryKey;

    private final AtomicInteger listingBrowseSlot = new AtomicInteger();
    private volatile Node listingBrowseNode;

    private final AtomicDouble hollisSparkTrainStaked = new AtomicDouble();
    private final AtomicDouble globalSparkTrainStaked = new AtomicDouble();

    private final AtomicDouble hollisSparkTrainShGiven = new AtomicDouble();
    private final AtomicDouble globalSparkTrainShGiven = new AtomicDouble();


    public AtomicDouble getHollisSparkTrainSsh() {
        return hollisSparkTrainSsh;
    }

    public AtomicDouble getHollisSparkTrainShGiven() {
        return hollisSparkTrainShGiven;
    }

    public AtomicInteger getHollisSparkTrainPlace() {
        return hollisSparkTrainPlace;
    }

    public AtomicInteger getGlobalSparkTrainPlace() {
        return globalSparkTrainPlace;
    }

    public AtomicDouble getGlobalSparkTrainSsh() {
        return globalSparkTrainSsh;
    }

    public AtomicDouble getGlobalSparkTrainShGiven() {
        return globalSparkTrainShGiven;
    }

    public AtomicDouble getGlobalSparkTrainStaked() {
        return globalSparkTrainStaked;
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

    public AtomicInteger getListingBrowseSlot() {
        return listingBrowseSlot;
    }

    public void setListingBrowseNode(Node listingBrowseNode) {
        this.listingBrowseNode = listingBrowseNode;
    }

    public Node getListingBrowseNode() {
        return listingBrowseNode;
    }

    // TODO Make this into boolean global
    public AtomicDouble getHollisSparkTrainStaked() {
        return hollisSparkTrainStaked;
    }

    public double getTotalStaking() {
        return hollisSparkTrainStaked.get() + globalSparkTrainStaked.get();
    }
}
