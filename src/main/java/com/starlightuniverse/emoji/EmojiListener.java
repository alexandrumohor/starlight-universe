package com.starlightuniverse.emoji;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EmojiListener implements Listener {

    private final EmojiManager emojiManager;

    public EmojiListener(EmojiManager emojiManager) {
        this.emojiManager = emojiManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        emojiManager.loadPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        emojiManager.unloadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof EmojiCommand.EmojiHolder)) return;
        event.setCancelled(true);
        if (event.getSlot() == 53 && event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
            emojiManager.purchaseUnlock(player);
            new EmojiCommand(emojiManager).openGui(player);
        }
    }
}
