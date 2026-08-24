package com.starlightuniverse.skill;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SkillManager {

    public static final int MAX_LEVEL = 100;

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor DARK_GREEN = TextColor.color(0x00AA00);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor DARK_GRAY = TextColor.color(0x555555);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor PURPLE = TextColor.color(0xAA00AA);

    public record Milestone(int level, String description) {}

    private static final Map<SkillType, List<Milestone>> MILESTONES = new EnumMap<>(SkillType.class);

    static {
        MILESTONES.put(SkillType.MINING, List.of(
                new Milestone(10, "5% chance double ore drops"),
                new Milestone(25, "10% chance double ore drops"),
                new Milestone(50, "Haste I while mining"),
                new Milestone(75, "15% chance double ore drops"),
                new Milestone(100, "Haste II while mining")
        ));

        MILESTONES.put(SkillType.EXCAVATION, List.of(
                new Milestone(10, "5% chance double dig drops"),
                new Milestone(25, "10% chance double dig drops"),
                new Milestone(50, "15% chance double dig drops"),
                new Milestone(75, "20% chance double dig drops"),
                new Milestone(100, "25% chance double dig drops")
        ));

        MILESTONES.put(SkillType.WOODCUTTING, List.of(
                new Milestone(10, "5% chance double log drops"),
                new Milestone(25, "10% chance double log drops"),
                new Milestone(50, "15% chance double log drops"),
                new Milestone(75, "20% chance double log drops"),
                new Milestone(100, "25% chance double log drops")
        ));

        MILESTONES.put(SkillType.FARMING, List.of(
                new Milestone(10, "10% chance extra crop drop"),
                new Milestone(25, "20% chance extra crop drop"),
                new Milestone(50, "Auto-replant on harvest"),
                new Milestone(75, "35% chance extra crop drop"),
                new Milestone(100, "50% chance extra crop drop")
        ));

        MILESTONES.put(SkillType.COMBAT, List.of(
                new Milestone(10, "+3% melee damage"),
                new Milestone(25, "+5% melee damage"),
                new Milestone(50, "1% lifesteal on hit"),
                new Milestone(75, "2% lifesteal on hit"),
                new Milestone(100, "+10% damage, 3% lifesteal")
        ));

        MILESTONES.put(SkillType.ARCHERY, List.of(
                new Milestone(10, "+5% arrow damage"),
                new Milestone(25, "+10% arrow damage"),
                new Milestone(50, "10% arrow recovery"),
                new Milestone(75, "+15% arrow damage"),
                new Milestone(100, "+20% damage, 20% recovery")
        ));

        MILESTONES.put(SkillType.FISHING, List.of(
                new Milestone(10, "+5% treasure chance"),
                new Milestone(25, "+10% treasure chance"),
                new Milestone(50, "5% double catch"),
                new Milestone(75, "+15% treasure chance"),
                new Milestone(100, "10% double catch")
        ));

        MILESTONES.put(SkillType.ACROBATICS, List.of(
                new Milestone(10, "-10% fall damage"),
                new Milestone(25, "-20% fall damage"),
                new Milestone(50, "5% dodge chance"),
                new Milestone(75, "-35% fall damage"),
                new Milestone(100, "10% dodge, -50% fall damage")
        ));

        MILESTONES.put(SkillType.REPAIR, List.of(
                new Milestone(10, "-5% anvil XP cost"),
                new Milestone(25, "-10% anvil XP cost"),
                new Milestone(50, "5% chance free repair"),
                new Milestone(75, "-15% anvil XP cost"),
                new Milestone(100, "10% chance free repair")
        ));

        MILESTONES.put(SkillType.ALCHEMY, List.of(
                new Milestone(10, "+10% potion duration"),
                new Milestone(25, "+20% potion duration"),
                new Milestone(50, "5% chance double brew"),
                new Milestone(75, "+30% potion duration"),
                new Milestone(100, "10% chance double brew")
        ));

        MILESTONES.put(SkillType.TAMING, List.of(
                new Milestone(10, "Tamed wolves +10% damage"),
                new Milestone(25, "Tamed wolves +20% damage"),
                new Milestone(50, "5% instant tame chance"),
                new Milestone(75, "Tamed wolves +30% damage"),
                new Milestone(100, "15% instant tame chance")
        ));

        MILESTONES.put(SkillType.COOKING, List.of(
                new Milestone(10, "5% chance double food"),
                new Milestone(25, "10% chance double food"),
                new Milestone(50, "+1 extra hunger restored"),
                new Milestone(75, "15% chance double food"),
                new Milestone(100, "+2 extra hunger, 20% double")
        ));
    }

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;

    private final Map<UUID, Map<SkillType, long[]>> cache = new ConcurrentHashMap<>();
    private final Map<Long, UUID> brewingTracker = new ConcurrentHashMap<>();

    public SkillManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
    }

    public void initialize() {
        // nothing periodic needed for skills
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayer(player);
        }
    }

    // --- Brewing tracker ---

    public void trackBrewingStand(org.bukkit.block.Block block, UUID uuid) {
        brewingTracker.put(blockKey(block), uuid);
    }

    public UUID getBrewingUser(org.bukkit.block.Block block) {
        return brewingTracker.get(blockKey(block));
    }

    private static long blockKey(org.bukkit.block.Block block) {
        long wh = block.getWorld().getUID().getMostSignificantBits();
        int x = block.getX();
        int y = block.getY() + 64;
        int z = block.getZ();
        return wh ^ (((long) (x & 0x3FFFFFF)) << 38) ^ (((long) (z & 0x3FFFFFF)) << 12) ^ (y & 0xFFF);
    }

    // --- XP / Level math ---

    public static int xpForLevel(int level) {
        return (int) (100 * Math.pow(level, 1.5));
    }

    // --- Data access ---

    public int getLevel(UUID uuid, SkillType skill) {
        Map<SkillType, long[]> skills = cache.get(uuid);
        if (skills == null) return 1;
        long[] data = skills.get(skill);
        return data != null ? (int) data[0] : 1;
    }

    public long getXp(UUID uuid, SkillType skill) {
        Map<SkillType, long[]> skills = cache.get(uuid);
        if (skills == null) return 0;
        long[] data = skills.get(skill);
        return data != null ? data[1] : 0;
    }

    // --- Add XP ---

    public void addXp(Player player, SkillType skill, int xp) {
        UUID uuid = player.getUniqueId();
        Map<SkillType, long[]> skills = cache.computeIfAbsent(uuid, k -> new EnumMap<>(SkillType.class));
        long[] data = skills.computeIfAbsent(skill, k -> new long[]{1, 0});

        int level = (int) data[0];
        if (level >= MAX_LEVEL) return;

        data[1] += xp;

        while (data[0] < MAX_LEVEL && data[1] >= xpForLevel((int) data[0])) {
            data[1] -= xpForLevel((int) data[0]);
            data[0]++;
            onLevelUp(player, skill, (int) data[0]);
        }

        if (data[0] >= MAX_LEVEL) {
            data[1] = 0;
        }

        saveSkillAsync(player.getName().toLowerCase(), skill, (int) data[0], data[1]);
    }

    private void onLevelUp(Player player, SkillType skill, int newLevel) {
        TextColor skillColor = TextColor.color(Integer.parseInt(skill.getHexColor().substring(1), 16));

        player.sendMessage(Component.text("[SU] ", GOLD)
                .append(Component.text("⬆ ", GREEN))
                .append(Component.text(skill.getDisplayName(), skillColor).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" leveled up! ", GREEN))
                .append(Component.text("Level " + newLevel, CYAN)));

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);

        List<Milestone> milestones = MILESTONES.get(skill);
        if (milestones != null) {
            for (Milestone m : milestones) {
                if (m.level() == newLevel) {
                    player.sendMessage(Component.text("[SU] ", GOLD)
                            .append(Component.text("★ Milestone unlocked: ", PURPLE))
                            .append(Component.text(m.description(), YELLOW)));
                    break;
                }
            }
        }

        if (newLevel == MAX_LEVEL) {
            player.sendMessage(Component.text("[SU] ", GOLD)
                    .append(Component.text("★ ", GOLD).decoration(TextDecoration.BOLD, true))
                    .append(Component.text(skill.getDisplayName() + " MAXED! ", skillColor).decoration(TextDecoration.BOLD, true))
                    .append(Component.text("+★5 Stars reward!", PURPLE)));
            economy.addStars(player.getUniqueId(), 5);
        }
    }

    // --- Milestone effect helpers ---

    public static double getMiningDoubleDropChance(int level) {
        if (level >= 75) return 0.15;
        if (level >= 25) return 0.10;
        if (level >= 10) return 0.05;
        return 0;
    }

    public static int getMiningHasteLevel(int level) {
        if (level >= 100) return 1; // Haste II (amplifier 1)
        if (level >= 50) return 0;  // Haste I (amplifier 0)
        return -1;
    }

    public static double getExcavationDoubleDropChance(int level) {
        if (level >= 100) return 0.25;
        if (level >= 75) return 0.20;
        if (level >= 50) return 0.15;
        if (level >= 25) return 0.10;
        if (level >= 10) return 0.05;
        return 0;
    }

    public static double getWoodcuttingDoubleDropChance(int level) {
        if (level >= 100) return 0.25;
        if (level >= 75) return 0.20;
        if (level >= 50) return 0.15;
        if (level >= 25) return 0.10;
        if (level >= 10) return 0.05;
        return 0;
    }

    public static double getFarmingExtraDropChance(int level) {
        if (level >= 100) return 0.50;
        if (level >= 75) return 0.35;
        if (level >= 25) return 0.20;
        if (level >= 10) return 0.10;
        return 0;
    }

    public static boolean hasFarmingAutoReplant(int level) {
        return level >= 50;
    }

    public static double getCombatDamageBonus(int level) {
        if (level >= 100) return 0.10;
        if (level >= 25) return 0.05;
        if (level >= 10) return 0.03;
        return 0;
    }

    public static double getCombatLifesteal(int level) {
        if (level >= 100) return 0.03;
        if (level >= 75) return 0.02;
        if (level >= 50) return 0.01;
        return 0;
    }

    public static double getArcheryDamageBonus(int level) {
        if (level >= 100) return 0.20;
        if (level >= 75) return 0.15;
        if (level >= 25) return 0.10;
        if (level >= 10) return 0.05;
        return 0;
    }

    public static double getArcheryRecoveryChance(int level) {
        if (level >= 100) return 0.20;
        if (level >= 50) return 0.10;
        return 0;
    }

    public static double getFishingDoubleCatchChance(int level) {
        if (level >= 100) return 0.10;
        if (level >= 50) return 0.05;
        return 0;
    }

    public static double getAcrobaticsFallReduction(int level) {
        if (level >= 100) return 0.50;
        if (level >= 75) return 0.35;
        if (level >= 25) return 0.20;
        if (level >= 10) return 0.10;
        return 0;
    }

    public static double getAcrobaticsDodgeChance(int level) {
        if (level >= 100) return 0.10;
        if (level >= 50) return 0.05;
        return 0;
    }

    public static double getCookingDoubleFoodChance(int level) {
        if (level >= 100) return 0.20;
        if (level >= 75) return 0.15;
        if (level >= 25) return 0.10;
        if (level >= 10) return 0.05;
        return 0;
    }

    public static double getTamingWolfDamageBonus(int level) {
        if (level >= 75) return 0.30;
        if (level >= 25) return 0.20;
        if (level >= 10) return 0.10;
        return 0;
    }

    public static double getTamingInstantTameChance(int level) {
        if (level >= 100) return 0.15;
        if (level >= 50) return 0.05;
        return 0;
    }

    public static double getAlchemyDoubleBrew(int level) {
        if (level >= 100) return 0.10;
        if (level >= 50) return 0.05;
        return 0;
    }

    // --- Player data load/save ---

    public void loadPlayer(UUID uuid, String username) {
        db.queryAsync(conn -> {
            Map<SkillType, long[]> skills = new EnumMap<>(SkillType.class);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT skill_type, level, xp FROM su_skills WHERE username = ?")) {
                ps.setString(1, username.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            SkillType type = SkillType.valueOf(rs.getString("skill_type"));
                            skills.put(type, new long[]{rs.getInt("level"), rs.getLong("xp")});
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            return skills;
        }).thenAccept(skills -> {
            if (skills != null) cache.put(uuid, skills);
        });
    }

    public void unloadPlayer(UUID uuid) {
        cache.remove(uuid);
    }

    public void savePlayer(Player player) {
        Map<SkillType, long[]> skills = cache.get(player.getUniqueId());
        if (skills == null) return;
        String username = player.getName().toLowerCase();
        for (Map.Entry<SkillType, long[]> entry : skills.entrySet()) {
            saveSkillAsync(username, entry.getKey(), (int) entry.getValue()[0], entry.getValue()[1]);
        }
    }

    private void saveSkillAsync(String username, SkillType skill, int level, long xp) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_skills (username, skill_type, level, xp) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE level = VALUES(level), xp = VALUES(xp)")) {
                ps.setString(1, username);
                ps.setString(2, skill.name());
                ps.setInt(3, level);
                ps.setLong(4, xp);
                ps.executeUpdate();
            }
        });
    }

    // --- GUI ---

    private static final int[] TOP_ROW_SLOTS = {10, 11, 12, 13, 14, 15};
    private static final int[] BOTTOM_ROW_SLOTS = {28, 29, 30, 31, 32, 33};
    private static final SkillType[] TOP_SKILLS = {
            SkillType.MINING, SkillType.EXCAVATION, SkillType.WOODCUTTING,
            SkillType.FARMING, SkillType.COMBAT, SkillType.ARCHERY
    };
    private static final SkillType[] BOTTOM_SKILLS = {
            SkillType.FISHING, SkillType.ACROBATICS, SkillType.REPAIR,
            SkillType.ALCHEMY, SkillType.TAMING, SkillType.COOKING
    };

    public void openSkillsGui(Player player) {
        SkillHolder holder = new SkillHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Skills", PURPLE).decoration(TextDecoration.BOLD, true));
        holder.setInventory(inv);

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        UUID uuid = player.getUniqueId();

        for (int i = 0; i < TOP_SKILLS.length; i++) {
            inv.setItem(TOP_ROW_SLOTS[i], buildSkillItem(uuid, TOP_SKILLS[i]));
        }
        for (int i = 0; i < BOTTOM_SKILLS.length; i++) {
            inv.setItem(BOTTOM_ROW_SLOTS[i], buildSkillItem(uuid, BOTTOM_SKILLS[i]));
        }

        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Skill Info", YELLOW).decoration(TextDecoration.ITALIC, false));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.text("12 skills level up as you play.", GRAY).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("Unlock milestone benefits!", GRAY).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("Max level (100) = ★5 Stars reward.", GREEN).decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        player.openInventory(inv);
    }

    private ItemStack buildSkillItem(UUID uuid, SkillType skill) {
        int level = getLevel(uuid, skill);
        long xp = getXp(uuid, skill);
        long xpNeeded = level >= MAX_LEVEL ? 0 : xpForLevel(level);
        double progress = level >= MAX_LEVEL ? 1.0 : (xpNeeded > 0 ? (double) xp / xpNeeded : 0);

        TextColor skillColor = TextColor.color(Integer.parseInt(skill.getHexColor().substring(1), 16));

        ItemStack item = new ItemStack(skill.getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(skill.getDisplayName(), skillColor)
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
        lore.add(Component.text("Milestones:", YELLOW).decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Milestone> milestones = MILESTONES.get(skill);
        if (milestones != null) {
            for (Milestone m : milestones) {
                boolean unlocked = level >= m.level();
                String prefix = unlocked ? "✔ " : "✖ ";
                TextColor color = unlocked ? GREEN : DARK_GRAY;
                lore.add(Component.text("  " + prefix, color).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("Lv" + m.level() + ": ", unlocked ? CYAN : GRAY))
                        .append(Component.text(m.description(), color)));
            }
        }

        lore.add(Component.empty());
        lore.add(Component.text(skill.getDescription(), DARK_GRAY).decoration(TextDecoration.ITALIC, true));

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
