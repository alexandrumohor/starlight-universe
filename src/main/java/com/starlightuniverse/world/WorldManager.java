package com.starlightuniverse.world;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.Set;

public class WorldManager implements Listener {

    public static final String LOBBY = "lobby";
    public static final String SURVIVAL_LOBBY = "survivallobby";
    public static final String OVERWORLD = "overworld";
    public static final String WORLD_NETHER = "world_the_nether";
    public static final String WORLD_THE_END = "world_the_end";
    public static final String RESOURCE_OVERWORLD = "resource_overworld";
    public static final String RESOURCE_NETHER = "resource_nether";
    public static final String RESOURCE_END = "resource_end";
    public static final String WORLD_DRAGON = "world_dragon";

    private static final Set<String> LOBBY_WORLDS = Set.of(LOBBY);
    private static final Set<String> SURVIVAL_WORLDS = Set.of(
            SURVIVAL_LOBBY, OVERWORLD, WORLD_NETHER, WORLD_THE_END,
            RESOURCE_OVERWORLD, RESOURCE_NETHER, RESOURCE_END, WORLD_DRAGON
    );

    private static final ZoneId ROMANIA_ZONE = ZoneId.of("Europe/Bucharest");
    private static final TextColor DRAGON_COLOR = TextColor.color(0xAA00AA);

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private boolean dragonLocked = false;

    public WorldManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public enum WorldGroup {
        LOBBY, SURVIVAL, UNKNOWN
    }

    public static WorldGroup getWorldGroup(World world) {
        if (world == null) return WorldGroup.UNKNOWN;
        return getWorldGroup(world.getName());
    }

    public static WorldGroup getWorldGroup(String worldName) {
        String stripped = canonicalWorldName(stripNamespace(worldName));
        if (LOBBY_WORLDS.contains(stripped)) return WorldGroup.LOBBY;
        if (SURVIVAL_WORLDS.contains(stripped)) return WorldGroup.SURVIVAL;
        return WorldGroup.UNKNOWN;
    }

    private static String canonicalWorldName(String name) {
        return switch (name) {
            case "world" -> OVERWORLD;
            case "world_nether", "the_nether", "nether" -> WORLD_NETHER;
            case "world_the_end", "the_end", "end" -> WORLD_THE_END;
            default -> name;
        };
    }

