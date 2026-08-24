package com.starlightuniverse.crate;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class CrateManager {

    private static final TextColor COMMON_COLOR = TextColor.color(0xAAAAAA);
    private static final TextColor UNCOMMON_COLOR = TextColor.color(0x55FF55);
    private static final TextColor RARE_COLOR = TextColor.color(0x5555FF);
    private static final TextColor EPIC_COLOR = TextColor.color(0xAA00AA);
    private static final TextColor LEGENDARY_COLOR = TextColor.color(0xFFD700);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);

    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0");

    private static final NamespacedKey CRATE_KEY_TAG = NamespacedKey.fromString("starlightuniverse:crate_key_type");
    private static final NamespacedKey HOLOGRAM_TAG = NamespacedKey.fromString("starlightuniverse:crate_hologram");

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;
    private final AdminManager adminManager;

    private final Map<String, CrateType> crateLocations = new ConcurrentHashMap<>();
    private final Map<CrateType, List<CrateReward>> rewardTables = new EnumMap<>(CrateType.class);
    private final Map<CrateType, Double> totalWeights = new EnumMap<>(CrateType.class);

    private BukkitTask particleTask;

    public CrateManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy, AdminManager adminManager) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
        this.adminManager = adminManager;
    }

    public void initialize() {
        buildRewardTables();
        loadCrates();
        startParticleTask();
    }

    public void shutdown() {
        if (particleTask != null) particleTask.cancel();
        removeAllHolograms();
    }

    // ── Location key ──

    private static String locKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private static String locKey(Location loc) {
        return locKey(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    // ── Crate location management ──

    public boolean isCrateLocation(Location loc) {
        return crateLocations.containsKey(locKey(loc));
    }

    public CrateType getCrateType(Location loc) {
        return crateLocations.get(locKey(loc));
    }

    public void spawnCrate(Location loc, CrateType type) {
        String key = locKey(loc);
        crateLocations.put(key, type);

        Block block = loc.getBlock();
        block.setType(type.getShulkerMaterial());

        spawnHologram(loc, type);
        saveCrate(loc, type);
    }

    public void removeCrate(Location loc) {
        String key = locKey(loc);
        crateLocations.remove(key);

        Block block = loc.getBlock();
        block.setType(Material.AIR);

        removeHologram(loc);
        deleteCrate(loc);
    }

    public Map<String, CrateType> getCrateLocations() {
        return Collections.unmodifiableMap(crateLocations);
    }

    // ── Holograms ──

    private void spawnHologram(Location loc, CrateType type) {
        removeHologram(loc);
        Location holoLoc = loc.clone().add(0.5, 1.5, 0.5);
        loc.getWorld().spawn(holoLoc, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setCustomNameVisible(true);
            stand.customName(Component.text(type.getDisplayName(), type.getColor()).decoration(TextDecoration.BOLD, true));
            stand.addScoreboardTag("su_crate_hologram");
            stand.addScoreboardTag("su_crate_" + locKey(loc));
            stand.setPersistent(true);
        });
    }

    private void removeHologram(Location loc) {
        String tag = "su_crate_" + locKey(loc);
        for (Entity entity : loc.getWorld().getEntitiesByClass(ArmorStand.class)) {
            if (entity.getScoreboardTags().contains(tag)) {
                entity.remove();
            }
        }
    }

    private void removeAllHolograms() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (entity.getScoreboardTags().contains("su_crate_hologram")) {
                    entity.remove();
                }
            }
        }
    }

    private void respawnAllHolograms() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            removeAllHolograms();
            for (Map.Entry<String, CrateType> entry : crateLocations.entrySet()) {
                String[] parts = entry.getKey().split(":");
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;
                Location loc = new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                spawnHologram(loc, entry.getValue());
            }
        });
    }

    // ── Particles ──

    private void startParticleTask() {
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<String, CrateType> entry : crateLocations.entrySet()) {
                String[] parts = entry.getKey().split(":");
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;
                double x = Integer.parseInt(parts[1]) + 0.5;
                double y = Integer.parseInt(parts[2]) + 1.0;
                double z = Integer.parseInt(parts[3]) + 0.5;

                if (world.getNearbyPlayers(new Location(world, x, y, z), 32).isEmpty()) continue;

                world.spawnParticle(Particle.SMOKE, x, y + 0.5, z, 3, 0.2, 0.3, 0.2, 0.01);
                world.spawnParticle(entry.getValue().getParticle(), x, y + 0.3, z, 2, 0.3, 0.2, 0.3, 0.01);
            }
        }, 20L, 15L);
    }

    // ── Key items ──

    public ItemStack createKey(CrateType type, int amount) {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, amount);
        ItemMeta meta = key.getItemMeta();
        meta.displayName(Component.text(type.getDisplayName() + " Key", type.getColor())
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Right-click a " + type.getDisplayName(), GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("to claim a random reward!", GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Rarity: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(type.getDisplayName(), type.getColor())));
        meta.lore(lore);

        meta.setEnchantmentGlintOverride(true);

        meta.getPersistentDataContainer().set(CRATE_KEY_TAG,
                org.bukkit.persistence.PersistentDataType.STRING, type.name());

        key.setItemMeta(meta);
        return key;
    }

    public boolean isKey(ItemStack item) {
        if (item == null || item.getType() != Material.TRIPWIRE_HOOK) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(CRATE_KEY_TAG);
    }

    public CrateType getKeyType(ItemStack item) {
        if (item == null || item.getType() != Material.TRIPWIRE_HOOK) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String value = meta.getPersistentDataContainer().get(CRATE_KEY_TAG,
                org.bukkit.persistence.PersistentDataType.STRING);
        return value != null ? CrateType.fromName(value) : null;
    }

    // ── Reward rolling ──

    public CrateReward rollReward(CrateType type) {
        List<CrateReward> rewards = rewardTables.get(type);
        double total = totalWeights.get(type);
        double roll = ThreadLocalRandom.current().nextDouble(total);

        double cumulative = 0;
        for (CrateReward reward : rewards) {
            cumulative += reward.weight();
            if (roll < cumulative) return reward;
        }
        return rewards.getLast();
    }

    public void giveReward(Player player, CrateReward reward, CrateType crateType) {
        reward.giveAction().accept(player);

        TextColor rarityColor = reward.rarityColor();
        player.sendMessage(Component.empty());
        player.sendMessage(Msg.prefix()
                .append(Component.text("You opened a ", WHITE))
                .append(Component.text(crateType.getDisplayName(), crateType.getColor()).decoration(TextDecoration.BOLD, true))
                .append(Component.text("!", WHITE)));
        player.sendMessage(Msg.prefix()
                .append(Component.text("Reward: ", GRAY))
                .append(Component.text(reward.name(), rarityColor).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" [", GRAY))
                .append(Component.text(reward.rarity(), rarityColor))
                .append(Component.text("]", GRAY)));
        player.sendMessage(Component.empty());

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

        if (reward.rarity().equals("Legendary")) {
            Component broadcast = Msg.prefix()
                    .append(Component.text(player.getName(), LEGENDARY_COLOR).decoration(TextDecoration.BOLD, true))
                    .append(Component.text(" got ", WHITE))
                    .append(Component.text(reward.name(), LEGENDARY_COLOR).decoration(TextDecoration.BOLD, true))
                    .append(Component.text(" from a ", WHITE))
                    .append(Component.text(crateType.getDisplayName(), crateType.getColor()).decoration(TextDecoration.BOLD, true))
                    .append(Component.text("!", WHITE));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.sendMessage(broadcast);
                    online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f);
                }
            }
        }
    }

    // ── Preview GUI ──

    public void openPreview(Player player, CrateType type) {
        List<CrateReward> rewards = rewardTables.get(type);
        double total = totalWeights.get(type);

        int rows = Math.min(6, (int) Math.ceil((double) rewards.size() / 7) + 1);
        int size = rows * 9;

        CrateHolder holder = new CrateHolder(type);
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text(type.getDisplayName() + " - Preview", type.getColor()));
        holder.setInventory(inv);

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);
        for (int i = 0; i < size; i++) inv.setItem(i, border);

        int slot = 10;
        for (CrateReward reward : rewards) {
            if (slot >= size) break;
            int col = slot % 9;
            if (col == 0) { slot++; continue; }
            if (col == 8) { slot += 2; continue; }

            double chance = (reward.weight() / total) * 100.0;
            ItemStack display = new ItemStack(reward.displayMaterial(), Math.max(1, Math.min(64, reward.displayAmount())));
            ItemMeta meta = display.getItemMeta();
            meta.displayName(Component.text(reward.name(), reward.rarityColor())
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Rarity: ", GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(reward.rarity(), reward.rarityColor())));
            lore.add(Component.text("Chance: ", GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(PERCENT_FORMAT.format(chance) + "%", YELLOW)));
            meta.lore(lore);

            display.setItemMeta(meta);
            inv.setItem(slot, display);
            slot++;
        }

        player.openInventory(inv);
    }

    // ── Item giving helper ──

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    // ── Reward table builders ──

    private void buildRewardTables() {
        rewardTables.put(CrateType.STAR, buildStarRewards());
        rewardTables.put(CrateType.COSMIC, buildCosmicRewards());
        rewardTables.put(CrateType.GALAXY, buildGalaxyRewards());
        rewardTables.put(CrateType.SEASONAL, buildSeasonalRewards());

        for (CrateType type : CrateType.values()) {
            double total = 0;
            for (CrateReward r : rewardTables.get(type)) total += r.weight();
            totalWeights.put(type, total);
        }
    }

    private List<CrateReward> buildStarRewards() {
        List<CrateReward> r = new ArrayList<>();
        r.add(money("$5,000", 5_000, "Common", COMMON_COLOR, 5.5));
        r.add(money("$10,000", 10_000, "Common", COMMON_COLOR, 5.5));
        r.add(money("$25,000", 25_000, "Uncommon", UNCOMMON_COLOR, 5.0));
        r.add(money("$50,000", 50_000, "Uncommon", UNCOMMON_COLOR, 4.0));
        r.add(money("$100,000", 100_000, "Rare", RARE_COLOR, 2.0));
        r.add(gems(EconomyManager.GEMS_ICON + "10 Gems", 10, "Common", COMMON_COLOR, 5.5));
        r.add(gems(EconomyManager.GEMS_ICON + "25 Gems", 25, "Uncommon", UNCOMMON_COLOR, 5.0));
        r.add(gems(EconomyManager.GEMS_ICON + "50 Gems", 50, "Rare", RARE_COLOR, 3.5));
        r.add(item("8x Diamond", new ItemStack(Material.DIAMOND, 8), "Common", COMMON_COLOR, 5.5));
        r.add(item("16x Diamond", new ItemStack(Material.DIAMOND, 16), "Uncommon", UNCOMMON_COLOR, 4.5));
        r.add(item("32x Diamond", new ItemStack(Material.DIAMOND, 32), "Rare", RARE_COLOR, 2.5));
        r.add(item("16x Iron Block", new ItemStack(Material.IRON_BLOCK, 16), "Common", COMMON_COLOR, 5.5));
        r.add(item("8x Gold Block", new ItemStack(Material.GOLD_BLOCK, 8), "Common", COMMON_COLOR, 5.0));
        r.add(item("1x Netherite Scrap", new ItemStack(Material.NETHERITE_SCRAP, 1), "Rare", RARE_COLOR, 3.0));
        r.add(item("4x Golden Apple", new ItemStack(Material.GOLDEN_APPLE, 4), "Uncommon", UNCOMMON_COLOR, 5.0));
        r.add(item("8x Ender Pearl", new ItemStack(Material.ENDER_PEARL, 8), "Common", COMMON_COLOR, 5.5));
        r.add(enchantBook("Stardust Enchant Book", enchants_stardust(), "Common", COMMON_COLOR, 5.5));
        r.add(enchantBook("Starlight Enchant Book", enchants_starlight(), "Uncommon", UNCOMMON_COLOR, 3.5));
        r.add(item("32x Exp Bottle", new ItemStack(Material.EXPERIENCE_BOTTLE, 32), "Common", COMMON_COLOR, 4.0));
        r.add(potion("Speed II Potion", PotionEffectType.SPEED, 1, 3600, "Common", COMMON_COLOR, 5.0));
        r.add(potion("Strength I Potion", PotionEffectType.STRENGTH, 0, 1800, "Common", COMMON_COLOR, 5.0));
        r.add(item("Diamond Sword", enchantedItem(Material.DIAMOND_SWORD, Map.of(Enchantment.SHARPNESS, 1)), "Uncommon", UNCOMMON_COLOR, 4.5));
        return r;
    }

    private List<CrateReward> buildCosmicRewards() {
        List<CrateReward> r = new ArrayList<>();
        r.add(money("$25,000", 25_000, "Common", COMMON_COLOR, 5.5));
        r.add(money("$50,000", 50_000, "Common", COMMON_COLOR, 5.5));
        r.add(money("$100,000", 100_000, "Uncommon", UNCOMMON_COLOR, 5.0));
        r.add(money("$250,000", 250_000, "Rare", RARE_COLOR, 3.0));
        r.add(money("$500,000", 500_000, "Epic", EPIC_COLOR, 1.0));
        r.add(gems(EconomyManager.GEMS_ICON + "25 Gems", 25, "Common", COMMON_COLOR, 5.5));
        r.add(gems(EconomyManager.GEMS_ICON + "50 Gems", 50, "Uncommon", UNCOMMON_COLOR, 5.0));
        r.add(gems(EconomyManager.GEMS_ICON + "100 Gems", 100, "Rare", RARE_COLOR, 3.5));
        r.add(stars(EconomyManager.STARS_ICON + "5 Stars", 5, "Rare", RARE_COLOR, 3.0));
        r.add(item("16x Diamond", new ItemStack(Material.DIAMOND, 16), "Common", COMMON_COLOR, 5.5));
        r.add(item("32x Diamond", new ItemStack(Material.DIAMOND, 32), "Uncommon", UNCOMMON_COLOR, 4.0));
        r.add(item("64x Diamond", new ItemStack(Material.DIAMOND, 64), "Rare", RARE_COLOR, 2.0));
        r.add(item("2x Netherite Scrap", new ItemStack(Material.NETHERITE_SCRAP, 2), "Uncommon", UNCOMMON_COLOR, 4.0));
        r.add(item("4x Netherite Scrap", new ItemStack(Material.NETHERITE_SCRAP, 4), "Rare", RARE_COLOR, 2.5));
        r.add(enchantBook("Starlight Enchant Book", enchants_starlight(), "Common", COMMON_COLOR, 5.5));
        r.add(enchantBook("Starborn Enchant Book", enchants_starborn(), "Uncommon", UNCOMMON_COLOR, 4.0));
        r.add(enchantBook("Stellar Enchant Book", enchants_stellar(), "Rare", RARE_COLOR, 2.0));
        r.add(item("Diamond Chestplate", enchantedItem(Material.DIAMOND_CHESTPLATE, Map.of(Enchantment.PROTECTION, 3)), "Uncommon", UNCOMMON_COLOR, 4.0));
        r.add(item("Diamond Sword", enchantedItem(Material.DIAMOND_SWORD, Map.of(Enchantment.SHARPNESS, 3)), "Uncommon", UNCOMMON_COLOR, 4.0));
        r.add(potion("Strength II Potion", PotionEffectType.STRENGTH, 1, 1800, "Common", COMMON_COLOR, 5.0));
        r.add(potion("Fire Resistance Potion", PotionEffectType.FIRE_RESISTANCE, 0, 6000, "Common", COMMON_COLOR, 5.5));
        r.add(item("4x Enchanted Golden Apple", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4), "Epic", EPIC_COLOR, 1.0));
        r.add(item("8x Golden Apple", new ItemStack(Material.GOLDEN_APPLE, 8), "Common", COMMON_COLOR, 5.0));
        r.add(item("1x Netherite Ingot", new ItemStack(Material.NETHERITE_INGOT, 1), "Epic", EPIC_COLOR, 1.0));
        r.add(item("64x Exp Bottle", new ItemStack(Material.EXPERIENCE_BOTTLE, 64), "Common", COMMON_COLOR, 3.5));
        return r;
    }

    private List<CrateReward> buildGalaxyRewards() {
        List<CrateReward> r = new ArrayList<>();
        r.add(money("$100,000", 100_000, "Common", COMMON_COLOR, 5.5));
        r.add(money("$250,000", 250_000, "Common", COMMON_COLOR, 5.0));
        r.add(money("$500,000", 500_000, "Uncommon", UNCOMMON_COLOR, 4.5));
        r.add(money("$1,000,000", 1_000_000, "Rare", RARE_COLOR, 3.0));
        r.add(money("$2,500,000", 2_500_000, "Epic", EPIC_COLOR, 1.0));
        r.add(gems(EconomyManager.GEMS_ICON + "50 Gems", 50, "Common", COMMON_COLOR, 5.5));
        r.add(gems(EconomyManager.GEMS_ICON + "100 Gems", 100, "Uncommon", UNCOMMON_COLOR, 4.5));
        r.add(gems(EconomyManager.GEMS_ICON + "250 Gems", 250, "Rare", RARE_COLOR, 2.5));
        r.add(stars(EconomyManager.STARS_ICON + "10 Stars", 10, "Rare", RARE_COLOR, 3.0));
        r.add(stars(EconomyManager.STARS_ICON + "25 Stars", 25, "Epic", EPIC_COLOR, 1.5));
        r.add(item("64x Diamond", new ItemStack(Material.DIAMOND, 64), "Common", COMMON_COLOR, 5.0));
        r.add(item("2x Netherite Ingot", new ItemStack(Material.NETHERITE_INGOT, 2), "Uncommon", UNCOMMON_COLOR, 4.0));
        r.add(item("4x Netherite Ingot", new ItemStack(Material.NETHERITE_INGOT, 4), "Rare", RARE_COLOR, 2.5));
        r.add(enchantBook("Starborn Enchant Book", enchants_starborn(), "Common", COMMON_COLOR, 5.0));
        r.add(enchantBook("Stellar Enchant Book", enchants_stellar(), "Uncommon", UNCOMMON_COLOR, 4.0));
        r.add(enchantBook("Celestial Enchant Book", enchants_celestial(), "Legendary", LEGENDARY_COLOR, 0.5));
        r.add(item("Netherite Chestplate", enchantedItem(Material.NETHERITE_CHESTPLATE, Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)), "Epic", EPIC_COLOR, 1.5));
        r.add(item("Netherite Sword", enchantedItem(Material.NETHERITE_SWORD, Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3)), "Epic", EPIC_COLOR, 1.5));
        r.add(spawner("Zombie Spawner", EntityType.ZOMBIE, "Rare", RARE_COLOR, 2.0));
        r.add(spawner("Skeleton Spawner", EntityType.SKELETON, "Rare", RARE_COLOR, 2.0));
        r.add(spawner("Blaze Spawner", EntityType.BLAZE, "Epic", EPIC_COLOR, 0.5));
        r.add(item("8x Enchanted Golden Apple", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8), "Rare", RARE_COLOR, 2.5));
        r.add(item("Netherite Helmet", enchantedItem(Material.NETHERITE_HELMET, Map.of(Enchantment.PROTECTION, 4)), "Rare", RARE_COLOR, 3.0));
        r.add(potion("Strength III Buff", PotionEffectType.STRENGTH, 2, 3600, "Uncommon", UNCOMMON_COLOR, 4.0));
        r.add(item("16x Netherite Scrap", new ItemStack(Material.NETHERITE_SCRAP, 16), "Uncommon", UNCOMMON_COLOR, 4.0));
        r.add(item("Totem of Undying", new ItemStack(Material.TOTEM_OF_UNDYING, 1), "Rare", RARE_COLOR, 3.0));
        r.add(money("$5,000,000", 5_000_000, "Legendary", LEGENDARY_COLOR, 0.2));
        r.add(stars(EconomyManager.STARS_ICON + "50 Stars", 50, "Legendary", LEGENDARY_COLOR, 0.3));
        return r;
    }

    private List<CrateReward> buildSeasonalRewards() {
        List<CrateReward> r = new ArrayList<>();
        r.add(money("$50,000", 50_000, "Common", COMMON_COLOR, 5.5));
        r.add(money("$100,000", 100_000, "Common", COMMON_COLOR, 5.5));
        r.add(money("$250,000", 250_000, "Uncommon", UNCOMMON_COLOR, 5.0));
        r.add(money("$500,000", 500_000, "Rare", RARE_COLOR, 3.0));
        r.add(money("$1,000,000", 1_000_000, "Epic", EPIC_COLOR, 1.5));
        r.add(gems(EconomyManager.GEMS_ICON + "50 Gems", 50, "Common", COMMON_COLOR, 5.5));
        r.add(gems(EconomyManager.GEMS_ICON + "100 Gems", 100, "Uncommon", UNCOMMON_COLOR, 5.0));
        r.add(gems(EconomyManager.GEMS_ICON + "200 Gems", 200, "Rare", RARE_COLOR, 3.0));
        r.add(stars(EconomyManager.STARS_ICON + "10 Stars", 10, "Rare", RARE_COLOR, 3.0));
        r.add(stars(EconomyManager.STARS_ICON + "25 Stars", 25, "Epic", EPIC_COLOR, 1.0));
        r.add(item("32x Diamond", new ItemStack(Material.DIAMOND, 32), "Common", COMMON_COLOR, 5.5));
        r.add(item("64x Diamond", new ItemStack(Material.DIAMOND, 64), "Uncommon", UNCOMMON_COLOR, 4.0));
        r.add(item("2x Netherite Ingot", new ItemStack(Material.NETHERITE_INGOT, 2), "Rare", RARE_COLOR, 3.0));
        r.add(enchantBook("Starborn Enchant Book", enchants_starborn(), "Uncommon", UNCOMMON_COLOR, 5.0));
        r.add(enchantBook("Stellar Enchant Book", enchants_stellar(), "Rare", RARE_COLOR, 3.0));
        r.add(enchantBook("Celestial Enchant Book", enchants_celestial(), "Legendary", LEGENDARY_COLOR, 0.5));
        r.add(item("Diamond Chestplate", enchantedItem(Material.DIAMOND_CHESTPLATE, Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)), "Rare", RARE_COLOR, 3.0));
        r.add(item("Enchanted Golden Apple x4", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4), "Rare", RARE_COLOR, 3.0));
        r.add(spawner("Zombie Spawner", EntityType.ZOMBIE, "Rare", RARE_COLOR, 2.5));
        r.add(item("Totem of Undying", new ItemStack(Material.TOTEM_OF_UNDYING, 1), "Rare", RARE_COLOR, 3.0));
        r.add(item("Dragon Egg", new ItemStack(Material.DRAGON_EGG, 1), "Legendary", LEGENDARY_COLOR, 0.2));
        r.add(potion("Haste II Buff", PotionEffectType.HASTE, 1, 6000, "Uncommon", UNCOMMON_COLOR, 5.0));
        r.add(item("Elytra", new ItemStack(Material.ELYTRA, 1), "Legendary", LEGENDARY_COLOR, 0.3));
        r.add(item("8x Golden Apple", new ItemStack(Material.GOLDEN_APPLE, 8), "Common", COMMON_COLOR, 5.5));
        r.add(potion("Regeneration III Buff", PotionEffectType.REGENERATION, 2, 1200, "Uncommon", UNCOMMON_COLOR, 4.0));
        r.add(item("64x Exp Bottle", new ItemStack(Material.EXPERIENCE_BOTTLE, 64), "Common", COMMON_COLOR, 3.5));
        return r;
    }

    // ── Reward factory helpers ──

    private CrateReward money(String name, double amount, String rarity, TextColor color, double weight) {
        return new CrateReward(name, Material.SUNFLOWER, 1, rarity, color, weight,
                p -> { economy.addMoney(p.getUniqueId(), amount); });
    }

    private CrateReward gems(String name, double amount, String rarity, TextColor color, double weight) {
        return new CrateReward(name, Material.EMERALD, Math.max(1, (int) amount), rarity, color, weight,
                p -> { economy.addGems(p.getUniqueId(), amount); });
    }

    private CrateReward stars(String name, double amount, String rarity, TextColor color, double weight) {
        return new CrateReward(name, Material.NETHER_STAR, Math.max(1, (int) amount), rarity, color, weight,
                p -> { economy.addStars(p.getUniqueId(), amount); });
    }

    private CrateReward item(String name, ItemStack stack, String rarity, TextColor color, double weight) {
        return new CrateReward(name, stack.getType(), stack.getAmount(), rarity, color, weight,
                p -> giveItem(p, stack.clone()));
    }

    private CrateReward enchantBook(String name, Map<Enchantment, Integer> enchants, String rarity, TextColor color, double weight) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        enchants.forEach((e, lvl) -> meta.addStoredEnchant(e, lvl, true));
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        book.setItemMeta(meta);
        return new CrateReward(name, Material.ENCHANTED_BOOK, 1, rarity, color, weight,
                p -> giveItem(p, book.clone()));
    }

    private CrateReward potion(String name, PotionEffectType effectType, int amplifier, int durationTicks,
                               String rarity, TextColor color, double weight) {
        ItemStack bottle = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) bottle.getItemMeta();
        meta.addCustomEffect(new PotionEffect(effectType, durationTicks, amplifier), true);
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        bottle.setItemMeta(meta);
        return new CrateReward(name, Material.SPLASH_POTION, 1, rarity, color, weight,
                p -> giveItem(p, bottle.clone()));
    }

    private CrateReward spawner(String name, EntityType entityType, String rarity, TextColor color, double weight) {
        return new CrateReward(name, Material.SPAWNER, 1, rarity, color, weight, p -> {
            ItemStack spawnerItem = new ItemStack(Material.SPAWNER);
            BlockStateMeta meta = (BlockStateMeta) spawnerItem.getItemMeta();
            CreatureSpawner cs = (CreatureSpawner) meta.getBlockState();
            cs.setSpawnedType(entityType);
            meta.setBlockState(cs);
            meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            spawnerItem.setItemMeta(meta);
            giveItem(p, spawnerItem);
        });
    }

    private ItemStack enchantedItem(Material material, Map<Enchantment, Integer> enchants) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        enchants.forEach((e, lvl) -> meta.addEnchant(e, lvl, true));
        item.setItemMeta(meta);
        return item;
    }

    // ── Enchant maps per tier ──

    private Map<Enchantment, Integer> enchants_stardust() {
        return switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> Map.of(Enchantment.PROTECTION, 1);
            case 1 -> Map.of(Enchantment.SHARPNESS, 1);
            case 2 -> Map.of(Enchantment.EFFICIENCY, 1);
            default -> Map.of(Enchantment.UNBREAKING, 1);
        };
    }

    private Map<Enchantment, Integer> enchants_starlight() {
        return switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> Map.of(Enchantment.PROTECTION, 2);
            case 1 -> Map.of(Enchantment.SHARPNESS, 2);
            case 2 -> Map.of(Enchantment.EFFICIENCY, 3);
            default -> Map.of(Enchantment.UNBREAKING, 2);
        };
    }

    private Map<Enchantment, Integer> enchants_starborn() {
        return switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> Map.of(Enchantment.PROTECTION, 3);
            case 1 -> Map.of(Enchantment.SHARPNESS, 3);
            case 2 -> Map.of(Enchantment.EFFICIENCY, 4);
            default -> Map.of(Enchantment.UNBREAKING, 3, Enchantment.FORTUNE, 2);
        };
    }

    private Map<Enchantment, Integer> enchants_stellar() {
        return switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> Map.of(Enchantment.PROTECTION, 4);
            case 1 -> Map.of(Enchantment.SHARPNESS, 4, Enchantment.FIRE_ASPECT, 2);
            case 2 -> Map.of(Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3);
            default -> Map.of(Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        };
    }

    private Map<Enchantment, Integer> enchants_celestial() {
        return switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
            case 1 -> Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
            case 2 -> Map.of(Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3, Enchantment.MENDING, 1);
            default -> Map.of(Enchantment.SILK_TOUCH, 1, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        };
    }

    // ── Database ──

    private void loadCrates() {
        db.queryAsync(conn -> {
            List<String[]> rows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT crate_type, world, x, y, z FROM su_crates")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new String[]{
                                rs.getString("crate_type"),
                                rs.getString("world"),
                                String.valueOf(rs.getInt("x")),
                                String.valueOf(rs.getInt("y")),
                                String.valueOf(rs.getInt("z"))
                        });
                    }
                }
            }
            return rows;
        }).thenAccept(rows -> {
            if (rows == null) return;
            for (String[] row : rows) {
                CrateType type = CrateType.fromName(row[0]);
                if (type == null) continue;
                String key = locKey(row[1], Integer.parseInt(row[2]), Integer.parseInt(row[3]), Integer.parseInt(row[4]));
                crateLocations.put(key, type);
            }
            plugin.getLogger().info("[SU] Loaded " + rows.size() + " crate locations.");
            respawnAllHolograms();
        });
    }

    private void saveCrate(Location loc, CrateType type) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_crates (crate_type, world, x, y, z) VALUES (?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE crate_type = VALUES(crate_type)")) {
                ps.setString(1, type.name());
                ps.setString(2, loc.getWorld().getName());
                ps.setInt(3, loc.getBlockX());
                ps.setInt(4, loc.getBlockY());
                ps.setInt(5, loc.getBlockZ());
                ps.executeUpdate();
            }
        });
    }

    private void deleteCrate(Location loc) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM su_crates WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                ps.setString(1, loc.getWorld().getName());
                ps.setInt(2, loc.getBlockX());
                ps.setInt(3, loc.getBlockY());
                ps.setInt(4, loc.getBlockZ());
                ps.executeUpdate();
            }
        });
    }

    public AdminManager getAdminManager() { return adminManager; }
    public EconomyManager getEconomy() { return economy; }
}
