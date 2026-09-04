package com.starlightuniverse.pwarp;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import com.starlightuniverse.world.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PWarpManager {

    public static final int MAX_WARPS_PER_PLAYER = 3;
    public static final double CREATE_COST = 2_500;
    public static final int PROTECTION_RADIUS = 15;
    public static final int MIN_NAME = 3;
    public static final int MAX_NAME = 24;
    public static final int MIN_DESCRIPTION = 5;
    public static final int MAX_DESCRIPTION = 50;

    public static final String[] CATEGORIES = {"Shop", "Farm", "PvP", "Build", "Event", "Other"};

    public enum Sort {
        BEST_RATING("Best Rating"),
        LOWEST_RATING("Lowest Rating"),
        NEWEST("Newest"),
        OLDEST("Oldest");

        public final String display;
        Sort(String display) { this.display = display; }
        public Sort next() {
            Sort[] vs = values();
            return vs[(ordinal() + 1) % vs.length];
        }
    }

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;
    private final NamespacedKey warpIdKey;

    private final List<PersonalWarp> warps = Collections.synchronizedList(new ArrayList<>());
    private final Map<Integer, double[]> ratings = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> bans = new ConcurrentHashMap<>();

    private final Set<UUID> descriptionMode = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> descriptionWarpId = new ConcurrentHashMap<>();

    public PWarpManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
        this.warpIdKey = new NamespacedKey(plugin, "pwarp_id");
    }

    public void initialize() {
        loadAll();
    }

    private void loadAll() {
        db.queryAsync(conn -> {
            List<PersonalWarp> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM su_pwarps");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PersonalWarp w = new PersonalWarp(
                            rs.getInt("id"), rs.getString("owner_username"), rs.getString("name"),
                            rs.getString("world"), rs.getDouble("x"), rs.getDouble("y"),
                            rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"),
                            rs.getString("category"), rs.getString("description"),
                            rs.getDouble("entry_cost"), rs.getInt("visitors"),
                            rs.getBoolean("allow_pvp"), rs.getBoolean("allow_break"),
                            rs.getBoolean("allow_place"), rs.getBoolean("allow_containers"),
                            rs.getBoolean("allow_interact"));
                    java.sql.Timestamp ts = rs.getTimestamp("created_date");
                    if (ts != null) w.setCreatedMillis(ts.getTime());
                    list.add(w);
                }
            }
            Map<Integer, double[]> ratingMap = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT pwarp_id, AVG(stars) AS avg_stars, COUNT(*) AS votes FROM su_pwarp_ratings GROUP BY pwarp_id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ratingMap.put(rs.getInt("pwarp_id"),
                            new double[]{rs.getDouble("avg_stars"), rs.getInt("votes")});
                }
            }
            Map<String, Set<Long>> banMap = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM su_pwarp_bans");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("owner_username") + ":" + rs.getString("banned_username");
                    banMap.computeIfAbsent(key, k -> new HashSet<>())
                            .add((long) rs.getInt("pwarp_id"));
                }
            }
            return new Object[]{list, ratingMap, banMap};
        }).thenAccept(result -> {
            if (result == null) return;
            @SuppressWarnings("unchecked")
            List<PersonalWarp> list = (List<PersonalWarp>) ((Object[]) result)[0];
            @SuppressWarnings("unchecked")
            Map<Integer, double[]> rMap = (Map<Integer, double[]>) ((Object[]) result)[1];
            @SuppressWarnings("unchecked")
            Map<String, Set<Long>> bMap = (Map<String, Set<Long>>) ((Object[]) result)[2];
            warps.clear();
            warps.addAll(list);
            ratings.clear();
            ratings.putAll(rMap);
            bans.clear();
            bMap.forEach((k, v) -> {
                Set<Long> set = ConcurrentHashMap.newKeySet();
                set.addAll(v);
                bans.put(k, set);
            });
            plugin.getLogger().info("[SU] Loaded " + list.size() + " personal warps.");
        });
    }

    // ============================================================
    // Lookups
    // ============================================================

    public PersonalWarp getWarp(String owner, String name) {
        String lower = owner.toLowerCase();
        synchronized (warps) {
            for (PersonalWarp w : warps)
                if (w.getOwner().equals(lower) && w.getName().equalsIgnoreCase(name)) return w;
        }
        return null;
    }

    public PersonalWarp getWarpById(int id) {
        synchronized (warps) {
            for (PersonalWarp w : warps) if (w.getId() == id) return w;
        }
        return null;
    }

    public List<PersonalWarp> getWarpsByName(String name) {
        List<PersonalWarp> hits = new ArrayList<>();
        synchronized (warps) {
            for (PersonalWarp w : warps) if (w.getName().equalsIgnoreCase(name)) hits.add(w);
        }
        return hits;
    }

    public List<PersonalWarp> getWarpsOf(String owner) {
        String lower = owner.toLowerCase();
        List<PersonalWarp> mine = new ArrayList<>();
        synchronized (warps) {
            for (PersonalWarp w : warps) if (w.getOwner().equals(lower)) mine.add(w);
        }
        return mine;
    }

    public List<PersonalWarp> getAllWarps() {
        synchronized (warps) {
            return new ArrayList<>(warps);
        }
    }

    public PersonalWarp getWarpContaining(String world, int x, int z) {
        synchronized (warps) {
            for (PersonalWarp w : warps) if (w.containsBlock(world, x, z)) return w;
        }
        return null;
    }

    public boolean isBannedFrom(String owner, String targetName, int warpId) {
        Set<Long> set = bans.get(owner.toLowerCase() + ":" + targetName.toLowerCase());
        if (set == null) return false;
        return set.contains(0L) || set.contains((long) warpId);
    }

    public boolean isBannedGlobal(String owner, String targetName) {
        Set<Long> set = bans.get(owner.toLowerCase() + ":" + targetName.toLowerCase());
        return set != null && set.contains(0L);
    }

    public boolean isBannedSpecific(String owner, String targetName, int warpId) {
        Set<Long> set = bans.get(owner.toLowerCase() + ":" + targetName.toLowerCase());
        return set != null && set.contains((long) warpId);
    }

    public double[] getRating(int warpId) {
        return ratings.getOrDefault(warpId, new double[]{0, 0});
    }

    /** Resolves a query for /pwarp <query> or /pwarp rate <query> etc. Returns null and messages the player on failure. */
    public PersonalWarp resolveWarp(Player sender, String query) {
        if (query.contains(":")) {
            String[] parts = query.split(":", 2);
            PersonalWarp w = getWarp(parts[0], parts[1]);
            if (w == null) Msg.error(sender, "No such warp: " + query);
            return w;
        }
        PersonalWarp own = getWarp(sender.getName(), query);
        if (own != null) return own;
        List<PersonalWarp> hits = getWarpsByName(query);
        if (hits.isEmpty()) { Msg.error(sender, "Warp not found: " + query); return null; }
        if (hits.size() == 1) return hits.get(0);
        Msg.error(sender, "Multiple warps named \"" + query + "\". Use <owner>:<name> format:");
        for (PersonalWarp w : hits) Msg.gray(sender, "  " + w.getOwner() + ":" + w.getName());
        return null;
    }

    // ============================================================
    // Create / Delete
    // ============================================================

    public void createWarp(Player player, String name, String description) {
        String owner = player.getName().toLowerCase();

        if (name.length() < MIN_NAME || name.length() > MAX_NAME) {
            Msg.error(player, "Warp name must be " + MIN_NAME + "-" + MAX_NAME + " characters!");
            return;
        }
        if (!name.matches("[A-Za-z0-9_-]+")) {
            Msg.error(player, "Warp name may only contain letters, digits, _ and -.");
            return;
        }
        if (description == null || description.trim().length() < MIN_DESCRIPTION) {
            Msg.error(player, "Description is required (min " + MIN_DESCRIPTION + " chars). Usage: /pwarp create <name> <description>");
            return;
        }
        String desc = description.trim();
        if (desc.length() > MAX_DESCRIPTION) desc = desc.substring(0, MAX_DESCRIPTION);
        if (getWarp(owner, name) != null) {
            Msg.error(player, "You already have a warp with that name!");
            return;
        }
        if (getWarpsOf(owner).size() >= MAX_WARPS_PER_PLAYER) {
            Msg.error(player, "You already have " + MAX_WARPS_PER_PLAYER + " warps! Delete one first.");
            return;
        }
        Location loc = player.getLocation();
        if (WorldManager.getWorldGroup(loc.getWorld()) != WorldManager.WorldGroup.SURVIVAL) {
            Msg.error(player, "You can only create warps in survival worlds!");
            return;
        }

        int bx = loc.getBlockX(), bz = loc.getBlockZ();
        PersonalWarp overlap = getWarpContaining(loc.getWorld().getName(), bx, bz);
        if (overlap != null) {
            Msg.error(player, "This area overlaps with " + overlap.getOwner() + "'s warp!");
            return;
        }
        synchronized (warps) {
            for (PersonalWarp w : warps) {
                if (!w.getWorldName().equals(loc.getWorld().getName())) continue;
                double dx = w.getX() - loc.getX();
                double dz = w.getZ() - loc.getZ();
                if (dx * dx + dz * dz < (double) (PROTECTION_RADIUS * 2) * (PROTECTION_RADIUS * 2)) {
                    Msg.error(player, "This area is too close to " + w.getOwner() + "'s warp!");
                    return;
                }
            }
        }

        if (!economy.removeMoney(player.getUniqueId(), CREATE_COST)) {
            Msg.error(player, Component.text("Not enough Money! Need ")
                    .append(EconomyManager.moneyText(CREATE_COST)));
            return;
        }

        String finalDesc = desc;
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_pwarps (owner_username, name, world, x, y, z, yaw, pitch, category, description) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, owner);
                ps.setString(2, name);
                ps.setString(3, loc.getWorld().getName());
                ps.setDouble(4, loc.getX());
                ps.setDouble(5, loc.getY());
                ps.setDouble(6, loc.getZ());
                ps.setFloat(7, loc.getYaw());
                ps.setFloat(8, loc.getPitch());
                ps.setString(9, "Other");
                ps.setString(10, finalDesc);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
            return -1;
        }).thenAccept(id -> {
            if (id == null || id < 0) return;
            PersonalWarp warp = new PersonalWarp(id, owner, name, loc.getWorld().getName(),
                    loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(),
                    "Other", finalDesc, 0, 0, false, false, false, false, true);
            warp.setCreatedMillis(System.currentTimeMillis());
            warps.add(warp);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    Msg.success(player, Component.text("Warp \"" + name + "\" created! Cost: ")
                            .append(EconomyManager.moneyText(CREATE_COST)));
                    Msg.gray(player, "Area is auto-protected within " + PROTECTION_RADIUS + " blocks. Use /pwarps to manage.");
                }
            });
        });
    }

    public void deleteWarp(Player player, String name) {
        PersonalWarp w = getWarp(player.getName(), name);
        if (w == null) { Msg.error(player, "You don't own a warp named \"" + name + "\"!"); return; }
        deleteWarp(player, w);
    }

    public void deleteWarp(Player player, PersonalWarp w) {
        if (!w.getOwner().equals(player.getName().toLowerCase())) {
            Msg.error(player, "You don't own that warp!");
            return;
        }
        warps.remove(w);
        ratings.remove(w.getId());
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM su_pwarps WHERE id = ?")) {
                ps.setInt(1, w.getId());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM su_pwarp_bans WHERE owner_username = ? AND pwarp_id = ?")) {
                ps.setString(1, w.getOwner());
                ps.setInt(2, w.getId());
                ps.executeUpdate();
            }
        });
        for (Set<Long> set : bans.values()) set.remove((long) w.getId());
        Msg.success(player, "Warp \"" + w.getName() + "\" deleted!");
    }

    // ============================================================
    // Teleport
    // ============================================================

    public void teleport(Player player, PersonalWarp w) {
        if (w == null) return;
        String user = player.getName().toLowerCase();
        boolean isOwner = w.getOwner().equals(user);

        if (!isOwner && isBannedFrom(w.getOwner(), user, w.getId())) {
            Msg.error(player, "You are banned from this warp!");
            return;
        }

        Location loc = w.toLocation();
        if (loc == null) { Msg.error(player, "That warp's world is not loaded!"); return; }

        if (!isOwner && w.getEntryCost() > 0) {
            if (!economy.removeMoney(player.getUniqueId(), w.getEntryCost())) {
                Msg.error(player, Component.text("This warp costs ")
                        .append(EconomyManager.moneyText(w.getEntryCost()))
                        .append(Component.text(" to enter — you don't have enough!")));
                return;
            }
            Player ownerPlayer = Bukkit.getPlayerExact(w.getOwner());
            if (ownerPlayer != null && ownerPlayer.isOnline()) {
                economy.addMoney(ownerPlayer.getUniqueId(), w.getEntryCost());
            } else {
                economy.giveOffline(w.getOwner(), "money", w.getEntryCost());
            }
            Msg.info(player, Component.text("Paid ")
                    .append(EconomyManager.moneyText(w.getEntryCost()))
                    .append(Component.text(" to " + w.getOwner() + " for entry.")));
        }

        player.teleport(loc);
        player.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        Msg.success(player, "Teleported to warp \"" + w.getName() + "\"!");

        if (!isOwner) {
            w.incrementVisitors();
            db.executeAsync(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE su_pwarps SET visitors = visitors + 1 WHERE id = ?")) {
                    ps.setInt(1, w.getId());
                    ps.executeUpdate();
                }
            });
        }
    }

    // ============================================================
    // Rating
    // ============================================================

    public void rateWarp(Player player, PersonalWarp w, int stars) {
        if (stars < 1 || stars > 5) { Msg.error(player, "Rating must be 1-5 stars!"); return; }
        if (w == null) return;
        if (w.getOwner().equals(player.getName().toLowerCase())) {
            Msg.error(player, "You can't rate your own warp!");
            return;
        }
        String user = player.getName().toLowerCase();
        int wid = w.getId();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_pwarp_ratings (pwarp_id, username, stars) VALUES (?,?,?) " +
                            "ON DUPLICATE KEY UPDATE stars = ?")) {
                ps.setInt(1, wid);
                ps.setString(2, user);
                ps.setInt(3, stars);
                ps.setInt(4, stars);
                ps.executeUpdate();
            }
        }).thenRun(() -> refreshRating(wid));
        Msg.success(player, "You rated \"" + w.getName() + "\" " + stars + "/5!");
    }

    private void refreshRating(int warpId) {
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT AVG(stars) AS avg_stars, COUNT(*) AS votes FROM su_pwarp_ratings WHERE pwarp_id = ?")) {
                ps.setInt(1, warpId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return new double[]{rs.getDouble("avg_stars"), rs.getInt("votes")};
                }
            }
            return new double[]{0, 0};
        }).thenAccept(arr -> {
            if (arr != null) ratings.put(warpId, arr);
        });
    }

    // ============================================================
    // Ban / Unban
    // ============================================================

    public void banGlobal(Player owner, String targetName) {
        setBan(owner, targetName, 0, true);
    }

    public void unbanGlobal(Player owner, String targetName) {
        setBan(owner, targetName, 0, false);
    }

    public void banFromWarp(Player owner, String targetName, PersonalWarp warp) {
        if (warp == null || !warp.getOwner().equals(owner.getName().toLowerCase())) {
            Msg.error(owner, "You don't own that warp!");
            return;
        }
        setBan(owner, targetName, warp.getId(), true);
    }

    public void unbanFromWarp(Player owner, String targetName, PersonalWarp warp) {
        if (warp == null || !warp.getOwner().equals(owner.getName().toLowerCase())) {
            Msg.error(owner, "You don't own that warp!");
            return;
        }
        setBan(owner, targetName, warp.getId(), false);
    }

    private void setBan(Player owner, String targetName, int pwarpId, boolean nowBanned) {
        String ownerName = owner.getName().toLowerCase();
        String targetLower = targetName.toLowerCase();
        if (ownerName.equals(targetLower)) { Msg.error(owner, "You can't ban yourself!"); return; }
        String key = ownerName + ":" + targetLower;
        Set<Long> set = bans.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        long id = pwarpId;
        String scope = pwarpId == 0 ? "all your warps"
                : "warp \"" + Optional.ofNullable(getWarpById(pwarpId)).map(PersonalWarp::getName).orElse("?") + "\"";
        if (nowBanned) {
            if (set.contains(id)) { Msg.info(owner, targetName + " is already banned from " + scope + "."); return; }
            set.add(id);
            Msg.success(owner, targetName + " is now banned from " + scope + ".");
        } else {
            if (!set.contains(id)) { Msg.error(owner, targetName + " is not banned from " + scope + "."); return; }
            set.remove(id);
            Msg.success(owner, targetName + " is no longer banned from " + scope + ".");
        }
        db.executeAsync(conn -> {
            if (nowBanned) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT IGNORE INTO su_pwarp_bans (owner_username, banned_username, pwarp_id) VALUES (?, ?, ?)")) {
                    ps.setString(1, ownerName);
                    ps.setString(2, targetLower);
                    ps.setInt(3, pwarpId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM su_pwarp_bans WHERE owner_username = ? AND banned_username = ? AND pwarp_id = ?")) {
                    ps.setString(1, ownerName);
                    ps.setString(2, targetLower);
                    ps.setInt(3, pwarpId);
                    ps.executeUpdate();
                }
            }
        });
    }

    // ============================================================
    // Set settings
    // ============================================================

    public void setCategory(PersonalWarp w, String category) {
        w.setCategory(category);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_pwarps SET category = ? WHERE id = ?")) {
                ps.setString(1, category);
                ps.setInt(2, w.getId());
                ps.executeUpdate();
            }
        });
    }

    public void setDescription(PersonalWarp w, String desc) {
        String trimmed = desc.length() > MAX_DESCRIPTION ? desc.substring(0, MAX_DESCRIPTION) : desc;
        w.setDescription(trimmed);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_pwarps SET description = ? WHERE id = ?")) {
                ps.setString(1, trimmed);
                ps.setInt(2, w.getId());
                ps.executeUpdate();
            }
        });
    }

    public void setEntryCost(PersonalWarp w, double cost) {
        double clamped = Math.max(0, Math.min(cost, 100_000));
        w.setEntryCost(clamped);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_pwarps SET entry_cost = ? WHERE id = ?")) {
                ps.setDouble(1, clamped);
                ps.setInt(2, w.getId());
                ps.executeUpdate();
            }
        });
    }

    public void setPermission(PersonalWarp w, String perm, boolean value) {
        switch (perm) {
            case "pvp" -> w.setAllowPvp(value);
            case "break" -> w.setAllowBreak(value);
            case "place" -> w.setAllowPlace(value);
            case "containers" -> w.setAllowContainers(value);
            case "interact" -> w.setAllowInteract(value);
            default -> { return; }
        }
        String col = switch (perm) {
            case "pvp" -> "allow_pvp";
            case "break" -> "allow_break";
            case "place" -> "allow_place";
            case "containers" -> "allow_containers";
            case "interact" -> "allow_interact";
            default -> null;
        };
        if (col == null) return;
        String finalCol = col;
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_pwarps SET " + finalCol + " = ? WHERE id = ?")) {
                ps.setBoolean(1, value);
                ps.setInt(2, w.getId());
                ps.executeUpdate();
            }
        });
    }

    // ============================================================
    // Description input mode
    // ============================================================

    public boolean isInDescriptionMode(UUID uuid) { return descriptionMode.contains(uuid); }
    public int getDescriptionWarpId(UUID uuid) { return descriptionWarpId.getOrDefault(uuid, -1); }
    public void startDescriptionMode(UUID uuid, int warpId) {
        descriptionMode.add(uuid);
        descriptionWarpId.put(uuid, warpId);
    }
    public void endDescriptionMode(UUID uuid) {
        descriptionMode.remove(uuid);
        descriptionWarpId.remove(uuid);
    }

    // ============================================================
    // GUIs
    // ============================================================

    public void openBrowseGui(Player player, int page, String categoryFilter, Sort sort) {
        if (sort == null) sort = Sort.BEST_RATING;
        List<PersonalWarp> all = getAllWarps();
        if (categoryFilter != null && !categoryFilter.equalsIgnoreCase("All")) {
            all.removeIf(w -> !w.getCategory().equalsIgnoreCase(categoryFilter));
        }
        final Sort s = sort;
        all.sort((a, b) -> switch (s) {
            case BEST_RATING -> Double.compare(getRating(b.getId())[0], getRating(a.getId())[0]);
            case LOWEST_RATING -> Double.compare(getRating(a.getId())[0], getRating(b.getId())[0]);
            case NEWEST -> Long.compare(b.getCreatedMillis(), a.getCreatedMillis());
            case OLDEST -> Long.compare(a.getCreatedMillis(), b.getCreatedMillis());
        });

        int perPage = 28;
        int maxPage = Math.max(0, (all.size() - 1) / perPage);
        page = Math.max(0, Math.min(page, maxPage));

        PWarpHolder holder = new PWarpHolder(PWarpHolder.Type.BROWSE);
        holder.setPage(page);
        holder.setCategory(categoryFilter);
        holder.setSort(sort);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Personal Warps" + (categoryFilter != null && !categoryFilter.equalsIgnoreCase("All")
                        ? " — " + categoryFilter : ""), GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        int start = page * perPage;
        int end = Math.min(all.size(), start + perPage);
        int[] contentSlots = layoutContentSlots();
        for (int i = start; i < end; i++) {
            inv.setItem(contentSlots[i - start], buildWarpItem(all.get(i), false));
        }

        // Sort cycle button
        ItemStack sortBtn = new ItemStack(Material.HOPPER);
        ItemMeta sm = sortBtn.getItemMeta();
        sm.displayName(Component.text("Sort: " + sort.display, CYAN).decoration(TextDecoration.ITALIC, false));
        List<Component> sLore = new ArrayList<>();
        for (Sort v : Sort.values()) {
            TextColor c = v == sort ? GREEN : GRAY;
            String prefix = v == sort ? "▶ " : "  ";
            sLore.add(Component.text(prefix + v.display, c).decoration(TextDecoration.ITALIC, false));
        }
        sLore.add(Component.empty());
        sLore.add(Component.text("Left-click: next sort mode", YELLOW).decoration(TextDecoration.ITALIC, false));
        sm.lore(sLore);
        sortBtn.setItemMeta(sm);
        inv.setItem(36, sortBtn);

        int[] catSlots = {45, 46, 47, 48, 49, 50, 51};
        String[] catAll = new String[]{"All", "Shop", "Farm", "PvP", "Build", "Event", "Other"};
        Material[] catIcons = {Material.COMPASS, Material.EMERALD, Material.WHEAT, Material.IRON_SWORD,
                Material.BRICKS, Material.FIREWORK_ROCKET, Material.PAPER};
        for (int i = 0; i < catSlots.length; i++) {
            ItemStack cat = new ItemStack(catIcons[i]);
            ItemMeta cmeta = cat.getItemMeta();
            String label = catAll[i];
            boolean active = (categoryFilter == null && label.equals("All")) ||
                    (categoryFilter != null && categoryFilter.equalsIgnoreCase(label));
            cmeta.displayName(Component.text(label, active ? GREEN : YELLOW).decoration(TextDecoration.ITALIC, false));
            cmeta.lore(List.of(Component.text("Left-click: filter by " + label, GRAY).decoration(TextDecoration.ITALIC, false)));
            cat.setItemMeta(cmeta);
            inv.setItem(catSlots[i], cat);
        }

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm = prev.getItemMeta();
            pm.displayName(Component.text("Previous Page", YELLOW).decoration(TextDecoration.ITALIC, false));
            pm.lore(List.of(Component.text("Left-click: go back", GRAY).decoration(TextDecoration.ITALIC, false)));
            prev.setItemMeta(pm);
            inv.setItem(52, prev);
        }
        if (page < maxPage) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nm = next.getItemMeta();
            nm.displayName(Component.text("Next Page", YELLOW).decoration(TextDecoration.ITALIC, false));
            nm.lore(List.of(Component.text("Left-click: go forward", GRAY).decoration(TextDecoration.ITALIC, false)));
            next.setItemMeta(nm);
            inv.setItem(53, next);
        }

        player.openInventory(inv);
    }

    private int[] layoutContentSlots() {
        int[] slots = new int[28];
        int idx = 0;
        for (int row = 0; row < 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[idx++] = row * 9 + col;
            }
        }
        return slots;
    }

    private ItemStack buildWarpItem(PersonalWarp w, boolean managed) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyOwnerSkin(meta, w.getOwner());
        meta.displayName(Component.text(w.getName(), CYAN).decoration(TextDecoration.ITALIC, false));

        double[] r = getRating(w.getId());
        String stars = renderStars(r[0]);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Owner: " + w.getOwner(), GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Category: " + w.getCategory(), GRAY).decoration(TextDecoration.ITALIC, false));
        if (!w.getDescription().isEmpty())
            lore.add(Component.text("\"" + w.getDescription() + "\"", YELLOW).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Rating: " + stars + "  " + String.format(java.util.Locale.US, "%.1f", r[0]) + "/5", GOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Reviews Number: " + (int) r[1], GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Visitors: " + w.getVisitors(), GRAY).decoration(TextDecoration.ITALIC, false));
        if (w.getEntryCost() > 0)
            lore.add(Component.text("Entry cost: ", YELLOW)
                    .append(EconomyManager.moneyText(w.getEntryCost()).decoration(TextDecoration.ITALIC, false))
                    .decoration(TextDecoration.ITALIC, false));
        else
            lore.add(Component.text("Entry cost: FREE", GREEN).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        if (managed) {
            lore.add(Component.text("Left-click: teleport", GREEN).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Right-click: settings", YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Shift-right-click: delete", RED).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Left-click: teleport", GREEN).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Right-click: rate 1-5", YELLOW).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(warpIdKey, PersistentDataType.INTEGER, w.getId());
        item.setItemMeta(meta);
        return item;
    }

    private void applyOwnerSkin(SkullMeta meta, String ownerName) {
        try {
            Player online = Bukkit.getPlayerExact(ownerName);
            if (online != null) {
                meta.setPlayerProfile(online.getPlayerProfile());
                return;
            }
            PlayerProfile profile = Bukkit.createProfile(ownerName);
            meta.setPlayerProfile(profile);
        } catch (Throwable ignored) {
            // fallback: leave head blank
        }
    }

    private String renderStars(double rating) {
        StringBuilder sb = new StringBuilder();
        int full = (int) Math.round(rating);
        for (int i = 0; i < 5; i++) sb.append(i < full ? '★' : '☆');
        return sb.toString();
    }

    public Integer getWarpIdFrom(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(warpIdKey, PersistentDataType.INTEGER);
    }

    public void openMyWarpsGui(Player player) {
        List<PersonalWarp> mine = getWarpsOf(player.getName());
        PWarpHolder holder = new PWarpHolder(PWarpHolder.Type.MY_WARPS);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("My Personal Warps", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        for (int i = 0; i < mine.size() && i < 9; i++) {
            inv.setItem(i, buildWarpItem(mine.get(i), true));
        }

        for (int i = mine.size(); i < MAX_WARPS_PER_PLAYER; i++) {
            ItemStack empty = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
            ItemMeta em = empty.getItemMeta();
            em.displayName(Component.text("Empty slot #" + (i + 1), GRAY).decoration(TextDecoration.ITALIC, false));
            em.lore(List.of(Component.text("/pwarp create <name> <description>", YELLOW).decoration(TextDecoration.ITALIC, false)));
            empty.setItemMeta(em);
            inv.setItem(i, empty);
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.displayName(Component.text("Your Warps", YELLOW).decoration(TextDecoration.ITALIC, false));
        im.lore(List.of(
                Component.text("You have " + mine.size() + "/" + MAX_WARPS_PER_PLAYER + " warps.", GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Create cost: ", GRAY).append(EconomyManager.moneyText(CREATE_COST).decoration(TextDecoration.ITALIC, false))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Description required (min " + MIN_DESCRIPTION + " chars)", GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        info.setItemMeta(im);
        inv.setItem(22, info);

        player.openInventory(inv);
    }

    public void openSettingsGui(Player player, PersonalWarp w) {
        PWarpHolder holder = new PWarpHolder(PWarpHolder.Type.SETTINGS);
        holder.setPwarpId(w.getId());
        Inventory inv = Bukkit.createInventory(holder, 45,
                Component.text("Settings: " + w.getName(), GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        inv.setItem(4, buildWarpItem(w, true));

        inv.setItem(10, toggleItem("PvP", w.isAllowPvp(), Material.IRON_SWORD));
        inv.setItem(11, toggleItem("Block Break", w.isAllowBreak(), Material.DIAMOND_PICKAXE));
        inv.setItem(12, toggleItem("Block Place", w.isAllowPlace(), Material.OAK_PLANKS));
        inv.setItem(13, toggleItem("Containers", w.isAllowContainers(), Material.CHEST));
        inv.setItem(14, toggleItem("Interact", w.isAllowInteract(), Material.LEVER));

        ItemStack cat = new ItemStack(Material.NAME_TAG);
        ItemMeta cm = cat.getItemMeta();
        cm.displayName(Component.text("Category: " + w.getCategory(), YELLOW).decoration(TextDecoration.ITALIC, false));
        cm.lore(List.of(Component.text("Left-click: change category", GRAY).decoration(TextDecoration.ITALIC, false)));
        cat.setItemMeta(cm);
        inv.setItem(28, cat);

        ItemStack desc = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta dm = desc.getItemMeta();
        dm.displayName(Component.text("Description", YELLOW).decoration(TextDecoration.ITALIC, false));
        dm.lore(List.of(
                Component.text(w.getDescription().isEmpty() ? "(empty)" : "\"" + w.getDescription() + "\"", GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Left-click: type new description in chat", CYAN)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Min " + MIN_DESCRIPTION + " chars, max " + MAX_DESCRIPTION + " chars", GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        desc.setItemMeta(dm);
        inv.setItem(30, desc);

        ItemStack cost = new ItemStack(Material.GOLD_INGOT);
        ItemMeta costm = cost.getItemMeta();
        costm.displayName(Component.text(w.getEntryCost() > 0
                        ? "Entry cost: " + EconomyManager.MONEY_ICON + " $" + EconomyManager.format(w.getEntryCost())
                        : "Entry cost: FREE",
                w.getEntryCost() > 0 ? YELLOW : GREEN).decoration(TextDecoration.ITALIC, false));
        costm.lore(List.of(
                Component.text("Left-click: +$100 (max $100,000)", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Shift-left-click: +$1,000", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click: set to FREE ($0)", GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        cost.setItemMeta(costm);
        inv.setItem(32, cost);

        ItemStack tp = new ItemStack(Material.ENDER_PEARL);
        ItemMeta tpm = tp.getItemMeta();
        tpm.displayName(Component.text("Teleport to warp", GREEN).decoration(TextDecoration.ITALIC, false));
        tpm.lore(List.of(Component.text("Left-click: teleport", GRAY).decoration(TextDecoration.ITALIC, false)));
        tp.setItemMeta(tpm);
        inv.setItem(40, tp);

        ItemStack del = new ItemStack(Material.BARRIER);
        ItemMeta delm = del.getItemMeta();
        delm.displayName(Component.text("Delete warp", RED).decoration(TextDecoration.ITALIC, false));
        delm.lore(List.of(Component.text("Shift-left-click: confirm deletion", GRAY).decoration(TextDecoration.ITALIC, false)));
        del.setItemMeta(delm);
        inv.setItem(44, del);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.displayName(Component.text("Back", GRAY).decoration(TextDecoration.ITALIC, false));
        bm.lore(List.of(Component.text("Left-click: back to My Warps", GRAY).decoration(TextDecoration.ITALIC, false)));
        back.setItemMeta(bm);
        inv.setItem(36, back);

        player.openInventory(inv);
    }

    private ItemStack toggleItem(String label, boolean on, Material icon) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label + ": " + (on ? "ON" : "OFF"), on ? GREEN : RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(on ? "Visitors CAN " + label.toLowerCase() : "Visitors CANNOT " + label.toLowerCase(), GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Left-click: toggle", YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    public void openCategoryPickGui(Player player, PersonalWarp w) {
        PWarpHolder holder = new PWarpHolder(PWarpHolder.Type.CATEGORY_PICK);
        holder.setPwarpId(w.getId());
        Inventory inv = Bukkit.createInventory(holder, 9,
                Component.text("Pick Category", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        Material[] icons = {Material.EMERALD, Material.WHEAT, Material.IRON_SWORD,
                Material.BRICKS, Material.FIREWORK_ROCKET, Material.PAPER};
        for (int i = 0; i < CATEGORIES.length; i++) {
            ItemStack it = new ItemStack(icons[i]);
            ItemMeta m = it.getItemMeta();
            boolean current = CATEGORIES[i].equalsIgnoreCase(w.getCategory());
            m.displayName(Component.text(CATEGORIES[i], current ? GREEN : YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(Component.text("Left-click: set this category", GRAY).decoration(TextDecoration.ITALIC, false)));
            it.setItemMeta(m);
            inv.setItem(i, it);
        }
        player.openInventory(inv);
    }

    public void openRateGui(Player player, PersonalWarp w) {
        PWarpHolder holder = new PWarpHolder(PWarpHolder.Type.RATE);
        holder.setPwarpId(w.getId());
        Inventory inv = Bukkit.createInventory(holder, 9,
                Component.text("Rate: " + w.getName(), GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        for (int i = 0; i < 5; i++) {
            ItemStack star = new ItemStack(Material.NETHER_STAR, i + 1);
            ItemMeta sm = star.getItemMeta();
            sm.displayName(Component.text((i + 1) + " star" + (i > 0 ? "s" : ""), YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            sm.lore(List.of(Component.text("Left-click: submit this rating", GRAY).decoration(TextDecoration.ITALIC, false)));
            star.setItemMeta(sm);
            inv.setItem(2 + i, star);
        }
        player.openInventory(inv);
    }
}
