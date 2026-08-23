package com.starlightuniverse.world;

import com.starlightuniverse.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

public class InventoryManager {

    private static final String SAVE_SQL = """
            INSERT INTO su_inventories (username, inventory_group, inventory_data, armor_data,
                offhand_data, exp_level, exp_progress, health, food_level, saturation)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                inventory_data = VALUES(inventory_data), armor_data = VALUES(armor_data),
                offhand_data = VALUES(offhand_data), exp_level = VALUES(exp_level),
                exp_progress = VALUES(exp_progress), health = VALUES(health),
                food_level = VALUES(food_level), saturation = VALUES(saturation)
            """;

    private static final String LOAD_SQL =
            "SELECT * FROM su_inventories WHERE username = ? AND inventory_group = ?";

    private final JavaPlugin plugin;
    private final DatabaseManager db;

    public InventoryManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void saveInventory(Player player, WorldManager.WorldGroup group) {
        PlayerState state = captureState(player, group);
        db.executeAsync(conn -> writeState(conn, state));
    }

    public void saveInventorySync(Player player, WorldManager.WorldGroup group) {
        PlayerState state = captureState(player, group);
        try (Connection conn = db.getConnection()) {
            writeState(conn, state);
        } catch (SQLException e) {
            plugin.getLogger().severe("[SU] Failed to save inventory for " + state.username + ": " + e.getMessage());
        }
    }

    public void loadInventory(Player player, WorldManager.WorldGroup group) {
        String username = player.getName().toLowerCase();
        String groupName = group.name();

        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(LOAD_SQL)) {
                ps.setString(1, username);
                ps.setString(2, groupName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new LoadedState(
                                deserializeItems(rs.getString("inventory_data")),
                                deserializeItems(rs.getString("armor_data")),
                                deserializeItem(rs.getString("offhand_data")),
                                rs.getInt("exp_level"),
                                rs.getFloat("exp_progress"),
                                rs.getDouble("health"),
                                rs.getInt("food_level"),
                                rs.getFloat("saturation")
                        );
                    }
                }
            }
            return null;
        }).thenAccept(data -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (data != null) {
                player.getInventory().setContents(data.inventory);
                player.getInventory().setArmorContents(data.armor);
                player.getInventory().setItemInOffHand(
                        data.offhand != null ? data.offhand : new ItemStack(Material.AIR));
                player.setLevel(data.expLevel);
                player.setExp(data.expProgress);
                player.setHealth(Math.min(data.health, player.getMaxHealth()));
                player.setFoodLevel(data.foodLevel);
                player.setSaturation(data.saturation);
            }
        }));
    }

    private PlayerState captureState(Player player, WorldManager.WorldGroup group) {
        return new PlayerState(
                player.getName().toLowerCase(),
                group.name(),
                serializeItems(player.getInventory().getContents()),
                serializeItems(player.getInventory().getArmorContents()),
                serializeItem(player.getInventory().getItemInOffHand()),
                player.getLevel(),
                player.getExp(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation()
        );
    }

    private void writeState(Connection conn, PlayerState state) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SAVE_SQL)) {
            ps.setString(1, state.username);
            ps.setString(2, state.group);
            ps.setString(3, state.invData);
            ps.setString(4, state.armorData);
            ps.setString(5, state.offhandData);
            ps.setInt(6, state.expLevel);
            ps.setFloat(7, state.expProgress);
            ps.setDouble(8, state.health);
            ps.setInt(9, state.foodLevel);
            ps.setFloat(10, state.saturation);
            ps.executeUpdate();
        }
    }

    private static String serializeItems(ItemStack[] items) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BukkitObjectOutputStream data = new BukkitObjectOutputStream(out);
            data.writeInt(items.length);
            for (ItemStack item : items) {
                data.writeObject(item);
            }
            data.close();
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private static ItemStack[] deserializeItems(String encoded) {
        if (encoded == null || encoded.isEmpty()) return new ItemStack[0];
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
            BukkitObjectInputStream data = new BukkitObjectInputStream(in);
            int size = data.readInt();
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) data.readObject();
            }
            data.close();
            return items;
        } catch (Exception e) {
            return new ItemStack[0];
        }
    }

    private static String serializeItem(ItemStack item) {
        return serializeItems(new ItemStack[]{item});
    }

    private static ItemStack deserializeItem(String encoded) {
        ItemStack[] items = deserializeItems(encoded);
        return items.length > 0 ? items[0] : null;
    }

    private record PlayerState(String username, String group, String invData, String armorData,
                               String offhandData, int expLevel, float expProgress,
                               double health, int foodLevel, float saturation) {}

    private record LoadedState(ItemStack[] inventory, ItemStack[] armor, ItemStack offhand,
                               int expLevel, float expProgress, double health,
                               int foodLevel, float saturation) {}
}
