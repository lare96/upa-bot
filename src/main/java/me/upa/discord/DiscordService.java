package me.upa.discord;

import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import me.upa.UpaBotContext;
import me.upa.discord.event.UpaEventDiscordListener;
import me.upa.discord.history.PacTransaction;
import me.upa.discord.listener.buttons.CasinoButtonListener;
import me.upa.discord.listener.buttons.FaqButtonListener;
import me.upa.discord.listener.buttons.NewbieGuideButtonListener;
import me.upa.discord.listener.buttons.PacLotteryButtonListener;
import me.upa.discord.listener.buttons.SupportButtonListener;
import me.upa.discord.listener.command.AccountCommands;
import me.upa.discord.listener.command.AdminCommands;
import me.upa.discord.listener.command.AdminPanelCommand;
import me.upa.discord.listener.command.CreditViewerContextMenu;
import me.upa.discord.listener.command.PacCommands;
import me.upa.discord.listener.command.StatisticsCommand;
import me.upa.discord.listener.credit.CreditTransaction;
import me.upa.discord.listener.credit.CreditTransaction.CreditTransactionType;
import me.upa.fetcher.DataFetcherManager;
import me.upa.game.City;
import me.upa.game.CityCollection;
import me.upa.game.Sale;
import me.upa.game.selector.CityPropertySelector;
import me.upa.game.selector.PropertySelector;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.Command.Type;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * A service that starts and manages the Discord bot.
 *
 * @author lare96
 */
public final class DiscordService extends AbstractIdleService {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger();

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
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy").
            withLocale(Locale.US).withZone(ZoneId.from(ZoneOffset.UTC));

    /**
     * The purple color constant.
     */
    public static final Color PURPLE = new Color(102, 0, 153);

    /**
     * The gold color constant.
     */
    private static final Color GOLD = new Color(255, 223, 0);

    /**
     * The context.
     */
    private final UpaBotContext ctx;

    /**
     * The pool for asynchronous commands.
     */
    private final ExecutorService commandPool = Executors.newCachedThreadPool();
    private final ListeningScheduledExecutorService schedulePool = MoreExecutors.listeningDecorator(Executors.newSingleThreadScheduledExecutor());
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
    private final StatisticsCommand statisticsCommand;

    /**
     * The spark train message listener.
     */
    private final SparkTrainMessageListener sparkTrain;

    /**
     * Creates a new {@link UpaBotContext}.
     *
     * @param ctx The context.
     */
    public DiscordService(UpaBotContext ctx) {
        this.ctx = ctx;
        statisticsCommand = new StatisticsCommand(ctx);
        sparkTrain = new SparkTrainMessageListener(ctx);
    }

