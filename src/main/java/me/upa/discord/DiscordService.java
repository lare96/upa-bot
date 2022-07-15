package me.upa.discord;

import com.google.common.util.concurrent.AbstractIdleService;
import me.upa.discord.command.AdminPanelCommand;
import me.upa.util.Throttler;
import me.upa.UpaBot;
import me.upa.discord.CreditTransaction.CreditTransactionType;
import me.upa.discord.command.AccountCommands;
import me.upa.discord.command.AdminCommands;
import me.upa.discord.command.PacCommands;
import me.upa.discord.command.EventCommands;
import me.upa.discord.command.ScholarshipCommand;
import me.upa.discord.command.StatisticsCommand;
import me.upa.fetcher.DataFetcherManager;
import me.upa.game.City;
import me.upa.game.CityCollection;
import me.upa.game.Sale;
import me.upa.service.SalesProcessorService;
import me.upa.selector.CityPropertySelector;
import me.upa.selector.PropertySelector;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * A service that starts and manages the Discord bot.
 *
 * @author lare96
 */
public final class DiscordService extends AbstractIdleService {

    private static final Logger logger = LogManager.getLogger();

    /**
     * The slash command throttler.
     */
    public static final Throttler THROTTLER = new Throttler();

    /**
     * The pool for asynchronous commands.
     */
    private static final ExecutorService commandPool = Executors.newCachedThreadPool();

    /**
     * The decimal formatter.
     */
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

    /**
     * The price formatter.
     */
    public static final NumberFormat COMMA_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    /**
     * The date-time formatter.
     */
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    /**
     * The purple color constant.
     */
    private static final Color PURPLE = new Color(102, 0, 153);

    /**
     * The gold color constant.
     */
    private static final Color GOLD = new Color(255, 223, 0);

    /**
     * Previous sent notifications to avoid duplicates.
     */
    private final Map<String, Integer> sentNotifications = new ConcurrentHashMap<>();

    /**
     * The counted duplicates.
     */
    private final AtomicInteger duplicates = new AtomicInteger();

    /**
     * The logged in Discord bot.
     */
    private JDA bot;

    /**
     * The statistics command event listener.
     */
    private final StatisticsCommand statisticsCommand = new StatisticsCommand();

    /**
     * The scholarship command event listener.
     */
    private final ScholarshipCommand scholarshipCommand = new ScholarshipCommand();

    /**
     * The invite tracker event listener.
     */
    private final InviteTracker inviteTracker = new InviteTracker();

    /**
     * The spark train message listener.
     */
    private final SparkTrainMessageListener sparkTrain = new SparkTrainMessageListener();

    @Override
    protected void startUp() throws Exception {
        bot = JDABuilder.createDefault("OTU2ODcxNjc5NTg0NDAzNTI2.Yj2iMg.jCRKAe8o6u1XppSbS4uV_eJRPxI").enableIntents(GatewayIntent.GUILD_WEBHOOKS).
                addEventListeners(sparkTrain, inviteTracker, scholarshipCommand, new RulesButtonListener(), new FaqButtonListener(), new AdminPanelCommand(),
                        new CreditViewerContextMenu(), new PacLotteryButtonListener(), new AdminCommands(), new AccountCommands(),
                        new EventCommands(), new PacCommands(), statisticsCommand).build().awaitReady();
    }

    @Override
    protected void shutDown() throws Exception {
        bot().shutdown();
    }

