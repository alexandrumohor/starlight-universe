package com.starlightuniverse.skill;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SkillDataListener implements Listener {

    private final SkillManager skillManager;

    public SkillDataListener(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        skillManager.loadPlayer(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        skillManager.savePlayer(event.getPlayer());
        skillManager.unloadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof SkillHolder) {
            event.setCancelled(true);
        }
    }
}
