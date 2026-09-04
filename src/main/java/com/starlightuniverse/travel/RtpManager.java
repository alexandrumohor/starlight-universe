package com.starlightuniverse.travel;

import com.starlightuniverse.util.Msg;
import com.starlightuniverse.world.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RtpManager {

    public static final int OVERWORLD_RADIUS = 37_500;
    public static final int NETHER_RADIUS = 25_000;
    public static final int END_RADIUS = 25_000;
    public static final int RESOURCE_RADIUS = 5_000;

    public static final long COOLDOWN_MS = 3_000L;
    private static final int MAX_SEARCH_TICKS = 100;
    private static final int CHECKS_PER_TICK = 5;
    private static final int SAFE_RADIUS = 5;
    private static final int Y_TOLERANCE = 3;

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);

    private static final Set<Material> DANGEROUS_SURFACE = Set.of(
            Material.LAVA, Material.WATER, Material.CACTUS, Material.MAGMA_BLOCK,
            Material.SWEET_BERRY_BUSH, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.POWDER_SNOW, Material.POINTED_DRIPSTONE, Material.WITHER_ROSE,
            Material.FIRE, Material.SOUL_FIRE
    );

    private static final Set<Material> DANGEROUS_BODY = Set.of(
            Material.LAVA, Material.WATER, Material.FIRE, Material.SOUL_FIRE,
            Material.SWEET_BERRY_BUSH, Material.COBWEB, Material.POWDER_SNOW,
            Material.WITHER_ROSE
    );

    private static final Set<Biome> OCEAN_BIOMES = Set.of(
            Biome.OCEAN, Biome.DEEP_OCEAN, Biome.WARM_OCEAN,
            Biome.LUKEWARM_OCEAN, Biome.COLD_OCEAN, Biome.FROZEN_OCEAN,
            Biome.DEEP_LUKEWARM_OCEAN, Biome.DEEP_COLD_OCEAN, Biome.DEEP_FROZEN_OCEAN
    );

    public enum RtpWorld {
        OVERWORLD(WorldManager.OVERWORLD, "Overworld", "#55FF55", Material.GRASS_BLOCK, OVERWORLD_RADIUS, false, Kind.SURFACE),
        NETHER(WorldManager.WORLD_NETHER, "Nether", "#FF5555", Material.NETHERRACK, NETHER_RADIUS, false, Kind.NETHER),
        END(WorldManager.WORLD_THE_END, "The End", "#AA00AA", Material.END_STONE, END_RADIUS, false, Kind.END),
        RESOURCE_OVERWORLD(WorldManager.RESOURCE_OVERWORLD, "Resource Overworld", "#55FFFF", Material.OAK_LOG, RESOURCE_RADIUS, true, Kind.SURFACE),
        RESOURCE_NETHER(WorldManager.RESOURCE_NETHER, "Resource Nether", "#FFAA00", Material.NETHER_BRICKS, RESOURCE_RADIUS, true, Kind.NETHER),
        RESOURCE_END(WorldManager.RESOURCE_END, "Resource End", "#AAAAAA", Material.CHORUS_FRUIT, RESOURCE_RADIUS, true, Kind.END);

        public enum Kind { SURFACE, NETHER, END }

        public final String worldName;
        public final String display;
        public final TextColor color;
        public final Material icon;
        public final int radius;
        public final boolean resource;
        public final Kind kind;

        RtpWorld(String worldName, String display, String hex, Material icon, int radius, boolean resource, Kind kind) {
            this.worldName = worldName;
            this.display = display;
            this.color = TextColor.fromHexString(hex);
            this.icon = icon;
            this.radius = radius;
            this.resource = resource;
            this.kind = kind;
        }

        public static RtpWorld byKey(String key) {
            for (RtpWorld r : values()) {
                if (r.name().equalsIgnoreCase(key) || r.worldName.equalsIgnoreCase(key)) return r;
            }
            return null;
        }
    }

    private final JavaPlugin plugin;
    private final WorldManager worldManager;

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> searching = ConcurrentHashMap.newKeySet();
    private final Set<RtpWorld> lockedWorlds = ConcurrentHashMap.newKeySet();

    public RtpManager(JavaPlugin plugin, WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        loadLocks();
    }

    private void loadLocks() {
        for (RtpWorld r : RtpWorld.values()) {
            String v = worldManager.getServerData("rtp_lock_" + r.name());
            if ("1".equals(v)) lockedWorlds.add(r);
        }
    }

    public boolean isLocked(RtpWorld r) {
        return lockedWorlds.contains(r);
    }

    public void setLocked(RtpWorld r, boolean locked) {
        if (locked) lockedWorlds.add(r); else lockedWorlds.remove(r);
        worldManager.setServerData("rtp_lock_" + r.name(), locked ? "1" : "0");
    }

    public void clearCooldown(UUID uuid) {
        cooldowns.remove(uuid);
        searching.remove(uuid);
    }

    // ===== GUI =====

    public void openGui(Player player) {
        RtpHolder holder = new RtpHolder();
        Inventory inv = Bukkit.createInventory(holder, 36,
                Component.text("Random Teleport", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        RtpWorld[] worlds = RtpWorld.values();
        int[] slots = {11, 13, 15, 20, 22, 24};

        for (int i = 0; i < worlds.length; i++) {
            RtpWorld r = worlds[i];
            World w = WorldManager.findWorld(r.worldName);
            int players = w == null ? 0 : w.getPlayers().size();
            boolean locked = isLocked(r);

            ItemStack item = new ItemStack(locked ? Material.BARRIER : r.icon);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(r.display, r.color).decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("World: " + r.worldName, GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Status: ", GRAY)
                    .append(Component.text(locked ? "LOCKED" : "OPEN", locked ? RED : GREEN))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Players there: " + players, GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Border: " + formatBorderSize(r.radius), GRAY).decoration(TextDecoration.ITALIC, false));
            if (r.resource) {
                lore.add(Component.text("Next Reset: " + nextResourceResetDisplay(), YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            if (locked) {
                lore.add(Component.text("This dimension is locked!", RED).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Left-click: teleport (FREE)", GREEN).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Cooldown: 3s", GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(slots[i], item);
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(Component.text("Close", RED).decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(closeMeta);
        inv.setItem(31, close);

        player.openInventory(inv);
    }

    private String formatBorderSize(int radius) {
        int size = radius * 2;
        String s = size >= 1000 && size % 1000 == 0
                ? (size / 1000) + "k"
                : String.valueOf(size);
        return s + " x " + s;
    }

    private String nextResourceResetDisplay() {
        LocalDate now = LocalDate.now();
        LocalDate firstOfMonth = now.getDayOfMonth() == 1
                ? now.plusMonths(1).withDayOfMonth(1)
                : now.plusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
        return firstOfMonth.toString() + " 06:00 RO";
    }

    public RtpWorld getWorldFromSlot(int slot) {
        return switch (slot) {
            case 11 -> RtpWorld.OVERWORLD;
            case 13 -> RtpWorld.NETHER;
            case 15 -> RtpWorld.END;
            case 20 -> RtpWorld.RESOURCE_OVERWORLD;
            case 22 -> RtpWorld.RESOURCE_NETHER;
            case 24 -> RtpWorld.RESOURCE_END;
            default -> null;
        };
    }

    // ===== Teleport =====

    public void teleport(Player player, RtpWorld r) {
        if (isLocked(r)) {
            Msg.error(player, r.display + " is locked!");
            return;
        }

        UUID uuid = player.getUniqueId();

        if (searching.contains(uuid)) {
            Msg.error(player, "You are already searching for a safe location!");
            return;
        }

        long now = System.currentTimeMillis();
        Long last = cooldowns.get(uuid);
        if (last != null && now - last < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (now - last)) / 1000 + 1;
            Msg.error(player, "Please wait " + remaining + "s before teleporting again!");
            return;
        }

        World world = WorldManager.findWorld(r.worldName);
        if (world == null) {
            Msg.error(player, "World not loaded: " + r.display);
            return;
        }

        player.closeInventory();
        searching.add(uuid);
        Msg.info(player, "Searching for a safe location in " + r.display + "...");

        new BukkitRunnable() {
            final Random random = new Random();
            int ticks = 0;

            @Override
            public void run() {
                try {
                    if (!player.isOnline()) {
                        searching.remove(uuid);
                        cancel();
                        return;
                    }

                    if (ticks >= MAX_SEARCH_TICKS) {
                        searching.remove(uuid);
                        Msg.error(player, "Could not find a safe location. Please try again!");
                        cancel();
                        return;
                    }

                    for (int i = 0; i < CHECKS_PER_TICK; i++) {
                        Location safe = switch (r.kind) {
                            case SURFACE -> trySurfaceLocation(world, r, random);
                            case NETHER -> tryNetherLocation(world, r, random);
                            case END -> tryEndLocation(world, r, random);
                        };
                        if (safe != null) {
                            searching.remove(uuid);
                            cancel();
                            cooldowns.put(uuid, System.currentTimeMillis());
                            doTeleport(player, safe, r);
                            return;
                        }
                    }

                    ticks++;
                } catch (Exception e) {
                    searching.remove(uuid);
                    cancel();
                    plugin.getComponentLogger().error("RTP search error for {}", player.getName(), e);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void doTeleport(Player player, Location safe, RtpWorld r) {
        for (Entity passenger : new ArrayList<>(player.getPassengers())) {
            if (passenger instanceof TextDisplay) {
                player.removePassenger(passenger);
                passenger.remove();
            }
        }
        if (player.isInsideVehicle()) player.leaveVehicle();

        boolean success = player.teleport(safe, PlayerTeleportEvent.TeleportCause.COMMAND);
        if (success) {
            Msg.success(player, "Teleported to " + r.display + " at (" +
                    safe.getBlockX() + ", " + safe.getBlockY() + ", " + safe.getBlockZ() + ")");
            player.playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        } else {
            Msg.error(player, "Teleport was cancelled by another plugin!");
            cooldowns.remove(player.getUniqueId());
        }
    }

    // ===== Safe-location search =====

    private Location trySurfaceLocation(World world, RtpWorld r, Random random) {
        int x = random.nextInt(r.radius * 2 + 1) - r.radius;
        int z = random.nextInt(r.radius * 2 + 1) - r.radius;

        int highestY = world.getHighestBlockYAt(x, z);
        if (highestY < world.getMinHeight() + 5) return null;
        if (OCEAN_BIOMES.contains(world.getBiome(x, highestY, z))) return null;

        Location result = checkSafeSpot(world, x, highestY, z, random);
        if (result == null) return null;
        if (!hasOpenSkyAbove(world, x, highestY + 1, z)) return null;
        if (!hasSafeArea(world, x, highestY + 1, z)) return null;
        return result;
    }

    /**
     * True only if every block between {@code startY} and the world ceiling is
     * air / passable. Guarantees the spawn is at the true surface, not inside
     * a cave with a stone ceiling overhead.
     */
    private boolean hasOpenSkyAbove(World world, int x, int startY, int z) {
        int top = world.getMaxHeight() - 1;
        for (int y = startY; y <= top; y++) {
            Material t = world.getBlockAt(x, y, z).getType();
            if (t.isSolid()) return false;
        }
        return true;
    }

    private Location tryNetherLocation(World world, RtpWorld r, Random random) {
        int x = random.nextInt(r.radius * 2 + 1) - r.radius;
        int z = random.nextInt(r.radius * 2 + 1) - r.radius;

        for (int y = 100; y >= 32; y--) {
            Block floor = world.getBlockAt(x, y, z);
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);

            if (!floor.getType().isSolid()) continue;
            if (DANGEROUS_SURFACE.contains(floor.getType())) continue;
            if (!feet.isPassable() || DANGEROUS_BODY.contains(feet.getType())) continue;
            if (!head.isPassable() || DANGEROUS_BODY.contains(head.getType())) continue;

            if (!hasSafeArea(world, x, y + 1, z)) continue;

            return new Location(world, x + 0.5, y + 1, z + 0.5,
                    random.nextFloat() * 360 - 180, 0);
        }
        return null;
    }

    private Location tryEndLocation(World world, RtpWorld r, Random random) {
        int x = random.nextInt(r.radius * 2 + 1) - r.radius;
        int z = random.nextInt(r.radius * 2 + 1) - r.radius;

        int highestY = world.getHighestBlockYAt(x, z);
        if (highestY < 5) return null;

        Location result = checkSafeSpot(world, x, highestY, z, random);
        if (result != null && hasSafeArea(world, x, highestY + 1, z)) return result;
        return null;
    }

    private Location checkSafeSpot(World world, int x, int y, int z, Random random) {
        Block surface = world.getBlockAt(x, y, z);
        Block feet = world.getBlockAt(x, y + 1, z);
        Block head = world.getBlockAt(x, y + 2, z);

        if (!surface.getType().isSolid()) return null;
        if (DANGEROUS_SURFACE.contains(surface.getType())) return null;
        if (!feet.isPassable() || DANGEROUS_BODY.contains(feet.getType())) return null;
        if (!head.isPassable() || DANGEROUS_BODY.contains(head.getType())) return null;

        return new Location(world, x + 0.5, y + 1, z + 0.5,
                random.nextFloat() * 360 - 180, 0);
    }

    /**
     * For every column in a diamond of radius {@link #SAFE_RADIUS} around (cx, cz):
     *  - a solid, non-dangerous ground block must exist within ±{@link #Y_TOLERANCE}
     *    of (cy − 1). No ground = a cliff, edge, or void drop.
     *  - no lava / fire / magma / other hazard within the same vertical range.
     *  - no lava / fire in the 2-block body space above the ground.
     * This rules out platform edges (End cities), void slopes, lava pools,
     * cliffs, and nether ledges even when the exact spawn block looks fine.
     */
    private boolean hasSafeArea(World world, int cx, int cy, int cz) {
        int groundY = cy - 1;
        int minY = Math.max(world.getMinHeight(), groundY - Y_TOLERANCE);
        int maxY = Math.min(world.getMaxHeight() - 1, groundY + Y_TOLERANCE);

        for (int dx = -SAFE_RADIUS; dx <= SAFE_RADIUS; dx++) {
            for (int dz = -SAFE_RADIUS; dz <= SAFE_RADIUS; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > SAFE_RADIUS) continue;
                int x = cx + dx;
                int z = cz + dz;

                boolean groundFound = false;
                for (int y = minY; y <= maxY; y++) {
                    Material t = world.getBlockAt(x, y, z).getType();
                    if (isHazard(t)) return false;
                    if (t.isSolid() && !DANGEROUS_SURFACE.contains(t)) groundFound = true;
                }
                if (!groundFound) return false;

                int bodyTop = Math.min(world.getMaxHeight() - 1, cy + 1);
                for (int y = cy; y <= bodyTop; y++) {
                    Material t = world.getBlockAt(x, y, z).getType();
                    if (t == Material.LAVA || t == Material.FIRE || t == Material.SOUL_FIRE) return false;
                }
            }
        }
        return true;
    }

    private boolean isHazard(Material t) {
        return t == Material.LAVA || t == Material.FIRE || t == Material.SOUL_FIRE
                || t == Material.MAGMA_BLOCK;
    }
}
