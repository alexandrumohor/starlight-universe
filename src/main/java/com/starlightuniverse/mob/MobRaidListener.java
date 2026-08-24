package com.starlightuniverse.mob;

import com.starlightuniverse.arena.ArenaWorlds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MobRaidListener implements Listener {

    private final MobRaidManager manager;

    public MobRaidListener(MobRaidManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (!manager.isRaidMob(mob)) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = mob.getKiller();
        if (killer == null) {
            var damager = event.getDamageSource().getDirectEntity();
            if (damager instanceof Player p) killer = p;
            else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) killer = p;
        }
        manager.handleMobDeath(mob, killer);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        if (!manager.hasActiveRaid()) return;
        if (!manager.getActive().livesLeft.containsKey(p.getUniqueId())) return;
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        manager.handlePlayerDeath(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!manager.isBlacksmith(clicked)) return;
        event.setCancelled(true);
        manager.tryRepairAll(event.getPlayer());
    }

    // Prevent explosions from breaking blocks in mob arena (already handled by ArenaWorldListener too — safety net)
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!event.getEntity().getWorld().getName().equals(ArenaWorlds.MOBS_WORLD)) return;
        event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!event.getBlock().getWorld().getName().equals(ArenaWorlds.MOBS_WORLD)) return;
        event.blockList().clear();
    }

    // Prevent raid mobs from targeting the blacksmith
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof LivingEntity le)) return;
        if (!manager.isRaidMob(le)) return;
        if (event.getTarget() != null && manager.isBlacksmith(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    // Prevent natural mob spawning in mob arena (already disabled by gamerule; extra safety)
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!event.getLocation().getWorld().getName().equals(ArenaWorlds.MOBS_WORLD)) return;
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM ||
                event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND) return;
        event.setCancelled(true);
    }
}
