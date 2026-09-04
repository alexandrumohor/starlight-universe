package com.starlightuniverse.voucher;

import com.starlightuniverse.booster.BoosterManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class VoucherListener implements Listener {

    private final VoucherManager manager;
    private final BoosterManager boosterManager;

    public VoucherListener(VoucherManager manager, BoosterManager boosterManager) {
        this.manager = manager;
        this.boosterManager = boosterManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        String type = manager.getVoucherType(item);
        if (type == null) return;

        event.setCancelled(true);
        manager.handleRightClick(event.getPlayer(), item);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boosterManager.loadPlayer(player.getUniqueId(), player.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.onPlayerQuit(event.getPlayer().getUniqueId());
        boosterManager.onPlayerQuit(event.getPlayer().getUniqueId());
    }
}
