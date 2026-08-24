package com.starlightuniverse.pvp;

import com.starlightuniverse.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.entity.Projectile;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PvPListener implements Listener {

    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "tpa", "tpahere", "tpaccept", "tpdeny", "tpayes", "tpano",
            "home", "sethome", "delhome", "homes",
            "warp", "warps", "spawn", "back", "rtp",
            "teamhome", "teamtp", "teamsethome",
            "lobby", "hub"
    );

    private final PvPManager manager;
    private final Map<UUID, Long> lastBlockedMessage = new HashMap<>();

    public PvPListener(PvPManager manager) {
        this.manager = manager;
    }

    private void sendBlockedMessage(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastBlockedMessage.get(p.getUniqueId());
        if (last != null && now - last < 1500) return;
        lastBlockedMessage.put(p.getUniqueId(), now);
        Msg.error(p, "You can't use this item while in PVP");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        manager.loadStats(p.getUniqueId(), p.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        manager.handlePlayerQuit(p);
        manager.unloadStats(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PvPMatch match = manager.getMatch(player.getUniqueId());
        if (match == null) return;

        if (match.state != PvPMatch.State.ROUND_ACTIVE) {
            event.setCancelled(true);
            return;
        }

        double newHp = player.getHealth() - event.getFinalDamage();
        if (newHp <= 0.5) {
            event.setCancelled(true);
            player.setHealth(Math.min(20, player.getMaxHealth()));
            manager.onDeathIntercept(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj &&
                proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) return;

        PvPMatch vm = manager.getMatch(victim.getUniqueId());
        PvPMatch am = manager.getMatch(attacker.getUniqueId());

        if (vm != null && am != vm) {
            event.setCancelled(true);
            return;
        }
        if (am != null && vm != am) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player p = event.getEntity();
        PvPMatch match = manager.getMatch(p.getUniqueId());
        if (match == null) return;

        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.deathMessage(null);
        manager.onDeathIntercept(p);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        PvPMatch match = manager.getMatch(p.getUniqueId());
        if (match == null) return;
        Location loc = match.p1.equals(p.getUniqueId()) ? PvPArena.pos1() : PvPArena.pos2();
        if (loc != null) event.setRespawnLocation(loc);
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player p = event.getPlayer();
        if (!manager.isInMatch(p.getUniqueId())) return;

        Material type = event.getItem().getType();
        if (!PvPArena.isAllowedItem(type)) {
            event.setCancelled(true);
            sendBlockedMessage(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
                event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (!manager.isInMatch(p.getUniqueId())) return;

        ItemStack item = event.getItem();
        if (item == null) return;
        Material t = item.getType();
        if (!PvPArena.isAllowedItem(t)) {
            event.setCancelled(true);
            sendBlockedMessage(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player p)) return;
        if (!manager.isInMatch(p.getUniqueId())) return;
        String name = event.getEntity().getType().name();
        if (name.contains("ARROW") || name.contains("TRIDENT")) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        for (var entity : event.getAffectedEntities()) {
            if (entity instanceof Player p && manager.isInMatch(p.getUniqueId())) {
                event.setIntensity(p, 0);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLingering(LingeringPotionSplashEvent event) {
        if (event.getEntity().getShooter() instanceof Player p &&
                manager.isInMatch(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!manager.isInMatch(p.getUniqueId())) return;
        if (event.getFoodLevel() < p.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();

        if (manager.isInternalTeleport(uuid)) return;

        boolean specBlocked = manager.isSpectating(uuid) &&
                event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE;

        boolean matchBlocked = manager.isInMatch(uuid);

        if (!specBlocked && !matchBlocked) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL ||
                cause == PlayerTeleportEvent.TeleportCause.COMMAND ||
                cause == PlayerTeleportEvent.TeleportCause.END_PORTAL ||
                cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL ||
                cause == PlayerTeleportEvent.TeleportCause.END_GATEWAY) {
            event.setCancelled(true);
            Msg.error(p, "Teleporting is blocked while in the arena!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (!manager.isInMatch(p.getUniqueId()) && !manager.isSpectating(p.getUniqueId())) return;

        String msg = event.getMessage();
        if (msg.startsWith("/")) msg = msg.substring(1);
        int space = msg.indexOf(' ');
        String cmd = (space == -1 ? msg : msg.substring(0, space)).toLowerCase();
        if (cmd.contains(":")) {
            cmd = cmd.substring(cmd.indexOf(':') + 1);
        }

        if (cmd.equals("pvp")) return;

        if (BLOCKED_COMMANDS.contains(cmd)) {
            event.setCancelled(true);
            Msg.error(p, "That command is blocked while in the arena!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        if (!manager.isInMatch(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }
}
