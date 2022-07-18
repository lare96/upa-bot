package me.upa.discord.command;

import com.google.common.collect.ImmutableMap;
import me.upa.UpaBot;
import me.upa.discord.DiscordService;
import me.upa.discord.Scholar;
import me.upa.discord.UpaMember;
import me.upa.fetcher.DataFetcherManager;
import me.upa.fetcher.ProfileDataFetcher;
import me.upa.fetcher.UserPropertiesDataFetcher;
import me.upa.fetcher.UserPropertiesDataFetcher.UserProperty;
import me.upa.fetcher.VisitorsDataFetcher;
import me.upa.game.PropertyVisitor;
import me.upa.service.DatabaseCachingService;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the scholarship commands.
 *
 * @author lare96
 */
public final class ScholarshipCommand extends ListenerAdapter {

    public static final class LeaderboardMember implements Comparable<LeaderboardMember> {

        private final long memberId;
        private final String discordName;
        private final int totalSends;

        private LeaderboardMember(long memberId, String discordName, int totalSends) {
            this.memberId = memberId;
            this.discordName = discordName;
            this.totalSends = totalSends;
        }

        @Override
        public int compareTo(@NotNull ScholarshipCommand.LeaderboardMember o) {
            return Integer.compare(o.totalSends, totalSends);
        }

        public long getMemberId() {
            return memberId;
        }
    }

    private static final class SortedScholar implements Comparable<SortedScholar> {

        private final Scholar scholar;

        private SortedScholar(Scholar scholar) {
            this.scholar = scholar;
        }

        @Override
        public int compareTo(@NotNull ScholarshipCommand.SortedScholar o) {
            return Integer.compare(o.scholar.getNetWorth().get(), scholar.getNetWorth().get());
        }
    }

    private static final class ScholarComparator implements Comparator<Scholar> {

        @Override
        public int compare(Scholar o1, Scholar o2) {
            return Integer.compare(o2.getCompareValue(), o1.getCompareValue());
        }
    }

    private static final Logger logger = LogManager.getLogger();
    private static final ScholarComparator SCHOLAR_COMPARATOR = new ScholarComparator();
    private static final int CITY_TIER_COUNT = 5;
    private static ImmutableMap<String, Integer> CITY_TIERS = ImmutableMap.<String, Integer>builder().
            put("Chicago", 1).
            put("Los Angeles", 1).
            put("Manhattan", 1).
            put("San Francisco", 1).
            put("Bronx", 2).
            put("Nashville", 2).
            put("Santa Clara", 2).
            put("Queens", 2).
            put("Cleveland", 4).
            put("Kansas City", 4).
            put("New Orleans", 4).
            put("Oakland", 4).
            put("Rutherford", 4).
            put("Bakersfield", 5).
            put("Fresno", 5).
            put("Detroit", 5).
            put("Staten Island", 5).build();

    /**
     * A task sent when a scholar application is sent.
     */
    private static final class ApplyTask extends SqlTask<Void> {

        private final Scholar scholar;

        private ApplyTask(Scholar scholar) {
            this.scholar = scholar;
        }

        @Override
        public Void execute(Connection connection) throws Exception {
            try (PreparedStatement updateScholar = connection.prepareStatement("INSERT INTO scholars (address, property_id, discord_id, net_worth, last_fetch) VALUES (?, ?, ?, ?, ?);")) {
                updateScholar.setString(1, scholar.getAddress());
                updateScholar.setLong(2, scholar.getPropertyId());
                updateScholar.setLong(3, scholar.getMemberId());
                updateScholar.setInt(4, scholar.getNetWorth().get());
                updateScholar.setString(5, scholar.getLastFetchInstant().get().toString());
                if (updateScholar.executeUpdate() < 1) {
                    logger.warn("Scholar could not be sponsored.");
                    return null;
                }
            }
            return null;
        }
    }

