package me.upa.service;

import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.AbstractScheduledService;
import me.upa.UpaBotContext;
import me.upa.discord.UpaMember;
import me.upa.discord.history.PacTransaction;
import me.upa.fetcher.PropertyDataFetcher;
import me.upa.game.CachedProperty;
import me.upa.game.Neighborhood;
import me.upa.game.Property;
import me.upa.sql.SqlConnectionManager;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static me.upa.discord.DiscordService.PURPLE;

public final class PropertyCachingMicroService extends AbstractScheduledService {
    // TODO prioritize things that dont need lookups. this should still be running
    public static void addCachedProperty(UpaBotContext ctx, Property property) {
        CachedProperty cachedProperty = ctx.databaseCaching().getPropertyLookup().get(property.getPropId());
        if (isInvalid(cachedProperty)) {
            properties.add(property);
        }
    }

    public static boolean isInvalid(CachedProperty cachedProperty) {
        return cachedProperty == null || cachedProperty.getAddress() == null || cachedProperty.getAddress().isEmpty() || cachedProperty.getNeighborhoodId() == -1 ||
                cachedProperty.getNeighborhoodId() == 0 || cachedProperty.getArea() == 0 || cachedProperty.getMintPrice() == 0;
    }

    private static final Logger logger = LogManager.getLogger();
    private final Queue<Long> lookups = new ConcurrentLinkedQueue<>();
    private static final Queue<Property> properties = new ConcurrentLinkedQueue<>();
    private final UpaBotContext ctx;

    private static final int LOOKUP_COUNT = 10;

    public PropertyCachingMicroService(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedDelaySchedule(1, 3, TimeUnit.MINUTES);
    }

    @Override
    public void startUp() throws Exception {
        Set<Long> lookupSet = new LinkedHashSet<>();
        for (CachedProperty property : ctx.databaseCaching().getPropertyLookup().values()) {
            if (property.getMintPrice() == 0 || property.getNeighborhoodId() == -1 || property.getArea() < 1) {
                lookupSet.add(property.getPropertyId());
            }
        }
        lookups.addAll(lookupSet);
        int mins = ((lookupSet.size() / LOOKUP_COUNT) * 15) / 60;
        logger.info("Preparing to synchronize {} lookups. (ETA. {} mins)", lookups.size(), mins);
    }

    private final Set<Long> comments = new HashSet<>();

    private enum State {
        IDLE,
        OPEN,
        PARSE
    }

    private List<Long> getMemberIdsFromDesc(String description) {
        List<Long> ids = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        State state = State.IDLE;
        for (char next : description.toCharArray()) {
            if (next == '<' && state == State.IDLE) {
                state = State.OPEN;
            } else if (next == '@' && state == State.OPEN) {
                state = State.PARSE;
            } else if (state == State.PARSE) {
                if (next == '>') {
                    state = State.IDLE;
                    ids.add(Long.parseLong(sb.toString()));
                    sb.setLength(0);
                } else if (Character.isDigit(next)) {
                    sb.append(next);
                } else {
                    logger.error("Character '{}' invalid in this section. Description: \"{}\"", next, description);
                }
            }
        }
        return ids;
    }

