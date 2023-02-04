package me.upa.discord.listener.command;

import com.fasterxml.jackson.databind.annotation.JsonAppend.Prop;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import me.upa.UpaBotContext;
import me.upa.discord.DiscordService;
import me.upa.discord.TipTransfer;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaPoolProperty;
import me.upa.discord.UpaProperty;
import me.upa.discord.UpaStoreRequest;
import me.upa.discord.UpaStoreRequest.RequestType;
import me.upa.discord.event.UpaEvent;
import me.upa.discord.event.impl.BonusPacEventHandler;
import me.upa.discord.event.impl.BonusSshEventHandler;
import me.upa.discord.event.impl.DoubleDividendEventHandler;
import me.upa.discord.history.PacTransaction;
import me.upa.discord.listener.credit.CreditTransaction;
import me.upa.discord.listener.credit.CreditTransaction.CreditTransactionType;
import me.upa.discord.listener.credit.CreditTransfer;
import me.upa.discord.listener.credit.CreditTransferTask;
import me.upa.fetcher.DataFetcherManager;
import me.upa.fetcher.ProfileDataFetcher;
import me.upa.fetcher.PropertyDataFetcher;
import me.upa.game.City;
import me.upa.game.Neighborhood;
import me.upa.game.Node;
import me.upa.game.Profile;
import me.upa.game.Property;
import me.upa.service.DailyResetMicroService;
import me.upa.service.DatabaseCachingService;
import me.upa.service.PropertySynchronizationService;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static me.upa.fetcher.ProfileDataFetcher.DUMMY;

public final class PacCommands extends ListenerAdapter {

    public static SelectMenu computePurchaseWithVisits() {
        SelectMenu.Builder eligibleCities = SelectMenu.create("choose_city");
        for (var next : UPA_VISIT_PROPERTIES.entrySet()) {
            String city = next.getKey();
            eligibleCities.addOption(city, city.toLowerCase());
        }
        return eligibleCities.build();
    }

