package com.starlightuniverse.admin;

import com.starlightuniverse.database.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AdminManager {

    private final JavaPlugin plugin;
    private final DatabaseManager db;

    private final Map<UUID, Integer> adminLevelCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> premiumLevelCache = new ConcurrentHashMap<>();
    private final Set<UUID> mutedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> staffChatPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spyPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastChatTime = new ConcurrentHashMap<>();
    private volatile int slowModeSeconds = 0;
    private final Map<String, List<Long>> banJoinAttempts = new ConcurrentHashMap<>();

    public AdminManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void loadPlayer(UUID uuid, String username) {
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT admin_level, premium_level FROM su_players WHERE username = ?")) {
                ps.setString(1, username.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return new int[]{rs.getInt("admin_level"), rs.getInt("premium_level")};
                }
            }
            return new int[]{0, 0};
        }).thenAccept(levels -> {
            if (levels != null) {
                adminLevelCache.put(uuid, levels[0]);
                premiumLevelCache.put(uuid, levels[1]);
            }
        });
    }

    public void unloadPlayer(UUID uuid) {
        adminLevelCache.remove(uuid);
        premiumLevelCache.remove(uuid);
        mutedPlayers.remove(uuid);
        frozenPlayers.remove(uuid);
        vanishedPlayers.remove(uuid);
        staffChatPlayers.remove(uuid);
        spyPlayers.remove(uuid);
        lastChatTime.remove(uuid);
    }

    public int getAdminLevel(UUID uuid) { return adminLevelCache.getOrDefault(uuid, 0); }
    public AdminRank getAdminRank(UUID uuid) { return AdminRank.fromLevel(getAdminLevel(uuid)); }
    public boolean hasPermission(UUID uuid, int requiredLevel) { return getAdminLevel(uuid) >= requiredLevel; }

    public CompletableFuture<Void> setAdminLevel(String username, int level) {
        return db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET admin_level = ? WHERE username = ?")) {
                ps.setInt(1, level);
                ps.setString(2, username.toLowerCase());
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Integer> getAdminLevelFromDb(String username) {
        return db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT admin_level FROM su_players WHERE username = ?")) {
                ps.setString(1, username.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("admin_level");
                }
            }
            return -1;
        });
    }

    public int getPremiumLevel(UUID uuid) { return premiumLevelCache.getOrDefault(uuid, 0); }

    public CompletableFuture<Void> setPremiumLevel(String username, int level) {
        return db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET premium_level = ? WHERE username = ?")) {
                ps.setInt(1, level);
                ps.setString(2, username.toLowerCase());
                ps.executeUpdate();
            }
        });
    }

    // ---- Ban System ----

    public BanInfo getActiveBanSync(String username) {
        try (Connection conn = db.getConnection()) {
            int playerId = getPlayerId(conn, username);
            if (playerId == -1) return null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, banned_by, reason, duration_minutes, ban_date, expire_date, login_attempts " +
                    "FROM su_bans WHERE player_id = ? AND active = 1 " +
                    "AND (expire_date IS NULL OR expire_date > NOW()) ORDER BY id DESC LIMIT 1")) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new BanInfo(rs.getInt("id"), rs.getString("banned_by"),
                                rs.getString("reason"), rs.getInt("duration_minutes"),
                                rs.getString("ban_date"), rs.getString("expire_date"),
                                rs.getInt("login_attempts"));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[SU] Ban check error: " + e.getMessage());
        }
        return null;
    }

    public CompletableFuture<Void> banPlayer(String target, String bannedBy, String reason, int durationMinutes) {
        return db.executeAsync(conn -> {
            int playerId = getOrCreatePlayerId(conn, target);
            if (playerId == -1) return;
            deactivateRecords(conn, "su_bans", playerId);
            String r = reason.length() > 50 ? reason.substring(0, 50) : reason;
            if (durationMinutes > 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO su_bans (player_id, banned_by, reason, duration_minutes, ban_date, expire_date) " +
                        "VALUES (?, ?, ?, ?, NOW(), DATE_ADD(NOW(), INTERVAL ? MINUTE))")) {
                    ps.setInt(1, playerId); ps.setString(2, bannedBy); ps.setString(3, r);
                    ps.setInt(4, durationMinutes); ps.setInt(5, durationMinutes);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO su_bans (player_id, banned_by, reason, duration_minutes, ban_date) " +
                        "VALUES (?, ?, ?, 0, NOW())")) {
                    ps.setInt(1, playerId); ps.setString(2, bannedBy); ps.setString(3, r);
                    ps.executeUpdate();
                }
            }
        });
    }

    public CompletableFuture<Void> unbanPlayer(String username) {
        return db.executeAsync(conn -> {
            int playerId = getPlayerId(conn, username);
            if (playerId == -1) return;
            deactivateRecords(conn, "su_bans", playerId);
        });
    }

    public void incrementLoginAttempts(String username) {
        db.executeAsync(conn -> {
            int playerId = getPlayerId(conn, username);
            if (playerId == -1) return;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_bans SET login_attempts = login_attempts + 1 WHERE player_id = ? AND active = 1")) {
                ps.setInt(1, playerId);
                ps.executeUpdate();
            }
        });
    }

    public boolean trackBanJoinAttempt(String username) {
        String key = username.toLowerCase();
        long now = System.currentTimeMillis();
        List<Long> attempts = banJoinAttempts.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
        attempts.add(now);
        attempts.removeIf(t -> now - t > 30_000);
        return attempts.size() > 10;
    }

    // ---- Mute System ----

    public void loadMuteStatus(UUID uuid, String username) {
        db.queryAsync(conn -> {
            int playerId = getPlayerId(conn, username);
            if (playerId == -1) return false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM su_mutes WHERE player_id = ? AND active = 1 " +
                    "AND (expire_date IS NULL OR expire_date > NOW()) LIMIT 1")) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
            }
        }).thenAccept(muted -> { if (muted != null && muted) mutedPlayers.add(uuid); });
    }

    public boolean isMuted(UUID uuid) { return mutedPlayers.contains(uuid); }

    public CompletableFuture<MuteInfo> getActiveMute(String username) {
        return db.queryAsync(conn -> {
            int playerId = getPlayerId(conn, username);
            if (playerId == -1) return null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, muted_by, reason, duration_minutes, mute_date, expire_date " +
                    "FROM su_mutes WHERE player_id = ? AND active = 1 " +
                    "AND (expire_date IS NULL OR expire_date > NOW()) ORDER BY id DESC LIMIT 1")) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return new MuteInfo(rs.getInt("id"), rs.getString("muted_by"),
                            rs.getString("reason"), rs.getInt("duration_minutes"),
                            rs.getString("mute_date"), rs.getString("expire_date"));
                }
            }
            return null;
        });
    }

    public CompletableFuture<Void> mutePlayer(String target, String mutedBy, String reason, int durationMinutes) {
        return db.executeAsync(conn -> {
            int playerId = getOrCreatePlayerId(conn, target);
            if (playerId == -1) return;
            deactivateRecords(conn, "su_mutes", playerId);
            String r = reason.length() > 50 ? reason.substring(0, 50) : reason;
            if (durationMinutes > 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO su_mutes (player_id, muted_by, reason, duration_minutes, mute_date, expire_date) " +
                        "VALUES (?, ?, ?, ?, NOW(), DATE_ADD(NOW(), INTERVAL ? MINUTE))")) {
                    ps.setInt(1, playerId); ps.setString(2, mutedBy); ps.setString(3, r);
                    ps.setInt(4, durationMinutes); ps.setInt(5, durationMinutes);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO su_mutes (player_id, muted_by, reason, duration_minutes, mute_date) " +
                        "VALUES (?, ?, ?, 0, NOW())")) {
                    ps.setInt(1, playerId); ps.setString(2, mutedBy); ps.setString(3, r);
                    ps.executeUpdate();
                }
            }
        });
    }

    public CompletableFuture<Void> unmutePlayer(String username) {
        return db.executeAsync(conn -> {
            int playerId = getPlayerId(conn, username);
            if (playerId == -1) return;
            deactivateRecords(conn, "su_mutes", playerId);
        });
    }

    public void addMuted(UUID uuid) { mutedPlayers.add(uuid); }
    public void removeMuted(UUID uuid) { mutedPlayers.remove(uuid); }

    // ---- Warn System ----

    public CompletableFuture<Integer> warnPlayer(String target, String warnedBy, String reason) {
        return db.queryAsync(conn -> {
            int playerId = getOrCreatePlayerId(conn, target);
            if (playerId == -1) return -1;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_warns (player_id, warned_by, reason) VALUES (?, ?, ?)")) {
                ps.setInt(1, playerId); ps.setString(2, warnedBy); ps.setString(3, reason);
                ps.executeUpdate();
            }
            try (PreparedStatement count = conn.prepareStatement(
                    "SELECT COUNT(*) FROM su_warns WHERE player_id = ? AND active = 1")) {
                count.setInt(1, playerId);
                try (ResultSet rs = count.executeQuery()) { if (rs.next()) return rs.getInt(1); }
            }
            return 0;
        });
    }

    public CompletableFuture<List<WarnInfo>> getWarns(String username) {
        return db.queryAsync(conn -> {
            int playerId = getPlayerId(conn, username);
            if (playerId == -1) return List.<WarnInfo>of();
            List<WarnInfo> warns = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, warned_by, reason, warn_date FROM su_warns WHERE player_id = ? AND active = 1 ORDER BY id DESC")) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) warns.add(new WarnInfo(rs.getInt("id"), rs.getString("warned_by"),
                            rs.getString("reason"), rs.getString("warn_date")));
                }
            }
            return warns;
        });
    }

    public CompletableFuture<Void> removeWarn(int warnId) {
        return db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE su_warns SET active = 0 WHERE id = ?")) {
                ps.setInt(1, warnId); ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> removeAllWarns(String username) {
        return db.executeAsync(conn -> {
            int playerId = getPlayerId(conn, username);
            if (playerId == -1) return;
            deactivateRecords(conn, "su_warns", playerId);
        });
    }

    // ---- Notes ----

    public CompletableFuture<Void> addNote(String target, String author, String note) {
        return db.executeAsync(conn -> {
            int playerId = getOrCreatePlayerId(conn, target);
            if (playerId == -1) return;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_admin_notes (player_id, note_by, note_text) VALUES (?, ?, ?)")) {
                ps.setInt(1, playerId); ps.setString(2, author); ps.setString(3, note);
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<NoteInfo>> getNotes(String username) {
        return db.queryAsync(conn -> {
            int playerId = getPlayerId(conn, username);
            if (playerId == -1) return List.<NoteInfo>of();
            List<NoteInfo> notes = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, note_by, note_text, note_date FROM su_admin_notes WHERE player_id = ? ORDER BY id DESC LIMIT 20")) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) notes.add(new NoteInfo(rs.getInt("id"), rs.getString("note_by"),
                            rs.getString("note_text"), rs.getString("note_date")));
                }
            }
            return notes;
        });
    }

    // ---- Reports ----

    public CompletableFuture<Void> addReport(String reporter, String reported, String reason) {
        return db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_reports (reporter_username, reported_username, reason) VALUES (?, ?, ?)")) {
                ps.setString(1, reporter.toLowerCase()); ps.setString(2, reported.toLowerCase());
                ps.setString(3, reason);
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<ReportInfo>> getActiveReports() {
        return db.queryAsync(conn -> {
            List<ReportInfo> reports = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, reporter_username, reported_username, reason, report_date, responded_by " +
                    "FROM su_reports WHERE active = 1 ORDER BY id DESC LIMIT 50")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) reports.add(new ReportInfo(rs.getInt("id"),
                            rs.getString("reporter_username"), rs.getString("reported_username"),
                            rs.getString("reason"), rs.getString("report_date"), rs.getString("responded_by")));
                }
            }
            return reports;
        });
    }

    public CompletableFuture<Void> respondToReport(int reportId, String responder) {
        return db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_reports SET active = 0, responded_by = ? WHERE id = ?")) {
                ps.setString(1, responder); ps.setInt(2, reportId);
                ps.executeUpdate();
            }
        });
    }

    // ---- Check / Player Info ----

    public CompletableFuture<PlayerInfo> getPlayerInfo(String username) {
        return db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT p.id, p.username, p.admin_level, p.premium_level, p.money, p.gems, p.stars, " +
                    "p.playtime, p.level, p.pvp_kills, p.pvm_kills, p.deaths, p.last_active, " +
                    "p.name_color, p.last_login_ip " +
                    "FROM su_players p WHERE p.username = ?")) {
                ps.setString(1, username.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int pid = rs.getInt("id");
                        int bans = countActive(conn, "su_bans", pid);
                        int mutes = countActive(conn, "su_mutes", pid);
                        int warns = countActive(conn, "su_warns", pid);
                        return new PlayerInfo(rs.getString("username"), rs.getInt("admin_level"),
                                rs.getInt("premium_level"), rs.getDouble("money"), rs.getDouble("gems"),
                                rs.getDouble("stars"), rs.getLong("playtime"), rs.getInt("level"),
                                rs.getInt("pvp_kills"), rs.getInt("pvm_kills"), rs.getInt("deaths"),
                                rs.getString("last_active"), bans, mutes, warns,
                                rs.getString("name_color"), rs.getString("last_login_ip"));
                    }
                }
            }
            return null;
        });
    }

    // ---- History ----

    public CompletableFuture<List<HistoryEntry>> getHistory(String username) {
        return db.queryAsync(conn -> {
            int playerId = getPlayerId(conn, username);
            if (playerId == -1) return List.<HistoryEntry>of();
            List<HistoryEntry> history = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT banned_by, reason, ban_date, active FROM su_bans WHERE player_id = ? ORDER BY id DESC LIMIT 10")) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) history.add(new HistoryEntry("BAN", rs.getString("banned_by"),
                            rs.getString("reason"), rs.getString("ban_date"), rs.getBoolean("active")));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT muted_by, reason, mute_date, active FROM su_mutes WHERE player_id = ? ORDER BY id DESC LIMIT 10")) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) history.add(new HistoryEntry("MUTE", rs.getString("muted_by"),
                            rs.getString("reason"), rs.getString("mute_date"), rs.getBoolean("active")));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT warned_by, reason, warn_date, active FROM su_warns WHERE player_id = ? ORDER BY id DESC LIMIT 10")) {
                ps.setInt(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) history.add(new HistoryEntry("WARN", rs.getString("warned_by"),
                            rs.getString("reason"), rs.getString("warn_date"), rs.getBoolean("active")));
                }
            }
            return history;
        });
    }

    // ---- In-Memory Toggles ----

    public boolean isFrozen(UUID uuid) { return frozenPlayers.contains(uuid); }
    public boolean toggleFreeze(UUID uuid) {
        if (frozenPlayers.remove(uuid)) return false;
        frozenPlayers.add(uuid); return true;
    }

    public boolean isVanished(UUID uuid) { return vanishedPlayers.contains(uuid); }
    public boolean toggleVanish(UUID uuid) {
        if (vanishedPlayers.remove(uuid)) return false;
        vanishedPlayers.add(uuid); return true;
    }

    public boolean isInStaffChat(UUID uuid) { return staffChatPlayers.contains(uuid); }
    public boolean toggleStaffChat(UUID uuid) {
        if (staffChatPlayers.remove(uuid)) return false;
        staffChatPlayers.add(uuid); return true;
    }

    public boolean isSpy(UUID uuid) { return spyPlayers.contains(uuid); }
    public boolean toggleSpy(UUID uuid) {
        if (spyPlayers.remove(uuid)) return false;
        spyPlayers.add(uuid); return true;
    }

    public int getSlowModeSeconds() { return slowModeSeconds; }
    public void setSlowMode(int seconds) { slowModeSeconds = seconds; }

    public boolean canChat(UUID uuid) {
        if (slowModeSeconds <= 0) return true;
        Long last = lastChatTime.get(uuid);
        return last == null || (System.currentTimeMillis() - last) >= slowModeSeconds * 1000L;
    }

    public void recordChat(UUID uuid) { lastChatTime.put(uuid, System.currentTimeMillis()); }

    public Set<UUID> getVanishedPlayers() { return Collections.unmodifiableSet(vanishedPlayers); }

    // ---- Password Admin ----

    public CompletableFuture<Boolean> setPassword(String username, String newPassword) {
        String hash = BCrypt.hashpw(newPassword, BCrypt.gensalt(10));
        return db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET password_hash = ? WHERE username = ?")) {
                ps.setString(1, hash); ps.setString(2, username.toLowerCase());
                return ps.executeUpdate() > 0;
            }
        });
    }

    // ---- Name Change ----

    public CompletableFuture<Boolean> changeName(String oldName, String newName) {
        return db.queryAsync(conn -> {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement check = conn.prepareStatement(
                        "SELECT 1 FROM su_players WHERE username = ?")) {
                    check.setString(1, newName.toLowerCase());
                    try (ResultSet rs = check.executeQuery()) { if (rs.next()) { conn.rollback(); return false; } }
                }
                String o = oldName.toLowerCase(), n = newName.toLowerCase();
                updateCol(conn, "su_players", "username", o, n);
                updateCol(conn, "su_inventories", "username", o, n);
                updateCol(conn, "su_auction_listings", "seller_username", o, n);
                updateCol(conn, "su_orders", "creator_username", o, n);
                updateCol(conn, "su_order_storage", "username", o, n);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        });
    }

    // ---- Helpers ----

    private int getPlayerId(Connection conn, String username) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM su_players WHERE username = ?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt("id"); }
        }
        return -1;
    }

    private int getOrCreatePlayerId(Connection conn, String username) throws SQLException {
        int id = getPlayerId(conn, username);
        if (id != -1) return id;
        try (PreparedStatement ins = conn.prepareStatement("INSERT IGNORE INTO su_players (username) VALUES (?)")) {
            ins.setString(1, username.toLowerCase());
            ins.executeUpdate();
        }
        return getPlayerId(conn, username);
    }

    private void deactivateRecords(Connection conn, String table, int playerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE " + table + " SET active = 0 WHERE player_id = ? AND active = 1")) {
            ps.setInt(1, playerId); ps.executeUpdate();
        }
    }

    private int countActive(Connection conn, String table, int playerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM " + table + " WHERE player_id = ? AND active = 1")) {
            ps.setInt(1, playerId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        }
        return 0;
    }

    private void updateCol(Connection conn, String table, String column, String oldVal, String newVal) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?")) {
            ps.setString(1, newVal); ps.setString(2, oldVal); ps.executeUpdate();
        }
    }

    // ---- Records ----

    public record BanInfo(int id, String bannedBy, String reason, int durationMin, String banDate, String expireDate, int loginAttempts) {}
    public record MuteInfo(int id, String mutedBy, String reason, int durationMin, String muteDate, String expireDate) {}
    public record WarnInfo(int id, String warnedBy, String reason, String warnDate) {}
    public record NoteInfo(int id, String noteBy, String note, String noteDate) {}
    public record ReportInfo(int id, String reporter, String reported, String reason, String reportDate, String respondedBy) {}
    public record PlayerInfo(String username, int adminLevel, int premiumLevel, double money, double gems, double stars,
                             long playtime, int level, int pvpKills, int pvmKills, int deaths, String lastActive,
                             int activeBans, int activeMutes, int activeWarns, String nameColor, String lastIp) {}
    public record HistoryEntry(String type, String by, String reason, String date, boolean active) {}
}
