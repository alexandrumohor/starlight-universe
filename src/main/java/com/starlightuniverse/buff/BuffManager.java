package com.starlightuniverse.buff;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BuffManager {

    private static final long BUFF_DURATION_MS = 12 * 60 * 60 * 1000L;
    private static final int LOOT_MULTIPLIER = 5;

    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);

    private final JavaPlugin plugin;
    private final DatabaseManager db;

    private final Map<UUID, Map<BuffType, Long>> activeBuffs = new ConcurrentHashMap<>();

    public BuffManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBuffs, 20L * 30, 20L * 30);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPotionBuffs, 20L * 60, 20L * 60);
    }

    public void loadBuffs(UUID uuid, String username) {
        db.queryAsync(conn -> {
            Map<BuffType, Long> buffs = new EnumMap<>(BuffType.class);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT buff_type, expire_time FROM su_buffs WHERE username = ? AND expire_time > NOW()")) {
                ps.setString(1, username.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            BuffType type = BuffType.valueOf(rs.getString("buff_type"));
                            buffs.put(type, rs.getTimestamp("expire_time").getTime());
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            return buffs;
        }).thenAccept(buffs -> {
            if (buffs != null && !buffs.isEmpty()) {
                activeBuffs.put(uuid, new ConcurrentHashMap<>(buffs));
            }
        });
    }

    public boolean activateBuff(Player player, BuffType type) {
        UUID uuid = player.getUniqueId();
        long expireTime = System.currentTimeMillis() + BUFF_DURATION_MS;

        Map<BuffType, Long> playerBuffs = activeBuffs.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        playerBuffs.put(type, expireTime);

        applyPotionBuff(player, type);

        if (type == BuffType.FLY_MODE) {
            player.setAllowFlight(true);
        }

        String lower = player.getName().toLowerCase();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_buffs (username, buff_type, expire_time) VALUES (?, ?, FROM_UNIXTIME(?/1000)) " +
                            "ON DUPLICATE KEY UPDATE expire_time = FROM_UNIXTIME(?/1000)")) {
                ps.setString(1, lower);
                ps.setString(2, type.name());
                ps.setLong(3, expireTime);
                ps.setLong(4, expireTime);
                ps.executeUpdate();
            }
        });

        Msg.success(player, type.getDisplayName() + " activated for 12 hours!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        return true;
    }

    public boolean hasBuff(UUID uuid, BuffType type) {
        Map<BuffType, Long> playerBuffs = activeBuffs.get(uuid);
        if (playerBuffs == null) return false;
        Long expire = playerBuffs.get(type);
        if (expire == null) return false;
        if (expire < System.currentTimeMillis()) {
            playerBuffs.remove(type);
            return false;
        }
        return true;
    }

    public int getLootMultiplier(UUID uuid, BuffType type) {
        return hasBuff(uuid, type) ? LOOT_MULTIPLIER : 1;
    }

    public boolean hasGodMode(UUID uuid) { return hasBuff(uuid, BuffType.GOD_MODE); }
    public boolean hasFlyBuff(UUID uuid) { return hasBuff(uuid, BuffType.FLY_MODE); }
    public boolean hasExtraChunks(UUID uuid) { return hasBuff(uuid, BuffType.EXTRA_CHUNKS); }

    public Map<BuffType, Long> getActiveBuffs(UUID uuid) {
        return activeBuffs.getOrDefault(uuid, Map.of());
    }

    public void onPlayerQuit(UUID uuid) {
        activeBuffs.remove(uuid);
    }

    public void onPlayerJoin(Player player) {
        Map<BuffType, Long> buffs = activeBuffs.get(player.getUniqueId());
        if (buffs == null) return;

        long now = System.currentTimeMillis();
        buffs.entrySet().removeIf(e -> e.getValue() < now);

        for (BuffType type : buffs.keySet()) {
            applyPotionBuff(player, type);
            if (type == BuffType.FLY_MODE) {
                player.setAllowFlight(true);
            }
        }
    }

    private void applyPotionBuff(Player player, BuffType type) {
        int ticks = (int) ((BUFF_DURATION_MS / 1000) * 20);
        switch (type) {
            case NIGHT_VISION -> player.addPotionEffect(
                    new PotionEffect(PotionEffectType.NIGHT_VISION, ticks, 0, false, false, true));
            case JUMP_BOOST -> player.addPotionEffect(
                    new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, 1, false, false, true));
            case SPEED_WALK -> player.addPotionEffect(
                    new PotionEffect(PotionEffectType.SPEED, ticks, 1, false, false, true));
            default -> {}
        }
    }

    private void tickBuffs() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<BuffType, Long>> entry : activeBuffs.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            entry.getValue().entrySet().removeIf(e -> {
                if (e.getValue() < now) {
                    if (player != null && player.isOnline()) {
                        Msg.info(player, e.getKey().getDisplayName() + " buff has expired.");
                        if (e.getKey() == BuffType.FLY_MODE) {
                            player.setAllowFlight(false);
                            player.setFlying(false);
                        }
                    }
                    return true;
                }
                return false;
            });
            if (entry.getValue().isEmpty()) {
                activeBuffs.remove(entry.getKey());
            }
        }
    }

    private void tickPotionBuffs() {
        for (Map.Entry<UUID, Map<BuffType, Long>> entry : activeBuffs.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            for (BuffType type : entry.getValue().keySet()) {
                applyPotionBuff(player, type);
            }
        }
    }
}
