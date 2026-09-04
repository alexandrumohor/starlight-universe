package com.starlightuniverse.premium;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PremiumManager {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor PURPLE = TextColor.color(0xAA00AA);

    public static final String[] TRAIL_TYPES = {
            "HEARTS", "FLAMES", "SPARKLES", "SMOKE", "MAGIC",
            "SNOW", "NOTES", "WATER", "CRIT", "ENCHANT"
    };

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;
    private final AdminManager adminManager;

    private final Map<UUID, Location> lastDeathLocations = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeTrails = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, ArmorStand> sittingPlayers = new ConcurrentHashMap<>();
    private final Set<UUID> flyingPlayers = ConcurrentHashMap.newKeySet();

    private BukkitTask trailTask;

    public PremiumManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy, AdminManager adminManager) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
        this.adminManager = adminManager;
    }

    public void initialize() {
        trailTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTrails, 5L, 5L);
    }

    public void shutdown() {
        if (trailTask != null) trailTask.cancel();
        for (Map.Entry<UUID, ArmorStand> entry : sittingPlayers.entrySet()) {
            ArmorStand stand = entry.getValue();
            if (stand != null && stand.isValid()) stand.remove();
        }
        sittingPlayers.clear();
        for (UUID uuid : flyingPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setAllowFlight(false);
                p.setFlying(false);
            }
        }
        flyingPlayers.clear();
    }

    public JavaPlugin getPlugin() { return plugin; }
    public DatabaseManager getDb() { return db; }
    public EconomyManager getEconomy() { return economy; }
    public AdminManager getAdminManager() { return adminManager; }

    public PremiumRank getPlayerRank(UUID uuid) {
        return PremiumRank.fromLevel(adminManager.getPremiumLevel(uuid));
    }

    public boolean hasRank(UUID uuid, int requiredLevel) {
        return adminManager.getPremiumLevel(uuid) >= requiredLevel;
    }

    // ==================== COOLDOWNS ====================

    public boolean isOnCooldown(UUID uuid, String key) {
        Map<String, Long> playerCd = cooldowns.get(uuid);
        if (playerCd == null) return false;
        Long expires = playerCd.get(key);
        if (expires == null) return false;
        return System.currentTimeMillis() < expires;
    }

    public long getCooldownRemaining(UUID uuid, String key) {
        Map<String, Long> playerCd = cooldowns.get(uuid);
        if (playerCd == null) return 0;
        Long expires = playerCd.get(key);
        if (expires == null) return 0;
        long remaining = expires - System.currentTimeMillis();
        return remaining > 0 ? remaining : 0;
    }

    public void setCooldown(UUID uuid, String key, long durationMs) {
        cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(key, System.currentTimeMillis() + durationMs);
    }

    // ==================== TRAILS ====================

    public String getActiveTrail(UUID uuid) { return activeTrails.get(uuid); }

    public void setTrail(UUID uuid, String trailType) {
        if (trailType == null) {
            activeTrails.remove(uuid);
        } else {
            activeTrails.put(uuid, trailType);
        }
    }

    private void tickTrails() {
        for (Map.Entry<UUID, String> entry : activeTrails.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            Location loc = player.getLocation().add(0, 0.5, 0);
            Particle particle = trailParticle(entry.getValue());
            if (particle != null) {
                player.getWorld().spawnParticle(particle, loc, 3, 0.2, 0.2, 0.2, 0);
            }
        }
    }

    private Particle trailParticle(String type) {
        return switch (type) {
            case "HEARTS" -> Particle.HEART;
            case "FLAMES" -> Particle.FLAME;
            case "SPARKLES" -> Particle.END_ROD;
            case "SMOKE" -> Particle.SMOKE;
            case "MAGIC" -> Particle.WITCH;
            case "SNOW" -> Particle.SNOWFLAKE;
            case "NOTES" -> Particle.NOTE;
            case "WATER" -> Particle.DRIPPING_WATER;
            case "CRIT" -> Particle.CRIT;
            case "ENCHANT" -> Particle.ENCHANT;
            default -> null;
        };
    }

    // ==================== DEATH TRACKING ====================

    public void setLastDeath(UUID uuid, Location loc) {
        lastDeathLocations.put(uuid, loc.clone());
    }

    public Location getLastDeath(UUID uuid) {
        return lastDeathLocations.get(uuid);
    }

    // ==================== SITTING ====================

    public boolean isSitting(UUID uuid) { return sittingPlayers.containsKey(uuid); }

    public void sitDown(Player player) {
        UUID uuid = player.getUniqueId();
        if (sittingPlayers.containsKey(uuid)) {
            standUp(player);
            return;
        }
        Location loc = player.getLocation().clone();
        loc.setY(loc.getY() - 0.3);
        ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setInvulnerable(true);
            as.setGravity(false);
            as.setSmall(true);
            as.setMarker(false);
            as.setBasePlate(false);
        });
        stand.addPassenger(player);
        sittingPlayers.put(uuid, stand);
    }

    public void standUp(Player player) {
        ArmorStand stand = sittingPlayers.remove(player.getUniqueId());
        if (stand != null && stand.isValid()) {
            stand.removePassenger(player);
            stand.remove();
        }
    }

    // ==================== FLYING ====================

    public boolean isFlying(UUID uuid) { return flyingPlayers.contains(uuid); }

    public void toggleFly(Player player) {
        UUID uuid = player.getUniqueId();
        if (flyingPlayers.remove(uuid)) {
            player.setAllowFlight(false);
            player.setFlying(false);
            Msg.success(player, "Flight disabled.");
        } else {
            flyingPlayers.add(uuid);
            player.setAllowFlight(true);
            Msg.success(player, "Flight enabled.");
        }
    }

    public void disableFly(Player player) {
        UUID uuid = player.getUniqueId();
        if (flyingPlayers.remove(uuid)) {
            player.setAllowFlight(false);
            player.setFlying(false);
            Msg.info(player, "Flight disabled in this area.");
        }
    }

    // ==================== DAILY BONUS ====================

    public void checkDailyBonus(Player player) {
        UUID uuid = player.getUniqueId();
        PremiumRank rank = getPlayerRank(uuid);
        if (rank == PremiumRank.NONE) return;
        int bonus = rank.getDailyBonus();
        if (bonus <= 0) return;

        String username = player.getName().toLowerCase();
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT daily_bonus_date FROM su_players WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        java.sql.Date lastDate = rs.getDate("daily_bonus_date");
                        java.sql.Date today = java.sql.Date.valueOf(java.time.LocalDate.now());
                        if (lastDate != null && !lastDate.before(today)) return false;
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET daily_bonus_date = CURDATE() WHERE username = ?")) {
                ps.setString(1, username);
                ps.executeUpdate();
            }
            return true;
        }).thenAccept(shouldGive -> {
            if (shouldGive != null && shouldGive) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        economy.addMoney(uuid, bonus);
                        Msg.success(player, "Daily bonus: " + EconomyManager.MONEY_ICON + " $" + EconomyManager.format(bonus) + "!");
                    }
                });
            }
        });
    }

    // ==================== MONTHLY STARS ====================

    public void checkMonthlyStars(Player player) {
        UUID uuid = player.getUniqueId();
        PremiumRank rank = getPlayerRank(uuid);
        int stars = rank.getMonthlyStars();
        if (stars <= 0) return;

        String username = player.getName().toLowerCase();
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT monthly_stars_date FROM su_players WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String lastMonth = rs.getString("monthly_stars_date");
                        String currentMonth = java.time.LocalDate.now().getYear() + "-" +
                                String.format("%02d", java.time.LocalDate.now().getMonthValue());
                        if (currentMonth.equals(lastMonth)) return false;
                    }
                }
            }
            String currentMonth = java.time.LocalDate.now().getYear() + "-" +
                    String.format("%02d", java.time.LocalDate.now().getMonthValue());
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET monthly_stars_date = ? WHERE username = ?")) {
                ps.setString(1, currentMonth);
                ps.setString(2, username);
                ps.executeUpdate();
            }
            return true;
        }).thenAccept(shouldGive -> {
            if (shouldGive != null && shouldGive) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        economy.addStars(uuid, stars);
                        Msg.success(player, "Monthly Stars reward: " + EconomyManager.STARS_ICON +
                                EconomyManager.format(stars) + "!");
                    }
                });
            }
        });
    }

    // ==================== PREMIUM TRIAL ====================

    public void grantTrial(String username) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET premium_level = 1, premium_expire_date = DATE_ADD(NOW(), INTERVAL 3 DAY) " +
                            "WHERE username = ? AND premium_level = 0")) {
                ps.setString(1, username.toLowerCase());
                ps.executeUpdate();
            }
        });
    }

    public void checkTrialExpiry(Player player) {
        String username = player.getName().toLowerCase();
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT premium_level, premium_expire_date FROM su_players WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int level = rs.getInt("premium_level");
                        java.sql.Timestamp expire = rs.getTimestamp("premium_expire_date");
                        if (level > 0 && expire != null && expire.before(new java.sql.Timestamp(System.currentTimeMillis()))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }).thenAccept(expired -> {
            if (expired != null && expired) {
                db.executeAsync(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE su_players SET premium_level = 0, premium_expire_date = NULL WHERE username = ?")) {
                        ps.setString(1, username);
                        ps.executeUpdate();
                    }
                });
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        adminManager.loadPlayer(player.getUniqueId(), player.getName());
                        Msg.info(player, "Your premium trial has expired! Use /premium to purchase a rank.");
                    }
                });
            }
        });
    }

    // ==================== REFERRAL ====================

    public void handleReferral(Player player, String referrerName) {
        String username = player.getName().toLowerCase();
        String referrer = referrerName.toLowerCase();

        if (username.equals(referrer)) {
            Msg.error(player, "You can't refer yourself!");
            return;
        }

        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT referred_by FROM su_players WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getString("referred_by") != null) return "ALREADY";
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM su_players WHERE username = ?")) {
                ps.setString(1, referrer);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return "NOT_FOUND";
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET referred_by = ? WHERE username = ?")) {
                ps.setString(1, referrer);
                ps.setString(2, username);
                ps.executeUpdate();
            }
            return "OK";
        }).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || result == null) return;
            switch (result) {
                case "ALREADY" -> Msg.error(player, "You have already used your referral!");
                case "NOT_FOUND" -> Msg.error(player, "Player not found!");
                case "OK" -> {
                    economy.addStars(player.getUniqueId(), 20);
                    Msg.success(player, "Referral bonus: " + EconomyManager.STARS_ICON + "20!");
                    Player ref = Bukkit.getPlayer(referrer);
                    if (ref != null && ref.isOnline()) {
                        economy.addStars(ref.getUniqueId(), 20);
                        Msg.success(ref, player.getName() + " used your referral code! Bonus: " +
                                EconomyManager.STARS_ICON + "20!");
                    } else {
                        economy.giveOffline(referrer, "stars", 20);
                    }
                }
            }
        }));
    }

    // ==================== BUY RANK ====================

    public void buyRank(Player player, PremiumRank rank, String currency) {
        UUID uuid = player.getUniqueId();
        int currentLevel = adminManager.getPremiumLevel(uuid);

        if (currentLevel >= rank.getLevel()) {
            Msg.error(player, "You already have this rank or higher!");
            return;
        }

        boolean success;
        String costDisplay;

        if ("gems".equalsIgnoreCase(currency)) {
            int cost = rank.getGemsCost();
            success = economy.removeGems(uuid, cost);
            costDisplay = EconomyManager.GEMS_ICON + EconomyManager.format(cost);
            if (!success) { Msg.error(player, "Not enough Gems! Need " + costDisplay); return; }
        } else {
            int cost = rank.getStarsCost();
            success = economy.removeStars(uuid, cost);
            costDisplay = EconomyManager.STARS_ICON + EconomyManager.format(cost);
            if (!success) { Msg.error(player, "Not enough Stars! Need " + costDisplay); return; }
        }

        String username = player.getName().toLowerCase();
        adminManager.setPremiumLevel(username, rank.getLevel()).thenRun(() -> {
            db.executeAsync(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE su_players SET premium_expire_date = NULL WHERE username = ?")) {
                    ps.setString(1, username);
                    ps.executeUpdate();
                }
            });
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    adminManager.loadPlayer(uuid, player.getName());
                    player.sendMessage(Component.text("[SU] ", GOLD)
                            .append(Component.text("You are now ", GREEN))
                            .append(rank.getColoredPrefix())
                            .append(Component.text("! Enjoy your perks.", GREEN)));
                    Bukkit.getServer().sendMessage(Component.text("[SU] ", GOLD)
                            .append(Component.text(player.getName(), YELLOW))
                            .append(Component.text(" purchased ", GREEN))
                            .append(rank.getColoredPrefix())
                            .append(Component.text(" rank!", GREEN)));
                }
            });
        });
    }

    // ==================== PREMIUM GUI ====================

    public void openPremiumGui(Player player) {
        PremiumHolder holder = new PremiumHolder(PremiumHolder.Type.RANK_OVERVIEW);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Premium Ranks", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        int currentLevel = adminManager.getPremiumLevel(player.getUniqueId());

        PremiumRank[] ranks = {PremiumRank.METEOR, PremiumRank.COMET, PremiumRank.NEBULA,
                PremiumRank.SUPERNOVA, PremiumRank.GALAXY, PremiumRank.UNIVERSE};
        Material[] icons = {Material.IRON_INGOT, Material.DIAMOND, Material.AMETHYST_SHARD,
                Material.GOLD_INGOT, Material.NETHER_STAR, Material.END_CRYSTAL};
        int[] slots = {1, 3, 5, 7, 10, 16};

        for (int i = 0; i < ranks.length; i++) {
            PremiumRank rank = ranks[i];
            ItemStack item = new ItemStack(icons[i]);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(rank.getColoredPrefix().decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            if (currentLevel >= rank.getLevel()) {
                lore.add(Component.text("OWNED", GREEN).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(EconomyManager.STARS_ICON + EconomyManager.format(rank.getStarsCost()) +
                        " Stars", YELLOW).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(EconomyManager.GEMS_ICON + EconomyManager.format(rank.getGemsCost()) +
                        " Gems", CYAN).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            String homesStr = rank.getMaxHomes() < 0 ? "Unlimited" : String.valueOf(rank.getMaxHomes());
            lore.add(Component.text("Homes: " + homesStr + " | Blocks: " +
                    String.format("%,d", rank.getMaxProtectionBlocks()), GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Keep XP: " + rank.getKeepXpPercent() + "% | Cooldown: " +
                    rank.getCooldownSeconds() + "s", GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("XP Boost: " + rank.getXpBoost() + "x | Mob Money: +" +
                    rank.getMobKillMoneyBonus() + "%", GRAY).decoration(TextDecoration.ITALIC, false));
            if (rank.getDailyBonus() > 0)
                lore.add(Component.text("Daily: " + EconomyManager.MONEY_ICON + " $" + EconomyManager.format(rank.getDailyBonus()), GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            if (rank.getMonthlyStars() > 0)
                lore.add(Component.text("Monthly: " + EconomyManager.STARS_ICON + rank.getMonthlyStars(), GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            if (currentLevel < rank.getLevel()) {
                lore.add(Component.text("Click to purchase!", YELLOW).decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Premium Info", YELLOW).decoration(TextDecoration.ITALIC, false));
        PremiumRank current = PremiumRank.fromLevel(currentLevel);
        infoMeta.lore(List.of(
                Component.text("Your rank: " + current.getDisplayName(), GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Each rank includes all previous perks!", GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(22, info);

        player.openInventory(inv);
    }

    public void openBuyGui(Player player, PremiumRank rank) {
        PremiumHolder holder = new PremiumHolder(PremiumHolder.Type.RANK_BUY);
        holder.setSelectedRank(rank.getLevel());
        Inventory inv = Bukkit.createInventory(holder, 9,
                Component.text("Buy " + rank.getDisplayName(), GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        ItemStack stars = new ItemStack(Material.NETHER_STAR);
        ItemMeta starsMeta = stars.getItemMeta();
        starsMeta.displayName(Component.text("Buy with Stars", PURPLE).decoration(TextDecoration.ITALIC, false));
        starsMeta.lore(List.of(
                Component.text("Cost: " + EconomyManager.STARS_ICON + EconomyManager.format(rank.getStarsCost()), YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        stars.setItemMeta(starsMeta);
        inv.setItem(2, stars);

        ItemStack gems = new ItemStack(Material.DIAMOND);
        ItemMeta gemsMeta = gems.getItemMeta();
        gemsMeta.displayName(Component.text("Buy with Gems", CYAN).decoration(TextDecoration.ITALIC, false));
        gemsMeta.lore(List.of(
                Component.text("Cost: " + EconomyManager.GEMS_ICON + EconomyManager.format(rank.getGemsCost()), YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        gems.setItemMeta(gemsMeta);
        inv.setItem(6, gems);

        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(Component.text("Cancel", RED).decoration(TextDecoration.ITALIC, false));
        cancel.setItemMeta(cancelMeta);
        inv.setItem(4, cancel);

        player.openInventory(inv);
    }

    public void openTrashGui(Player player) {
        PremiumHolder holder = new PremiumHolder(PremiumHolder.Type.TRASH);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Trash", RED).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    public void openTrailGui(Player player) {
        UUID uuid = player.getUniqueId();
        PremiumRank rank = getPlayerRank(uuid);
        int maxTrails = rank.getMaxTrails();
        String activeTrail = getActiveTrail(uuid);

        PremiumHolder holder = new PremiumHolder(PremiumHolder.Type.TRAIL_SELECT);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Trails", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        Material[] trailIcons = {
                Material.POPPY, Material.BLAZE_POWDER, Material.GLOWSTONE_DUST,
                Material.GUNPOWDER, Material.ENDER_PEARL, Material.SNOWBALL,
                Material.NOTE_BLOCK, Material.WATER_BUCKET, Material.FLINT,
                Material.ENCHANTED_BOOK
        };

        for (int i = 0; i < TRAIL_TYPES.length; i++) {
            ItemStack item = new ItemStack(trailIcons[i]);
            ItemMeta meta = item.getItemMeta();
            boolean active = TRAIL_TYPES[i].equals(activeTrail);
            meta.displayName(Component.text(formatTrailName(TRAIL_TYPES[i]),
                    active ? GREEN : YELLOW).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            if (active) {
                lore.add(Component.text("ACTIVE - Click to disable", GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Click to activate", GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        ItemStack disable = new ItemStack(Material.BARRIER);
        ItemMeta disableMeta = disable.getItemMeta();
        disableMeta.displayName(Component.text("Disable Trail", RED).decoration(TextDecoration.ITALIC, false));
        disable.setItemMeta(disableMeta);
        inv.setItem(22, disable);

        player.openInventory(inv);
    }

    private String formatTrailName(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    // ==================== CONDENSING ====================

    private static final Map<Material, Material> CONDENSE_MAP = new LinkedHashMap<>();
    private static final Map<Material, Material> UNCONDENSE_MAP = new LinkedHashMap<>();

    static {
        CONDENSE_MAP.put(Material.IRON_INGOT, Material.IRON_BLOCK);
        CONDENSE_MAP.put(Material.GOLD_INGOT, Material.GOLD_BLOCK);
        CONDENSE_MAP.put(Material.DIAMOND, Material.DIAMOND_BLOCK);
        CONDENSE_MAP.put(Material.EMERALD, Material.EMERALD_BLOCK);
        CONDENSE_MAP.put(Material.LAPIS_LAZULI, Material.LAPIS_BLOCK);
        CONDENSE_MAP.put(Material.REDSTONE, Material.REDSTONE_BLOCK);
        CONDENSE_MAP.put(Material.COAL, Material.COAL_BLOCK);
        CONDENSE_MAP.put(Material.COPPER_INGOT, Material.COPPER_BLOCK);
        CONDENSE_MAP.put(Material.RAW_IRON, Material.RAW_IRON_BLOCK);
        CONDENSE_MAP.put(Material.RAW_GOLD, Material.RAW_GOLD_BLOCK);
        CONDENSE_MAP.put(Material.RAW_COPPER, Material.RAW_COPPER_BLOCK);
        CONDENSE_MAP.put(Material.WHEAT, Material.HAY_BLOCK);
        CONDENSE_MAP.put(Material.SLIME_BALL, Material.SLIME_BLOCK);
        CONDENSE_MAP.put(Material.CLAY_BALL, Material.CLAY);
        CONDENSE_MAP.put(Material.SNOWBALL, Material.SNOW_BLOCK);
        CONDENSE_MAP.put(Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK);
        CONDENSE_MAP.put(Material.AMETHYST_SHARD, Material.AMETHYST_BLOCK);
        CONDENSE_MAP.put(Material.BONE_MEAL, Material.BONE_BLOCK);
        CONDENSE_MAP.put(Material.DRIED_KELP, Material.DRIED_KELP_BLOCK);

        CONDENSE_MAP.forEach((from, to) -> UNCONDENSE_MAP.put(to, from));
    }

    public int condenseInventory(Player player) {
        int total = 0;
        for (Map.Entry<Material, Material> entry : CONDENSE_MAP.entrySet()) {
            total += condenseItem(player, entry.getKey(), entry.getValue(), 9);
        }
        return total;
    }

    public int uncondenseInventory(Player player) {
        int total = 0;
        for (Map.Entry<Material, Material> entry : UNCONDENSE_MAP.entrySet()) {
            total += uncondenseItem(player, entry.getKey(), entry.getValue(), 9);
        }
        return total;
    }

    private int condenseItem(Player player, Material from, Material to, int ratio) {
        int count = countMaterial(player, from);
        int blocks = count / ratio;
        if (blocks <= 0) return 0;
        removeMaterial(player, from, blocks * ratio);
        giveMaterial(player, to, blocks);
        return blocks;
    }

    private int uncondenseItem(Player player, Material from, Material to, int ratio) {
        int count = countMaterial(player, from);
        if (count <= 0) return 0;
        removeMaterial(player, from, count);
        giveMaterial(player, to, count * ratio);
        return count;
    }

    private int countMaterial(Player player, Material mat) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) count += item.getAmount();
        }
        return count;
    }

    private void removeMaterial(Player player, Material mat, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == mat) {
                int remove = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - remove);
                remaining -= remove;
            }
        }
    }

    private void giveMaterial(Player player, Material mat, int amount) {
        while (amount > 0) {
            int give = Math.min(amount, mat.getMaxStackSize());
            ItemStack item = new ItemStack(mat, give);
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            overflow.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
            amount -= give;
        }
    }

    // ==================== SMELTING ====================

    private static final Map<Material, Material> SMELT_MAP = new LinkedHashMap<>();

    static {
        SMELT_MAP.put(Material.RAW_IRON, Material.IRON_INGOT);
        SMELT_MAP.put(Material.RAW_GOLD, Material.GOLD_INGOT);
        SMELT_MAP.put(Material.RAW_COPPER, Material.COPPER_INGOT);
        SMELT_MAP.put(Material.COBBLESTONE, Material.STONE);
        SMELT_MAP.put(Material.SAND, Material.GLASS);
        SMELT_MAP.put(Material.RED_SAND, Material.GLASS);
        SMELT_MAP.put(Material.CLAY_BALL, Material.BRICK);
        SMELT_MAP.put(Material.NETHERRACK, Material.NETHER_BRICK);
        SMELT_MAP.put(Material.OAK_LOG, Material.CHARCOAL);
        SMELT_MAP.put(Material.BIRCH_LOG, Material.CHARCOAL);
        SMELT_MAP.put(Material.SPRUCE_LOG, Material.CHARCOAL);
        SMELT_MAP.put(Material.DARK_OAK_LOG, Material.CHARCOAL);
        SMELT_MAP.put(Material.JUNGLE_LOG, Material.CHARCOAL);
        SMELT_MAP.put(Material.ACACIA_LOG, Material.CHARCOAL);
        SMELT_MAP.put(Material.IRON_ORE, Material.IRON_INGOT);
        SMELT_MAP.put(Material.GOLD_ORE, Material.GOLD_INGOT);
        SMELT_MAP.put(Material.COPPER_ORE, Material.COPPER_INGOT);
        SMELT_MAP.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
        SMELT_MAP.put(Material.WET_SPONGE, Material.SPONGE);
        SMELT_MAP.put(Material.CACTUS, Material.GREEN_DYE);
        SMELT_MAP.put(Material.KELP, Material.DRIED_KELP);
    }

    public Material getSmeltResult(Material source) {
        return SMELT_MAP.get(source);
    }

    // ==================== SORTING ====================

    public void sortInventory(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 9; i < contents.length; i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                items.add(contents[i].clone());
                contents[i] = null;
            }
        }

        items.sort(Comparator.comparing(a -> a.getType().name()));

        int slot = 9;
        for (ItemStack item : items) {
            if (slot < contents.length) {
                contents[slot++] = item;
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    public void stackItems(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 9; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getAmount() >= item.getMaxStackSize()) continue;

            for (int j = i + 1; j < contents.length; j++) {
                ItemStack other = contents[j];
                if (other == null || other.getType() == Material.AIR) continue;
                if (item.isSimilar(other)) {
                    int space = item.getMaxStackSize() - item.getAmount();
                    if (space <= 0) break;
                    int transfer = Math.min(space, other.getAmount());
                    item.setAmount(item.getAmount() + transfer);
                    other.setAmount(other.getAmount() - transfer);
                    if (other.getAmount() <= 0) contents[j] = null;
                }
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    // ==================== CLEANUP ====================

    public void unloadPlayer(UUID uuid) {
        lastDeathLocations.remove(uuid);
        activeTrails.remove(uuid);
        cooldowns.remove(uuid);
        ArmorStand stand = sittingPlayers.remove(uuid);
        if (stand != null && stand.isValid()) stand.remove();
        flyingPlayers.remove(uuid);
    }
}
