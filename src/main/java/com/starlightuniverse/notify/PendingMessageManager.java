package com.starlightuniverse.notify;

import com.starlightuniverse.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Queues Adventure {@link Component} messages for offline players and shows
 * them on their next login. Backed by the {@code su_pending_messages} table.
 * Messages are stored as their gson-serialized JSON so styling / hover / click
 * events survive.
 */
public class PendingMessageManager {

    private final JavaPlugin plugin;
    private final DatabaseManager db;

    public PendingMessageManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    /** Queue a message for {@code username} to see on next login. Runs async. */
    public void enqueue(String username, Component message) {
        String json = GsonComponentSerializer.gson().serialize(message);
        String lower = username.toLowerCase();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_pending_messages (username, message) VALUES (?, ?)")) {
                ps.setString(1, lower);
                ps.setString(2, json);
                ps.executeUpdate();
            }
        });
    }

    /** Fetch and deliver all queued messages for this player, then delete them. */
    public void flush(Player player) {
        String lower = player.getName().toLowerCase();
        db.queryAsync(conn -> {
            List<int[]> ids = new ArrayList<>();
            List<String> jsons = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, message FROM su_pending_messages WHERE username = ? ORDER BY id",
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                ps.setString(1, lower);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(new int[]{rs.getInt("id")});
                        jsons.add(rs.getString("message"));
                    }
                }
            }
            if (ids.isEmpty()) return new Object[]{ids, jsons};
            try (Statement st = conn.createStatement()) {
                StringBuilder sb = new StringBuilder("DELETE FROM su_pending_messages WHERE id IN (");
                for (int i = 0; i < ids.size(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(ids.get(i)[0]);
                }
                sb.append(')');
                st.executeUpdate(sb.toString());
            }
            return new Object[]{ids, jsons};
        }).thenAccept(result -> {
            if (result == null) return;
            @SuppressWarnings("unchecked")
            List<String> jsons = (List<String>) ((Object[]) result)[1];
            if (jsons.isEmpty()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                for (String json : jsons) {
                    try {
                        player.sendMessage(GsonComponentSerializer.gson().deserialize(json));
                    } catch (Exception ignored) {}
                }
            });
        });
    }
}
