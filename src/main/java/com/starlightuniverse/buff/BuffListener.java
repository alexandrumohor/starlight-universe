package com.starlightuniverse.buff;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class BuffListener implements Listener {

    private static final int TREE_FELLER_MAX_BLOCKS = 256;

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

    private static final Set<Material> REPLANTABLE_CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART
    );

    private static final Map<Material, Material> SMELT_MAP = Map.ofEntries(
            Map.entry(Material.RAW_IRON, Material.IRON_INGOT),
            Map.entry(Material.RAW_GOLD, Material.GOLD_INGOT),
            Map.entry(Material.RAW_COPPER, Material.COPPER_INGOT),
            Map.entry(Material.COBBLESTONE, Material.STONE),
            Map.entry(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP),
            Map.entry(Material.SAND, Material.GLASS),
            Map.entry(Material.RED_SAND, Material.GLASS),
            Map.entry(Material.CLAY_BALL, Material.BRICK),
            Map.entry(Material.CACTUS, Material.GREEN_DYE),
            Map.entry(Material.KELP, Material.DRIED_KELP),
            Map.entry(Material.WET_SPONGE, Material.SPONGE)
    );

    private final JavaPlugin plugin;
    private final BuffManager manager;
    private final Set<UUID> treeFellerActive = new HashSet<>();

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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreakTreeFeller(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (treeFellerActive.contains(uuid)) return;
        if (!manager.hasTreeFeller(uuid)) return;
        if (!Tag.LOGS.isTagged(event.getBlock().getType())) return;

        treeFellerActive.add(uuid);
        try {
            fellTree(event.getBlock(), player);
        } finally {
            treeFellerActive.remove(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material mat = block.getType();

        if (manager.hasReCropper(player.getUniqueId()) && REPLANTABLE_CROPS.contains(mat)) {
            if (block.getBlockData() instanceof Ageable ageable && ageable.getAge() == ageable.getMaximumAge()) {
                Material cropType = mat;
                Location loc = block.getLocation();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Block b = loc.getBlock();
                    if (b.getType() == Material.AIR) {
                        b.setType(cropType);
                        if (b.getBlockData() instanceof Ageable newAgeable) {
                            newAgeable.setAge(0);
                            b.setBlockData(newAgeable);
                        }
                    }
                });
            }
        }

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
    public void onBlockDropItem(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (!manager.hasSmelter(player.getUniqueId())) return;

        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            Material smelted = SMELT_MAP.get(stack.getType());
            if (smelted != null) {
                stack.setType(smelted);
                item.setItemStack(stack);
            }
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

    private void fellTree(Block origin, Player player) {
        Set<Block> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty() && visited.size() < TREE_FELLER_MAX_BLOCKS) {
            Block current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block neighbor = current.getRelative(dx, dy, dz);
                        if (visited.contains(neighbor)) continue;
                        if (Tag.LOGS.isTagged(neighbor.getType())) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }

        visited.remove(origin);
        ItemStack tool = player.getInventory().getItemInMainHand();
        Location dropLoc = origin.getLocation().add(0.5, 0.5, 0.5);
        for (Block block : visited) {
            for (ItemStack drop : block.getDrops(tool, player)) {
                origin.getWorld().dropItemNaturally(dropLoc, drop);
            }
            block.setType(Material.AIR);
        }
    }
}
