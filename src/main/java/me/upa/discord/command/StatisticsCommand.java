package me.upa.discord.command;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import me.upa.UpaBotContext;
import me.upa.discord.DiscordService;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaProperty;
import me.upa.service.DatabaseCachingService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StatisticsCommand extends ListenerAdapter {
    private static final int HOLLIS_PROPERTIES = 4383;

    public StatisticsCommand(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    public static final class StatisticsData {
        private final int memberCount;
        private final int propertyCount;
        private final double hollisPercentOwned;
        private final int completedBuildings;
        private final int inProgressBuildings;
        private final Map<Long, Integer> topOwners;

        public StatisticsData(int memberCount, int propertyCount, double hollisPercentOwned, int completedBuildings, int inProgressBuildings, Map<Long, Integer> topOwners) {
            this.memberCount = memberCount;
            this.propertyCount = propertyCount;
            this.hollisPercentOwned = hollisPercentOwned;
            this.completedBuildings = completedBuildings;
            this.inProgressBuildings = inProgressBuildings;
            this.topOwners = topOwners;
        }

        public int getMemberCount() {
            return memberCount;
        }

        public int getPropertyCount() {
            return propertyCount;
        }

        public double getHollisPercentOwned() {
            return hollisPercentOwned;
        }

        public int getCompletedBuildings() {
            return completedBuildings;
        }

        public int getInProgressBuildings() {
            return inProgressBuildings;
        }

        public Map<Long, Integer> getTopOwners() {
            return topOwners;
        }
    }
    /**
     * The context.
     */
    private final UpaBotContext ctx;
    private volatile Instant lastUpdated = Instant.now().minus(2, ChronoUnit.HOURS);
    private volatile StatisticsData statisticsData;

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("statistics")) {
            event.deferReply().queue();
            Instant refreshAt = lastUpdated.plus(30, ChronoUnit.MINUTES);
            if (Instant.now().isAfter(refreshAt)) {
                StatisticsData statistics = buildStatistics();
                statisticsData = statistics;
                event.getHook().sendMessage(buildMessage(statistics)).queue();
                lastUpdated = Instant.now();
            } else {
                event.getHook().sendMessage(buildMessage(statisticsData)).queue();
            }
        }
    }

    public void load() {
        statisticsData = buildStatistics();
        lastUpdated = Instant.now();
    }

    private StatisticsData buildStatistics() {
        DatabaseCachingService databaseCaching = ctx.databaseCaching();
        int memberCount = 0;
        int propertyCount = 0;
        double hollisPercentOwned = 0;
        int completedBuildings = 0;
        int inProgressBuildings = 0;
        Map<Long, Integer> topOwners = new LinkedHashMap<>();
        Map<Integer, Long> keys = new HashMap<>();
        Multiset<Long> ownerCount = HashMultiset.create();

        for (UpaMember next : databaseCaching.getMembers().values()) {
            memberCount++;
            keys.put(next.getKey(), next.getMemberId());
        }
        for (UpaProperty next : databaseCaching.getProperties().values()) {
            Long discordId = keys.get(next.getMemberKey());
            propertyCount++;
            String buildStatus = next.getBuildStatus().get();
            if (buildStatus.equals("In progress"))
                inProgressBuildings++;
            else if (buildStatus.equals("Completed"))
                completedBuildings++;
            if (discordId != null) {
                ownerCount.add(discordId);
            }
        }


        while (!ownerCount.isEmpty()) {
            Long discordId = null;
            int highest = -1;
            Iterator<Entry<Long>> it = ownerCount.entrySet().iterator();
            while (it.hasNext()) {
                Entry<Long> next = it.next();
                if (next.getCount() > highest) {
                    highest = next.getCount();
                    discordId = next.getElement();
                }
            }
            if (discordId != null) {
                topOwners.put(discordId, highest);
                ownerCount.remove(discordId, highest);
            }
        }



        double propertyCountD = propertyCount;
        double hollisCountD = HOLLIS_PROPERTIES;
        hollisPercentOwned = propertyCountD * 100 / hollisCountD;
        return new StatisticsData(memberCount, propertyCount, hollisPercentOwned, completedBuildings, inProgressBuildings, topOwners);
    }

    private String buildMessage(StatisticsData statistics) {
        DatabaseCachingService databaseCaching = ctx.databaseCaching();
        StringBuilder sb = new StringBuilder("```\n");
        sb.append("Total members: ").append(statistics.memberCount).append('\n').
                append("Total properties: ").append(statistics.propertyCount).append('\n').
                append("Ownership of Hollis, Queens: ").append(DiscordService.DECIMAL_FORMAT.format(statistics.hollisPercentOwned)).append("% (").append(statistics.propertyCount).append('/').append(HOLLIS_PROPERTIES).append(')').append('\n').
                append("Completed buildings: ").append(statistics.completedBuildings).append('\n').
                append("In progress buildings: ").append(statistics.inProgressBuildings).append("\n\n\n").
                append("Top 10 Members\n");
        int place = 1;
        for (var next : statistics.topOwners.entrySet()) {
            UpaMember upaMember = databaseCaching.getMembers().get(next.getKey());
            if (upaMember == null) {
                continue;
            }
            sb.append(place++).append(". ").append('@').
                    append(upaMember.getDiscordName()).
                    append(" (").append(next.getValue()).
                    append(" properties, ").
                    append(DiscordService.COMMA_FORMAT.format(upaMember.getTotalUp2().get())).
                    append(" total UP2)\n\n");
            if (place > 10) {
                break;
            }
        }
        sb.append("```");
        return sb.toString();
    }

    public StatisticsData getStatisticsData() {
        return statisticsData;
    }
}