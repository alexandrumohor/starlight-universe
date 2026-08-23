package com.starlightuniverse.economy;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EconomyListener implements Listener {

    private final EconomyManager economyManager;

    public EconomyListener(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        economyManager.loadPlayer(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        economyManager.unloadPlayer(event.getPlayer().getUniqueId());
    }
}
