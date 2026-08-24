package com.starlightuniverse.travel;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TravelListener implements Listener {

    private final RtpManager rtpManager;
    private final TpaManager tpaManager;

    public TravelListener(RtpManager rtpManager, TpaManager tpaManager) {
        this.rtpManager = rtpManager;
        this.tpaManager = tpaManager;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        tpaManager.loadPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        tpaManager.unloadPlayer(event.getPlayer().getUniqueId());
        rtpManager.clearCooldown(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RtpHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        int slot = event.getRawSlot();
        if (slot == 26) {
            player.closeInventory();
            return;
        }

        RtpManager.RtpWorld r = rtpManager.getWorldFromSlot(slot);
        if (r != null) {
            player.closeInventory();
            rtpManager.teleport(player, r);
        }
    }
}