    /**
     * Paper 26.2 stores worlds under {@code world/dimensions/<namespace>/<name>/},
     * exposing them via the API as e.g. {@code minecraft:lobby}. This finds a world
     * by short name regardless of whether it was registered with a namespace prefix.
     */
    public static World findWorld(String shortName) {
        World w = Bukkit.getWorld(shortName);
        if (w != null) return w;
        try {
            w = Bukkit.getWorld(org.bukkit.NamespacedKey.minecraft(shortName));
            if (w != null) return w;
        } catch (Throwable ignored) {}
        w = Bukkit.getWorld("minecraft:" + shortName);
        if (w != null) return w;
        // Aliases for the vanilla main-world names (Paper's key is minecraft:overworld etc.)
        String alias = switch (shortName) {
            case "overworld" -> "world";
            case "world_the_nether", "the_nether", "nether" -> "world_nether";
            case "world_the_end", "the_end", "end" -> "world_the_end";
            default -> null;
        };
        if (alias != null) {
            w = Bukkit.getWorld(alias);
            if (w != null) return w;
        }
        for (World world : Bukkit.getWorlds()) {
            String n = world.getName();
            if (n.equals(shortName) || n.equals("minecraft:" + shortName) || n.endsWith(":" + shortName)) {
                return world;
            }
            try {
                if (world.getKey().getKey().equals(shortName)) return world;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String stripNamespace(String name) {
        if (name == null) return "";
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    public void initialize() {
        // Paper 26.2 stores worlds under <primary-world>/dimensions/<ns>/<name>/,
        // but does NOT auto-load them unless a datapack registers them. We must
        // trigger the load explicitly via a NamespacedKey WorldCreator.
        loadDimensionIfExists("lobby", World.Environment.NORMAL);
        loadDimensionIfExists("survivallobby", World.Environment.NORMAL);

        // Resource worlds — created by plugin (reset on 1st of month).
        ensureWorld(RESOURCE_OVERWORLD, World.Environment.NORMAL);
        ensureWorld(RESOURCE_NETHER, World.Environment.NETHER);
        ensureWorld(RESOURCE_END, World.Environment.THE_END);

        if (findWorld(WORLD_DRAGON) == null) {
            createDragonWorld();
        }

        configureWorlds();

        if (isDragonResetDue()) {
            performDragonReset();
            saveDragonResetTime();
            plugin.getLogger().info("[SU] Dragon world reset on startup.");
        }

        checkResourceWorldReset();

        scheduleDragonResetCheck();
        scheduleResourceWorldCheck();
    }

    private void ensureWorld(String name, World.Environment env) {
        if (findWorld(name) != null) return;
        try {
            WorldCreator creator = new WorldCreator(name);
            creator.environment(env);
            creator.generateStructures(true);
            World world = Bukkit.createWorld(creator);
            if (world != null) {
                plugin.getLogger().info("[SU] World '" + name + "' (" + env + ") loaded/created.");
            } else {
                plugin.getLogger().warning("[SU] Failed to create world '" + name + "'.");
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[SU] Skipping world '" + name + "': " + e.getMessage());
        }
    }

    /**
     * Loads an existing dimension from Paper's new layout
     * ({@code <primary-world>/dimensions/minecraft/<name>/}) via a NamespacedKey
     * WorldCreator. Skips if the folder isn't present so we never overwrite
     * a hand-configured world with a blank one.
     */
    private void loadDimensionIfExists(String name, World.Environment env) {
        if (findWorld(name) != null) return;

        File primaryWorldDir = new File(Bukkit.getWorldContainer(),
                Bukkit.getWorlds().getFirst().getName());
        File dimFolder = new File(primaryWorldDir, "dimensions/minecraft/" + name);
        if (!dimFolder.isDirectory() || !new File(dimFolder, "region").isDirectory()) {
            plugin.getLogger().info("[SU] Dimension '" + name
                    + "' not present at " + dimFolder.getPath() + " — skipping load.");
            return;
        }
        try {
            WorldCreator creator = new WorldCreator(
                    org.bukkit.NamespacedKey.minecraft(name));
            creator.environment(env);
            World w = Bukkit.createWorld(creator);
            if (w != null) {
                plugin.getLogger().info("[SU] Dimension 'minecraft:" + name + "' loaded.");
            } else {
                plugin.getLogger().warning("[SU] createWorld returned null for 'minecraft:" + name + "'.");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[SU] Failed to load 'minecraft:" + name + "': " + t.getMessage());
        }
    }

    private void configureWorlds() {
        World lobby = findWorld(LOBBY);
        if (lobby != null) {
            lobby.setPVP(false);
            lobby.setSpawnFlags(false, false);
            lobby.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            lobby.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            lobby.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            lobby.setGameRule(GameRule.DO_FIRE_TICK, false);
            lobby.setGameRule(GameRule.MOB_GRIEFING, false);
            lobby.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            lobby.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            lobby.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
            lobby.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
            lobby.setGameRule(GameRule.FALL_DAMAGE, false);
            lobby.setGameRule(GameRule.FIRE_DAMAGE, false);
            lobby.setGameRule(GameRule.DROWNING_DAMAGE, false);
            lobby.setTime(18000);
            lobby.setStorm(false);
            lobby.setThundering(false);
            lobby.setDifficulty(Difficulty.PEACEFUL);
        }

        World survivalLobby = findWorld(SURVIVAL_LOBBY);
        if (survivalLobby != null) {
            survivalLobby.setPVP(false);
            survivalLobby.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        }

        for (String name : new String[]{OVERWORLD, WORLD_NETHER, WORLD_THE_END,
                RESOURCE_OVERWORLD, RESOURCE_NETHER, RESOURCE_END, WORLD_DRAGON}) {
            World w = findWorld(name);
            if (w != null) {
                w.setPVP(true);
            }
        }

        applyWorldBorder(OVERWORLD, 75_000);
        applyWorldBorder(WORLD_NETHER, 50_000);
        applyWorldBorder(WORLD_THE_END, 50_000);
        applyWorldBorder(RESOURCE_OVERWORLD, 10_000);
        applyWorldBorder(RESOURCE_NETHER, 10_000);
        applyWorldBorder(RESOURCE_END, 10_000);
    }

    private void applyWorldBorder(String worldName, double size) {
        World w = findWorld(worldName);
        if (w == null) return;
        w.getWorldBorder().setCenter(0, 0);
        w.getWorldBorder().setSize(size);
        w.getWorldBorder().setWarningDistance(16);
        w.getWorldBorder().setDamageBuffer(5);
    }

    // ── Dragon World ──

    private void createDragonWorld() {
        WorldCreator creator = new WorldCreator(WORLD_DRAGON);
        creator.environment(World.Environment.THE_END);
        creator.generateStructures(true);
        World world = Bukkit.createWorld(creator);
        if (world != null) {
            world.setPVP(true);
            plugin.getLogger().info("[SU] Dragon world created.");
        }
    }

    private boolean isDragonResetDue() {
        ZonedDateTime now = ZonedDateTime.now(ROMANIA_ZONE);
        long nowEpoch = now.toEpochSecond();

        long[] resets = {
                now.toLocalDate().atTime(0, 0).atZone(ROMANIA_ZONE).toEpochSecond(),
                now.toLocalDate().atTime(6, 0).atZone(ROMANIA_ZONE).toEpochSecond(),
                now.toLocalDate().atTime(12, 0).atZone(ROMANIA_ZONE).toEpochSecond(),
                now.toLocalDate().atTime(18, 0).atZone(ROMANIA_ZONE).toEpochSecond()
        };

        String lastStr = getServerData("dragon_last_reset");
        long lastReset = 0;
        if (lastStr != null) {
            try { lastReset = Long.parseLong(lastStr); } catch (Exception ignored) {}
        }

        for (long reset : resets) {
            if (nowEpoch >= reset && lastReset < reset) return true;
        }
        return false;
    }

    private void scheduleDragonResetCheck() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!dragonLocked && isDragonResetDue()) {
                startDragonReset();
            }
        }, 600L, 600L);
    }

    private void startDragonReset() {
        dragonLocked = true;

        World dragon = findWorld(WORLD_DRAGON);
        World lobby = findWorld(LOBBY);
        if (dragon != null && lobby != null) {
            for (Player p : dragon.getPlayers()) {
                p.teleport(lobby.getSpawnLocation());
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            performDragonReset();
            saveDragonResetTime();

            Component msg = Component.text("[SU] ", TextColor.color(0xFFD700))
                    .append(Component.text("The Dragon has been respawned!", DRAGON_COLOR)
                            .decoration(TextDecoration.BOLD, true));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(msg);
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> dragonLocked = false, 300L);
        }, 400L);
    }

    private void performDragonReset() {
        World dragon = findWorld(WORLD_DRAGON);
        if (dragon != null) {
            World lobby = findWorld(LOBBY);
            for (Player p : dragon.getPlayers()) {
                if (lobby != null) p.teleport(lobby.getSpawnLocation());
            }
            Bukkit.unloadWorld(dragon, false);
        }

        File folder = new File(Bukkit.getWorldContainer(), WORLD_DRAGON);
        deleteDirectory(folder);
        createDragonWorld();
    }

    private void saveDragonResetTime() {
        setServerData("dragon_last_reset",
                String.valueOf(ZonedDateTime.now(ROMANIA_ZONE).toEpochSecond()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) return;

        if (dragonLocked && event.getTo().getWorld().getName().equals(WORLD_DRAGON)) {
            event.setCancelled(true);
            Msg.error(event.getPlayer(), "The Dragon is respawning! Please wait...");
            return;
        }

        if (event.getFrom().getWorld().getName().equals(WORLD_DRAGON)
                && event.getCause() == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            event.setCancelled(true);
            Msg.error(event.getPlayer(), "End Cities are not accessible from this world!");
        }
    }

    /**
     * When a player dies in any survival world without a valid bed / respawn
     * anchor, redirect them to the survival lobby's spawn instead of the
     * default overworld world spawn. Bed and anchor spawns are respected.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn() || event.isAnchorSpawn()) return;
        WorldGroup deathGroup = getWorldGroup(event.getPlayer().getWorld());
        if (deathGroup != WorldGroup.SURVIVAL) return;
        World survivalLobby = findWorld(SURVIVAL_LOBBY);
        if (survivalLobby == null) return;
        event.setRespawnLocation(survivalLobby.getSpawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleportMonitor(PlayerTeleportEvent event) {
        if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null) return;
        WorldGroup fromGroup = getWorldGroup(event.getFrom().getWorld());
        WorldGroup toGroup = getWorldGroup(event.getTo().getWorld());
        // When a player leaves the auth lobby into a survival world, drop the
        // forced-midnight client-time override so they see the destination's
        // actual day/night cycle.
        if (fromGroup == WorldGroup.LOBBY && toGroup != WorldGroup.LOBBY) {
            event.getPlayer().resetPlayerTime();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) return;

        if (event.getFrom().getWorld().getName().equals(WORLD_DRAGON)
                && event.getCause() == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            event.setCancelled(true);
            Msg.error(event.getPlayer(), "End Cities are not accessible from this world!");
            return;
        }

        if (dragonLocked && event.getTo().getWorld().getName().equals(WORLD_DRAGON)) {
            event.setCancelled(true);
            Msg.error(event.getPlayer(), "The Dragon is respawning! Please wait...");
        }
    }

    // ── Resource World Reset ──

    private void checkResourceWorldReset() {
        LocalDate today = LocalDate.now(ROMANIA_ZONE);
        if (today.getDayOfMonth() != 1) return;

        LocalTime now = LocalTime.now(ROMANIA_ZONE);
        if (now.isBefore(LocalTime.of(6, 0))) return;

        String lastReset = getServerData("resource_last_reset");
        if (lastReset != null) {
            try {
                YearMonth lastResetMonth = YearMonth.parse(lastReset);
                if (lastResetMonth.equals(YearMonth.from(today))) return;
            } catch (Exception ignored) {}
        }

        resetResourceWorlds();
        setServerData("resource_last_reset", YearMonth.from(today).toString());
    }

    private void resetResourceWorlds() {
        plugin.getLogger().info("[SU] Resetting resource worlds...");
        String[] names = {RESOURCE_OVERWORLD, RESOURCE_NETHER, RESOURCE_END};
        World.Environment[] envs = {World.Environment.NORMAL, World.Environment.NETHER, World.Environment.THE_END};

        World lobby = findWorld(LOBBY);

        for (int i = 0; i < names.length; i++) {
            World world = findWorld(names[i]);
            if (world != null) {
                if (lobby != null) {
                    for (Player p : world.getPlayers()) {
                        p.teleport(lobby.getSpawnLocation());
                        Msg.info(p, "Resource worlds are being reset!");
                    }
                }
                Bukkit.unloadWorld(world, false);
            }

            File worldFolder = new File(Bukkit.getWorldContainer(), names[i]);
            deleteDirectory(worldFolder);

            WorldCreator creator = new WorldCreator(names[i]);
            creator.environment(envs[i]);
            creator.generateStructures(true);
            Bukkit.createWorld(creator);
            applyWorldBorder(names[i], 10_000);
        }
        plugin.getLogger().info("[SU] Resource worlds have been reset!");
    }

    private void scheduleResourceWorldCheck() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkResourceWorldReset, 72000L, 72000L);
    }

    // ── Utilities ──

    private void deleteDirectory(File dir) {
        if (!dir.exists()) return;
        try {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            plugin.getLogger().warning("[SU] Failed to delete world folder: " + dir.getName());
        }
    }

    public String getServerData(String key) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT data_value FROM su_server_data WHERE data_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("data_value");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[SU] Failed to read server data: " + key);
        }
        return null;
    }

    public void setServerData(String key, String value) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO su_server_data (data_key, data_value) VALUES (?, ?) " +
                             "ON DUPLICATE KEY UPDATE data_value = VALUES(data_value)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[SU] Failed to save server data: " + key);
        }
    }

    public boolean isDragonLocked() {
        return dragonLocked;
    }
}