    public PacCommands(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    public static final class UpaVisitProperty {
        private final String address;
        private final long propertyId;

        private UpaVisitProperty(String address, long propertyId) {
            this.address = address;
            this.propertyId = propertyId;
        }
    }

    public static ImmutableMap<String, UpaVisitProperty> UPA_VISIT_PROPERTIES;
    public static ImmutableSet<Long> UPA_VISIT_PROPERTY_IDS;

    static {
        // Get automatically
        UPA_VISIT_PROPERTIES = ImmutableMap.of(
                "Bronx", new UpaVisitProperty("1052 E 224TH ST", 81482042143007L),
                "Brooklyn", new UpaVisitProperty("1016 E 100TH ST", 81332322277927L),
                "Chicago", new UpaVisitProperty("6932 S UNION AVE", 82032669949269L),
                "Detroit", new UpaVisitProperty("3201 VIRGINIA PARK ST", 82403782068378L),
                "Las Vegas", new UpaVisitProperty("4528 MONTEBELLO AVE", 78547437595171L),
                //"Los Angeles", new UpaVisitProperty("", 0L),
                "Porto", new UpaVisitProperty("RUA DO PINHEIRO MANSO 62", 81655755247623L),
                "Oakland", new UpaVisitProperty("2605 90TH AVE", 79535028167544L),
                "Queens", new UpaVisitProperty("19111 104TH AVE", 81372201721075L),
                "Rio de Janeiro", new UpaVisitProperty("ESTRADA DO ITANHANGA, 2533", 41716334150032L)
        );
        UPA_VISIT_PROPERTY_IDS = UPA_VISIT_PROPERTIES.values().stream().map(next -> next.propertyId).collect(ImmutableSet.toImmutableSet());
    }

    private static final double UPX_PER_PAC_RATE = 2;
    private static final int PAC_PER_UPX_RATE = 5;
    private static final int PAC_PER_SSH_RATE = 100;


    private static String purchaseWithUpxInfo(UpaBotContext ctx) {
        double rate = UpaEvent.isActive(ctx, BonusPacEventHandler.class) ? UPX_PER_PAC_RATE * 1.25 : UPX_PER_PAC_RATE;
        int exampleStart = 5000;
        String exampleFinish = DiscordService.COMMA_FORMAT.format(exampleStart * rate);
        return "__**Rate**__: " + rate + " PAC per 1 UPX (ie. " + DiscordService.COMMA_FORMAT.format(exampleStart) + " UPX would get you " + exampleFinish + " PAC).\n\n" +
                "__**How it works**__\n" +
                "-> Send a UPX transfer of your desired amount to 'unruly_cj' in-game, and you will be awarded the PAC\n" +
                "-> If you need to use a burner property to pay, use the menu below\n";
    }

    private static String purchaseWithVisitInfo(UpaBotContext ctx) {
        boolean isBonusEvent = UpaEvent.isActive(ctx, BonusPacEventHandler.class);
        String rate = isBonusEvent ? "125%" : "100%";
        int exampleStart = 100;
        int exampleFinish = isBonusEvent ? exampleStart : (int) (exampleStart * 1.25);
        return "__**Rate**__: " + rate + " of visit fee (ie. " + exampleStart + " UPX visit fee = " + exampleFinish + " PAC per visit).\n\n" +
                "__**How it works**__\n" +
                "-> Choose your desired city from our list of available cities\n" +
                "-> You will be shown the property you can send to in that city to earn PAC\n" +
                "-> Visit to earn!\n\n";
    }

    private static final String PURCHASE_WITH_PROPERTY_INFO = "__**Rate**__\n" +
            "-> UPA node properties: Mint price * 2.5 (ie. If the mint price is 5k, you would get 12.5k PAC)\n" +
            "-> Other properties: Mint price * 1.5 (ie. If the mint price is 5k, you would get 7.5k PAC)\n\n" +
            "__**How it works**__\n" +
            "-> A staff member will contact you once you've made a purchase request\n" +
            "-> You transfer the property you wish to donate for our burner property\n" +
            "-> We buy back the burner from you at mint price, and award you PAC\n" +
            "-> You transfer back the cost of the burner\n\n" +
            "Burner cost will be subtracted from the mint price if you cannot transfer the difference.\n\n" +
            "__**How to get the property link**__\n";

    private static final String REDEEM_FOR_UPX_INFO = "__**Rate**__: " + PAC_PER_UPX_RATE + " PAC per 1 UPX (ie. 25,000 PAC would get you 5000 UPX).\n\n" +
            "__**How it works**__\n" +
            "-> A staff member will contact you once you've made a redeem request\n" +
            "-> We will send you your UPX through an in-game transfer or burner property\n" +
            "-> We will deduct the proportionate amount of PAC\n\n";

    private static String redeemForSshInfo(boolean global) {
        return "Purchasing SSH for the **" + (global ? "global" : "Hollis") + "** train.\n\n" +
                "__**Rate**__ " + PAC_PER_SSH_RATE + " PAC per SSH (ie. 5000 PAC would get you 100 SSH)\n\n" +
                "__**How it works**__\n" +
                "-> Select the amount of SSH you would like to purchase\n" +
                "-> Confirm that you would like to go through with the purchase\n" +
                "-> I will automatically award you with your spark share hours\n\n";
    }

    private static final String REDEEM_FOR_PROPERTY_INFO = "__**Rate**__\n" +
            "-> UPA node properties: Mint price * 4 (ie. If the mint price is 5k, you would pay 20k PAC)\n" +
            "-> Other properties: Mint price * 3 (ie. If the mint price is 5k, you would pay 15k PAC)\n\n" +
            "__**How it works**__\n" +
            "-> A staff member will contact you once you've made a redeem request\n" +
            "-> We will swap your requested property with the cheapest mint you own\n" +
            "-> We will send you the UPX required to buy the property back at mint price";
    private static final String REDEEM_FOR_TH_MAP_INFO = "__**Rate**__: -> 1000 PAC per map\n\n" +
            "__**How it works**__\n" +
            "<@373218455937089537> Will contact you for additional details if needed, and send it to you as soon as it's ready.\n\n" +
            "Here is an example of what it will look like https://i.imgur.com/SQ7gcux.png";

    private static final String REDEEM_FOR_PORTFOLIO_ANALYSIS_INFO = "__**Rate**__: -> 25 PAC per property you own\n\n" +
            "__**How it works**__\n" +
            "Your portfolio analysis will begin processing, and will be DMed to you by me when it's ready.\n\n";
    private static final Logger logger = LogManager.getLogger();

    private static final Map<Long, MessageCreateBuilder> statements = new ConcurrentHashMap<>();

    private final UpaBotContext ctx;

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("pac")) {
            switch (event.getSubcommandName()) {
                case "daily":
                    handleDailyCommand(ctx, event.getMember().getIdLong(), event);
                    break;
                case "send":
                    handleTransferCommand(ctx, event, event.getOptions().get(0).getAsMember(),
                            event.getOptions().get(1).getAsLong(),
                            event.getOptions().get(2).getAsString(), CreditTransactionType.TRANSFER);
                    break;
            }
        }
    }

    public static List<Button> openStoreButtons() {
        return List.of(
                openUnderstandingPacButton(),
                Button.of(ButtonStyle.PRIMARY, "purchase", "Purchase PAC", Emoji.fromUnicode("U+1F4B3")),
                Button.of(ButtonStyle.PRIMARY, "redeem", "Redeem your PAC", Emoji.fromUnicode("U+1F381"))
        );
    }

    public static Button openUnderstandingPacButton() {
        return Button.of(ButtonStyle.PRIMARY, "understanding_pac", "Understanding PAC", Emoji.fromUnicode("U+2753"));
    }

    public static Button openStoreInfoButton() {
        return Button.of(ButtonStyle.LINK, "https://docs.google.com/spreadsheets/d/e/2PACX-1vRg1dqgSwj7zbY8qXHviXqNj55ljlBma2KaLYupfIK0V2QUTRLLScWvSEttlbOku4gawap9zc2Z2MOz/pubhtml?gid=2012586904&single=true", "Information ", Emoji.fromUnicode("U+1F50E"));
    }

    private void handleRedeemSsh(SelectMenuInteractionEvent event, boolean global) {
        UpaMember requester = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
        if (requester == null) {
            event.reply("Please use the /account command before making purchases.").setEphemeral(true).queue();
            return;
        }
        if (!requester.getActive().get()) {
            event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
            return;
        }
        int pacAmount = requester.getCredit().get();
        Integer amount = Ints.tryParse(event.getSelectedOptions().get(0).getValue().replace(global ? "redeem_for_ssh_menu_global" : "redeem_for_ssh_menu_", ""));
        if (amount == null) {
            event.reply("Fatal error, please notify administration.").setEphemeral(true).queue();
            return;
        }
        int cost = amount * PAC_PER_SSH_RATE;
        if (pacAmount < cost) {
            event.reply("You need at least **" + DiscordService.COMMA_FORMAT.format(cost) + " PAC** to redeem this (You have **" + pacAmount + " PAC**).").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        if (UpaEvent.isActive(ctx, BonusSshEventHandler.class)) {
            amount = (int) (amount * 1.25);
        }
        Integer finalAmount2 = amount;
        Integer finalAmount3 = amount;
        String field = global ? "global_ssh" : "hollis_ssh";
        ctx.discord().sendCredit(new CreditTransaction(requester, -cost, CreditTransactionType.REDEEM, "**" + DiscordService.COMMA_FORMAT.format(amount) + " SSH**") {
            @Override
            public void onSuccess() {
                SqlConnectionManager.getInstance().execute(new SqlTask<Boolean>() {
                    @Override
                    public Boolean execute(Connection connection) throws Exception {
                        try (PreparedStatement ps = connection.prepareStatement("UPDATE members SET " + field + " = " + field + " + ? WHERE member_id = ?;")) {
                            ps.setInt(1, finalAmount2);
                            ps.setLong(2, requester.getMemberId());
                            if (ps.executeUpdate() != 1) {
                                return false;
                            }
                        }
                        return true;
                    }
                }, success -> {
                    String formattedAmt = DiscordService.COMMA_FORMAT.format(finalAmount3);
                    if (success) {
                        if (global) {
                            requester.getGlobalSsh().addAndGet(finalAmount3);
                        } else {
                            requester.getHollisSsh().addAndGet(finalAmount3);
                        }
                        event.getHook().setEphemeral(true).editOriginal("Your **" + formattedAmt + " SSH** Has been credited to your account. You can verify this using /account.").queue();
                    } else {
                        ctx.discord().sendBotRequestsMsg(requester, "purchasing **" + formattedAmt + " SSH** using PAC (Database transaction failed).",
                                msg -> event.getHook().setEphemeral(true).editOriginal("Database transaction failed, you will be awarded your SSH manually within a few hours. You can check /account to see when it's processed.").queue());
                    }
                });
            }
        });
    }

    @Override
    public void onSelectMenuInteraction(@NotNull SelectMenuInteractionEvent event) {
        if (event.getSelectMenu().getId() != null) {
            switch (event.getSelectMenu().getId()) {
                case "redeem_for_upx_menu":
                    UpaMember requester = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
                    if (requester == null) {
                        event.reply("Please use the /account command before redeeming rewards.").setEphemeral(true).queue();
                        return;
                    }
                    if (!requester.getActive().get()) {
                        event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
                        return;
                    }
                    Integer amount = Ints.tryParse(event.getSelectedOptions().get(0).getValue().replace("redeem_for_upx_menu_", ""));
                    if (amount == null) {
                        event.reply("Fatal error, please notify administration.").setEphemeral(true).queue();
                        return;
                    }
                    int pacAmount = requester.getCredit().get();
                    if(pacAmount < amount) {
                        event.reply("You need at least "+amount+" PAC to redeem this amount of UPX.").queue();
                        return;
                    }
                    String upxAmount = DiscordService.COMMA_FORMAT.format(amount / 5);
                    String action = "**" + DiscordService.COMMA_FORMAT.format(amount) + " PAC** for UPX";
                    event.deferReply(true).queue();
                    Integer finalAmount1 = amount;
                    ctx.discord().sendBotRequestsMsg(requester, "redeeming " + action,
                            msg -> {
                                long commentId = msg.getIdLong();
                                ctx.variables().storeRequests().access(map -> map.get().putIfAbsent(commentId, new UpaStoreRequest(requester.getMemberId(), commentId, -finalAmount1, "for UPX", RequestType.PAC)) == null);
                                event.getHook().setEphemeral(true).editOriginal("Your request to redeem " + action + " has been sent to the UPA staff team. You'll hear from us shortly!").queue();
                                event.editSelectMenu(event.getSelectMenu().asDisabled()).queue();
                            });
                    break;
                case "redeem_for_ssh_menu":
                    handleRedeemSsh(event, false);
                    break;

                case "redeem_for_ssh_menu_global":
                    handleRedeemSsh(event, true);
                    break;
                case "choose_city":
                    String cityName = event.getSelectedOptions().get(0).getLabel();
                    UpaVisitProperty property = UPA_VISIT_PROPERTIES.get(cityName);
                    if (property == null) {
                        event.reply("There is no designated UPA property for this city yet.").setEphemeral(true).queue();
                        event.getInteraction().editSelectMenu(event.getSelectMenu().asDisabled()).queue();
                        return;
                    }
                    event.reply("Please visit **" + property.address + "** in order to gain 100% of the visitor fee as PAC. You should be awarded your PAC within 5-10 minutes.\n\nProperty link: https://play.upland.me/?prop_id=" + property.propertyId + "\n\nIf you do not receive your PAC automatically, please reach out using the button below and we can verify it manually.").addActionRow(
                            Button.of(ButtonStyle.SUCCESS, "help_with_paid_sends", "Report untracked visits", Emoji.fromUnicode("U+1F4E7"))
                    ).setEphemeral(true).queue();
                    break;
                case "confirm_redeem_for_property_select":
                    long propertyId = Long.parseLong(event.getSelectedOptions().get(0).getValue());
                    UpaPoolProperty poolProperty = ctx.databaseCaching().getPoolProperties().get(propertyId);
                    if (poolProperty == null) {
                        event.reply("Selected pool property can no longer be found. Someone may have claimed it already.").setEphemeral(true).queue();
                        return;
                    }
                    MessageEditBuilder mb = new MessageEditBuilder().setContent(" ").setEmbeds(new EmbedBuilder().
                            setDescription("Are you sure you'd like to purchase **" + poolProperty.getDescriptiveAddress() + "** for **" +
                                    DiscordService.COMMA_FORMAT.format(poolProperty.getCost().get()) + " PAC**?").
                            addField("Mint price", String.valueOf(poolProperty.getMintPrice()), false).
                            addField("UP2", String.valueOf(poolProperty.getUp2()), false).
                            addField("Previous owner", "<@" + poolProperty.getDonorMemberId() + ">", false).
                            addField("Property link", "https://play.upland.me/?prop_id=" + poolProperty.getPropertyId(), false).
                            setColor(Color.GREEN).
                            build()).setComponents(ActionRow.of(
                            Button.of(ButtonStyle.SUCCESS, "confirm_redeem_for_property_select" + poolProperty.getPropertyId(), "Yes!", Emoji.fromUnicode("U+1F3E0")),
                            Button.of(ButtonStyle.DANGER, "cancel_redeem_for_property_select", "No", Emoji.fromUnicode("U+270B"))
                    ));
                    event.getInteraction().editMessage(mb.build()).queue();
                    break;
            }
        }
    }

    private void purchaseSsh(ButtonInteractionEvent event, boolean global) {
        boolean active = UpaEvent.isActive(ctx, BonusSshEventHandler.class);
        int pac = 2500;
        int ssh = 25;
        double rate = 1.25;
        SelectMenu.Builder menuBuilder = SelectMenu.create(global ? "redeem_for_ssh_menu_global" : "redeem_for_ssh_menu");
        for (int loops = 0; loops < 5; loops++) {
            menuBuilder.addOption("Redeem " + (int) (active ? ssh * rate : ssh) + " SSH (" + DiscordService.COMMA_FORMAT.format(pac) + " PAC)",
                    global ? "redeem_for_ssh_menu_global" + ssh : "redeem_for_ssh_menu_" + ssh);
            pac *= 2;
            ssh *= 2;
        }
        event.reply(redeemForSshInfo(global)).setEphemeral(true).addActionRow(
                menuBuilder.build()).queue();
    }

    public static int getPropertySubmitValue(int mintPrice, boolean global) {
        double nodePac = mintPrice * 2.5;
        double otherPac = mintPrice * 1.5;
        return (int) Math.floor(!global ? nodePac : otherPac);
    }

    public static int getPropertyRedeemValue(UpaPoolProperty poolProperty, boolean global) {
        if (poolProperty == null)
            return 0;
        double nodePac = poolProperty.getMintPrice() * 4;
        double otherPac = poolProperty.getMintPrice() * 3;
        int cost = (int) Math.floor(!global ? nodePac : otherPac);
        if (poolProperty.getCost().compareAndSet(0, cost)) {
            return cost;
        }
        return poolProperty.getCost().get();
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null)
            return;
        if (event.getButton().getId().startsWith("confirm_redeem_for_property_select")) {
            UpaMember upaMember = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
            if (upaMember == null) {
                event.getInteraction().editMessage("Please become a UPA member by using /account before doing this.").queue();
                return;
            }
            if (!upaMember.getActive().get()) {
                event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
                return;
            }
            Long propertyId = Longs.tryParse(event.getButton().getId().replace("confirm_redeem_for_property_select", ""));
            MessageEditBuilder mb = new MessageEditBuilder().setComponents(List.of()).setEmbeds(List.of());
            if (propertyId == null) {
                event.getInteraction().editMessage(mb.setContent("Error. Please try again.").build()).queue();
                return;
            }
            UpaPoolProperty upaProperty = ctx.databaseCaching().getPoolProperties().get(propertyId);
            if (upaProperty == null) {
                event.getInteraction().editMessage(mb.setContent("Property was claimed by another member.").setEmbeds(List.of()).
                        setComponents(List.of()).build()).queue();
                return;
            }
            int propertyCost = upaProperty.getCost().get();
            int diff = upaMember.getCredit().get() - propertyCost;
            if (diff < 0) {
                event.getInteraction().editMessage("You need an additional **" + DiscordService.COMMA_FORMAT.format(Math.abs(diff)) + " PAC** in order to purchase this.").setEmbeds(List.of()).setComponents(List.of()).queue();
                return;
            }
            ctx.discord().sendBotRequestsMsg(upaMember, "purchasing **" + upaProperty.getDescriptiveAddress() + "** for **" + upaProperty.getCost() + " PAC**",
                    msg -> {
                        event.getInteraction().editMessage("Your request to trade **" +
                                upaProperty.getDescriptiveAddress() + "** for PAC was sent to the UPA team. You'll hear from us shortly!").queue();
                        ctx.variables().storeRequests().accessValue(storeRequests -> storeRequests.putIfAbsent(msg.getIdLong(), new UpaStoreRequest(event.getMember().getIdLong(), msg.getIdLong(), -propertyId, "redeeming " + upaProperty.getDescriptiveAddress(), RequestType.PROPERTY)) == null);
                    });
            return;
        }
        switch (event.getButton().getId()) {
            case "cancel_redeem_for_property_select":
                redeemForProperty(event, true);
                break;
            case "redeem_for_upx":
                SelectMenu.Builder menuBuilder = SelectMenu.create("redeem_for_upx_menu");
                menuBuilder.addOption("Redeem 2k UPX (10k PAC)", "redeem_for_upx_menu_10000");
                menuBuilder.addOption("Redeem 4k UPX (20k PAC)", "redeem_for_upx_menu_20000");
                menuBuilder.addOption("Redeem 6k UPX (30k PAC)", "redeem_for_upx_menu_30000");
                menuBuilder.addOption("Redeem 8k UPX (40k PAC)", "redeem_for_upx_menu_40000");
                menuBuilder.addOption("Redeem 10k UPX (50k PAC)", "redeem_for_upx_menu_50000");

                event.reply(REDEEM_FOR_UPX_INFO).setEphemeral(true).addActionRow(menuBuilder.build()).queue();
                //    event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "purchase":
                handlePurchaseButton(event);
                //  event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "redeem":
                handleRedeemButton(event);
                // event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "confirm_redeem_for_th_map":
                UpaMember requester = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
                if (requester == null) {
                    event.reply("Please use the /account command before making purchases.").setEphemeral(true).queue();
                    return;
                }
                if (!requester.getActive().get()) {
                    event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
                    return;
                }
                int pacAmount = requester.getCredit().get();
                if (pacAmount < 1000) {
                    event.reply("You need at least **1000 PAC** to redeem this (You have **" + pacAmount + " PAC**).").setEphemeral(true).queue();
                    return;
                }
                ctx.discord().sendBotRequestsMsg(requester, "redeeming a treasure hunting map",
                        msg -> {
                            ctx.variables().storeRequests().access(storeRequests -> storeRequests.get().putIfAbsent(msg.getIdLong(), new UpaStoreRequest(requester.getMemberId(), msg.getIdLong(), -1000, "a custom treasure hunting map", RequestType.PAC)) == null);
                            event.getHook().setEphemeral(true).editOriginal("Your request to redeem a custom TH map has been sent to the UPA staff team. You'll hear from us shortly!").queue();
                        });
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "confirm_redeem_for_portfolio_analysis":
                if (true) {
                    event.reply("Under construction!").setEphemeral(true).queue();
                    return;
                }
                requester = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
                if (requester == null) {
                    event.reply("Please use the /account command before making purchases.").setEphemeral(true).queue();
                    return;
                }
                if (!requester.getActive().get()) {
                    event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
                    return;
                }
                event.deferReply(true).queue();
                ctx.discord().execute(() -> {
                    try {
                        Profile profile = ProfileDataFetcher.fetchProfileSynchronous(requester.getInGameName());
                        if (profile == null || profile == ProfileDataFetcher.DUMMY) {
                            event.getHook().setEphemeral(true).editOriginal("You profile could not be fetched. Please try again later.").queue();
                            return;
                        }
                        int finalPacAmount = requester.getCredit().get();
                        int pacNeeded = 25 * profile.getProperties().size();
                        if (finalPacAmount < pacNeeded) {
                            event.getHook().setEphemeral(true).editOriginal("You need **" + pacNeeded + "** to redeem this (You have **" + finalPacAmount + " PAC**).").queue();
                            return;
                        }
                        requester.getCredit().addAndGet(-pacNeeded);
                        event.getHook().setEphemeral(true).editOriginal("Your request to redeem a portfolio analysis has been registered with me! I'll DM you with the analysis when it's ready.").queue();
                        event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                    } catch (Exception e) {
                        logger.catching(e);
                    }
                });
                break;
            case "help_with_paid_sends":
                TextInput helpDescription = TextInput.create("confirm_help_with_paid_sends", "Enter details", TextInputStyle.PARAGRAPH).setRequired(true).setPlaceholder("I visited <property> 10 times. I should've received 1000 PAC, but I only got 500.").build();
                event.replyModal(Modal.create("confirm_help_with_paid_sends_form", "Unawarded PAC for sends").addActionRow(helpDescription).build()).queue();
                //    event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "confirm_purchase_with_property":
                TextInput amountInput = TextInput.create("confirm_purchase_with_property_form_link", "Property link", TextInputStyle.SHORT).setRequired(true).setPlaceholder("https://play.upland.me/?prop_id=77296058553954").build();
                Modal modal = Modal.create("confirm_purchase_with_property_form", "Which property would you like to trade in?").addActionRow(amountInput).build();
                event.replyModal(modal).queue();
                //  event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "need_burner":
                requester = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
                if (requester == null) {
                    event.reply("Please use the /account command before making purchases.").setEphemeral(true).queue();
                    return;
                }
                if (!requester.getActive().get()) {
                    event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
                    return;
                }
                event.reply(new MessageCreateBuilder().
                        addContent("To use a burner property to purchase PAC please send an offer to one of the following properties. **Please make sure the property is owned by <@220622659665264643> before making your offer.**\n\n").
                        addContent("RUA JOSAFA, 59B | https://play.upland.me/?prop_id=41745627167609 | Mint price **539 UPX**\n").
                        addContent("12714 ROSA PARKS BLVD | https://play.upland.me/?prop_id=82421800798333 | Mint price **624 UPX**\n\n").
                        addContent("I will then buy back my burner at mint price and credit the PAC to your account.").build()
                ).setEphemeral(true).queue();
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "purchase_with_upx":
                event.reply(purchaseWithUpxInfo(ctx)).setEphemeral(true).addActionRow(
                        Button.of(ButtonStyle.SECONDARY, "need_burner", "Use burner for transfer", Emoji.fromUnicode("U+1F3E0"))
                ).queue();
                //  event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "purchase_with_visits":
                event.reply(purchaseWithVisitInfo(ctx)).setEphemeral(true).addActionRow(computePurchaseWithVisits()).queue();
                //    event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "purchase_with_property":
                if (event.getMember() == null)
                    return;
                String username = ctx.databaseCaching().getMemberNames().get(event.getMember().getIdLong());
                if (username == null) {
                    event.reply("Please use /account before doing this.").setEphemeral(true).queue();
                    return;
                }
                event.reply(PURCHASE_WITH_PROPERTY_INFO + "Go to http://upxland.me/users/" + username + " and select the link of the property you wish to use from here https://i.imgur.com/WpdAnu9.png").setEphemeral(true).addActionRow(
                        Button.of(ButtonStyle.SUCCESS, "confirm_purchase_with_property", "Buy PAC using a property", Emoji.fromUnicode("U+2705"))).queue();
                //    event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;

            case "redeem_for_ssh":
                event.reply("Which spark train would you like to purchase SSH for?").addActionRow(
                        Button.of(ButtonStyle.PRIMARY, "purchase_ssh_hollis", "Hollis spark train", Emoji.fromUnicode("U+1F9F1")),
                        Button.of(ButtonStyle.PRIMARY, "purchase_ssh_global", "Global spark train", Emoji.fromUnicode("U+1F30E"))).setEphemeral(true).queue();
                //     event.getInteraction().editButton(event.getButton().asDisabled()h).queue();
                break;
            case "purchase_ssh_global":
                purchaseSsh(event, true);
                break;
            case "purchase_ssh_hollis":
                purchaseSsh(event, false);
                break;
            case "redeem_for_property":
                redeemForProperty(event, false);
                break;
            case "redeem_for_th_map":
                event.reply(REDEEM_FOR_TH_MAP_INFO).setEphemeral(true).addActionRow(
                        Button.of(ButtonStyle.SUCCESS, "confirm_redeem_for_th_map", "Redeem a TH map", Emoji.fromUnicode("U+2705"))).queue();
                //     event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "redeem_for_portfolio_analysis":
                event.reply("Coming soon!").setEphemeral(true).queue();

                //    event.reply(REDEEM_FOR_PORTFOLIO_ANALYSIS_INFO).setEphemeral(true).addActionRow(Button.of(ButtonStyle.SUCCESS, "confirm_redeem_for_portfolio_analysis", "Redeem a portfolio analysis", Emoji.fromUnicode("U+2705"))).queue();
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "assign_yourself":
                if (event.getChannel().asTextChannel().getIdLong() == 984551707176480778L && event.getMember() != null && !event.getMember().getUser().isBot()) {
                    Message success = event.getMessage();
                    if (success.getEmbeds().isEmpty()) {
                        return;
                    }
                    MessageEmbed old = success.getEmbeds().get(0);
                    if (success.getAuthor().isBot()) {
                        if (!old.getFields().isEmpty() && Objects.equals(old.getFields().get(0).getName(), "Assigned to")) {
                            return;
                        }
                        success.editMessageEmbeds(new EmbedBuilder()
                                .setDescription(old.getDescription())
                                .addField("Assigned to", "<@" + event.getMember().getIdLong() + ">", true)
                                .setFooter(old.getFooter().getText())
                                .setColor(old.getColor()).build()).queue();
                        event.getInteraction().editButton(null).queue();
                    }
                }
                break;
            case "mark_completed":
                if (event.getChannel().asTextChannel().getIdLong() == 984551707176480778L && event.getMember() != null && !event.getMember().getUser().isBot()) {
                    Message success = event.getMessage();
                    if (success.getEmbeds().isEmpty()) {
                        return;
                    }
                    MessageEmbed old = success.getEmbeds().get(0);
                    if (success.getAuthor().isBot()) {
                        if (old.getColor() == Color.GREEN) {
                            return;
                        }
                        var bldr = new EmbedBuilder();
                        old.getFields().forEach(bldr::addField);
                        success.editMessageEmbeds(bldr
                                .setTitle(old.getTitle())
                                .setDescription(old.getDescription())
                                .setFooter(old.getFooter().getText())
                                .setColor(Color.GREEN).build()).queue();
                        event.deferReply(true).queue();
                        ctx.variables().storeRequests().access(sq -> {
                            var storeRequests = sq.get();
                            UpaStoreRequest request = storeRequests.remove(success.getIdLong());
                            if (request == null) {
                                event.getHook().setEphemeral(true).editOriginal("PAC should be **manually awarded** for this store transaction.").queue();
                                event.getInteraction().editButton(null).queue();
                                return false;
                            }
                            UpaMember upaMember = ctx.databaseCaching().getMembers().get(request.getMemberId());
                            if (upaMember == null || !upaMember.getActive().get()) {
                                event.getHook().setEphemeral(true).editOriginal("Request could not be satisfied. Member not found [ID = " + request.getMemberId() + "].").queue();
                                event.getInteraction().editButton(null).queue();
                                return false;
                            }
                            switch (request.getType()) {
                                case PAC:
                                    CreditTransactionType transactionType = request.getValue() < 0 ? CreditTransactionType.REDEEM : CreditTransactionType.PURCHASE;
                                    if (request.getValue() != 0) {
                                        ctx.discord().sendCredit(new CreditTransaction(upaMember, Ints.checkedCast(request.getValue()), transactionType, request.getReason()) {
                                            @Override
                                            public void onSuccess() {
                                                event.getHook().setEphemeral(true).editOriginal("PAC has been auto-awarded for this store transaction. Please verify manually in <#1058028231388835840>.").queue();
                                                event.getInteraction().editButton(null).queue();
                                            }
                                        });
                                    } else {
                                        event.getHook().setEphemeral(true).editOriginal("PAC should be **manually awarded** for this store transaction.").queue();
                                        event.getInteraction().editButton(null).queue();
                                    }
                                    break;
                                case PROPERTY:
                                    long propertyId = request.getValue();
                                    boolean redeem = propertyId < 0;
                                    propertyId = Math.abs(propertyId);
                                    String query = "UPDATE pool_properties SET verified = 1, cost = ? WHERE property_id = ?;";
                                    if (redeem) {
                                        query = "DELETE FROM pool_properties WHERE property_id = ?;";
                                    }
                                    String finalQuery = query;
                                    long finalPropertyId = propertyId;
                                    Property property = null;
                                    try {
                                        property = PropertyDataFetcher.fetchPropertySynchronous(finalPropertyId);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                    if (property == null) {
                                        event.getHook().setEphemeral(true).editOriginal("Could not fetch property. Please reach out to <@220622659665264643>.").queue();
                                        break;
                                    }
                                    Neighborhood neighborhood = PropertySynchronizationService.getNeighborhood(property);
                                    if (neighborhood == null) {
                                        event.getHook().setEphemeral(true).editOriginal("Could not fetch neighborhood. Please reach out to <@220622659665264643>.").queue();
                                        break;
                                    }
                                    Property finalProperty = property;
                                    String address = property.getFullAddress();
                                    boolean global = !Node.isValidNeighborhood(neighborhood);
                                    UpaPoolProperty poolProperty = ctx.databaseCaching().getPoolProperties().get(propertyId);
                                    int cost = getPropertyRedeemValue(poolProperty, global);
                                    int givePac = redeem ? -cost : getPropertySubmitValue(property.getMintPrice(), global);
                                    if (!redeem && cost == 0) {
                                        event.getHook().setEphemeral(true).editOriginal("An error has occurred. Please open a ticket in the support channel.").queue();
                                        break;
                                    }
                                    SqlConnectionManager.getInstance().execute(new SqlTask<Void>() {
                                        @Override
                                        public Void execute(Connection connection) throws Exception {
                                            try (PreparedStatement propertyRequest = connection.prepareStatement(finalQuery)) {
                                                if (!redeem) {
                                                    propertyRequest.setInt(1, cost);
                                                    propertyRequest.setLong(2, finalPropertyId);
                                                } else {
                                                    propertyRequest.setLong(1, finalPropertyId);
                                                }
                                                if (propertyRequest.executeUpdate() != 1) {
                                                    event.getHook().setEphemeral(true).editOriginal("Could update pool property in the database. Please reach out to <@220622659665264643>.").queue();
                                                }
                                            } catch (Exception e) {
                                                logger.catching(e);
                                            }
                                            return null;
                                        }
                                    }, result -> {
                                        try {
                                            CreditTransactionType type = givePac < 0 ? CreditTransactionType.REDEEM : CreditTransactionType.PURCHASE;
                                            String reason = redeem ? "purchasing **" + address + "** from the UPA pool" : "offering **" + address + "** to the UPA pool";
                                            ctx.discord().sendCredit(new CreditTransaction(upaMember, (int) Math.floor(givePac), type, reason) {
                                                @Override
                                                public void onSuccess() {
                                                    long propertyId = finalProperty.getPropId();
                                                    if (redeem) {
                                                        ctx.databaseCaching().getPoolProperties().remove(propertyId);
                                                    } else {
                                                        poolProperty.getVerified().set(true);
                                                    }
                                                    event.getHook().setEphemeral(true).editOriginal("PAC has been auto-awarded for this store transaction. Please verify manually in <#1058028231388835840>.").queue();
                                                    event.getInteraction().editButton(null).queue();
                                                }
                                            });
                                        } catch (Exception e) {
                                            logger.catching(e);
                                        }
                                    });
                                    break;
                            }
                            return true;
                        });
                    }
                }
                break;
            case "statement":
                long memberId = event.getMember().getIdLong();
                MessageCreateBuilder statementBldr = statements.remove(memberId);
                if (statementBldr == null) {
                    event.reply("The bot was restarted since this message appeared.").setEphemeral(true).queue();
                    return;
                }
                event.reply(statementBldr.build()).setEphemeral(true).queue();
                event.editButton(event.getButton().asDisabled()).queue();
                break;
        }

    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case "confirm_purchase_with_property_form":
                UpaMember propertyFrom = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
                if (propertyFrom == null || !propertyFrom.getActive().get()) {
                    event.reply("Please become a UPA member by using the /account command before making purchases.").setEphemeral(true).queue();
                    return;
                }

                String propertyLink = event.getValue("confirm_purchase_with_property_form_link").getAsString();
                if (propertyLink.isBlank()) {
                    event.reply("Invalid property link (Should look like 'https://play.upland.me/?prop_id=77296058553954'). If you are having trouble please contact a staff member.").setEphemeral(true).queue();
                    return;
                }
                Long propertyId = Longs.tryParse(propertyLink.replace("https://play.upland.me/?prop_id=", "").trim());
                if (propertyId == null) {
                    event.reply("Invalid property link (Should look like 'https://play.upland.me/?prop_id=77296058553954'). If you are having trouble please contact a staff member.").setEphemeral(true).queue();
                    return;
                }
                event.deferReply(true).queue();
                PropertyDataFetcher.fetchProperty(propertyId, success -> {
                    if (success == null) {
                        event.getHook().setEphemeral(true).editOriginal("Invalid property link [" + propertyLink + "] entered.").queue();
                        return;
                    }
                    City city = DataFetcherManager.getCityMap().get(success.getCityId());
                    if (city == null) {
                        event.getHook().setEphemeral(true).editOriginal("City ID from Upland API [" + success.getCityId() + "] is invalid.").queue();
                        return;
                    }
                    long propertyIdLong = success.getPropId();
                    UpaPoolProperty newPoolProperty = new UpaPoolProperty(propertyIdLong, success.getFullAddress(), city.getName(), success.getMintPrice(), 0, success.getArea(), propertyFrom.getMemberId(), LocalDate.now());
                    SqlConnectionManager.getInstance().execute(new SqlTask<UpaPoolProperty>() {
                        @Override
                        public UpaPoolProperty execute(Connection connection) throws Exception {
                            try (PreparedStatement insertPoolProperty = connection.prepareStatement("INSERT INTO pool_properties (property_id, address, city_name, mint_price, up2, donor_member_id, listed_on) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                                insertPoolProperty.setLong(1, success.getPropId());
                                insertPoolProperty.setString(2, success.getFullAddress());
                                insertPoolProperty.setString(3, city.getName());
                                insertPoolProperty.setInt(4, success.getMintPrice());
                                insertPoolProperty.setInt(5, success.getArea());
                                insertPoolProperty.setLong(6, propertyFrom.getMemberId());
                                insertPoolProperty.setDate(7, Date.valueOf(newPoolProperty.getListedOn()));
                                if (insertPoolProperty.executeUpdate() != 1) {
                                    event.getHook().setEphemeral(true).editOriginal("Could not add pool property to the database. Please reach out to <@220622659665264643>.").queue();
                                } else {
                                    ctx.databaseCaching().
                                            getPoolProperties().put(propertyIdLong, newPoolProperty);
                                    return newPoolProperty;
                                }
                            } catch (Exception e) {
                                logger.catching(e);
                            }
                            return null;
                        }
                    }, result -> {
                        if (result != null) {
                            ctx.discord().sendBotRequestsMsg(propertyFrom, "trading " + result.getDescriptiveAddress() + " for PAC",
                                    msg -> {
                                        ctx.variables().storeRequests().access(storeRequests -> storeRequests.get().putIfAbsent(msg.getIdLong(), new UpaStoreRequest(propertyFrom.getMemberId(), msg.getIdLong(), propertyId, "a property transfer to the UPA pool (" + result.getDescriptiveAddress() + ").", RequestType.PROPERTY)) == null);
                                        event.getHook().setEphemeral(true).editOriginal("Your request to trade " + result.getDescriptiveAddress() + " for PAC was sent to the UPA team. You'll hear from us shortly!").queue();
                                    });
                        }
                    });
                });
                break;
            case "confirm_help_with_paid_sends_form":
                UpaMember helpFor = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
                if (helpFor == null || !helpFor.getActive().get()) {
                    event.reply("Please use the /account command before requesting help for purchases.").setEphemeral(true).queue();
                    return;
                }
                String helpNeeded = event.getValue("confirm_help_with_paid_sends").getAsString();
                event.deferReply(true).queue();
                ctx.discord().sendBotRequestsMsg(helpFor, "purchasing PAC using visits.\n\nTheir issue: " + helpNeeded,
                        msg -> event.getHook().setEphemeral(true).editOriginal("Your request for help regarding sends was sent to the UPA team. You'll hear from us shortly!").queue());
                break;
            case "confirm_redeem_for_property_form":
                UpaMember propertyFor = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
                if (propertyFor == null || !propertyFor.getActive().get()) {
                    event.reply("Please become a UPA member by using the /account command before redeeming PAC.").setEphemeral(true).queue();
                    return;
                }
                propertyLink = event.getValue("confirm_redeem_for_property_link").getAsString();
                if (propertyLink.isBlank()) {
                    event.reply("Invalid property link (Should look like 'https://play.upland.me/?prop_id=77296058553954'). If you are having trouble please contact a staff member.").setEphemeral(true).queue();
                    return;
                }
                propertyId = Longs.tryParse(propertyLink.replace("https://play.upland.me/?prop_id=", "").trim());
                if (propertyId == null) {
                    event.reply("Invalid property link (Should look like 'https://play.upland.me/?prop_id=77296058553954'). If you are having trouble please contact a staff member.").setEphemeral(true).queue();
                    return;
                }
                UpaPoolProperty poolProperty = ctx.databaseCaching().getPoolProperties().get(propertyId);
                if (poolProperty == null) {
                    event.reply("Invalid pool property link selected.").setEphemeral(true).queue();
                    return;
                }
                event.deferReply(true).queue();
                ctx.discord().sendBotRequestsMsg(propertyFor, "redeeming " + poolProperty.getAddress() + " from the UPA pool",
                        msg -> {
                            ctx.variables().storeRequests().access(storeRequests -> storeRequests.get().putIfAbsent(msg.getIdLong(), new UpaStoreRequest(propertyFor.getMemberId(), msg.getIdLong(), -poolProperty.getPropertyId(), poolProperty.getAddress() + " from the UPA pool", RequestType.PROPERTY)) == null);
                            event.getHook().setEphemeral(true).editOriginal("Your request to trade " + propertyLink + " for PAC was sent to the UPA team. You'll hear from us shortly!").queue();
                        });
                break;
        }
    }

    private void redeemForProperty(ButtonInteractionEvent event, boolean edit) {
        var poolProperties = ctx.databaseCaching().getPoolProperties().values();
        if (poolProperties.isEmpty()) {
            String msg = REDEEM_FOR_PROPERTY_INFO + "\n\nThere are currently no eligible properties that can be redeemed. Please check back in a few days.";
            if (edit) {
                event.getInteraction().editMessage(msg).queue();
            } else {
                event.reply(msg).setEphemeral(true).queue();
            }
        } else {
            var menuBldr = SelectMenu.create("confirm_redeem_for_property_select");
            for (UpaPoolProperty property : poolProperties) {
                if (!property.getVerified().get()) {
                    continue;
                }
                menuBldr.addOption(property.getDescriptiveAddress(), Long.toString(property.getPropertyId()), "Mint price: " + DiscordService.COMMA_FORMAT.format(property.getMintPrice()) + " UPX | Size: " + property.getUp2() + " UP2 | Cost: " + DiscordService.COMMA_FORMAT.format(property.getCost().get()) + " PAC");
            }
            MessageCreateBuilder mb = new MessageCreateBuilder().setContent(REDEEM_FOR_PROPERTY_INFO).setComponents(ActionRow.of(menuBldr.build()));
            if (edit) {
                event.getInteraction().editMessage(MessageEditData.fromCreateData(mb.build())).queue();
            } else {
                event.reply(mb.build()).setEphemeral(true).queue();
            }
        }
        if (!edit) {
            //  event.getInteraction().editButton(event.getButton().asDisabled()).queue();
        }
    }

    private void handlePurchaseButton(ButtonInteractionEvent event) {
        DatabaseCachingService databaseCaching = ctx.databaseCaching();
        UpaMember requester = databaseCaching.getMembers().get(event.getMember().getIdLong());
        if (requester == null || !requester.getActive().get()) {
            event.reply("Please use the /account command before making purchases.").setEphemeral(true).queue();
            return;
        }
        event.reply("Please select which payment method you are interested in **purchasing** PAC with below.").setEphemeral(true).addActionRow(
                Button.of(ButtonStyle.PRIMARY, "purchase_with_upx", "Use UPX", Emoji.fromCustom("upx", 987836478455431188L, true)),
                Button.of(ButtonStyle.PRIMARY, "purchase_with_visits", "Use visits", Emoji.fromUnicode("U+1F6EB")),
                Button.of(ButtonStyle.PRIMARY, "purchase_with_property", "Use a property", Emoji.fromUnicode("U+1F3E0"))).queue();
    }

    private void handleRedeemButton(ButtonInteractionEvent event) {
        DatabaseCachingService databaseCaching = ctx.databaseCaching();
        UpaMember requester = databaseCaching.getMembers().get(event.getMember().getIdLong());
        if (requester == null || !requester.getActive().get()) {
            event.reply("Please use the /account command before trying to redeem rewards.").setEphemeral(true).queue();
            return;
        }
        event.reply("Please select which reward you are interested in **redeeming** below.\nYou have **" + DiscordService.COMMA_FORMAT.format(requester.getCredit().get()) + " PAC** available to spend.").setEphemeral(true).addActionRow(
                Button.of(ButtonStyle.PRIMARY, "redeem_for_upx", "UPX", Emoji.fromCustom("upx", 987836478455431188L, true)),
                Button.of(ButtonStyle.PRIMARY, "redeem_for_ssh", "Spark share hours (SSH)", Emoji.fromUnicode("U+26A1")),
                Button.of(ButtonStyle.PRIMARY, "redeem_for_property", "Properties", Emoji.fromUnicode("U+1F3E0")),
                Button.of(ButtonStyle.PRIMARY, "redeem_for_th_map", "Treasure hunting map", Emoji.fromUnicode("U+1F5FA")),
                Button.of(ButtonStyle.PRIMARY, "redeem_for_portfolio_analysis", "Portfolio analysis", Emoji.fromUnicode("U+1F4C8"))

        ).queue();
    }

    public static void handleTransferCommand(UpaBotContext ctx, IReplyCallback event, Member receiverMember, long inputAmount, String reason, CreditTransactionType type) {
        DatabaseCachingService databaseCaching = ctx.databaseCaching();
        Member senderMember = event.getMember();
        int amount = Ints.saturatedCast(inputAmount);
        if (receiverMember == null) {
            event.reply("Member could not be found on this Discord server.").setEphemeral(true).queue();
            return;
        }
        if (amount < 100) {
            event.reply("You must transfer a minimum of 100 PAC.").setEphemeral(true).queue();
            return;
        }
        UpaMember receiver = databaseCaching.getMembers().get(receiverMember.getIdLong());
        if (receiver == null || !receiver.getActive().get()) {
            event.reply("Please use /account before trying to transfer PAC to another member.").setEphemeral(true).queue();
            return;
        }
        UpaMember sender = databaseCaching.getMembers().get(senderMember.getIdLong());
        if (sender == null || !sender.getActive().get()) {
            event.reply("The member you are transferring to needs to use /account before they can receive PAC.").setEphemeral(true).queue();
            return;
        }
        int senderPac = sender.getCredit().get();
        int diff = senderPac - amount;
        if (diff < 0) {
            event.reply("You only have **" + senderPac + " PAC** (Need additional **" + diff + " PAC**).").setEphemeral(true).queue();
            return;
        }
        if (type == CreditTransactionType.TIP) {
            event.deferReply().queue();
        } else {
            event.deferReply().setEphemeral(true).queue();
        }
        CreditTransfer transfer = type == CreditTransactionType.TIP ? new TipTransfer(sender, receiver, amount) :
                new CreditTransfer(sender, receiver, amount, reason);
        SqlConnectionManager.getInstance().execute(new CreditTransferTask(transfer),
                success -> {
                    receiver.getCredit().addAndGet(transfer.getAmount());
                    sender.getCredit().addAndGet(-transfer.getAmount());
                    ctx.discord().sendCreditMessage(transfer);
                    ctx.variables().pacTransactions().accessValue(repo -> {
                        String historyReason = transfer.getHistoryReason();
                        repo.store(new PacTransaction(transfer.getAmount(), historyReason, receiver.getMemberId(), Instant.now()));
                        repo.store(new PacTransaction(-transfer.getAmount(), historyReason, sender.getMemberId(), Instant.now()));
                        return true;
                    });

                    switch (type) {
                        case TRANSFER:
                            event.getHook().setEphemeral(true).editOriginal("Transfer of " + amount + " PAC to <@" + receiver.getMemberId() + "> complete. You should see a confirmation in <#1058028231388835840> shortly. ").queue();
                            break;
                        case TIP:
                            event.getHook().editOriginal("Successfully tipped <@" + receiver.getMemberId() + "> **" + amount + " PAC**. I bet they appreciated that.").queue();
                            break;
                    }
                }, failure -> event.getHook().setEphemeral(true).editOriginal("Transfer of " + amount + " PAC to <@" + receiver.getMemberId() + "> failed. Please try again later.").queue());
    }

    public static void handleDailyCommand(UpaBotContext ctx, long memberId, IReplyCallback event) {
        if (event != null)
            event.deferReply().setEphemeral(true).queue();
        ctx.discord().execute(() -> {
            UpaMember upaMember = ctx.databaseCaching().getMembers().get(memberId);
            if (upaMember == null || !upaMember.getActive().get()) {
                if (event != null)
                    event.getHook().setEphemeral(true).editOriginal("You must use /account before claiming a dividend.").queue();
                return;
            }
            Instant lastClaimed = upaMember.getClaimedDailyAt().get();
            Instant lastReset = ctx.variables().lastDailyReset().get();
            if (lastClaimed.isBefore(lastReset)) {
                if (event != null && (upaMember.getTotalSsh(false) < 0 || upaMember.getTotalSsh(true) < 0)) {
                    Profile nextProfile = ProfileDataFetcher.fetchProfileSynchronous(upaMember.getInGameName());
                    if (nextProfile == DUMMY) {
                        event.getHook().setEphemeral(true).editOriginal("Profile could not be fetched. Please try again later.").queue();
                        return;
                    }
                    double staking = upaMember.getHollisSparkTrainStaked().get() + upaMember.getGlobalSparkTrainStaked().get();
                    double req;
                    if (nextProfile.getNetWorth() < 100_000) {
                        req = 0.01;
                    } else if (nextProfile.getNetWorth() < 1_000_000) {
                        req = 0.15;
                    } else if (nextProfile.getNetWorth() < 10_000_000) {
                        req = 0.75;
                    } else {
                        req = 2;
                    }
                    if (staking < req) {
                        event.getHook().setEphemeral(true).editOriginal("You have negative SSH, so you'll need to have at least " + req + " total spark staked before claiming your daily.").queue();
                        return;
                    }
                }
                Instant now = Instant.now();
                try (Connection connection = SqlConnectionManager.getInstance().take();
                     PreparedStatement updateDaily = connection.prepareStatement("UPDATE members SET claimed_daily_at = ? WHERE member_id = ?;")) {
                    updateDaily.setString(1, now.toString());
                    updateDaily.setLong(2, memberId);
                    int row = updateDaily.executeUpdate();
                    if (row != 1) {
                        logger.error("Unexpected row result when updating daily (was " + row + ", expected 1).");
                        if (event != null) {
                            event.getHook().setEphemeral(true).editOriginal("Unexpected result when updating daily record.").queue();
                            return;
                        }
                    }
                } catch (SQLException e) {
                    logger.catching(e);
                    if (event != null) {
                        event.getHook().setEphemeral(true).editOriginal("Error when updating daily record.").queue();
                        return;
                    }
                }
                upaMember.getClaimedDailyAt().set(now);
                int baseDividend = 50;
                var countObj = new Object() {
                    int count = 0;
                    int genCount = 0;
                };
                long hoursDiff = lastClaimed.until(now, ChronoUnit.HOURS);
                if (hoursDiff >= 168) {
                    hoursDiff = 168;
                }
                Set<UpaProperty> nodeProperties = ctx.databaseCaching().getMemberProperties().get(upaMember.getKey());
                double yieldPerHour = nodeProperties.isEmpty() ? 0 : nodeProperties.stream().mapToDouble(next -> {
                    double base;
                    if (next.isGenesis()) {
                        countObj.genCount++;
                        base = 5.0;
                    } else {
                        countObj.count++;
                        base = 1.0;
                    }
                    if (next.hasBuild()) {
                        base += 2;
                    }
                    return base / 24.0;
                }).sum();
                long dividend = (long) (baseDividend + (yieldPerHour * hoursDiff));
                boolean active = UpaEvent.isActive(ctx, DoubleDividendEventHandler.class);
                if (active) {
                    dividend *= 2;
                }
                long finalHoursDiff = hoursDiff;
                long finalDividend = dividend;
                ctx.discord().sendCredit(new CreditTransaction(upaMember, Ints.checkedCast(finalDividend), CreditTransactionType.DAILY, null) {
                    @Override
                    public void onSuccess() {
                        if (event != null) {
                            statements.put(memberId, new MessageCreateBuilder().
                                    setContent(new StringBuilder().
                                            append("```\n").
                                            append("Base dividend\t").append("25 PAC\n").
                                            append("Yield per hour\t").append(DiscordService.DECIMAL_FORMAT.format(yieldPerHour)).append(" PAC\n").
                                            append("Last claimed\t").append(finalHoursDiff).append(" hours\n\n").
                                            append("Double dividends active?\t").append(active ? "Yes" : "No").append("\n\n").
                                            append("Total\t").append(finalDividend).append(" PAC\n").append("```").toString()));
                            event.getHook().setEphemeral(true).editOriginal(new MessageEditBuilder().setComponents(
                                    ActionRow.of(Button.of(ButtonStyle.SUCCESS, "statement", "Statement", Emoji.fromUnicode("U+1F9FE")))
                            ).setContent("You have claimed your dividend of **" + finalDividend + " PAC**. You should see the transaction in <#1058028231388835840> shortly.").build()).queue();
                        }
                    }
                });
            } else if (event != null) {
                event.getHook().setEphemeral(true).editOriginal("You can claim your daily in " + DailyResetMicroService.checkIn(ctx) + ".").queue();
            }
        });
    }
}
