package com.starlightuniverse.premium;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class PremiumListener implements Listener {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);

    private final JavaPlugin plugin;
    private final PremiumManager premiumManager;
    private final AdminManager adminManager;
    private final EconomyManager economy;

    private final Map<UUID, ItemStack[]> deathArmor = new HashMap<>();
    private final Map<UUID, List<ItemStack>> deathInventory = new HashMap<>();
    private final Map<UUID, Integer> deathXp = new HashMap<>();

    public PremiumListener(JavaPlugin plugin, PremiumManager premiumManager, AdminManager adminManager, EconomyManager economy) {
        this.plugin = plugin;
        this.premiumManager = premiumManager;
        this.adminManager = adminManager;
        this.economy = economy;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        PremiumRank rank = premiumManager.getPlayerRank(uuid);

        premiumManager.setLastDeath(uuid, player.getLocation());

        if (rank == PremiumRank.NONE) return;

        int keepXp = rank.getKeepXpPercent();
        if (keepXp > 0) {
            int totalXp = player.getTotalExperience();
            int kept = (int) (totalXp * keepXp / 100.0);
            deathXp.put(uuid, kept);
            event.setDroppedExp(event.getDroppedExp() * (100 - keepXp) / 100);
        }

        int armorPercent = rank.getKeepArmorPercent();
        if (armorPercent >= 100) {
            ItemStack[] armor = player.getInventory().getArmorContents();
            ItemStack[] saved = new ItemStack[armor.length];
            for (int i = 0; i < armor.length; i++) {
                if (armor[i] != null && armor[i].getType() != Material.AIR) {
                    saved[i] = armor[i].clone();
                    event.getDrops().remove(armor[i]);
                }
            }
            deathArmor.put(uuid, saved);
        }

        int invPercent = rank.getKeepInventoryPercent();
        if (invPercent > 0) {
            List<ItemStack> drops = event.getDrops();
            if (invPercent >= 100) {
                List<ItemStack> saved = new ArrayList<>();
                for (ItemStack item : drops) {
                    if (item != null) saved.add(item.clone());
                }
                deathInventory.put(uuid, saved);
                drops.clear();
            } else {
                int keepCount = (int) Math.ceil(drops.size() * invPercent / 100.0);
                List<ItemStack> saved = new ArrayList<>();
                for (int i = 0; i < keepCount && i < drops.size(); i++) {
                    saved.add(drops.get(i).clone());
                }
                deathInventory.put(uuid, saved);
                for (int i = 0; i < keepCount && !drops.isEmpty(); i++) {
                    drops.removeFirst();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            Integer xp = deathXp.remove(uuid);
            if (xp != null && xp > 0) {
                player.giveExp(xp);
            }

            ItemStack[] armor = deathArmor.remove(uuid);
            if (armor != null) {
                player.getInventory().setArmorContents(armor);
            }

            List<ItemStack> items = deathInventory.remove(uuid);
            if (items != null) {
                for (ItemStack item : items) {
                    HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                    overflow.values().forEach(i ->
                            player.getWorld().dropItemNaturally(player.getLocation(), i));
                }
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        if (event instanceof PlayerDeathEvent) return;
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        PremiumRank rank = premiumManager.getPlayerRank(killer.getUniqueId());
        int bonus = rank.getMobKillMoneyBonus();
        if (bonus > 0) {
            int baseReward = event.getDroppedExp();
            if (baseReward > 0) {
                int moneyBonus = Math.max(1, baseReward * bonus / 100);
                economy.addMoney(killer.getUniqueId(), moneyBonus);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onXpGain(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        double boost = premiumManager.getPlayerRank(player.getUniqueId()).getXpBoost();
        var boosterMgr = com.starlightuniverse.StarlightUniverse.getInstance().getBoosterManager();
        if (boosterMgr != null) {
            boost *= boosterMgr.getMultiplier(player.getUniqueId(),
                    com.starlightuniverse.booster.BoosterType.XP_VANILLA);
        }
        if (boost > 1.0) {
            event.setAmount((int) Math.ceil(event.getAmount() * boost));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PremiumRank rank = premiumManager.getPlayerRank(player.getUniqueId());
        if (!rank.hasAutoPickup()) return;

        Item item = event.getItem();
        ItemStack stack = item.getItemStack();
        if (stack.getType() == Material.AIR) return;

        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(stack.clone());
        if (overflow.isEmpty()) {
            item.setItemStack(new ItemStack(Material.AIR));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            premiumManager.checkTrialExpiry(player);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                premiumManager.checkDailyBonus(player);
                premiumManager.checkMonthlyStars(player);
            }, 40L);
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        premiumManager.standUp(player);
        premiumManager.disableFly(player);
        premiumManager.unloadPlayer(uuid);
        deathArmor.remove(uuid);
        deathInventory.remove(uuid);
        deathXp.remove(uuid);
    }

    // ==================== GUI CLICK HANDLING ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof PremiumHolder holder)) return;

        switch (holder.getType()) {
            case TRASH -> {}
            case RANK_OVERVIEW -> {
                event.setCancelled(true);
                handleRankOverviewClick(player, event.getSlot());
            }
            case RANK_BUY -> {
                event.setCancelled(true);
                handleBuyClick(player, holder, event.getSlot());
            }
            case TRAIL_SELECT -> {
                event.setCancelled(true);
                handleTrailClick(player, event.getSlot());
            }
            case HEAD_DATABASE -> {
                event.setCancelled(true);
                handleHeadClick(player, event);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getHolder() instanceof PremiumHolder holder) {
            if (holder.getType() == PremiumHolder.Type.TRASH) {
                event.getInventory().clear();
                Msg.info(player, "Trash disposed!");
            }
        }
    }

    private void handleRankOverviewClick(Player player, int slot) {
        int currentLevel = adminManager.getPremiumLevel(player.getUniqueId());
        int[] slots = {2, 4, 6, 10, 14};
        int[] levels = {1, 2, 3, 4, 5};

        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) {
                PremiumRank rank = PremiumRank.fromLevel(levels[i]);
                if (currentLevel >= rank.getLevel()) {
                    Msg.info(player, "You already own this rank!");
                } else {
                    premiumManager.openBuyGui(player, rank);
                }
                return;
            }
        }
    }

    private void handleBuyClick(Player player, PremiumHolder holder, int slot) {
        PremiumRank rank = PremiumRank.fromLevel(holder.getSelectedRank());
        if (rank == PremiumRank.NONE) return;

        switch (slot) {
            case 2 -> {
                player.closeInventory();
                premiumManager.buyRank(player, rank, "stars");
            }
            case 6 -> {
                player.closeInventory();
                premiumManager.buyRank(player, rank, "gems");
            }
            case 4 -> player.closeInventory();
        }
    }

    private void handleTrailClick(Player player, int slot) {
        UUID uuid = player.getUniqueId();
        PremiumRank rank = premiumManager.getPlayerRank(uuid);

        if (slot == 22) {
            premiumManager.setTrail(uuid, null);
            Msg.success(player, "Trail disabled.");
            player.closeInventory();
            return;
        }

        if (slot >= 0 && slot < PremiumManager.TRAIL_TYPES.length) {
            String trail = PremiumManager.TRAIL_TYPES[slot];
            String current = premiumManager.getActiveTrail(uuid);

            if (trail.equals(current)) {
                premiumManager.setTrail(uuid, null);
                Msg.success(player, "Trail disabled.");
            } else {
                premiumManager.setTrail(uuid, trail);
                Msg.success(player, "Trail set to " + trail.charAt(0) + trail.substring(1).toLowerCase() + "!");
            }
            player.closeInventory();
        }
    }

    private void handleHeadClick(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;

        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(clicked.clone());
        overflow.values().forEach(i ->
                player.getWorld().dropItemNaturally(player.getLocation(), i));
        Msg.success(player, "Head added to inventory!");
    }
}
