package com.starlightuniverse.maintenance;

import com.starlightuniverse.database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class MaintenanceListener implements Listener {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor RED = TextColor.color(0xFF5555);

    private final MaintenanceManager manager;
    private final DatabaseManager db;

    public MaintenanceListener(MaintenanceManager manager, DatabaseManager db) {
        this.manager = manager;
        this.db = db;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!manager.isActive()) return;

        int adminLevel = 0;
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT admin_level FROM su_players WHERE username = ?")) {
            ps.setString(1, event.getName().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) adminLevel = rs.getInt("admin_level");
            }
        } catch (Exception ignored) {
            adminLevel = 0;
        }

        if (adminLevel < MaintenanceManager.STAFF_ADMIN_LEVEL) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("[SU] ", GOLD)
                            .append(Component.text(MaintenanceManager.BLOCK_MESSAGE, RED)));
        }
    }
}
