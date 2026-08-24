package com.starlightuniverse.boss;

import com.starlightuniverse.util.Msg;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class BossKillListener implements Listener {

    private final BossKillManager manager;

    public BossKillListener(BossKillManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!manager.isBoss(victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null) return;

        double damage = Math.min(event.getFinalDamage(), victim.getHealth());
        manager.handleBossDamage(attacker, victim, damage);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity le)) return;
        if (!manager.isBoss(le)) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        manager.handleBossDeath(le);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        if (!manager.hasActiveBoss()) return;
        if (!manager.getActive().participants.contains(p.getUniqueId())) return;
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
}