    public void updateCommands() {
        Guild guild = guild();
        guild.updateCommands().queue();
        guild.upsertCommand(Commands.user("View PAC")).queue();
        guild.upsertCommand(Commands.user("Tip 250 PAC")).queue();
        guild.upsertCommand(Commands.user("Give PAC [Admin]")).queue();

        guild.upsertCommand(Commands.slash("scholarships", "All scholarship related commands.")).queue();

        guild.upsertCommand(Commands.slash("pac", "All commands related to PAC (Property Advisor Credits).").
                addSubcommands(new SubcommandData("daily", "Claim your daily free PAC. Resets at 8:00am UTC everyday."),
                        new SubcommandData("send", "Send your PAC to another UPA member."). // TODO remove
                                addOption(OptionType.USER, "member", "The member to send PAC to.", true).
                                addOption(OptionType.STRING, "amount", "The amount of PAC to send.", true).
                                addOption(OptionType.STRING, "reason", "The reason for sending PAC.", true),
                        new SubcommandData("store", "Purchase PAC using a variety of methods, or redeem PAC for rewards."))).queue();

        guild.upsertCommand(Commands.slash("admin", "All administrative commands.").
                addSubcommands(new SubcommandData("panel", "View the admin panel."),
                        new SubcommandData("give_credit", "Give PAC to another member.").
                                addOption(OptionType.USER, "member", "The member to send to.", true).
                                addOption(OptionType.INTEGER, "amount", "The amount of PAC to send.", true).
                                addOption(OptionType.STRING, "reason", "The reason for sending PAC.", true),
                        new SubcommandData("force_link", "Forcibly link a user with UPA using Upland's public API.").
                                addOption(OptionType.STRING, "in_game_name", "Their in-game name. Not case sensitive.", true).
                                addOption(OptionType.USER, "member", "The member to apply this to.", true),
                        new SubcommandData("send_message", "Force the bot to send a message to the channel you're entering the command on.").
                                addOption(OptionType.STRING, "message", "The message to send.", true))).queue();

        guild.upsertCommand("statistics", "View common statistics related to the Hollis, Queens node.").queue();
        guild.upsertCommand("account", "View data related to your UPA account, or become a member.").queue();

      /*  guild.getTextChannelById(956791879867977738L).
                retrieveMessageById(995401229754703883L).complete().editMessage(new MessageBuilder().append("Select an option.").setActionRows(ActionRow.of(
                        Button.of(ButtonStyle.PRIMARY, "member_guidelines", "Member guidelines", Emoji.fromUnicode("U+1F4C3")),
                        Button.of(ButtonStyle.PRIMARY, "st_guidelines", "Spark train guidelines", Emoji.fromUnicode("U+1F682"))
                )).build()).queue();
        guild.getTextChannelById(956799638260830238L).
                retrieveMessageById(996376842053374012L).complete().editMessage(new MessageBuilder().append("What would you like to know?").setActionRows(ActionRow.of(
                        Button.of(ButtonStyle.PRIMARY, "getting_started", "Getting started", Emoji.fromUnicode("U+1F44B")),
                        Button.of(ButtonStyle.PRIMARY, "joining_spark_train", "Joining the spark train", Emoji.fromUnicode("U+1F686")),
                        Button.of(ButtonStyle.PRIMARY, "understanding_pac", "Understanding PAC", Emoji.fromUnicode("U+1F4B3")),
                        Button.of(ButtonStyle.PRIMARY, "command_list", "Command list", Emoji.fromUnicode("U+1F4CB")),
                        Button.of(ButtonStyle.PRIMARY, "videos", "Videos", Emoji.fromUnicode("U+1F3A5"))
                )).build()).queue();*/
    }

    public void sendBotRequestsMsg(UpaMember requester, String assistanceWith, Consumer<Message> onSuccess) {
        UpaBot.getDiscordService().guild().getTextChannelById(984551707176480778L).sendMessageEmbeds(
                new EmbedBuilder()
                        .setDescription("<@" + requester.getMemberId() + "> needs assistance with " + assistanceWith + ".")
                        .setFooter("Note: Marking a request as completed will automatically award/take away PAC!")
                        .setColor(Color.RED).build()
        ).setActionRow(
                Button.of(ButtonStyle.PRIMARY, "assign_yourself", "Assign yourself", Emoji.fromUnicode("U+1F64B")),
                Button.of(ButtonStyle.PRIMARY, "mark_completed", "Request handled", Emoji.fromUnicode("U+2705"))).queue(onSuccess);
    }

    public void sendUpxForPacMsg(UpaMember requester, int amount, int cost, Consumer<Message> onSuccess) {
        UpaBot.getDiscordService().guild().getTextChannelById(984551707176480778L).sendMessageEmbeds(
                new EmbedBuilder()
                        .setDescription("<@" + requester.getMemberId() + "> needs assistance with buying " + amount + " PAC for " + cost + " UPX.")
                        .setFooter("Note: Press reactions to mark as completed or assign yourself.")
                        .setColor(Color.RED).build()
        ).setActionRow(
                Button.of(ButtonStyle.PRIMARY, "assign_yourself", "Assign yourself", Emoji.fromUnicode("U+1F64B")),
                Button.of(ButtonStyle.PRIMARY, "mark_completed", "Request handled", Emoji.fromUnicode("U+2705"))).queue(onSuccess);
    }

    public void sendPropertyForPacMsg(UpaMember requester, UpaPoolProperty property, Consumer<Message> onSuccess) {
        UpaBot.getDiscordService().guild().getTextChannelById(984551707176480778L).sendMessageEmbeds(
                new EmbedBuilder()
//                        .setDescription("<@" + requester.getMemberId() + "> needs assistance with " + assistanceWith + ".")
                        .setFooter("Note: Press reactions to mark as completed or assign yourself.")
                        .setColor(Color.RED).build()
        ).setActionRow(
                Button.of(ButtonStyle.PRIMARY, "assign_yourself", "Assign yourself", Emoji.fromUnicode("U+1F64B")),
                Button.of(ButtonStyle.PRIMARY, "mark_completed", "Request handled", Emoji.fromUnicode("U+2705"))).queue(onSuccess);
    }

