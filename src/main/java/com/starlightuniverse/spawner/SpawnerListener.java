package com.starlightuniverse.spawner;

import com.starlightuniverse.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Iterator;

public class SpawnerListener implements Listener {

    private final SpawnerManager spawnerManager;

    public SpawnerListener(SpawnerManager spawnerManager) {
        this.spawnerManager = spawnerManager;
    }

    // ── Right-click virtual spawner block → open GUI ──
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.SPAWNER) return;

        VirtualSpawner spawner = spawnerManager.getSpawnerAt(block.getLocation());
        if (spawner == null) return;

        // If holding a matching spawner item → stack instead of opening GUI
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        VirtualSpawnerType handType = spawnerManager.getSpawnerItemType(hand);
        if (handType != null) {
            event.setCancelled(true);
            int handStack = spawnerManager.getSpawnerItemStack(hand);
            if (spawnerManager.tryStackOnto(player, block.getLocation(), handType, handStack)) {
                hand.setAmount(hand.getAmount() - 1);
                return;
            }
        }

        event.setCancelled(true);
        if (!spawnerManager.canManage(player, spawner)) {
            Msg.error(player, "This spawner belongs to " + spawner.getOwnerUsername() + "!");
            return;
        }
        spawnerManager.openManageGui(player, spawner);
    }

    // ── Place virtual spawner item ──
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        VirtualSpawnerType type = spawnerManager.getSpawnerItemType(item);
        if (type == null) return;

        Player player = event.getPlayer();
        Location loc = event.getBlockPlaced().getLocation();

        // Prevent overlapping a virtual spawner
        if (spawnerManager.getSpawnerAt(loc) != null) {
            event.setCancelled(true);
            Msg.error(player, "There is already a virtual spawner at that location!");
            return;
        }

        int tier = spawnerManager.getSpawnerItemTier(item);
        int stack = spawnerManager.getSpawnerItemStack(item);
        spawnerManager.placeSpawner(player, loc, type, tier, stack);
    }

    // ── Break virtual spawner block ──
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;
        VirtualSpawner spawner = spawnerManager.getSpawnerAt(block.getLocation());
        if (spawner == null) return;

        Player player = event.getPlayer();

        // Prevent non-owner from breaking (unless op)
        if (!spawnerManager.canManage(player, spawner) && !player.isOp()) {
            event.setCancelled(true);
            Msg.error(player, "This spawner belongs to " + spawner.getOwnerUsername() + "!");
            return;
        }

        event.setDropItems(false);
        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean silk = SpawnerManager.hasSilkTouch(tool);
        if (silk) {
            ItemStack dropped = spawnerManager.createSpawnerItem(
                    spawner.getType(), spawner.getTier(), spawner.getStackCount());
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), dropped);
            player.playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 0.6f, 1.2f);
            if (!spawner.getStorage().isEmpty() || spawner.getStoredXp() > 0) {
                Msg.info(player, "Spawner broken. Stored contents were lost — collect them first next time!");
            }
        } else {
            Msg.error(player, "You need a Silk Touch pickaxe to keep the spawner!");
            player.playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
        }

        spawnerManager.removeSpawner(spawner);
    }

    // ── Protect from explosions ──
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeSpawnerBlocks(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeSpawnerBlocks(event.blockList().iterator());
    }

    private void removeSpawnerBlocks(Iterator<Block> it) {
        while (it.hasNext()) {
            Block b = it.next();
            if (b.getType() == Material.SPAWNER && spawnerManager.getSpawnerAt(b.getLocation()) != null) {
                it.remove();
            }
        }
    }

    // ── GUI click handling ──
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpawnerHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (holder.getType() == SpawnerHolder.Type.SHOP) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getItemMeta() == null) return;
            String typeName = clicked.getItemMeta().getPersistentDataContainer()
                    .get(SpawnerManager.SPAWNER_TYPE_KEY, PersistentDataType.STRING);
            VirtualSpawnerType type = VirtualSpawnerType.fromName(typeName);
            if (type == null) return;
            spawnerManager.buyFromShop(player, type);
            return;
        }

        if (holder.getType() == SpawnerHolder.Type.MANAGE) {
            VirtualSpawner spawner = spawnerManager.getSpawnerById(holder.getSpawnerId());
            if (spawner == null) {
                player.closeInventory();
                return;
            }
            switch (slot) {
                case 45 -> spawnerManager.takeAll(player, spawner);
                case 47 -> spawnerManager.sellAll(player, spawner);
                case 49 -> spawnerManager.upgradeTier(player, spawner);
                case 51 -> spawnerManager.collectXp(player, spawner);
                case 53 -> player.closeInventory();
            }
        }
    }
}
