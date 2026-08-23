package com.starlightuniverse.admin;

import com.starlightuniverse.StarlightUniverse;
import com.starlightuniverse.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

public class AdminListener implements Listener {

    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor RED = TextColor.color(0xFF5555);

    private final StarlightUniverse plugin;
    private final AdminManager adminManager;

    public AdminListener(StarlightUniverse plugin, AdminManager adminManager) {
        this.plugin = plugin;
        this.adminManager = adminManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String username = event.getName();
        AdminManager.BanInfo ban = adminManager.getActiveBanSync(username);
        if (ban != null) {
            if (adminManager.trackBanJoinAttempt(username)) {
                adminManager.banPlayer(username, "System", "Auto-ban: rapid login attempts", 0);
            }
            adminManager.incrementLoginAttempts(username);
            String msg = "[SU] You are banned!\nReason: " + ban.reason() + "\nBanned by: " + ban.bannedBy();
            if (ban.expireDate() != null) {
                msg += "\nExpires: " + ban.expireDate();
            } else {
                msg += "\nDuration: Permanent";
            }
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Component.text(msg));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String username = player.getName();

        adminManager.loadPlayer(player.getUniqueId(), username);
        adminManager.loadMuteStatus(player.getUniqueId(), username);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            int adminLevel = adminManager.getAdminLevel(player.getUniqueId());

            if (adminLevel >= AdminRank.OWNER.getLevel()) {
                player.setOp(true);
            }

            if (adminLevel > 0) {
                AdminRank rank = AdminRank.fromLevel(adminLevel);
                Component notification = Msg.prefix()
                        .append(Component.text(rank.getPrefix() + " ", rank.getColor()))
                        .append(Component.text(username, WHITE))
                        .append(Component.text(" has logged in.", GRAY));
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.equals(player)) continue;
                    if (adminManager.getAdminLevel(online.getUniqueId()) > 0) {
                        online.sendMessage(notification);
                    }
                }
            }

            if (adminLevel == 0) {
                for (java.util.UUID vanishedUuid : adminManager.getVanishedPlayers()) {
                    Player vanished = Bukkit.getPlayer(vanishedUuid);
                    if (vanished != null) player.hidePlayer(plugin, vanished);
                }
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        adminManager.unloadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFrozenMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!adminManager.isFrozen(player.getUniqueId())) return;
        if (!plugin.getAuthManager().isAuthenticated(player.getUniqueId())) return;

        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            event.setTo(from.clone().setDirection(to.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        java.util.UUID uuid = player.getUniqueId();

        if (adminManager.isInStaffChat(uuid)) {
            event.setCancelled(true);
            Component staffMsg = Component.text("[SC] ", RED)
                    .append(Component.text(player.getName(), WHITE))
                    .append(Component.text(": ", GRAY))
                    .append(event.message());
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (adminManager.getAdminLevel(online.getUniqueId()) > 0) {
                        online.sendMessage(staffMsg);
                    }
                }
            });
            return;
        }

        if (adminManager.isMuted(uuid)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> Msg.error(player, "You are muted!"));
            return;
        }

        if (adminManager.getAdminLevel(uuid) == 0 && !adminManager.canChat(uuid)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () ->
                    Msg.error(player, "Chat is in slow mode! Wait " + adminManager.getSlowModeSeconds() + "s."));
            return;
        }
        if (adminManager.getAdminLevel(uuid) == 0) {
            adminManager.recordChat(uuid);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFrozenCommand(PlayerCommandPreprocessEvent event) {
        if (!adminManager.isFrozen(event.getPlayer().getUniqueId())) return;
        if (!plugin.getAuthManager().isAuthenticated(event.getPlayer().getUniqueId())) return;
        String cmd = event.getMessage().toLowerCase().split(" ")[0];
        if (cmd.equals("/login") || cmd.equals("/register")) return;
        event.setCancelled(true);
        Msg.error(event.getPlayer(), "You are frozen!");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFrozenInteract(PlayerInteractEvent event) {
        if (!adminManager.isFrozen(event.getPlayer().getUniqueId())) return;
        if (!plugin.getAuthManager().isAuthenticated(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }
}
