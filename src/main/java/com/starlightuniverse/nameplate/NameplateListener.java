package com.starlightuniverse.nameplate;

import com.starlightuniverse.StarlightUniverse;
import com.starlightuniverse.auth.AuthManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class NameplateListener implements Listener {

    private final JavaPlugin plugin;
    private final NameplateManager mgr;
    private final AuthManager authManager;

    public NameplateListener(JavaPlugin plugin, NameplateManager mgr, AuthManager authManager) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.authManager = authManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Nameplate spawn is triggered by AuthListener after auth success
        // (session restore / /login / /register) once the player is in the
        // lobby world. No pre-auth spawn here — it would spawn in the wrong
        // world and get replaced on teleport, causing a visible flash.
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        mgr.removeFor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) mgr.spawnFor(player);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (event.getFrom().getWorld() != null && event.getTo() != null &&
                !event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    mgr.spawnFor(player);
                    // Force-remount every other player's nameplate in the
                    // destination world so this observer's entity tracker
                    // receives a fresh SetPassengers packet for each of them.
                    for (Player other : player.getWorld().getPlayers()) {
                        if (other.equals(player)) continue;
                        mgr.remount(other);
                    }
                }
            }, 5L);
        }
    }
}
