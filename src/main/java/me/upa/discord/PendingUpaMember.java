package me.upa.discord;

import net.dv8tion.jda.api.entities.Member;

import javax.annotation.Nullable;

/**
 * A pending UPA member awaiting verification.
 *
 * @author lare96
 */
public final class PendingUpaMember {

    /**
     * The Discord member.
     */
    private final Member member;

    /**
     * The price they must set their property to.
     */
    private final int price;

    /**
     * Their in-game username.
     */
    private final String username;

    /**
     * Creates a new {@link PendingUpaMember}.
     */
    public PendingUpaMember(Member member, int price, String username) {
        this.member = member;
        this.price = price;
        this.username = username;
    }

    /**
     * @return The Discord member.
     */
    public Member getMember() {
        return member;
    }

    /**
     * @return The price they must set their property to.
     */
    public int getPrice() {
        return price;
    }

    /**
     * @return Their in-game username.
     */
    public String getUsername() {
        return username;
    }

}
