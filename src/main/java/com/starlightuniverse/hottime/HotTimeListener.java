package com.starlightuniverse.hottime;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class HotTimeListener implements Listener {

    private final HotTimeManager manager;

    public HotTimeListener(HotTimeManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!manager.isActive()) return;
        // Boost dropped XP
        event.setDroppedExp(manager.applyXpAmount(event.getDroppedExp()));
        // Boost each drop stack's amount
        for (ItemStack stack : event.getDrops()) {
            if (stack == null || stack.getType().isAir()) continue;
            int scaled = manager.applyDropAmount(stack.getAmount());
            int max = stack.getMaxStackSize();
            stack.setAmount(Math.min(max, scaled));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExp(BlockExpEvent event) {
        if (!manager.isActive()) return;
        event.setExpToDrop(manager.applyXpAmount(event.getExpToDrop()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (manager.isActive()) manager.addPlayerToBar(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.removePlayerFromBar(event.getPlayer());
    }
}
