package com.starlightuniverse.crate;

import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class CrateListener implements Listener {

    private static final TextColor WHITE = TextColor.color(0xFFFFFF);

    private final CrateManager crateManager;

    public CrateListener(CrateManager crateManager) {
        this.crateManager = crateManager;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Location loc = block.getLocation();
        CrateType crateType = crateManager.getCrateType(loc);
        if (crateType == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            crateManager.openPreview(player, crateType);
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            CrateType keyType = crateManager.getKeyType(hand);

            if (keyType == null) {
                Msg.info(player, "You need a " + crateType.getDisplayName() + " Key to open this crate!");
                Msg.gray(player, "Left-click to preview rewards.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }

            if (keyType != crateType) {
                Msg.error(player, "This key doesn't fit this crate! You need a " + crateType.getDisplayName() + " Key.");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }

            if (hand.getAmount() > 1) {
                hand.setAmount(hand.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            CrateReward reward = crateManager.rollReward(crateType);
            crateManager.giveReward(player, reward, crateType);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (crateManager.isCrateLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
            Msg.error(event.getPlayer(), "You cannot break a crate! Use /removecrate as admin.");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> crateManager.isCrateLocation(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> crateManager.isCrateLocation(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CrateHolder) {
            event.setCancelled(true);
        }
    }
}
