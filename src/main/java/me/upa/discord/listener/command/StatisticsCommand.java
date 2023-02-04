package me.upa.discord.listener.command;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import me.upa.UpaBotContext;
import me.upa.discord.DiscordService;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaProperty;
import me.upa.game.Node;
import me.upa.service.DatabaseCachingService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StatisticsCommand extends ListenerAdapter {

    public StatisticsCommand(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    public static final class StatisticsData {
        private final int memberCount;


        private final int completedBuildings;
        private final int inProgressBuildings;
        private final Map<Long, Integer> topOwners;

        private final Multiset<Node> propertyCountSet;
        private final Map<Node, Double> percentOwnedMap;

        public StatisticsData(int memberCount, int completedBuildings, int inProgressBuildings, Map<Long, Integer> topOwners,
                              Multiset<Node> propertyCountSet, Map<Node, Double> percentOwnedMap) {
            this.memberCount = memberCount;
            this.completedBuildings = completedBuildings;
            this.inProgressBuildings = inProgressBuildings;
            this.topOwners = topOwners;
            this.propertyCountSet = propertyCountSet;
            this.percentOwnedMap = percentOwnedMap;
        }

        public int getMemberCount() {
            return memberCount;
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

        public Multiset<Node> getPropertyCountSet() {
            return propertyCountSet;
        }

        public Map<Node, Double> getPercentOwnedMap() {
            return percentOwnedMap;
        }
    }

    public static final class NodeStatisticsData {
        private final int nodeMembers;
        private final int properties;
        private final int completedBuildings;
        private final Map<Long, Integer> topOwners;

        public NodeStatisticsData(int nodeMembers, int properties, int completedBuildings, Map<Long, Integer> topOwners) {
            this.nodeMembers = nodeMembers;
            this.properties = properties;
            this.completedBuildings = completedBuildings;
            this.topOwners = topOwners;
        }

        public int getNodeMembers() {
            return nodeMembers;
        }

        public int getProperties() {
            return properties;
        }

        public int getCompletedBuildings() {
            return completedBuildings;
        }

        public Map<Long, Integer> getTopOwners() {
            return topOwners;
        }
    }
    public static final class NodeStatisticsRecord {
        private volatile Instant lastUpdated = Instant.now().minus(2, ChronoUnit.HOURS);
        private volatile NodeStatisticsData data;
    }

    /**
     * The context.
     */
    private final UpaBotContext ctx;
    private volatile Instant lastUpdated = Instant.now().minus(2, ChronoUnit.HOURS);
    private volatile StatisticsData statisticsData;

    private final Map<Node, NodeStatisticsRecord> statsMap = new ConcurrentHashMap<>();

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
            } else if (event.getName().equals("node")) {
                Node node = Node.valueOf(event.getOptions().get(0).getAsString());
                NodeStatisticsRecord record = statsMap.computeIfAbsent(node, k -> new NodeStatisticsRecord());
                Instant refreshAt = record.lastUpdated.plus(30, ChronoUnit.MINUTES);
                if (Instant.now().isAfter(refreshAt)) {
                    NodeStatisticsData statistics = buildStatistics(node);
                    record.data = statistics;
                    event.reply(buildMessage(node, statistics)).queue();
                    record.lastUpdated = Instant.now();
                } else {
                    event.reply(buildMessage(node, record.data)).queue();
                }
            }
    }

    public void load() {
        statisticsData = buildStatistics();
        lastUpdated = Instant.now();
    }
    private NodeStatisticsData buildStatistics(Node node) {
            DatabaseCachingService databaseCaching = ctx.databaseCaching();
            Set<Integer> members = new HashSet<>();
            int properties = 0;
            int completedBuildings = 0;
            Map<Long, Integer> topOwners = new LinkedHashMap<>();
            Map<Integer, Long> keys = new HashMap<>();
            Multiset<Long> ownerCount = HashMultiset.create();

            for (UpaMember next : databaseCaching.getMembers().values()) {
                if (!next.getActive().get()) {
                    continue;
                }
                keys.put(next.getKey(), next.getMemberId());
            }

            for (UpaProperty next : databaseCaching.getProperties().values()) {
                if (next.getNode() != node) {
                    continue;
                }
                members.add(next.getMemberKey());
                Long discordId = keys.get(next.getMemberKey());
                String buildStatus = next.getBuildStatus().get();
                properties++;
                if (buildStatus.equals("Completed"))
                    completedBuildings++;
                if (discordId != null) {
                    ownerCount.add(discordId);
                }
            }

            while (!ownerCount.isEmpty()) {
                Long discordId = null;
                int highest = -1;
                for (Entry<Long> next : ownerCount.entrySet()) {
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
            return new NodeStatisticsData(members.size(), properties, completedBuildings, topOwners);
    }

    private StatisticsData buildStatistics() {
        DatabaseCachingService databaseCaching = ctx.databaseCaching();
        int memberCount = 0;
        int completedBuildings = 0;
        int inProgressBuildings = 0;
        Multiset<Node> propertyCounts = HashMultiset.create();
        Map<Node, Double> percentOwned = new HashMap<>();
        Map<Long, Integer> topOwners = new LinkedHashMap<>();
        Map<Integer, Long> keys = new HashMap<>();
        Multiset<Long> ownerCount = HashMultiset.create();

        for (UpaMember next : databaseCaching.getMembers().values()) {
            if (!next.getActive().get()) {
                continue;
            }
            memberCount++;
            keys.put(next.getKey(), next.getMemberId());
        }

        for (UpaProperty next : databaseCaching.getProperties().values()) {
            Long discordId = keys.get(next.getMemberKey());
            propertyCounts.add(next.getNode());
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

        for (var entry : propertyCounts.entrySet()) {
            Node node = entry.getElement();
            int count = entry.getCount();
            double totalProperties = node.getTotalProperties();
            double percent = count * 100.0 / totalProperties;
            percentOwned.put(node, percent);
        }
        return new StatisticsData(memberCount, completedBuildings, inProgressBuildings, topOwners, propertyCounts, percentOwned);
    }

    private String buildMessage(StatisticsData statistics) {
        DatabaseCachingService databaseCaching = ctx.databaseCaching();
        int totalProperties = statistics.propertyCountSet.entrySet().stream().mapToInt(Entry::getCount).sum();
        StringBuilder sb = new StringBuilder("```\n");
        sb.append("Total members: ").append(statistics.memberCount).append('\n').
                append("Total properties: ").append(totalProperties).append('\n');
        for (var entry : statistics.propertyCountSet.entrySet()) {
            Node node = entry.getElement();
            Double percent = statistics.getPercentOwnedMap().get(node);
            if (percent == null) {
                continue;
            }
            int ownedProperties = entry.getCount();
            sb.append("Ownership of ").append(node.getDisplayName()).append(": ").
                    append(DiscordService.DECIMAL_FORMAT.format(percent)).
                    append("% (").append(ownedProperties).append('/').
                    append(node.getTotalProperties()).append(')').append('\n');

        }
        //  sb.append("Completed buildings: ").append(statistics.completedBuildings).append('\n').
        //         append("In progress buildings: ").append(statistics.inProgressBuildings).append("\n\n\n").
        sb.append("Top 10 Members\n");
        int place = 1;
        for (var next : statistics.topOwners.entrySet()) {
            UpaMember upaMember = databaseCaching.getMembers().get(next.getKey());
            if (upaMember == null || !upaMember.getActive().get()) {
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
    private String buildMessage(Node node, NodeStatisticsData statistics) {
        DatabaseCachingService databaseCaching = ctx.databaseCaching();
        StringBuilder sb = new StringBuilder("```\n");
        sb.append("Total "+node.getDisplayName()+" members: ").append(statistics.nodeMembers).append('\n').
                append("Owned properties: ").append(statistics.properties).append('\n').append("Completed structures: ").append(statistics.completedBuildings).append("\n\n");
        //  sb.append("Completed buildings: ").append(statistics.completedBuildings).append('\n').
        //         append("In progress buildings: ").append(statistics.inProgressBuildings).append("\n\n\n").
        sb.append("Top 10 Members\n");
        int place = 1;
        for (var next : statistics.topOwners.entrySet()) {
            UpaMember upaMember = databaseCaching.getMembers().get(next.getKey());
            if (upaMember == null || !upaMember.getActive().get()) {
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