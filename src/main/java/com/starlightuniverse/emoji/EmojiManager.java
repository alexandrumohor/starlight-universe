package com.starlightuniverse.emoji;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmojiManager {

    public static final String CATEGORY = "emoji_pack";
    public static final String UNLOCK_KEY = "all";

    private static final Pattern TOKEN_PATTERN = Pattern.compile(":([a-z][a-z0-9_]{1,20}):");

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;

    private final Set<UUID> unlocked = ConcurrentHashMap.newKeySet();

    public EmojiManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        String username = player.getName().toLowerCase();
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM su_player_unlocks WHERE username = ? AND category = ? AND unlock_key = ?")) {
                ps.setString(1, username);
                ps.setString(2, CATEGORY);
                ps.setString(3, UNLOCK_KEY);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }).thenAccept(has -> {
            if (Boolean.TRUE.equals(has)) unlocked.add(uuid);
        });
    }

    public void unloadPlayer(UUID uuid) {
        unlocked.remove(uuid);
    }

    public boolean isUnlocked(UUID uuid) {
        return unlocked.contains(uuid);
    }

    public void purchaseUnlock(Player player) {
        UUID uuid = player.getUniqueId();
        if (isUnlocked(uuid)) {
            Msg.info(player, "You already unlocked the emoji pack.");
            return;
        }
        if (!economy.removeGems(uuid, Emoji.UNLOCK_GEM_COST)) {
            Msg.error(player, "Not enough Gems! Need " + EconomyManager.GEMS_ICON + Emoji.UNLOCK_GEM_COST);
            return;
        }
        unlocked.add(uuid);
        String username = player.getName().toLowerCase();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO su_player_unlocks (username, category, unlock_key) VALUES (?, ?, ?)")) {
                ps.setString(1, username);
                ps.setString(2, CATEGORY);
                ps.setString(3, UNLOCK_KEY);
                ps.executeUpdate();
            }
        });
        Msg.success(player, "Emoji pack unlocked! Type :name: in chat.");
    }

    public String replaceTokens(String message, boolean unlockedForPlayer) {
        if (!unlockedForPlayer) return message;
        Matcher m = TOKEN_PATTERN.matcher(message);
        if (!m.find()) return message;
        m.reset();
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String key = m.group(1);
            Emoji e = Emoji.byName(key);
            sb.append(message, last, m.start());
            if (e != null) {
                sb.append(e.getUnicode());
            } else {
                sb.append(m.group());
            }
            last = m.end();
        }
        sb.append(message.substring(last));
        return sb.toString();
    }

    public JavaPlugin getPlugin() { return plugin; }
    public EconomyManager getEconomy() { return economy; }
}
