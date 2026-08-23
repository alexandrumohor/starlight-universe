package com.starlightuniverse.world;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class QueueManager {

    private static final TextColor GOLD = TextColor.color(0xFFD700);

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final Deque<UUID> priorityQueue = new ArrayDeque<>();
    private final Deque<UUID> normalQueue = new ArrayDeque<>();
    private final Set<UUID> inQueue = new HashSet<>();
    private BukkitTask queueTask;

    public QueueManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        queueTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processQueue, 20L, 20L);
    }

    public void stop() {
        if (queueTask != null) {
            queueTask.cancel();
        }
        inQueue.clear();
        priorityQueue.clear();
        normalQueue.clear();
    }

    public void addToQueue(Player player) {
        UUID uuid = player.getUniqueId();
        if (inQueue.contains(uuid)) {
            Msg.info(player, "You are already in the queue!");
            return;
        }

        inQueue.add(uuid);
        Msg.info(player, "Joining queue...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int premiumLevel = getPremiumLevel(player.getName());

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !inQueue.contains(uuid)) return;

                if (premiumLevel >= 4) {
                    priorityQueue.addLast(uuid);
                    Msg.success(player, "Added to priority queue! Your rank gives you priority.");
                } else {
                    normalQueue.addLast(uuid);
                    Msg.success(player, "Added to queue! Please wait...");
                }
            });
        });
    }

    public void removeFromQueue(UUID uuid) {
        inQueue.remove(uuid);
        priorityQueue.remove(uuid);
        normalQueue.remove(uuid);
    }

    private void processQueue() {
        cleanDisconnected();
        updateActionBars();

        UUID next = priorityQueue.pollFirst();
        if (next == null) {
            next = normalQueue.pollFirst();
        }
        if (next == null) return;

        inQueue.remove(next);
        Player player = Bukkit.getPlayer(next);
        if (player == null || !player.isOnline()) return;

        teleportToSurvival(player);
    }

    private void cleanDisconnected() {
        priorityQueue.removeIf(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                inQueue.remove(uuid);
                return true;
            }
            return false;
        });
        normalQueue.removeIf(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                inQueue.remove(uuid);
                return true;
            }
            return false;
        });
    }

    private void updateActionBars() {
        int totalSize = priorityQueue.size() + normalQueue.size();
        if (totalSize == 0) return;

        int position = 1;
        for (UUID uuid : priorityQueue) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendActionBar(Component.text("Pozitia ta: " + position + " / " + totalSize, GOLD));
            }
            position++;
        }
        for (UUID uuid : normalQueue) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendActionBar(Component.text("Pozitia ta: " + position + " / " + totalSize, GOLD));
            }
            position++;
        }
    }

    private void teleportToSurvival(Player player) {
        World world = Bukkit.getWorld(WorldManager.SURVIVAL_LOBBY);
        if (world != null) {
            player.teleport(world.getSpawnLocation());
            Msg.success(player, "Welcome to Survival!");
        } else {
            Msg.error(player, "Survival world is not available!");
        }
    }

    private int getPremiumLevel(String username) {
        try (var conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT premium_level FROM su_players WHERE username = ?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("premium_level");
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
