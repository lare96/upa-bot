package me.upa.discord.listener.command;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import me.upa.UpaBotContext;
import me.upa.discord.UpaBuildRequest;
import me.upa.discord.listener.buttons.SupportButtonListener;
import me.upa.discord.listener.credit.CreditTransaction;
import me.upa.discord.listener.credit.CreditTransaction.CreditTransactionType;
import me.upa.discord.UpaMember;
import me.upa.fetcher.PropertyDataFetcher;
import me.upa.game.Node;
import me.upa.game.Property;
import me.upa.game.Structure;
import me.upa.sql.DeleteBuildRequestTask;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SshTransferTask;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Handles the command /admin for administrative commands.
 *
 * @author lare96
 */
public final class AdminCommands extends ListenerAdapter {

    private static final Logger logger = LogManager.getLogger();
    /**
     * The context.
     */
    private final UpaBotContext ctx;

    public AdminCommands(UpaBotContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        var options = event.getOptions();
        if (event.getName().equals("admin")) {
            switch (event.getSubcommandName()) {
                case "add_build_request":
                    Member member = options.get(0).getAsMember();
                    long targetMemberId = member.getIdLong();
                    Long propertyId = Longs.tryParse(options.get(1).getAsString().trim());
                    String structure = options.get(2).getAsString();
                    GuildChannelUnion channel = options.get(3).getAsChannel();
                    Node node = Node.getNodeForChannel(channel.getIdLong());

                    if (propertyId == null) {
                        event.reply("Invalid property ID.").setEphemeral(true).queue();
                        return;
                    }
                    Structure realStructure = ctx.sparkTrain().getStructureData().values().stream().filter(str -> str.getName().equalsIgnoreCase(structure)).findFirst().orElse(null);
                    if (realStructure == null) {
                        event.reply("Structure '" + structure + "' is not a valid structure.").setEphemeral(true).queue();
                        return;
                    }
                    var requests = Node.getNodeForChannel(channel.getIdLong()) == Node.HOLLIS ? ctx.sparkTrain().getHollisBuildRequests() :
                            ctx.sparkTrain().getGlobalBuildRequests();
                    if (requests.containsKey(targetMemberId)) {
                        event.reply("This member already has an active build request on this train. Please remove it first.").setEphemeral(true).queue();
                        return;
                    }
                    event.deferReply(true).queue();
                    ctx.discord().execute(() -> {
                        try {
                            event.getHook().setEphemeral(true);
                            Property property = PropertyDataFetcher.fetchPropertySynchronous(propertyId);
                            if (property == null) {
                                event.getHook().editOriginal("Property could not be fetched. Either property ID is invalid or try again later.").complete();
                                return;
                            }
                            DeleteBuildRequestTask deleteTask = new DeleteBuildRequestTask(member, node != Node.HOLLIS);
                            deleteTask.call();
                            requests.put(targetMemberId, new UpaBuildRequest(targetMemberId, propertyId, realStructure.getName(), property.getFullAddress()));
                            event.getHook().editOriginal("Build request successfully added.").complete();
                        } catch (Exception e) {
                            logger.catching(e);
                            event.getHook().editOriginal("Request could not be completed correctly due to an error.").complete();
                        }
                    });
                    break;
                case "remove_build_request":
                    options = event.getOptions();
                    member = options.get(0).getAsMember();
                    channel = options.get(1).getAsChannel();
                    node = Node.getNodeForChannel(channel.getIdLong());
                    requests = node == Node.HOLLIS ? ctx.sparkTrain().getHollisBuildRequests() :
                            ctx.sparkTrain().getGlobalBuildRequests();
                    targetMemberId = member.getIdLong();

                    if (requests.remove(targetMemberId) == null) {
                        event.reply("This member does not have an active build request to remove.").setEphemeral(true).queue();
                    } else {
                        event.deferReply(true).queue();
                        SqlConnectionManager.getInstance().execute(new DeleteBuildRequestTask(member, node != Node.HOLLIS), success ->
                                        event.getHook().setEphemeral(true).editOriginal("Build request removed.").queue(),
                                failure -> event.getHook().setEphemeral(true).editOriginal("Build request removal failed.").queue());
                    }
                    break;
                case "transfer_ssh":
                    options = event.getOptions();
                    member = options.get(0).getAsMember();
                    GuildChannelUnion fromChannel = options.get(2).getAsChannel();
                    GuildChannelUnion toChannel = options.get(3).getAsChannel();
                    int amount = options.get(4).getAsInt();
                    Node fromNode = Node.getNodeForChannel(fromChannel.getIdLong());
                    Node toNode = Node.getNodeForChannel(toChannel.getIdLong());
                    event.deferReply(true).queue();
                    SqlConnectionManager.getInstance().execute(new SshTransferTask(member.getIdLong(), fromNode, toNode, amount),
                            success -> event.getHook().editOriginal("SSH transfer completed successfully.").queue(),
                            failure -> event.getHook().editOriginal("SSH transfer failed.").queue());
                    break;
                case "open_ticket":
                    options = event.getOptions();
                    member = options.get(0).getAsMember();
                    SupportButtonListener.openTicket(ctx, member, "Ticket opened by a staff member.", (c, t) -> {
                    });
                    break;
                case "award_referral":
                    //  addOption(OptionType.USER, "referrer", "The referrer.", true).
                    //          addOption(OptionType.INTEGER, "joined", "The member that joined because of the referrer.", true))).queue();
                    break;
                case "give_credit":
                    handleGiveCredit(ctx, event, event.getOptions().get(0).getAsMember(),
                            event.getOptions().get(1).getAsLong(),
                            event.getOptions().get(2).getAsString().trim());
                    break;
                case "force_link":
                    LinkCommand.handleLinkCommand(ctx, event, event.getOptions().get(0).getAsString(),
                            event.getOptions().get(1).getAsMember().getIdLong());
                    break;
                case "send_message":
                    handleSendMessage(event);
                    break;
                case "purge":
                    if (event.getChannelType() != ChannelType.TEXT) {
                        event.reply("Not a text channel.").setEphemeral(true).queue();
                        return;
                    }
                    event.reply("Starting...").setEphemeral(true).queue();
                    MessageHistory history = new MessageHistory(event.getChannel().asTextChannel());
                    history.retrievePast(100).queue(success -> {
                        for (Message m : success) {
                            if (m.getAuthor().isBot() && m.getAuthor().getIdLong() == 956871679584403526L) {
                                continue;
                            }
                            m.delete().queue();
                        }
                    });
                    break;
            }
        }
    }

