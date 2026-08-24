package com.starlightuniverse.antigrief;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class AntiGriefListener implements Listener {

    private static final int LEAF_DECAY_RADIUS = 6;
    private static final int LEAF_LOG_DISTANCE = 6;
    private static final long LEAF_DECAY_DELAY_TICKS = 20L;

    private final JavaPlugin plugin;

    public AntiGriefListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEndermanPickup(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Enderman) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreeperExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof Creeper) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLogBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();
        if (!isLog(type)) return;
        Block base = event.getBlock();
        Bukkit.getScheduler().runTaskLater(plugin, () -> scheduleFastDecay(base), LEAF_DECAY_DELAY_TICKS);
    }

    private void scheduleFastDecay(Block source) {
        Set<Block> checked = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        for (int dx = -LEAF_DECAY_RADIUS; dx <= LEAF_DECAY_RADIUS; dx++) {
            for (int dy = -LEAF_DECAY_RADIUS; dy <= LEAF_DECAY_RADIUS; dy++) {
                for (int dz = -LEAF_DECAY_RADIUS; dz <= LEAF_DECAY_RADIUS; dz++) {
                    Block b = source.getRelative(dx, dy, dz);
                    if (isDecayableLeaf(b)) queue.add(b);
                }
            }
        }
        int delay = 2;
        for (Block leaf : queue) {
            if (checked.contains(leaf)) continue;
            checked.add(leaf);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isDecayableLeaf(leaf)) return;
                if (hasNearbyLog(leaf, LEAF_LOG_DISTANCE)) return;
                leaf.breakNaturally();
            }, delay);
            delay += 2;
        }
    }

    private boolean isLog(Material m) {
        return m.name().endsWith("_LOG") || m.name().endsWith("_WOOD") ||
                m.name().endsWith("_STEM") || m.name().endsWith("_HYPHAE");
    }

    private boolean isLeaf(Material m) {
        return m.name().endsWith("_LEAVES");
    }

    private boolean isDecayableLeaf(Block block) {
        if (!isLeaf(block.getType())) return false;
        org.bukkit.block.data.BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Leaves leaves) {
            return !leaves.isPersistent();
        }
        return false;
    }

    private boolean hasNearbyLog(Block leaf, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (isLog(leaf.getRelative(dx, dy, dz).getType())) return true;
                }
            }
        }
        return false;
    }
}
