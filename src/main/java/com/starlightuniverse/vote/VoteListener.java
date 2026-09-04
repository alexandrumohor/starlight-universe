package com.starlightuniverse.vote;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class VoteListener implements Listener {

    private final VoteManager manager;

    public VoteListener(VoteManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof VoteHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;
        manager.handleClick(player, rawSlot);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.onPlayerQuit(event.getPlayer().getUniqueId());
    }
}