    public void sendFeedbackOrBugMsg(UpaMember requester, String subject, String description, Consumer<Message> onSuccess) {
        UpaBot.getDiscordService().guild().getTextChannelById(984551707176480778L).sendMessageEmbeds(
                new EmbedBuilder().setTitle(subject)
                        .addField("Sent by", "<@" + requester.getMemberId() + ">", false)
                        .setDescription(description)
                        .setFooter("Feel free to award PAC for useful feedback.")
                        .setColor(Color.RED).build()
        ).setActionRow(
                Button.of(ButtonStyle.PRIMARY, "mark_acknowledged", "Acknowledged", Emoji.fromUnicode("U+2705"))).queue(onSuccess);
    }

    public void execute(Runnable r) {
        commandPool.execute(r);
    }

    public void sendCreditMessage(CreditTransaction transaction) {
        if (transaction.getTransactionType() == CreditTransactionType.DAILY) {
            requireNonNull(UpaBot.getDiscordService().guild().getTextChannelById("983628894919860234")).sendMessage(transaction.getReason()).queue();
        } else {
            Color color;
            if (transaction.getTransactionType() == CreditTransactionType.GIFTED) {
                color = PURPLE;
            } else if (transaction.getAmount() < 0) {
                color = Color.RED;
            } else if (transaction.getTransactionType() == CreditTransactionType.TRANSFER ||
                    transaction.getTransactionType() == CreditTransactionType.TIP) {
                color = Color.BLUE;
            } else {
                color = Color.GREEN;
            }
            requireNonNull(UpaBot.getDiscordService().guild().getTextChannelById("983628894919860234")).sendMessageEmbeds(new EmbedBuilder().
                    setDescription(transaction.getReason())
                    .setColor(color)
                    .build()).queue();
        }
    }

    public void sendCredit(List<CreditTransaction> transactions) {
        SqlConnectionManager.getInstance().execute(new SqlTask<Void>() {
            @Override
            public Void execute(Connection connection) throws Exception {
                try (PreparedStatement updateCredits = connection.prepareStatement("UPDATE members SET credit = credit + ? WHERE member_id = ?;")) {
                    for (CreditTransaction next : transactions) {
                        updateCredits.setInt(1, next.getAmount());
                        updateCredits.setLong(2, next.getUpaMember().getMemberId());
                        updateCredits.addBatch();
                    }
                    if (updateCredits.executeBatch().length != transactions.size()) {
                        throw new IllegalStateException("SQL batch update and transaction list size mismatch.");
                    }
                }
                return null;
            }
        }, success -> transactions.forEach(next -> {
            next.getUpaMember().getCredit().addAndGet(next.getAmount());
            next.onSuccess();
            sendCreditMessage(next);
          /*  if ((next.getTransactionType() == CreditTransactionType.PURCHASE ||
                    next.getTransactionType() == CreditTransactionType.REDEEM) && next instanceof BotRequestTransaction) {
                BotRequestTransaction transaction = (BotRequestTransaction) next;
                SqlConnectionManager.getInstance().execute(new BotRequestTask(transaction.getHandler().getKey(),
                        transaction.getUpaMember().getKey(), transaction.getNetUpx(), transaction.getAmount(), transaction.getAsset()));
            }*/
        }));
    }

    public void sendCredit(CreditTransaction transaction) {
        sendCredit(List.of(transaction));
    }

