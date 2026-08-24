package com.starlightuniverse.job;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JobManager {

    public static final int MAX_LEVEL = 100;

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor DARK_GREEN = TextColor.color(0x00AA00);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor DARK_GRAY = TextColor.color(0x555555);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);

    public record JobReward(JobType job, double money, int xp) {}

    private static final Map<Material, JobReward> BREAK_REWARDS = new HashMap<>();
    private static final Map<Material, JobReward> CROP_REWARDS = new HashMap<>();
    private static final Map<Material, JobReward> PLACE_REWARDS = new HashMap<>();
    private static final Map<EntityType, JobReward> KILL_REWARDS = new HashMap<>();

    static {
        // --- MINER ---
        for (Material m : List.of(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE))
            BREAK_REWARDS.put(m, new JobReward(JobType.MINER, 2, 5));
        for (Material m : List.of(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE))
            BREAK_REWARDS.put(m, new JobReward(JobType.MINER, 3, 7));
        for (Material m : List.of(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE))
            BREAK_REWARDS.put(m, new JobReward(JobType.MINER, 5, 10));
        for (Material m : List.of(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE))
            BREAK_REWARDS.put(m, new JobReward(JobType.MINER, 6, 12));
        for (Material m : List.of(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE))
            BREAK_REWARDS.put(m, new JobReward(JobType.MINER, 4, 8));
        for (Material m : List.of(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE))
            BREAK_REWARDS.put(m, new JobReward(JobType.MINER, 8, 15));
        for (Material m : List.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE))
            BREAK_REWARDS.put(m, new JobReward(JobType.MINER, 25, 50));
        for (Material m : List.of(Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE))
            BREAK_REWARDS.put(m, new JobReward(JobType.MINER, 20, 40));
        BREAK_REWARDS.put(Material.NETHER_GOLD_ORE, new JobReward(JobType.MINER, 5, 10));
        BREAK_REWARDS.put(Material.NETHER_QUARTZ_ORE, new JobReward(JobType.MINER, 3, 7));
        BREAK_REWARDS.put(Material.ANCIENT_DEBRIS, new JobReward(JobType.MINER, 50, 100));
        for (Material m : List.of(Material.STONE, Material.DEEPSLATE, Material.ANDESITE,
                Material.DIORITE, Material.GRANITE, Material.TUFF, Material.CALCITE,
                Material.BLACKSTONE, Material.BASALT, Material.NETHERRACK, Material.END_STONE))
            BREAK_REWARDS.put(m, new JobReward(JobType.MINER, 0.5, 1));

        // --- WOODCUTTER ---
        for (Material m : List.of(Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG,
                Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
                Material.MANGROVE_LOG, Material.CHERRY_LOG))
            BREAK_REWARDS.put(m, new JobReward(JobType.WOODCUTTER, 2, 5));
        BREAK_REWARDS.put(Material.BAMBOO_BLOCK, new JobReward(JobType.WOODCUTTER, 1, 3));
        for (Material m : List.of(Material.CRIMSON_STEM, Material.WARPED_STEM))
            BREAK_REWARDS.put(m, new JobReward(JobType.WOODCUTTER, 3, 7));

        // --- DIGGER ---
        for (Material m : List.of(Material.DIRT, Material.GRASS_BLOCK, Material.ROOTED_DIRT,
                Material.COARSE_DIRT, Material.DIRT_PATH))
            BREAK_REWARDS.put(m, new JobReward(JobType.DIGGER, 0.5, 1));
        for (Material m : List.of(Material.SAND, Material.RED_SAND))
            BREAK_REWARDS.put(m, new JobReward(JobType.DIGGER, 1, 2));
        BREAK_REWARDS.put(Material.GRAVEL, new JobReward(JobType.DIGGER, 1, 2));
        BREAK_REWARDS.put(Material.CLAY, new JobReward(JobType.DIGGER, 2, 4));
        for (Material m : List.of(Material.SOUL_SAND, Material.SOUL_SOIL))
            BREAK_REWARDS.put(m, new JobReward(JobType.DIGGER, 2, 3));
        for (Material m : List.of(Material.MYCELIUM, Material.PODZOL))
            BREAK_REWARDS.put(m, new JobReward(JobType.DIGGER, 2, 3));
        BREAK_REWARDS.put(Material.MUD, new JobReward(JobType.DIGGER, 1, 2));

        // --- FARMER (non-ageable) ---
        BREAK_REWARDS.put(Material.MELON, new JobReward(JobType.FARMER, 1, 2));
        BREAK_REWARDS.put(Material.PUMPKIN, new JobReward(JobType.FARMER, 2, 3));
        BREAK_REWARDS.put(Material.SUGAR_CANE, new JobReward(JobType.FARMER, 1, 2));
        BREAK_REWARDS.put(Material.CACTUS, new JobReward(JobType.FARMER, 1, 2));

        // --- FARMER (ageable crops, check maturity before rewarding) ---
        CROP_REWARDS.put(Material.WHEAT, new JobReward(JobType.FARMER, 1, 3));
        CROP_REWARDS.put(Material.CARROTS, new JobReward(JobType.FARMER, 1, 3));
        CROP_REWARDS.put(Material.POTATOES, new JobReward(JobType.FARMER, 1, 3));
        CROP_REWARDS.put(Material.BEETROOTS, new JobReward(JobType.FARMER, 2, 4));
        CROP_REWARDS.put(Material.NETHER_WART, new JobReward(JobType.FARMER, 3, 5));
        CROP_REWARDS.put(Material.COCOA, new JobReward(JobType.FARMER, 2, 4));
        CROP_REWARDS.put(Material.SWEET_BERRY_BUSH, new JobReward(JobType.FARMER, 1, 2));
        CROP_REWARDS.put(Material.TORCHFLOWER_CROP, new JobReward(JobType.FARMER, 3, 5));
        CROP_REWARDS.put(Material.PITCHER_CROP, new JobReward(JobType.FARMER, 3, 5));

        // --- BUILDER (any block placed) ---
        // Handled dynamically in listener — flat $0.5, 1 XP

        // --- HUNTER ---
        for (EntityType e : List.of(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER,
                EntityType.HUSK, EntityType.DROWNED))
            KILL_REWARDS.put(e, new JobReward(JobType.HUNTER, 3, 5));
        for (EntityType e : List.of(EntityType.SKELETON, EntityType.STRAY))
            KILL_REWARDS.put(e, new JobReward(JobType.HUNTER, 3, 5));
        KILL_REWARDS.put(EntityType.WITHER_SKELETON, new JobReward(JobType.HUNTER, 15, 25));
        for (EntityType e : List.of(EntityType.SPIDER, EntityType.CAVE_SPIDER))
            KILL_REWARDS.put(e, new JobReward(JobType.HUNTER, 3, 5));
        KILL_REWARDS.put(EntityType.CREEPER, new JobReward(JobType.HUNTER, 3, 5));
        KILL_REWARDS.put(EntityType.ENDERMAN, new JobReward(JobType.HUNTER, 10, 20));
        KILL_REWARDS.put(EntityType.BLAZE, new JobReward(JobType.HUNTER, 15, 25));
        KILL_REWARDS.put(EntityType.WITCH, new JobReward(JobType.HUNTER, 8, 15));
        KILL_REWARDS.put(EntityType.GUARDIAN, new JobReward(JobType.HUNTER, 10, 20));
        KILL_REWARDS.put(EntityType.ELDER_GUARDIAN, new JobReward(JobType.HUNTER, 50, 80));
        KILL_REWARDS.put(EntityType.WARDEN, new JobReward(JobType.HUNTER, 100, 200));
        KILL_REWARDS.put(EntityType.WITHER, new JobReward(JobType.HUNTER, 200, 400));
        KILL_REWARDS.put(EntityType.ENDER_DRAGON, new JobReward(JobType.HUNTER, 500, 1000));
        KILL_REWARDS.put(EntityType.PHANTOM, new JobReward(JobType.HUNTER, 5, 10));
        KILL_REWARDS.put(EntityType.PIGLIN_BRUTE, new JobReward(JobType.HUNTER, 12, 20));
        for (EntityType e : List.of(EntityType.HOGLIN, EntityType.ZOGLIN))
            KILL_REWARDS.put(e, new JobReward(JobType.HUNTER, 8, 15));
        KILL_REWARDS.put(EntityType.GHAST, new JobReward(JobType.HUNTER, 10, 15));
        for (EntityType e : List.of(EntityType.VINDICATOR, EntityType.PILLAGER))
            KILL_REWARDS.put(e, new JobReward(JobType.HUNTER, 8, 15));
        KILL_REWARDS.put(EntityType.EVOKER, new JobReward(JobType.HUNTER, 15, 25));
        KILL_REWARDS.put(EntityType.RAVAGER, new JobReward(JobType.HUNTER, 15, 25));
        KILL_REWARDS.put(EntityType.VEX, new JobReward(JobType.HUNTER, 3, 5));
        KILL_REWARDS.put(EntityType.SHULKER, new JobReward(JobType.HUNTER, 10, 20));
        KILL_REWARDS.put(EntityType.MAGMA_CUBE, new JobReward(JobType.HUNTER, 5, 8));
        KILL_REWARDS.put(EntityType.SLIME, new JobReward(JobType.HUNTER, 3, 5));
        KILL_REWARDS.put(EntityType.SILVERFISH, new JobReward(JobType.HUNTER, 2, 3));
        KILL_REWARDS.put(EntityType.ENDERMITE, new JobReward(JobType.HUNTER, 2, 3));
        KILL_REWARDS.put(EntityType.BREEZE, new JobReward(JobType.HUNTER, 12, 20));
        KILL_REWARDS.put(EntityType.BOGGED, new JobReward(JobType.HUNTER, 5, 8));
    }

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;

    private final Map<UUID, Map<JobType, long[]>> cache = new ConcurrentHashMap<>();
    private final Set<Long> placedBlocks = ConcurrentHashMap.newKeySet();
    private final Map<Long, UUID> brewingTracker = new ConcurrentHashMap<>();

    public JobManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
    }

    public void initialize() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            placedBlocks.clear();
        }, 36000L, 36000L); // clear placed blocks every 30 min
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayer(player);
        }
    }

    // --- Placed block tracking ---

    public void trackPlacedBlock(org.bukkit.block.Block block) {
        placedBlocks.add(blockKey(block));
    }

    public boolean isPlacedBlock(org.bukkit.block.Block block) {
        return placedBlocks.contains(blockKey(block));
    }

    private static long blockKey(org.bukkit.block.Block block) {
        long wh = block.getWorld().getUID().getMostSignificantBits();
        int x = block.getX();
        int y = block.getY() + 64;
        int z = block.getZ();
        return wh ^ (((long) (x & 0x3FFFFFF)) << 38) ^ (((long) (z & 0x3FFFFFF)) << 12) ^ (y & 0xFFF);
    }

    // --- Brewing tracker ---

    public void trackBrewingStand(org.bukkit.block.Block block, UUID uuid) {
        brewingTracker.put(blockKey(block), uuid);
    }

    public UUID getBrewingUser(org.bukkit.block.Block block) {
        return brewingTracker.get(blockKey(block));
    }

    // --- Reward lookup ---

    public static JobReward getBreakReward(Material material) {
        return BREAK_REWARDS.get(material);
    }

    public static JobReward getCropReward(Material material) {
        return CROP_REWARDS.get(material);
    }

    public static JobReward getKillReward(EntityType entityType) {
        return KILL_REWARDS.get(entityType);
    }

    // --- XP / Level math ---

    public static int xpForLevel(int level) {
        return (int) (100 * Math.pow(level, 1.5));
    }

    public static double getMultiplier(int level) {
        return 1.0 + (level - 1) * 0.02;
    }

    // --- Data access ---

    public int getLevel(UUID uuid, JobType job) {
        Map<JobType, long[]> jobs = cache.get(uuid);
        if (jobs == null) return 1;
        long[] data = jobs.get(job);
        return data != null ? (int) data[0] : 1;
    }

    public long getXp(UUID uuid, JobType job) {
        Map<JobType, long[]> jobs = cache.get(uuid);
        if (jobs == null) return 0;
        long[] data = jobs.get(job);
        return data != null ? data[1] : 0;
    }

    // --- Add reward ---

    public void addReward(Player player, JobType job, double baseMoney, int baseXp) {
        UUID uuid = player.getUniqueId();
        Map<JobType, long[]> jobs = cache.computeIfAbsent(uuid, k -> new EnumMap<>(JobType.class));
        long[] data = jobs.computeIfAbsent(job, k -> new long[]{1, 0});

        int level = (int) data[0];
        if (level >= MAX_LEVEL) {
            double money = baseMoney * getMultiplier(level);
            if (money > 0) economy.addMoney(uuid, money);
            return;
        }

        double multiplier = getMultiplier(level);
        double money = baseMoney * multiplier;
        if (money > 0) economy.addMoney(uuid, money);

        data[1] += baseXp;

        while (data[0] < MAX_LEVEL && data[1] >= xpForLevel((int) data[0])) {
            data[1] -= xpForLevel((int) data[0]);
            data[0]++;
            onLevelUp(player, job, (int) data[0]);
        }

        if (data[0] >= MAX_LEVEL) {
            data[1] = 0;
        }

        saveJobAsync(player.getName().toLowerCase(), job, (int) data[0], data[1]);
    }

    private void onLevelUp(Player player, JobType job, int newLevel) {
        TextColor jobColor = TextColor.color(Integer.parseInt(job.getHexColor().substring(1), 16));
        String mult = String.format("%.2fx", getMultiplier(newLevel));

        player.sendMessage(Component.text("[SU] ", GOLD)
                .append(Component.text("⬆ ", GREEN))
                .append(Component.text(job.getDisplayName(), jobColor).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" leveled up! ", GREEN))
                .append(Component.text("Level " + newLevel, CYAN))
                .append(Component.text(" (" + mult + " multiplier)", YELLOW)));

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    // --- Player data load/save ---

    public void loadPlayer(UUID uuid, String username) {
        db.queryAsync(conn -> {
            Map<JobType, long[]> jobs = new EnumMap<>(JobType.class);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT job_type, level, xp FROM su_jobs WHERE username = ?")) {
                ps.setString(1, username.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            JobType type = JobType.valueOf(rs.getString("job_type"));
                            jobs.put(type, new long[]{rs.getInt("level"), rs.getLong("xp")});
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            return jobs;
        }).thenAccept(jobs -> {
            if (jobs != null) cache.put(uuid, jobs);
        });
    }

    public void unloadPlayer(UUID uuid) {
        cache.remove(uuid);
    }

    public void savePlayer(Player player) {
        Map<JobType, long[]> jobs = cache.get(player.getUniqueId());
        if (jobs == null) return;
        String username = player.getName().toLowerCase();
        for (Map.Entry<JobType, long[]> entry : jobs.entrySet()) {
            saveJobAsync(username, entry.getKey(), (int) entry.getValue()[0], entry.getValue()[1]);
        }
    }

    private void saveJobAsync(String username, JobType job, int level, long xp) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_jobs (username, job_type, level, xp) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE level = VALUES(level), xp = VALUES(xp)")) {
                ps.setString(1, username);
                ps.setString(2, job.name());
                ps.setInt(3, level);
                ps.setLong(4, xp);
                ps.executeUpdate();
            }
        });
    }

    // --- GUI ---

    private static final int[] TOP_ROW_SLOTS = {11, 12, 13, 14, 15};
    private static final int[] BOTTOM_ROW_SLOTS = {29, 30, 31, 32, 33};
    private static final JobType[] TOP_JOBS = {
            JobType.MINER, JobType.WOODCUTTER, JobType.FARMER, JobType.HUNTER, JobType.FISHERMAN
    };
    private static final JobType[] BOTTOM_JOBS = {
            JobType.BUILDER, JobType.DIGGER, JobType.BREWER, JobType.ENCHANTER, JobType.SMELTER
    };

    public void openJobsGui(Player player) {
        JobHolder holder = new JobHolder();
        Inventory inv = Bukkit.createInventory(holder, 45,
                Component.text("Jobs", GOLD).decoration(TextDecoration.BOLD, true));
        holder.setInventory(inv);

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);
        for (int i = 0; i < 45; i++) inv.setItem(i, border);

        UUID uuid = player.getUniqueId();

        for (int i = 0; i < TOP_JOBS.length; i++) {
            inv.setItem(TOP_ROW_SLOTS[i], buildJobItem(uuid, TOP_JOBS[i]));
        }
        for (int i = 0; i < BOTTOM_JOBS.length; i++) {
            inv.setItem(BOTTOM_ROW_SLOTS[i], buildJobItem(uuid, BOTTOM_JOBS[i]));
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Job Info", YELLOW).decoration(TextDecoration.ITALIC, false));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.text("All 10 jobs are always active.", GRAY).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("Earn Money for every action.", GRAY).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("Higher level = bigger multiplier!", GREEN).decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(40, info);

        player.openInventory(inv);
    }

    private ItemStack buildJobItem(UUID uuid, JobType job) {
        int level = getLevel(uuid, job);
        long xp = getXp(uuid, job);
        long xpNeeded = level >= MAX_LEVEL ? 0 : xpForLevel(level);
        double mult = getMultiplier(level);
        double progress = level >= MAX_LEVEL ? 1.0 : (xpNeeded > 0 ? (double) xp / xpNeeded : 0);

        TextColor jobColor = TextColor.color(Integer.parseInt(job.getHexColor().substring(1), 16));

        ItemStack item = new ItemStack(job.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(job.getDisplayName(), jobColor)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        lore.add(Component.text("Level: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(level, WHITE))
                .append(Component.text(" / " + MAX_LEVEL, DARK_GRAY)));

        if (level >= MAX_LEVEL) {
            lore.add(Component.text("XP: ", GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("MAX", GOLD).decoration(TextDecoration.BOLD, true)));
        } else {
            lore.add(Component.text("XP: ", GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(EconomyManager.format(xp), WHITE))
                    .append(Component.text(" / " + EconomyManager.format(xpNeeded), DARK_GRAY)));
        }

        lore.add(buildProgressBar(progress));

        lore.add(Component.empty());
        lore.add(Component.text("Multiplier: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format("%.2fx", mult), GREEN)
                        .decoration(TextDecoration.BOLD, true)));

        lore.add(Component.empty());
        lore.add(Component.text(job.getDescription(), DARK_GRAY).decoration(TextDecoration.ITALIC, true));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component buildProgressBar(double progress) {
        int total = 20;
        int filled = (int) (total * Math.min(progress, 1.0));
        int empty = total - filled;
        String pct = String.format("%.1f%%", progress * 100);

        StringBuilder filledBar = new StringBuilder();
        StringBuilder emptyBar = new StringBuilder();
        for (int i = 0; i < filled; i++) filledBar.append("|");
        for (int i = 0; i < empty; i++) emptyBar.append("|");

        return Component.text("[", DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(filledBar.toString(), DARK_GREEN))
                .append(Component.text(emptyBar.toString(), TextColor.color(0x333333)))
                .append(Component.text("] ", DARK_GRAY))
                .append(Component.text(pct, YELLOW));
    }
}
