package com.starlightuniverse.chestshop;

import com.starlightuniverse.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public class ChestShopListener implements Listener {

    private final JavaPlugin plugin;
    private final ChestShopManager manager;

    public ChestShopListener(JavaPlugin plugin, ChestShopManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK && ChestShopManager.isChest(block.getType())) {
            if (manager.hasPendingMenu(player.getUniqueId())) {
                event.setCancelled(true);
                manager.handleMenuClick(player, block);
                return;
            }
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        if (ChestShopManager.isChest(block.getType())) {
            if (manager.hasPendingCreation(player.getUniqueId())) {
                event.setCancelled(true);
                manager.handleChestClick(player, block);
                return;
            }

            if (manager.hasPendingItemChange(player.getUniqueId())) {
                event.setCancelled(true);
                manager.handleItemChange(player, block);
                return;
            }

            ChestShop shop = manager.getShopAt(block);
            if (shop != null && !shop.getOwnerUsername().equals(player.getName().toLowerCase())) {
                event.setCancelled(true);
            }
            return;
        }

        if (ChestShopManager.isWallSign(block.getType())) {
            ChestShop shop = manager.getShopBySignBlock(block);
            if (shop != null) {
                event.setCancelled(true);
                manager.startTransaction(player, shop);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (ChestShopManager.isChest(block.getType()) && manager.isShopChest(block)) {
            Msg.error(event.getPlayer(), "You cannot break a shop chest! Remove the shop first.");
            event.setCancelled(true);
            return;
        }

        if (ChestShopManager.isWallSign(block.getType()) && manager.isShopSignBlock(block)) {
            Msg.error(event.getPlayer(), "You cannot break a shop sign! Remove the shop first.");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getLocation() == null) return;

        Block block = event.getInventory().getLocation().getBlock();
        if (!ChestShopManager.isChest(block.getType())) return;

        ChestShop shop = manager.getShopAt(block);
        if (shop == null) return;

        if (!shop.getOwnerUsername().equals(player.getName().toLowerCase())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getLocation() == null) return;

        Block block = event.getInventory().getLocation().getBlock();
        if (!ChestShopManager.isChest(block.getType())) return;

        ChestShop shop = manager.getShopAt(block);
        if (shop == null) return;

        if (shop.getOwnerUsername().equals(player.getName().toLowerCase())) {
            Material itemMat;
            try {
                itemMat = Material.valueOf(shop.getItemType());
            } catch (IllegalArgumentException e) {
                return;
            }
            int stock = manager.countStock(block, itemMat);
            manager.updateSignStock(block, shop, stock);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ChestShopHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        switch (holder.getType()) {
            case MENU -> {
                ChestShop shop = manager.getShopById(holder.getShopId());
                if (shop == null) {
                    player.closeInventory();
                    return;
                }
                manager.handleMenuAction(player, event.getRawSlot(), shop);
            }
            case BANK -> manager.handleBankClick(player, event.getRawSlot(), holder);
            case FIND_ITEM -> {
                int slot = event.getRawSlot();
                if (slot == 48) manager.openFindItemGui(player, extractQuery(event), holder.getPage() - 1);
                else if (slot == 50) manager.openFindItemGui(player, extractQuery(event), holder.getPage() + 1);
            }
        }
    }

    private String extractQuery(InventoryClickEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.startsWith("Find: ")) {
            int dash = title.lastIndexOf(" — ");
            if (dash > 6) return title.substring(6, dash);
        }
        return "";
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (manager.hasPendingTransaction(player.getUniqueId())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                manager.handleTransactionInput(player, message);
            });
            return;
        }

        if (manager.hasPendingPriceChange(player.getUniqueId())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                manager.handlePriceInput(player, message);
            });
            return;
        }

        if (manager.hasPendingNameChange(player.getUniqueId())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                manager.handleNameInput(player, message);
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.cleanupPlayer(event.getPlayer().getUniqueId());
    }
}