    /**
     * SendAddition a notification to the Discord server.
     *
     * @param notification The notification to send.
     */
    public void sendNotification(SaleNotification notification) {
        Sale sale = notification.getSale();
        City city = requireNonNull(DataFetcherManager.getCityMap().get(sale.getCityId()),
                "City [" + sale.getCityId() + "] does not exist for property ID [" + sale.getPropertyId() + "].");
        Guild guild = guild();
        String floorType = SalesProcessorService.SELECTOR.getClass() == CityPropertySelector.class ? "city" : "neighborhood";
        String marginPrice = COMMA_FORMAT.format(notification.getMarginPrice());
        String marginPercentage = DECIMAL_FORMAT.format(notification.getMarginPercentage());
        String price = COMMA_FORMAT.format(sale.getPrice());
        String mintPrice = COMMA_FORMAT.format(sale.getMintPrice());
        String markup = COMMA_FORMAT.format(sale.getMarkup());
        String size = COMMA_FORMAT.format(sale.getSize());
        CityCollection collection = DataFetcherManager.getCollectionMap().get(sale.getCollectionName());
        int rarity = collection == null ? 0 : collection.getCategory();
        Color color = Color.BLUE;
        switch (rarity) {
            case 2:
                color = PURPLE;
                break;
            case 3:
                color = GOLD;
                break;
            case 4:
                color = Color.RED;
                break;
            case 5:
                color = Color.YELLOW;
                break;
        }
        int cityFloor = PropertySelector.floorPrices.getOrDefault(city, -1);
        int cityMargin = cityFloor - sale.getPrice();
        double cityMarginPercentage = cityFloor < 1 ? 0 : 100 - ((double) sale.getPrice() * 100 / (double) cityFloor);
        String sendAdditional = floorType.equals("neighborhood") && cityFloor > 0 && cityMargin > 0 ?
                "\n " + COMMA_FORMAT.format(cityMargin) + " UPX/" + DECIMAL_FORMAT.format(cityMarginPercentage) + "% below city floor" : "";
        Integer lastPrice = sentNotifications.putIfAbsent(sale.getAddress(), sale.getPrice());
        if (lastPrice != null && lastPrice == sale.getPrice()) {
            logger.info("Stopped a total of " + duplicates.incrementAndGet() + " duplicate notifications.");
            return;
        }
        switch (notification.getType()) {
            case SOFT_DEAL:
                var embed2 = new EmbedBuilder()
                        .setTitle(sale.getAddress() + " in " + city + " @ " + price + " UPX")
                        .setColor(color)
                        .addField("Price margin", marginPrice + " UPX/" + marginPercentage + "% below " + floorType + " floor" + sendAdditional, false)
                        .addField("Mint price", mintPrice + " UPX (" + markup + "% markup)", false)
                        .addField("Neighborhood", sale.getNeighborhoodName(), false)
                        .addField("Size", size + " UP2", false)
                        .addField("Property link", "https://play.upland.me/?prop_id=" + sale.getPropertyId(), false)
                        .setThumbnail("https://i.imgur.com/yNQfOcc.gif");
                requireNonNull(guild.getTextChannelById("966244394644701194")).sendMessageEmbeds(embed2.build()).queue();
                break;
            case HUGE_DEAL:
            case CITY_DEAL:
                var ratingStr = new StringBuilder();
                for (int loop = 0; loop < notification.getRating(); loop++) {
                    ratingStr.append("\uD83D\uDD25  ");
                }
                requireNonNull(guild.getTextChannelById("957833927693860885")).sendMessageEmbeds(new EmbedBuilder()
                        .setTitle(ratingStr + " " + sale.getAddress() + " in " + city + " @ " + price + " UPX")
                        .setColor(color)
                        .addField("Price margin", marginPrice + " UPX/" + marginPercentage + "% below " + floorType + " floor" + sendAdditional, false)
                        .addField("Mint price", mintPrice + " UPX (" + markup + "% markup)", false)
                        .addField("Neighborhood", sale.getNeighborhoodName(), false)
                        .addField("Size", size + " UP2", false)
                        .addField("Property link", "https://play.upland.me/?prop_id=" + sale.getPropertyId(), false)
                        .setThumbnail("https://i.imgur.com/yNQfOcc.gif")
                        .build()).queue();
                break;
            case UNDER_MINT:
                var underMintEmbed = new EmbedBuilder()
                        .setTitle(sale.getAddress() + " is being sold under mint price @ " + price + " UPX")
                        .setColor(color)
                        .addField("Price margin", marginPrice + " UPX/" + marginPercentage + "% below mint price", false)
                        .addField("Mint price", mintPrice + " UPX", false)
                        .addField("Neighborhood", sale.getNeighborhoodName(), false)
                        .addField("Size", size + " UP2", false)
                        .addField("Property link", "https://play.upland.me/?prop_id=" + sale.getPropertyId(), false)
                        .setThumbnail("https://i.imgur.com/yNQfOcc.gif");
                // channel.sendMessageEmbeds(underMintEmbed.build()).queue();
                requireNonNull(guild.getTextChannelById("957833886329602149")).
                        sendMessageEmbeds(underMintEmbed.setTitle(sale.getAddress() + " in " + city + " @ " + price + " UPX").build()).queue();
                break;
        }
    }


    /**
     * @return The logged in Discord bot. Will never be {@code null}.
     */
    public JDA bot() {
        if (bot == null) {
            throw new IllegalStateException("Discord bot is not logged in.");
        }
        return bot;
    }

    /**
     * @return The main guild the bot is responsible for.
     */
    public Guild guild() {
        return bot().getGuildById(956789347712126976L);
    }

    public StatisticsCommand getStatisticsCommand() {
        return statisticsCommand;
    }

    public ScholarshipCommand getScholarshipCommands() {
        return scholarshipCommand;
    }

    public InviteTracker getInviteTracker() {
        return inviteTracker;
    }

    public SparkTrainMessageListener getSparkTrain() {
        return sparkTrain;
    }
}
