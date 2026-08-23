package com.starlightuniverse.home;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import com.starlightuniverse.world.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HomeManager {

    static final int DEFAULT_HOME_SLOTS = 2;
    static final int MAX_HOME_SLOTS_NONPREMIUM = 5;
    static final double EXTRA_SLOT_MONEY_COST = 10_000;
    static final double EXTRA_SLOT_GEMS_COST = 500;
    static final int DEFAULT_PROTECTION_RADIUS = 25;
    static final double GOLEM_MONEY_COST = 5_000;

    static final int[] EXPAND_RADII = {25, 35, 45, 55, 65, 75};
    static final double[] EXPAND_MONEY_COSTS = {0, 5_000, 10_000, 15_000, 20_000, 30_000};
    static final double[] EXPAND_GEM_COSTS = {0, 100, 200, 300, 400, 500};
    static final double[] EXPAND_STAR_COSTS = {0, 10, 10, 10, 10, 10};

    static final Material[] ICON_OPTIONS = {
            Material.GRASS_BLOCK, Material.STONE, Material.OAK_LOG, Material.BIRCH_LOG,
            Material.COBBLESTONE, Material.BRICKS, Material.DIAMOND_BLOCK, Material.IRON_BLOCK,
            Material.GOLD_BLOCK, Material.EMERALD_BLOCK, Material.LAPIS_BLOCK, Material.REDSTONE_BLOCK,
            Material.NETHERRACK, Material.END_STONE, Material.BOOKSHELF, Material.CRAFTING_TABLE,
            Material.FURNACE, Material.CHEST, Material.RED_BED, Material.CAKE,
            Material.PUMPKIN, Material.MELON, Material.HAY_BLOCK, Material.GLOWSTONE,
            Material.SEA_LANTERN, Material.PRISMARINE, Material.CRYING_OBSIDIAN, Material.AMETHYST_BLOCK
    };

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;
    private final AdminManager adminManager;

    private final Map<String, List<Home>> homeCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> extraSlotsCache = new ConcurrentHashMap<>();
    private final List<Protection> protections = Collections.synchronizedList(new ArrayList<>());
    private final Map<Integer, Map<String, ProtectionLevel>> protMembers = new ConcurrentHashMap<>();
    private final Set<UUID> protectionGolems = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> golemToProtection = new ConcurrentHashMap<>();
    private final Map<UUID, Long> homeCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> addMemberMode = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> addMemberProtId = new ConcurrentHashMap<>();

    public HomeManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy, AdminManager adminManager) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
        this.adminManager = adminManager;
    }

    public void initialize() {
        loadAllProtections();
    }

    public void shutdown() {
        protectionGolems.clear();
        golemToProtection.clear();
    }

    private void loadAllProtections() {
        db.queryAsync(conn -> {
            List<Protection> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM su_protections");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Protection(
                            rs.getInt("id"), rs.getString("owner_username"),
                            rs.getString("world"), rs.getInt("center_x"),
                            rs.getInt("center_z"), rs.getInt("radius")));
                }
            }
            Map<Integer, Map<String, ProtectionLevel>> members = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM su_protection_members");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int protId = rs.getInt("protection_id");
                    members.computeIfAbsent(protId, k -> new HashMap<>())
                            .put(rs.getString("username"), ProtectionLevel.fromLevel(rs.getInt("permission_level")));
                }
            }
            return new Object[]{list, members};
        }).thenAccept(result -> {
            if (result == null) return;
            @SuppressWarnings("unchecked")
            List<Protection> list = (List<Protection>) ((Object[]) result)[0];
            @SuppressWarnings("unchecked")
            Map<Integer, Map<String, ProtectionLevel>> members =
                    (Map<Integer, Map<String, ProtectionLevel>>) ((Object[]) result)[1];
            protections.clear();
            protections.addAll(list);
            protMembers.clear();
            protMembers.putAll(members);
            plugin.getLogger().info("[SU] Loaded " + list.size() + " protections.");
        });
    }

    public void loadPlayerHomes(String username) {
        String lower = username.toLowerCase();
        db.queryAsync(conn -> {
            List<Home> homes = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM su_homes WHERE username = ? ORDER BY home_number")) {
                ps.setString(1, lower);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        homes.add(new Home(rs.getInt("id"), rs.getString("username"),
                                rs.getInt("home_number"), rs.getString("home_name"),
                                rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"),
                                rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"),
                                rs.getString("icon_material")));
                    }
                }
            }
            int extraSlots = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT extra_home_slots FROM su_players WHERE username = ?")) {
                ps.setString(1, lower);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) extraSlots = rs.getInt("extra_home_slots");
                }
            }
            return new Object[]{homes, extraSlots};
        }).thenAccept(result -> {
            if (result == null) return;
            @SuppressWarnings("unchecked")
            List<Home> homes = (List<Home>) ((Object[]) result)[0];
            int extra = (int) ((Object[]) result)[1];
            homeCache.put(lower, Collections.synchronizedList(new ArrayList<>(homes)));
            extraSlotsCache.put(lower, extra);
        });
    }

    public void unloadPlayer(String username) {
        String lower = username.toLowerCase();
        homeCache.remove(lower);
        extraSlotsCache.remove(lower);
    }

    public List<Home> getHomes(String username) {
        List<Home> homes = homeCache.get(username.toLowerCase());
        return homes != null ? homes : List.of();
    }

    public Home getHome(String username, int number) {
        return getHomes(username).stream()
                .filter(h -> h.getNumber() == number)
                .findFirst().orElse(null);
    }

    public int getMaxHomes(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return DEFAULT_HOME_SLOTS;
        String lower = player.getName().toLowerCase();
        int extra = extraSlotsCache.getOrDefault(lower, 0);
        int premiumMax = getPremiumMaxHomes(uuid);
        return Math.max(DEFAULT_HOME_SLOTS + extra, premiumMax);
    }

    private int getPremiumMaxHomes(UUID uuid) {
        int premiumLevel = adminManager.getPremiumLevel(uuid);
        return switch (premiumLevel) {
            case 1 -> 3;   // Meteor
            case 2 -> 5;   // Comet
            case 3 -> 7;   // Nebula
            case 4 -> 10;  // Supernova
            case 5 -> 15;  // Galaxy
            default -> DEFAULT_HOME_SLOTS;
        };
    }

    public int getMaxProtectionRadius(UUID uuid) {
        int premiumLevel = adminManager.getPremiumLevel(uuid);
        return switch (premiumLevel) {
            case 1 -> 75;
            case 2 -> 100;
            case 3 -> 150;
            case 4 -> 200;
            case 5 -> 300;
            default -> 75;
        };
    }

    public boolean canBuyExtraSlot(Player player) {
        String lower = player.getName().toLowerCase();
        int extra = extraSlotsCache.getOrDefault(lower, 0);
        int currentMax = DEFAULT_HOME_SLOTS + extra;
        return currentMax < MAX_HOME_SLOTS_NONPREMIUM;
    }

    public void buyExtraSlot(Player player, String currency) {
        String lower = player.getName().toLowerCase();
        int extra = extraSlotsCache.getOrDefault(lower, 0);
        int currentMax = DEFAULT_HOME_SLOTS + extra;
        if (currentMax >= MAX_HOME_SLOTS_NONPREMIUM) {
            Msg.error(player, "You already have the maximum home slots!");
            return;
        }
        UUID uuid = player.getUniqueId();
        boolean success;
        if ("gems".equalsIgnoreCase(currency)) {
            success = economy.removeGems(uuid, EXTRA_SLOT_GEMS_COST);
            if (!success) { Msg.error(player, "Not enough Gems! Need " + EconomyManager.GEMS_ICON + EconomyManager.format(EXTRA_SLOT_GEMS_COST)); return; }
        } else {
            success = economy.removeMoney(uuid, EXTRA_SLOT_MONEY_COST);
            if (!success) { Msg.error(player, "Not enough Money! Need $" + EconomyManager.format(EXTRA_SLOT_MONEY_COST)); return; }
        }
        int newExtra = extra + 1;
        extraSlotsCache.put(lower, newExtra);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET extra_home_slots = ? WHERE username = ?")) {
                ps.setInt(1, newExtra);
                ps.setString(2, lower);
                ps.executeUpdate();
            }
        });
        Msg.success(player, "Home slot purchased! You now have " + (DEFAULT_HOME_SLOTS + newExtra) + " home slots.");
    }

    public void setHome(Player player, int number) {
        String lower = player.getName().toLowerCase();
        UUID uuid = player.getUniqueId();
        int max = getMaxHomes(uuid);
        if (number < 1 || number > max) {
            Msg.error(player, "Invalid home number! You have " + max + " slots (1-" + max + ").");
            return;
        }

        if (WorldManager.getWorldGroup(player.getWorld()) != WorldManager.WorldGroup.SURVIVAL) {
            Msg.error(player, "You can only set homes in survival worlds!");
            return;
        }

        Location loc = player.getLocation();
        Home existing = getHome(lower, number);

        db.executeAsync(conn -> {
            if (existing != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE su_homes SET world=?, x=?, y=?, z=?, yaw=?, pitch=? WHERE id=?")) {
                    ps.setString(1, loc.getWorld().getName());
                    ps.setDouble(2, loc.getX());
                    ps.setDouble(3, loc.getY());
                    ps.setDouble(4, loc.getZ());
                    ps.setFloat(5, loc.getYaw());
                    ps.setFloat(6, loc.getPitch());
                    ps.setInt(7, existing.getId());
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO su_homes (username, home_number, world, x, y, z, yaw, pitch, icon_material) VALUES (?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, lower);
                    ps.setInt(2, number);
                    ps.setString(3, loc.getWorld().getName());
                    ps.setDouble(4, loc.getX());
                    ps.setDouble(5, loc.getY());
                    ps.setDouble(6, loc.getZ());
                    ps.setFloat(7, loc.getYaw());
                    ps.setFloat(8, loc.getPitch());
                    ps.setString(9, "GRASS_BLOCK");
                    ps.executeUpdate();
                }
            }
        }).thenRun(() -> {
            loadPlayerHomes(lower);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline())
                    Msg.success(player, "Home #" + number + " set at " + loc.getWorld().getName()
                            + " (" + (int) loc.getX() + ", " + (int) loc.getY() + ", " + (int) loc.getZ() + ")");
            });
        });
    }

    public void deleteHome(Player player, int number) {
        String lower = player.getName().toLowerCase();
        Home home = getHome(lower, number);
        if (home == null) { Msg.error(player, "Home #" + number + " does not exist!"); return; }

        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM su_homes WHERE id = ?")) {
                ps.setInt(1, home.getId());
                ps.executeUpdate();
            }
        }).thenRun(() -> {
            List<Home> homes = homeCache.get(lower);
            if (homes != null) homes.removeIf(h -> h.getNumber() == number);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) Msg.success(player, "Home #" + number + " deleted!");
            });
        });
    }

    public void setHomeName(Player player, int number, String name) {
        String lower = player.getName().toLowerCase();
        Home home = getHome(lower, number);
        if (home == null) { Msg.error(player, "Home #" + number + " does not exist!"); return; }
        if (name.length() > 32) { Msg.error(player, "Name too long! Max 32 characters."); return; }

        home.setName(name);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_homes SET home_name = ? WHERE id = ?")) {
                ps.setString(1, name);
                ps.setInt(2, home.getId());
                ps.executeUpdate();
            }
        });
        Msg.success(player, "Home #" + number + " renamed to \"" + name + "\"!");
    }

    public void setHomeIcon(Player player, int number, String material) {
        String lower = player.getName().toLowerCase();
        Home home = getHome(lower, number);
        if (home == null) return;

        home.setIconMaterial(material);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_homes SET icon_material = ? WHERE id = ?")) {
                ps.setString(1, material);
                ps.setInt(2, home.getId());
                ps.executeUpdate();
            }
        });
        Msg.success(player, "Home #" + number + " icon changed!");
    }

    public void teleportHome(Player player, int number) {
        String lower = player.getName().toLowerCase();
        UUID uuid = player.getUniqueId();

        if (WorldManager.getWorldGroup(player.getWorld()) != WorldManager.WorldGroup.SURVIVAL) {
            Msg.error(player, "You can only teleport to homes from survival worlds!");
            return;
        }

        Home home = getHome(lower, number);
        if (home == null) { Msg.error(player, "Home #" + number + " does not exist!"); return; }

        long now = System.currentTimeMillis();
        Long last = homeCooldowns.get(uuid);
        if (last != null && now - last < 3000) {
            Msg.error(player, "Please wait " + ((3000 - (now - last)) / 1000 + 1) + "s before teleporting again!");
            return;
        }
        homeCooldowns.put(uuid, now);

        World world = Bukkit.getWorld(home.getWorld());
        if (world == null) { Msg.error(player, "World not found!"); return; }

        Location loc = new Location(world, home.getX(), home.getY(), home.getZ(), home.getYaw(), home.getPitch());
        player.teleport(loc);
        Msg.success(player, "Teleported to " + home.getDisplayName() + "!");
    }

    public void shareHome(Player player, int number, Player target) {
        String lower = player.getName().toLowerCase();
        Home home = getHome(lower, number);
        if (home == null) { Msg.error(player, "Home #" + number + " does not exist!"); return; }

        Msg.success(player, "Shared " + home.getDisplayName() + " coordinates with " + target.getName() + "!");
        target.sendMessage(Component.text("[SU] ", GOLD)
                .append(Component.text(player.getName(), GREEN))
                .append(Component.text(" shared their home with you: ", YELLOW))
                .append(Component.text(home.getWorld() + " (" + (int) home.getX() + ", "
                        + (int) home.getY() + ", " + (int) home.getZ() + ")", CYAN)));
    }

    // ==================== PROTECTION ====================

    public Protection getProtectionAt(String world, int x, int z) {
        synchronized (protections) {
            for (Protection p : protections) {
                if (p.contains(world, x, z)) return p;
            }
        }
        return null;
    }

    public Protection getPlayerProtection(String username) {
        String lower = username.toLowerCase();
        synchronized (protections) {
            for (Protection p : protections) {
                if (p.getOwner().equals(lower)) return p;
            }
        }
        return null;
    }

    public boolean createProtection(Player player, Location loc) {
        String lower = player.getName().toLowerCase();

        if (getPlayerProtection(lower) != null) {
            Msg.error(player, "You already have a protection! Use /homeprotect to manage it.");
            return false;
        }

        if (WorldManager.getWorldGroup(loc.getWorld()) != WorldManager.WorldGroup.SURVIVAL) {
            Msg.error(player, "You can only create protections in survival worlds!");
            return false;
        }

        int cx = loc.getBlockX();
        int cz = loc.getBlockZ();
        String worldName = loc.getWorld().getName();
        int radius = DEFAULT_PROTECTION_RADIUS;

        Protection temp = new Protection(0, lower, worldName, cx, cz, radius);
        synchronized (protections) {
            for (Protection p : protections) {
                if (p.overlaps(temp)) {
                    Msg.error(player, "This area overlaps with " + p.getOwner() + "'s protection!");
                    return false;
                }
            }
        }

        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_protections (owner_username, world, center_x, center_z, radius) VALUES (?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, lower);
                ps.setString(2, worldName);
                ps.setInt(3, cx);
                ps.setInt(4, cz);
                ps.setInt(5, radius);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
            return -1;
        }).thenAccept(id -> {
            if (id == null || id < 0) return;
            Protection prot = new Protection(id, lower, worldName, cx, cz, radius);
            protections.add(prot);
            addLog(id, lower, "Created protection");
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    Msg.success(player, "Protection created! Area: " + (radius * 2 + 1) + "x" + (radius * 2 + 1)
                            + " centered at (" + cx + ", " + cz + ")");
                    showVisualizer(player, prot);
                }
            });
        });
        return true;
    }

    public boolean canBuild(Player player, Location loc) {
        String world = loc.getWorld().getName();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        Protection prot = getProtectionAt(world, x, z);
        if (prot == null) return true;

        String lower = player.getName().toLowerCase();
        if (prot.getOwner().equals(lower)) return true;
        if (adminManager.getAdminLevel(player.getUniqueId()) >= 3) return true;

        ProtectionLevel level = getMemberLevel(prot.getId(), lower);
        return level.getLevel() >= ProtectionLevel.BUILDER.getLevel();
    }

    public boolean canInteract(Player player, Location loc) {
        String world = loc.getWorld().getName();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        Protection prot = getProtectionAt(world, x, z);
        if (prot == null) return true;

        String lower = player.getName().toLowerCase();
        if (prot.getOwner().equals(lower)) return true;
        if (adminManager.getAdminLevel(player.getUniqueId()) >= 3) return true;

        ProtectionLevel level = getMemberLevel(prot.getId(), lower);
        return level.getLevel() >= ProtectionLevel.FULL.getLevel();
    }

    public ProtectionLevel getMemberLevel(int protId, String username) {
        Map<String, ProtectionLevel> members = protMembers.get(protId);
        if (members == null) return ProtectionLevel.VISITOR;
        return members.getOrDefault(username.toLowerCase(), ProtectionLevel.VISITOR);
    }

    public Map<String, ProtectionLevel> getMembers(int protId) {
        return protMembers.getOrDefault(protId, Map.of());
    }

    public void setMember(int protId, String username, ProtectionLevel level) {
        String lower = username.toLowerCase();
        protMembers.computeIfAbsent(protId, k -> new ConcurrentHashMap<>()).put(lower, level);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_protection_members (protection_id, username, permission_level) VALUES (?,?,?) " +
                            "ON DUPLICATE KEY UPDATE permission_level = ?")) {
                ps.setInt(1, protId);
                ps.setString(2, lower);
                ps.setInt(3, level.getLevel());
                ps.setInt(4, level.getLevel());
                ps.executeUpdate();
            }
        });
        addLog(protId, lower, "Set to " + level.getDisplay());
    }

    public void removeMember(int protId, String username) {
        String lower = username.toLowerCase();
        Map<String, ProtectionLevel> members = protMembers.get(protId);
        if (members != null) members.remove(lower);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM su_protection_members WHERE protection_id = ? AND username = ?")) {
                ps.setInt(1, protId);
                ps.setString(2, lower);
                ps.executeUpdate();
            }
        });
        addLog(protId, lower, "Removed from protection");
    }

    public boolean expandProtection(Player player, int protId, String currency) {
        Protection prot = null;
        synchronized (protections) {
            for (Protection p : protections) {
                if (p.getId() == protId) { prot = p; break; }
            }
        }
        if (prot == null) { Msg.error(player, "Protection not found!"); return false; }

        int currentLevel = prot.getSizeLevel();
        if (currentLevel >= EXPAND_RADII.length - 1) {
            Msg.error(player, "Protection is already at maximum size!");
            return false;
        }

        int maxRadius = getMaxProtectionRadius(player.getUniqueId());
        int nextRadius = EXPAND_RADII[currentLevel + 1];
        if (nextRadius > maxRadius) {
            Msg.error(player, "You need a higher premium rank to expand further!");
            return false;
        }

        int nextLevel = currentLevel + 1;

        Protection tempExpanded = new Protection(protId, prot.getOwner(), prot.getWorld(),
                prot.getCenterX(), prot.getCenterZ(), nextRadius);
        synchronized (protections) {
            for (Protection p : protections) {
                if (p.getId() != protId && p.overlaps(tempExpanded)) {
                    Msg.error(player, "Expansion would overlap with " + p.getOwner() + "'s protection!");
                    return false;
                }
            }
        }

        UUID uuid = player.getUniqueId();
        boolean success;
        String costDisplay;

        switch (currency.toLowerCase()) {
            case "gems" -> {
                double cost = EXPAND_GEM_COSTS[nextLevel];
                success = economy.removeGems(uuid, cost);
                costDisplay = EconomyManager.GEMS_ICON + EconomyManager.format(cost);
                if (!success) { Msg.error(player, "Not enough Gems! Need " + costDisplay); return false; }
            }
            case "stars" -> {
                double cost = EXPAND_STAR_COSTS[nextLevel];
                success = economy.removeStars(uuid, cost);
                costDisplay = EconomyManager.STARS_ICON + EconomyManager.format(cost);
                if (!success) { Msg.error(player, "Not enough Stars! Need " + costDisplay); return false; }
            }
            default -> {
                double cost = EXPAND_MONEY_COSTS[nextLevel];
                success = economy.removeMoney(uuid, cost);
                costDisplay = "$" + EconomyManager.format(cost);
                if (!success) { Msg.error(player, "Not enough Money! Need " + costDisplay); return false; }
            }
        }

        prot.setRadius(nextRadius);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_protections SET radius = ? WHERE id = ?")) {
                ps.setInt(1, nextRadius);
                ps.setInt(2, protId);
                ps.executeUpdate();
            }
        });

        int size = nextRadius * 2 + 1;
        Msg.success(player, "Protection expanded to " + size + "x" + size + " for " + costDisplay + "!");
        addLog(protId, player.getName().toLowerCase(), "Expanded to " + size + "x" + size);
        showVisualizer(player, prot);
        return true;
    }

    public void deleteProtection(Player player) {
        String lower = player.getName().toLowerCase();
        Protection prot = getPlayerProtection(lower);
        if (prot == null) { Msg.error(player, "You don't have a protection!"); return; }

        int protId = prot.getId();
        protections.remove(prot);
        protMembers.remove(protId);
        golemToProtection.entrySet().removeIf(e -> e.getValue() == protId);

        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM su_protections WHERE id = ?")) {
                ps.setInt(1, protId);
                ps.executeUpdate();
            }
        });
        Msg.success(player, "Protection deleted!");
    }

    public void addLog(int protId, String username, String action) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_protection_logs (protection_id, username, action) VALUES (?,?,?)")) {
                ps.setInt(1, protId);
                ps.setString(2, username.toLowerCase());
                ps.setString(3, action);
                ps.executeUpdate();
            }
        });
    }

    public void showLogs(Player player, int protId) {
        db.queryAsync(conn -> {
            List<String> logs = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT username, action, log_date FROM su_protection_logs WHERE protection_id = ? ORDER BY log_date DESC LIMIT 20")) {
                ps.setInt(1, protId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        logs.add(rs.getString("log_date") + " - " + rs.getString("username") + ": " + rs.getString("action"));
                    }
                }
            }
            return logs;
        }).thenAccept(logs -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || logs == null) return;
            if (logs.isEmpty()) { Msg.info(player, "No protection logs yet."); return; }
            player.sendMessage(Component.text("[SU] Protection Logs:", GOLD));
            for (String log : logs) {
                player.sendMessage(Component.text("  " + log, GRAY));
            }
        }));
    }

    public void showVisualizer(Player player, Protection prot) {
        World world = Bukkit.getWorld(prot.getWorld());
        if (world == null) return;

        int minX = prot.getMinX();
        int maxX = prot.getMaxX();
        int minZ = prot.getMinZ();
        int maxZ = prot.getMaxZ();
        int y = player.getLocation().getBlockY();

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0x55, 0xFF, 0x55), 1.0f);
        final int[] ticksLeft = {200};

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            ticksLeft[0] -= 10;
            if (!player.isOnline() || ticksLeft[0] <= 0) { task.cancel(); return; }
            for (int x = minX; x <= maxX; x += 2) {
                world.spawnParticle(Particle.DUST, x + 0.5, y + 0.5, minZ + 0.5, 1, dust);
                world.spawnParticle(Particle.DUST, x + 0.5, y + 0.5, maxZ + 0.5, 1, dust);
            }
            for (int z = minZ; z <= maxZ; z += 2) {
                world.spawnParticle(Particle.DUST, minX + 0.5, y + 0.5, z + 0.5, 1, dust);
                world.spawnParticle(Particle.DUST, maxX + 0.5, y + 0.5, z + 0.5, 1, dust);
            }
        }, 0L, 10L);
    }

    // ==================== GOLEMS ====================

    public void spawnGolem(Player player, Protection prot) {
        UUID uuid = player.getUniqueId();
        if (!economy.removeMoney(uuid, GOLEM_MONEY_COST)) {
            Msg.error(player, "Not enough Money! Need $" + EconomyManager.format(GOLEM_MONEY_COST));
            return;
        }

        World world = Bukkit.getWorld(prot.getWorld());
        if (world == null) { Msg.error(player, "World not found!"); return; }

        Location spawnLoc = player.getLocation().add(2, 0, 0);
        IronGolem golem = (IronGolem) world.spawnEntity(spawnLoc, EntityType.IRON_GOLEM);
        golem.setPlayerCreated(true);
        golem.customName(Component.text("Guardian Golem", GREEN).decoration(TextDecoration.ITALIC, false));
        golem.setCustomNameVisible(true);

        UUID golemUuid = golem.getUniqueId();
        protectionGolems.add(golemUuid);
        golemToProtection.put(golemUuid, prot.getId());

        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_golems (protection_id, golem_uuid) VALUES (?,?)")) {
                ps.setInt(1, prot.getId());
                ps.setString(2, golemUuid.toString());
                ps.executeUpdate();
            }
        });

        Msg.success(player, "Guardian Golem spawned! Cost: $" + EconomyManager.format(GOLEM_MONEY_COST));
        addLog(prot.getId(), player.getName().toLowerCase(), "Spawned Guardian Golem");
    }

    public boolean isProtectionGolem(UUID entityUuid) {
        return protectionGolems.contains(entityUuid);
    }

    public Integer getGolemProtectionId(UUID entityUuid) {
        return golemToProtection.get(entityUuid);
    }

    public Protection getProtectionById(int id) {
        synchronized (protections) {
            for (Protection p : protections) {
                if (p.getId() == id) return p;
            }
        }
        return null;
    }

    public void loadGolems() {
        db.queryAsync(conn -> {
            Map<UUID, Integer> map = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM su_golems");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID uuid = UUID.fromString(rs.getString("golem_uuid"));
                        map.put(uuid, rs.getInt("protection_id"));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            return map;
        }).thenAccept(map -> {
            if (map == null) return;
            protectionGolems.addAll(map.keySet());
            golemToProtection.putAll(map);
            plugin.getLogger().info("[SU] Loaded " + map.size() + " protection golems.");
        });
    }

    public void removeGolem(UUID golemUuid) {
        protectionGolems.remove(golemUuid);
        golemToProtection.remove(golemUuid);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM su_golems WHERE golem_uuid = ?")) {
                ps.setString(1, golemUuid.toString());
                ps.executeUpdate();
            }
        });
    }

    // ==================== GUIs ====================

    public void openHomesGui(Player player) {
        String lower = player.getName().toLowerCase();
        int max = getMaxHomes(player.getUniqueId());
        List<Home> homes = getHomes(lower);

        HomeHolder holder = new HomeHolder(HomeHolder.Type.HOMES_LIST);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Homes", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        for (int i = 0; i < max && i < 9; i++) {
            int num = i + 1;
            Home home = homes.stream().filter(h -> h.getNumber() == num).findFirst().orElse(null);
            if (home != null) {
                Material icon;
                try { icon = Material.valueOf(home.getIconMaterial()); }
                catch (Exception e) { icon = Material.GRASS_BLOCK; }
                ItemStack item = new ItemStack(icon);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(Component.text(home.getDisplayName(), GREEN).decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(
                        Component.text(home.getWorld() + " (" + (int) home.getX() + ", "
                                + (int) home.getY() + ", " + (int) home.getZ() + ")", GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Left-click to teleport", YELLOW).decoration(TextDecoration.ITALIC, false),
                        Component.text("Right-click to manage", YELLOW).decoration(TextDecoration.ITALIC, false)
                ));
                item.setItemMeta(meta);
                inv.setItem(i, item);
            } else {
                ItemStack empty = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = empty.getItemMeta();
                meta.displayName(Component.text("Empty Slot #" + num, GRAY).decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(Component.text("Use /sethome " + num + " to set", GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
                empty.setItemMeta(meta);
                inv.setItem(i, empty);
            }
        }

        if (canBuyExtraSlot(player)) {
            ItemStack buy = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta meta = buy.getItemMeta();
            meta.displayName(Component.text("Buy Extra Home Slot", GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("$" + EconomyManager.format(EXTRA_SLOT_MONEY_COST) + " or "
                            + EconomyManager.GEMS_ICON + EconomyManager.format(EXTRA_SLOT_GEMS_COST), YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            buy.setItemMeta(meta);
            inv.setItem(22, buy);
        }

        player.openInventory(inv);
    }

    public void openManageGui(Player player, int homeNumber) {
        String lower = player.getName().toLowerCase();
        Home home = getHome(lower, homeNumber);
        if (home == null) { Msg.error(player, "Home not found!"); return; }

        HomeHolder holder = new HomeHolder(HomeHolder.Type.HOME_MANAGE);
        holder.setSelectedHome(homeNumber);
        Inventory inv = Bukkit.createInventory(holder, 9,
                Component.text("Manage: " + home.getDisplayName(), GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text(home.getDisplayName(), GREEN).decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(List.of(
                Component.text("World: " + home.getWorld(), GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Coords: " + (int) home.getX() + ", " + (int) home.getY() + ", " + (int) home.getZ(), GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(0, info);

        ItemStack rename = new ItemStack(Material.NAME_TAG);
        ItemMeta renameMeta = rename.getItemMeta();
        renameMeta.displayName(Component.text("Rename", YELLOW).decoration(TextDecoration.ITALIC, false));
        renameMeta.lore(List.of(Component.text("Use /sethomename " + homeNumber + " <name>", GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        rename.setItemMeta(renameMeta);
        inv.setItem(2, rename);

        ItemStack icon = new ItemStack(Material.PAINTING);
        ItemMeta iconMeta = icon.getItemMeta();
        iconMeta.displayName(Component.text("Change Icon", YELLOW).decoration(TextDecoration.ITALIC, false));
        iconMeta.lore(List.of(Component.text("Click to select a new icon", GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        icon.setItemMeta(iconMeta);
        inv.setItem(3, icon);

        ItemStack share = new ItemStack(Material.COMPASS);
        ItemMeta shareMeta = share.getItemMeta();
        shareMeta.displayName(Component.text("Share", YELLOW).decoration(TextDecoration.ITALIC, false));
        shareMeta.lore(List.of(Component.text("Use /sharehome " + homeNumber + " <player>", GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        share.setItemMeta(shareMeta);
        inv.setItem(4, share);

        ItemStack delete = new ItemStack(Material.BARRIER);
        ItemMeta deleteMeta = delete.getItemMeta();
        deleteMeta.displayName(Component.text("Delete Home", RED).decoration(TextDecoration.ITALIC, false));
        deleteMeta.lore(List.of(Component.text("Click to delete this home", GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        delete.setItemMeta(deleteMeta);
        inv.setItem(6, delete);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back", GRAY).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inv.setItem(8, back);

        player.openInventory(inv);
    }

    public void openIconSelectGui(Player player, int homeNumber) {
        HomeHolder holder = new HomeHolder(HomeHolder.Type.ICON_SELECT);
        holder.setSelectedHome(homeNumber);
        Inventory inv = Bukkit.createInventory(holder, 36,
                Component.text("Select Icon", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        for (int i = 0; i < ICON_OPTIONS.length && i < 28; i++) {
            ItemStack item = new ItemStack(ICON_OPTIONS[i]);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(formatMaterial(ICON_OPTIONS[i].name()), YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Click to select", GRAY).decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back", GRAY).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inv.setItem(35, back);

        player.openInventory(inv);
    }

    public void openBuySlotGui(Player player) {
        HomeHolder holder = new HomeHolder(HomeHolder.Type.BUY_HOME_SLOT);
        Inventory inv = Bukkit.createInventory(holder, 9,
                Component.text("Buy Home Slot", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        ItemStack money = new ItemStack(Material.GOLD_INGOT);
        ItemMeta moneyMeta = money.getItemMeta();
        moneyMeta.displayName(Component.text("Buy with Money", GREEN).decoration(TextDecoration.ITALIC, false));
        moneyMeta.lore(List.of(Component.text("Cost: $" + EconomyManager.format(EXTRA_SLOT_MONEY_COST), YELLOW)
                .decoration(TextDecoration.ITALIC, false)));
        money.setItemMeta(moneyMeta);
        inv.setItem(2, money);

        ItemStack gems = new ItemStack(Material.DIAMOND);
        ItemMeta gemsMeta = gems.getItemMeta();
        gemsMeta.displayName(Component.text("Buy with Gems", CYAN).decoration(TextDecoration.ITALIC, false));
        gemsMeta.lore(List.of(Component.text("Cost: " + EconomyManager.GEMS_ICON + EconomyManager.format(EXTRA_SLOT_GEMS_COST), YELLOW)
                .decoration(TextDecoration.ITALIC, false)));
        gems.setItemMeta(gemsMeta);
        inv.setItem(6, gems);

        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(Component.text("Cancel", RED).decoration(TextDecoration.ITALIC, false));
        cancel.setItemMeta(cancelMeta);
        inv.setItem(4, cancel);

        player.openInventory(inv);
    }

    public void openProtectGui(Player player) {
        String lower = player.getName().toLowerCase();
        Protection prot = getPlayerProtection(lower);
        if (prot == null) {
            Msg.error(player, "You don't have a protection! Right-click with a Golden Shovel to create one.");
            return;
        }

        HomeHolder holder = new HomeHolder(HomeHolder.Type.PROTECT_MAIN);
        holder.setProtectionId(prot.getId());
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Home Protection", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        int size = prot.getRadius() * 2 + 1;
        ItemStack info = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Protection Info", GREEN).decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(List.of(
                Component.text("World: " + prot.getWorld(), GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Center: " + prot.getCenterX() + ", " + prot.getCenterZ(), GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Size: " + size + "x" + size, GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Radius: " + prot.getRadius() + " blocks", GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(10, info);

        ItemStack members = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta membersMeta = members.getItemMeta();
        membersMeta.displayName(Component.text("Members", CYAN).decoration(TextDecoration.ITALIC, false));
        membersMeta.lore(List.of(
                Component.text(getMembers(prot.getId()).size() + " members", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Click to manage permissions", YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        members.setItemMeta(membersMeta);
        inv.setItem(12, members);

        ItemStack expand = new ItemStack(Material.SLIME_BALL);
        ItemMeta expandMeta = expand.getItemMeta();
        expandMeta.displayName(Component.text("Expand", YELLOW).decoration(TextDecoration.ITALIC, false));
        int currentLevel = prot.getSizeLevel();
        if (currentLevel < EXPAND_RADII.length - 1) {
            int nextSize = EXPAND_RADII[currentLevel + 1] * 2 + 1;
            expandMeta.lore(List.of(
                    Component.text("Next: " + nextSize + "x" + nextSize, GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Click to see costs", YELLOW).decoration(TextDecoration.ITALIC, false)
            ));
        } else {
            expandMeta.lore(List.of(Component.text("Maximum size reached!", GREEN).decoration(TextDecoration.ITALIC, false)));
        }
        expand.setItemMeta(expandMeta);
        inv.setItem(14, expand);

        ItemStack golem = new ItemStack(Material.IRON_BLOCK);
        ItemMeta golemMeta = golem.getItemMeta();
        golemMeta.displayName(Component.text("Spawn Guardian Golem", GREEN).decoration(TextDecoration.ITALIC, false));
        golemMeta.lore(List.of(
                Component.text("Cost: $" + EconomyManager.format(GOLEM_MONEY_COST), YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("Golems attack intruders inside", GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        golem.setItemMeta(golemMeta);
        inv.setItem(20, golem);

        ItemStack logs = new ItemStack(Material.BOOK);
        ItemMeta logsMeta = logs.getItemMeta();
        logsMeta.displayName(Component.text("View Logs", YELLOW).decoration(TextDecoration.ITALIC, false));
        logsMeta.lore(List.of(Component.text("Recent protection activity", GRAY).decoration(TextDecoration.ITALIC, false)));
        logs.setItemMeta(logsMeta);
        inv.setItem(22, logs);

        ItemStack visualize = new ItemStack(Material.ENDER_EYE);
        ItemMeta vizMeta = visualize.getItemMeta();
        vizMeta.displayName(Component.text("Visualize Boundary", CYAN).decoration(TextDecoration.ITALIC, false));
        vizMeta.lore(List.of(Component.text("Show protection border for 10s", GRAY).decoration(TextDecoration.ITALIC, false)));
        visualize.setItemMeta(vizMeta);
        inv.setItem(24, visualize);

        ItemStack delete = new ItemStack(Material.BARRIER);
        ItemMeta delMeta = delete.getItemMeta();
        delMeta.displayName(Component.text("Delete Protection", RED).decoration(TextDecoration.ITALIC, false));
        delMeta.lore(List.of(Component.text("Permanently remove your protection", GRAY).decoration(TextDecoration.ITALIC, false)));
        delete.setItemMeta(delMeta);
        inv.setItem(16, delete);

        player.openInventory(inv);
    }

    public void openMembersGui(Player player, int protId) {
        HomeHolder holder = new HomeHolder(HomeHolder.Type.PROTECT_MEMBERS);
        holder.setProtectionId(protId);
        Map<String, ProtectionLevel> members = getMembers(protId);
        int rows = Math.max(3, (int) Math.ceil((members.size() + 1) / 9.0) + 1);
        rows = Math.min(rows, 6);
        Inventory inv = Bukkit.createInventory(holder, rows * 9,
                Component.text("Protection Members", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        int slot = 0;
        for (Map.Entry<String, ProtectionLevel> entry : members.entrySet()) {
            if (slot >= (rows - 1) * 9) break;
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(entry.getKey(), entry.getValue().getColor())
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Level: " + entry.getValue().getDisplay(), GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Left-click: cycle permission", YELLOW).decoration(TextDecoration.ITALIC, false),
                    Component.text("Shift-right-click: remove", RED).decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        ItemStack add = new ItemStack(Material.LIME_DYE);
        ItemMeta addMeta = add.getItemMeta();
        addMeta.displayName(Component.text("Add Member", GREEN).decoration(TextDecoration.ITALIC, false));
        addMeta.lore(List.of(Component.text("Click, then type a player name in chat", GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        add.setItemMeta(addMeta);
        inv.setItem((rows - 1) * 9, add);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back", GRAY).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inv.setItem((rows - 1) * 9 + 8, back);

        player.openInventory(inv);
    }

    public void openExpandGui(Player player, int protId) {
        Protection prot = getProtectionById(protId);
        if (prot == null) return;

        HomeHolder holder = new HomeHolder(HomeHolder.Type.PROTECT_EXPAND);
        holder.setProtectionId(protId);
        Inventory inv = Bukkit.createInventory(holder, 9,
                Component.text("Expand Protection", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        int currentLevel = prot.getSizeLevel();
        if (currentLevel >= EXPAND_RADII.length - 1) {
            ItemStack maxed = new ItemStack(Material.BARRIER);
            ItemMeta meta = maxed.getItemMeta();
            meta.displayName(Component.text("Maximum Size Reached!", RED).decoration(TextDecoration.ITALIC, false));
            maxed.setItemMeta(meta);
            inv.setItem(4, maxed);
        } else {
            int nextLevel = currentLevel + 1;
            int nextSize = EXPAND_RADII[nextLevel] * 2 + 1;

            ItemStack moneyBtn = new ItemStack(Material.GOLD_INGOT);
            ItemMeta moneyMeta = moneyBtn.getItemMeta();
            moneyMeta.displayName(Component.text("Expand with Money", GREEN).decoration(TextDecoration.ITALIC, false));
            moneyMeta.lore(List.of(
                    Component.text("Cost: $" + EconomyManager.format(EXPAND_MONEY_COSTS[nextLevel]), YELLOW).decoration(TextDecoration.ITALIC, false),
                    Component.text("New size: " + nextSize + "x" + nextSize, GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            moneyBtn.setItemMeta(moneyMeta);
            inv.setItem(1, moneyBtn);

            ItemStack gemsBtn = new ItemStack(Material.DIAMOND);
            ItemMeta gemsMeta = gemsBtn.getItemMeta();
            gemsMeta.displayName(Component.text("Expand with Gems", CYAN).decoration(TextDecoration.ITALIC, false));
            gemsMeta.lore(List.of(
                    Component.text("Cost: " + EconomyManager.GEMS_ICON + EconomyManager.format(EXPAND_GEM_COSTS[nextLevel]), YELLOW).decoration(TextDecoration.ITALIC, false),
                    Component.text("New size: " + nextSize + "x" + nextSize, GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            gemsBtn.setItemMeta(gemsMeta);
            inv.setItem(4, gemsBtn);

            ItemStack starsBtn = new ItemStack(Material.NETHER_STAR);
            ItemMeta starsMeta = starsBtn.getItemMeta();
            starsMeta.displayName(Component.text("Expand with Stars", TextColor.color(0xAA00AA)).decoration(TextDecoration.ITALIC, false));
            starsMeta.lore(List.of(
                    Component.text("Cost: " + EconomyManager.STARS_ICON + EconomyManager.format(EXPAND_STAR_COSTS[nextLevel]), YELLOW).decoration(TextDecoration.ITALIC, false),
                    Component.text("New size: " + nextSize + "x" + nextSize, GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            starsBtn.setItemMeta(starsMeta);
            inv.setItem(7, starsBtn);
        }

        player.openInventory(inv);
    }

    // ==================== ADD MEMBER MODE ====================

    public boolean isInAddMemberMode(UUID uuid) { return addMemberMode.contains(uuid); }

    public void startAddMemberMode(UUID uuid, int protId) {
        addMemberMode.add(uuid);
        addMemberProtId.put(uuid, protId);
    }

    public void endAddMemberMode(UUID uuid) {
        addMemberMode.remove(uuid);
        addMemberProtId.remove(uuid);
    }

    public int getAddMemberProtId(UUID uuid) {
        return addMemberProtId.getOrDefault(uuid, -1);
    }

    // ==================== UTILITY ====================

    private String formatMaterial(String name) {
        return name.replace('_', ' ').toLowerCase();
    }

    public EconomyManager getEconomy() { return economy; }
    public JavaPlugin getPlugin() { return plugin; }
}
