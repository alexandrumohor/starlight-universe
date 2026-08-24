package com.starlightuniverse.announce;

import com.starlightuniverse.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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

public class AnnouncementManager {

    public static final int MAX_MESSAGE_LENGTH = 200;
    public static final int MIN_FREQUENCY_MINUTES = 1;
    public static final int MAX_FREQUENCY_MINUTES = 1440;
    public static final int MIN_DURATION_SECONDS = 1;
    public static final int MAX_DURATION_SECONDS = 30;
    public static final int MAX_ANNOUNCEMENTS = 45;

    public static final long TASK_INTERVAL_TICKS = 20L;

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor DARK_GRAY = TextColor.color(0x555555);

    private final JavaPlugin plugin;
    private final DatabaseManager db;

    private final Map<Integer, Announcement> byId = new ConcurrentHashMap<>();

    public enum InputStage { MESSAGE_CREATE, MESSAGE_EDIT, FREQUENCY, DURATION }

    public static class PendingInput {
        public final InputStage stage;
        public final int targetId; // -1 for new
        public PendingInput(InputStage stage, int targetId) {
            this.stage = stage;
            this.targetId = targetId;
        }
    }

    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    private BukkitTask broadcastTask;

    public AnnouncementManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void initialize() {
        loadAll();
    }

