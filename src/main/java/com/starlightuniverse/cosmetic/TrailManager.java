package com.starlightuniverse.cosmetic;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TrailManager {

    private static final long TRAIL_DURATION_MS = 14L * 24 * 60 * 60 * 1000;

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor RED = TextColor.color(0xFF5555);

    private static final NamespacedKey TRAIL_SCROLL_TAG = NamespacedKey.fromString("starlightuniverse:trail_scroll");

    private final JavaPlugin plugin;
    private final DatabaseManager db;

    private final Map<UUID, TrailType> activeTrails = new ConcurrentHashMap<>();
    private final Map<UUID, Map<TrailType, Long>> ownedTrails = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    private BukkitTask particleTask;
    private BukkitTask expiryTask;

    public TrailManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickParticles, 2L, 2L);
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickExpiry, 20L * 60, 20L * 60);
    }

    public void shutdown() {
        if (particleTask != null) particleTask.cancel();
        if (expiryTask != null) expiryTask.cancel();
        activeTrails.clear();
        ownedTrails.clear();
        lastLocations.clear();
    }

    // ── Scroll item ──

    public ItemStack createTrailScroll() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Trail Scroll", CYAN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Right-click to open the trail menu", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Choose a particle trail that", YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("follows you for 14 days!", YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.setItemModel(NamespacedKey.fromString("starlight:trail_scroll"));
        meta.getPersistentDataContainer().set(TRAIL_SCROLL_TAG, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isTrailScroll(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(TRAIL_SCROLL_TAG, PersistentDataType.BYTE, (byte) 0) == 1;
    }

    // ── Trail selection GUI ──

    public void openTrailMenu(Player player) {
        TrailType[] types = TrailType.values();
        int rows = Math.min(6, (int) Math.ceil((double) types.length / 7) + 1);
        int size = rows * 9;

        TrailHolder holder = new TrailHolder();
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text("Choose Your Trail", CYAN));
        holder.setInventory(inv);

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);
        for (int i = 0; i < size; i++) inv.setItem(i, border);

        Map<TrailType, Long> owned = ownedTrails.getOrDefault(player.getUniqueId(), Map.of());
        TrailType currentTrail = activeTrails.get(player.getUniqueId());
        long now = System.currentTimeMillis();

        int slot = 10;
        for (TrailType type : types) {
            if (slot >= size) break;
            int col = slot % 9;
            if (col == 0) { slot++; continue; }
            if (col == 8) { slot += 2; continue; }

            ItemStack icon = new ItemStack(type.getIcon());
            ItemMeta meta = icon.getItemMeta();

            Long expiry = owned.get(type);
            boolean isOwned = expiry != null && expiry > now;
            boolean isActive = currentTrail == type;

            if (isActive) {
                meta.displayName(Component.text(type.getDisplayName(), type.getColor())
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));
                long remaining = expiry - now;
                meta.lore(List.of(
                        Component.text("ACTIVE", GREEN).decoration(TextDecoration.ITALIC, false)
                                .decoration(TextDecoration.BOLD, true),
                        Component.text("Time left: " + formatDuration(remaining), GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Click to deactivate", RED).decoration(TextDecoration.ITALIC, false)
                ));
                meta.setEnchantmentGlintOverride(true);
            } else if (isOwned) {
                meta.displayName(Component.text(type.getDisplayName(), type.getColor())
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));
                long remaining = expiry - now;
                meta.lore(List.of(
                        Component.text("OWNED", GREEN).decoration(TextDecoration.ITALIC, false),
                        Component.text("Time left: " + formatDuration(remaining), GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Click to activate", YELLOW).decoration(TextDecoration.ITALIC, false)
                ));
            } else {
                meta.displayName(Component.text(type.getDisplayName(), GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));
                meta.lore(List.of(
                        Component.text("NOT OWNED", RED).decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Use a Trail Scroll to unlock!", GRAY).decoration(TextDecoration.ITALIC, false)
                ));
            }

            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slot++;
        }

        player.openInventory(inv);
    }

    public void openScrollTrailMenu(Player player) {
        TrailType[] types = TrailType.values();
        int rows = Math.min(6, (int) Math.ceil((double) types.length / 7) + 1);
        int size = rows * 9;

        TrailHolder holder = new TrailHolder();
        holder.setScrollMode(true);
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text("Choose a Trail (Scroll)", GOLD));
        holder.setInventory(inv);

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);
        for (int i = 0; i < size; i++) inv.setItem(i, border);

        int slot = 10;
        for (TrailType type : types) {
            if (slot >= size) break;
            int col = slot % 9;
            if (col == 0) { slot++; continue; }
            if (col == 8) { slot += 2; continue; }

            ItemStack icon = new ItemStack(type.getIcon());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(type.getDisplayName(), type.getColor())
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(
                    Component.text("Click to unlock for 14 days!", YELLOW).decoration(TextDecoration.ITALIC, false)
            ));
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slot++;
        }

        player.openInventory(inv);
    }

    // ── Trail selection handling ──

    public void handleMenuClick(Player player, int slot, boolean scrollMode) {
        TrailType type = getTypeFromSlot(slot);
        if (type == null) return;

        if (scrollMode) {
            handleScrollRedeem(player, type);
        } else {
            handleTrailToggle(player, type);
        }
    }

    private void handleScrollRedeem(Player player, TrailType type) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!isTrailScroll(mainHand)) {
            Msg.error(player, "You need a Trail Scroll in your hand!");
            return;
        }

        UUID uuid = player.getUniqueId();
        Map<TrailType, Long> owned = ownedTrails.get(uuid);
        long now = System.currentTimeMillis();
        if (owned != null) {
            Long existing = owned.get(type);
            if (existing != null && existing > now) {
                Msg.error(player, "You already own this trail! Time left: " + formatDuration(existing - now));
                return;
            }
        }

        if (mainHand.getAmount() > 1) {
            mainHand.setAmount(mainHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        long expiry = now + TRAIL_DURATION_MS;
        ownedTrails.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(type, expiry);

        String lower = player.getName().toLowerCase();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_cosmetic_trails (username, trail_type, expires_at) VALUES (?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at)")) {
                ps.setString(1, lower);
                ps.setString(2, type.name());
                ps.setTimestamp(3, new Timestamp(expiry));
                ps.executeUpdate();
            }
        });

        player.closeInventory();
        Msg.success(player, "You unlocked " + type.getDisplayName() + " trail for 14 days!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

        activeTrails.put(uuid, type);
        saveTrailState(lower, type);
    }

    private void handleTrailToggle(Player player, TrailType type) {
        UUID uuid = player.getUniqueId();
        TrailType current = activeTrails.get(uuid);

        if (current == type) {
            activeTrails.remove(uuid);
            lastLocations.remove(uuid);
            player.closeInventory();
            Msg.info(player, type.getDisplayName() + " trail deactivated.");
            saveTrailState(player.getName().toLowerCase(), null);
            return;
        }

        Map<TrailType, Long> owned = ownedTrails.get(uuid);
        if (owned == null) {
            Msg.error(player, "You don't own this trail! Use a Trail Scroll to unlock it.");
            return;
        }
        Long expiry = owned.get(type);
        if (expiry == null || expiry < System.currentTimeMillis()) {
            Msg.error(player, "You don't own this trail! Use a Trail Scroll to unlock it.");
            return;
        }

        activeTrails.put(uuid, type);
        player.closeInventory();
        Msg.success(player, type.getDisplayName() + " trail activated!");
        saveTrailState(player.getName().toLowerCase(), type);
    }

    private TrailType getTypeFromSlot(int slot) {
        TrailType[] types = TrailType.values();
        int s = 10;
        for (TrailType type : types) {
            int col = s % 9;
            if (col == 0) { s++; col = s % 9; }
            if (col == 8) { s += 2; col = s % 9; }
            if (s == slot) return type;
            s++;
        }
        return null;
    }

    // ── Particle tick ──

    private void tickParticles() {
        for (Map.Entry<UUID, TrailType> entry : activeTrails.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;

            Location current = player.getLocation();
            Location last = lastLocations.get(entry.getKey());
            lastLocations.put(entry.getKey(), current.clone());

            if (last == null) continue;
            if (last.getWorld() != current.getWorld()) continue;
            if (last.distanceSquared(current) < 0.04) continue;

            TrailType type = entry.getValue();
            Location particleLoc = current.clone().add(0, 0.1, 0);

            if (type == TrailType.REDSTONE) {
                player.getWorld().spawnParticle(Particle.DUST,
                        particleLoc, 3, 0.15, 0.1, 0.15, 0,
                        new Particle.DustOptions(Color.RED, 1.0f));
            } else {
                player.getWorld().spawnParticle(type.getParticle(),
                        particleLoc, 3, 0.15, 0.1, 0.15, 0.01);
            }
        }
    }

    // ── Expiry tick ──

    private void tickExpiry() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<TrailType, Long>> entry : ownedTrails.entrySet()) {
            entry.getValue().entrySet().removeIf(e -> e.getValue() < now);
            if (entry.getValue().isEmpty()) {
                ownedTrails.remove(entry.getKey());
            }
        }

        Iterator<Map.Entry<UUID, TrailType>> it = activeTrails.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TrailType> entry = it.next();
            UUID uuid = entry.getKey();
            TrailType type = entry.getValue();

            Map<TrailType, Long> owned = ownedTrails.get(uuid);
            if (owned == null || !owned.containsKey(type)) {
                it.remove();
                lastLocations.remove(uuid);

                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    Msg.info(player, "Your " + type.getDisplayName() + " trail has expired.");
                }
            }
        }
    }

    // ── DB load/save ──

    public void loadTrails(UUID uuid, String username) {
        db.queryAsync(conn -> {
            Map<TrailType, Long> trails = new EnumMap<>(TrailType.class);
            String activeTrailType = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT trail_type, expires_at, active FROM su_cosmetic_trails WHERE username = ? AND expires_at > NOW()")) {
                ps.setString(1, username.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            TrailType type = TrailType.valueOf(rs.getString("trail_type"));
                            trails.put(type, rs.getTimestamp("expires_at").getTime());
                            if (rs.getBoolean("active")) {
                                activeTrailType = type.name();
                            }
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            return new Object[]{trails, activeTrailType};
        }).thenAccept(result -> {
            if (result == null) return;
            @SuppressWarnings("unchecked")
            Map<TrailType, Long> trails = (Map<TrailType, Long>) result[0];
            String activeType = (String) result[1];

            if (!trails.isEmpty()) {
                ownedTrails.put(uuid, new ConcurrentHashMap<>(trails));
            }

            if (activeType != null) {
                TrailType type = TrailType.valueOf(activeType);
                activeTrails.put(uuid, type);
            }
        });
    }

    public void onPlayerQuit(UUID uuid) {
        activeTrails.remove(uuid);
        ownedTrails.remove(uuid);
        lastLocations.remove(uuid);
    }

    private void saveTrailState(String username, TrailType activeType) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_cosmetic_trails SET active = 0 WHERE username = ?")) {
                ps.setString(1, username);
                ps.executeUpdate();
            }
            if (activeType != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE su_cosmetic_trails SET active = 1 WHERE username = ? AND trail_type = ?")) {
                    ps.setString(1, username);
                    ps.setString(2, activeType.name());
                    ps.executeUpdate();
                }
            }
        });
    }

    // ── Command: /trail ──

    public void handleTrailCommand(Player player) {
        openTrailMenu(player);
    }

    // ── Utility ──

    public boolean hasTrail(UUID uuid) {
        return activeTrails.containsKey(uuid);
    }

    private String formatDuration(long ms) {
        long totalMinutes = ms / 60_000;
        long days = totalMinutes / 1440;
        long hours = (totalMinutes % 1440) / 60;
        long minutes = totalMinutes % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
