package com.starlightuniverse.chat;

import com.starlightuniverse.arena.ArenaWorlds;
import com.starlightuniverse.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;

public class ChatListener implements Listener {

    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor TEAL = TextColor.color(0x00AAAA);

    private final ChatManager chatManager;

    public ChatListener(ChatManager chatManager) {
        this.chatManager = chatManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        chatManager.loadPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        chatManager.unloadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        ChatManager.ChatChannel channel = chatManager.getEffectiveChannel(uuid);

        if (channel == ChatManager.ChatChannel.STAFF) {
            sendStaffChat(player, rawMessage);
            return;
        }

        if (channel == ChatManager.ChatChannel.TEAM) {
            var team = chatManager.getTeamManager().getPlayerTeam(uuid);
            if (team == null) {
                Bukkit.getScheduler().runTask(chatManager.getPlugin(), () ->
                        Msg.error(player, "You are not in a team! Switched to global chat."));
                chatManager.switchToGlobal(uuid);
                return;
            }
            sendTeamChat(player, rawMessage);
            return;
        }

        if (!chatManager.canChat(uuid)) {
            long remaining = chatManager.getCooldownRemaining(uuid);
            Bukkit.getScheduler().runTask(chatManager.getPlugin(), () ->
                    Msg.error(player, "Chat cooldown! Wait " +
                            String.format("%.1f", remaining / 1000.0) + "s."));
            return;
        }

        if (!chatManager.isStaffExempt(uuid) &&
                !chatManager.getAdminManager().canChat(uuid)) {
            Bukkit.getScheduler().runTask(chatManager.getPlugin(), () ->
                    Msg.error(player, "Chat is in slow mode! Wait " +
                            chatManager.getAdminManager().getSlowModeSeconds() + "s."));
            return;
        }

        if (!chatManager.isStaffExempt(uuid) && chatManager.containsLink(rawMessage)) {
            Bukkit.getScheduler().runTask(chatManager.getPlugin(), () ->
                    Msg.error(player, "Links are not allowed in chat!"));
            return;
        }

        String filteredMessage = chatManager.filterSwears(rawMessage);

        chatManager.recordChatCooldown(uuid);
        if (!chatManager.isStaffExempt(uuid)) {
            chatManager.getAdminManager().recordChat(uuid);
        }

        Component formattedMessage = chatManager.buildChatMessage(player, filteredMessage, null);

        handleMentions(rawMessage, player);

        if (channel == ChatManager.ChatChannel.LOCAL) {
            sendLocalChat(player, formattedMessage);
        } else {
            Set<String> crossScopes = collectStaffCrossScopeMentions(rawMessage, player);
            if (crossScopes != null && !crossScopes.isEmpty()) {
                sendToScopes(player, formattedMessage, crossScopes);
            } else {
                sendGlobalChat(player, formattedMessage);
            }
        }

        sendToSpies(player, formattedMessage, channel);
    }

    private void sendGlobalChat(Player sender, Component message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!inSameChatScope(sender, online)) continue;
            online.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    private boolean inSameChatScope(Player sender, Player receiver) {
        return chatScopeKey(sender.getWorld().getName())
                .equals(chatScopeKey(receiver.getWorld().getName()));
    }

    private String chatScopeKey(String worldName) {
        if (ArenaWorlds.isArenaWorld(worldName)) return worldName;
        return "MAIN";
    }

    private Set<String> collectStaffCrossScopeMentions(String message, Player sender) {
        UUID senderId = sender.getUniqueId();
        if (chatManager.getAdminManager().getAdminLevel(senderId) <= 0) return null;

        Set<String> scopes = new HashSet<>();
        String senderScope = chatScopeKey(sender.getWorld().getName());
        Matcher matcher = ChatManager.MENTION_PATTERN.matcher(message);
        while (matcher.find()) {
            String name = matcher.group(1);
            Player mentioned = Bukkit.getPlayerExact(name);
            if (mentioned == null || !mentioned.isOnline() || mentioned.equals(sender)) continue;
            String targetScope = chatScopeKey(mentioned.getWorld().getName());
            if (!targetScope.equals(senderScope)) scopes.add(targetScope);
        }
        return scopes;
    }

    private void sendToScopes(Player sender, Component message, Set<String> targetScopes) {
        boolean senderSeen = false;
        for (Player online : Bukkit.getOnlinePlayers()) {
            String scope = chatScopeKey(online.getWorld().getName());
            if (targetScopes.contains(scope)) {
                online.sendMessage(message);
                if (online.equals(sender)) senderSeen = true;
            }
        }
        if (!senderSeen && sender.isOnline()) sender.sendMessage(message);
        Bukkit.getConsoleSender().sendMessage(message);
    }