    @Override
    protected void runOneIteration() throws Exception {

       /* ctx.variables().pacTransactions().accessValue(transactions -> {
            TextChannel channel = ctx.discord().guild().getTextChannelById(983628894919860234L);
            MessageHistory history = new MessageHistory(channel);
            var success = history.retrievePast(100).complete();
            for (Message m : success) {
                if (!m.getEmbeds().isEmpty()) {
                    MessageEmbed embed = m.getEmbeds().get(0);
                    if (embed.getDescription() == null)
                        continue;
                    String str = embed.getDescription().replace("**", "");
                    int amount = getAmount(str);
                    if (Objects.equals(embed.getColor(), Color.RED)) {
                        amount = -amount;
                    } else if (Objects.equals(embed.getColor(), PURPLE)) {
                        if (str.contains("lost")) {
                            amount = -amount;
                        }
                    }
                    var mentions = getMemberIdsFromDesc(embed.getDescription());
                    if (mentions.size() > 0) {
                        long memberId = mentions.get(0);
                        if (memberId == 329352410982121482L && str.contains("through visiting a designated UPA property.")) {
                            m.delete().complete();
                            continue;
                        }
                        UpaMember mem = ctx.databaseCaching().getMembers().get(memberId);
                        String name = mem == null ? "null" : mem.getDiscordName().get();
                        str = str.replace("<@" + memberId + ">", name);
                        transactions.store(new PacTransaction(amount, str, memberId, m.getTimeCreated().toInstant()));
                        m.delete().complete();
                    }
                } else {
                    String desc = m.getContentRaw().replace("**", "");
                    int amount = getAmount(desc);
                    var mentions = m.getMentions().getUsers();
                    if (mentions.size() > 0) {
                        long memberId = mentions.get(0).getIdLong();
                        desc = desc.replace("<@" + memberId + ">", mentions.get(0).getName());
                        transactions.store(new PacTransaction(amount, desc, memberId, m.getTimeCreated().toInstant()));
                        m.delete().complete();
                    }
                }
            }
            return true;
        });*/
        if (ctx.propertySync().isIdle()) {
            for (int loop = 0; loop < LOOKUP_COUNT; loop++) {
                Long propertyId = lookups.poll();
                if (propertyId == null) {
                    break;
                }
                Property property = null;
                try {
                    property = PropertyDataFetcher.fetchPropertySynchronous(propertyId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if (property == null) {
                    continue;
                }
                properties.add(property);
            }
        }

        if (properties.size() > 0) {
            try (Connection connection = SqlConnectionManager.getInstance().take();
                 PreparedStatement ps = connection.prepareStatement("INSERT INTO property_lookup(property_id, address, area, neighborhood_id, city_id, mint_price) VALUES(?, ?, ?, ?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE neighborhood_id = ?, mint_price = ?, area = ?;")) {
                for (; ; ) {
                    Property property = properties.poll();
                    if (property == null) {
                        break;
                    }
                    Neighborhood neighborhood = PropertySynchronizationService.getNeighborhood(property);
                    int neighborhoodId = neighborhood == null ? -1 : neighborhood.getId();
                    ps.setLong(1, property.getPropId());
                    ps.setString(2, property.getFullAddress());
                    ps.setInt(3, property.getArea());
                    ps.setInt(4, neighborhoodId);
                    ps.setInt(5, property.getCityId());
                    ps.setInt(6, property.getMintPrice());
                    ps.setInt(7, neighborhoodId);
                    ps.setInt(8, property.getMintPrice());
                    ps.setInt(9, property.getArea());
                    logger.info("Property data cached {} ({}).", property.getFullAddress(), property.getPropId());
                    ps.addBatch();
                    ctx.databaseCaching().getPropertyLookup().put(property.getPropId(), new CachedProperty(property.getPropId(), property.getFullAddress(), property.getArea(), neighborhoodId, property.getCityId(), property.getMintPrice()));
                }
                ps.executeBatch();
            } catch (Exception e) {
                logger.catching(e);
            }
        }

    }

    private int getAmount(String origStr) {
        int amount = -1;
        boolean next = false;
        String[] words = origStr.split(" ");
        for (String str : words) {
            str = str.replace(",", "");
            str = str.replace(".", "");
            if (next) {
                if (str.equalsIgnoreCase("PAC")) {
                    return amount;
                } else {
                    next = false;
                    amount = -1;
                }
            }

            Integer value = Ints.tryParse(str);
            if (value != null) {
                amount = value;
                next = true;
            }
        }
        return amount;
    }

    public static Queue<Property> getProperties() {
        return properties;
    }
}