    public void start() {
        broadcastTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick,
                TASK_INTERVAL_TICKS, TASK_INTERVAL_TICKS);
        plugin.getLogger().info("[SU] Announcement broadcast task started.");
    }

    public void shutdown() {
        if (broadcastTask != null) broadcastTask.cancel();
    }

    // ── Load ──
    private void loadAll() {
        db.queryAsync(conn -> {
            List<Announcement> list = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT id, message, ann_type, frequency_minutes, duration_seconds, enabled FROM su_announcements")) {
                while (rs.next()) {
                    AnnouncementType t = AnnouncementType.fromName(rs.getString("ann_type"));
                    if (t == null) t = AnnouncementType.INFO;
                    list.add(new Announcement(
                            rs.getInt("id"),
                            rs.getString("message"),
                            t,
                            rs.getInt("frequency_minutes"),
                            rs.getInt("duration_seconds"),
                            rs.getBoolean("enabled")));
                }
            }
            return list;
        }).thenAccept(list -> {
            if (list == null) return;
            for (Announcement a : list) byId.put(a.getId(), a);
            plugin.getLogger().info("[SU] Loaded " + list.size() + " announcements.");
        });
    }

    // ── CRUD ──
    public Collection<Announcement> getAll() {
        List<Announcement> list = new ArrayList<>(byId.values());
        list.sort(Comparator.comparingInt(Announcement::getId));
        return list;
    }

    public Announcement get(int id) { return byId.get(id); }

    public void create(Player creator, String message, AnnouncementType type,
                       int frequencyMinutes, int durationSeconds) {
        if (byId.size() >= MAX_ANNOUNCEMENTS) {
            creator.sendMessage(Component.text("[SU] ", GOLD)
                    .append(Component.text("Max announcements reached (" + MAX_ANNOUNCEMENTS + ")!", RED)));
            return;
        }
        String msg = truncate(message, MAX_MESSAGE_LENGTH);
        int freq = Math.max(MIN_FREQUENCY_MINUTES, Math.min(MAX_FREQUENCY_MINUTES, frequencyMinutes));
        int dur = Math.max(MIN_DURATION_SECONDS, Math.min(MAX_DURATION_SECONDS, durationSeconds));

        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_announcements (message, ann_type, frequency_minutes, duration_seconds, enabled) VALUES (?,?,?,?,1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, msg);
                ps.setString(2, type.name());
                ps.setInt(3, freq);
                ps.setInt(4, dur);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
            return -1;
        }).thenAccept(id -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (id == null || id <= 0) {
                creator.sendMessage(Component.text("[SU] ", GOLD)
                        .append(Component.text("Failed to create announcement!", RED)));
                return;
            }
            Announcement a = new Announcement(id, msg, type, freq, dur, true);
            byId.put(id, a);
            creator.sendMessage(Component.text("[SU] ", GOLD)
                    .append(Component.text("Announcement created (id " + id + ").", GREEN)));
            openListGui(creator);
        }));
    }

    public void delete(Player who, int id) {
        Announcement a = byId.remove(id);
        if (a == null) return;
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM su_announcements WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
        });
        who.sendMessage(Component.text("[SU] ", GOLD)
                .append(Component.text("Deleted announcement #" + id + ".", RED)));
    }

    public void toggle(Player who, int id) {
        Announcement a = byId.get(id);
        if (a == null) return;
        a.setEnabled(!a.isEnabled());
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_announcements SET enabled = ? WHERE id = ?")) {
                ps.setBoolean(1, a.isEnabled());
                ps.setInt(2, id);
                ps.executeUpdate();
            }
        });
        who.sendMessage(Component.text("[SU] ", GOLD)
                .append(Component.text("Announcement #" + id + " is now " + (a.isEnabled() ? "ENABLED" : "DISABLED") + ".",
                        a.isEnabled() ? GREEN : RED)));
    }

    public void setMessage(int id, String message) {
        Announcement a = byId.get(id);
        if (a == null) return;
        String msg = truncate(message, MAX_MESSAGE_LENGTH);
        a.setMessage(msg);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_announcements SET message = ? WHERE id = ?")) {
                ps.setString(1, msg);
                ps.setInt(2, id);
                ps.executeUpdate();
            }
        });
    }

    public void setType(int id, AnnouncementType type) {
        Announcement a = byId.get(id);
        if (a == null) return;
        a.setType(type);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_announcements SET ann_type = ? WHERE id = ?")) {
                ps.setString(1, type.name());
                ps.setInt(2, id);
                ps.executeUpdate();
            }
        });
    }

    public void setFrequency(int id, int frequencyMinutes) {
        Announcement a = byId.get(id);
        if (a == null) return;
        int freq = Math.max(MIN_FREQUENCY_MINUTES, Math.min(MAX_FREQUENCY_MINUTES, frequencyMinutes));
        a.setFrequencyMinutes(freq);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_announcements SET frequency_minutes = ? WHERE id = ?")) {
                ps.setInt(1, freq);
                ps.setInt(2, id);
                ps.executeUpdate();
            }
        });
    }

    public void setDuration(int id, int durationSeconds) {
        Announcement a = byId.get(id);
        if (a == null) return;
        int dur = Math.max(MIN_DURATION_SECONDS, Math.min(MAX_DURATION_SECONDS, durationSeconds));
        a.setDurationSeconds(dur);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_announcements SET duration_seconds = ? WHERE id = ?")) {
                ps.setInt(1, dur);
                ps.setInt(2, id);
                ps.executeUpdate();
            }
        });
    }

    // ── Pending chat input ──
    public PendingInput getPending(UUID uuid) { return pending.get(uuid); }
    public void setPending(UUID uuid, PendingInput p) { pending.put(uuid, p); }
    public void clearPending(UUID uuid) { pending.remove(uuid); }
    public boolean hasPending(UUID uuid) { return pending.containsKey(uuid); }

    // ── Broadcast tick ──
    private void tick() {
        long now = System.currentTimeMillis();
        for (Announcement a : byId.values()) {
            if (!a.isEnabled()) continue;
            long freqMillis = a.getFrequencyMinutes() * 60_000L;
            if (now - a.getLastBroadcastMillis() >= freqMillis) {
                a.setLastBroadcastMillis(now);
                broadcast(a);
            }
        }
    }

    public void broadcast(Announcement a) {
        AnnouncementType type = a.getType();
        Component prefix = Component.text("[", DARK_GRAY)
                .append(Component.text(type.getDisplayName(), type.getColor(), TextDecoration.BOLD))
                .append(Component.text("] ", DARK_GRAY));
        Component msg = Component.text(a.getMessage(), type.getColor());
        Component full = prefix.append(msg);

        Component actionbar = Component.text(type.getDisplayName() + " • ", type.getColor(), TextDecoration.BOLD)
                .append(Component.text(a.getMessage(), WHITE));

        Sound sound = switch (type) {
            case ALERT -> Sound.BLOCK_NOTE_BLOCK_BASS;
            case EVENT -> Sound.UI_TOAST_CHALLENGE_COMPLETE;
            case UPDATE -> Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            case TIP -> Sound.BLOCK_NOTE_BLOCK_PLING;
            default -> Sound.BLOCK_NOTE_BLOCK_HAT;
        };

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(full);
            p.sendActionBar(actionbar);
            p.playSound(p.getLocation(), sound, 0.6f, 1.0f);
        }

        int reps = Math.max(1, a.getDurationSeconds());
        for (int i = 1; i < reps; i++) {
            final int delayTicks = i * 20;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) p.sendActionBar(actionbar);
            }, delayTicks);
        }
    }

    // ── GUI: list ──
    public void openListGui(Player player) {
        Inventory inv = Bukkit.createInventory(new AnnouncementHolder(AnnouncementHolder.Type.LIST, -1),
                54, Component.text("Announcements", GOLD, TextDecoration.BOLD));

        for (int i = 45; i < 54; i++) inv.setItem(i, borderPane());

        List<Announcement> all = new ArrayList<>(getAll());
        int slot = 0;
        for (Announcement a : all) {
            if (slot >= 45) break;
            inv.setItem(slot, buildAnnouncementItem(a));
            slot++;
        }

        // Create button (slot 49)
        ItemStack create = simpleItem(Material.WRITABLE_BOOK,
                Component.text("+ Create New Announcement", GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Click to start a chat wizard", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("You'll type: message, then pick", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("frequency, duration, type", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Currently: " + all.size() + " / " + MAX_ANNOUNCEMENTS, YELLOW).decoration(TextDecoration.ITALIC, false));
        inv.setItem(49, create);

        ItemStack close = simpleItem(Material.BARRIER,
                Component.text("Close", RED).decoration(TextDecoration.ITALIC, false));
        inv.setItem(53, close);

        AnnouncementHolder h = (AnnouncementHolder) inv.getHolder();
        h.setInventory(inv);
        player.openInventory(inv);
    }

    private ItemStack buildAnnouncementItem(Announcement a) {
        ItemStack item = new ItemStack(a.getType().getIcon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("#" + a.getId() + " ", DARK_GRAY)
                .append(Component.text("[" + a.getType().getDisplayName() + "]", a.getType().getColor(), TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Message: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(shortenMsg(a.getMessage(), 40), WHITE)));
        lore.add(Component.text("Frequency: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("every " + a.getFrequencyMinutes() + " min", YELLOW)));
        lore.add(Component.text("Duration: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(a.getDurationSeconds() + "s actionbar", YELLOW)));
        lore.add(Component.text("Status: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(a.isEnabled() ? "ENABLED" : "DISABLED",
                        a.isEnabled() ? GREEN : RED, TextDecoration.BOLD)));
        lore.add(Component.empty());
        lore.add(Component.text("Left-click: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("toggle enable", WHITE)));
        lore.add(Component.text("Right-click: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("edit settings", WHITE)));
        lore.add(Component.text("Shift + Right-click: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("delete", RED)));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── GUI: edit ──
    public void openEditGui(Player player, int id) {
        Announcement a = byId.get(id);
        if (a == null) { openListGui(player); return; }

        Inventory inv = Bukkit.createInventory(new AnnouncementHolder(AnnouncementHolder.Type.EDIT, id),
                45, Component.text("Edit Announcement #" + id, GOLD, TextDecoration.BOLD));

        for (int i = 0; i < 45; i++) inv.setItem(i, borderPane());

        // Preview
        inv.setItem(4, buildAnnouncementItem(a));

        // Change message (slot 19)
        inv.setItem(19, simpleItem(Material.WRITABLE_BOOK,
                Component.text("Change Message", YELLOW, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Click to enter a new message in chat", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Type 'cancel' to abort", GRAY).decoration(TextDecoration.ITALIC, false)));

        // Change type (slot 21)
        inv.setItem(21, simpleItem(a.getType().getIcon(),
                Component.text("Change Type: ", YELLOW).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(a.getType().getDisplayName(), a.getType().getColor(), TextDecoration.BOLD)),
                Component.text("Click to open the type picker", GRAY).decoration(TextDecoration.ITALIC, false)));

        // Change frequency (slot 23)
        inv.setItem(23, simpleItem(Material.CLOCK,
                Component.text("Change Frequency: ", YELLOW).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(a.getFrequencyMinutes() + " min", WHITE, TextDecoration.BOLD)),
                Component.text("Left-click: -1 min", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click: +1 min", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Shift-left: -10 min", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Shift-right: +10 min", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Range: " + MIN_FREQUENCY_MINUTES + " to " + MAX_FREQUENCY_MINUTES + " min", DARK_GRAY).decoration(TextDecoration.ITALIC, false)));

        // Change duration (slot 25)
        inv.setItem(25, simpleItem(Material.REPEATER,
                Component.text("Change Duration: ", YELLOW).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(a.getDurationSeconds() + "s", WHITE, TextDecoration.BOLD)),
                Component.text("Left-click: -1s", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click: +1s", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Range: " + MIN_DURATION_SECONDS + " to " + MAX_DURATION_SECONDS + "s", DARK_GRAY).decoration(TextDecoration.ITALIC, false)));

        // Toggle enable (slot 31)
        inv.setItem(31, simpleItem(a.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text(a.isEnabled() ? "ENABLED" : "DISABLED",
                        a.isEnabled() ? GREEN : RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Click to toggle", GRAY).decoration(TextDecoration.ITALIC, false)));

        // Test broadcast (slot 39)
        inv.setItem(39, simpleItem(Material.BELL,
                Component.text("Broadcast Now", GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Send this announcement to all players", GRAY).decoration(TextDecoration.ITALIC, false)));

        // Delete (slot 41)
        inv.setItem(41, simpleItem(Material.BARRIER,
                Component.text("DELETE", RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Shift + Left-click to confirm", GRAY).decoration(TextDecoration.ITALIC, false)));

        // Back (slot 36)
        inv.setItem(36, simpleItem(Material.ARROW,
                Component.text("Back", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Return to the announcement list", GRAY).decoration(TextDecoration.ITALIC, false)));

        AnnouncementHolder h = (AnnouncementHolder) inv.getHolder();
        h.setInventory(inv);
        player.openInventory(inv);
    }

    // ── GUI: type picker ──
    public void openTypePickerGui(Player player, int id) {
        Inventory inv = Bukkit.createInventory(new AnnouncementHolder(AnnouncementHolder.Type.TYPE_PICKER, id),
                27, Component.text("Pick Type", GOLD, TextDecoration.BOLD));

        for (int i = 0; i < 27; i++) inv.setItem(i, borderPane());

        int[] slots = {10, 12, 13, 14, 16};
        AnnouncementType[] types = AnnouncementType.values();
        for (int i = 0; i < types.length && i < slots.length; i++) {
            AnnouncementType t = types[i];
            inv.setItem(slots[i], simpleItem(t.getIcon(),
                    Component.text(t.getDisplayName(), t.getColor(), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                    Component.text("Click to select this type", GRAY).decoration(TextDecoration.ITALIC, false)));
        }

        AnnouncementHolder h = (AnnouncementHolder) inv.getHolder();
        h.setInventory(inv);
        player.openInventory(inv);
    }

    // ── Helpers ──
    private static ItemStack simpleItem(Material material, Component displayName, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(displayName);
        List<Component> loreList = new ArrayList<>(Arrays.asList(lore));
        meta.lore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack borderPane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    private static String shortenMsg(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