    @Override
    protected void startUp() throws Exception {
        //  bot = JDABuilder.createDefault("OTk5MjMwOTk3NDgyNzA0ODk2.Gk9FKr.-6ThQ9Xc4nJiBEQeFtFFrGUIIAf-Uz3jxCYjk8").build();
        bot = JDABuilder.createDefault("OTU2ODcxNjc5NTg0NDAzNTI2.Yj2iMg.jCRKAe8o6u1XppSbS4uV_eJRPxI").
                enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_WEBHOOKS, GatewayIntent.GUILD_MEMBERS).
                setMemberCachePolicy(MemberCachePolicy.ALL).
                addEventListeners(sparkTrain, new ReferralMessageListener(ctx), new FaqButtonListener(ctx), new AdminPanelCommand(ctx),
                        new CreditViewerContextMenu(ctx), new PacLotteryButtonListener(ctx), new AdminCommands(ctx), new AccountCommands(ctx),
                        new PacCommands(ctx), new CasinoButtonListener(ctx), new SupportButtonListener(ctx), new UpaEventDiscordListener(ctx), statisticsCommand, new NewbieGuideButtonListener(), new NodeListingCommands(ctx)).build().awaitReady();
        //updateCommands();
    }

    @Override
    protected void shutDown() throws Exception {
        bot().shutdown();
    }

    public volatile long cjNetworth;
    public volatile long highroadNetworth;

    public void updatePacStats() {

        long totalPac = ctx.databaseCaching().getTotalPac().get();
        long upxValue = (long) Math.floor(totalPac / 5.0);
        double totalAssetsUncalc = ((highroadNetworth + cjNetworth) * 0.25);
        long totalAssets = (long) (Math.floor(totalAssetsUncalc) - upxValue);
        StringBuilder sb = new StringBuilder().append("**").
                append(COMMA_FORMAT.format(totalPac)).
                append(" PAC** (**").
                append(COMMA_FORMAT.format(upxValue)).
                append(" UPX**) in circulation\n").
                append("**").
                append(COMMA_FORMAT.format(totalAssets * 5)).
                append(" PAC** (**").
                append(COMMA_FORMAT.format(totalAssets)).
                append(" UPX**) available\n\n").
                append("Purchase PAC or redeem your existing PAC here for rewards!");
        guild().getTextChannelById(1000173221326364722L).editMessageById(1000173279534907504L,
                new MessageEditBuilder().
                        setContent(sb.toString()).
                        setComponents(ActionRow.of(PacCommands.openStoreButtons())).build()).queue();
    }

    public void updateCommands() {
        Guild guild = guild();
       /*

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
                                addOption(OptionType.STRING, "message", "The message to send.", true),
                        new SubcommandData("purge", "Purge all non-goat messages."),
                       new SubcommandData("add_build_request", "Add a build request to one of the trains.").
                               addOption(OptionType.USER, "member", "The member.", true).
                               addOption(OptionType.STRING, "property_id", "The property id.", true).
                               addOption(OptionType.CHANNEL, "train", "The train.", true),
                                new SubcommandData("remove_build_request", "Remove a build request from one of the trains.").
                                        addOption(OptionType.USER, "member", "The member.", true).
                                        addOption(OptionType.CHANNEL, "train", "The train.", true),
                      new SubcommandData("transfer_ssh", "Force transfer SSH from one train to another.").
                              addOption(OptionType.USER, "member", "The member.", true).
                              addOption(OptionType.STRING, "from_train", "From train.", true).
                              addOption(OptionType.STRING, "to_train", "To train.", true).
                              addOption(OptionType.CHANNEL, "amount", "The amount.", true),
                        new SubcommandData("open_ticket", "Opens a support thread.").
                                addOption(OptionType.USER, "member", "The member.", true),
                                new SubcommandData("award_referral", "Give PAC to another member.").
                                        addOption(OptionType.USER, "referrer", "The referrer.", true).
                                        addOption(OptionType.INTEGER, "joined", "The member that joined because of the referrer.", true))).queue();


             ctx.discord().bot().getTextChannelById(986638348418449479L).editMessageById(1025868644946825358L, new MessageBuilder().
                append("Please use one of the buttons below to get help and someone will assist you shortly. You can only have one ticket open at a time.").setActionRows(ActionRow.of(
                        Button.of(ButtonStyle.PRIMARY, "request_help", "Request help", Emoji.fromUnicode("U+270B")),
                        Button.of(ButtonStyle.PRIMARY, "make_a_suggestion", "Make a suggestion", Emoji.fromUnicode("U+1F4A1"))
                        )).build()).queue();
                ctx.discord().guild().getTextChannelById(993201967096660069L).retrieveMessageById(1025529436256358500L).complete().editMessage(
                new MessageBuilder().setContent("Choose one of the options below to win some extra PAC (If you're lucky!)").
                        setActionRows(ActionRow.of(Button.of(ButtonStyle.PRIMARY, "lottery", "Lottery", Emoji.fromUnicode("U+1F3AB")),
                                Button.of(ButtonStyle.PRIMARY, "slot_machine", "Slot machine", Emoji.fromUnicode("U+1F3B0")),
                                Button.of(ButtonStyle.PRIMARY, "bets", "Bets", Emoji.fromUnicode("U+1F91D")))).build()).queue(); guild.upsertCommand(Commands.slash("properties", "Access the UPA node property marketplace. You must be a UPA member.").
                addSubcommands(new SubcommandData("sell", "List a node property for sale.").
                                addOptions(
                                        new OptionData(OptionType.STRING, "address", "The listing address.", true).setAutoComplete(true),
                                        new OptionData(OptionType.STRING, "currency", "The currency to list in.", true).
                                                addChoice("UPX", "upx").
                                                addChoice("USD", "usd").
                                                addChoice("PAC", "pac"),
                                        new OptionData(OptionType.INTEGER, "amount", "The amount you wish to sell for.", true),
                                        new OptionData(OptionType.STRING, "description", "Eg. Right beside the genesis block, make an offer for 12k UPX."),
                                        new OptionData(OptionType.STRING, "image_link", "Link to any image that you want to include in your listing.")),
                        new SubcommandData("buy", "View listed node properties."),
                        new SubcommandData("delete", "Delete a node property listing.").
                                addOptions( new OptionData(OptionType.STRING, "address", "The listing address.", true).setAutoComplete(true)))).queue();
        guild.getTextChannelById(975123222506917919L).editMessageById(1009896559191785634L,
                new MessageBuilder().append("Use the following commands in <#967096154607194232>\n\n/properties buy -> To purchase node properties from UPA members\n/properties sell -> To list your node properties for sale\n/properties delete -> To delete node listings").setActionRows(ActionRow.of(
                        Button.of(ButtonStyle.PRIMARY, "view_listings", "View listings", Emoji.fromUnicode("U+1F4CB")),
                        Button.of(ButtonStyle.PRIMARY, "node_locations", "Node locations", Emoji.fromUnicode("U+1F30E"))
                )).build()).queue();
     /*  SelectMenu.Builder eligibleCities = SelectMenu.create("choose_city");
        for (var next : PacCommands.UPA_VISIT_PROPERTIES.entrySet()) {
            String city = next.getKey();
            eligibleCities.addOption(city, city.toLowerCase());
        }
        guild.getTextChannelById(982800525386993694L).editMessageById(1006264301616644276L,
                new MessageBuilder().append("Use the menu below to get the visit location for the event.").setActionRows(ActionRow.of(eligibleCities.build())).build()).queue();

        guild.getTextChannelById(970916628223123456L).editMessageById(999917437153718362L,
                new MessageBuilder().append("Select one of the buttons below.").setActionRows(ActionRow.of(
                        Button.of(ButtonStyle.PRIMARY, "visitor_guide", "Visitor guide", Emoji.fromUnicode("U+1F4B5")),
                        Button.of(ButtonStyle.PRIMARY, "uplander_guide", "Uplander guide", Emoji.fromUnicode("U+1F4B8")),
                        Button.of(ButtonStyle.PRIMARY, "pro_guide", "Pro guide", Emoji.fromUnicode("U+1F4B0")),
                        Button.of(ButtonStyle.PRIMARY, "more_help", "More help", Emoji.fromUnicode("U+1F64B"))
                )).build()).queue();


             guild.getTextChannelById(975506360231948288L).editMessageById(996389101915877386L, new MessageBuilder().
                append("@everyone UPA is pleased to announce that we are starting a scholarship program for players that are of VISITOR rank.\n\n").
                append("What is a scholar?\n", Formatting.BOLD, Formatting.UNDERLINE).
                append("A scholar is any server member that is of VISITOR rank.\n\n").
                append("How does it work?\n", Formatting.BOLD, Formatting.UNDERLINE).
                append("Sign-up using the \"Become a scholar\" button below and our server members will be rewarded for visiting your property. ").
                append("In addition to this, every month during spark week a random scholar will be sponsored. Our members receive bonus rewards for visiting the sponsored scholar's property.\n\n").
                append("Requirements\n", Formatting.BOLD, Formatting.UNDERLINE).
                append("- Must be of VISITOR rank (< 10,000 networth)\n").
                append("- Must have at least 5k networth\n").
                append("- Must be a member of this Discord server\n").setActionRows(ActionRow.of(
                        ScholarshipCommand.becomeAScholarButton(),
                        Button.of(ButtonStyle.PRIMARY, "view_scholar_list", "View scholar list", Emoji.fromUnicode("U+1F4CB")),
                        Button.of(ButtonStyle.PRIMARY, "view_leaderboard", "View leaderboard", Emoji.fromUnicode("U+1F947"))
                )).build()).queue();
                  guild.upsertCommand(Commands.slash("pac", "All commands related to PAC (Property Advisor Credits).").
                addSubcommands(new SubcommandData("daily", "Claim your daily free PAC. Resets at 8:00am UTC everyday."),
                        new SubcommandData("send", "Send your PAC to another UPA member."). // TODO remove
                                addOption(OptionType.USER, "member", "The member to send PAC to.", true).
                                addOption(OptionType.STRING, "amount", "The amount of PAC to send.", true).
                                addOption(OptionType.STRING, "reason", "The reason for sending PAC.", true))).queue();

      guild.updateCommands().queue();
        guild.upsertCommand(Commands.user("View PAC")).queue();
        guild.upsertCommand(Commands.user("View node properties")).queue();
        guild.upsertCommand(Commands.user("Tip 250 PAC")).queue();
        guild.upsertCommand(Commands.user("Give PAC [Admin]")).queue();

        guild.upsertCommand(Commands.slash("scholarships", "All scholarship related commands.")).queue();




        guild.upsertCommand("statistics", "View common statistics related to the Hollis, Queens node.").queue();
        guild.upsertCommand("account", "View data related to your UPA account, or become a member.").queue();

        guild.getTextChannelById(956791879867977738L).
                retrieveMessageById(995401229754703883L).complete().editMessage(new MessageBuilder().append("Select an option.").setActionRows(ActionRow.of(
                        Button.of(ButtonStyle.PRIMARY, "member_guidelines", "Member guidelines", Emoji.fromUnicode("U+1F4C3")),
                        Button.of(ButtonStyle.PRIMARY, "st_guidelines", "Spark train guidelines", Emoji.fromUnicode("U+1F682"))
                )).build()).queue();
        guild.getTextChannelById(956799638260830238L).
                retrieveMessageById(996376842053374012L).complete().editMessage(new MessageBuilder().append("What would you like to know?").setAct  ionRows(ActionRow.of(
                        Button.of(ButtonStyle.PRIMARY, "getting_started", "Getting started", Emoji.fromUnicode("U+1F44B")),
                        Button.of(ButtonStyle.PRIMARY, "joining_spark_train", "Joining the spark train", Emoji.fromUnicode("U+1F686")),
                       PacCommands.openUnderstandingPacButton(),
                        Button.of(ButtonStyle.PRIMARY, "command_list", "Command list", Emoji.fromUnicode("U+1F4CB")),
                        Button.of(ButtonStyle.PRIMARY, "videos", "Videos", Emoji.fromUnicode("U+1F3A5"))
                )).build()).queue();*/
    }

    public <T> ListenableScheduledFuture<T> schedule(Callable<T> task, long delay, TimeUnit unit) {
        return schedulePool.schedule(task, delay, unit);
    }

    public ListenableScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return schedulePool.schedule(task, delay, unit);
    }

    public void sendBotRequestsMsg(UpaMember requester, String assistanceWith, Consumer<Message> onSuccess) {
        ctx.discord().guild().getTextChannelById(984551707176480778L).sendMessageEmbeds(
                new EmbedBuilder()
                        .setDescription("<@" + requester.getMemberId() + "> needs assistance with " + assistanceWith + ".")
                        .setFooter("Note: Marking a request as completed will automatically award/take away PAC!")
                        .setColor(Color.RED).build()
        ).setActionRow(
                Button.of(ButtonStyle.PRIMARY, "assign_yourself", "Assign yourself", Emoji.fromUnicode("U+1F64B")),
                Button.of(ButtonStyle.PRIMARY, "mark_completed", "Request handled", Emoji.fromUnicode("U+2705"))).queue(onSuccess);
    }

    public void sendUpxForPacMsg(UpaMember requester, int amount, int cost, Consumer<Message> onSuccess) {
        ctx.discord().guild().getTextChannelById(984551707176480778L).sendMessageEmbeds(
                new EmbedBuilder()
                        .setDescription("<@" + requester.getMemberId() + "> needs assistance with buying " + amount + " PAC for " + cost + " UPX.")
                        .setFooter("Note: Press reactions to mark as completed or assign yourself.")
                        .setColor(Color.RED).build()
        ).setActionRow(
                Button.of(ButtonStyle.PRIMARY, "assign_yourself", "Assign yourself", Emoji.fromUnicode("U+1F64B")),
                Button.of(ButtonStyle.PRIMARY, "mark_completed", "Request handled", Emoji.fromUnicode("U+2705"))).queue(onSuccess);
    }

    public void sendPropertyForPacMsg(UpaMember requester, UpaPoolProperty property, Consumer<Message> onSuccess) {
        ctx.discord().guild().getTextChannelById(984551707176480778L).sendMessageEmbeds(
                new EmbedBuilder()
//                        .setDescription("<@" + requester.getMemberId() + "> needs assistance with " + assistanceWith + ".")
                        .setFooter("Note: Press reactions to mark as completed or assign yourself.")
                        .setColor(Color.RED).build()
        ).setActionRow(
                Button.of(ButtonStyle.PRIMARY, "assign_yourself", "Assign yourself", Emoji.fromUnicode("U+1F64B")),
                Button.of(ButtonStyle.PRIMARY, "mark_completed", "Request handled", Emoji.fromUnicode("U+2705"))).queue(onSuccess);
    }

    public void sendFeedbackOrBugMsg(UpaMember requester, String subject, String description, Consumer<Message> onSuccess) {
        ctx.discord().guild().getTextChannelById(984551707176480778L).sendMessageEmbeds(
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
            requireNonNull(ctx.discord().guild().getTextChannelById("1058028231388835840")).sendMessage(transaction.getReason()).queue();
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
            requireNonNull(ctx.discord().guild().getTextChannelById("1058028231388835840")).sendMessageEmbeds(new EmbedBuilder().
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
                        if (next.getAmount() == 0) {
                            continue;
                        }
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
        }, success -> {
            ctx.variables().pacTransactions().accessValue(repo -> {
                transactions.forEach(next -> {
                    next.getUpaMember().getCredit().addAndGet(next.getAmount());
                    next.onSuccess();
                    repo.store(new PacTransaction(next.getAmount(), next.getHistoryReason(), next.getUpaMember().getMemberId(), Instant.now()));
                    sendCreditMessage(next);
                    if (ThreadLocalRandom.current().nextInt(3) == 0) {
                        updatePacStats();
                    }
                    ctx.databaseCaching().getTotalPac().addAndGet(next.getAmount());
                    if (next.getTransactionType() != CreditTransactionType.GIFTED) {
                        ctx.variables().reports().accessValue(reportData -> {
                            PendingWeeklyReport report = reportData.getReports().get(next.getUpaMember().getMemberId());
                            if (report != null) {
                                int amount = Math.abs(next.getAmount());
                                if (next.getAmount() < 0) {
                                    report.getPacSpent().addAndGet(amount);
                                } else {
                                    report.getPacGained().addAndGet(amount);
                                }
                                return true;
                            }
                            return false;
                        });
                    }
                });
                return true;
            });
        });

        /*  if ((next.getTransactionType() == CreditTransactionType.PURCHASE ||
                    next.getTransactionType() == CreditTransactionType.REDEEM) && next instanceof BotRequestTransaction) {
                BotRequestTransaction transaction = (BotRequestTransaction) next;
                SqlConnectionManager.getInstance().execute(new BotRequestTask(transaction.getHandler().getKey(),
                        transaction.getUpaMember().getKey(), transaction.getNetUpx(), transaction.getAmount(), transaction.getAsset()));
            }*/
    }

    public void sendCredit(CreditTransaction transaction) {
        if (transaction.getAmount() == 0) {
            return;
        }
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
        String floorType = ctx.salesProcessor().selector.getClass() == CityPropertySelector.class ? "city" : "neighborhood";
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


    public SparkTrainMessageListener getSparkTrain() {
        return sparkTrain;
    }
}
