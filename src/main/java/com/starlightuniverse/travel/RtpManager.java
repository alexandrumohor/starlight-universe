package com.starlightuniverse.travel;

import com.starlightuniverse.util.Msg;
import com.starlightuniverse.world.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RtpManager {

    public static final int OVERWORLD_RADIUS = 37_500;   // world 75k x 75k
    public static final int NETHER_RADIUS = 25_000;      // world 50k x 50k
    public static final int END_RADIUS = 25_000;         // world 50k x 50k
    public static final int RESOURCE_RADIUS = 5_000;     // resource 10k x 10k

    public static final long COOLDOWN_MS = 3_000L;
    public static final int MAX_ATTEMPTS = 30;

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);

    public enum RtpWorld {
        OVERWORLD(WorldManager.OVERWORLD, "Overworld", "#55FF55", Material.GRASS_BLOCK, OVERWORLD_RADIUS, false),
        NETHER(WorldManager.WORLD_NETHER, "Nether", "#FF5555", Material.NETHERRACK, NETHER_RADIUS, false),
        END(WorldManager.WORLD_THE_END, "The End", "#AA00AA", Material.END_STONE, END_RADIUS, false),
        RESOURCE_OVERWORLD(WorldManager.RESOURCE_OVERWORLD, "Resource Overworld", "#55FFFF", Material.OAK_LOG, RESOURCE_RADIUS, true),
        RESOURCE_NETHER(WorldManager.RESOURCE_NETHER, "Resource Nether", "#FFAA00", Material.NETHER_BRICKS, RESOURCE_RADIUS, true),
        RESOURCE_END(WorldManager.RESOURCE_END, "Resource End", "#AAAAAA", Material.CHORUS_FRUIT, RESOURCE_RADIUS, true);

        public final String worldName;
        public final String display;
        public final TextColor color;
        public final Material icon;
        public final int radius;
        public final boolean resource;

        RtpWorld(String worldName, String display, String hex, Material icon, int radius, boolean resource) {
            this.worldName = worldName;
            this.display = display;
            this.color = TextColor.fromHexString(hex);
            this.icon = icon;
            this.radius = radius;
            this.resource = resource;
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
    private final Set<RtpWorld> lockedWorlds = ConcurrentHashMap.newKeySet();
    private final Random random = new Random();

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

    public void openGui(Player player) {
        RtpHolder holder = new RtpHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Random Teleport", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        RtpWorld[] worlds = RtpWorld.values();
        int[] slots = {10, 12, 14, 20, 21, 23};

        for (int i = 0; i < worlds.length; i++) {
            RtpWorld r = worlds[i];
            World w = Bukkit.getWorld(r.worldName);
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
            lore.add(Component.text("Border: X " + (-r.radius) + " → " + r.radius, GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Border: Z " + (-r.radius) + " → " + r.radius, GRAY).decoration(TextDecoration.ITALIC, false));
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
        inv.setItem(26, close);

        player.openInventory(inv);
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
            case 10 -> RtpWorld.OVERWORLD;
            case 12 -> RtpWorld.NETHER;
            case 14 -> RtpWorld.END;
            case 20 -> RtpWorld.RESOURCE_OVERWORLD;
            case 21 -> RtpWorld.RESOURCE_NETHER;
            case 23 -> RtpWorld.RESOURCE_END;
            default -> null;
        };
    }

    public void teleport(Player player, RtpWorld r) {
        if (isLocked(r)) {
            Msg.error(player, r.display + " is locked!");
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(uuid);
        if (last != null && now - last < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (now - last)) / 1000 + 1;
            Msg.error(player, "Please wait " + remaining + "s before teleporting again!");
            return;
        }

        World world = Bukkit.getWorld(r.worldName);
        if (world == null) {
            Msg.error(player, "World not loaded: " + r.display);
            return;
        }

        cooldowns.put(uuid, now);
        Msg.info(player, "Searching for a safe location in " + r.display + "...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location safe = findSafeLocationAsync(world, r);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (safe == null) {
                    Msg.error(player, "Could not find a safe location. Please try again!");
                    cooldowns.remove(uuid);
                    return;
                }
                player.teleport(safe);
                Msg.success(player, "Teleported to " + r.display + " at (" +
                        safe.getBlockX() + ", " + safe.getBlockY() + ", " + safe.getBlockZ() + ")");
                player.playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            });
        });
    }

    private Location findSafeLocationAsync(World world, RtpWorld r) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int x = random.nextInt(r.radius * 2 + 1) - r.radius;
            int z = random.nextInt(r.radius * 2 + 1) - r.radius;

            Location loc = findSafeYSync(world, x, z, r);
            if (loc != null) return loc;
        }
        return null;
    }

    private Location findSafeYSync(World world, int x, int z, RtpWorld r) {
        try {
            world.getChunkAt(x >> 4, z >> 4).load(true);
        } catch (Exception ignored) {
            return null;
        }

        World.Environment env = world.getEnvironment();
        int startY, endY;

        if (env == World.Environment.NETHER) {
            startY = 100;
            endY = 32;
            for (int y = startY; y > endY; y--) {
                if (isSafeSpot(world, x, y, z)) {
                    return centerLoc(world, x, y, z);
                }
            }
            return null;
        } else if (env == World.Environment.THE_END) {
            int top = world.getHighestBlockYAt(x, z);
            if (top < 0) return null;
            if (isSafeSpot(world, x, top + 1, z)) return centerLoc(world, x, top + 1, z);
            return null;
        } else {
            int top = world.getHighestBlockYAt(x, z);
            if (top < world.getMinHeight() + 5) return null;
            if (isSafeSpot(world, x, top + 1, z)) return centerLoc(world, x, top + 1, z);
            return null;
        }
    }

    private boolean isSafeSpot(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) return false;
        Block below = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);

        Material bt = below.getType();
        if (bt == Material.LAVA || bt == Material.WATER || bt == Material.FIRE
                || bt == Material.MAGMA_BLOCK || bt == Material.CAMPFIRE
                || bt == Material.SOUL_CAMPFIRE || bt == Material.CACTUS
                || bt == Material.SWEET_BERRY_BUSH || bt == Material.POWDER_SNOW) return false;
        if (!below.getType().isSolid()) return false;
        if (!feet.isEmpty() && feet.getType() != Material.CAVE_AIR) return false;
        if (!head.isEmpty() && head.getType() != Material.CAVE_AIR) return false;
        return true;
    }

    private Location centerLoc(World world, int x, int y, int z) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    public void clearCooldown(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
