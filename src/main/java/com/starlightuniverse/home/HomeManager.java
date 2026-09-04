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
import org.bukkit.scheduler.BukkitTask;

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
    static final double GOLEM_MONEY_COST = 5_000;

    static final int DEFAULT_PROTECTION_BLOCKS = 1_000;
    static final int[] BLOCK_PURCHASE_AMOUNTS = {100, 500, 1_000, 5_000};
    static final double[] BLOCK_PURCHASE_COSTS = {2_500, 10_000, 18_000, 75_000};

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
    private final Map<String, List<Home>> homesByColumn = new ConcurrentHashMap<>();
    private final List<Protection> protections = Collections.synchronizedList(new ArrayList<>());
    private final Map<Integer, Map<String, ProtectionLevel>> protMembers = new ConcurrentHashMap<>();
    private final Set<UUID> protectionGolems = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> golemToProtection = new ConcurrentHashMap<>();
    private final Map<UUID, Long> homeCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> addMemberMode = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> addMemberProtId = new ConcurrentHashMap<>();

    private final Map<String, Integer> blockBudgetCache = new ConcurrentHashMap<>();
    private final Map<UUID, Location> selectionCornerA = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> hudTasks = new ConcurrentHashMap<>();

    public HomeManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy, AdminManager adminManager) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
        this.adminManager = adminManager;
    }

    public void initialize() {
        loadAllProtections();
        loadAllHomeColumns();
    }

    private void loadAllHomeColumns() {
        db.queryAsync(conn -> {
            List<Home> all = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM su_homes");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    all.add(new Home(rs.getInt("id"), rs.getString("username"),
                            rs.getInt("home_number"), rs.getString("home_name"),
                            rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"),
                            rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"),
                            rs.getString("icon_material")));
                }
            }
            return all;
        }).thenAccept(all -> {
            if (all == null) return;
            homesByColumn.clear();
            for (Home h : all) addToColumnIndex(h);
            plugin.getLogger().info("[SU] Indexed " + all.size() + " home spawn columns.");
        });
    }

    private String columnKey(String world, int blockX, int blockZ) {
        return world + "|" + blockX + "|" + blockZ;
    }

    private void addToColumnIndex(Home home) {
        String key = columnKey(home.getWorld(),
                (int) Math.floor(home.getX()), (int) Math.floor(home.getZ()));
        homesByColumn.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(home);
    }

    private void removeFromColumnIndex(String world, double x, double z, int homeId) {
        String key = columnKey(world, (int) Math.floor(x), (int) Math.floor(z));
        List<Home> list = homesByColumn.get(key);
        if (list == null) return;
        list.removeIf(h -> h.getId() == homeId);
        if (list.isEmpty()) homesByColumn.remove(key);
    }

    public boolean canPlaceInHomeSpawnColumn(Player player, Location loc) {
        String world = loc.getWorld().getName();
        int bx = loc.getBlockX();
        int bz = loc.getBlockZ();
        List<Home> homes = homesByColumn.get(columnKey(world, bx, bz));
        if (homes == null || homes.isEmpty()) return true;

        String lower = player.getName().toLowerCase();
        if (adminManager.getAdminLevel(player.getUniqueId()) >= 3) return true;

        synchronized (homes) {
            for (Home h : homes) {
                if (h.getUsername().equalsIgnoreCase(lower)) continue;
                Protection prot = getProtectionAt(h.getWorld(),
                        (int) Math.floor(h.getX()), (int) Math.floor(h.getZ()));
                if (prot == null) return false;
                ProtectionLevel level = getMemberLevel(prot.getId(), lower);
                if (prot.getOwner().equalsIgnoreCase(lower)) continue;
                if (level.getLevel() < ProtectionLevel.BUILDER.getLevel()) return false;
            }
        }
        return true;
    }

    public void shutdown() {
        hudTasks.values().forEach(BukkitTask::cancel);
        hudTasks.clear();
        selectionCornerA.clear();
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
                            rs.getString("world"), rs.getInt("min_x"),
                            rs.getInt("min_z"), rs.getInt("max_x"),
                            rs.getInt("max_z")));
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
            int blocks = DEFAULT_PROTECTION_BLOCKS;
            boolean giveDaily = false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT extra_home_slots, protection_blocks, daily_blocks_date FROM su_players WHERE username = ?")) {
                ps.setString(1, lower);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        extraSlots = rs.getInt("extra_home_slots");
                        blocks = rs.getInt("protection_blocks");
                        if (blocks <= 0) blocks = DEFAULT_PROTECTION_BLOCKS;
                        java.sql.Date lastDate = rs.getDate("daily_blocks_date");
                        java.sql.Date today = java.sql.Date.valueOf(java.time.LocalDate.now());
                        giveDaily = (lastDate == null || lastDate.before(today));
                    }
                }
            }
            if (giveDaily) {
                blocks += 5;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE su_players SET protection_blocks = ?, daily_blocks_date = CURDATE() WHERE username = ?")) {
                    ps.setInt(1, blocks);
                    ps.setString(2, lower);
                    ps.executeUpdate();
                }
            }
            return new Object[]{homes, extraSlots, blocks, giveDaily};
        }).thenAccept(result -> {
            if (result == null) return;
            @SuppressWarnings("unchecked")
            List<Home> homes = (List<Home>) ((Object[]) result)[0];
            int extra = (int) ((Object[]) result)[1];
            int blocks = (int) ((Object[]) result)[2];
            boolean gaveDaily = (boolean) ((Object[]) result)[3];
            homeCache.put(lower, Collections.synchronizedList(new ArrayList<>(homes)));
            extraSlotsCache.put(lower, extra);
            blockBudgetCache.put(lower, blocks);
            if (gaveDaily) {
                final int finalBlocks = blocks;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().equals(lower)) {
                            Msg.info(p, "+5 daily protection blocks! Budget: " + String.format("%,d", finalBlocks));
                            break;
                        }
                    }
                });
            }
        });
    }

    public void unloadPlayer(String username) {
        String lower = username.toLowerCase();
        homeCache.remove(lower);
        extraSlotsCache.remove(lower);
        blockBudgetCache.remove(lower);
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
        if (premiumMax < 0) return Integer.MAX_VALUE;
        return Math.max(DEFAULT_HOME_SLOTS + extra, premiumMax);
    }

    private int getPremiumMaxHomes(UUID uuid) {
        int premiumLevel = adminManager.getPremiumLevel(uuid);
        return switch (premiumLevel) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 10;
            case 4 -> 20;
            case 5 -> 40;
            case 6 -> -1;
            default -> DEFAULT_HOME_SLOTS;
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
            if (!success) { Msg.error(player, "Not enough Money! Need " + EconomyManager.MONEY_ICON + " $" + EconomyManager.format(EXTRA_SLOT_MONEY_COST)); return; }
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
        String oldWorld = existing != null ? existing.getWorld() : null;
        double oldX = existing != null ? existing.getX() : 0;
        double oldZ = existing != null ? existing.getZ() : 0;
        int oldId = existing != null ? existing.getId() : -1;

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
            if (existing != null && oldWorld != null) {
                removeFromColumnIndex(oldWorld, oldX, oldZ, oldId);
            }
            loadPlayerHomes(lower);
            db.queryAsync(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM su_homes WHERE username=? AND home_number=?")) {
                    ps.setString(1, lower);
                    ps.setInt(2, number);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return new Home(rs.getInt("id"), rs.getString("username"),
                                    rs.getInt("home_number"), rs.getString("home_name"),
                                    rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"),
                                    rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"),
                                    rs.getString("icon_material"));
                        }
                    }
                }
                return null;
            }).thenAccept(fresh -> {
                if (fresh != null) addToColumnIndex(fresh);
            });
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
            removeFromColumnIndex(home.getWorld(), home.getX(), home.getZ(), home.getId());
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

        World world = WorldManager.findWorld(home.getWorld());
        if (world == null) { Msg.error(player, "World not found!"); return; }

        long now = System.currentTimeMillis();
        Long last = homeCooldowns.get(uuid);
        if (last != null && now - last < 3000) {
            Msg.error(player, "Please wait " + ((3000 - (now - last)) / 1000 + 1) + "s before teleporting again!");
            return;
        }
        homeCooldowns.put(uuid, now);

        Location loc = new Location(world, home.getX(), home.getY(), home.getZ(), home.getYaw(), home.getPitch());

        for (org.bukkit.entity.Entity passenger : new ArrayList<>(player.getPassengers())) {
            if (passenger instanceof org.bukkit.entity.TextDisplay) {
                player.removePassenger(passenger);
                passenger.remove();
            }
        }
        if (player.isInsideVehicle()) player.leaveVehicle();

        Runnable doTeleport = () -> {
            if (!player.isOnline()) return;
            player.setFallDistance(0);
            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            player.setFireTicks(0);
            player.setNoDamageTicks(40);
            boolean success = player.teleport(loc,
                    org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.COMMAND);
            if (success) {
                player.setFallDistance(0);
                player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                player.setNoDamageTicks(40);
                Msg.success(player, "Teleported to " + home.getDisplayName() + "!");
                player.playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            } else {
                Msg.error(player, "Teleport was cancelled!");
                homeCooldowns.remove(uuid);
            }
        };

        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        if (world.isChunkLoaded(cx, cz)) {
            doTeleport.run();
        } else {
            world.getChunkAtAsync(cx, cz, true)
                    .thenAccept(chunk -> Bukkit.getScheduler().runTask(plugin, doTeleport))
                    .exceptionally(ex -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                Msg.error(player, "Teleport failed: " + ex.getClass().getSimpleName());
                            }
                            homeCooldowns.remove(uuid);
                        });
                        return null;
                    });
        }
    }

    public void shareHome(Player player, int number, Player target) {
        String lower = player.getName().toLowerCase();
        Home home = getHome(lower, number);
        if (home == null) { Msg.error(player, "Home #" + number + " does not exist!"); return; }

        Msg.success(player, "Shared " + home.getDisplayName() + " coordinates with " + target.getName() + "!");
        target.sendMessage(Component.text("[SU] ", GOLD)
                .append(Component.text(player.getName(), GREEN))
                .append(Component.text(" shared their home with you: ", YELLOW))
                .append(Component.text(worldDisplayName(home.getWorld()) + " ("
                        + (int) home.getX() + " | " + (int) home.getY() + " | "
                        + (int) home.getZ() + ")", CYAN)));
    }

    private static String worldDisplayName(String worldName) {
        String stripped = worldName == null ? "" : worldName;
        int colon = stripped.indexOf(':');
        if (colon >= 0) stripped = stripped.substring(colon + 1);
        return switch (stripped) {
            case "world", WorldManager.OVERWORLD -> "Overworld";
            case "world_nether", "the_nether", "nether", WorldManager.WORLD_NETHER -> "Nether";
            case "world_the_end", "the_end", "end" -> "End";
            case WorldManager.RESOURCE_OVERWORLD -> "Resource Overworld";
            case WorldManager.RESOURCE_NETHER -> "Resource Nether";
            case WorldManager.RESOURCE_END -> "Resource End";
            default -> stripped;
        };
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

    // ── Block Budget ──

    public int getPlayerBlockBudget(String username) {
        return blockBudgetCache.getOrDefault(username.toLowerCase(), DEFAULT_PROTECTION_BLOCKS);
    }

    public void addProtectionBlocks(String username, int blocks) {
        String lower = username.toLowerCase();
        int current = blockBudgetCache.getOrDefault(lower, DEFAULT_PROTECTION_BLOCKS);
        int newBudget = current + blocks;
        blockBudgetCache.put(lower, newBudget);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET protection_blocks = ? WHERE username = ?")) {
                ps.setInt(1, newBudget);
                ps.setString(2, lower);
                ps.executeUpdate();
            }
        });
    }

    public void purchaseBlocks(Player player, int index) {
        if (index < 0 || index >= BLOCK_PURCHASE_AMOUNTS.length) return;
        int blocks = BLOCK_PURCHASE_AMOUNTS[index];
        double cost = BLOCK_PURCHASE_COSTS[index];
        UUID uuid = player.getUniqueId();
        if (!economy.removeMoney(uuid, cost)) {
            Msg.error(player, "Not enough money! Need " + EconomyManager.MONEY_ICON + " $" + EconomyManager.format(cost));
            return;
        }
        String lower = player.getName().toLowerCase();
        addProtectionBlocks(lower, blocks);
        int newBudget = blockBudgetCache.getOrDefault(lower, DEFAULT_PROTECTION_BLOCKS);
        Msg.success(player, "Purchased +" + String.format("%,d", blocks) + " protection blocks! Budget: " + String.format("%,d", newBudget));
    }

    // ── Selection System (two-corner) ──

    public boolean hasCornerA(UUID uuid) {
        return selectionCornerA.containsKey(uuid);
    }

    public void setCornerA(Player player, Location loc) {
        UUID uuid = player.getUniqueId();
        selectionCornerA.put(uuid, loc);
        startHudTask(player);
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        Msg.info(player, "Corner A set at (" + x + ", " + z + "). Right-click another block to set Corner B.");
    }

    public void cancelSelection(UUID uuid) {
        selectionCornerA.remove(uuid);
        BukkitTask task = hudTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private void startHudTask(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask old = hudTasks.remove(uuid);
        if (old != null) old.cancel();
        long startTime = System.currentTimeMillis();
        BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) { cancelSelection(uuid); cancel(); return; }
                if (System.currentTimeMillis() - startTime > 60_000) {
                    Msg.error(player, "Protection selection timed out!");
                    cancelSelection(uuid);
                    cancel();
                    return;
                }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() != Material.GOLDEN_SHOVEL) {
                    cancelSelection(uuid);
                    cancel();
                    return;
                }
                Location cornerA = selectionCornerA.get(uuid);
                if (cornerA == null) { cancel(); return; }

                Location current = player.getLocation();
                int ax = cornerA.getBlockX();
                int az = cornerA.getBlockZ();
                int bx = current.getBlockX();
                int bz = current.getBlockZ();

                int w = Math.abs(bx - ax) + 1;
                int l = Math.abs(bz - az) + 1;
                int area = w * l;
                int budget = getPlayerBlockBudget(player.getName().toLowerCase());

                TextColor color = area <= budget ? GREEN : RED;
                player.sendActionBar(Component.text("Protection: " + w + " x " + l + " | "
                        + String.format("%,d", area) + " / " + String.format("%,d", budget) + " blocks", color));
            }
        }.runTaskTimer(plugin, 0L, 5L);
        hudTasks.put(uuid, task);
    }

    public boolean tryCreateProtection(Player player, Location locB) {
        UUID uuid = player.getUniqueId();
        Location locA = selectionCornerA.remove(uuid);
        BukkitTask task = hudTasks.remove(uuid);
        if (task != null) task.cancel();

        if (locA == null) return false;
        String lower = player.getName().toLowerCase();

        if (getPlayerProtection(lower) != null) {
            Msg.error(player, "You already have a protection! Delete it first to create a new one.");
            return false;
        }

        if (WorldManager.getWorldGroup(locA.getWorld()) != WorldManager.WorldGroup.SURVIVAL) {
            Msg.error(player, "You can only create protections in survival worlds!");
            return false;
        }
        if (!locA.getWorld().getName().equals(locB.getWorld().getName())) {
            Msg.error(player, "Both corners must be in the same world!");
            return false;
        }

        int ax = locA.getBlockX();
        int az = locA.getBlockZ();
        int bx = locB.getBlockX();
        int bz = locB.getBlockZ();
        int minX = Math.min(ax, bx);
        int maxX = Math.max(ax, bx);
        int minZ = Math.min(az, bz);
        int maxZ = Math.max(az, bz);
        int area = (maxX - minX + 1) * (maxZ - minZ + 1);

        int budget = getPlayerBlockBudget(lower);
        if (area > budget) {
            Msg.error(player, "Not enough blocks! Area: " + String.format("%,d", area)
                    + ", Budget: " + String.format("%,d", budget));
            return false;
        }

        String worldName = locA.getWorld().getName();
        Protection temp = new Protection(0, lower, worldName, minX, minZ, maxX, maxZ);
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
                    "INSERT INTO su_protections (owner_username, world, min_x, min_z, max_x, max_z) VALUES (?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, lower);
                ps.setString(2, worldName);
                ps.setInt(3, minX);
                ps.setInt(4, minZ);
                ps.setInt(5, maxX);
                ps.setInt(6, maxZ);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
            return -1;
        }).thenAccept(id -> {
            if (id == null || id < 0) return;
            Protection prot = new Protection(id, lower, worldName, minX, minZ, maxX, maxZ);
            protections.add(prot);
            addLog(id, lower, "Created protection " + (maxX - minX + 1) + "x" + (maxZ - minZ + 1));
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    Msg.success(player, "Protection created! Area: " + (maxX - minX + 1) + "x" + (maxZ - minZ + 1)
                            + " (" + String.format("%,d", area) + " blocks)");
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
            Msg.error(player, "Not enough Money! Need " + EconomyManager.MONEY_ICON + " $" + EconomyManager.format(GOLEM_MONEY_COST));
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

        Msg.success(player, "Guardian Golem spawned! Cost: " + EconomyManager.MONEY_ICON + " $" + EconomyManager.format(GOLEM_MONEY_COST));
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
                    Component.text(EconomyManager.MONEY_ICON + " $" + EconomyManager.format(EXTRA_SLOT_MONEY_COST) + " or "
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
        moneyMeta.lore(List.of(Component.text("Cost: " + EconomyManager.MONEY_ICON + " $" + EconomyManager.format(EXTRA_SLOT_MONEY_COST), YELLOW)
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
            Msg.error(player, "You don't have a protection! Right-click with a Protection Shovel to create one.");
            return;
        }

        HomeHolder holder = new HomeHolder(HomeHolder.Type.PROTECT_MAIN);
        holder.setProtectionId(prot.getId());
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Home Protection", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        int w = prot.getWidth();
        int l = prot.getLength();
        int area = prot.getArea();
        int budget = getPlayerBlockBudget(lower);
        ItemStack info = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Protection Info", GREEN).decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(List.of(
                Component.text("World: " + prot.getWorld(), GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Area: " + w + " x " + l + " (" + String.format("%,d", area) + " blocks)", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("From: (" + prot.getMinX() + ", " + prot.getMinZ() + ") To: (" + prot.getMaxX() + ", " + prot.getMaxZ() + ")", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Block Budget: " + String.format("%,d", budget), CYAN).decoration(TextDecoration.ITALIC, false)
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
        expandMeta.displayName(Component.text("Buy Blocks", YELLOW).decoration(TextDecoration.ITALIC, false));
        expandMeta.lore(List.of(
                Component.text("Budget: " + String.format("%,d", budget) + " blocks", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Click to buy more blocks", YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        expand.setItemMeta(expandMeta);
        inv.setItem(14, expand);

        ItemStack golem = new ItemStack(Material.IRON_BLOCK);
        ItemMeta golemMeta = golem.getItemMeta();
        golemMeta.displayName(Component.text("Spawn Guardian Golem", GREEN).decoration(TextDecoration.ITALIC, false));
        golemMeta.lore(List.of(
                Component.text("Cost: " + EconomyManager.MONEY_ICON + " $" + EconomyManager.format(GOLEM_MONEY_COST), YELLOW).decoration(TextDecoration.ITALIC, false),
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
        String lower = player.getName().toLowerCase();
        int budget = getPlayerBlockBudget(lower);

        HomeHolder holder = new HomeHolder(HomeHolder.Type.PROTECT_EXPAND);
        holder.setProtectionId(protId);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Buy Protection Blocks", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        ItemStack budgetInfo = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta budgetMeta = budgetInfo.getItemMeta();
        budgetMeta.displayName(Component.text("Your Block Budget", CYAN).decoration(TextDecoration.ITALIC, false));
        budgetMeta.lore(List.of(
                Component.text(String.format("%,d", budget) + " blocks", GREEN).decoration(TextDecoration.ITALIC, false),
                Component.text("Buy more to protect larger areas", GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        budgetInfo.setItemMeta(budgetMeta);
        inv.setItem(4, budgetInfo);

        for (int i = 0; i < BLOCK_PURCHASE_AMOUNTS.length; i++) {
            int blocks = BLOCK_PURCHASE_AMOUNTS[i];
            double cost = BLOCK_PURCHASE_COSTS[i];
            int slot = 10 + (i * 2);

            ItemStack btn = new ItemStack(Material.GOLD_INGOT);
            ItemMeta meta = btn.getItemMeta();
            meta.displayName(Component.text("+" + String.format("%,d", blocks) + " Blocks", GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Cost: " + EconomyManager.MONEY_ICON + " $" + EconomyManager.format(cost), YELLOW).decoration(TextDecoration.ITALIC, false),
                    Component.text("New budget: " + String.format("%,d", budget + blocks), GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            btn.setItemMeta(meta);
            inv.setItem(slot, btn);
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("Back", GRAY).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inv.setItem(22, back);

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
