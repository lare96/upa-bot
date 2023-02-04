package me.upa.discord;

import me.upa.UpaBotContext;
import me.upa.game.CachedProperty;
import me.upa.game.Node;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class NodeListingCommands extends ListenerAdapter {

    private final UpaBotContext ctx;

    private final Map<Long, NodeListing> listingMap = new ConcurrentHashMap<>();

    public NodeListingCommands(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        if (event.getSubcommandName() == null)
            return;
        if (event.getSubcommandName().equals("sell") && event.getFocusedOption().getName().equals("address")) {
            UpaMember upaMember = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
            if (upaMember == null || !upaMember.getActive().get()) {
                return;
            }
            List<Choice> names = ctx.databaseCaching().getMemberProperties().get(upaMember.getKey()).stream().
                    filter(prop -> !ctx.variables().fastListings().get().contains(prop.getPropertyId())).
                    filter(prop -> prop.getAddress().startsWith(event.getFocusedOption().getValue())).
                    map(prop -> new Choice(prop.getAddress(), prop.getAddress())).
                    limit(25).collect(Collectors.toList());
            event.replyChoices(names).queue();
        } else if (event.getSubcommandName().equals("delete") && event.getFocusedOption().getName().equals("address")) {
            UpaMember upaMember = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
            if (upaMember == null || !upaMember.getActive().get()) {
                return;
            }
            List<Choice> names = ctx.variables().listings().get().getAll().stream().
                    filter(listing -> listing.getMemberId() == upaMember.getMemberId()).
                    filter(listing -> listing.getAddress().startsWith(event.getFocusedOption().getValue())).
                    map(listing -> new Choice(listing.getAddress(), listing.getAddress())).
                    limit(25).collect(Collectors.toList());
            event.replyChoices(names).queue();
        }
    }

    @Override
    public void onSelectMenuInteraction(@NotNull SelectMenuInteractionEvent event) {
        switch (event.getSelectMenu().getId()) {
            case "node_selection_buy":
                UpaMember upaMember = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
                if (upaMember == null) {
                    event.reply("You must be a registered UPA member in order to view node property listings.").setEphemeral(true).queue();
                    return;
                }
                if(!upaMember.getActive().get()) {
                    event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
                    return;
                }
                String nodeName = event.getSelectedOptions().get(0).getValue();
                Node node = Node.valueOf(nodeName.toUpperCase());
                event.deferEdit().queue();
                upaMember.getListingBrowseSlot().set(0);
                upaMember.setListingBrowseNode(node);
                showListings(node, upaMember, event);
                break;
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("properties")) {
            switch (event.getSubcommandName()) {
                case "buy":
                    UpaMember upaMember = ctx.databaseCaching().getMembers().get(event.getMember().getIdLong());
                    if (upaMember == null) {
                        event.reply("You must be a registered UPA member in order to view node property listings.").setEphemeral(true).queue();
                        return;
                    }
                    if(!upaMember.getActive().get()) {
                        event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
                        return;
                    }
                    showNodeOptions(event);
                    break;
                case "sell":
                    try {
                        String address = event.getOption("address").getAsString().trim();
                        String currency = event.getOption("currency").getAsString();
                        int amount = event.getOption("amount").getAsInt();
                        String description = event.getOption("description", OptionMapping::getAsString);
                        String image = event.getOption("image_link", OptionMapping::getAsString);

                        UpaProperty property = ctx.databaseCaching().getProperties().values().stream().filter(next ->
                                next.getAddress().equalsIgnoreCase(address)).filter(next -> next.getActive().get()).findFirst().orElse(null);
                        if (property == null) {
                            event.reply("Property could not be found. Please try again or submit a ticket in <#986638348418449479>.\n\nYour input: " + event.getCommandString()).setEphemeral(true).queue();
                            return;
                        }
                        CachedProperty cachedProperty = ctx.databaseCaching().getPropertyLookup().get(property.getPropertyId());
                        if (cachedProperty == null) {
                            // TODO Load cached property into memory manually. Need whole system for this
                            event.reply("Property could not be found. Please submit a ticket in <#986638348418449479>.\n\nYour input: " + event.getCommandString()).setEphemeral(true).queue();
                            return;
                        }
                        NodeListing listing = new NodeListing(property.getPropertyId(), address, currency, cachedProperty.getMintPrice(),
                                cachedProperty.getArea(), property.getNode(), amount, event.getMember().getIdLong(), description, image, Instant.now());
                        listingMap.put(event.getMember().getIdLong(), listing);
                        event.reply(new MessageCreateBuilder().
                                addContent("__**Listing preview**__\n").
                                addContent("Please confirm your listing ASAP, as pending listings are only held by the bot temporarily.\n").
                                addContent("Your input: ").addContent(event.getCommandString()).
                                setEmbeds(computeListingEmbed(listing)).
                                setComponents(ActionRow.of(
                                        Button.of(ButtonStyle.PRIMARY, "accept_listing", "List my property!", Emoji.fromUnicode("U+2705")))).build()).setEphemeral(true).queue();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case "delete":
                    String address = event.getOption("address").getAsString().trim();
                    ctx.variables().listings().accessValue(listings -> {
                        List<NodeListing> all = listings.getAll();
                        for (int index = 0; index < all.size(); index++) {
                            NodeListing next = all.get(index);
                            if (next.getAddress().equals(address)) {
                                all.remove(index);
                                ctx.variables().fastListings().accessValue(fastListings -> fastListings.remove(next.getPropertyId()));
                                event.reply("Listing successfully removed!").setEphemeral(true).queue();
                                return true;
                            }
                        }
                        event.reply("Listing could not be found.").setEphemeral(true).queue();
                        return false;
                    });
                    break;
            }
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null)
            return;
        long memberId = event.getMember().getIdLong();
        UpaMember upaMember = ctx.databaseCaching().getMembers().get(memberId);
        if (upaMember == null)
            return;
        if(!upaMember.getActive().get()) {
            event.reply("Please use '/account/' in <#967096154607194232> to reactivate your account.").setEphemeral(true).queue();
            return;
        }
        switch (event.getButton().getId()) {
            case "view_listings":
                showNodeOptions(event);
                break;
            case "next_listing":
                Node node = upaMember.getListingBrowseNode();
                if (node != null) {
                    event.deferEdit().queue();
                    upaMember.getListingBrowseSlot().incrementAndGet();
                    showListings(node, upaMember, event);
                }
                break;
            case "previous_listing":
                node = upaMember.getListingBrowseNode();
                if (node != null) {
                    event.deferEdit().queue();
                    upaMember.getListingBrowseSlot().decrementAndGet();
                    showListings(node, upaMember, event);
                }
                break;
            case "accept_listing":
                NodeListing listing = listingMap.remove(event.getMember().getIdLong());
                if (listing == null) {
                    event.getHook().editOriginal(new MessageEditBuilder().
                            setContent("Unfortunately, your listing has been forgotten by the bot. Please use **/properties sell** again.").build()).queue();
                } else {
                    ctx.variables().listings().accessValue(listings -> {
                        listings.add(listing);
                        ctx.variables().fastListings().accessValue(fastListings -> fastListings.add(listing.getPropertyId()));
                        return true;
                    });
                    ctx.discord().guild().getTextChannelById(963112034726195210L).sendMessage(event.getMember().getAsMention() +" has listed **"+listing.getAddress()+"** in **"+listing.getNode().name()+"** up for auction. Use **/properties buy** to view the listing!").queue();
                    event.getHook().editOriginal(new MessageEditBuilder().
                            setContent("Thanks for choosing to list your property with us! Your listing is now available for all members to see through **/properties buy**.\nIt will be automatically removed if the property is sold.").build()).queue();
                }
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            case "node_locations":
                StringBuilder sb = new StringBuilder();
                for(Node next : Node.ALL) {
                    sb.append(next.getDisplayName()).append('\n');
                }
                event.reply(sb.toString()).setEphemeral(true).queue();
                break;
        }
    }

    private MessageEmbed computeListingEmbed(NodeListing listing) {
        EmbedBuilder embed = new EmbedBuilder().
                setColor(Color.GREEN).
                setTitle(listing.getAddress()).
                addField("Mint price", DiscordService.COMMA_FORMAT.format(listing.getMintPrice()) + " UPX", true).
                addField("Area", DiscordService.COMMA_FORMAT.format(listing.getArea()) + " UP2", true).
                addField("Node", listing.getNode().name(), true).
                addField("Price", DiscordService.COMMA_FORMAT.format(listing.getPrice()) + " " + listing.getCurrency().toUpperCase(), true).
                addField("Listed by", "<@" + listing.getMemberId() + ">", false).
                addField("Property link", "https://play.upland.me/?prop_id=" + listing.getPropertyId(), false).
                addField("Listed on", DiscordService.DATE_FORMAT.format(listing.getListedOn()), false).
                setImage(listing.getImageLink());
        if (listing.getDescription() != null) {
            embed.addField("Description", listing.getDescription(), false);
        }
        return embed.build();
    }

    private boolean showListings(Node node, UpaMember upaMember, IReplyCallback event) {
        List<NodeListing> listings = ctx.variables().listings().get().getAllForNode(node);
        if (listings.isEmpty()) {
            event.getHook().editOriginal(new MessageEditBuilder().
                    setContent("There are no listings for this node at the moment. New listing notifications will appear in <#963112034726195210>.").build()).queue();
            return false;
        }
        if (listings.size() == 1) {
            event.getHook().editOriginal(new MessageEditBuilder().setContent("Listing 1/1").
                    setEmbeds(computeListingEmbed(listings.get(0))).build()).queue();
            return false;
        }
        int slot = upaMember.getListingBrowseSlot().updateAndGet(old -> {
            if (old >= listings.size() || old < 0) {
                return 0;
            }
            return old;
        });
        ActionRow actionRow;
        if (slot == 0) {
            actionRow = ActionRow.of(
                    Button.of(ButtonStyle.SECONDARY, "next_listing", "Next", Emoji.fromUnicode("U+27A1")));
        } else if (slot == listings.size() - 1) {
            actionRow = ActionRow.of(
                    Button.of(ButtonStyle.SECONDARY, "previous_listing", "Previous", Emoji.fromUnicode("U+2B05")));
        } else {
            actionRow = ActionRow.of(Button.of(ButtonStyle.SECONDARY, "previous_listing", "Previous", Emoji.fromUnicode("U+2B05")),
                    Button.of(ButtonStyle.SECONDARY, "next_listing", "Next", Emoji.fromUnicode("U+27A1")));
        }
        MessageEditData msg = new MessageEditBuilder().setContent("Listing "+(slot + 1)+"/"+listings.size()).
                setComponents(actionRow).setEmbeds(computeListingEmbed(listings.get(slot))).build();
        event.getHook().editOriginal(msg).queue();
        return true;
    }

    private void showNodeOptions(IReplyCallback event) {
        event.reply("Please select which node you'd like to view listings from.").
                addActionRow(SelectMenu.create("node_selection_buy").
                        addOption("Hollis, Queens", "hollis").
                        addOption("Sunrise, Las Vegas", "sunrise").
                        build()).setEphemeral(true).queue();
    }
}
