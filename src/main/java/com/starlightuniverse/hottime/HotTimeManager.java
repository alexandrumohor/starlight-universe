package com.starlightuniverse.hottime;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class HotTimeManager {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);

    public static final double MIN_MULTIPLIER = 1.1;
    public static final double MAX_MULTIPLIER = 10.0;
    public static final int MAX_DURATION_MINUTES = 240;

    private final JavaPlugin plugin;

    private volatile boolean active = false;
    private volatile double multiplier = 1.0;
    private volatile long endMillis = 0L;
    private volatile int totalSeconds = 0;

    private BossBar bossBar;
    private BukkitTask updateTask;

    public HotTimeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() { return active; }
    public double getMultiplier() { return active ? multiplier : 1.0; }
    public double getRawMultiplier() { return multiplier; }
    public long getEndMillis() { return endMillis; }
    public int getRemainingSeconds() {
        if (!active) return 0;
        long ms = endMillis - System.currentTimeMillis();
        return (int) Math.max(0, ms / 1000);
    }

    public double getMoneyMultiplier() { return getMultiplier(); }
    public double getXpMultiplier() { return getMultiplier(); }
    public double getDropMultiplier() { return getMultiplier(); }

    public int applyDropAmount(int base) {
        if (!active) return base;
        double m = getMultiplier();
        int scaled = (int) Math.round(base * m);
        return Math.max(base, scaled);
    }

    public int applyXpAmount(int base) {
        if (!active) return base;
        double m = getMultiplier();
        int scaled = (int) Math.round(base * m);
        return Math.max(base, scaled);
    }

    public double applyMoneyAmount(double base) {
        if (!active) return base;
        return base * getMultiplier();
    }

    // ── Control ──
    public boolean start(int minutes, double mult) {
        if (active) return false;
        int m = Math.max(1, Math.min(MAX_DURATION_MINUTES, minutes));
        double mm = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, mult));

        this.multiplier = mm;
        this.totalSeconds = m * 60;
        this.endMillis = System.currentTimeMillis() + totalSeconds * 1000L;
        this.active = true;

        bossBar = BossBar.bossBar(barTitle(), 1.0f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_20);
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(bossBar);

        broadcast(Component.text("HOT TIME! ", RED, TextDecoration.BOLD)
                .append(Component.text(String.format("x%.2f", multiplier), GOLD, TextDecoration.BOLD))
                .append(Component.text(" for ", WHITE))
                .append(Component.text(m + " min", YELLOW))
                .append(Component.text(" on Money, XP and drops!", WHITE)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("[SU] Hot Time started: x" + multiplier + " for " + m + " min.");
        return true;
    }

    public boolean stop() {
        if (!active) return false;
        active = false;
        endMillis = 0;
        totalSeconds = 0;
        if (bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bossBar);
            bossBar = null;
        }
        if (updateTask != null) { updateTask.cancel(); updateTask = null; }
        broadcast(Component.text("Hot Time has ended.", YELLOW));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 0.5f);
        }
        multiplier = 1.0;
        return true;
    }

    private void tick() {
        int remaining = getRemainingSeconds();
        if (remaining <= 0) {
            stop();
            return;
        }
        if (bossBar != null) {
            float progress = totalSeconds > 0 ? (float) remaining / totalSeconds : 0f;
            bossBar.progress(Math.max(0f, Math.min(1f, progress)));
            bossBar.name(barTitle());
        }
    }

    private Component barTitle() {
        int remaining = getRemainingSeconds();
        int m = remaining / 60;
        int s = remaining % 60;
        String time = String.format("%d:%02d", m, s);
        return Component.text("HOT TIME ", RED, TextDecoration.BOLD)
                .append(Component.text(String.format("x%.2f", multiplier), GOLD, TextDecoration.BOLD))
                .append(Component.text(" — ", GRAY))
                .append(Component.text(time + " left", YELLOW, TextDecoration.BOLD));
    }

    public void addPlayerToBar(Player player) {
        if (active && bossBar != null) player.showBossBar(bossBar);
    }

    public void removePlayerFromBar(Player player) {
        if (bossBar != null) player.hideBossBar(bossBar);
    }

    private void broadcast(Component body) {
        Component msg = Component.text("[SU] ", GOLD).append(body);
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(msg);
    }
}
