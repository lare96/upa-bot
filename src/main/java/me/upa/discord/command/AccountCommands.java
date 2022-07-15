package me.upa.discord.command;

import com.google.common.primitives.Longs;
import me.upa.UpaBot;
import me.upa.discord.DiscordService;
import me.upa.discord.UpaBuildRequest;
import me.upa.discord.UpaLottery;
import me.upa.discord.UpaMember;
import me.upa.discord.UpaProperty;
import me.upa.game.Structure;
import me.upa.service.DatabaseCachingService;
import me.upa.service.SparkTrainMicroService;
import me.upa.sql.SqlConnectionManager;
import me.upa.sql.SqlTask;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu.Builder;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AccountCommands extends ListenerAdapter {


    private static final class PropertyRequest {
        private final long propertyId;
        private final boolean firstBuild;

        private PropertyRequest(long propertyId, boolean firstBuild) {
            this.propertyId = propertyId;
            this.firstBuild = firstBuild;
        }
    }

    private final Map<Long, PropertyRequest> propertyRequests = new ConcurrentHashMap<>();

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getName().equals("account")) {
            handleOverviewCommand(event);
        }
    }

    @Override
    public void onSelectMenuInteraction(@NotNull SelectMenuInteractionEvent event) {
        switch (event.getSelectMenu().getId()) {
            case "select_property":
                Long propertyId = Longs.tryParse(event.getInteraction().getValues().get(0));
                if(propertyId == null) {
                    propertyId = -1L;
                }
                selectProperty(propertyId, event.getMember().getIdLong(), (msg, menu) -> {
                    event.getInteraction().editSelectMenu(menu.build()).queue();
                    event.getHook().setEphemeral(true).editOriginal(msg).queue();
                }, failedMsg -> event.reply(failedMsg).setEphemeral(true).queue());
                break;
            case "select_structure":
                PropertyRequest request = propertyRequests.get(event.getMember().getIdLong());
                String structureName = event.getInteraction().getValues().get(0);
                Structure structure = UpaBot.getSparkTrainMicroService().getStructureData().get(structureName);

                if (request == null) {
                    event.reply("Bot has been restarted. Please try again.").setEphemeral(true).queue();
                    return;
                }
                if (structure == null) {
                    event.reply("Invalid structure selected.").setEphemeral(true).queue();
                    return;
                }
                UpaMember mem = UpaBot.getDatabaseCachingService().getMembers().get(event.getMember().getIdLong());
                if (mem == null) {
                    event.reply("Invalid member ID.").setEphemeral(true).queue();
                    return;
                }
                double ssh = mem.getTotalSsh();
                int sshNeeded = structure.getSshRequired();
                if (request.firstBuild) {
                    sshNeeded = structure.getSshRequired() / 2;
                }
                if (ssh < sshNeeded) {
                    event.reply("You need an additional **" + DiscordService.COMMA_FORMAT.format(sshNeeded - ssh) + " SSH** in order to request this kind of build.").setEphemeral(true).queue();
                    return;
                }
                UpaProperty buildRequestProp = UpaBot.getDatabaseCachingService().getProperties().get(request.propertyId);
                if (buildRequestProp == null) {
                    event.reply("Your requested property could not be found in our databases.").setEphemeral(true).queue();
                    return;
                }
                if (Objects.equals(buildRequestProp.getBuildStatus().get(), "Completed")) {
                    event.reply("This property already has a build on it.").setEphemeral(true).queue();
                    return;
                }
                event.deferReply(true).queue();
                SqlConnectionManager.getInstance().execute(new SqlTask<Void>() {
                    @Override
                    public Void execute(Connection connection) throws Exception {
                        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO build_requests(member_id, property_id,structure_name) VALUES (?,?,?);")) {
                            ps.setLong(1, event.getMember().getIdLong());
                            ps.setLong(2, request.propertyId);
                            ps.setString(3, structureName);
                            if (ps.executeUpdate() != 1) {
                                throw new RuntimeException("Build request not inserted into database!");
                            }
                        }
                        return null;
                    }
                }, success -> {
                    propertyRequests.remove(event.getMember().getIdLong());
                    event.getHook().setEphemeral(true).editOriginal("You have successfully secured your build request. It should appear in <#990518630129221652> and <#963112034726195210> shortly.\n\nPlease start your build as soon as possible, or you may be skipped!").queue();
                    event.getInteraction().editSelectMenu(null).queue();
                    UpaBot.getSparkTrainMicroService().getBuildRequests().put(event.getMember().getIdLong(), new UpaBuildRequest(mem.getMemberId(), request.propertyId, structureName));
                    UpaBot.getDiscordService().guild().getTextChannelById(963112034726195210L).sendMessage("<@" + mem.getMemberId() + "> has requested structure '" + structureName + "' on **" + buildRequestProp.getAddress() + "**.").queue();
                });
                break;
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (event.getButton().getId() == null) {
            return;
        }
        switch (event.getButton().getId()) {
            case "confirm_cancel_a_build":
                if (UpaBot.getSparkTrainMicroService().getBuildRequests().containsKey(event.getMember().getIdLong())) {
                    event.deferReply(true).queue();
                    SqlConnectionManager.getInstance().execute(new SqlTask<Void>() {
                        @Override
                        public Void execute(Connection connection) throws Exception {
                            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM build_requests WHERE member_id = ?;")) {
                                ps.setLong(1, event.getMember().getIdLong());
                                if (ps.executeUpdate() != 1) {
                                    throw new RuntimeException("Build was not cancelled.");
                                }
                            }
                            return null;
                        }
                    }, success -> {
                        UpaBot.getSparkTrainMicroService().getBuildRequests().remove(event.getMember().getIdLong());
                        event.getHook().setEphemeral(true).editOriginal("Your build request has successfully been cancelled.").queue();
                        event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                        UpaBot.getDiscordService().guild().getTextChannelById(963112034726195210L).sendMessage(event.getMember().getAsMention() + " has cancelled their build request!").queue();
                    });
                }
                break;
            case "cancel_a_build":
                event.reply("Are you absolutely sure you would like to cancel your build request?").
                        addActionRow(Button.of(ButtonStyle.DANGER, "confirm_cancel_a_build", "Yes", Emoji.fromUnicode("U+2705"))).setEphemeral(true).queue();
                break;
            case "request_a_build":
                UpaMember requester = UpaBot.getDatabaseCachingService().getMembers().get(event.getMember().getIdLong());
                if (requester == null) {
                    event.reply("You must be a member to request a build.").setEphemeral(true).queue();
                    return;
                } else if (UpaBot.getSparkTrainMicroService().hasActiveBuild(event.getMember().getIdLong())) {
                    event.reply("You have already requested a build. Please cancel first before requesting another.").setEphemeral(true).queue();
                    return;
                }
                Set<UpaProperty> propertiesOwned = UpaBot.getDatabaseCachingService().getMemberProperties().get(requester.getKey());
                if (propertiesOwned.isEmpty()) {
                    event.reply("You must own at least 1 property in Hollis to request a build.").setEphemeral(true).queue();
                } else {
                    if (propertiesOwned.size() > SelectMenu.OPTIONS_MAX_AMOUNT) {
                        event.replyModal(Modal.create("select_property_form", "Property you wish to build on").
                                addActionRow(TextInput.create("select_property_form_link", "Property link", TextInputStyle.SHORT).
                                        setRequired(true).setPlaceholder("https://play.upland.me/?prop_id=77296058553954").build()).build()).queue();
                    } else {
                        ReplyCallbackAction pendingReply = event.reply("Please select which property you'd like to build on.");
                        SelectMenu.Builder propertyListBldr = SelectMenu.create("select_property");
                        StringBuilder sb = new StringBuilder();
                        for (UpaProperty property : propertiesOwned) {
                            if (!property.getBuildStatus().get().equals("Completed") && property.getUp2() >= Structure.MIN_UP2) {
                                propertyListBldr.addOption(property.getAddress() + ", Queens", String.valueOf(property.getPropertyId()), sb.append(property.getUp2()).append(" UP2").append(property.isGenesis() ? " | Genesis" : "").toString());
                            }
                            sb.setLength(0);
                        }
                        pendingReply.addActionRow(propertyListBldr.build()).setEphemeral(true).queue();
                    }
                }
                event.getInteraction().editButton(event.getButton().asDisabled()).queue();
                break;
            /*   case "report_a_bug":
                event.replyModal(Modal.create("report_a_bug_form", "Report a bug/Send feedback").
                        addActionRow(TextInput.create("report_a_bug_form_subject", "Subject", TextInputStyle.SHORT).
                                setRequired(true).setPlaceholder("I love UPA!").build()).
                        addActionRow(TextInput.create("report_a_bug_form_description", "Description", TextInputStyle.PARAGRAPH).
                                setRequired(true).setPlaceholder("I love everything about this server, especially that dapper g.o.a.t! How is he so rich?").build()).
                        build()).queue();
                break;*/
            case "pac_store":
                PacCommands.openStore(event);
                event.editButton(event.getButton().asDisabled()).queue();
                break;
            case "mark_acknowledged":
                if (event.getTextChannel().getIdLong() == 984551707176480778L && event.getMember() != null && !event.getMember().getUser().isBot()) {
                    Message success = event.getMessage();
                    success.delete().queue();
                }
                break;
            case "link":
                event.replyModal(Modal.create("link_form", "Please enter your in-game name").
                        addActionRow(TextInput.create("link_form_ign", "In-game name", TextInputStyle.SHORT).
                                setRequired(true).setPlaceholder("rich_goat").build()).
                        build()).queue();
                break;
        }
    }

    public static Button linkButton() {
        return Button.of(ButtonStyle.PRIMARY, "link", "Create account", Emoji.fromUnicode("U+1F517"));
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        switch (event.getModalId()) {
            case "report_a_bug_form":
                event.deferReply(true).queue();
                String subject = event.getValue("report_a_bug_form_subject").getAsString();
                String description = event.getValue("report_a_bug_form_description").getAsString();
                Member requester = event.getMember();
                if (requester != null) {
                    UpaMember upaMember = UpaBot.getDatabaseCachingService().getMembers().get(requester.getIdLong());
                    if (upaMember != null)
                        UpaBot.getDiscordService().sendFeedbackOrBugMsg(upaMember, subject, description,
                                msg -> event.getHook().setEphemeral(true).editOriginal("Your feedback has been sent to the UPA team. If we find it useful we will award you with PAC!").queue());
                }
                break;
            case "link_form":
                String inGameName = event.getValue("link_form_ign").getAsString();
                LinkCommand.handleLinkCommand(event, inGameName, -1L);
                break;
            case "select_property_form":
                String propertyLink = event.getValue("select_property_form_link").getAsString().trim();
                if (propertyLink.isEmpty() || propertyLink.isBlank() || !propertyLink.startsWith("https://play.upland.me/?prop_id=")) {
                    event.reply("Invalid property link. Please try again.").setEphemeral(true).queue();
                    return;
                }
                Long propertyId = Longs.tryParse(propertyLink.replace("https://play.upland.me/?prop_id=", "").trim());
                if (propertyId == null) {
                    propertyId = -1L;
                }
                selectProperty(propertyId, event.getMember().getIdLong(), (msg, menu) -> {
                    event.reply(msg).addActionRow(menu.build()).setEphemeral(true).queue();
                }, failedMsg -> event.reply(failedMsg).setEphemeral(true).queue());
                break;
        }
    }

    private void selectProperty(long propertyId, long memberId, BiConsumer<String, Builder> finish, Consumer<String> failed) {
        if (propertyId == -1) {
            failed.accept("Invalid property ID. Please try again.");
            return;
        }
        UpaProperty upaProp = UpaBot.getDatabaseCachingService().getProperties().get(propertyId);
        if (upaProp == null) {
            failed.accept("Must be a registered Hollis property that you own. Please try again.");
            return;
        }
        UpaMember upaMember = UpaBot.getDatabaseCachingService().getMembers().get(memberId);
        if (upaMember == null) {
            failed.accept("Please become a UPA member by using /account first.");
            return;
        }
        if (upaMember.getKey() != upaProp.getMemberKey()) {
            failed.accept("You do not own this property.");
            return;
        }
        boolean firstBuild = UpaBot.getDatabaseCachingService().getMemberProperties().get(upaProp.getMemberKey()).stream().
                noneMatch(next -> next.getBuildStatus().get().equals("Completed"));
        SelectMenu.Builder selectStructureBldr = SelectMenu.create("select_structure");
        for (Structure structure : UpaBot.getSparkTrainMicroService().getSuitableStructures(upaProp.getUp2())) {
            selectStructureBldr.addOption(structure.getName() + " (" + (firstBuild ? (structure.getSshRequired() / 2) : structure.getSshRequired()) + " SSH)", structure.getName());
        }

        propertyRequests.put(memberId, new PropertyRequest(propertyId, firstBuild));
        String message = "Please select which structure you'd like to build on **" + upaProp.getAddress() + "**.";
        if (firstBuild) {
            message += " If this is your first build, you only require half the amount of SSH.";
        }
        finish.accept(message, selectStructureBldr);
    }

    private void handleOverviewCommand(SlashCommandInteractionEvent event) {
        Role lotteryRole = UpaBot.getDiscordService().guild().getRoleById(983750455429570620L);
        DatabaseCachingService databaseCaching = UpaBot.getDatabaseCachingService();
        long memberId = event.getMember().getIdLong();
        if (DiscordService.THROTTLER.needsThrottling(memberId)) {
            event.reply("Please wait before using this again.").setEphemeral(true).queue();
            return;
        }
        UpaMember upaMember = databaseCaching.getMembers().get(memberId);
        if (upaMember == null) {
            event.reply("It seems you are not a UPA member. Would you like to become one?").addActionRow(
                    linkButton()).setEphemeral(true).queue();
            return;
        }
        SparkTrainMicroService sparkTrain = UpaBot.getSparkTrainMicroService();
        UpaBuildRequest buildRequest = UpaBot.getSparkTrainMicroService().getBuildRequests().get(event.getMember().getIdLong());
        int propertiesOwned = databaseCaching.getMemberProperties().get(upaMember.getKey()).size();
        event.deferReply().setEphemeral(true).queue();

        String notification = null;
        if(!sparkTrain.hasActiveBuild(memberId) && buildRequest == null && upaMember.getTotalSsh() >= sparkTrain.getLeastRequiredSsh()) {
           notification = "**You meet the SSH threshold to request a build! You can do so using the \"Request a build\" button below.**";
        } else if(event.getMember().getRoles().contains(lotteryRole) && !UpaBot.getPacLotteryMicroService().hasTicket(memberId)) {
            UpaLottery lottery = UpaBot.variables().lottery().getValue();
            notification = "A lottery with a pot of **"+lottery.getPac().get()+" PAC** is ongoing, buy a ticket @ <#993201967096660069>!";
        }
        var sb = new StringBuilder(notification != null ? notification : "");
        sb.append("```\n");
        sb.append("! ~ Account ~ !").append('\n');
        sb.append("In-game name: ").append(upaMember.getInGameName()).append('\n');
        sb.append("Blockchain account ID: ").append(upaMember.getBlockchainAccountId()).append('\n');
        sb.append("PAC: ").append(upaMember.getCredit()).append('\n');
        sb.append("Referrals: ").append(upaMember.getReferrals().get()).append('\n');
        sb.append("Join date: ").append(DiscordService.DATE_FORMAT.format(upaMember.getJoinDate())).append("\n\n\n");
        sb.append("! ~ Node ~ !").append('\n');
        sb.append("Properties owned: ").append(propertiesOwned).append('\n');
        sb.append("Total UP2: ").append(DiscordService.COMMA_FORMAT.format(upaMember.getTotalUp2().get())).append('\n');
        int rank = 1;
        for (var next : UpaBot.getDiscordService().getStatisticsCommand().getStatisticsData().getTopOwners().entrySet()) {
            if (memberId == next.getKey()) {
                break;
            }
            rank++;
        }
        sb.append("Ownership ranking #").append(rank).append('\n');
        sb.append("Passenger #").append(upaMember.getSparkTrainPlace().get()).append(" with ").append(DiscordService.DECIMAL_FORMAT.format(upaMember.getSparkTrainSsh().get())).append(" SSH").append("\n\n\n");
        sb.append("! ~ Contributions ~ !").append('\n');
        sb.append("Sends: ").append(upaMember.getSends().get()).append("\n");
        sb.append("Sponsored sends: ").append(upaMember.getSponsoredSends().get()).append("\n");
        rank = 1;
        for (var next : UpaBot.getDiscordService().getScholarshipCommands().computeLeaderboard()) {
            if (next.getMemberId() == memberId) {
                break;
            }
            rank++;
        }
        sb.append("Leaderboard position #").append(rank).append("\n");
        sb.append("```");
        event.getHook().setEphemeral(true);
        event.getHook().sendMessage(sb.toString()).addActionRow(
                buildRequest != null ? Button.of(ButtonStyle.DANGER, "cancel_a_build", "Cancel '" + buildRequest.getStructureName() + "' build", Emoji.fromUnicode("U+1F6A7")) :
                        UpaBot.getSparkTrainMicroService().hasActiveBuild(event.getMember().getIdLong()) ?
                                Button.of(ButtonStyle.DANGER, "cancel_a_build", "Cancel build", Emoji.fromUnicode("U+1F6A7")).asDisabled() :
                                Button.of(ButtonStyle.SUCCESS, "request_a_build", "Request a build", Emoji.fromUnicode("U+1F6A7")),
                Button.of(ButtonStyle.PRIMARY, "pac_store", "PAC store", Emoji.fromUnicode("U+1F3EA"))).queue();
    }
}
