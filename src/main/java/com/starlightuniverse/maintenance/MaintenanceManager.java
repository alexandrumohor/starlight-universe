package com.starlightuniverse.maintenance;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;

public class MaintenanceManager {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor GREEN = TextColor.color(0x55FF55);

    public static final int STAFF_ADMIN_LEVEL = 1;
    public static final int COMMAND_ADMIN_LEVEL = 4;

    public static final int MIN_COUNTDOWN_SECONDS = 1;
    public static final int MAX_COUNTDOWN_SECONDS = 3600;

    public static final long KICK_TO_BARRIER_DELAY_TICKS = 20L; // 1 second

    private static final int[] WARN_SECONDS = {600, 300, 180, 60, 30, 10, 5, 4, 3, 2, 1};

    public static final String KICK_MESSAGE = "Server under maintenance. Check Discord for info.";
    public static final String BLOCK_MESSAGE = "Server under maintenance. Check Discord for info.";

    private static final String DB_KEY = "maintenance_active";

    private final JavaPlugin plugin;
    private final AdminManager adminManager;
    private final DatabaseManager db;

    private volatile boolean active = false;
    private volatile int countdownRemainingSec = 0;
    private BukkitTask countdownTask;

    public MaintenanceManager(JavaPlugin plugin, AdminManager adminManager, DatabaseManager db) {
        this.plugin = plugin;
        this.adminManager = adminManager;
        this.db = db;
    }

    public boolean isActive() { return active; }
    public boolean isCountdownRunning() { return countdownTask != null && countdownRemainingSec > 0; }
    public int getCountdownRemainingSec() { return countdownRemainingSec; }

    public boolean canBypass(int adminLevel) {
        return adminLevel >= STAFF_ADMIN_LEVEL;
    }

    // ── Load persistent state on plugin enable ──
    public void loadPersistentState() {
        boolean flagged = readPersistentFlag();
        if (flagged) {
            active = true;
            plugin.getLogger().info("[SU] Server booted with maintenance flag set — barrier stays up until /maintenance stop is used.");
        }
    }

    // ── Start countdown → kick + barrier flow ──
    public void startWithCountdown(Player initiator, int seconds) {
        if (active) {
            if (initiator != null) initiator.sendMessage(Component.text("[SU] ", GOLD)
                    .append(Component.text("Maintenance is already active.", RED)));
            return;
        }
        if (isCountdownRunning()) {
            if (initiator != null) initiator.sendMessage(Component.text("[SU] ", GOLD)
                    .append(Component.text("A maintenance countdown is already running (" + countdownRemainingSec + "s left).", RED)));
            return;
        }
        int total = Math.max(MIN_COUNTDOWN_SECONDS, Math.min(MAX_COUNTDOWN_SECONDS, seconds));
        countdownRemainingSec = total;
        broadcastPrefixed(Component.text("Server maintenance in " + formatDuration(total) + ".", YELLOW));

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            countdownRemainingSec--;
            if (Arrays.stream(WARN_SECONDS).anyMatch(v -> v == countdownRemainingSec)) {
                broadcastPrefixed(Component.text("Maintenance in " + formatDuration(countdownRemainingSec) + "!", YELLOW));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
                    p.showTitle(net.kyori.adventure.title.Title.title(
                            Component.text("MAINTENANCE", GOLD, TextDecoration.BOLD),
                            Component.text("in " + formatDuration(countdownRemainingSec), YELLOW),
                            net.kyori.adventure.title.Title.Times.times(
                                    java.time.Duration.ofMillis(200),
                                    java.time.Duration.ofMillis(1500),
                                    java.time.Duration.ofMillis(300))));
                }
            }
            if (countdownRemainingSec <= 0) {
                if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
                triggerKick(initiator);
            }
        }, 20L, 20L);
    }

    // ── t=0: kick everyone. +1s: raise barrier & persist. ──
    private void triggerKick(Player initiator) {
        // Auto-save world data before kicking
        Bukkit.savePlayers();
        for (World w : Bukkit.getWorlds()) {
            try { w.save(); } catch (Throwable ignored) {}
        }

        broadcastPrefixed(Component.text("Server entering maintenance. Check Discord for info.", RED));

        Component kickMsg = Component.text("[SU] ", GOLD).append(Component.text(KICK_MESSAGE, RED));
        for (Player p : List.copyOf(Bukkit.getOnlinePlayers())) {
            if (adminManager.getAdminLevel(p.getUniqueId()) >= STAFF_ADMIN_LEVEL) continue;
            p.kick(kickMsg);
        }
        plugin.getLogger().info("[SU] Maintenance activated (by "
                + (initiator != null ? initiator.getName() : "console") + ").");

        // +1s: raise barrier and persist flag
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            active = true;
            writePersistentFlag(true);
            plugin.getLogger().info("[SU] Maintenance barrier raised — no new joins allowed. "
                    + "Use /maintenance stop to lift it (survives server restart).");
        }, KICK_TO_BARRIER_DELAY_TICKS);
    }

    // ── Cancel countdown OR lift barrier manually ──
    public void stop(Player initiator) {
        boolean wasCountdown = isCountdownRunning();
        if (!active && !wasCountdown) {
            if (initiator != null) initiator.sendMessage(Component.text("[SU] ", GOLD)
                    .append(Component.text("Maintenance is not active.", RED)));
            return;
        }
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        countdownRemainingSec = 0;

        if (active) {
            active = false;
            writePersistentFlag(false);
        }
        broadcastPrefixed(Component.text("Maintenance ended. Welcome back!", GREEN));
        plugin.getLogger().info("[SU] Maintenance OFF (by " + (initiator != null ? initiator.getName() : "console") + ").");
    }

    // ── DB persistence for the barrier flag ──
    private boolean readPersistentFlag() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT data_value FROM su_server_data WHERE data_key = ?")) {
            ps.setString(1, DB_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return "1".equals(rs.getString("data_value"));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[SU] Failed to read maintenance flag: " + e.getMessage());
        }
        return false;
    }

    private void writePersistentFlag(boolean value) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO su_server_data (data_key, data_value) VALUES (?, ?) "
                             + "ON DUPLICATE KEY UPDATE data_value = VALUES(data_value)")) {
            ps.setString(1, DB_KEY);
            ps.setString(2, value ? "1" : "0");
            ps.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().warning("[SU] Failed to write maintenance flag: " + e.getMessage());
        }
    }

    private void broadcastPrefixed(Component body) {
        Component msg = Component.text("[SU] ", GOLD).append(body);
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
        plugin.getLogger().info("[SU] " + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(body));
    }

    public static String formatDuration(int seconds) {
        if (seconds <= 0) return "0s";
        if (seconds < 60) return seconds + "s";
        int m = seconds / 60;
        int s = seconds % 60;
        return s == 0 ? m + "m" : m + "m " + s + "s";
    }
}
