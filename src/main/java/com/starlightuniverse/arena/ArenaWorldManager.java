package com.starlightuniverse.arena;

import com.starlightuniverse.database.DatabaseManager;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaWorldManager {

    private static final int BLOCKS_PER_TICK = 8000;

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final Set<String> builtWorlds = ConcurrentHashMap.newKeySet();
    private final Set<String> buildingWorlds = ConcurrentHashMap.newKeySet();

    private static List<int[]> cachedCircle;
    private static List<int[]> cachedRing;

    private BukkitTask currentTask;

    public ArenaWorldManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void initialize() {
        loadBuiltFlags();
        for (String name : ArenaWorlds.ALL_WORLDS) {
            createWorld(name);
        }
        scheduleNextBuild();
    }

    public boolean isReady(String worldName) {
        return builtWorlds.contains(worldName) && Bukkit.getWorld(worldName) != null;
    }

    public boolean isBuilding(String worldName) {
        return buildingWorlds.contains(worldName);
    }

    private void loadBuiltFlags() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT data_value FROM su_server_data WHERE data_key = ?")) {
            ps.setString(1, "arena_built_worlds");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString("data_value");
                    if (value != null && !value.isEmpty()) {
                        for (String s : value.split(",")) {
                            if (!s.isEmpty()) builtWorlds.add(s);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[SU] Failed to read arena build flags: " + e.getMessage());
        }
    }

    private void saveBuiltFlags() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO su_server_data (data_key, data_value) VALUES (?, ?) " +
                             "ON DUPLICATE KEY UPDATE data_value = VALUES(data_value)")) {
            ps.setString(1, "arena_built_worlds");
            ps.setString(2, String.join(",", builtWorlds));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[SU] Failed to save arena build flags: " + e.getMessage());
        }
    }

    private void createWorld(String name) {
        if (Bukkit.getWorld(name) != null) return;

        WorldCreator creator = new WorldCreator(name);
        creator.generator(new VoidChunkGenerator());
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        creator.environment(World.Environment.NORMAL);

        World world = Bukkit.createWorld(creator);
        if (world == null) {
            plugin.getLogger().severe("[SU] Failed to create arena world: " + name);
            return;
        }

        world.setSpawnLocation(ArenaWorlds.CENTER_X, ArenaWorlds.SPAWN_Y, ArenaWorlds.CENTER_Z);
        world.setPVP(true);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        world.setDifficulty(Difficulty.NORMAL);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);

        plugin.getLogger().info("[SU] Arena world ready: " + name);
    }

    private void scheduleNextBuild() {
        String next = null;
        for (String name : ArenaWorlds.ALL_WORLDS) {
            if (!builtWorlds.contains(name)) {
                next = name;
                break;
            }
        }
        if (next == null) {
            plugin.getLogger().info("[SU] All arena worlds already built.");
            return;
        }
        buildArena(next);
    }

    private void buildArena(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("[SU] Cannot build arena — world missing: " + worldName);
            return;
        }
        buildingWorlds.add(worldName);
        plugin.getLogger().info("[SU] Building arena in " + worldName + "... (this may take a minute)");

        BuildTask task = new BuildTask(world);
        currentTask = task.runTaskTimer(plugin, 1L, 1L);
    }

    private synchronized List<int[]> getCircle() {
        if (cachedCircle != null) return cachedCircle;
        List<int[]> list = new ArrayList<>();
        int r2 = ArenaWorlds.RADIUS * ArenaWorlds.RADIUS;
        for (int x = -ArenaWorlds.RADIUS; x <= ArenaWorlds.RADIUS; x++) {
            for (int z = -ArenaWorlds.RADIUS; z <= ArenaWorlds.RADIUS; z++) {
                if (x * x + z * z <= r2) list.add(new int[]{x, z});
            }
        }
        cachedCircle = Collections.unmodifiableList(list);
        return cachedCircle;
    }

    private synchronized List<int[]> getRing() {
        if (cachedRing != null) return cachedRing;
        List<int[]> list = new ArrayList<>();
        int r2 = ArenaWorlds.RADIUS * ArenaWorlds.RADIUS;
        int rm2 = (ArenaWorlds.RADIUS - 1) * (ArenaWorlds.RADIUS - 1);
        for (int x = -ArenaWorlds.RADIUS; x <= ArenaWorlds.RADIUS; x++) {
            for (int z = -ArenaWorlds.RADIUS; z <= ArenaWorlds.RADIUS; z++) {
                int d2 = x * x + z * z;
                if (d2 <= r2 && d2 > rm2) list.add(new int[]{x, z});
            }
        }
        cachedRing = Collections.unmodifiableList(list);
        return cachedRing;
    }

    private class BuildTask extends BukkitRunnable {
        private final World world;
        private final java.util.Random random = new java.util.Random(ArenaWorlds.PATTERN_SEED);

        private int stage = 0;
        private int circleIdx = 0;
        private int ringIdx = 0;
        private int wallY = ArenaWorlds.FLOOR_Y + 1;
        private long placed = 0;
        private long total;
        private long lastLogged = 0;

        BuildTask(World world) {
            this.world = world;
            long floorCeiling = (long) getCircle().size() * 2L;
            long walls = (long) getRing().size() *
                    (ArenaWorlds.CEILING_Y - ArenaWorlds.FLOOR_Y - 1);
            this.total = floorCeiling + walls;
        }

        @Override
        public void run() {
            List<int[]> circle = getCircle();
            List<int[]> ring = getRing();
            int budget = BLOCKS_PER_TICK;

            while (budget > 0) {
                switch (stage) {
                    case 0 -> {
                        if (circleIdx >= circle.size()) {
                            stage = 1;
                            circleIdx = 0;
                            continue;
                        }
                        int[] p = circle.get(circleIdx++);
                        placeBlock(p[0], ArenaWorlds.FLOOR_Y, p[1]);
                        budget--;
                    }
                    case 1 -> {
                        if (circleIdx >= circle.size()) {
                            stage = 2;
                            continue;
                        }
                        int[] p = circle.get(circleIdx++);
                        placeBlock(p[0], ArenaWorlds.CEILING_Y, p[1]);
                        budget--;
                    }
                    case 2 -> {
                        if (wallY >= ArenaWorlds.CEILING_Y) {
                            finish();
                            return;
                        }
                        if (ringIdx >= ring.size()) {
                            wallY++;
                            ringIdx = 0;
                            continue;
                        }
                        int[] p = ring.get(ringIdx++);
                        placeBlock(p[0], wallY, p[1]);
                        budget--;
                    }
                    default -> {
                        finish();
                        return;
                    }
                }
            }

            if (placed - lastLogged >= 100_000) {
                lastLogged = placed;
                int pct = (int) (placed * 100 / Math.max(1, total));
                plugin.getLogger().info("[SU] Arena " + world.getName() +
                        " — " + placed + " / " + total + " blocks (" + pct + "%)");
            }
        }

        private void placeBlock(int x, int y, int z) {
            Material mat = random.nextInt(ArenaWorlds.FROGLIGHT_ONE_IN) == 0
                    ? ArenaWorlds.FROGLIGHT
                    : ArenaWorlds.BLOCKS[random.nextInt(ArenaWorlds.BLOCKS.length)];
            world.getBlockAt(x, y, z).setType(mat, false);
            placed++;
        }

        private void finish() {
            cancel();
            currentTask = null;
            buildingWorlds.remove(world.getName());
            builtWorlds.add(world.getName());
            saveBuiltFlags();
            world.save();
            plugin.getLogger().info("[SU] Arena built in " + world.getName() +
                    " (" + placed + " blocks).");
            scheduleNextBuild();
        }
    }

    public void shutdown() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
    }
}
