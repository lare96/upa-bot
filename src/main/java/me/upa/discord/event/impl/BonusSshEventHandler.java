package me.upa.discord.event.impl;

import com.google.common.util.concurrent.AtomicDouble;
import me.upa.UpaBotContext;
import me.upa.discord.DiscordService;
import me.upa.discord.UpaMember;
import me.upa.discord.event.UpaEvent;
import me.upa.discord.event.UpaEventHandler;
import me.upa.discord.event.UpaEventRarity;
import me.upa.service.DailyResetMicroService;
import me.upa.sql.SqlConnectionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BonusSshEventHandler extends UpaEventHandler {

    public static final class BonusSsh implements Serializable {
        private static final long serialVersionUID = 8483830835580962319L;
        private final AtomicDouble hollis = new AtomicDouble();
        private final AtomicDouble global = new AtomicDouble();

        public BonusSsh(double hollis, double global) {
            this.hollis.set(hollis);
            this.global.set(global);
        }

        public BonusSsh addSsh(double amt, double amtGlobal) {
            global.addAndGet(amtGlobal);
            hollis.addAndGet(amt);
            return this;
        }

        public double getHollis() {
            return hollis.get();
        }

        public double getGlobal() {
            return global.get();
        }
    }

    private static final Logger logger = LogManager.getLogger();
    private static final long serialVersionUID = 2701902036205666425L;
    private final Map<Long, BonusSsh> sshMap = new ConcurrentHashMap<>();
    private final Map<Long, BonusSsh> totalSshGained = new ConcurrentHashMap<>();

    @Override
    public void onStart(UpaBotContext ctx) {
        loadSsh(ctx);
    }

    @Override
    public void onEnd(UpaBotContext ctx) {
        loadSsh(ctx);
        try (Connection c = SqlConnectionManager.getInstance().take();
             PreparedStatement statement = c.prepareStatement("UPDATE members SET hollis_ssh = hollis_ssh + ?, global_ssh = global_ssh + ? WHERE member_id = ?;")) {
            for (var entry : totalSshGained.entrySet()) {
                try {
                    long memberId = entry.getKey();
                    BonusSsh ssh = entry.getValue();
                    int hollisSsh = (int) Math.floor(ssh.getHollis());
                    int globalSsh = (int) Math.floor(ssh.getGlobal());
                    UpaMember upaMember = ctx.databaseCaching().getMembers().get(memberId);
                    if (upaMember == null || !upaMember.getActive().get() || (hollisSsh <= 0 && globalSsh <= 0)) {
                        continue;
                    }
                    statement.setInt(1, hollisSsh);
                    statement.setInt(2, globalSsh);
                    statement.setLong(3, memberId);
                    statement.addBatch();
                    upaMember.getHollisSsh().addAndGet(hollisSsh);
                    upaMember.getGlobalSsh().addAndGet(globalSsh);
                    ctx.discord().guild().retrieveMemberById(upaMember.getMemberId()).queue(member ->
                            member.getUser().openPrivateChannel().queue(channel ->
                                    channel.sendMessageEmbeds(new EmbedBuilder().setDescription("Bonus SSH event concluded! All earnings have been credited to your account.").setColor(Color.GREEN).
                                            addField("Hollis bonus SSH", DiscordService.DECIMAL_FORMAT.format(ssh.getHollis()), false).
                                            addField("Global bonus SSH", DiscordService.DECIMAL_FORMAT.format(ssh.getGlobal()), false).
                                            addField("Total bonus SSH", DiscordService.DECIMAL_FORMAT.format(ssh.getHollis() + ssh.getGlobal()), false).build()
                                    ).queue()));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            statement.executeBatch();
        } catch (SQLException e) {
            logger.catching(e);
        }
        totalSshGained.clear();
        sshMap.clear();
    }

    @Override
    public UpaEventRarity rarity() {
        return UpaEventRarity.COMMON;
    }

    @Override
    public String name() {
        return "Bonus SSH";
    }

    @Override
    public int durationDays() {
        return 4;
    }

    @Override
    public StringBuilder buildMessageContent() {
        return new StringBuilder().append("For the duration of this event\n\n").
                append("- You will earn 25% more SSH when staking spark on the <#963108957784772659> train. You will earn 15% more SSH when staking spark on the <#1026633109367685131> train. Calculated daily and awarded at the end of the event.\n").
                append("- You will get 25% more SSH when redeemed from <#1000173221326364722>");
    }

    @Override
    public List<ActionRow> buildMessageComponents() {
        return List.of(ActionRow.of(
                Button.of(ButtonStyle.PRIMARY, "view_bonus_ssh", "Check your bonus SSH", Emoji.fromUnicode("U+1F50D")))
        );
    }


    public void loadSsh(UpaBotContext ctx) {
        if (sshMap.isEmpty()) {
            for (UpaMember upaMember : ctx.databaseCaching().getMembers().values()) {
                if (!upaMember.getActive().get()) {
                    continue;
                }
                sshMap.put(upaMember.getMemberId(), new BonusSsh(upaMember.getHollisSparkTrainShGiven().get(),
                        upaMember.getGlobalSparkTrainShGiven().get()));
            }
        } else {
            boolean active = UpaEvent.isActive(ctx, BonusSshEventHandler.class);
            for (UpaMember upaMember : ctx.databaseCaching().getMembers().values()) {
                if (!upaMember.getActive().get()) {
                    continue;
                }
                double newShGiven = upaMember.getHollisSparkTrainShGiven().get();
                double newShGivenGlobal = upaMember.getGlobalSparkTrainShGiven().get();
                if (newShGiven == 0 && newShGivenGlobal == 0) {
                    continue;
                }
                BonusSsh oldShGiven = sshMap.put(upaMember.getMemberId(), new BonusSsh(newShGiven, newShGivenGlobal));
                if (oldShGiven == null) {
                    continue;
                }
                double shGivenDiff = newShGiven - oldShGiven.getHollis();
                double shGivenDiffGlobal = newShGivenGlobal - oldShGiven.getGlobal();
                if (shGivenDiff < 0 && shGivenDiffGlobal < 0) {
                    continue;
                }
                double bonusShGiven = Math.floor(shGivenDiff * 0.25);
                double bonusShGivenGlobal = Math.floor(shGivenDiffGlobal * 0.15);
                if (active) {
                    totalSshGained.compute(upaMember.getMemberId(), (key, old) -> {
                        if (old == null)
                            return new BonusSsh(bonusShGiven, bonusShGivenGlobal);
                        return old.addSsh(bonusShGiven, bonusShGivenGlobal);
                    });
                }
            }
        }
        ctx.variables().event().save();
    }

    public void printBonusSsh(UpaBotContext ctx) {
        logger.error("SSH MAP");
        for (var entry : sshMap.entrySet()) {
            logger.error("[" + ctx.discord().guild().retrieveMemberById(entry.getKey()).complete().getEffectiveName() + "] = " + entry.getValue());
        }
        logger.error("TOTAL SSH MAP");
        for (var entry : totalSshGained.entrySet()) {
            logger.error("[" + ctx.discord().guild().retrieveMemberById(entry.getKey()).complete().getEffectiveName() + "] = " + entry.getValue());
        }
    }

    public Map<Long, BonusSsh> getSshMap() {
        return sshMap;
    }

    public Map<Long, BonusSsh> getTotalSshGained() {
        return totalSshGained;
    }
}
