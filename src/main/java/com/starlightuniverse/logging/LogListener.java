package com.starlightuniverse.logging;

import com.starlightuniverse.auth.AuthManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.TradeSelectEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LogListener implements Listener {

    public static final long MOVEMENT_INTERVAL_TICKS = 100L;

    private final JavaPlugin plugin;
    private final LogManager logs;
    private final AuthManager authManager;
    private final Map<UUID, long[]> lastMovementPos = new ConcurrentHashMap<>();
    private BukkitTask movementTask;

    public LogListener(JavaPlugin plugin, LogManager logs, AuthManager authManager) {
        this.plugin = plugin;
        this.logs = logs;
        this.authManager = authManager;
    }

    public void start() {
        movementTask = Bukkit.getScheduler().runTaskTimer(plugin, this::logMovementTick,
                MOVEMENT_INTERVAL_TICKS, MOVEMENT_INTERVAL_TICKS);
    }

    public void shutdown() {
        if (movementTask != null) movementTask.cancel();
    }

    private void logMovementTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!authManager.isAuthenticated(player.getUniqueId())) continue;
            long[] last = lastMovementPos.get(player.getUniqueId());
            Location loc = player.getLocation();
            long x = (long) loc.getX();
            long y = (long) loc.getY();
            long z = (long) loc.getZ();
            if (last != null && last[0] == x && last[1] == y && last[2] == z) continue;
            lastMovementPos.put(player.getUniqueId(), new long[]{x, y, z});
            logs.log("movement", player, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> data = new LinkedHashMap<>();
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            data.put("ip", player.getAddress().getAddress().getHostAddress());
        }
        logs.log("login", player, data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        logs.log("logout", event.getPlayer(), null);
        lastMovementPos.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("block", event.getBlock().getType().name());
        logs.log("block_break", event.getPlayer(), data);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("block", event.getBlock().getType().name());
        logs.log("block_place", event.getPlayer(), data);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("item", event.getItemDrop().getItemStack().getType().name());
        data.put("amount", event.getItemDrop().getItemStack().getAmount());
        logs.log("item_drop", event.getPlayer(), data);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("item", event.getItem().getItemStack().getType().name());
        data.put("amount", event.getItem().getItemStack().getAmount());
        logs.log("item_pickup", player, data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getHolder() instanceof Container container) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("container", container.getBlock().getType().name());
            Location cl = container.getBlock().getLocation();
            data.put("cx", cl.getBlockX());
            data.put("cy", cl.getBlockY());
            data.put("cz", cl.getBlockZ());
            logs.log("chest_open", player, data);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        logs.log("sneak_start", event.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) return;
        logs.log("sprint_start", event.getPlayer(), null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", PlainTextComponentSerializer.plainText().serialize(event.message()));
        logs.log("chat", event.getPlayer(), data);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        String cmd = event.getMessage();
        if (cmd.toLowerCase().startsWith("/login ") || cmd.toLowerCase().startsWith("/register ")
                || cmd.toLowerCase().startsWith("/changepass ")) {
            String[] parts = cmd.split(" ");
            data.put("command", parts[0] + " ***");
        } else {
            data.put("command", cmd);
        }
        logs.log("command", event.getPlayer(), data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Map<String, Object> data = new LinkedHashMap<>();
        Player killer = player.getKiller();
        if (killer != null) data.put("killer", killer.getName());
        else if (player.getLastDamageCause() != null) data.put("cause", player.getLastDamageCause().getCause().name());
        logs.log("death", player, data);

        if (killer != null) {
            Map<String, Object> killData = new LinkedHashMap<>();
            killData.put("victim", player.getName());
            killData.put("type", "player");
            logs.log("kill", killer, killData);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (event.getEntity() instanceof Player) return;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("victim", event.getEntityType().name());
        data.put("type", "mob");
        logs.log("kill", killer, data);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(TradeSelectEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("index", event.getIndex());
        if (event.getMerchant() != null && event.getMerchant().getRecipe(event.getIndex()) != null) {
            data.put("result", event.getMerchant().getRecipe(event.getIndex()).getResult().getType().name());
        }
        logs.log("trade", player, data);
    }
}
