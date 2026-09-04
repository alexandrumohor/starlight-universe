package com.starlightuniverse.world;

import com.starlightuniverse.StarlightUniverse;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class LobbyManager implements Listener {

    private static final TextColor STAR_COLOR = TextColor.color(0xFFD700);
    private static final Component STAR_NAME = Component.text("★ Survival Teleport", STAR_COLOR)
            .decoration(TextDecoration.ITALIC, false);

    private final JavaPlugin plugin;
    private final QueueManager queueManager;

    public LobbyManager(JavaPlugin plugin, QueueManager queueManager) {
        this.plugin = plugin;
        this.queueManager = queueManager;
    }

    public void giveSurvivalItem(Player player) {
        player.getInventory().clear();
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = star.getItemMeta();
        meta.displayName(STAR_NAME);
        star.setItemMeta(meta);
        player.getInventory().setItem(4, star);
    }

    public static boolean isInLobby(Player player) {
        org.bukkit.World lobby = WorldManager.findWorld(WorldManager.LOBBY);
        return lobby != null && player.getWorld().equals(lobby);
    }

    /**
     * Teleports the player to the lobby world spawn if they aren't already there.
     * Called after successful login/register to guarantee they land in lobby.
     */
    public static void ensureInLobby(Player player) {
        org.bukkit.World lobby = WorldManager.findWorld(WorldManager.LOBBY);
        if (lobby == null) {
            org.bukkit.Bukkit.getLogger().warning("[SU] Cannot teleport " + player.getName()
                    + " to lobby — lobby world is not loaded.");
            return;
        }
        if (!player.getWorld().equals(lobby)) {
            player.teleport(lobby.getSpawnLocation());
        }
        // Keep flight allowed in lobby so the vanilla anti-fly kick doesn't fire
        // while players stand around; premium ranks (Nebula+) use /fly here too.
        player.setAllowFlight(true);
    }

    /**
     * Player names allowed to keep their chosen gamemode inside the lobby /
     * survival-lobby worlds — no forced ADVENTURE, no forced SURVIVAL swap.
     * They can /gamemode creative and place blocks freely.
     */
    private static final java.util.Set<String> GAMEMODE_BYPASS_NAMES = java.util.Set.of(
            "moheur"
    );

    private static boolean isGamemodeBypass(String playerName) {
        return playerName != null
                && GAMEMODE_BYPASS_NAMES.contains(playerName.toLowerCase());
    }

    private boolean isSurvivalItem(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) return false;
        if (!item.hasItemMeta()) return false;
        Component name = item.getItemMeta().displayName();
        return STAR_NAME.equals(name);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!isInLobby(player)) return;
        if (!StarlightUniverse.getInstance().getAuthManager().isAuthenticated(player.getUniqueId())) return;
        if (!isSurvivalItem(event.getItem())) return;

        event.setCancelled(true);
        queueManager.addToQueue(player);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!StarlightUniverse.getInstance().getAuthManager().isAuthenticated(player.getUniqueId())) return;

        String worldName = player.getWorld().getName();

        // Bypass forced gamemode swap for whitelisted staff so they can build/edit
        // in the lobbies without teleporting to a full survival world first.
        boolean bypass = isGamemodeBypass(player.getName());
        if (worldName.equals(WorldManager.LOBBY) || worldName.equals(WorldManager.SURVIVAL_LOBBY)) {
            if (!bypass) player.setGameMode(GameMode.ADVENTURE);
        } else if (WorldManager.getWorldGroup(worldName) == WorldManager.WorldGroup.SURVIVAL) {
            if (!bypass) player.setGameMode(GameMode.SURVIVAL);
        }

        WorldManager.WorldGroup oldGroup = WorldManager.getWorldGroup(event.getFrom());
        WorldManager.WorldGroup newGroup = WorldManager.getWorldGroup(worldName);

        if (oldGroup == newGroup) return;
        if (oldGroup == WorldManager.WorldGroup.UNKNOWN || newGroup == WorldManager.WorldGroup.UNKNOWN) return;

        InventoryManager invManager = StarlightUniverse.getInstance().getInventoryManager();
        invManager.saveInventory(player, oldGroup);

        if (newGroup == WorldManager.WorldGroup.LOBBY) {
            giveSurvivalItem(player);
        } else if (newGroup == WorldManager.WorldGroup.SURVIVAL) {
            player.getInventory().clear();
            invManager.loadInventory(player, newGroup);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        queueManager.removeFromQueue(uuid);

        if (StarlightUniverse.getInstance().getAuthManager().isAuthenticated(uuid)) {
            WorldManager.WorldGroup group = WorldManager.getWorldGroup(player.getWorld());
            if (group != WorldManager.WorldGroup.UNKNOWN) {
                StarlightUniverse.getInstance().getInventoryManager().saveInventory(player, group);
            }
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getLocation().getWorld().getName().equals(WorldManager.LOBBY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        if (event.getWorld().getName().equals(WorldManager.LOBBY) && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDropLobby(PlayerDropItemEvent event) {
        if (isInLobby(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHungerLobby(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isInLobby(player)) {
            event.setCancelled(true);
        }
    }
}
