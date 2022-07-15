package me.upa.discord;

import me.upa.UpaBot;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteDeleteEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class InviteTracker extends ListenerAdapter {

    private static final Path JOINED = Paths.get("data", "invites.bin");
    private final KeySetView<String, Boolean> joinCache = ConcurrentHashMap.newKeySet();
    private final Map<String, Invite> cachedInvites = new ConcurrentHashMap<>();

    @Override
    public void onReady(@NotNull ReadyEvent event) {
     //   if (Files.exists(JOINED)) {
      //      joinCache.addAll(UpaBot.load(JOINED));
    //        computeInvites(invite -> cachedInvites.put(invite.getCode(), invite), true);
       // }
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        // TODO Do this in a microservice maybe?
      /*  String userId = event.getUser().getId();
        if (joinCache.add(userId)) {
            UpaBot.save(JOINED, joinCache);
            computeInvites(newInvite -> {
                if (newInvite.getInviter() == null) {
                    return;
                }
                Invite oldInvite = cachedInvites.put(newInvite.getInviter().getId(), newInvite);
                if (oldInvite == null) {
                   logger.warning("No old invite found! Caching error.");
                    return;
                }
                if (newInvite.getUses() > oldInvite.getUses() && newInvite.getInviter() != null) {
                    String referrerId = newInvite.getInviter().getId();
                    UpaMember upaMember = UpaBot.getDatabaseCachingService().getMembers().get();
                    if(referrerId !=)
                    UpaBot.getDiscordService().sendCredit(new CreditTransaction(upaMember));
                }
            });
        }*/
    }

    @Override
    public void onGuildInviteCreate(@NotNull GuildInviteCreateEvent event) {
      //  Invite invite = event.getInvite();
      //  cachedInvites.put(invite.getCode(), invite);
    }

    @Override
    public void onGuildInviteDelete(@NotNull GuildInviteDeleteEvent event) {
      //  cachedInvites.remove(event.getCode());
    }

    private void computeInvites(Consumer<Invite> onLoop, boolean synchronous) {
        if (synchronous) {
            UpaBot.getDiscordService().guild().retrieveInvites().complete().forEach(invite ->
                    onLoop.accept(invite.expand().complete()));
        } else {
            UpaBot.getDiscordService().guild().retrieveInvites().queue(invites -> {
                for (Invite invite : invites) {
                    invite.expand().queue(onLoop);
                }
            });
        }
    }
}
