package com.starlightuniverse.job;

import com.starlightuniverse.auth.AuthManager;
import com.starlightuniverse.world.WorldManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;

public class JobListener implements Listener {

    private static final Set<Material> FISH_ITEMS = Set.of(
            Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH
    );
    private static final Set<Material> TREASURE_ITEMS = Set.of(
            Material.BOW, Material.ENCHANTED_BOOK, Material.FISHING_ROD,
            Material.NAME_TAG, Material.SADDLE, Material.NAUTILUS_SHELL
    );

    private final JavaPlugin plugin;
    private final JobManager jobManager;
    private final AuthManager authManager;

    public JobListener(JavaPlugin plugin, JobManager jobManager, AuthManager authManager) {
        this.plugin = plugin;
        this.jobManager = jobManager;
        this.authManager = authManager;
    }

    private boolean canReward(Player player) {
        if (!authManager.isAuthenticated(player.getUniqueId())) return false;
        return WorldManager.getWorldGroup(player.getWorld()) == WorldManager.WorldGroup.SURVIVAL;
    }

    // --- MINER / WOODCUTTER / DIGGER / FARMER (block break) ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!canReward(player)) return;

        Block block = event.getBlock();
        Material type = block.getType();

        JobManager.JobReward cropReward = JobManager.getCropReward(type);
        if (cropReward != null) {
            if (block.getBlockData() instanceof Ageable ageable) {
                if (ageable.getAge() >= ageable.getMaximumAge()) {
                    if (!jobManager.isPlacedBlock(block)) {
                        jobManager.addReward(player, cropReward.job(), cropReward.money(), cropReward.xp());
                    }
                }
            }
            return;
        }

        JobManager.JobReward breakReward = JobManager.getBreakReward(type);
        if (breakReward != null) {
            if (breakReward.job() == JobType.BUILDER) return;
            if (!jobManager.isPlacedBlock(block)) {
                jobManager.addReward(player, breakReward.job(), breakReward.money(), breakReward.xp());
            }
        }
    }

    // --- BUILDER (block place) ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!canReward(player)) return;

        jobManager.trackPlacedBlock(event.getBlock());
        jobManager.addReward(player, JobType.BUILDER, 0.5, 1);
    }

    // --- HUNTER (mob kill) ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;
        if (!canReward(killer)) return;

        JobManager.JobReward reward = JobManager.getKillReward(entity.getType());
        if (reward != null) {
            jobManager.addReward(killer, reward.job(), reward.money(), reward.xp());
        } else if (isHostile(entity)) {
            jobManager.addReward(killer, JobType.HUNTER, 2, 3);
        }
    }

    private boolean isHostile(Entity entity) {
        return entity instanceof org.bukkit.entity.Monster
                || entity instanceof org.bukkit.entity.Slime
                || entity instanceof org.bukkit.entity.Phantom
                || entity instanceof org.bukkit.entity.Ghast;
    }

    // --- FISHERMAN ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        if (!canReward(player)) return;

        Entity caught = event.getCaught();
        if (caught instanceof org.bukkit.entity.Item itemEntity) {
            ItemStack item = itemEntity.getItemStack();
            Material mat = item.getType();

            if (mat == Material.COD) {
                jobManager.addReward(player, JobType.FISHERMAN, 3, 5);
            } else if (mat == Material.SALMON) {
                jobManager.addReward(player, JobType.FISHERMAN, 5, 8);
            } else if (mat == Material.TROPICAL_FISH) {
                jobManager.addReward(player, JobType.FISHERMAN, 8, 15);
            } else if (mat == Material.PUFFERFISH) {
                jobManager.addReward(player, JobType.FISHERMAN, 10, 20);
            } else if (TREASURE_ITEMS.contains(mat)) {
                jobManager.addReward(player, JobType.FISHERMAN, 15, 30);
            } else {
                jobManager.addReward(player, JobType.FISHERMAN, 1, 2);
            }
        }
    }

    // --- BREWER ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory() instanceof BrewerInventory brew)) return;
        if (brew.getHolder() instanceof BrewingStand stand) {
            jobManager.trackBrewingStand(stand.getBlock(), player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Block block = event.getBlock();
        UUID uuid = jobManager.getBrewingUser(block);
        if (uuid == null) return;

        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) return;
        if (!canReward(player)) return;

        int count = 0;
        for (ItemStack item : event.getResults()) {
            if (item != null && item.getType() != Material.AIR) count++;
        }
        if (count > 0) {
            jobManager.addReward(player, JobType.BREWER, 10.0 * count, 15 * count);
        }
    }

    // --- ENCHANTER ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (!canReward(player)) return;
        jobManager.addReward(player, JobType.ENCHANTER, 20, 30);
    }

    // --- SMELTER ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        if (!canReward(player)) return;
        int amount = event.getItemAmount();
        jobManager.addReward(player, JobType.SMELTER, 2.0 * amount, 3 * amount);
    }
}