    private void sendLocalChat(Player sender, Component message) {
        Component localMessage = Component.text("[L] ", TEAL).append(message);
        int rangeSquared = 100 * 100;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getWorld().equals(sender.getWorld())) {
                try {
                    if (online.getLocation().distanceSquared(sender.getLocation()) <= rangeSquared) {
                        online.sendMessage(localMessage);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        Bukkit.getConsoleSender().sendMessage(localMessage);
    }

    private void sendTeamChat(Player sender, String message) {
        String filtered = chatManager.filterSwears(message);
        chatManager.getTeamManager().sendTeamChat(sender, filtered);

        sendTeamSpies(sender, filtered);
    }

    private void sendStaffChat(Player sender, String message) {
        Component staffMsg = Component.text("[SC] ", RED)
                .append(Component.text(sender.getName(), WHITE))
                .append(Component.text(": ", GRAY))
                .append(Component.text(message, WHITE));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (chatManager.getAdminManager().getAdminLevel(online.getUniqueId()) > 0) {
                online.sendMessage(staffMsg);
            }
        }
        Bukkit.getConsoleSender().sendMessage(staffMsg);
    }

    public void sendReplyMessage(Player sender, String targetName, String message) {
        String filtered = chatManager.filterSwears(message);
        Component formattedMessage = chatManager.buildChatMessage(sender, filtered, targetName);

        handleMentions(message, sender);

        ChatManager.ChatChannel channel = chatManager.getEffectiveChannel(sender.getUniqueId());
        if (channel == ChatManager.ChatChannel.LOCAL) {
            Component localMessage = Component.text("[L] ", TEAL).append(formattedMessage);
            int rangeSquared = 100 * 100;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getWorld().equals(sender.getWorld())) {
                    try {
                        if (online.getLocation().distanceSquared(sender.getLocation()) <= rangeSquared) {
                            online.sendMessage(localMessage);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } else {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!inSameChatScope(sender, online)) continue;
                online.sendMessage(formattedMessage);
            }
        }
        Bukkit.getConsoleSender().sendMessage(formattedMessage);
    }

    private void handleMentions(String message, Player sender) {
        UUID senderId = sender.getUniqueId();
        int adminLevel = chatManager.getAdminManager().getAdminLevel(senderId);
        int premiumLevel = chatManager.getAdminManager().getPremiumLevel(senderId);
        boolean staff = adminLevel > 0;
        boolean premium = premiumLevel > 0;

        if (!staff && !premium) return;

        Matcher matcher = ChatManager.MENTION_PATTERN.matcher(message);
        while (matcher.find()) {
            String name = matcher.group(1);
            Player mentioned = Bukkit.getPlayerExact(name);
            if (mentioned == null || !mentioned.isOnline() || mentioned.equals(sender)) continue;

            boolean sameScope = inSameChatScope(sender, mentioned);
            if (!sameScope && !staff) continue;

            chatManager.playMentionSound(mentioned);
            chatManager.setReplyTarget(mentioned.getUniqueId(), senderId);
        }
    }

    private void sendToSpies(Player sender, Component message, ChatManager.ChatChannel channel) {
        if (channel != ChatManager.ChatChannel.LOCAL) return;

        int rangeSquared = 100 * 100;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!chatManager.getAdminManager().isSpy(online.getUniqueId())) continue;
            boolean alreadyReceived = online.getWorld().equals(sender.getWorld());
            if (alreadyReceived) {
                try {
                    alreadyReceived = online.getLocation().distanceSquared(
                            sender.getLocation()) <= rangeSquared;
                } catch (IllegalArgumentException e) {
                    alreadyReceived = false;
                }
            }
            if (!alreadyReceived) {
                online.sendMessage(Component.text("[SPY] ", GRAY).append(message));
            }
        }
    }

    private void sendTeamSpies(Player sender, String message) {
        var team = chatManager.getTeamManager().getPlayerTeam(sender);
        if (team == null) return;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!chatManager.getAdminManager().isSpy(online.getUniqueId())) continue;
            if (team.getMembers().containsKey(online.getName().toLowerCase())) continue;

            Component spyMsg = Component.text("[SPY-TC] ", GRAY)
                    .append(Component.text("[" + team.getName() + "] ", CYAN))
                    .append(Component.text(sender.getName(), YELLOW))
                    .append(Component.text(" >> ", GRAY))
                    .append(Component.text(message, WHITE));
            online.sendMessage(spyMsg);
        }
    }
}
