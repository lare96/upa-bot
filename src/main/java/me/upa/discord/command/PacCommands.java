package me.upa.discord.command;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import me.upa.UpaBot;

import me.upa.discord.CreditTransaction;
import me.upa.discord.CreditTransaction.CreditTransactionType;
import me.upa.discord.CreditTransfer;
import me.upa.discord.CreditTransferTask;
import me.upa.discord.DiscordService;
import me.upa.discord.TipTransfer;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaPoolProperty;
import me.upa.discord.UpaProperty;
import me.upa.discord.UpaStoreRequest;
import me.upa.discord.UpaStoreRequest.RequestType;
import me.upa.fetcher.DataFetcherManager;
import me.upa.fetcher.PropertyDataFetcher;
import me.upa.game.City;
import me.upa.game.Property;
import me.upa.service.DailyResetMicroService;
import me.upa.service.DatabaseCachingService;
import me.upa.service.PropertySynchronizationService;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public final class PacCommands extends ListenerAdapter {

    private static final class UpaVisitProperty {
        private final String address;
        private final String propertyId;

        private UpaVisitProperty(String address, String propertyId) {
            this.address = address;
            this.propertyId = propertyId;
        }
    }

    public static ImmutableMap<String, UpaVisitProperty> UPA_VISIT_PROPERTIES;
    public static ImmutableSet<String> UPA_VISIT_PROPERTY_IDS;

    static {
        UPA_VISIT_PROPERTIES = ImmutableMap.of(
                "Nashville", new UpaVisitProperty("453 CLEARWATER DR", "78491016674865"),
                "Los Angeles", new UpaVisitProperty("7412 LOMA VERDE AVE", "77324378489633"),
                "Las Vegas", new UpaVisitProperty("4528 MONTEBELLO AVE", "78547437595171"),
                "Chicago", new UpaVisitProperty("6932 S UNION AVE", "82032669949269"),
                "Brooklyn", new UpaVisitProperty("1016 E 100TH ST", "81332322277927"),
                "Detroit", new UpaVisitProperty("15509 ROSA PARKS BLVD", "82429803527462"),
                "Queens", new UpaVisitProperty("19048 103RD AVE", "81372453379329")
        );
        UPA_VISIT_PROPERTY_IDS = UPA_VISIT_PROPERTIES.values().stream().map(next -> next.propertyId).collect(ImmutableSet.toImmutableSet());
    }

    private static final int UPX_PER_PAC_RATE = 2;
    private static final int PAC_PER_UPX_RATE = 5;
    private static final int PAC_PER_SSH_RATE = 50;


    private static final int MINIMUM_UPX_FOR_PAC = 2000;
    private static final int MINIMUM_PAC_FOR_SSH = 10;
    private static final int MINIMUM_PAC_FOR_UPX = 1000;

    private static final String PURCHASE_WITH_UPX_INFO = "__**Rate**__ " + UPX_PER_PAC_RATE + " PAC per 1 UPX (ie. 5,000 UPX would get you 10,000 PAC).\n\n" +
            "__**How it works**__\n" +
            "-> A staff member will contact you once you've made a purchase request\n" +
            "-> You pay using an in-game UPX transfer or one of our burner properties\n" +
            "-> We give you PAC at the current rate, which you can instantly redeem for rewards! (/pac redeem)\n\n" +
            "Minimum purchase amount is " + MINIMUM_UPX_FOR_PAC + " PAC.";
    private static final String PURCHASE_WITH_VISIT_INFO = "__**Rate**__: 100% of visit fee (ie. 100 UPX visit = 100 PAC per visit).\n\n" +
            "__**How it works**__\n" +
            "-> Choose your desired city from our list of available cities\n" +
            "-> You will be shown the property you can send to in that city to earn PAC\n" +
            "-> Follow the instructions in order to receive PAC from your sends\n\n" +
            "Please note that you can also earn PAC for visiting the properties of newer players. Use '/scholarship' for more info.";
    private static final String PURCHASE_WITH_PROPERTY_INFO = "__**Rate**__\n" +
            "-> Hollis, Queens properties: Mint price * 2.5 (ie. If the mint price is 5k, you would get 12.5k PAC)\n" +
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
            "-> We will deduct the proportionate amount of PAC\n\n" +
            "Minimum cash out amount is " + MINIMUM_PAC_FOR_UPX + " UPX.";
    private static final String REDEEM_FOR_SSH_INFO = "__**Rate**__ " + PAC_PER_SSH_RATE + " PAC per SSH (ie. 5000 PAC would get you 100 SSH)\n\n" +
            "__**How it works**__\n" +
            "-> Enter the amount of SSH you would like to purchase\n" +
            "-> Confirm that you would like to go through with the purchase\n" +
            "-> I will automatically award you with your spark share hours\n\n" +
            "A minimum of " + MINIMUM_PAC_FOR_SSH + " spark hours must be redeemed.";
    private static final String REDEEM_FOR_PROPERTY_INFO = "__**Rate**__\n" +
            "-> Hollis, Queens properties: Mint price * 3.5 (ie. If the mint price is 5k, you would pay 17.5k PAC)\n" +
            "-> Other properties: Mint price * 2.5 (ie. If the mint price is 5k, you would pay 12.5k PAC)\n\n" +
            "__**How it works**__\n" +
            "-> A staff member will contact you once you've made a redeem request\n" +
            "-> We will swap your requested property with the cheapest mint you own\n" +
            "-> We will send you the UPX required to buy the property back at mint price";
    private static final String REDEEM_FOR_TH_MAP_INFO = "__**Rate**__: -> 1000 PAC per map\n\n" +
            "__**How it works**__\n" +
            "<@373218455937089537> Will contact you with your map for additional details, and send it to you as soon as it's ready.\n\n" +
            "Here is an example of what it will look like https://i.imgur.com/SQ7gcux.png";
    private static final Logger logger = LogManager.getLogger();

    private final Map<Long, MessageBuilder> statements = new ConcurrentHashMap<>();

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("pac")) {
            switch (event.getSubcommandName()) {
                case "daily":
                    handleDailyCommand(event);
                    break;
                case "send":
                    handleTransferCommand(event, event.getOptions().get(0).getAsMember(),
                            event.getOptions().get(1).getAsLong(),
                            event.getOptions().get(2).getAsString(), CreditTransactionType.TRANSFER);
                    break;
                case "store":
                    openStore(event);
                    break;
            }
        }
    }

    public static void openStore(IReplyCallback event) {
        event.reply("Which part of the store would you like to access?").setEphemeral(true).addActionRow(
                openStoreButtons()).queue();
    }

    public static List<Button> openStoreButtons() {
        return List.of(
                openStoreInfoButton(),
                Button.of(ButtonStyle.PRIMARY, "purchase", "Purchase PAC", Emoji.fromUnicode("U+1F4B3")),
                Button.of(ButtonStyle.PRIMARY, "redeem", "Redeem your PAC", Emoji.fromUnicode("U+1F381"))
        );
    }

    public static Button openStoreInfoButton() {
        return Button.of(ButtonStyle.LINK, "https://docs.google.com/spreadsheets/d/e/2PACX-1vRg1dqgSwj7zbY8qXHviXqNj55ljlBma2KaLYupfIK0V2QUTRLLScWvSEttlbOku4gawap9zc2Z2MOz/pubhtml?gid=2012586904&single=true", "Information ", Emoji.fromUnicode("U+1F50E"));
    }

    @Override
    public void onSelectMenuInteraction(@NotNull SelectMenuInteractionEvent event) {
        if (event.getSelectMenu().getId() != null) {
            switch (event.getSelectMenu().getId()) {
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
                    UpaPoolProperty poolProperty = UpaBot.getDatabaseCachingService().getPoolProperties().get(propertyId);
                    if (poolProperty == null) {
                        event.reply("Selected pool property can no longer be found. Someone may have claimed it already.").setEphemeral(true).queue();
                        return;
                    }
                    MessageBuilder mb = new MessageBuilder().setContent(" ").setEmbeds(new EmbedBuilder().
                            setDescription("Are you sure you'd like to purchase **" + poolProperty.getDescriptiveAddress() + "** for **" +
                                    DiscordService.COMMA_FORMAT.format(poolProperty.getCost().get()) + " PAC**?").
                            addField("Mint price", String.valueOf(poolProperty.getMintPrice()), false).
                            addField("UP2", String.valueOf(poolProperty.getUp2()), false).
                            addField("Previous owner", "<@" + poolProperty.getDonorMemberId() + ">", false).
                            addField("Property link", "https://play.upland.me/?prop_id=" + poolProperty.getPropertyId(), false).
                            setColor(Color.GREEN).
                            build()).setActionRows(ActionRow.of(
                            Button.of(ButtonStyle.SUCCESS, "confirm_redeem_for_property_select" + poolProperty.getPropertyId(), "Yes!", Emoji.fromUnicode("U+1F3E0")),
                            Button.of(ButtonStyle.DANGER, "cancel_redeem_for_property_select", "No", Emoji.fromUnicode("U+270B"))
                    ));
                    event.getInteraction().editMessage(mb.build()).queue();
                    break;
            }
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null)
            return;
        if (event.getButton().getId().startsWith("confirm_redeem_for_property_select")) {
            UpaMember upaMember = UpaBot.getDatabaseCachingService().getMembers().get(event.getMember().getIdLong());
            if (upaMember == null) {
                event.getInteraction().editMessage("Please become a UPA member by using /account before doing this.").queue();
                return;
            }

            Long propertyId = Longs.tryParse(event.getButton().getId().replace("confirm_redeem_for_property_select", ""));
            Collection<ActionRow> delete = null;
            MessageBuilder mb = new MessageBuilder().setActionRows(delete).setEmbeds(List.of());
            if (propertyId == null) {
                event.getInteraction().editMessage(mb.setContent("Error. Please try again.").build()).queue();
                return;
            }
            UpaPoolProperty upaProperty = UpaBot.getDatabaseCachingService().getPoolProperties().get(propertyId);
            if (upaProperty == null) {
                event.getInteraction().editMessage(mb.setContent("Property was claimed by another member.").setEmbeds().setActionRows().build()).queue();
                return;
            }
            int propertyCost = upaProperty.getCost().get();
            int diff = upaMember.getCredit().get() - propertyCost;
            if (diff < 0) {
                event.getInteraction().editMessage("You need an additional **" + DiscordService.COMMA_FORMAT.format(Math.abs(diff)) + " PAC** in order to purchase this.").setEmbeds().setActionRows().queue();
                return;
            }
            UpaBot.getDiscordService().sendBotRequestsMsg(upaMember, "purchasing **" + upaProperty.getDescriptiveAddress() + "** for **" + upaProperty.getCost() + " PAC**",
                    msg -> {
                        event.getInteraction().editMessage("Your request to trade **" +
                                upaProperty.getDescriptiveAddress() + "** for PAC was sent to the UPA team. You'll hear from us shortly!").queue();
                        UpaBot.variables().storeRequests().accessValue(storeRequests -> storeRequests.putIfAbsent(msg.getIdLong(), new UpaStoreRequest(event.getMember().getIdLong(), msg.getIdLong(), -propertyId, "redeeming " + upaProperty.getDescriptiveAddress(), RequestType.PROPERTY)) == null);
                    });
            return;
        }
        switch (event.getButton().getId()) {
            case "cancel_redeem_for_property_select":
                redeemForProperty(event, true);
                break;
          /*  case "info":
                StringBuilder sb = new StringBuilder();
                sb.append("__**How to obtain PAC?**__\n\n");
                sb.append(":calendar: __Daily dividend__\n").
                        append("-> Use '/pac daily' to claim your dividend for being a member. You must own at least 1 property in Hollis\n\n");
                sb.append(":arrow_forward: __Sending__\n").
                        append("-> Visit scholar properties. Use '/scholarships' for more info\n").
                        append("-> Visit designated UPA properties (Purchase PAC through visits)\n\n");
                sb.append(":rocket: __Events__\n").
                        append("-> Participating in events will award PAC\n\n");
                sb.append(Emoji.fromEmote("upx", 987836478455431188L, true).getAsMention()).append(" __UPX Transfer__\n").
                        append("-> Purchase PAC through in-game UPX transfer\n\n");
                sb.append(":busts_in_silhouette: __Referrals__\n").
                        append("-> Invite users to the server\n\n");
                sb.append(":house: __Property transfer__\n").
                        append("-> Purchase PAC through donating a property to the UPA pool\n\n\n");
                sb.append("__**How can I spend PAC?**__\n\n");
                sb.append(Emoji.fromEmote("upx", 987836478455431188L, true).getAsMention()).
                        append(" __UPX__\n").
                        append("-> Redeem your PAC for UPX. You will be paid UPX through in-game transfer or a burner property\n\n");
                sb.append(":zap: __SSH__\n").
                        append("-> Redeem your PAC for spark share hours (SSH).\n").
                        append("-> Visit designated UPA properties (Purchase PAC through visits)\n\n");
                sb.append(":house: __Pool properties__\n").
                        append("-> Redeem your PAC for a property from the UPA pool");
                event.reply(sb.toString()).setEphemeral(true).queue();
     event.getHook().setEphemeral(true).sendMessageEmbeds(new EmbedBuilder().
             set("[PAC Earn/Redeem Amounts](https://docs.google.com/spreadsheets/d/e/2PACX-1vRg1dqgSwj7zbY8qXHviXqNj55ljlBma2KaLYupfIK0V2QUTRLLScWvSEttlbOku4gawap9zc2Z2MOz/pubhtml?gid=2012586904&single=true)").build()).queue();
                //           event.getHook().setEphemeral(true).sendMessage("You can view an up-to-date spreadsheet of all ways to earn and redeem PAC here.\n\n").queue();
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;*/
            case "purchase":
                handlePurchaseButton(event);
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "redeem":
                handleRedeemButton(event);
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "confirm_redeem_for_upx":
                event.replyModal(Modal.create("confirm_redeem_for_upx_form", "How much UPX would you like to redeem?").
                        addActionRow(TextInput.create("confirm_redeem_for_upx_amount", "Enter amount", TextInputStyle.SHORT).
                                setRequired(true).setPlaceholder(Integer.toString(MINIMUM_PAC_FOR_UPX)).build()).build()).queue();
                //event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "confirm_redeem_for_ssh":
                event.replyModal(Modal.create("confirm_redeem_for_ssh_form", "How much SSH would you like to redeem?").
                        addActionRow(TextInput.create("confirm_redeem_for_ssh_amount", "Enter amount", TextInputStyle.SHORT).
                                setRequired(true).setPlaceholder(Integer.toString(MINIMUM_PAC_FOR_SSH)).build()).build()).queue();
                // event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "confirm_redeem_for_th_map":
                UpaMember requester = UpaBot.getDatabaseCachingService().getMembers().get(event.getMember().getIdLong());
                if (requester == null) {
                    event.reply("Please use the /account command before making purchases.").setEphemeral(true).queue();
                    return;
                }
                int pacAmount = requester.getCredit().get();
                if (pacAmount < 1000) {
                    event.reply("You need at least **1000 PAC** to redeem this (You have **" + pacAmount + " PAC**).").setEphemeral(true).queue();
                    return;
                }
                UpaBot.getDiscordService().sendBotRequestsMsg(requester, "redeeming a treasure hunting map",
                        msg -> {
                            UpaBot.variables().storeRequests().access(storeRequests -> storeRequests.get().putIfAbsent(msg.getIdLong(), new UpaStoreRequest(requester.getMemberId(), msg.getIdLong(), -1000, "a custom treasure hunting map", RequestType.PAC)) == null);
                            event.getHook().setEphemeral(true).editOriginal("Your request to redeem a custom TH map has been sent to the UPA staff team. You'll hear from us shortly!").queue();
                        });
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "help_with_paid_sends":
                TextInput helpDescription = TextInput.create("confirm_help_with_paid_sends", "Enter details", TextInputStyle.PARAGRAPH).setRequired(true).setPlaceholder("I visited <property> 10 times. I should've received 1000 PAC, but I only got 500.").build();
                event.replyModal(Modal.create("confirm_help_with_paid_sends_form", "Unawarded PAC for sends").addActionRow(helpDescription).build()).queue();
                //    event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "confirm_purchase_with_upx":
                TextInput upxAmountInput = TextInput.create("confirm_purchase_with_upx_amount", "Enter amount", TextInputStyle.SHORT).setRequired(true).setPlaceholder(Integer.toString(MINIMUM_UPX_FOR_PAC)).build();
                event.replyModal(Modal.create("confirm_purchase_with_upx_form", "How much PAC would you like to purchase?").addActionRow(upxAmountInput).build()).queue();
                //   event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "confirm_purchase_with_property":
                TextInput amountInput = TextInput.create("confirm_purchase_with_property_form_link", "Property link", TextInputStyle.SHORT).setRequired(true).setPlaceholder("https://play.upland.me/?prop_id=77296058553954").build();
                Modal modal = Modal.create("confirm_purchase_with_property_form", "Which property would you like to trade in?").addActionRow(amountInput).build();
                event.replyModal(modal).queue();
                //  event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "purchase_with_upx":
                event.reply(PURCHASE_WITH_UPX_INFO).setEphemeral(true).addActionRow(
                        Button.of(ButtonStyle.SUCCESS, "confirm_purchase_with_upx", "Buy PAC using UPX", Emoji.fromUnicode("U+2705"))).queue();
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "purchase_with_visits":
                SelectMenu.Builder eligibleCities = SelectMenu.create("choose_city");
                for (var next : UPA_VISIT_PROPERTIES.entrySet()) {
                    String city = next.getKey();
                    eligibleCities.addOption(city, city.toLowerCase());
                }
                event.reply(PURCHASE_WITH_VISIT_INFO).setEphemeral(true).addActionRow(eligibleCities.build()).queue();
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "purchase_with_property":
                if (event.getMember() == null)
                    return;
                String username = UpaBot.getDatabaseCachingService().getMemberNames().get(event.getMember().getIdLong());
                if (username == null) {
                    event.reply("Please use /account before doing this.").setEphemeral(true).queue();
                    return;
                }
                event.reply(PURCHASE_WITH_PROPERTY_INFO + "Go to http://upxland.me/users/" + username + " and select the link of the property you wish to use from here https://i.imgur.com/WpdAnu9.png").setEphemeral(true).addActionRow(
                        Button.of(ButtonStyle.SUCCESS, "confirm_purchase_with_property", "Buy PAC using a property", Emoji.fromUnicode("U+2705"))).queue();
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "redeem_for_upx":
                event.reply(REDEEM_FOR_UPX_INFO).setEphemeral(true).addActionRow(
                        Button.of(ButtonStyle.SUCCESS, "confirm_redeem_for_upx", "Redeem UPX", Emoji.fromUnicode("U+2705"))).queue();
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "redeem_for_ssh":
                event.reply(REDEEM_FOR_SSH_INFO).setEphemeral(true).addActionRow(
                        Button.of(ButtonStyle.SUCCESS, "confirm_redeem_for_ssh", "Redeem SSH", Emoji.fromUnicode("U+2705"))).queue();
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "redeem_for_property":
                redeemForProperty(event, false);
                break;
            case "redeem_for_th_map":
                event.reply(REDEEM_FOR_TH_MAP_INFO).setEphemeral(true).addActionRow(
                        Button.of(ButtonStyle.SUCCESS, "confirm_redeem_for_th_map", "Redeem a TH map", Emoji.fromUnicode("U+2705"))).queue();
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "assign_yourself":
                if (event.getTextChannel().getIdLong() == 984551707176480778L && event.getMember() != null && !event.getMember().getUser().isBot()) {
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
                if (event.getTextChannel().getIdLong() == 984551707176480778L && event.getMember() != null && !event.getMember().getUser().isBot()) {
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
                        UpaBot.variables().storeRequests().access(sq -> {
                            var storeRequests = sq.get();
                            UpaStoreRequest request = storeRequests.remove(success.getIdLong());
                            if (request == null) {
                                return false;
                            }
                            UpaMember upaMember = UpaBot.getDatabaseCachingService().getMembers().get(request.getMemberId());
                            if (upaMember == null) {
                                event.getHook().setEphemeral(true).editOriginal("Request could not be satisfied. Member not found [ID = " + request.getMemberId() + "].").queue();
                                return false;
                            }
                            switch (request.getType()) {
                                case PAC:
                                    CreditTransactionType transactionType = request.getValue() < 0 ? CreditTransactionType.REDEEM : CreditTransactionType.PURCHASE;
                                    UpaBot.getDiscordService().sendCredit(new CreditTransaction(upaMember, Ints.checkedCast(request.getValue()), transactionType, request.getReason()) {
                                        @Override
                                        public void onSuccess() {
                                            event.getHook().setEphemeral(true).editOriginal("PAC has been auto-awarded for this store transaction. Please verify manually in <#983628894919860234>.").queue();
                                            event.getInteraction().editButton(null).queue();
                                        }
                                    });
                                    break;
                                case PROPERTY:
                                    long propertyId = request.getValue();
                                    boolean redeem = propertyId < 0;
                                    propertyId = redeem ? Math.abs(propertyId) : propertyId;
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
                                    int mintPrice = property.getMintPrice();
                                    boolean isHollis = PropertySynchronizationService.withinBounds(property, "Hollis");
                                    double redeemHollisPac = mintPrice * 3.5;
                                    double redeemOtherPac = mintPrice * 2.5;
                                    double hollisPac = redeem ? -redeemHollisPac : mintPrice * 2.5;
                                    double otherPac = redeem ? -redeemOtherPac : mintPrice * 1.5;
                                    double givePac = isHollis ?
                                            hollisPac : otherPac;
                                    int cost = (int) Math.floor(isHollis ? redeemHollisPac : redeemOtherPac);
                                    Property finalProperty = property;
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
                                            String reason = redeem ? "purchasing **"+ finalProperty.getFullAddress() + "** from the UPA pool" : "offering **" + finalProperty.getFullAddress() + "** to the UPA pool";
                                            UpaBot.getDiscordService().sendCredit(new CreditTransaction(upaMember, (int) Math.floor(givePac), type, reason) {
                                                @Override
                                                public void onSuccess() {
                                                    long propertyId = finalProperty.getPropId();
                                                    if (redeem) {
                                                        UpaBot.getDatabaseCachingService().getPoolProperties().remove(propertyId);
                                                    } else {
                                                        UpaPoolProperty poolProperty = UpaBot.getDatabaseCachingService().getPoolProperties().get(propertyId);
                                                        if (poolProperty == null) {
                                                            City city = DataFetcherManager.getCityMap().get(finalProperty.getCityId());
                                                            if (city == null) {
                                                                throw new IllegalStateException("Invalid city ID " + finalProperty.getCityId());
                                                            }
                                                            poolProperty = new UpaPoolProperty(propertyId, finalProperty.getFullAddress(), city.getName(), finalProperty.getMintPrice(), cost, finalProperty.getArea(), request.getMemberId());
                                                            UpaBot.getDatabaseCachingService().getPoolProperties().put(propertyId, poolProperty);
                                                        }
                                                        poolProperty.getVerified().set(true);
                                                        poolProperty.getCost().set(cost);
                                                    }
                                                    event.getHook().setEphemeral(true).editOriginal("PAC has been auto-awarded for this store transaction. Please verify manually in <#983628894919860234>.").queue();
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
                MessageBuilder statementBldr = statements.remove(memberId);
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
                UpaMember propertyFrom = UpaBot.getDatabaseCachingService().getMembers().get(event.getMember().getIdLong());
                if (propertyFrom == null) {
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
                    UpaPoolProperty newPoolProperty = new UpaPoolProperty(propertyIdLong, success.getFullAddress(), city.getName(), success.getMintPrice(), 0, success.getArea(), propertyFrom.getMemberId());
                    SqlConnectionManager.getInstance().execute(new SqlTask<UpaPoolProperty>() {
                        @Override
                        public UpaPoolProperty execute(Connection connection) throws Exception {
                            try (PreparedStatement insertPoolProperty = connection.prepareStatement("INSERT INTO pool_properties (property_id, address, city_name, mint_price, up2, donor_member_id) VALUES (?, ?, ?, ?, ?, ?)")) {
                                insertPoolProperty.setLong(1, success.getPropId());
                                insertPoolProperty.setString(2, success.getFullAddress());
                                insertPoolProperty.setString(3, city.getName());
                                insertPoolProperty.setInt(4, success.getMintPrice());
                                insertPoolProperty.setInt(5, success.getArea());
                                insertPoolProperty.setLong(6, propertyFrom.getMemberId());
                                if (insertPoolProperty.executeUpdate() != 1) {
                                    event.getHook().setEphemeral(true).editOriginal("Could not add pool property to the database. Please reach out to <@220622659665264643>.").queue();
                                } else {
                                    UpaBot.getDatabaseCachingService().
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
                            UpaBot.getDiscordService().sendBotRequestsMsg(propertyFrom, "trading " + result.getDescriptiveAddress() + " for PAC",
                                    msg -> {
                                        UpaBot.variables().storeRequests().access(storeRequests -> storeRequests.get().putIfAbsent(msg.getIdLong(), new UpaStoreRequest(propertyFrom.getMemberId(), msg.getIdLong(), propertyId, "a property transfer to the UPA pool (" + success.getFullAddress() + ").", RequestType.PROPERTY)) == null);
                                        event.getHook().setEphemeral(true).editOriginal("Your request to trade " + propertyLink + " for PAC was sent to the UPA team. You'll hear from us shortly!").queue();
                                    });
                        }
                    });
                });
                break;
            case "confirm_help_with_paid_sends_form":
                UpaMember helpFor = UpaBot.getDatabaseCachingService().getMembers().get(event.getMember().getIdLong());
                if (helpFor == null) {
                    event.reply("Please use the /account command before requesting help for purchases.").setEphemeral(true).queue();
                    return;
                }
                String helpNeeded = event.getValue("confirm_help_with_paid_sends").getAsString();
                event.deferReply(true).queue();
                UpaBot.getDiscordService().sendBotRequestsMsg(helpFor, "purchasing PAC using visits.\n\nTheir issue: " + helpNeeded,
                        msg -> event.getHook().setEphemeral(true).editOriginal("Your request for help regarding sends was sent to the UPA team. You'll hear from us shortly!").queue());
                break;
            case "confirm_purchase_with_upx_form":
                UpaMember requester = UpaBot.getDatabaseCachingService().getMembers().get(event.getMember().getIdLong());
                if (requester == null) {
                    event.reply("Please use the /account command before making purchases.").setEphemeral(true).queue();
                    return;
                }
                String amountStr = event.getValue("confirm_purchase_with_upx_amount").getAsString();
                Integer amount = Ints.tryParse(amountStr.replace(",", "").trim());
                if (amount == null) {
                    event.reply("Value '" + amountStr + "' is not a valid whole number.").setEphemeral(true).queue();
                    return;
                }
                if (amount < MINIMUM_UPX_FOR_PAC) {
                    event.reply("You must buy a minimum of " + MINIMUM_UPX_FOR_PAC + " PAC.").setEphemeral(true).queue();
                    return;
                }
                String action = "**" + DiscordService.COMMA_FORMAT.format(amount) + " PAC** using UPX";
                event.deferReply(true).queue();
                UpaBot.getDiscordService().sendBotRequestsMsg(requester, "purchasing " + action,
                        msg -> {
                            long commentId = msg.getIdLong();
                            UpaBot.variables().storeRequests().access(map -> map.get().putIfAbsent(commentId, new UpaStoreRequest(requester.getMemberId(), commentId, amount, "through UPX transfer", RequestType.PAC)) == null);
                            event.getHook().setEphemeral(true).editOriginal("Your request to purchase " + action + " has been sent to the UPA staff team. You'll hear from us shortly!").queue();
                        });
                break;
            case "confirm_redeem_for_ssh_form":
                requester = UpaBot.getDatabaseCachingService().getMembers().get(event.getMember().getIdLong());
                if (requester == null) {
                    event.reply("Please use the /account command before making purchases.").setEphemeral(true).queue();
                    return;
                }
                int pacAmount = requester.getCredit().get();
                amountStr = event.getValue("confirm_redeem_for_ssh_amount").getAsString();
                amount = Ints.tryParse(amountStr.replace(",", "").trim());
                if (amount == null) {
                    event.reply("Value '" + amountStr + "' is not a valid whole number.").setEphemeral(true).queue();
                    return;
                }
                if (amount < MINIMUM_PAC_FOR_SSH) {
                    event.reply("You must redeem a minimum of **" + MINIMUM_PAC_FOR_SSH + " SSH**.").setEphemeral(true).queue();
                    return;
                }
                int cost = amount * PAC_PER_SSH_RATE;
                if (pacAmount < cost) {
                    event.reply("You need at least **" + cost + " PAC** to redeem this (You have **" + pacAmount + " PAC**).").setEphemeral(true).queue();
                    return;
                }
                event.deferReply(true).queue();
                UpaBot.getDiscordService().sendCredit(new CreditTransaction(requester, -cost, CreditTransactionType.REDEEM, "**" + DiscordService.COMMA_FORMAT.format(amount) + " SSH**") {
                    @Override
                    public void onSuccess() {
                        SqlConnectionManager.getInstance().execute(new SqlTask<Boolean>() {
                            @Override
                            public Boolean execute(Connection connection) throws Exception {
                                try (PreparedStatement ps = connection.prepareStatement("UPDATE members SET ssh = ssh + ? WHERE member_id = ?;")) {
                                    ps.setInt(1, amount);
                                    ps.setLong(2, requester.getMemberId());
                                    if (ps.executeUpdate() != 1) {
                                        return false;
                                    }
                                }
                                return true;
                            }
                        }, success -> {
                            String formattedAmt = DiscordService.COMMA_FORMAT.format(amount);
                            if (success) {
                                requester.getSsh().addAndGet(amount);
                                event.getHook().setEphemeral(true).editOriginal("Your **" + formattedAmt + " SSH** Has been credited to your account. You can verify this using /account.").queue();
                            } else {
                                UpaBot.getDiscordService().sendBotRequestsMsg(requester, "purchasing **" + formattedAmt + " SSH** using PAC (Database transaction failed).",
                                        msg -> event.getHook().setEphemeral(true).editOriginal("Database transaction failed, you will be awarded your SSH manually within a few hours. You can check /account to see when it's processed.").queue());
                            }
                        });
                    }
                });
                break;
            case "confirm_redeem_for_upx_form":
                requester = UpaBot.getDatabaseCachingService().getMembers().get(event.getMember().getIdLong());
                if (requester == null) {
                    event.reply("Please use the /account command before making purchases.").setEphemeral(true).queue();
                    return;
                }
                pacAmount = requester.getCredit().get();
                amountStr = event.getValue("confirm_redeem_for_upx_amount").getAsString();
                amount = Ints.tryParse(amountStr.replace(",", "").trim());
                if (amount == null) {
                    event.reply("Value '" + amountStr + "' is not a valid whole number.").setEphemeral(true).queue();
                    return;
                }
                if (amount < MINIMUM_PAC_FOR_UPX) {
                    event.reply("You must redeem a minimum of **" + MINIMUM_PAC_FOR_UPX + " UPX**.").setEphemeral(true).queue();
                    return;
                }
                cost = amount * PAC_PER_UPX_RATE;
                if (pacAmount < cost) {
                    event.reply("You need at least **" + cost + " PAC** to redeem this (You have **" + pacAmount + " PAC**).").setEphemeral(true).queue();
                    return;
                }
                String formattedAmt = "**" + DiscordService.COMMA_FORMAT.format(amount) + " UPX**";
                event.deferReply(true).queue();
                UpaBot.getDiscordService().sendBotRequestsMsg(requester, "redeeming " + formattedAmt,
                        msg -> {
                            UpaBot.variables().storeRequests().access(storeRequests -> storeRequests.get().putIfAbsent(msg.getIdLong(), new UpaStoreRequest(requester.getMemberId(), msg.getIdLong(), -cost, "**" + amount + " UPX**", RequestType.PAC)) == null);
                            event.getHook().setEphemeral(true).editOriginal("Your request to redeem " + formattedAmt + " has been sent to the UPA staff team. You'll hear from us shortly!").queue();
                        });
                break;
            case "confirm_redeem_for_property_form":
                UpaMember propertyFor = UpaBot.getDatabaseCachingService().getMembers().get(event.getMember().getIdLong());
                if (propertyFor == null) {
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
                UpaPoolProperty poolProperty = UpaBot.getDatabaseCachingService().getPoolProperties().get(propertyId);
                if (poolProperty == null) {
                    event.reply("Invalid pool property link selected.").setEphemeral(true).queue();
                    return;
                }
                event.deferReply(true).queue();
                UpaBot.getDiscordService().sendBotRequestsMsg(propertyFor, "redeeming " + poolProperty.getAddress() + " from the UPA pool",
                        msg -> {
                            UpaBot.variables().storeRequests().access(storeRequests -> storeRequests.get().putIfAbsent(msg.getIdLong(), new UpaStoreRequest(propertyFor.getMemberId(), msg.getIdLong(), -poolProperty.getPropertyId(), poolProperty.getAddress() + " from the UPA pool", RequestType.PROPERTY)) == null);
                            event.getHook().setEphemeral(true).editOriginal("Your request to trade " + propertyLink + " for PAC was sent to the UPA team. You'll hear from us shortly!").queue();
                        });
                break;
        }
    }

    private void redeemForProperty(ButtonInteractionEvent event, boolean edit) {
        var poolProperties = UpaBot.getDatabaseCachingService().getPoolProperties().values();
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
            MessageBuilder mb = new MessageBuilder().setContent(REDEEM_FOR_PROPERTY_INFO).setActionRows(ActionRow.of(menuBldr.build()));
            if (edit) {
                event.getInteraction().editMessage(mb.build()).queue();
            } else {
                event.reply(mb.build()).setEphemeral(true).queue();
            }
        }
        if (!edit) {
            event.getInteraction().editButton(event.getButton().asDisabled()).queue();
        }
    }

    private void handlePurchaseButton(ButtonInteractionEvent event) {
        DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
        UpaMember requester = databaseCaching.getMembers().get(event.getMember().getIdLong());
        if (requester == null) {
            event.reply("Please use the /account command before making purchases.").setEphemeral(true).queue();
            return;
        }
        event.reply("Please select which payment method you are interested in **purchasing** PAC with below.").setEphemeral(true).addActionRow(
                Button.of(ButtonStyle.PRIMARY, "purchase_with_upx", "Use UPX", Emoji.fromEmote("upx", 987836478455431188L, true)),
                Button.of(ButtonStyle.PRIMARY, "purchase_with_visits", "Use visits", Emoji.fromUnicode("U+1F6EB")),
                Button.of(ButtonStyle.PRIMARY, "purchase_with_property", "Use a property", Emoji.fromUnicode("U+1F3E0"))).queue();
    }

    private void handleRedeemButton(ButtonInteractionEvent event) {
        DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
        UpaMember requester = databaseCaching.getMembers().get(event.getMember().getIdLong());
        if (requester == null) {
            event.reply("Please use the /account command before trying to redeem rewards.").setEphemeral(true).queue();
            return;
        }
        event.reply("Please select which reward you are interested in **redeeming** below.").setEphemeral(true).addActionRow(
                Button.of(ButtonStyle.PRIMARY, "redeem_for_upx", "UPX", Emoji.fromEmote("upx", 987836478455431188L, true)),
                Button.of(ButtonStyle.PRIMARY, "redeem_for_ssh", "Spark share hours (SSH)", Emoji.fromUnicode("U+26A1")),
                Button.of(ButtonStyle.PRIMARY, "redeem_for_property", "Properties", Emoji.fromUnicode("U+1F3E0")),
                Button.of(ButtonStyle.PRIMARY, "redeem_for_th_map", "Treasure hunting map overlay", Emoji.fromUnicode("U+1F5FA"))
        ).queue();
    }

    public static void handleTransferCommand(IReplyCallback event, Member receiverMember, long inputAmount, String reason, CreditTransactionType type) {
        DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
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
        if (receiver == null) {
            event.reply("Please use /account before trying to transfer PAC to another member.").setEphemeral(true).queue();
            return;
        }
        UpaMember sender = databaseCaching.getMembers().get(senderMember.getIdLong());
        if (sender == null) {
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
                    UpaBot.getDiscordService().sendCreditMessage(transfer);
                    switch (type) {
                        case TRANSFER:
                            event.getHook().setEphemeral(true).editOriginal("Transfer of " + amount + " PAC to <@" + receiver.getMemberId() + "> complete. You should see a confirmation in <#983628894919860234> shortly. ").queue();
                            break;
                        case TIP:
                            event.getHook().editOriginal("Successfully tipped <@" + receiver.getMemberId() + "> **" + amount + " PAC**. I bet they appreciated that.").queue();
                            break;
                    }
                }, failure -> event.getHook().setEphemeral(true).editOriginal("Transfer of " + amount + " PAC to <@" + receiver.getMemberId() + "> failed. Please try again later.").queue());
    }

    private void handleDailyCommand(SlashCommandInteractionEvent event) {
        long memberId = event.getMember().getIdLong();
        event.deferReply().setEphemeral(true).queue();
        UpaMember upaMember = UpaBot.getDatabaseCachingService().getMembers().get(memberId);
        if (upaMember == null) {
            event.getHook().setEphemeral(true).editOriginal("You must use /account before claiming a dividend.").queue();
            return;
        }
        Set<UpaProperty> nodeProperties = UpaBot.getDatabaseCachingService().getMemberProperties().get(upaMember.getKey());
        if (nodeProperties.isEmpty()) {
            event.getHook().setEphemeral(true).editOriginal("You must own at least one property in Hollis, Queens to claim a dividend.").queue();
            return;
        }
        Instant lastClaimed = upaMember.getClaimedDailyAt().get();
        Instant lastReset = UpaBot.variables().lastDailyReset().getValue();
        if (lastClaimed.isBefore(lastReset)) {
            Instant now = Instant.now();
            SqlConnectionManager.getInstance().execute(new SqlTask<Void>() {
                                                           @Override
                                                           public Void execute(Connection connection) throws Exception {
                                                               try (PreparedStatement updateDaily = connection.prepareStatement("UPDATE members SET claimed_daily_at = ? WHERE member_id = ?;")) {
                                                                   updateDaily.setString(1, now.toString());
                                                                   updateDaily.setLong(2, memberId);
                                                                   int row = updateDaily.executeUpdate();
                                                                   if (row != 1) {
                                                                       throw new IllegalStateException("Unexpected row result when updating daily (was " + row + ", expected 1).");
                                                                   }
                                                               }
                                                               return null;
                                                           }
                                                       }, success -> {
                        upaMember.getClaimedDailyAt().set(now);
                        int baseDividend = 25;
                        var countObj = new Object() {
                            int count = 0;
                            int genCount = 0;
                        };
                        long hoursDiff = lastClaimed.until(now, ChronoUnit.HOURS);
                        if (hoursDiff >= 168) {
                            hoursDiff = 168;
                        }
                        double yieldPerHour = nodeProperties.stream().mapToDouble(next -> {
                            if (next.isGenesis()) {
                                countObj.genCount++;
                                return 5.0 / 24.0;
                            }
                            countObj.count++;
                            return 1.0 / 24.0;
                        }).sum();
                        long dividend = (long) (baseDividend + (yieldPerHour * hoursDiff));
                        long finalHoursDiff = hoursDiff;
                        UpaBot.getDiscordService().sendCredit(new CreditTransaction(upaMember, Ints.checkedCast(dividend), CreditTransactionType.DAILY, null) {
                            @Override
                            public void onSuccess() {
                                statements.put(memberId, new MessageBuilder().appendCodeBlock(new StringBuilder().
                                        append("Base dividend\t").append("25 PAC\n").
                                        append("Yield per hour\t").append(DiscordService.DECIMAL_FORMAT.format(yieldPerHour)).append(" PAC\n").
                                        append("Last claimed\t").append(finalHoursDiff).append(" hours\n\n").
                                        append("Total\t").append(dividend).append(" PAC").toString(), ""));
                                event.getHook().setEphemeral(true).editOriginal(new MessageBuilder().setActionRows(
                                                ActionRow.of(Button.of(ButtonStyle.SUCCESS, "statement", "Statement", Emoji.fromUnicode("U+1F9FE")))
                                        ).append("You have claimed your dividend of **").
                                        append(dividend).
                                        append(" PAC**. You should see the transaction in <#983628894919860234> shortly.").build()).queue();
                            }
                        });
                    },
                    failure -> {
                        event.getHook().setEphemeral(true).editOriginal("Could not verify the status of your daily. Please try again later.").queue();
                        logger.catching(failure);
                    });
        } else {
            String claimIn;
            Instant now = Instant.now();
            Instant resetAt = DailyResetMicroService.wait(UpaBot.variables().lastDailyReset().getValue());
            long hoursUntil = now.until(resetAt, ChronoUnit.HOURS);
            long minutesUntil = now.until(resetAt, ChronoUnit.MINUTES);
            if (hoursUntil > 0) {
                claimIn = hoursUntil + " hour(s)";
            } else if (minutesUntil > 0) {
                claimIn = minutesUntil + " minute(s)";
            } else {
                claimIn = "a few seconds";
            }
            event.getHook().setEphemeral(true).editOriginal("You can claim your daily in " + claimIn + ".").queue();
        }
    }


}
