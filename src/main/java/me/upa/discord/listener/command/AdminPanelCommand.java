package me.upa.discord.listener.command;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Range;
import com.google.common.primitives.Ints;
import me.upa.UpaBotConstants;
import me.upa.UpaBotContext;
import me.upa.discord.DiscordService;
import me.upa.discord.SparkTrainSnapshot;
import me.upa.discord.UpaInformationRepository.UpaInformationType;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaPoolProperty;
import me.upa.discord.event.UpaEvent;
import me.upa.discord.event.UpaEventHandler;
import me.upa.discord.listener.credit.CreditTransaction;
import me.upa.discord.listener.credit.CreditTransaction.CreditTransactionType;
import me.upa.game.Property;
import net.dv8tion.jda.api.EmbedBuilder;
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
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class AdminPanelCommand extends ListenerAdapter {

    private static final Logger logger = LogManager.getLogger();

    public AdminPanelCommand(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    public static final class GiveawayEventData implements Serializable {
        private static final long serialVersionUID = -3679859807863052466L;
        private final Multiset<Long> entries = ConcurrentHashMultiset.create();
        private final Multiset<Long> purchasedEntries = ConcurrentHashMultiset.create();
    }

    public static final class GiveawayEventSnapshot extends SparkTrainSnapshot<GiveawayEventData> {

        private static final class GiveawayEntryData {
            private final Range<Integer> range;
            private final long memberId;

            private GiveawayEntryData(Range<Integer> range, long memberId) {
                this.range = range;
                this.memberId = memberId;
            }
        }

        private static final long serialVersionUID = 6963944120299800700L;

        public GiveawayEventSnapshot() {
            super(Duration.ofHours(1), 24, new GiveawayEventData());
        }

        @Override
        public void onSnapshot(UpaBotContext ctx) {
            for (UpaMember upaMember : ctx.databaseCaching().getMembers().values()) {
                if (!upaMember.getActive().get())
                    continue;
                double totalStaked = upaMember.getTotalStaking();
                if (totalStaked >= 1) {
                    int pacAmount = (int) Math.floor(25 * totalStaked);
                    int entries = (int) Math.floor(totalStaked);
                    ctx.discord().sendCredit(new CreditTransaction(upaMember, pacAmount, CreditTransactionType.OTHER, "Staking " + DiscordService.DECIMAL_FORMAT.format(totalStaked) + " spark during the snapshot period."));
                    getData().entries.add(upaMember.getMemberId(), entries);
                }
            }
        }

        @Override
        public void onFinish(UpaBotContext ctx) {
            List<Long> winners = new ArrayList<>();
            Map<Long, Integer> originalEntries = new HashMap<>();
            for (var entry : getData().entries.entrySet()) {
                if (!UpaBotConstants.STAFF.contains(entry.getElement())) {
                    originalEntries.put(entry.getElement(), entry.getCount());
                }
            }
            while (winners.size() < 3) {
                int total = 0;
                List<GiveawayEntryData> entries = new ArrayList<>();
                for (var entry : originalEntries.entrySet()) {
                    int before = total;
                    total += entry.getValue();
                    entries.add(new GiveawayEntryData(Range.closedOpen(before, total), entry.getKey()));
                }
                int rand = ThreadLocalRandom.current().nextInt(total);
                for (GiveawayEntryData data : entries) {
                    if (data.range.contains(rand)) {
                        originalEntries.remove(data.memberId);
                        winners.add(data.memberId);
                        entries.clear();
                        break;
                    }
                }
            }
            Emoji emoji = Emoji.fromUnicode("U+1F389");
            MessageCreateBuilder mb = new MessageCreateBuilder();
            mb.addContent(emoji.getName() + " __**The winners of the giveaway are...**__ " + emoji.getName() + "\n");
            int place = 1;
            for (long id : winners) {
                mb.addContent("**#" + place++ + "** <@").addContent(String.valueOf(id)).addContent(">\n");
            }
            mb.addContent("Congratulations! Please reach out to <@" + UpaBotConstants.UNRULY_CJ_MEMBER_ID + "> for your reward!\n");
            mb.addContent("\n__**And here is the full list of contestants and their entries! Better luck next time.**__\n");
            for (var entry : getData().entries.entrySet()) {
                mb.addContent("<@").addContent(String.valueOf(entry.getElement())).
                        addContent("> (" + entry.getCount() + " entries)\n");
            }
            ctx.discord().guild().getTextChannelById(1004266388355022859L).sendMessage(mb.build()).queue();
        }
    }

    private static final class SortedUpaMember implements Comparable<SortedUpaMember> {

        private final String discordName;
        private final int credit;

        private SortedUpaMember(String discordName, int credit) {
            this.discordName = discordName;
            this.credit = credit;
        }

        @Override
        public int compareTo(@NotNull SortedUpaMember o) {
            return Integer.compare(o.credit, credit);
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getSubcommandName() != null && event.getSubcommandName().equals("panel")) {
            SelectMenu selectMenu = SelectMenu.create("admin_select").
                    addOption("Claim /pac daily for all", "mass_pac_daily", Emoji.fromUnicode("U+1F4B0")).
                    addOption("Update text", "update_text", Emoji.fromUnicode("U+1F4A1")).
                    addOption("View credit balances", "credit_balance", Emoji.fromUnicode("U+1F4B3")).
                    addOption("View UPA pool properties", "admin_pool_properties", Emoji.fromUnicode("U+1F3D8")).
                    addOption("Update commands", "update_commands", Emoji.fromUnicode("U+2B07")).
                    addOption("Claim sends", "claim_sends", Emoji.fromUnicode("U+270B")).
                    addOption("Test feature", "test_feature", Emoji.fromUnicode("U+1F9EA")).
                    addOption("Start spark train snapshots", "start_snapshots", Emoji.fromUnicode("U+1F4F8")).
                    addOption("Set next event", "set_next_event", Emoji.fromUnicode("U+1F389")).build();
            event.reply("Please select an action.").setEphemeral(true).setComponents(
                    ActionRow.of(selectMenu)
            ).queue();
        }
    }

    /**
     * The context.
     */
    private final UpaBotContext ctx;

    private volatile Property cachedProp;


    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null)
            return;
        switch (event.getButton().getId()) {
            case "finished_claim_sends":
                ctx.variables().yields().accessValue(data -> {
                    data.getProperties().clear();
                    return true;
                });
            case "cancel_claim_sends":
                ctx.paidVisitsMs().setPaused(false);
                event.reply("Done.").setEphemeral(true).queue();
                break;
            case "buy_more_entries_1":
                buyMoreEntries(event, 1);
                break;
            case "buy_more_entries_5":
                buyMoreEntries(event, 5);
                break;
            case "buy_more_entries_10":
                buyMoreEntries(event, 10);
                break;
        }
    }


    private void buyMoreEntries(IReplyCallback event, int entries) {
        UpaMember upaMember = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
        if (upaMember == null || !upaMember.getActive().get()) {
            event.reply("Please become a UPA member by using /account first.").setEphemeral(true).queue();
            return;
        }
        int cost = 500 * entries;
        int pac = upaMember.getCredit().get();
        if (pac < cost) {
            event.reply("You need at least " + cost + " PAC in order to buy " + entries + " entry(s) into this giveaway.").setEphemeral(true).queue();
            return;
        }
        ctx.variables().sparkTrainSnapshot().accessValue(snapshotWc -> {
            int entryCount = entries;
            if (snapshotWc instanceof GiveawayEventSnapshot) {
                GiveawayEventSnapshot giveawaySnapshot = (GiveawayEventSnapshot) snapshotWc;
                int newCount = entryCount + giveawaySnapshot.getData().purchasedEntries.count(upaMember.getMemberId());
                if (newCount >= 10) {
                    int fixedCount = newCount - 10;
                    if (fixedCount > 0) {
                        entryCount -= fixedCount;
                    } else {
                        event.reply("You have already purchased the maximum amount of entries (10).").setEphemeral(true).queue();
                        return false;
                    }
                }
                event.deferReply(true).queue();
                int finalEntryCount = entryCount;
                ctx.discord().sendCredit(new CreditTransaction(upaMember, -cost, CreditTransactionType.REDEEM, "redeeming an additional giveaway entry") {
                    @Override
                    public void onSuccess() {
                        giveawaySnapshot.getData().purchasedEntries.add(upaMember.getMemberId(), finalEntryCount);
                        giveawaySnapshot.getData().entries.add(upaMember.getMemberId(), finalEntryCount);
                        ctx.variables().sparkTrainSnapshot().save();
                        event.getHook().setEphemeral(true).editOriginal("You have successfully purchased " + finalEntryCount + " entry(s).").queue();
                    }
                });
            } else {
                event.reply("Additional entries cannot be purchased for this giveaway.").setEphemeral(true).queue();
            }
            return false;
        });
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getModalId().equals("set_lottery_jackpot_form")) {
            Integer amount = Ints.tryParse(event.getValue("set_lottery_jackpot_form_amount").getAsString());
            if (amount == null) {
                event.reply("Incorrect amount entered.").setEphemeral(true).queue();
                return;
            }
            ctx.variables().lottery().accessValue(currentLottery -> {
                currentLottery.getPac().set(amount);
                event.reply("Success!").setEphemeral(true).queue();
                return true;
            });
        } else if (event.getModalId().equals("update_text_input")) {
            String newText = event.getValue("update_text_input_text").getAsString();
            UpaInformationType type = currentType.get(event.getMember().getIdLong());
            if (type == null) {
                event.reply("Bot was restarted, please redo the process.").setFiles(FileUpload.fromData(newText.getBytes(), "input.txt")).setEphemeral(true).queue();
                return;
            }
            ctx.variables().information().accessValue(repo -> {
                repo.update(type, newText);
                return true;
            });
        }
    }

    private final Map<Long, UpaInformationType> currentType = new ConcurrentHashMap<>();

    @Override
    public void onSelectMenuInteraction(@NotNull SelectMenuInteractionEvent event) {
        if (event.getSelectedOptions().size() != 1) {
            return;
        }
        String id = event.getSelectMenu().getId();
        String value = event.getSelectedOptions().get(0).getValue();
        if (Objects.equals(id, "select_next_event")) {
            event.deferReply(true).queue();
            ctx.discord().execute(() -> {
                event.getHook().setEphemeral(true);
                Supplier<UpaEventHandler> eventHandler = UpaEvent.EVENT_HANDLER_MAPPINGS.get(value);
                if (eventHandler != null) {
                    ctx.variables().event().accessValue(upaEvent -> {
                        upaEvent.setNextEvent(eventHandler.get());
                        return true;
                    });
                    event.getHook().editOriginal("Event [" + value + "] set.").queue();
                } else {
                    event.getHook().editOriginal("Event with name " + value + " not found.").queue();
                }
            });
        } else if (Objects.equals(id, "update_text_option")) {
            UpaInformationType type = UpaInformationType.valueOf(value);
            String currentInfo = ctx.variables().information().get().get(type);
            Modal.Builder modalBuilder = Modal.create("update_text_input", "Update text");
            modalBuilder.addActionRow(TextInput.create("update_text_input_text", "New text", TextInputStyle.PARAGRAPH).setValue(currentInfo != null ? currentInfo : "null").build());
            event.replyModal(modalBuilder.build()).queue();
            currentType.put(event.getMember().getIdLong(), type);
        } else if (Objects.equals(id, "admin_select")) {
            switch (value) {
                case "update_text":
                    SelectMenu.Builder selectMenu = SelectMenu.create("update_text_option");
                    for (UpaInformationType type : UpaInformationType.ALL) {
                        selectMenu.addOption(type.name(), type.name());
                    }
                    event.reply("Select the text you want to update.").addActionRow(selectMenu.build()).setEphemeral(true).queue();
                    break;
                case "set_next_event":
                    selectMenu = SelectMenu.create("select_next_event");
                    for (String name : UpaEvent.EVENT_HANDLER_MAPPINGS.keySet()) {
                        selectMenu.addOption(name, name);
                    }
                    event.reply("Please select an action.").setEphemeral(true).setComponents(
                            ActionRow.of(selectMenu.build())).setEphemeral(true).queue();
                    break;
                case "start_snapshots":
                    if(event.getMember().getIdLong() != UpaBotConstants.UNRULY_CJ_MEMBER_ID) {
                        event.reply("Restricted to unruly_cj only.").setEphemeral(true).queue();
                        return;
                    }
                    ctx.variables().sparkTrainSnapshot().set(new GiveawayEventSnapshot());
                    event.reply("Done.").setEphemeral(true).queue();
                    event.getHook().setEphemeral(false).sendMessage("Use the button below to buy additional entries. You can only buy a maximum of 10 entries.").
                            setComponents(ActionRow.of(
                                    Button.of(ButtonStyle.PRIMARY, "buy_more_entries_1", "Buy 1 entry", Emoji.fromUnicode("U+1F39F")),
                                    Button.of(ButtonStyle.PRIMARY, "buy_more_entries_5", "Buy 5 entries", Emoji.fromUnicode("U+1F39F")),
                                    Button.of(ButtonStyle.PRIMARY, "buy_more_entries_10", "Buy 10 entries", Emoji.fromUnicode("U+1F39F"))

                            )).queue();
                    break;
                case "test_feature":
                    if(event.getMember().getIdLong() != UpaBotConstants.UNRULY_CJ_MEMBER_ID) {
                        event.reply("Restricted to unruly_cj only.").setEphemeral(true).queue();
                        return;
                    }
                /*                UpaEvent.forEvent(ctx, BonusSshEventHandler.class, handler -> {
                    for (UpaMember upaMember : ctx.databaseCaching().getMembers().values()) {
                        double given = upaMember.getHollisSparkTrainShGiven().get();
                        double bonus = (given/24) * 0.70;
                        double finalAmt = given - bonus;
                        if (finalAmt >= 1) {
                            handler.getSshMap().put(upaMember.getMemberId(), finalAmt);
                            logger.error("{} ({}}", upaMember.getDiscordName(), finalAmt);
                        }
                    }
                    ctx.variables().event().save();
                });
                                try {
                    UpaEvent upaEvent = ctx.variables().event().get();
                    if (upaEvent != null && upaEvent.getHandler() instanceof BonusSshEventHandler) {
                        BonusSshEventHandler handler = (BonusSshEventHandler) upaEvent.getHandler();
                            handler.getTotalSshGained().put(UpaBotConstants.UNRULY_CJ_MEMBER_ID, 15.0);
                            handler.getTotalSshGained().put(200653175127146501L, 24.0);

                    }
                    event.reply("Done.").setEphemeral(true).queue();
                } catch (Exception e) {
                    logger.catching(e);
                    event.reply("Error.").setEphemeral(true).queue();
                }

               event.deferReply(true).queue();
                if(cachedProp == null) {
                    PropertyDataFetcher.fetchProperty(82404268609937L, success -> {
                        try {
                            cachedProp = success;
                            List<Neighborhood> neighborhood = PropertySynchronizationService.getNeighborhoods(success);
                            event.getHook().setEphemeral(true).editOriginal("Neighborhood test: " + neighborhood.get(0).getName()).queue();
                        }catch (Exception e) {
                            logger.warn(e);
                        }
                    });
                } else {
                    List<Neighborhood> neighborhood = PropertySynchronizationService.getNeighborhoods(cachedProp);
                    event.getHook().setEphemeral(true).editOriginal("Neighborhood test: "+neighborhood.size()).queue();
                }
             event.reply("Blockchain synchronization @ "+ctx.variables().lastBlockchainFetch().getValue()+"\nTotal records: "+
                        DiscordService.COMMA_FORMAT.format(ctx.databaseCaching().getPropertyLookup().size())).setEphemeral(true).queue();*/
                /*    event.reply("How much PAC would you like to wager? (PAC will not be taken while in TEST mode)\n\n"+
                        BLUE_SQUARE_EMOJI.getAsMention()+""+BLUE_SQUARE_EMOJI.getAsMention()+""+BLUE_SQUARE_EMOJI.getAsMention()+" = Win double your PAC!\nAnything else=").addActionRow(
                        Button.of(ButtonStyle.PRIMARY, "wager_100", "Wager 100 PAC", Emoji.fromUnicode("U+1F4B5")),
                        Button.of(ButtonStyle.PRIMARY, "wager_250", "Wager 250 PAC", Emoji.fromUnicode("U+1F4B0")),
                        Button.of(ButtonStyle.PRIMARY, "wager_500", "Wager 500 PAC", Emoji.fromUnicode("U+1F911"))
                ).setEphemeral(true).queue();*/
                    break;
                case "update_commands":
                    if(event.getMember().getIdLong() != UpaBotConstants.UNRULY_CJ_MEMBER_ID) {
                        event.reply("Restricted to unruly_cj only.").setEphemeral(true).queue();
                        return;
                    }
                    ctx.discord().updateCommands();
                    event.reply("Done. Please give up to 10 minutes for commands to synchronize.").setEphemeral(true).queue();
                    break;
                case "credit_balance":
                    var memberList = ctx.databaseCaching().getMembers().values();
                    List<SortedUpaMember> sortedUpaMembers = new ArrayList<>(memberList.size());
                    for (UpaMember upaMember : memberList) {
                        if (!upaMember.getActive().get()) {
                            continue;
                        }
                        int credit = upaMember.getCredit().get();
                        if (credit > 0) {
                            sortedUpaMembers.add(new SortedUpaMember(upaMember.getDiscordName().get(), credit));
                        }
                    }
                    Collections.sort(sortedUpaMembers);
                    StringBuilder sb = new StringBuilder();
                    for (SortedUpaMember member : sortedUpaMembers) {
                        sb.append('@').append(member.discordName).append(" (").append(member.credit).append(" credit)").append("\n\n");
                    }
                    event.replyFiles(FileUpload.fromData(sb.toString().getBytes(), "credit_balances.txt")).setEphemeral(true).queue();
                    break;
                case "admin_pool_properties":
                    sb = new StringBuilder();
                    for (UpaPoolProperty poolProperty : ctx.databaseCaching().getPoolProperties().values()) {
                        if (!poolProperty.getVerified().get()) {
                            continue;
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
                        sb.append(poolProperty.getAddress()).append(" | ").append(poolProperty.getMintPrice()).
                                append(" UPX (").append(poolProperty.getCost().get()).append(" PAC) ").append(" | ").
                                append("https://play.upland.me/?prop_id=").append(poolProperty.getPropertyId()).
                                append("\n\n");
                    }
                    event.replyFiles(FileUpload.fromData(sb.toString().getBytes(), "upa_pool_properties.txt")).setEphemeral(true).queue();
                    break;
                case "claim_sends":
                    if(event.getMember().getIdLong() != UpaBotConstants.UNRULY_CJ_MEMBER_ID) {
                        event.reply("Restricted to unruly_cj only.").setEphemeral(true).queue();
                        return;
                    }
                    ctx.paidVisitsMs().setPaused(true);
                    event.reply("Paid visits service paused. Please claim UPX now and press the button when you're done.").
                            setComponents(ActionRow.of(
                                    Button.of(ButtonStyle.SECONDARY, "finished_claim_sends", "Finished", Emoji.fromUnicode("U+2705")),
                                    Button.of(ButtonStyle.SECONDARY, "cancel_claim_sends", "Cancel", Emoji.fromUnicode("U+274C"))
                            )).setEphemeral(true).queue();
                    break;
            }
        }
    }
}
