package com.starlightuniverse.buff;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BuffListener implements Listener {

    private static final Set<Material> ORES = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS
    );

    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.MELON, Material.PUMPKIN,
            Material.SUGAR_CANE, Material.SWEET_BERRY_BUSH,
            Material.COCOA, Material.NETHER_WART,
            Material.CACTUS, Material.BAMBOO
    );

    private final JavaPlugin plugin;
    private final BuffManager manager;

    public BuffListener(JavaPlugin plugin, BuffManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (manager.hasGodMode(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material mat = block.getType();

        BuffType buffType = null;
        if (ORES.contains(mat)) {
            buffType = BuffType.ORE_LOOT_5X;
        } else if (Tag.LOGS.isTagged(mat)) {
            buffType = BuffType.TREE_LOOT_5X;
        } else if (CROPS.contains(mat)) {
            buffType = BuffType.CROP_LOOT_5X;
        } else {
            buffType = BuffType.BLOCK_LOOT_5X;
        }

        int multiplier = manager.getLootMultiplier(player.getUniqueId(), buffType);
        if (multiplier <= 1) return;

        int extra = multiplier - 1;
        for (ItemStack drop : block.getDrops(player.getInventory().getItemInMainHand(), player)) {
            if (drop.getType() == Material.AIR) continue;
            ItemStack bonus = drop.clone();
            bonus.setAmount(drop.getAmount() * extra);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), bonus);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        int multiplier = manager.getLootMultiplier(killer.getUniqueId(), BuffType.MOB_LOOT_5X);
        if (multiplier <= 1) return;

        int extra = multiplier - 1;
        List<ItemStack> bonusDrops = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) {
            if (drop == null || drop.getType() == Material.AIR) continue;
            ItemStack bonus = drop.clone();
            bonus.setAmount(drop.getAmount() * extra);
            bonusDrops.add(bonus);
        }
        for (ItemStack bonus : bonusDrops) {
            event.getEntity().getWorld().dropItemNaturally(
                    event.getEntity().getLocation(), bonus);
        }

        event.setDroppedExp(event.getDroppedExp() * multiplier);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!(event.getCaught() instanceof Item caughtItem)) return;

        Player player = event.getPlayer();
        int multiplier = manager.getLootMultiplier(player.getUniqueId(), BuffType.FISH_LOOT_5X);
        if (multiplier <= 1) return;

        int extra = multiplier - 1;
        ItemStack stack = caughtItem.getItemStack();
        if (stack.getType() == Material.AIR) return;
        ItemStack bonus = stack.clone();
        bonus.setAmount(stack.getAmount() * extra);
        player.getWorld().dropItemNaturally(player.getLocation(), bonus);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        manager.loadBuffs(player.getUniqueId(), player.getName());

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                manager.onPlayerJoin(player);
                if (manager.hasExtraChunks(player.getUniqueId())) {
                    player.setViewDistance(player.getViewDistance() + 10);
                }
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (manager.hasFlyBuff(player.getUniqueId())) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
        if (manager.hasExtraChunks(player.getUniqueId())) {
            player.setViewDistance(player.getViewDistance() - 10);
        }
        manager.onPlayerQuit(player.getUniqueId());
    }
}
