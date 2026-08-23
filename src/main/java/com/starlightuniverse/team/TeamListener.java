package com.starlightuniverse.team;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;

public class TeamListener implements Listener {

    private static final Set<Material> LOG_MATERIALS = Set.of(
            Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG,
            Material.DARK_OAK_LOG, Material.JUNGLE_LOG, Material.ACACIA_LOG,
            Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM
    );

    private final JavaPlugin plugin;
    private final TeamManager manager;

    public TeamListener(JavaPlugin plugin, TeamManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.loadPlayer(event.getPlayer());
        Team team = manager.getPlayerTeam(event.getPlayer());
        if (team != null) {
            manager.ensureDailyMissions(team.getId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.unloadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (manager.isInTeamChat(player.getUniqueId())) {
            event.setCancelled(true);
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                    manager.sendTeamChat(player, event.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        UUID attackerUuid = attacker.getUniqueId();
        UUID victimUuid = victim.getUniqueId();

        if (manager.areTeammates(attackerUuid, victimUuid)) {
            Team team = manager.getPlayerTeam(attackerUuid);
            if (team != null && !team.isFriendlyFire()) {
                event.setCancelled(true);
                return;
            }
        }

        if (manager.areAllies(attackerUuid, victimUuid)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();
        UUID killerUuid = killer.getUniqueId();
        Team team = manager.getPlayerTeam(killerUuid);
        if (team == null) return;

        if (event.getEntity() instanceof Player victim) {
            UUID victimUuid = victim.getUniqueId();
            manager.recordPvPKill(killerUuid);

            Integer killerTeamId = getTeamId(killerUuid);
            Integer victimTeamId = getTeamId(victimUuid);
            if (killerTeamId != null && victimTeamId != null && !killerTeamId.equals(victimTeamId)) {
                if (manager.areAtWar(killerTeamId, victimTeamId)) {
                    manager.recordWarKill(killerTeamId, victimTeamId);
                }
            }
            manager.progressMission(team.getId(), "KILL_MOBS", 1);
        } else {
            manager.progressMission(team.getId(), "KILL_MOBS", 1);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Team team = manager.getPlayerTeam(player);
        if (team == null) return;

        Material type = event.getBlock().getType();
        if (LOG_MATERIALS.contains(type)) {
            manager.progressMission(team.getId(), "CHOP_LOGS", 1);
        }
        manager.progressMission(team.getId(), "MINE_BLOCKS", 1);
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Team team = manager.getPlayerTeam(event.getPlayer());
        if (team == null) return;
        manager.progressMission(team.getId(), "FISH", 1);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder() instanceof TeamHolder holder) {
            if (holder.getType() == TeamHolder.Type.TEAM_VAULT) {
                manager.saveVault(holder.getTeamId(), inv);
            }
        }
    }

    private Integer getTeamId(UUID uuid) {
        Team team = manager.getPlayerTeam(uuid);
        return team != null ? team.getId() : null;
    }
}
