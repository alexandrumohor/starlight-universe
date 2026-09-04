package com.starlightuniverse.auth;

import com.starlightuniverse.database.DatabaseManager;
import org.mindrot.jbcrypt.BCrypt;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {

    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long ATTEMPT_WINDOW_MS = 60_000;
    private static final long SESSION_DURATION_MS = 5 * 60_000;
    public static final int MAX_SIMULTANEOUS_ACCOUNTS_PER_IP = 3;
    private static final int MIN_PASSWORD_LENGTH = 3;

    private final DatabaseManager db;
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final Map<String, List<Long>> failedAttempts = new ConcurrentHashMap<>();

    public AuthManager(DatabaseManager db) {
        this.db = db;
    }

    public boolean isAuthenticated(UUID uuid) {
        return authenticated.contains(uuid);
    }

    public void setAuthenticated(UUID uuid) {
        authenticated.add(uuid);
    }

    public void removeAuthenticated(UUID uuid) {
        authenticated.remove(uuid);
    }

    public boolean isRegistered(String username) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM su_players WHERE username = ?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean hasPassword(String username) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT password_hash FROM su_players WHERE username = ?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString("password_hash");
                    return hash != null && !hash.isEmpty();
                }
            }
        } catch (SQLException e) {
            // fall through
        }
        return false;
    }

    public enum RegisterResult {
        SUCCESS, PASSWORD_TOO_SHORT, PASSWORD_MISMATCH, ALREADY_REGISTERED, TOO_MANY_ACCOUNTS, ERROR
    }

    public RegisterResult register(String username, String password, String confirm, String ip) {
        if (password.length() < MIN_PASSWORD_LENGTH) return RegisterResult.PASSWORD_TOO_SHORT;
        if (!password.equals(confirm)) return RegisterResult.PASSWORD_MISMATCH;
        if (isRegistered(username)) return RegisterResult.ALREADY_REGISTERED;

        String hash = BCrypt.hashpw(password, BCrypt.gensalt(10));

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO su_players (username, password_hash, register_date, last_login_ip, last_login_time) VALUES (?, ?, NOW(), ?, ?)")) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, hash);
            ps.setString(3, ip);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            return RegisterResult.SUCCESS;
        } catch (SQLException e) {
            return RegisterResult.ERROR;
        }
    }

    public void registerPremium(String username, String premiumUuid, String ip) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO su_players (username, premium_uuid, register_date, last_login_ip, last_login_time) VALUES (?, ?, NOW(), ?, ?)")) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, premiumUuid);
            ps.setString(3, ip);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public enum LoginResult {
        SUCCESS, WRONG_PASSWORD, NOT_REGISTERED, TOO_MANY_ATTEMPTS, ERROR
    }

    public LoginResult login(String username, String password) {
        if (!canAttemptLogin(username)) return LoginResult.TOO_MANY_ATTEMPTS;
        if (!isRegistered(username)) return LoginResult.NOT_REGISTERED;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT password_hash FROM su_players WHERE username = ?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString("password_hash");
                    if (hash == null || hash.isEmpty()) return LoginResult.ERROR;
                    if (BCrypt.checkpw(password, hash)) {
                        clearAttempts(username);
                        return LoginResult.SUCCESS;
                    } else {
                        recordFailedAttempt(username);
                        return LoginResult.WRONG_PASSWORD;
                    }
                }
            }
        } catch (SQLException e) {
            return LoginResult.ERROR;
        }
        return LoginResult.ERROR;
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        if (newPassword.length() < MIN_PASSWORD_LENGTH) return false;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT password_hash FROM su_players WHERE username = ?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString("password_hash");
                    if (hash == null || !BCrypt.checkpw(oldPassword, hash)) return false;

                    String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt(10));
                    try (PreparedStatement update = conn.prepareStatement(
                            "UPDATE su_players SET password_hash = ? WHERE username = ?")) {
                        update.setString(1, newHash);
                        update.setString(2, username.toLowerCase());
                        update.executeUpdate();
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            return false;
        }
        return false;
    }

    public boolean checkSession(String username, String ip) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT last_login_ip, last_login_time FROM su_players WHERE username = ?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String savedIp = rs.getString("last_login_ip");
                    long savedTime = rs.getLong("last_login_time");
                    if (ip.equals(savedIp) && (System.currentTimeMillis() - savedTime) < SESSION_DURATION_MS) {
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            return false;
        }
        return false;
    }

    public void saveSession(String username, String ip) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE su_players SET last_login_ip = ?, last_login_time = ?, last_active = NOW() WHERE username = ?")) {
            ps.setString(1, ip);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, username.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public void setPremiumUuid(String username, String premiumUuid) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE su_players SET premium_uuid = ? WHERE username = ?")) {
            ps.setString(1, premiumUuid);
            ps.setString(2, username.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public String getPremiumUuid(String username) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT premium_uuid FROM su_players WHERE username = ?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("premium_uuid");
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    /**
     * Count how many players currently online come from the given IP. Used to
     * cap simultaneous connections from the same address to
     * {@link #MAX_SIMULTANEOUS_ACCOUNTS_PER_IP}. Does not count the incoming
     * connection itself (that player isn't in Bukkit.getOnlinePlayers() yet
     * during AsyncPlayerPreLoginEvent).
     */
    public int countOnlineWithIp(String ip) {
        if (ip == null) return 0;
        int count = 0;
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (p.getAddress() == null) continue;
            String other = p.getAddress().getAddress().getHostAddress();
            if (ip.equals(other)) count++;
        }
        return count;
    }

    public MojangProfile checkMojangPremium(String username) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                String id = extractJsonString(body, "id");
                String name = extractJsonString(body, "name");
                if (id != null && name != null) {
                    return new MojangProfile(id, name);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean canAttemptLogin(String username) {
        String key = username.toLowerCase();
        List<Long> attempts = failedAttempts.get(key);
        if (attempts == null) return true;
        long now = System.currentTimeMillis();
        attempts.removeIf(t -> now - t > ATTEMPT_WINDOW_MS);
        return attempts.size() < MAX_LOGIN_ATTEMPTS;
    }

    private void recordFailedAttempt(String username) {
        String key = username.toLowerCase();
        failedAttempts.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(System.currentTimeMillis());
    }

    private void clearAttempts(String username) {
        failedAttempts.remove(username.toLowerCase());
    }

    public static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx == -1) return null;
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote == -1) return null;
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote == -1) return null;
        return json.substring(startQuote + 1, endQuote);
    }

    public record MojangProfile(String id, String name) {}
}
