package com.starlightuniverse.starshop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class StarShopListener implements Listener {

    private final JavaPlugin plugin;
    private final StarShopManager manager;

    public StarShopListener(JavaPlugin plugin, StarShopManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof StarShopHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;

        manager.handleClick(player, rawSlot);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!manager.hasSession(player.getUniqueId())) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof StarShopHolder)) {
                manager.removeSession(player);
            }
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.removeSession(event.getPlayer());
    }
}
