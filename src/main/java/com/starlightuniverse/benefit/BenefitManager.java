package com.starlightuniverse.benefit;

import com.starlightuniverse.chat.ChatManager;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.premium.PremiumManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BenefitManager {

    public static final String CAT_TRAIL = "trail";
    public static final String CAT_GLOW = "glow";
    public static final String CAT_KILL = "kill_effect";
    public static final String CAT_PREFIX = "prefix";
    public static final String CAT_NAME_COLOR = "name_color";
    public static final String CAT_CHAT_COLOR = "chat_color";
    public static final String CAT_JOIN_MSG = "join_msg";
    public static final String CAT_QUIT_MSG = "quit_msg";

    public static final int PREFIX_UNLOCK_GEMS = 200;
    public static final int NAME_COLOR_GEMS = 100;
    public static final int CHAT_COLOR_GEMS = 100;
    public static final int JOIN_QUIT_STARS = 15;

    public static final int[] TRAIL_GEM_COSTS = {100, 150, 200, 250, 300, 200, 100, 150, 250, 200};

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;
    private final PremiumManager premiumManager;
    private final ChatManager chatManager;

    private final Map<UUID, String> customPrefixCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> customJoinMsgCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> customQuitMsgCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeGlowCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeKillEffectCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> unlockCache = new ConcurrentHashMap<>();

    public BenefitManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy,
                          PremiumManager premiumManager, ChatManager chatManager) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
        this.premiumManager = premiumManager;
        this.chatManager = chatManager;
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        String username = player.getName().toLowerCase();
        db.queryAsync(conn -> {
            String[] cols = new String[5];
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT custom_prefix, custom_join_msg, custom_quit_msg, active_glow, active_kill_effect " +
                            "FROM su_players WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        cols[0] = rs.getString(1);
                        cols[1] = rs.getString(2);
                        cols[2] = rs.getString(3);
                        cols[3] = rs.getString(4);
                        cols[4] = rs.getString(5);
                    }
                }
            }
            Set<String> unlocks = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT category, unlock_key FROM su_player_unlocks WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        unlocks.add(rs.getString("category") + ":" + rs.getString("unlock_key"));
                    }
                }
            }
            return new Object[]{cols, unlocks};
        }).thenAccept(result -> {
            if (result == null) return;
            String[] cols = (String[]) result[0];
            @SuppressWarnings("unchecked")
            Set<String> unlocks = (Set<String>) result[1];
            if (cols[0] != null) customPrefixCache.put(uuid, cols[0]);
            if (cols[1] != null) customJoinMsgCache.put(uuid, cols[1]);
            if (cols[2] != null) customQuitMsgCache.put(uuid, cols[2]);
            if (cols[3] != null) activeGlowCache.put(uuid, cols[3]);
            if (cols[4] != null) activeKillEffectCache.put(uuid, cols[4]);
            unlockCache.put(uuid, unlocks);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) applyGlow(player);
            });
        });
    }

    public void unloadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        removeGlowTeam(player);
        customPrefixCache.remove(uuid);
        customJoinMsgCache.remove(uuid);
        customQuitMsgCache.remove(uuid);
        activeGlowCache.remove(uuid);
        activeKillEffectCache.remove(uuid);
        unlockCache.remove(uuid);
    }

    public boolean hasUnlock(UUID uuid, String category, String key) {
        Set<String> set = unlockCache.get(uuid);
        return set != null && set.contains(category + ":" + key);
    }

    public boolean hasCategoryUnlock(UUID uuid, String category) {
        Set<String> set = unlockCache.get(uuid);
        if (set == null) return false;
        String prefix = category + ":";
        for (String s : set) if (s.startsWith(prefix)) return true;
        return false;
    }

    private void addUnlockLocal(UUID uuid, String category, String key) {
        unlockCache.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet())
                .add(category + ":" + key);
    }

    private void saveUnlock(String username, String category, String key) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO su_player_unlocks (username, category, unlock_key) VALUES (?, ?, ?)")) {
                ps.setString(1, username);
                ps.setString(2, category);
                ps.setString(3, key);
                ps.executeUpdate();
            }
        });
    }

    private void saveColumn(String username, String column, String value) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET " + column + " = ? WHERE username = ?")) {
                ps.setString(1, value);
                ps.setString(2, username);
                ps.executeUpdate();
            }
        });
    }

    // ==================== PREFIX ====================

    public String getCustomPrefix(UUID uuid) { return customPrefixCache.get(uuid); }

    public void setCustomPrefix(Player player, String prefixOrNull) {
        UUID uuid = player.getUniqueId();
        if (prefixOrNull == null) {
            customPrefixCache.remove(uuid);
        } else {
            customPrefixCache.put(uuid, prefixOrNull);
        }
        saveColumn(player.getName().toLowerCase(), "custom_prefix", prefixOrNull);
    }

    public boolean purchasePrefixUnlock(Player player) {
        UUID uuid = player.getUniqueId();
        if (hasUnlock(uuid, CAT_PREFIX, "all")) {
            Msg.info(player, "Prefix ability already unlocked!");
            return true;
        }
        if (!economy.removeGems(uuid, PREFIX_UNLOCK_GEMS)) {
            Msg.error(player, "Not enough Gems! Need " + EconomyManager.GEMS_ICON + PREFIX_UNLOCK_GEMS);
            return false;
        }
        addUnlockLocal(uuid, CAT_PREFIX, "all");
        saveUnlock(player.getName().toLowerCase(), CAT_PREFIX, "all");
        Msg.success(player, "Custom prefix unlocked! Use /setprefix <text>");
        return true;
    }

    // ==================== NAME/CHAT COLOR ====================

    public boolean purchaseNameColor(Player player, String hex) {
        UUID uuid = player.getUniqueId();
        if (hasUnlock(uuid, CAT_NAME_COLOR, "any")) {
            saveColumn(player.getName().toLowerCase(), "name_color", hex);
            chatManager.setNameColor(uuid, hex);
            refreshNameplate(player);
            Msg.success(player, hex == null ? "Name color cleared." : "Name color set to " + hex);
            return true;
        }
        if (!economy.removeGems(uuid, NAME_COLOR_GEMS)) {
            Msg.error(player, "Not enough Gems! Need " + EconomyManager.GEMS_ICON + NAME_COLOR_GEMS);
            return false;
        }
        addUnlockLocal(uuid, CAT_NAME_COLOR, "any");
        saveUnlock(player.getName().toLowerCase(), CAT_NAME_COLOR, "any");
        saveColumn(player.getName().toLowerCase(), "name_color", hex);
        chatManager.setNameColor(uuid, hex);
        refreshNameplate(player);
        Msg.success(player, "Name color unlocked and set to " + hex);
        return true;
    }

    private void refreshNameplate(Player player) {
        var np = com.starlightuniverse.StarlightUniverse.getInstance().getNameplateManager();
        if (np != null) {
            org.bukkit.Bukkit.getScheduler().runTask(
                    com.starlightuniverse.StarlightUniverse.getInstance(),
                    () -> np.spawnFor(player));
        }
    }

    public boolean purchaseChatColor(Player player, String hex) {
        UUID uuid = player.getUniqueId();
        if (hasUnlock(uuid, CAT_CHAT_COLOR, "any")) {
            saveColumn(player.getName().toLowerCase(), "chat_color", hex);
            chatManager.setChatColor(uuid, hex);
            Msg.success(player, hex == null ? "Chat color cleared." : "Chat color set to " + hex);
            return true;
        }
        if (!economy.removeGems(uuid, CHAT_COLOR_GEMS)) {
            Msg.error(player, "Not enough Gems! Need " + EconomyManager.GEMS_ICON + CHAT_COLOR_GEMS);
            return false;
        }
        addUnlockLocal(uuid, CAT_CHAT_COLOR, "any");
        saveUnlock(player.getName().toLowerCase(), CAT_CHAT_COLOR, "any");
        saveColumn(player.getName().toLowerCase(), "chat_color", hex);
        chatManager.setChatColor(uuid, hex);
        Msg.success(player, "Chat color unlocked and set to " + hex);
        return true;
    }

    // ==================== JOIN / QUIT MSG ====================

    public String getCustomJoinMsg(UUID uuid) { return customJoinMsgCache.get(uuid); }
    public String getCustomQuitMsg(UUID uuid) { return customQuitMsgCache.get(uuid); }

    public boolean setJoinMsg(Player player, String msg) {
        UUID uuid = player.getUniqueId();
        if (!hasUnlock(uuid, CAT_JOIN_MSG, "any")) {
            if (!economy.removeStars(uuid, JOIN_QUIT_STARS)) {
                Msg.error(player, "Not enough Stars! Need " + EconomyManager.STARS_ICON + JOIN_QUIT_STARS);
                return false;
            }
            addUnlockLocal(uuid, CAT_JOIN_MSG, "any");
            saveUnlock(player.getName().toLowerCase(), CAT_JOIN_MSG, "any");
        }
        customJoinMsgCache.put(uuid, msg);
        saveColumn(player.getName().toLowerCase(), "custom_join_msg", msg);
        Msg.success(player, "Join message set!");
        return true;
    }

    public boolean setQuitMsg(Player player, String msg) {
        UUID uuid = player.getUniqueId();
        if (!hasUnlock(uuid, CAT_QUIT_MSG, "any")) {
            if (!economy.removeStars(uuid, JOIN_QUIT_STARS)) {
                Msg.error(player, "Not enough Stars! Need " + EconomyManager.STARS_ICON + JOIN_QUIT_STARS);
                return false;
            }
            addUnlockLocal(uuid, CAT_QUIT_MSG, "any");
            saveUnlock(player.getName().toLowerCase(), CAT_QUIT_MSG, "any");
        }
        customQuitMsgCache.put(uuid, msg);
        saveColumn(player.getName().toLowerCase(), "custom_quit_msg", msg);
        Msg.success(player, "Quit message set!");
        return true;
    }

    // ==================== BODY GLOW ====================

    public String getActiveGlow(UUID uuid) { return activeGlowCache.get(uuid); }

    public boolean purchaseGlow(Player player, BodyGlow glow) {
        UUID uuid = player.getUniqueId();
        if (hasUnlock(uuid, CAT_GLOW, glow.getKey())) {
            Msg.info(player, "Already owned!");
            return true;
        }
        if (!economy.removeGems(uuid, BodyGlow.UNLOCK_GEM_COST)) {
            Msg.error(player, "Not enough Gems! Need " + EconomyManager.GEMS_ICON + BodyGlow.UNLOCK_GEM_COST);
            return false;
        }
        addUnlockLocal(uuid, CAT_GLOW, glow.getKey());
        saveUnlock(player.getName().toLowerCase(), CAT_GLOW, glow.getKey());
        Msg.success(player, "Unlocked " + glow.getDisplayName() + " glow!");
        return true;
    }

    public void activateGlow(Player player, BodyGlow glow) {
        UUID uuid = player.getUniqueId();
        if (glow != null && !hasUnlock(uuid, CAT_GLOW, glow.getKey())) {
            Msg.error(player, "You have not unlocked this glow!");
            return;
        }
        if (glow == null) {
            activeGlowCache.remove(uuid);
            saveColumn(player.getName().toLowerCase(), "active_glow", null);
        } else {
            activeGlowCache.put(uuid, glow.getKey());
            saveColumn(player.getName().toLowerCase(), "active_glow", glow.getKey());
        }
        applyGlow(player);
    }

    public void applyGlow(Player player) {
        UUID uuid = player.getUniqueId();
        String key = activeGlowCache.get(uuid);
        BodyGlow glow = BodyGlow.byKey(key);
        if (glow == null) {
            player.setGlowing(false);
            removeGlowTeam(player);
            return;
        }
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "sug_" + glow.getKey();
        Team team = sb.getTeam(teamName);
        if (team == null) {
            team = sb.registerNewTeam(teamName);
            team.color(glow.getNamedColor());
        }
        for (Team t : sb.getTeams()) {
            if (t.getName().startsWith("sug_") && !t.getName().equals(teamName)) {
                if (t.hasEntry(player.getName())) t.removeEntry(player.getName());
            }
        }
        if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
        player.setGlowing(true);
    }

    private void removeGlowTeam(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team t : sb.getTeams()) {
            if (t.getName().startsWith("sug_") && t.hasEntry(player.getName())) {
                t.removeEntry(player.getName());
            }
        }
        player.setGlowing(false);
    }

    // ==================== KILL EFFECT ====================

    public String getActiveKillEffect(UUID uuid) { return activeKillEffectCache.get(uuid); }

    public boolean purchaseKillEffect(Player player, KillEffect fx) {
        UUID uuid = player.getUniqueId();
        if (hasUnlock(uuid, CAT_KILL, fx.getKey())) {
            Msg.info(player, "Already owned!");
            return true;
        }
        if (!economy.removeGems(uuid, fx.getGemCost())) {
            Msg.error(player, "Not enough Gems! Need " + EconomyManager.GEMS_ICON + fx.getGemCost());
            return false;
        }
        addUnlockLocal(uuid, CAT_KILL, fx.getKey());
        saveUnlock(player.getName().toLowerCase(), CAT_KILL, fx.getKey());
        Msg.success(player, "Unlocked " + fx.getDisplayName() + " kill effect!");
        return true;
    }

    public void activateKillEffect(Player player, KillEffect fx) {
        UUID uuid = player.getUniqueId();
        if (fx != null && !hasUnlock(uuid, CAT_KILL, fx.getKey())) {
            Msg.error(player, "You have not unlocked this kill effect!");
            return;
        }
        if (fx == null) {
            activeKillEffectCache.remove(uuid);
            saveColumn(player.getName().toLowerCase(), "active_kill_effect", null);
        } else {
            activeKillEffectCache.put(uuid, fx.getKey());
            saveColumn(player.getName().toLowerCase(), "active_kill_effect", fx.getKey());
        }
    }

    // ==================== TRAILS ====================

    public boolean purchaseTrail(Player player, String trailKey, int cost) {
        UUID uuid = player.getUniqueId();
        if (hasUnlock(uuid, CAT_TRAIL, trailKey)) {
            Msg.info(player, "Already owned!");
            return true;
        }
        if (!economy.removeGems(uuid, cost)) {
            Msg.error(player, "Not enough Gems! Need " + EconomyManager.GEMS_ICON + cost);
            return false;
        }
        addUnlockLocal(uuid, CAT_TRAIL, trailKey);
        saveUnlock(player.getName().toLowerCase(), CAT_TRAIL, trailKey);
        Msg.success(player, "Unlocked " + trailKey + " trail!");
        return true;
    }

    public boolean canUseTrail(UUID uuid, String trailKey) {
        return hasUnlock(uuid, CAT_TRAIL, trailKey);
    }

    // ==================== JOIN/QUIT BROADCAST ====================

    public Component buildJoinMessage(Player player) {
        String custom = customJoinMsgCache.get(player.getUniqueId());
        Component name = Component.text(player.getName(), GOLD);
        if (custom != null) {
            String msg = custom.replace("{name}", player.getName());
            return Component.text(msg, GREEN);
        }
        return Component.text("+ ", GREEN).append(name).append(Component.text(" joined!", GREEN));
    }

    public Component buildQuitMessage(Player player) {
        String custom = customQuitMsgCache.get(player.getUniqueId());
        Component name = Component.text(player.getName(), GOLD);
        if (custom != null) {
            String msg = custom.replace("{name}", player.getName());
            return Component.text(msg, TextColor.color(0xFF5555));
        }
        return Component.text("- ", TextColor.color(0xFF5555)).append(name)
                .append(Component.text(" left.", TextColor.color(0xFF5555)));
    }

    // ==================== GETTERS ====================

    public JavaPlugin getPlugin() { return plugin; }
    public EconomyManager getEconomy() { return economy; }
    public PremiumManager getPremiumManager() { return premiumManager; }
    public ChatManager getChatManager() { return chatManager; }
}