    public static Button becomeAScholarButton() {
    return     Button.of(ButtonStyle.PRIMARY, "become_a_scholar", "Become a scholar", Emoji.fromUnicode("U+1F393"));
    }
    private volatile List<LeaderboardMember> members;

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("scholarships")) {
            event.reply("Select an action.").setEphemeral(true).addActionRow(
                    becomeAScholarButton(),
                    Button.of(ButtonStyle.PRIMARY, "view_scholar_list", "View scholar list", Emoji.fromUnicode("U+1F4CB")),
                    Button.of(ButtonStyle.PRIMARY, "view_leaderboard", "View leaderboard", Emoji.fromUnicode("U+1F947"))).queue();
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() != null) {
            switch (event.getButton().getId()) {
                case "become_a_scholar":
                    handleApply(event);
                    event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                    break;
                case "view_scholar_list":
                    handleView(event);
                    event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                    break;
                case "view_leaderboard":
                    handleLeaderboard(event);
                    event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                    break;
            }
        }
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getModalId().equals("confirm_apply_form")) {
            String inGameName = event.getValue("confirm_apply_form_name").getAsString().trim();
            if (inGameName.isBlank()) {
                event.reply("In-game name cannot be empty.").setEphemeral(true).queue();
                return;
            }
            long memberId = event.getMember().getIdLong();
            event.deferReply().setEphemeral(true).queue();
            var profileDataFetcher = new ProfileDataFetcher(inGameName);
            profileDataFetcher.fetch();
            profileDataFetcher.getTask().thenAccept(profile -> {
                event.getHook().setEphemeral(true);
                if (profile == null || profile == ProfileDataFetcher.DUMMY) {
                    event.getHook().editOriginal("Profile for in-game name '" + inGameName + "' cannot be found.").queue();
                    return;
                }
                int netWorth = profile.getNetWorth();
                if (profile.isJailed()) {
                    event.getHook().editOriginal("Your application has been rejected because you are currently jailed.").queue();
                } else if (netWorth >= 10_000) {
                    event.getHook().editOriginal("Your application has been rejected because you already have 10k networth.").queue();
                } else {
                    var userProperties = new UserPropertiesDataFetcher(profile.getOwnerUsername());
                    userProperties.fetch();
                    userProperties.getTask().thenAccept(properties -> {
                        try {
                            if (properties.isEmpty()) {
                                event.getHook().editOriginal("Your application has been rejected because you need to own at least one property.").queue();
                                return;
                            }
                            UserProperty selected = null;
                            UserProperty[] eligble = new UserProperty[CITY_TIER_COUNT - 1];
                            for (UserProperty next : properties.values()) {
                                String cityName = DataFetcherManager.getCityMap().get(next.getCityId()).getName();
                                int tier = CITY_TIERS.get(cityName);
                                if (tier == 1) {
                                    selected = next;
                                    break;
                                } else if (tier == 2) {
                                    eligble[0] = next;
                                } else if (tier == 3) {
                                    eligble[1] = next;
                                } else if (tier == 4) {
                                    eligble[2] = next;
                                } else if (tier == 5) {
                                    eligble[3] = next;
                                }
                            }
                            if (selected == null) {
                                for (UserProperty next : eligble) {
                                    if (next != null) {
                                        selected = next;
                                        break;
                                    }
                                }
                                if (selected == null) {
                                    event.getHook().editOriginal("Your application has been rejected because an eligible property could not be found. I will DM your info to <@220622659665264643> for manual addition to the scholarship program.").queue();
                                    event.getGuild().retrieveMemberById(220622659665264643L).queue(success ->
                                            success.getUser().openPrivateChannel().queue(message -> message.sendMessage("Manual addition for scholar table {" + inGameName + "}").queue()));
                                    return;
                                }
                            }
                            UserProperty finalSelected = selected;
                            var visitorsFetcher = new VisitorsDataFetcher(selected.getPropId());
                            List<PropertyVisitor> visitors = visitorsFetcher.waitUntilDone();
                            Instant lastVisitorInstant = Instant.now();
                            if (visitors.size() > 0) {
                                lastVisitorInstant = visitors.get(0).getVisitedAt();
                            }

                            DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
                            for (PropertyVisitor next : visitors) {
                                if (next.isPending()) {
                                    continue;
                                }
                               Long nextMemberId = databaseCaching.getMemberNames().inverse().get(next.getUsername());
                                if (nextMemberId == null) {
                                    continue;
                                }
                                UpaMember nextMember = databaseCaching.getMembers().get(nextMemberId);
                                if (nextMember == null) {
                                    continue;
                                }
                                nextMember.getSends().incrementAndGet();
                            }
                            Scholar scholar = new Scholar(profile.getOwnerUsername(), selected.getFullAddress(), selected.getPropId(), netWorth, memberId, false, lastVisitorInstant);
                            SqlConnectionManager.getInstance().execute(new ApplyTask(scholar),
                                    success -> {
                                        event.getHook().editOriginal("Your application has been accepted! Please set the visitor fee to the maximum amount at **" + finalSelected.getFullAddress() + "**. This will ensure you get the most sends possible!").queue();
                                        UpaBot.getDatabaseCachingService().getScholars().put(memberId, scholar);
                                    },
                                    failure -> {
                                        event.getHook().editOriginal("Your application could not be finalized! Please reach out to <@220622659665264643>.").queue();
                                        logger.error("Error finalizing scholarship application.", failure);
                                    });
                        } catch (Exception e) {
                            logger.catching(e);
                        }
                    });
                }
            });
        }
    }

    private void handleApply(ButtonInteractionEvent event) {
        long memberId = event.getMember().getIdLong();
        DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
        if (databaseCaching.getScholars().containsKey(memberId)) {
            event.reply("You are already a scholar.").setEphemeral(true).queue();
            return;

        }
        event.replyModal(Modal.create("confirm_apply_form", "Please enter your in-game name.").
                addActionRow(TextInput.create("confirm_apply_form_name", "In-game name", TextInputStyle.SHORT).
                        setRequired(true).setPlaceholder("rich_goat").build()).build()).queue();
    }

    private void handleView(ButtonInteractionEvent event) {
        DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
        if (databaseCaching.getScholars().isEmpty()) {
            event.reply("There are no scholars to list.").setEphemeral(true).queue();
            return;
        }
        event.deferReply().setEphemeral(true).queue();
        UpaBot.getDiscordService().execute(() -> {
            StringBuilder sb = new StringBuilder("Send your block explorer to scholar properties to earn PAC. Sending to the sponsored scholar will gives bonus PAC!\n```\n");
            synchronized (databaseCaching.getScholars()) {
                List<SortedScholar> all = databaseCaching.getScholars().values().stream().map(SortedScholar::new).sorted().collect(Collectors.toList());
                try {
                    for (SortedScholar sorted : all) {
                        Member member = event.getGuild().retrieveMemberById(sorted.scholar.getMemberId()).complete();
                        sb.append(sorted.scholar.getAddress()).append(" | ").
                                append("Net worth: ").append(DiscordService.COMMA_FORMAT.format(sorted.scholar.getNetWorth().get())).append(" UPX").append(" | ").
                                append(sorted.scholar.getUsername()).append(" | ").
                                append("@").append(member.getEffectiveName());
                        if (sorted.scholar.getSponsored().get()) {
                            sb.append(" (Sponsored)");
                        }
                        sb.append("\n\n");
                    }
                } catch (Exception e) {
                    logger.error("Could not list scholar.", e);
                }

            }
            sb.append("```");
            event.getHook().setEphemeral(true).editOriginal(sb.toString()).queue();
        });
    }

    private void handleLeaderboard(ButtonInteractionEvent event) {
        event.deferReply().setEphemeral(true).queue();

        StringBuilder sb = new StringBuilder("Top 10 Contributors!\n```\n");
        int place = 1;
        for (LeaderboardMember sorted : computeLeaderboard()) {
            sb.append(place++).append(". ").append('@').append(sorted.discordName).append(" (").append(sorted.totalSends).append(" total sends)");
            sb.append("\n\n");
            if (place > 10) {
                break;
            }
        }
        sb.append("```");
        event.getHook().setEphemeral(true).editOriginal(sb.toString()).queue();
    }

    public List<LeaderboardMember> computeLeaderboard() {
        return UpaBot.getDatabaseCachingService().getMembers().values().stream().
                filter(next -> next.getTotalSends() > 0).
                map(next -> new LeaderboardMember(next.getMemberId(), next.getDiscordName().get(), next.getTotalSends())).
                sorted().collect(Collectors.toList());
    }
}
