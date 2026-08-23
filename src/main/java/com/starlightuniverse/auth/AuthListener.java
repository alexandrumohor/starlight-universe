package com.starlightuniverse.auth;

import com.starlightuniverse.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

public class AuthListener implements Listener {

    private final JavaPlugin plugin;
    private final AuthManager authManager;
    private final SkinManager skinManager;

    public AuthListener(JavaPlugin plugin, AuthManager authManager, SkinManager skinManager) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.skinManager = skinManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String username = player.getName();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean registered = authManager.isRegistered(username);

            if (registered) {
                if (authManager.checkSession(username, ip)) {
                    authManager.saveSession(username, ip);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            authManager.setAuthenticated(player.getUniqueId());
                            Msg.success(player, "Session restored! Welcome back.");
                        }
                    });
                    return;
                }

                String premiumUuid = authManager.getPremiumUuid(username);
                if (premiumUuid != null) {
                    AuthManager.MojangProfile profile = authManager.checkMojangPremium(username);
                    if (profile != null) {
                        authManager.saveSession(username, ip);
                        SkinManager.SkinData skin = skinManager.fetchMojangSkin(premiumUuid);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                authManager.setAuthenticated(player.getUniqueId());
                                Msg.success(player, "Premium account verified! Auto-login successful.");
                                if (skin != null) skinManager.applySkin(player, skin);
                            }
                        });
                        return;
                    }
                }

                boolean hasPw = authManager.hasPassword(username);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        if (hasPw) {
                            Msg.info(player, "Welcome back! Type /login <password>");
                        } else {
                            Msg.info(player, "Set a password: /register <password> <confirm>");
                        }
                    }
                });
            } else {
                AuthManager.MojangProfile mojang = authManager.checkMojangPremium(username);
                if (mojang != null) {
                    authManager.registerPremium(username, mojang.id(), ip);
                    SkinManager.SkinData skin = skinManager.fetchMojangSkin(mojang.id());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            authManager.setAuthenticated(player.getUniqueId());
                            Msg.success(player, "Premium account detected! Auto-registered and logged in.");
                            if (skin != null) skinManager.applySkin(player, skin);
                        }
                    });
                    return;
                }

                int accountsOnIp = authManager.countAccountsOnIp(ip);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        if (accountsOnIp >= 2) {
                            player.kick(Msg.errorComponent("Maximum 2 accounts per IP address!"));
                            return;
                        }
                        Msg.info(player, "Welcome! Type /register <password> <confirm>");

                        SkinManager.SkinData randomSkin = skinManager.getRandomSkin();
                        if (randomSkin != null) skinManager.applySkin(player, randomSkin);
                    }
                });
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (authManager.isAuthenticated(player.getUniqueId())) {
            String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    authManager.saveSession(player.getName(), ip));
        }
        authManager.removeAuthenticated(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (authManager.isAuthenticated(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            event.setTo(from.clone().setDirection(to.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () ->
                    Msg.error(event.getPlayer(), "You must log in first!"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (authManager.isAuthenticated(event.getPlayer().getUniqueId())) return;
        String cmd = event.getMessage().toLowerCase().split(" ")[0];
        if (cmd.equals("/register") || cmd.equals("/login") || cmd.equals("/changepass")) return;
        event.setCancelled(true);
        Msg.error(event.getPlayer(), "You must log in first!");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !authManager.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && !authManager.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !authManager.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && !authManager.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && !authManager.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
