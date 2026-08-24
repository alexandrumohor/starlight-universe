package com.starlightuniverse.anticheat;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiCheatManager {

    public static final int KICK_THRESHOLD = 5;
    public static final long VIOLATION_DECAY_MS = 5 * 60 * 1000L;
    public static final int MAX_HISTORY = 20;

    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JavaPlugin plugin;
    private final AdminManager adminManager;

    private final Map<UUID, Map<Violation, Integer>> violations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastViolationTime = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<ViolationEntry>> history = new ConcurrentHashMap<>();

    public AntiCheatManager(JavaPlugin plugin, AdminManager adminManager) {
        this.plugin = plugin;
        this.adminManager = adminManager;
    }

    public boolean isExempt(Player player) {
        if (player == null) return true;
        return adminManager.getAdminLevel(player.getUniqueId()) > 0;
    }

    public void flag(Player player, Violation violation, String details) {
        if (isExempt(player)) return;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastViolationTime.get(uuid);
        if (last != null && now - last > VIOLATION_DECAY_MS) {
            violations.remove(uuid);
        }
        lastViolationTime.put(uuid, now);

        Map<Violation, Integer> map = violations.computeIfAbsent(uuid, k -> new EnumMap<>(Violation.class));
        int count = map.merge(violation, 1, Integer::sum);
        int total = map.values().stream().mapToInt(Integer::intValue).sum();

        Deque<ViolationEntry> log = history.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        synchronized (log) {
            log.addFirst(new ViolationEntry(violation, details, LocalDateTime.now()));
            while (log.size() > MAX_HISTORY) log.removeLast();
        }

        notifyStaff(player, violation, details, count, total);

        if (total >= KICK_THRESHOLD) {
            violations.remove(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Component reason = Component.text("[SU] Kicked by AntiCheat: ", RED)
                        .append(Component.text(violation.getLabel(), YELLOW))
                        .append(Component.text(" (" + total + " violations)", GRAY));
                if (player.isOnline()) player.kick(reason);
            });
        }
    }

    private void notifyStaff(Player player, Violation violation, String details, int typeCount, int total) {
        Component msg = Msg.prefix()
                .append(Component.text("[AC] ", RED))
                .append(Component.text(player.getName(), WHITE))
                .append(Component.text(" flagged ", GRAY))
                .append(Component.text(violation.getLabel(), YELLOW))
                .append(Component.text(" x" + typeCount, GRAY))
                .append(Component.text(" [total " + total + "/" + KICK_THRESHOLD + "]", GRAY))
                .append(Component.text(details.isEmpty() ? "" : " — " + details, GRAY));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (adminManager.getAdminLevel(online.getUniqueId()) > 0) {
                online.sendMessage(msg);
            }
        }
        plugin.getLogger().info("[SU] [AC] " + player.getName() + " " + violation.getLabel() + " (" + details + ")");
    }

    public Map<Violation, Integer> getViolations(UUID uuid) {
        return violations.getOrDefault(uuid, Collections.emptyMap());
    }

    public List<ViolationEntry> getHistory(UUID uuid) {
        Deque<ViolationEntry> log = history.get(uuid);
        if (log == null) return Collections.emptyList();
        synchronized (log) {
            return new ArrayList<>(log);
        }
    }

    public void clear(UUID uuid) {
        violations.remove(uuid);
        lastViolationTime.remove(uuid);
        history.remove(uuid);
    }

    public record ViolationEntry(Violation violation, String details, LocalDateTime time) {
        public String formattedTime() {
            return time.format(TIME_FMT);
        }
    }
}
