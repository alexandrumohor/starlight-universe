package com.starlightuniverse.enchant;

import com.starlightuniverse.util.Msg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class EnchantGuiListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof EnchantListHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        EnchantManager manager = holder.getManager();
        if (!manager.isEnchantBook(clicked)) {
            int slot = event.getSlot();
            CustomEnchant[] values = CustomEnchant.values();
            if (slot >= 0 && slot < values.length) {
                CustomEnchant enchant = values[slot];
                ItemStack book = manager.createBook(enchant, enchant.getMaxLevel());
                var remaining = player.getInventory().addItem(book);
                for (ItemStack leftover : remaining.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
                Msg.success(player, "Received " + enchant.getDisplayName() + " " + EnchantManager.toRoman(enchant.getMaxLevel()) + "!");
            }
        }
    }
}
