package com.starlightuniverse.border;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class BorderManager {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final NamespacedKey borderKey;
    private final List<FlyBorder> borders = new CopyOnWriteArrayList<>();

    private final Map<UUID, Location> selectionCornerA = new HashMap<>();

    public BorderManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        this.borderKey = new NamespacedKey(plugin, "border_shovel");
    }

    public void initialize() {
        loadBorders();
    }

    private void loadBorders() {
        db.queryAsync(conn -> {
            List<FlyBorder> loaded = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, world, min_x, min_y, min_z, max_x, max_y, max_z FROM su_borders")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        loaded.add(new FlyBorder(
                                rs.getInt("id"),
                                rs.getString("world"),
                                rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
                                rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z")
                        ));
                    }
                }
            }
            return loaded;
        }).thenAccept(loaded -> {
            if (loaded != null) {
                borders.clear();
                borders.addAll(loaded);
                plugin.getLogger().info("[SU] Loaded " + borders.size() + " fly border(s).");
            }
        });
    }

    public boolean isInBorder(Location loc) {
        String world = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        for (FlyBorder border : borders) {
            if (border.contains(world, x, y, z)) return true;
        }
        return false;
    }

    public List<FlyBorder> getBorders() {
        return Collections.unmodifiableList(borders);
    }

    public boolean isBorderShovel(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SHOVEL) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(borderKey, PersistentDataType.BYTE);
    }

    public ItemStack createBorderShovel() {
        ItemStack shovel = new ItemStack(Material.NETHERITE_SHOVEL);
        ItemMeta meta = shovel.getItemMeta();
        meta.displayName(Component.text("Border Shovel", GOLD, TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("Right-click two corners to", CYAN),
                Component.text("define a 3D fly zone.", CYAN)
        ));
        meta.getPersistentDataContainer().set(borderKey, PersistentDataType.BYTE, (byte) 1);
        shovel.setItemMeta(meta);
        return shovel;
    }

    public boolean hasSelectionA(UUID uuid) {
        return selectionCornerA.containsKey(uuid);
    }

    public void setCornerA(UUID uuid, Location loc) {
        selectionCornerA.put(uuid, loc.clone());
    }

    public Location getCornerA(UUID uuid) {
        return selectionCornerA.get(uuid);
    }

    public void clearSelection(UUID uuid) {
        selectionCornerA.remove(uuid);
    }

    public void createBorder(Player player, Location a, Location b) {
        if (!a.getWorld().getName().equals(b.getWorld().getName())) {
            Msg.error(player, "Both corners must be in the same world!");
            clearSelection(player.getUniqueId());
            return;
        }

        String world = a.getWorld().getName();
        int minX = Math.min(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        int maxX = Math.max(a.getBlockX(), b.getBlockX());
        int maxY = Math.max(a.getBlockY(), b.getBlockY());
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ());

        clearSelection(player.getUniqueId());

        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_borders (world, min_x, min_y, min_z, max_x, max_y, max_z) VALUES (?,?,?,?,?,?,?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, world);
                ps.setInt(2, minX);
                ps.setInt(3, minY);
                ps.setInt(4, minZ);
                ps.setInt(5, maxX);
                ps.setInt(6, maxY);
                ps.setInt(7, maxZ);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
            return -1;
        }).thenAccept(id -> {
            if (id != null && id > 0) {
                FlyBorder border = new FlyBorder(id, world, minX, minY, minZ, maxX, maxY, maxZ);
                borders.add(border);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        Msg.success(player, "Fly border #" + id + " created! "
                                + (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1) + " blocks.");
                    }
                });
            }
        });
    }

    public void deleteBorder(Player player, int id) {
        FlyBorder target = null;
        for (FlyBorder b : borders) {
            if (b.id() == id) { target = b; break; }
        }
        if (target == null) {
            Msg.error(player, "No border with ID #" + id + " found!");
            return;
        }

        FlyBorder toRemove = target;
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM su_borders WHERE id = ?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            return null;
        }).thenRun(() -> {
            borders.remove(toRemove);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    Msg.success(player, "Fly border #" + id + " deleted.");
                }
            });
        });
    }

    public static List<Command> createCommands(BorderManager bm) {
        List<Command> cmds = new ArrayList<>();

        cmds.add(new Command("givebordershovel") {
            { setDescription("Give a border shovel"); setUsage("/givebordershovel <player>"); }
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                if (!sender.isOp()) { if (sender instanceof Player p) Msg.error(p, "Only operators can use this!"); return true; }
                if (args.length != 1) {
                    if (sender instanceof Player p) Msg.error(p, "Usage: /givebordershovel <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    if (sender instanceof Player p) Msg.error(p, "Player not found!");
                    return true;
                }
                target.getInventory().addItem(bm.createBorderShovel());
                if (sender instanceof Player p) Msg.success(p, "Gave border shovel to " + target.getName() + ".");
                Msg.info(target, "You received a Border Shovel! Right-click two corners to define a fly zone.");
                return true;
            }

            @Override
            public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
                if (!sender.isOp()) return List.of();
                if (args.length == 1) {
                    String prefix = args[0].toLowerCase();
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(prefix))
                            .toList();
                }
                return List.of();
            }
        });

        cmds.add(new Command("bordershovel") {
            { setDescription("Manage fly borders"); setUsage("/bordershovel <list|delete <id>>"); }
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                if (!sender.isOp()) { if (sender instanceof Player p) Msg.error(p, "Only operators can use this!"); return true; }
                if (!(sender instanceof Player player)) return true;

                if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                    List<FlyBorder> all = bm.getBorders();
                    if (all.isEmpty()) {
                        Msg.info(player, "No fly borders defined.");
                        return true;
                    }
                    Msg.info(player, "Fly borders (" + all.size() + "):");
                    for (FlyBorder b : all) {
                        Msg.gray(player, "#" + b.id() + " " + b.world()
                                + " [" + b.minX() + "," + b.minY() + "," + b.minZ()
                                + " -> " + b.maxX() + "," + b.maxY() + "," + b.maxZ() + "]");
                    }
                    return true;
                }

                if (args[0].equalsIgnoreCase("delete")) {
                    if (args.length < 2) { Msg.error(player, "Usage: /bordershovel delete <id>"); return true; }
                    int id;
                    try { id = Integer.parseInt(args[1]); } catch (NumberFormatException e) { Msg.error(player, "Invalid ID!"); return true; }
                    bm.deleteBorder(player, id);
                    return true;
                }

                Msg.error(player, "Usage: /bordershovel <list|delete <id>>");
                return true;
            }

            @Override
            public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
                if (!sender.isOp()) return List.of();
                if (args.length == 1) {
                    String prefix = args[0].toLowerCase();
                    return List.of("list", "delete").stream()
                            .filter(s -> s.startsWith(prefix)).toList();
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
                    String prefix = args[1];
                    return bm.getBorders().stream()
                            .map(b -> String.valueOf(b.id()))
                            .filter(s -> s.startsWith(prefix))
                            .toList();
                }
                return List.of();
            }
        });

        return cmds;
    }
}
