package com.starlightuniverse.order;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class OrderListener implements Listener {

    private final JavaPlugin plugin;
    private final OrderManager orderManager;

    public OrderListener(JavaPlugin plugin, OrderManager orderManager) {
        this.plugin = plugin;
        this.orderManager = orderManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof OrderDeliveryHolder) {
            if (!(event.getWhoClicked() instanceof Player)) return;
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof OrderHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;

        orderManager.handleClick(player, rawSlot);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        if (event.getView().getTopInventory().getHolder() instanceof OrderDeliveryHolder) {
            ItemStack[] contents = event.getView().getTopInventory().getContents().clone();
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null) contents[i] = contents[i].clone();
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                orderManager.processDeliveryClose(player, contents);
            }, 1L);
            return;
        }

        if (!orderManager.hasSession(player.getUniqueId())) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof OrderHolder)
                    && !(player.getOpenInventory().getTopInventory().getHolder() instanceof OrderDeliveryHolder)) {
                OrderSession session = orderManager.getSession(player.getUniqueId());
                if (session != null && session.awaitingSearch) return;
                orderManager.removeSession(player);
            }
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        orderManager.removeSession(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!orderManager.isAwaitingSearch(player.getUniqueId())) return;

        event.setCancelled(true);
        String query = event.getMessage().trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            orderManager.openCreateSearch(player, query);
        });
    }
}
