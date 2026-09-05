package com.starlightuniverse.cosmetic;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
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

public class DisguiseManager {

    private static final long DISGUISE_DURATION_MS = 14L * 24 * 60 * 60 * 1000;

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor RED = TextColor.color(0xFF5555);

    private static final NamespacedKey DISGUISE_SCROLL_TAG = NamespacedKey.fromString("starlightuniverse:disguise_scroll");

    private final JavaPlugin plugin;
    private final DatabaseManager db;

    private final Map<UUID, DisguiseData> activeDisguises = new ConcurrentHashMap<>();
    private final Map<UUID, Map<DisguiseType, Long>> ownedDisguises = new ConcurrentHashMap<>();

    private BukkitTask followTask;
    private BukkitTask expiryTask;

    public DisguiseManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickFollow, 1L, 1L);
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickExpiry, 20L * 60, 20L * 60);
    }

    public void shutdown() {
        if (followTask != null) followTask.cancel();
        if (expiryTask != null) expiryTask.cancel();
        for (Map.Entry<UUID, DisguiseData> entry : activeDisguises.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            DisguiseData data = entry.getValue();
            if (data.entity != null && data.entity.isValid()) {
                data.entity.remove();
            }
            if (player != null && player.isOnline()) {
                showPlayerToAll(player);
            }
        }
        activeDisguises.clear();
    }

    // ── Scroll item ──

    public ItemStack createDisguiseScroll() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Disguise Scroll", CYAN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Right-click to open the disguise menu", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Transform into a mob for", YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("other players for 14 days!", YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.setItemModel(NamespacedKey.fromString("starlight:disguise_scroll"));
        meta.getPersistentDataContainer().set(DISGUISE_SCROLL_TAG, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isDisguiseScroll(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(DISGUISE_SCROLL_TAG, PersistentDataType.BYTE, (byte) 0) == 1;
    }

    // ── Disguise selection GUI ──

    public void openDisguiseMenu(Player player) {
        DisguiseType[] types = DisguiseType.values();
        int rows = Math.min(6, (int) Math.ceil((double) types.length / 7) + 1);
        int size = rows * 9;

        DisguiseHolder holder = new DisguiseHolder();
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text("Choose Your Disguise", CYAN));
        holder.setInventory(inv);

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);
        for (int i = 0; i < size; i++) inv.setItem(i, border);

        Map<DisguiseType, Long> owned = ownedDisguises.getOrDefault(player.getUniqueId(), Map.of());
        DisguiseData currentDisguise = activeDisguises.get(player.getUniqueId());
        long now = System.currentTimeMillis();

        int slot = 10;
        for (DisguiseType type : types) {
            if (slot >= size) break;
            int col = slot % 9;
            if (col == 0) { slot++; continue; }
            if (col == 8) { slot += 2; continue; }

            ItemStack icon = new ItemStack(type.getIcon());
            ItemMeta meta = icon.getItemMeta();

            Long expiry = owned.get(type);
            boolean isOwned = expiry != null && expiry > now;
            boolean isActive = currentDisguise != null && currentDisguise.type == type;

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
                        Component.text("Use a Disguise Scroll to unlock!", GRAY).decoration(TextDecoration.ITALIC, false)
                ));
            }

            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slot++;
        }

        player.openInventory(inv);
    }

    public void openScrollDisguiseMenu(Player player) {
        DisguiseType[] types = DisguiseType.values();
        int rows = Math.min(6, (int) Math.ceil((double) types.length / 7) + 1);
        int size = rows * 9;

        DisguiseHolder holder = new DisguiseHolder();
        holder.setScrollMode(true);
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text("Choose a Disguise (Scroll)", GOLD));
        holder.setInventory(inv);

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);
        for (int i = 0; i < size; i++) inv.setItem(i, border);

        int slot = 10;
        for (DisguiseType type : types) {
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

    // ── Disguise selection handling ──

    public void handleMenuClick(Player player, int slot, boolean scrollMode) {
        DisguiseType type = getTypeFromSlot(slot);
        if (type == null) return;

        if (scrollMode) {
            handleScrollRedeem(player, type);
        } else {
            handleDisguiseToggle(player, type);
        }
    }

    private void handleScrollRedeem(Player player, DisguiseType type) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!isDisguiseScroll(mainHand)) {
            Msg.error(player, "You need a Disguise Scroll in your hand!");
            return;
        }

        UUID uuid = player.getUniqueId();
        Map<DisguiseType, Long> owned = ownedDisguises.get(uuid);
        long now = System.currentTimeMillis();
        if (owned != null) {
            Long existing = owned.get(type);
            if (existing != null && existing > now) {
                Msg.error(player, "You already own this disguise! Time left: " + formatDuration(existing - now));
                return;
            }
        }

        if (mainHand.getAmount() > 1) {
            mainHand.setAmount(mainHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        long expiry = now + DISGUISE_DURATION_MS;
        ownedDisguises.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(type, expiry);

        String lower = player.getName().toLowerCase();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_cosmetic_disguises (username, disguise_type, expires_at) VALUES (?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at)")) {
                ps.setString(1, lower);
                ps.setString(2, type.name());
                ps.setTimestamp(3, new Timestamp(expiry));
                ps.executeUpdate();
            }
        });

        player.closeInventory();
        Msg.success(player, "You unlocked " + type.getDisplayName() + " disguise for 14 days!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

        undisguise(player);
        disguise(player, type);
    }

    private void handleDisguiseToggle(Player player, DisguiseType type) {
        UUID uuid = player.getUniqueId();
        DisguiseData current = activeDisguises.get(uuid);

        if (current != null && current.type == type) {
            undisguise(player);
            player.closeInventory();
            Msg.info(player, type.getDisplayName() + " disguise deactivated.");
            saveDisguiseState(player.getName().toLowerCase(), null);
            return;
        }

        Map<DisguiseType, Long> owned = ownedDisguises.get(uuid);
        if (owned == null) {
            Msg.error(player, "You don't own this disguise! Use a Disguise Scroll to unlock it.");
            return;
        }
        Long expiry = owned.get(type);
        if (expiry == null || expiry < System.currentTimeMillis()) {
            Msg.error(player, "You don't own this disguise! Use a Disguise Scroll to unlock it.");
            return;
        }

        undisguise(player);
        disguise(player, type);
        player.closeInventory();
        Msg.success(player, type.getDisplayName() + " disguise activated!");
    }

    private DisguiseType getTypeFromSlot(int slot) {
        DisguiseType[] types = DisguiseType.values();
        int s = 10;
        for (DisguiseType type : types) {
            int col = s % 9;
            if (col == 0) { s++; col = s % 9; }
            if (col == 8) { s += 2; col = s % 9; }
            if (s == slot) return type;
            s++;
        }
        return null;
    }

    // ── Disguise activate/deactivate ──

    public void disguise(Player player, DisguiseType type) {
        UUID uuid = player.getUniqueId();
        undisguise(player);

        hidePlayerFromAll(player);

        Location spawnLoc = player.getLocation();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Entity entity = player.getWorld().spawnEntity(spawnLoc, type.getEntityType());
            if (entity instanceof LivingEntity living) {
                living.setInvulnerable(true);
                living.setSilent(true);
                living.setCollidable(false);
                living.setPersistent(false);
                living.setRemoveWhenFarAway(false);
                if (living instanceof Mob mob) {
                    mob.setAI(false);
                }
                if (living instanceof Ageable ageable) {
                    ageable.setAdult();
                    ageable.setAgeLock(true);
                }
            }

            entity.customName(Component.text(player.getName(), type.getColor()));
            entity.setCustomNameVisible(true);
            entity.addScoreboardTag("su_disguise");
            entity.addScoreboardTag("su_disguise_" + uuid);

            activeDisguises.put(uuid, new DisguiseData(type, entity));
            saveDisguiseState(player.getName().toLowerCase(), type);
        });
    }

    public void undisguise(Player player) {
        UUID uuid = player.getUniqueId();
        DisguiseData data = activeDisguises.remove(uuid);
        if (data != null && data.entity != null && data.entity.isValid()) {
            data.entity.remove();
        }
        showPlayerToAll(player);
    }

    private void hidePlayerFromAll(Player disguised) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(disguised)) {
                other.hidePlayer(plugin, disguised);
            }
        }
    }

    private void showPlayerToAll(Player disguised) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(disguised)) {
                other.showPlayer(plugin, disguised);
            }
        }
    }

    // ── Follow tick ──

    private void tickFollow() {
        for (Map.Entry<UUID, DisguiseData> entry : activeDisguises.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            DisguiseData data = entry.getValue();
            if (player == null || !player.isOnline() || data.entity == null || !data.entity.isValid()) {
                continue;
            }

            Entity mob = data.entity;
            Location playerLoc = player.getLocation();

            if (!playerLoc.getWorld().equals(mob.getLocation().getWorld())) {
                mob.teleport(playerLoc);
                continue;
            }

            Location target = playerLoc.clone();
            target.setYaw(playerLoc.getYaw());
            target.setPitch(playerLoc.getPitch());
            mob.teleport(target);
        }
    }

    // ── Expiry tick ──

    private void tickExpiry() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<DisguiseType, Long>> entry : ownedDisguises.entrySet()) {
            entry.getValue().entrySet().removeIf(e -> e.getValue() < now);
            if (entry.getValue().isEmpty()) {
                ownedDisguises.remove(entry.getKey());
            }
        }

        Iterator<Map.Entry<UUID, DisguiseData>> it = activeDisguises.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DisguiseData> entry = it.next();
            UUID uuid = entry.getKey();
            DisguiseData data = entry.getValue();

            Map<DisguiseType, Long> owned = ownedDisguises.get(uuid);
            if (owned == null || !owned.containsKey(data.type)) {
                if (data.entity != null && data.entity.isValid()) {
                    data.entity.remove();
                }
                it.remove();

                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    showPlayerToAll(player);
                    Msg.info(player, "Your " + data.type.getDisplayName() + " disguise has expired.");
                }
            }
        }
    }

    // ── DB load/save ──

    public void loadDisguises(UUID uuid, String username) {
        db.queryAsync(conn -> {
            Map<DisguiseType, Long> disguises = new EnumMap<>(DisguiseType.class);
            String activeDisguiseType = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT disguise_type, expires_at, active FROM su_cosmetic_disguises WHERE username = ? AND expires_at > NOW()")) {
                ps.setString(1, username.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            DisguiseType type = DisguiseType.valueOf(rs.getString("disguise_type"));
                            disguises.put(type, rs.getTimestamp("expires_at").getTime());
                            if (rs.getBoolean("active")) {
                                activeDisguiseType = type.name();
                            }
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            return new Object[]{disguises, activeDisguiseType};
        }).thenAccept(result -> {
            if (result == null) return;
            @SuppressWarnings("unchecked")
            Map<DisguiseType, Long> disguises = (Map<DisguiseType, Long>) result[0];
            String activeType = (String) result[1];

            if (!disguises.isEmpty()) {
                ownedDisguises.put(uuid, new ConcurrentHashMap<>(disguises));
            }

            if (activeType != null) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    DisguiseType type = DisguiseType.valueOf(activeType);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            disguise(player, type);
                        }
                    }, 40L);
                }
            }
        });
    }

    public void onPlayerQuit(UUID uuid) {
        DisguiseData data = activeDisguises.remove(uuid);
        if (data != null && data.entity != null && data.entity.isValid()) {
            data.entity.remove();
        }
        ownedDisguises.remove(uuid);
    }

    public void onPlayerJoin(Player joiner) {
        for (Map.Entry<UUID, DisguiseData> entry : activeDisguises.entrySet()) {
            Player disguised = Bukkit.getPlayer(entry.getKey());
            if (disguised != null && disguised.isOnline() && !disguised.equals(joiner)) {
                joiner.hidePlayer(plugin, disguised);
            }
        }
    }

    private void saveDisguiseState(String username, DisguiseType activeType) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_cosmetic_disguises SET active = 0 WHERE username = ?")) {
                ps.setString(1, username);
                ps.executeUpdate();
            }
            if (activeType != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE su_cosmetic_disguises SET active = 1 WHERE username = ? AND disguise_type = ?")) {
                    ps.setString(1, username);
                    ps.setString(2, activeType.name());
                    ps.executeUpdate();
                }
            }
        });
    }

    // ── Command: /disguise ──

    public void handleDisguiseCommand(Player player) {
        openDisguiseMenu(player);
    }

    // ── Utility ──

    public boolean hasDisguise(UUID uuid) {
        return activeDisguises.containsKey(uuid);
    }

    public boolean isDisguiseEntity(Entity entity) {
        return entity.getScoreboardTags().contains("su_disguise");
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

    // ── Inner data class ──

    public static class DisguiseData {
        public final DisguiseType type;
        public Entity entity;

        public DisguiseData(DisguiseType type, Entity entity) {
            this.type = type;
            this.entity = entity;
        }
    }
}
