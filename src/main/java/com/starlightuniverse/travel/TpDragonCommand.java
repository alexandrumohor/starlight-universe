package com.starlightuniverse.travel;

import com.starlightuniverse.util.Msg;
import com.starlightuniverse.world.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TpDragonCommand extends Command {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor PURPLE = TextColor.color(0xAA00AA);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);

    private static final ZoneId RO = ZoneId.of("Europe/Bucharest");
    private static final int[] RESET_HOURS = {0, 6, 12, 18};
    private static final DateTimeFormatter RESET_FMT = DateTimeFormatter.ofPattern("HH:mm 'RO'");

    static final int DRAGON_SLOT = 13;

    private final JavaPlugin plugin;

    public TpDragonCommand(JavaPlugin plugin) {
        super("tpdragon");
        setDescription("Open the dragon world portal");
        setUsage("/tpdragon");
        this.plugin = plugin;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        openGui(player);
        return true;
    }

    private void openGui(Player player) {
        TpDragonHolder holder = new TpDragonHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Dragon Portal", PURPLE).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        ItemStack head = new ItemStack(Material.DRAGON_HEAD);
        ItemMeta meta = head.getItemMeta();
        meta.displayName(Component.text("Dragon World", PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        WorldManager wm = com.starlightuniverse.StarlightUniverse.getInstance().getWorldManager();
        DragonStatus status = computeStatus(wm);
        World dragon = WorldManager.findWorld(WorldManager.WORLD_DRAGON);
        int playersHere = dragon == null ? 0 : dragon.getPlayers().size();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("World: " + WorldManager.WORLD_DRAGON, GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Status: ", GRAY)
                .append(Component.text(status.label, status.color, TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Players inside: " + playersHere, GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Next reset: " + nextResetDisplay(), YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("PvP: ON", RED).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        if (status == DragonStatus.RESPAWNING) {
            lore.add(Component.text("Locked — respawning in progress!", RED)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (dragon == null) {
            lore.add(Component.text("World not loaded — try again later.", RED)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Click to teleport", GREEN, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        head.setItemMeta(meta);
        inv.setItem(DRAGON_SLOT, head);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(Component.text("Close", RED).decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(closeMeta);
        inv.setItem(22, close);

        player.openInventory(inv);
    }

    void handleClick(Player player, int slot) {
        if (slot == 22) {
            player.closeInventory();
            return;
        }
        if (slot != DRAGON_SLOT) return;

        WorldManager wm = com.starlightuniverse.StarlightUniverse.getInstance().getWorldManager();
        if (wm != null && wm.isDragonLocked()) {
            Msg.error(player, "The Dragon is respawning! Please wait...");
            return;
        }
        World dragon = WorldManager.findWorld(WorldManager.WORLD_DRAGON);
        if (dragon == null) {
            Msg.error(player, "Dragon world is not loaded!");
            return;
        }
        player.closeInventory();
        teleport(player, dragon);
    }

    private void teleport(Player player, World dragon) {
        Location spawn = dragon.getSpawnLocation();

        for (Entity passenger : new ArrayList<>(player.getPassengers())) {
            if (passenger instanceof TextDisplay) {
                player.removePassenger(passenger);
                passenger.remove();
            }
        }
        if (player.isInsideVehicle()) player.leaveVehicle();

        player.setFallDistance(0);
        player.setVelocity(new Vector(0, 0, 0));
        player.setFireTicks(0);
        player.setNoDamageTicks(40);

        Runnable doTeleport = () -> {
            if (!player.isOnline()) return;
            player.setFallDistance(0);
            player.setVelocity(new Vector(0, 0, 0));
            player.setNoDamageTicks(40);
            boolean success = player.teleport(spawn, PlayerTeleportEvent.TeleportCause.COMMAND);
            if (success) {
                player.setFireTicks(0);
                Msg.success(player, "Welcome to the Dragon World!");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.0f);
            } else {
                Msg.error(player, "Teleport was cancelled!");
            }
        };

        int cx = spawn.getBlockX() >> 4;
        int cz = spawn.getBlockZ() >> 4;
        if (dragon.isChunkLoaded(cx, cz)) {
            doTeleport.run();
        } else {
            dragon.getChunkAtAsync(cx, cz, true)
                    .thenAccept(chunk -> Bukkit.getScheduler().runTask(plugin, doTeleport));
        }
    }

    private DragonStatus computeStatus(WorldManager wm) {
        if (wm != null && wm.isDragonLocked()) return DragonStatus.RESPAWNING;
        World dragon = WorldManager.findWorld(WorldManager.WORLD_DRAGON);
        if (dragon == null) return DragonStatus.RESPAWNING;
        for (Entity e : dragon.getEntities()) {
            if (e instanceof EnderDragon d && !d.isDead() && d.isValid()) {
                return DragonStatus.ALIVE;
            }
        }
        return DragonStatus.KILLED;
    }

    private String nextResetDisplay() {
        ZonedDateTime now = ZonedDateTime.now(RO);
        for (int hour : RESET_HOURS) {
            ZonedDateTime candidate = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
            if (candidate.isAfter(now)) return candidate.format(RESET_FMT);
        }
        return now.plusDays(1).withHour(RESET_HOURS[0]).withMinute(0).withSecond(0).withNano(0)
                .format(RESET_FMT);
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }

    private enum DragonStatus {
        ALIVE("ALIVE", GREEN),
        KILLED("KILLED — awaiting reset", GRAY),
        RESPAWNING("RESPAWNING", RED);

        final String label;
        final TextColor color;

        DragonStatus(String label, TextColor color) {
            this.label = label;
            this.color = color;
        }
    }
}
