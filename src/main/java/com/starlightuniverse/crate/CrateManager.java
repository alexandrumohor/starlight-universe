package com.starlightuniverse.crate;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.booster.BoosterType;
import com.starlightuniverse.buff.BuffManager;
import com.starlightuniverse.buff.BuffType;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.spawner.SpawnerManager;
import com.starlightuniverse.spawner.VirtualSpawnerType;
import com.starlightuniverse.tool.UniverseToolManager;
import com.starlightuniverse.util.Msg;
import com.starlightuniverse.voucher.VoucherManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
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

    private static final Material[] ORE_TYPES = {
            Material.COAL, Material.RAW_IRON, Material.RAW_COPPER, Material.RAW_GOLD,
            Material.LAPIS_LAZULI, Material.REDSTONE, Material.DIAMOND, Material.EMERALD,
            Material.AMETHYST_SHARD, Material.QUARTZ
    };

    private static final EntityType[] PHYSICAL_SPAWNER_MOBS = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER,
            EntityType.ENDERMAN, EntityType.BLAZE, EntityType.IRON_GOLEM, EntityType.WITCH,
            EntityType.GUARDIAN, EntityType.PIGLIN
    };

    private BukkitTask particleTask;
    private VoucherManager voucherManager;
    private SpawnerManager spawnerManager;
    private BuffManager buffManager;
    private UniverseToolManager universeToolManager;

    public CrateManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy, AdminManager adminManager) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
        this.adminManager = adminManager;
    }

    public void setVoucherManager(VoucherManager voucherManager) {
        this.voucherManager = voucherManager;
    }

    public void setSpawnerManager(SpawnerManager spawnerManager) {
        this.spawnerManager = spawnerManager;
    }

    public void setBuffManager(BuffManager buffManager) {
        this.buffManager = buffManager;
    }

    public void setUniverseToolManager(UniverseToolManager universeToolManager) {
        this.universeToolManager = universeToolManager;
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
                CrateType crateType = entry.getValue();
                var dust = new Particle.DustOptions(crateType.getBukkitColor(), 1.2f);
                world.spawnParticle(Particle.DUST, x, y + 0.3, z, 2, 0.3, 0.2, 0.3, 0.01, dust);
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

        String tierShort = type.getDisplayName().replace(" Crate", "");
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Right-Click", TextColor.fromHexString("#FF0000"))
                .append(Component.text(" a ", WHITE))
                .append(Component.text(tierShort, type.getColor()))
                .append(Component.text(" ", WHITE))
                .append(Component.text("Crate", type.getColor()))
                .append(Component.text(" to claim a random reward!", WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Rarity: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(type.getDisplayName(), type.getColor())));
        meta.lore(lore);

        meta.setEnchantmentGlintOverride(true);
        meta.setItemModel(NamespacedKey.fromString("starlight:cr_key_" + type.name().toLowerCase()));

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

            if (reward.displayModel() != null) {
                meta.setItemModel(reward.displayModel());
            }

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
        rewardTables.put(CrateType.CELESTIAL, buildCelestialRewards());
        rewardTables.put(CrateType.UNIVERSE, buildUniverseRewards());

        for (CrateType type : CrateType.values()) {
            double total = 0;
            for (CrateReward r : rewardTables.get(type)) total += r.weight();
            totalWeights.put(type, total);
        }
    }

    private List<CrateReward> buildStarRewards() {
        List<CrateReward> r = new ArrayList<>();
        r.add(moneyRange("$1,000-$5,000", 1_000, 5_000, "Common", COMMON_COLOR, 25));
        r.add(xpRange("250-1,000 XP", 250, 1_000, "Common", COMMON_COLOR, 25));
        r.add(flyVoucher("Fly Voucher 10 min", 10, "Uncommon", UNCOMMON_COLOR, 8));
        r.add(protectionToken("+50 Blocks", 50, "Uncommon", UNCOMMON_COLOR, 8));
        r.add(boosterRange("Booster 1.5-2.0x", 1.5, 2.0, 15, "Uncommon", UNCOMMON_COLOR, 6));
        r.add(item("8x Golden Apple", new ItemStack(Material.GOLDEN_APPLE, 8), "Uncommon", UNCOMMON_COLOR, 6));
        r.add(enchantBook("Stardust Enchant Book", enchants_stardust(), "Common", COMMON_COLOR, 10));
        r.add(gearTicket("Star Gear Ticket", CrateType.STAR, "Common", COMMON_COLOR, 10));
        r.add(bonusKeys("2 Star Keys", CrateType.STAR, 2, "Uncommon", UNCOMMON_COLOR, 5));
        r.add(bonusKeys("1 Universe Key", CrateType.UNIVERSE, 1, "Rare", RARE_COLOR, 2));
        return r;
    }

    private List<CrateReward> buildCosmicRewards() {
        List<CrateReward> r = new ArrayList<>();
        r.add(moneyRange("$5,000-$10,000", 5_000, 10_000, "Common", COMMON_COLOR, 25));
        r.add(xpRange("1,000-1,500 XP", 1_000, 1_500, "Common", COMMON_COLOR, 25));
        r.add(flyVoucher("Fly Voucher 30 min", 30, "Uncommon", UNCOMMON_COLOR, 8));
        r.add(protectionToken("+100 Blocks", 100, "Uncommon", UNCOMMON_COLOR, 8));
        r.add(boosterRange("Booster 2.0-2.5x", 2.0, 2.5, 15, "Uncommon", UNCOMMON_COLOR, 6));
        r.add(enchantBook("Starlight Enchant Book", enchants_starlight(), "Uncommon", UNCOMMON_COLOR, 8));
        r.add(gearTicket("Cosmic Gear Ticket", CrateType.COSMIC, "Uncommon", UNCOMMON_COLOR, 8));
        r.add(bonusKeys("2 Cosmic Keys", CrateType.COSMIC, 2, "Rare", RARE_COLOR, 4));
        r.add(bonusKeys("1 Universe Key", CrateType.UNIVERSE, 1, "Epic", EPIC_COLOR, 1.5));
        return r;
    }

    private List<CrateReward> buildGalaxyRewards() {
        List<CrateReward> r = new ArrayList<>();
        r.add(moneyRange("$15,000-$25,000", 15_000, 25_000, "Common", COMMON_COLOR, 22));
        r.add(xpRange("2,000-4,000 XP", 2_000, 4_000, "Common", COMMON_COLOR, 22));
        r.add(flyVoucher("Fly Voucher 45 min", 45, "Uncommon", UNCOMMON_COLOR, 7));
        r.add(protectionToken("+150 Blocks", 150, "Uncommon", UNCOMMON_COLOR, 7));
        r.add(boosterRange("Booster 2.5-3.0x", 2.5, 3.0, 20, "Rare", RARE_COLOR, 5));
        r.add(orePack("Ore Pack (15x)", 15, "Rare", RARE_COLOR, 4));
        r.add(enchantBook("Starborn Enchant Book", enchants_starborn(), "Rare", RARE_COLOR, 6));
        r.add(gearTicket("Galaxy Gear Ticket", CrateType.GALAXY, "Rare", RARE_COLOR, 6));
        r.add(bonusKeys("2 Galaxy Keys", CrateType.GALAXY, 2, "Rare", RARE_COLOR, 3));
        return r;
    }

    private List<CrateReward> buildCelestialRewards() {
        List<CrateReward> r = new ArrayList<>();
        r.add(moneyRange("$30,000-$50,000", 30_000, 50_000, "Uncommon", UNCOMMON_COLOR, 20));
        r.add(xpRange("5,000-10,000 XP", 5_000, 10_000, "Uncommon", UNCOMMON_COLOR, 20));
        r.add(flyVoucher("Fly Voucher 1h", 60, "Rare", RARE_COLOR, 6));
        r.add(protectionToken("+250 Blocks", 250, "Rare", RARE_COLOR, 6));
        r.add(boosterRange("Booster 3.0-3.5x", 3.0, 3.5, 25, "Epic", EPIC_COLOR, 4));
        r.add(orePack("Ore Pack (32x)", 32, "Epic", EPIC_COLOR, 4));
        r.add(randomVirtualSpawner("Random Virtual Spawner", "Epic", EPIC_COLOR, 3));
        r.add(enchantBook("Stellar Enchant Book", enchants_stellar(), "Epic", EPIC_COLOR, 5));
        r.add(gearTicket("Celestial Gear Ticket", CrateType.CELESTIAL, "Epic", EPIC_COLOR, 5));
        r.add(enchantProtectionScroll("Enchant Protection Scroll", "Epic", EPIC_COLOR, 3));
        r.add(randomBuff("Random Buff (30 min)", 30 * 60 * 1000L, "Epic", EPIC_COLOR, 3));
        r.add(bonusKeys("2 Celestial Keys", CrateType.CELESTIAL, 2, "Epic", EPIC_COLOR, 2));
        return r;
    }

    private List<CrateReward> buildUniverseRewards() {
        List<CrateReward> r = new ArrayList<>();
        r.add(moneyRange("$75,000-$100,000", 75_000, 100_000, "Uncommon", UNCOMMON_COLOR, 18));
        r.add(xpRange("10,000-20,000 XP", 10_000, 20_000, "Rare", RARE_COLOR, 15));
        r.add(flyVoucher("Fly Voucher 2h", 120, "Rare", RARE_COLOR, 5));
        r.add(protectionToken("+500 Blocks", 500, "Epic", EPIC_COLOR, 5));
        r.add(boosterRange("Booster 4.0-5.0x", 4.0, 5.0, 30, "Legendary", LEGENDARY_COLOR, 2));
        r.add(orePack("Ore Pack (64x)", 64, "Legendary", LEGENDARY_COLOR, 2));
        r.add(starsPack("Stars Pack (10★)", 10, "Legendary", LEGENDARY_COLOR, 2));
        r.add(randomPhysicalSpawner("Random Mob Spawner", "Legendary", LEGENDARY_COLOR, 1.5));
        r.add(enchantBook("Celestial Enchant Book", enchants_celestial(), "Legendary", LEGENDARY_COLOR, 3));
        r.add(gearTicket("Universe Gear Ticket", CrateType.UNIVERSE, "Legendary", LEGENDARY_COLOR, 3));
        r.add(enchantProtectionScroll("Enchant Protection Scroll", "Legendary", LEGENDARY_COLOR, 2));
        r.add(randomBuff("Random Buff (1h)", 60 * 60 * 1000L, "Legendary", LEGENDARY_COLOR, 2));
        r.add(randomUniverseTool("Random Universe Tool", "Legendary", LEGENDARY_COLOR, 1));
        r.add(bonusKeys("2 Universe Keys", CrateType.UNIVERSE, 2, "Legendary", LEGENDARY_COLOR, 1));
        return r;
    }

    // ── Reward factory helpers ──

    private CrateReward moneyRange(String name, double min, double max, String rarity, TextColor color, double weight) {
        NamespacedKey model = NamespacedKey.fromString("starlight:cr_money_reward");
        return new CrateReward(name, Material.SUNFLOWER, 1, rarity, color, weight, p -> {
            double amount = min + ThreadLocalRandom.current().nextDouble() * (max - min);
            amount = Math.round(amount / 100.0) * 100.0;
            economy.addMoney(p.getUniqueId(), amount);
        }, model);
    }

    private CrateReward xpRange(String name, int min, int max, String rarity, TextColor color, double weight) {
        NamespacedKey model = NamespacedKey.fromString("starlight:shop_xp");
        return new CrateReward(name, Material.EXPERIENCE_BOTTLE, 1, rarity, color, weight,
                p -> p.giveExp(ThreadLocalRandom.current().nextInt(min, max + 1), false), model);
    }

    private CrateReward protectionToken(String name, int blocks, String rarity, TextColor color, double weight) {
        NamespacedKey model = NamespacedKey.fromString("starlight:cr_protection");
        return new CrateReward(name, Material.HEART_OF_THE_SEA, 1, rarity, color, weight,
                p -> giveItem(p, voucherManager.createProtectionToken(blocks)), model);
    }

    private CrateReward flyVoucher(String name, int minutes, String rarity, TextColor color, double weight) {
        NamespacedKey model = NamespacedKey.fromString("starlight:shop_fly");
        return new CrateReward(name, Material.FEATHER, 1, rarity, color, weight,
                p -> giveItem(p, voucherManager.createFlyVoucher(minutes)), model);
    }

    private CrateReward boosterRange(String name, double minMult, double maxMult, int durationMin,
                                     String rarity, TextColor color, double weight) {
        NamespacedKey model = NamespacedKey.fromString("starlight:cr_booster");
        BoosterType[] types = BoosterType.values();
        return new CrateReward(name, Material.BLAZE_POWDER, 1, rarity, color, weight, p -> {
            BoosterType type = types[ThreadLocalRandom.current().nextInt(types.length)];
            double mult = minMult + ThreadLocalRandom.current().nextDouble() * (maxMult - minMult);
            mult = Math.round(mult * 10.0) / 10.0;
            giveItem(p, voucherManager.createBooster(type, mult, durationMin));
        }, model);
    }

    private CrateReward bonusKeys(String name, CrateType keyType, int amount, String rarity, TextColor color, double weight) {
        NamespacedKey model = NamespacedKey.fromString("starlight:cr_key_" + keyType.name().toLowerCase());
        return new CrateReward(name, Material.TRIPWIRE_HOOK, amount, rarity, color, weight,
                p -> giveItem(p, createKey(keyType, amount)), model);
    }

    private CrateReward gearTicket(String name, CrateType tier, String rarity, TextColor color, double weight) {
        NamespacedKey model = NamespacedKey.fromString("starlight:gear_ticket_" + tier.name().toLowerCase());
        return new CrateReward(name, Material.PAPER, 1, rarity, color, weight,
                p -> giveItem(p, voucherManager.createGearTicket(tier)), model);
    }

    private CrateReward item(String name, ItemStack stack, String rarity, TextColor color, double weight) {
        return new CrateReward(name, stack.getType(), stack.getAmount(), rarity, color, weight,
                p -> giveItem(p, stack.clone()));
    }

    private CrateReward enchantProtectionScroll(String name, String rarity, TextColor color, double weight) {
        NamespacedKey model = NamespacedKey.fromString("starlight:enchant_protection_scroll");
        return new CrateReward(name, Material.PAPER, 1, rarity, color, weight,
                p -> giveItem(p, voucherManager.createEnchantProtectionScroll()), model);
    }

    private CrateReward enchantBook(String name, Map<Enchantment, Integer> enchants, String rarity, TextColor color, double weight) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        enchants.forEach((e, lvl) -> meta.addStoredEnchant(e, lvl, true));
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        NamespacedKey model = NamespacedKey.fromString("starlight:shop_enchant_book");
        meta.setItemModel(model);
        book.setItemMeta(meta);
        return new CrateReward(name, Material.ENCHANTED_BOOK, 1, rarity, color, weight,
                p -> giveItem(p, book.clone()), model);
    }

    private CrateReward orePack(String name, int amount, String rarity, TextColor color, double weight) {
        NamespacedKey model = NamespacedKey.fromString("starlight:cr_ore_pack");
        return new CrateReward(name, Material.DIAMOND, amount, rarity, color, weight, p -> {
            Material ore = ORE_TYPES[ThreadLocalRandom.current().nextInt(ORE_TYPES.length)];
            giveItem(p, new ItemStack(ore, amount));
        }, model);
    }

    private CrateReward starsPack(String name, int stars, String rarity, TextColor color, double weight) {
        NamespacedKey model = NamespacedKey.fromString("starlight:cr_stars_pack");
        return new CrateReward(name, Material.NETHER_STAR, stars, rarity, color, weight,
                p -> economy.addStars(p.getUniqueId(), stars), model);
    }

    private CrateReward randomVirtualSpawner(String name, String rarity, TextColor color, double weight) {
        NamespacedKey model = NamespacedKey.fromString("starlight:cr_spawner");
        VirtualSpawnerType[] types = VirtualSpawnerType.values();
        return new CrateReward(name, Material.SPAWNER, 1, rarity, color, weight, p -> {
            VirtualSpawnerType type = types[ThreadLocalRandom.current().nextInt(types.length)];
            giveItem(p, spawnerManager.createSpawnerItem(type, 1, 1));
        }, model);
    }

    private CrateReward randomBuff(String name, long durationMs, String rarity, TextColor color, double weight) {
        NamespacedKey model = NamespacedKey.fromString("starlight:cr_buff");
        BuffType[] types = BuffType.values();
        return new CrateReward(name, Material.BEACON, 1, rarity, color, weight, p -> {
            BuffType type = types[ThreadLocalRandom.current().nextInt(types.length)];
            buffManager.activateBuff(p, type, durationMs);
        }, model);
    }

    private CrateReward randomUniverseTool(String name, String rarity, TextColor color, double weight) {
        NamespacedKey model = NamespacedKey.fromString("starlight:universe_pickaxe");
        return new CrateReward(name, Material.NETHERITE_PICKAXE, 1, rarity, color, weight,
                p -> giveItem(p, universeToolManager.createRandomTool()), model);
    }

    private CrateReward randomPhysicalSpawner(String name, String rarity, TextColor color, double weight) {
        NamespacedKey model = NamespacedKey.fromString("starlight:cr_spawner");
        return new CrateReward(name, Material.SPAWNER, 1, rarity, color, weight, p -> {
            EntityType mob = PHYSICAL_SPAWNER_MOBS[ThreadLocalRandom.current().nextInt(PHYSICAL_SPAWNER_MOBS.length)];
            ItemStack spawner = new ItemStack(Material.SPAWNER);
            ItemMeta meta = spawner.getItemMeta();
            String mobName = mob.name().charAt(0) + mob.name().substring(1).toLowerCase().replace('_', ' ');
            meta.displayName(Component.text(mobName + " Spawner", LEGENDARY_COLOR)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Mob: ", GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(mobName, YELLOW)));
            lore.add(Component.text("Place to activate!", GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.setItemModel(model);
            org.bukkit.block.BlockState blockState = ((org.bukkit.inventory.meta.BlockStateMeta) meta).getBlockState();
            if (blockState instanceof org.bukkit.block.CreatureSpawner cs) {
                cs.setSpawnedType(mob);
                ((org.bukkit.inventory.meta.BlockStateMeta) meta).setBlockState(blockState);
            }
            spawner.setItemMeta(meta);
            giveItem(p, spawner);
        }, model);
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
    public VoucherManager getVoucherManager() { return voucherManager; }
}
