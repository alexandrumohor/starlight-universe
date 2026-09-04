package com.starlightuniverse.booster;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BoosterManager {

    private final JavaPlugin plugin;
    private final DatabaseManager db;

    private final Map<UUID, Map<BoosterType, ActiveBooster>> activeBoosters = new ConcurrentHashMap<>();

    private record ActiveBooster(double multiplier, long expireTime) {}

    public BoosterManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBoosters, 20L, 20L);
    }

    public void loadPlayer(UUID uuid, String username) {
        db.queryAsync(conn -> {
            Map<BoosterType, ActiveBooster> boosters = new EnumMap<>(BoosterType.class);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT booster_type, multiplier, expire_time FROM su_boosters WHERE username = ? AND expire_time > NOW()")) {
                ps.setString(1, username.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            BoosterType type = BoosterType.valueOf(rs.getString("booster_type"));
                            double mult = rs.getDouble("multiplier");
                            long expire = rs.getTimestamp("expire_time").getTime();
                            boosters.put(type, new ActiveBooster(mult, expire));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            return boosters;
        }).thenAccept(boosters -> {
            if (boosters != null && !boosters.isEmpty()) {
                activeBoosters.put(uuid, new ConcurrentHashMap<>(boosters));
            }
        });
    }

    public void activate(Player player, BoosterType type, double multiplier, int durationMinutes) {
        UUID uuid = player.getUniqueId();
        Map<BoosterType, ActiveBooster> playerBoosters = activeBoosters
                .computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());

        ActiveBooster current = playerBoosters.get(type);
        if (current != null && current.multiplier > multiplier && current.expireTime > System.currentTimeMillis()) {
            Msg.error(player, "You already have a better " + type.getDisplayName() + " active! ("
                    + String.format("%.1fx", current.multiplier) + ")");
            return;
        }

        long expireTime = System.currentTimeMillis() + (durationMinutes * 60_000L);
        playerBoosters.put(type, new ActiveBooster(multiplier, expireTime));

        String lower = player.getName().toLowerCase();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_boosters (username, booster_type, multiplier, expire_time) VALUES (?, ?, ?, FROM_UNIXTIME(?/1000)) "
                            + "ON DUPLICATE KEY UPDATE multiplier = VALUES(multiplier), expire_time = VALUES(expire_time)")) {
                ps.setString(1, lower);
                ps.setString(2, type.name());
                ps.setDouble(3, multiplier);
                ps.setLong(4, expireTime);
                ps.executeUpdate();
            }
        });

        Msg.success(player, type.getDisplayName() + " activated! " + String.format("%.1fx", multiplier)
                + " for " + durationMinutes + " minutes!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
    }

    public double getMultiplier(UUID uuid, BoosterType type) {
        Map<BoosterType, ActiveBooster> playerBoosters = activeBoosters.get(uuid);
        if (playerBoosters == null) return 1.0;
        ActiveBooster b = playerBoosters.get(type);
        if (b == null || b.expireTime < System.currentTimeMillis()) return 1.0;
        return b.multiplier;
    }

    public boolean hasBooster(UUID uuid, BoosterType type) {
        return getMultiplier(uuid, type) > 1.0;
    }

    public Map<BoosterType, double[]> getActiveBoosters(UUID uuid) {
        Map<BoosterType, ActiveBooster> playerBoosters = activeBoosters.get(uuid);
        if (playerBoosters == null) return Map.of();
        long now = System.currentTimeMillis();
        Map<BoosterType, double[]> result = new EnumMap<>(BoosterType.class);
        for (Map.Entry<BoosterType, ActiveBooster> e : playerBoosters.entrySet()) {
            if (e.getValue().expireTime > now) {
                result.put(e.getKey(), new double[]{e.getValue().multiplier, e.getValue().expireTime});
            }
        }
        return result;
    }

    public void onPlayerQuit(UUID uuid) {
        activeBoosters.remove(uuid);
    }

    public void shutdown() {
        activeBoosters.clear();
    }

    private void tickBoosters() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<BoosterType, ActiveBooster>> entry : activeBoosters.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            entry.getValue().entrySet().removeIf(e -> {
                if (e.getValue().expireTime < now) {
                    if (player != null && player.isOnline()) {
                        Msg.info(player, "Your " + e.getKey().getDisplayName() + " has expired.");
                    }
                    return true;
                }
                return false;
            });
            if (entry.getValue().isEmpty()) {
                activeBoosters.remove(entry.getKey());
            }
        }
    }
}
