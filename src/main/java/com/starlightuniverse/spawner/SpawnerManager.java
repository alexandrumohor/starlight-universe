package com.starlightuniverse.spawner;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class SpawnerManager {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor PURPLE = TextColor.color(0xAA00AA);

    public static final NamespacedKey SPAWNER_TYPE_KEY = NamespacedKey.fromString("starlightuniverse:vspawner_type");
    public static final NamespacedKey SPAWNER_TIER_KEY = NamespacedKey.fromString("starlightuniverse:vspawner_tier");
    public static final NamespacedKey SPAWNER_STACK_KEY = NamespacedKey.fromString("starlightuniverse:vspawner_stack");

    // Storage GUI layout
    private static final int STORAGE_ITEMS_PER_PAGE = 45;
    private static final int STORAGE_BACK = 45;
    private static final int STORAGE_PREV = 47;
    private static final int STORAGE_COLLECT_ALL = 49;
    private static final int STORAGE_SELL_ALL = 51;
    private static final int STORAGE_NEXT = 53;

    // Menu layout (27 slots)
    private static final int MENU_INFO = 11;
    private static final int MENU_STORAGE = 13;
    private static final int MENU_XP = 15;
    private static final int MENU_CLOSE = 22;

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;

    private final Map<String, VirtualSpawner> spawnersByLoc = new ConcurrentHashMap<>();
    private final Map<Integer, VirtualSpawner> spawnersById = new ConcurrentHashMap<>();

    // Real-time refresh: track which player has which spawner view open.
    private final Map<UUID, ViewerState> openViewers = new ConcurrentHashMap<>();

    private BukkitTask tickTask;
    private BukkitTask refreshTask;

    private static final class ViewerState {
        final int spawnerId;
        final SpawnerHolder.Type type;
        int page;

        ViewerState(int spawnerId, SpawnerHolder.Type type, int page) {
            this.spawnerId = spawnerId;
            this.type = type;
            this.page = page;
        }
    }

    public SpawnerManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
    }

    public void initialize() {
        loadAll();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        // 1s refresh for open GUIs (matches AH countdown cadence).
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpenViewers, 20L, 20L);
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        if (refreshTask != null) refreshTask.cancel();
        for (VirtualSpawner spawner : spawnersByLoc.values()) {
            saveSpawnerSync(spawner);
        }
    }

    // ── Loading / persistence ──

    private void loadAll() {
        db.queryAsync(conn -> {
            List<VirtualSpawner> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM su_virtual_spawners");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VirtualSpawnerType type = VirtualSpawnerType.fromName(rs.getString("entity_type"));
                    if (type == null) continue;
                    VirtualSpawner spawner = new VirtualSpawner(
                            rs.getInt("id"),
                            rs.getString("owner_username"),
                            type,
                            rs.getString("world"),
                            rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                            Math.max(1, rs.getInt("tier")),
                            Math.max(1, rs.getInt("stack_count")),
                            rs.getInt("stored_xp"));
                    String storage = rs.getString("storage_data");
                    if (storage != null && !storage.isEmpty()) {
                        deserializeStorage(spawner, storage);
                    }
                    list.add(spawner);
                }
            }
            return list;
        }).thenAccept(list -> {
            if (list == null) return;
            for (VirtualSpawner s : list) {
                spawnersByLoc.put(s.locKey(), s);
                spawnersById.put(s.getId(), s);
            }
            plugin.getLogger().info("[SU] Loaded " + list.size() + " virtual spawners.");
        });
    }

    private static String serializeStorage(VirtualSpawner spawner) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Material, Integer> e : spawner.getStorage().entrySet()) {
            if (e.getValue() <= 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey().name()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    private static void deserializeStorage(VirtualSpawner spawner, String data) {
        String[] entries = data.split(",");
        for (String entry : entries) {
            if (entry.isEmpty()) continue;
            String[] parts = entry.split(":");
            if (parts.length != 2) continue;
            try {
                Material mat = Material.valueOf(parts[0]);
                int amount = Integer.parseInt(parts[1]);
                spawner.addToStorage(mat, amount);
            } catch (Exception ignored) {}
        }
    }

    private void saveSpawnerAsync(VirtualSpawner spawner) {
        final String storage = serializeStorage(spawner);
        final int tier = spawner.getTier();
        final int stack = spawner.getStackCount();
        final int xp = spawner.getStoredXp();
        final int id = spawner.getId();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_virtual_spawners SET tier=?, stack_count=?, storage_data=?, stored_xp=? WHERE id=?")) {
                ps.setInt(1, tier);
                ps.setInt(2, stack);
                ps.setString(3, storage);
                ps.setInt(4, xp);
                ps.setInt(5, id);
                ps.executeUpdate();
            }
        });
    }

    private void saveSpawnerSync(VirtualSpawner spawner) {
        String storage = serializeStorage(spawner);
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE su_virtual_spawners SET tier=?, stack_count=?, storage_data=?, stored_xp=? WHERE id=?")) {
            ps.setInt(1, spawner.getTier());
            ps.setInt(2, spawner.getStackCount());
            ps.setString(3, storage);
            ps.setInt(4, spawner.getStoredXp());
            ps.setInt(5, spawner.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("[SU] Failed to save spawner " + spawner.getId() + ": " + e.getMessage());
        }
    }

    // ── Placement / removal ──

    public VirtualSpawner getSpawnerAt(Location loc) {
        return spawnersByLoc.get(locKey(loc));
    }

    public VirtualSpawner getSpawnerById(int id) {
        return spawnersById.get(id);
    }

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public void placeSpawner(Player player, Location loc, VirtualSpawnerType type, int tier, int stack) {
        String key = locKey(loc);
        if (spawnersByLoc.containsKey(key)) {
            Msg.error(player, "There is already a spawner at this location!");
            return;
        }
        String owner = player.getName().toLowerCase();
        int tierClamped = Math.max(1, Math.min(3, tier));
        int stackClamped = Math.max(1, Math.min(VirtualSpawnerType.MAX_STACK, stack));

        loc.getBlock().setType(Material.SPAWNER);

        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_virtual_spawners (owner_username, entity_type, world, x, y, z, tier, stack_count) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, owner);
                ps.setString(2, type.name());
                ps.setString(3, loc.getWorld().getName());
                ps.setInt(4, loc.getBlockX());
                ps.setInt(5, loc.getBlockY());
                ps.setInt(6, loc.getBlockZ());
                ps.setInt(7, tierClamped);
                ps.setInt(8, stackClamped);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
            return -1;
        }).thenAccept(id -> {
            if (id == null || id < 0) return;
            VirtualSpawner spawner = new VirtualSpawner(id, owner, type,
                    loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    tierClamped, stackClamped, 0);
            spawnersByLoc.put(key, spawner);
            spawnersById.put(id, spawner);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    Msg.success(player, "Placed " + type.getDisplayName() + " Virtual Spawner (Tier "
                            + tierClamped + ", Stack " + stackClamped + ")!");
                }
            });
        });
    }

    public boolean tryStackOnto(Player player, Location loc, VirtualSpawnerType type, int stack) {
        VirtualSpawner existing = getSpawnerAt(loc);
        if (existing == null) return false;
        if (existing.getType() != type) {
            Msg.error(player, "That spawner is a different mob type!");
            return true;
        }
        if (!existing.getOwnerUsername().equalsIgnoreCase(player.getName())) {
            Msg.error(player, "That spawner belongs to " + existing.getOwnerUsername() + "!");
            return true;
        }
        if (existing.getStackCount() >= VirtualSpawnerType.MAX_STACK) {
            Msg.error(player, "This spawner is already at the maximum stack (" + VirtualSpawnerType.MAX_STACK + ")!");
            return true;
        }
        int newStack = Math.min(VirtualSpawnerType.MAX_STACK, existing.getStackCount() + stack);
        existing.setStackCount(newStack);
        saveSpawnerAsync(existing);
        Msg.success(player, "Stacked! " + type.getDisplayName() + " Spawner now at stack " + newStack + ".");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.6f);
        return true;
    }

    public void removeSpawner(VirtualSpawner spawner) {
        spawnersByLoc.remove(spawner.locKey());
        spawnersById.remove(spawner.getId());
        Location loc = spawner.getLocation();
        if (loc != null) loc.getBlock().setType(Material.AIR);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM su_virtual_spawners WHERE id = ?")) {
                ps.setInt(1, spawner.getId());
                ps.executeUpdate();
            }
        });
    }

    // ── Silk-touch pickup ──

    public ItemStack createSpawnerItem(VirtualSpawnerType type, int tier, int stack) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(type.getDisplayName() + " Spawner", GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Virtual Spawner", CYAN).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Tier: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(tier + "/3", tierColor(tier))));
        lore.add(Component.text("Stack: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("x" + stack, WHITE)));
        lore.add(Component.empty());
        lore.add(Component.text("Place to generate loot!", YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(SPAWNER_TYPE_KEY, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(SPAWNER_TIER_KEY, PersistentDataType.INTEGER, tier);
        meta.getPersistentDataContainer().set(SPAWNER_STACK_KEY, PersistentDataType.INTEGER, stack);

        item.setItemMeta(meta);
        return item;
    }

    public VirtualSpawnerType getSpawnerItemType(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String value = meta.getPersistentDataContainer().get(SPAWNER_TYPE_KEY, PersistentDataType.STRING);
        return VirtualSpawnerType.fromName(value);
    }

    public int getSpawnerItemTier(ItemStack item) {
        if (item == null) return 1;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 1;
        Integer v = meta.getPersistentDataContainer().get(SPAWNER_TIER_KEY, PersistentDataType.INTEGER);
        return v == null ? 1 : v;
    }

    public int getSpawnerItemStack(ItemStack item) {
        if (item == null) return 1;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 1;
        Integer v = meta.getPersistentDataContainer().get(SPAWNER_STACK_KEY, PersistentDataType.INTEGER);
        return v == null ? 1 : v;
    }

    public static boolean hasSilkTouch(ItemStack tool) {
        return tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH);
    }

    // ── Tick / loot generation ──

    private void tick() {
        long now = System.currentTimeMillis();
        for (VirtualSpawner spawner : spawnersByLoc.values()) {
            VirtualSpawnerType type = spawner.getType();
            int tierIdx = Math.max(0, Math.min(2, spawner.getTier() - 1));
            long intervalMs = VirtualSpawnerType.TIER_SECONDS[tierIdx] * 1000L;
            if (now - spawner.getLastSpawnMillis() < intervalMs) continue;
            spawner.setLastSpawnMillis(now);

            World world = Bukkit.getWorld(spawner.getWorldName());
            if (world == null) continue;
            if (!world.isChunkLoaded(spawner.getX() >> 4, spawner.getZ() >> 4)) continue;

            List<VirtualSpawnerType.Drop> drops = type.getDrops();
            if (drops.isEmpty()) continue;
            int spawnCount = spawner.getStackCount();
            for (int i = 0; i < spawnCount; i++) {
                // One drop + XP per stack unit per tick.
                VirtualSpawnerType.Drop drop = drops.get(
                        ThreadLocalRandom.current().nextInt(drops.size()));
                int maxAmt = Math.max(drop.minAmount(), drop.maxAmount());
                int minAmt = Math.min(drop.minAmount(), drop.maxAmount());
                int amt = minAmt == maxAmt ? minAmt
                        : ThreadLocalRandom.current().nextInt(minAmt, maxAmt + 1);
                if (amt > 0 && !spawner.isStorageFullFor(drop.material())) {
                    spawner.addToStorage(drop.material(), amt);
                }
                spawner.addStoredXp(type.getXpPerSpawn());
            }
            saveSpawnerAsync(spawner);
            spawnAmbientParticles(spawner);
        }
    }

    private void spawnAmbientParticles(VirtualSpawner spawner) {
        World world = Bukkit.getWorld(spawner.getWorldName());
        if (world == null) return;
        double x = spawner.getX() + 0.5;
        double y = spawner.getY() + 0.5;
        double z = spawner.getZ() + 0.5;
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, x, y + 0.5, z, 4, 0.25, 0.25, 0.25, 0.01);
    }

    // ── Real-time refresh of open GUIs ──

    private void refreshOpenViewers() {
        if (openViewers.isEmpty()) return;
        for (Map.Entry<UUID, ViewerState> e : openViewers.entrySet()) {
            Player player = Bukkit.getPlayer(e.getKey());
            if (player == null || !player.isOnline()) continue;
            ViewerState vs = e.getValue();
            VirtualSpawner spawner = spawnersById.get(vs.spawnerId);
            if (spawner == null) continue;
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top == null || !(top.getHolder() instanceof SpawnerHolder)) continue;
            switch (vs.type) {
                case MANAGE_MENU -> renderMenu(top, spawner);
                case STORAGE -> renderStorage(top, spawner, vs.page);
                default -> {}
            }
        }
    }

    public void trackViewer(Player player, int spawnerId, SpawnerHolder.Type type, int page) {
        openViewers.put(player.getUniqueId(), new ViewerState(spawnerId, type, page));
    }

    public void untrackViewer(Player player) {
        openViewers.remove(player.getUniqueId());
    }

    // ── GUI: manage menu ──

    public void openManageGui(Player player, VirtualSpawner spawner) {
        SpawnerHolder holder = new SpawnerHolder(SpawnerHolder.Type.MANAGE_MENU, spawner.getId());
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text(spawner.getType().getDisplayName() + " Spawner", GOLD)
                        .decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        ItemStack border = borderPane();
        for (int i = 0; i < 27; i++) inv.setItem(i, border);
        inv.setItem(MENU_INFO, null);
        inv.setItem(MENU_STORAGE, null);
        inv.setItem(MENU_XP, null);
        inv.setItem(MENU_CLOSE, null);

        renderMenu(inv, spawner);
        player.openInventory(inv);
        trackViewer(player, spawner.getId(), SpawnerHolder.Type.MANAGE_MENU, 0);
    }

    private void renderMenu(Inventory inv, VirtualSpawner spawner) {
        VirtualSpawnerType type = spawner.getType();
        int tierIdx = Math.max(0, Math.min(2, spawner.getTier() - 1));
        int intervalSec = VirtualSpawnerType.TIER_SECONDS[tierIdx];
        long remainingMs = Math.max(0,
                intervalSec * 1000L - (System.currentTimeMillis() - spawner.getLastSpawnMillis()));
        long remainingSec = (remainingMs + 999) / 1000;

        // ── Info ──
        ItemStack info = new ItemStack(type.getIcon());
        ItemMeta im = info.getItemMeta();
        im.displayName(Component.text("Info Spawner", GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> il = new ArrayList<>();
        il.add(Component.text("Type: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(type.getDisplayName(), WHITE)));
        il.add(Component.text("Owner: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(spawner.getOwnerUsername(), WHITE)));
        il.add(Component.text("Tier: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(spawner.getTier() + "/3", tierColor(spawner.getTier()))));
        il.add(Component.text("Stack: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("x" + spawner.getStackCount(), WHITE)));
        il.add(Component.text("Rate: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("1 item + XP every " + intervalSec + "s", YELLOW)));
        il.add(Component.text("Next drop: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(remainingSec + "s", CYAN)));
        im.lore(il);
        info.setItemMeta(im);
        inv.setItem(MENU_INFO, info);

        // ── Item Storage ──
        int totalItems = spawner.totalStorageCount();
        int uniqueMats = (int) spawner.getStorage().values().stream().filter(v -> v > 0).count();
        ItemStack storageIcon = new ItemStack(Material.CHEST);
        ItemMeta sm = storageIcon.getItemMeta();
        sm.displayName(Component.text("Item Storage", GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> sl = new ArrayList<>();
        sl.add(Component.text("Total items: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(EconomyManager.format(totalItems), YELLOW)));
        sl.add(Component.text("Unique materials: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.valueOf(uniqueMats), WHITE)));
        sl.add(Component.empty());
        sl.add(Component.text("Left-click: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("browse storage", GREEN)));
        sl.add(Component.text("Right-click: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("sell all", YELLOW)));
        sm.lore(sl);
        storageIcon.setItemMeta(sm);
        inv.setItem(MENU_STORAGE, storageIcon);

        // ── XP Storage ──
        int xp = spawner.getStoredXp();
        ItemStack xpIcon = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta xm = xpIcon.getItemMeta();
        xm.displayName(Component.text("Exp Storage", GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> xl = new ArrayList<>();
        xl.add(Component.text("Stored XP: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(EconomyManager.format(xp), YELLOW)));
        xl.add(Component.text("Max capacity: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(EconomyManager.format(VirtualSpawnerType.MAX_STORED_XP), WHITE)));
        xl.add(Component.empty());
        xl.add(Component.text("Click to collect", GREEN).decoration(TextDecoration.ITALIC, false));
        xl.add(Component.text("XP does not repair items!", GRAY).decoration(TextDecoration.ITALIC, false));
        xm.lore(xl);
        xpIcon.setItemMeta(xm);
        inv.setItem(MENU_XP, xpIcon);

        // Close
        ItemStack close = simpleItem(Material.BARRIER,
                Component.text("Close", RED).decoration(TextDecoration.ITALIC, false));
        inv.setItem(MENU_CLOSE, close);
    }

    // ── GUI: storage subpage ──

    public void openStorageGui(Player player, VirtualSpawner spawner, int page) {
        SpawnerHolder holder = new SpawnerHolder(SpawnerHolder.Type.STORAGE, spawner.getId(), page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text(spawner.getType().getDisplayName() + " Storage", GOLD)
                        .decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);
        renderStorage(inv, spawner, page);
        player.openInventory(inv);
        trackViewer(player, spawner.getId(), SpawnerHolder.Type.STORAGE, page);
    }

    private void renderStorage(Inventory inv, VirtualSpawner spawner, int page) {
        // Fill footer with border
        ItemStack border = borderPane();
        for (int i = STORAGE_ITEMS_PER_PAGE; i < 54; i++) inv.setItem(i, border);

        List<Map.Entry<Material, Integer>> entries = new ArrayList<>();
        for (Map.Entry<Material, Integer> e : spawner.getStorage().entrySet()) {
            if (e.getValue() > 0) entries.add(e);
        }
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) STORAGE_ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        int start = page * STORAGE_ITEMS_PER_PAGE;
        int end = Math.min(start + STORAGE_ITEMS_PER_PAGE, entries.size());
        // Clear grid
        for (int i = 0; i < STORAGE_ITEMS_PER_PAGE; i++) inv.setItem(i, null);

        for (int i = start; i < end; i++) {
            Map.Entry<Material, Integer> e = entries.get(i);
            int amount = Math.min(64, e.getValue());
            ItemStack stack = new ItemStack(e.getKey(), amount);
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(Component.text(prettify(e.getKey().name()), WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Stored: ", GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(EconomyManager.format(e.getValue()), YELLOW)));
            double sellUnit = VirtualSpawnerType.sellPrice(e.getKey());
            if (sellUnit > 0) {
                lore.add(Component.text("Sell price: ", GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(EconomyManager.moneyText(sellUnit).decoration(TextDecoration.ITALIC, false))
                        .append(Component.text(" each", GRAY)));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Click to collect this stack", GREEN)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            stack.setItemMeta(meta);
            inv.setItem(i - start, stack);
        }

        // Buttons row
        inv.setItem(STORAGE_BACK, simpleItem(Material.BARRIER,
                Component.text("Back to Menu", RED).decoration(TextDecoration.ITALIC, false)));
        if (page > 0) {
            inv.setItem(STORAGE_PREV, simpleItem(Material.ARROW,
                    Component.text("Previous Page", YELLOW).decoration(TextDecoration.ITALIC, false)));
        } else {
            inv.setItem(STORAGE_PREV, border);
        }
        inv.setItem(STORAGE_COLLECT_ALL, simpleItem(Material.HOPPER,
                Component.text("Collect All", GREEN).decoration(TextDecoration.ITALIC, false),
                Component.text("Move every stored item to your inventory", GRAY).decoration(TextDecoration.ITALIC, false)));
        inv.setItem(STORAGE_SELL_ALL, simpleItem(Material.GOLD_INGOT,
                Component.text("Sell All", GOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Sell all items for money", GRAY).decoration(TextDecoration.ITALIC, false)));
        if (page < totalPages - 1) {
            inv.setItem(STORAGE_NEXT, simpleItem(Material.ARROW,
                    Component.text("Next Page", YELLOW).decoration(TextDecoration.ITALIC, false)));
        } else {
            inv.setItem(STORAGE_NEXT, border);
        }

        // Page indicator in slot 46 (near back)
        inv.setItem(46, simpleItem(Material.PAPER,
                Component.text("Page " + (page + 1) + "/" + totalPages, GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
    }

    private static ItemStack simpleItem(Material material, Component displayName, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(displayName);
        List<Component> loreList = new ArrayList<>();
        for (Component c : lore) loreList.add(c);
        meta.lore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack borderPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    private static TextColor tierColor(int tier) {
        return switch (tier) {
            case 1 -> GREEN;
            case 2 -> CYAN;
            case 3 -> GOLD;
            default -> WHITE;
        };
    }

    private static String prettify(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.toLowerCase().split("_")) {
            if (sb.length() > 0) sb.append(' ');
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    // ── GUI actions ──

    public void collectStack(Player player, VirtualSpawner spawner, Material material) {
        Integer stored = spawner.getStorage().get(material);
        if (stored == null || stored <= 0) return;

        int give = Math.min(64, stored);
        ItemStack stack = new ItemStack(material, give);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        int leftover = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
        int actuallyGiven = give - leftover;
        if (actuallyGiven <= 0) {
            Msg.error(player, "Your inventory is full!");
            return;
        }

        int newStored = stored - actuallyGiven;
        if (newStored <= 0) spawner.getStorage().remove(material);
        else spawner.getStorage().put(material, newStored);

        saveSpawnerAsync(spawner);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.4f);
        // Force-refresh the current storage view so counts update immediately.
        ViewerState vs = openViewers.get(player.getUniqueId());
        if (vs != null && vs.type == SpawnerHolder.Type.STORAGE) {
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() instanceof SpawnerHolder) {
                renderStorage(top, spawner, vs.page);
            }
        }
    }

    public void collectAll(Player player, VirtualSpawner spawner) {
        if (spawner.getStorage().isEmpty()) {
            Msg.error(player, "The storage is empty!");
            return;
        }
        int totalMoved = 0;
        List<Material> toRemove = new ArrayList<>();
        for (Map.Entry<Material, Integer> e : spawner.getStorage().entrySet()) {
            int amount = e.getValue();
            if (amount <= 0) { toRemove.add(e.getKey()); continue; }
            int given = 0;
            while (given < amount) {
                int chunk = Math.min(64, amount - given);
                ItemStack stack = new ItemStack(e.getKey(), chunk);
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
                if (overflow.isEmpty()) {
                    given += chunk;
                } else {
                    int leftover = overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
                    given += chunk - leftover;
                    e.setValue(amount - given);
                    totalMoved += given;
                    if (given > 0) player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
                    Msg.error(player, "Your inventory is full! Moved " + EconomyManager.format(totalMoved) + " items.");
                    saveSpawnerAsync(spawner);
                    return;
                }
            }
            totalMoved += given;
            toRemove.add(e.getKey());
        }
        for (Material m : toRemove) spawner.getStorage().remove(m);
        saveSpawnerAsync(spawner);
        Msg.success(player, "Collected " + EconomyManager.format(totalMoved) + " items!");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.5f);
    }

    public void sellAll(Player player, VirtualSpawner spawner) {
        if (spawner.getStorage().isEmpty()) {
            Msg.error(player, "The storage is empty!");
            return;
        }
        double totalMoney = 0;
        int totalCount = 0;
        List<Material> toRemove = new ArrayList<>();
        for (Map.Entry<Material, Integer> e : spawner.getStorage().entrySet()) {
            double unit = VirtualSpawnerType.sellPrice(e.getKey());
            if (unit <= 0) continue;
            int amount = e.getValue();
            totalMoney += unit * amount;
            totalCount += amount;
            toRemove.add(e.getKey());
        }
        if (totalMoney <= 0) {
            Msg.error(player, "None of the stored items have a sell price!");
            return;
        }
        for (Material m : toRemove) spawner.getStorage().remove(m);
        economy.addMoney(player.getUniqueId(), totalMoney);
        saveSpawnerAsync(spawner);
        player.sendMessage(Msg.prefix()
                .append(Component.text("Sold " + EconomyManager.format(totalCount) + " items for ", GREEN))
                .append(EconomyManager.moneyText(totalMoney))
                .append(Component.text("!", GREEN)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
    }

    public void collectXp(Player player, VirtualSpawner spawner) {
        int xp = spawner.getStoredXp();
        if (xp <= 0) {
            Msg.error(player, "No stored XP to collect!");
            return;
        }
        // Bypass Mending: virtual-spawner XP does not repair items.
        try {
            player.giveExp(xp, false);
        } catch (NoSuchMethodError e) {
            // Fallback for older Paper APIs
            player.giveExp(xp);
        }
        spawner.setStoredXp(0);
        saveSpawnerAsync(spawner);
        Msg.success(player, "Collected " + EconomyManager.format(xp) + " XP!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
    }

    // ── GUI: shop ──

    public void openShopGui(Player player) {
        SpawnerHolder holder = new SpawnerHolder(SpawnerHolder.Type.SHOP, -1);
        VirtualSpawnerType[] types = VirtualSpawnerType.values();
        int size = Math.min(54, Math.max(27, ((types.length + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text("Virtual Spawner Shop", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        for (int i = 0; i < types.length && i < size; i++) {
            VirtualSpawnerType t = types[i];
            ItemStack item = new ItemStack(t.getIcon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(t.getDisplayName() + " Spawner", GOLD)
                    .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            Component priceComp = switch (t.getCurrency()) {
                case MONEY -> EconomyManager.moneyText(t.getShopPrice());
                case GEMS -> EconomyManager.gemsText(t.getShopPrice());
                case STARS -> EconomyManager.starsText(t.getShopPrice());
            };
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Cost: ", GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(priceComp.decoration(TextDecoration.ITALIC, false)));
            lore.add(Component.text("Tier: ", GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("1/3", GREEN)));
            lore.add(Component.text("XP per spawn: ", GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(t.getXpPerSpawn(), YELLOW)));
            lore.add(Component.empty());
            lore.add(Component.text("Drops (one random per tick):", GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            for (VirtualSpawnerType.Drop d : t.getDrops()) {
                String amountRange = d.minAmount() == d.maxAmount()
                        ? "x" + d.maxAmount()
                        : d.minAmount() + "-" + d.maxAmount();
                lore.add(Component.text("  " + prettify(d.material().name())
                        + " (" + amountRange + ")", WHITE).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            lore.add(Component.text("Click to purchase", YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(SPAWNER_TYPE_KEY, PersistentDataType.STRING, t.name());
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        player.openInventory(inv);
    }

    public void buyFromShop(Player player, VirtualSpawnerType type) {
        UUID uuid = player.getUniqueId();
        Component priceComp;
        boolean success;
        switch (type.getCurrency()) {
            case MONEY -> {
                priceComp = EconomyManager.moneyText(type.getShopPrice());
                success = economy.removeMoney(uuid, type.getShopPrice());
            }
            case GEMS -> {
                priceComp = EconomyManager.gemsText(type.getShopPrice());
                success = economy.removeGems(uuid, type.getShopPrice());
            }
            case STARS -> {
                priceComp = EconomyManager.starsText(type.getShopPrice());
                success = economy.removeStars(uuid, type.getShopPrice());
            }
            default -> { return; }
        }
        if (!success) {
            player.sendMessage(Msg.prefix()
                    .append(Component.text("Not enough currency! Need ", RED))
                    .append(priceComp));
            return;
        }

        ItemStack spawnerItem = createSpawnerItem(type, 1, 1);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(spawnerItem);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.sendMessage(Msg.prefix()
                .append(Component.text("Purchased " + type.getDisplayName() + " Spawner for ", GREEN))
                .append(priceComp)
                .append(Component.text("!", GREEN)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.5f);
    }

    // ── Ownership check ──

    public boolean canManage(Player player, VirtualSpawner spawner) {
        return spawner.getOwnerUsername().equalsIgnoreCase(player.getName());
    }

    public Collection<VirtualSpawner> getAllSpawners() {
        return spawnersByLoc.values();
    }
}
