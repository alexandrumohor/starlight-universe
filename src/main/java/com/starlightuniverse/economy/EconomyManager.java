package com.starlightuniverse.economy;

import com.starlightuniverse.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class EconomyManager {

    public static final String MONEY_ICON = String.valueOf((char) 0xE001);
    public static final String GEMS_ICON = String.valueOf((char) 0xE002);
    public static final String STARS_ICON = String.valueOf((char) 0xE003);

    public static final double PAY_TAX_RATE = 0.02;

    private static final DecimalFormat FORMATTER;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        FORMATTER = new DecimalFormat("#,##0", symbols);
    }

    private final DatabaseManager db;
    private final Map<UUID, double[]> cache = new ConcurrentHashMap<>();

    public EconomyManager(DatabaseManager db) {
        this.db = db;
    }

    public void loadPlayer(UUID uuid, String username) {
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT money, gems, stars FROM su_players WHERE username = ?")) {
                ps.setString(1, username.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new double[]{
                                rs.getDouble("money"),
                                rs.getDouble("gems"),
                                rs.getDouble("stars")
                        };
                    }
                }
            }
            return new double[]{0, 0, 0};
        }).thenAccept(bal -> {
            if (bal != null) cache.put(uuid, bal);
        });
    }

    public void unloadPlayer(UUID uuid) {
        cache.remove(uuid);
    }

    public double getMoney(UUID uuid) {
        double[] b = cache.get(uuid);
        return b != null ? b[0] : 0;
    }

    public double getGems(UUID uuid) {
        double[] b = cache.get(uuid);
        return b != null ? b[1] : 0;
    }

    public double getStars(UUID uuid) {
        double[] b = cache.get(uuid);
        return b != null ? b[2] : 0;
    }

    public boolean hasMoney(UUID uuid, double amount) {
        return getMoney(uuid) >= amount;
    }

    public boolean hasGems(UUID uuid, double amount) {
        return getGems(uuid) >= amount;
    }

    public boolean hasStars(UUID uuid, double amount) {
        return getStars(uuid) >= amount;
    }

    public void addMoney(UUID uuid, double amount) {
        double[] b = cache.computeIfAbsent(uuid, k -> new double[3]);
        b[0] += amount;
        saveColumn(uuid, "money", b[0]);
    }

    public void addGems(UUID uuid, double amount) {
        double[] b = cache.computeIfAbsent(uuid, k -> new double[3]);
        b[1] += amount;
        saveColumn(uuid, "gems", b[1]);
    }

    public void addStars(UUID uuid, double amount) {
        double[] b = cache.computeIfAbsent(uuid, k -> new double[3]);
        b[2] += amount;
        saveColumn(uuid, "stars", b[2]);
    }

    public boolean removeMoney(UUID uuid, double amount) {
        if (!hasMoney(uuid, amount)) return false;
        double[] b = cache.get(uuid);
        b[0] -= amount;
        saveColumn(uuid, "money", b[0]);
        return true;
    }

    public boolean removeGems(UUID uuid, double amount) {
        if (!hasGems(uuid, amount)) return false;
        double[] b = cache.get(uuid);
        b[1] -= amount;
        saveColumn(uuid, "gems", b[1]);
        return true;
    }

    public boolean removeStars(UUID uuid, double amount) {
        if (!hasStars(uuid, amount)) return false;
        double[] b = cache.get(uuid);
        b[2] -= amount;
        saveColumn(uuid, "stars", b[2]);
        return true;
    }

    public CompletableFuture<Boolean> giveOffline(String username, String column, double amount) {
        return db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET " + column + " = " + column + " + ? WHERE username = ?")) {
                ps.setDouble(1, amount);
                ps.setString(2, username.toLowerCase());
                return ps.executeUpdate() > 0;
            }
        });
    }

    private void saveColumn(UUID uuid, String column, double value) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        String username = player.getName().toLowerCase();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET " + column + " = ? WHERE username = ?")) {
                ps.setDouble(1, value);
                ps.setString(2, username);
                ps.executeUpdate();
            }
        });
    }

    public static String format(double amount) {
        return FORMATTER.format((long) amount);
    }
}