    public static void handleGiveCredit(UpaBotContext ctx, IReplyCallback event, Member discordMember, long enteredAmount, String enteredReason) {
        if (discordMember == null) {
            event.reply("Member does not exist on this server.").setEphemeral(true).queue();
            return;
        }
        int amount = Ints.checkedCast(enteredAmount);
        UpaMember member = ctx.databaseCaching().getMembers().get(discordMember.getIdLong());
        if (member == null || !member.getActive().get()) {
            event.reply("Member ID is invalid or the member hasn't used /account yet. You can force link them using /admin force_link.").setEphemeral(true).queue();
            return;
        }
        event.reply("Sending " + amount + " PAC to " + member.getInGameName() + ". Please verify if it was successful in <#1058028231388835840>.").setEphemeral(true).queue();
        ctx.discord().sendCredit(new CreditTransaction(member, amount, CreditTransactionType.GIFTED, enteredReason));
    }

    private void handleSendMessage(SlashCommandInteractionEvent event) {
        if (event.getChannelType() != ChannelType.TEXT) {
            event.reply("You can only send a message to a text channel.").setEphemeral(true).queue();
            return;
        }
        event.getChannel().asTextChannel().sendMessage(event.getOptions().get(0).getAsString()).queue();
        event.reply("Message sent.").setEphemeral(true).queue();
    }
}
