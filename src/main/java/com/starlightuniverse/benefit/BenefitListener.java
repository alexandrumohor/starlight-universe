package com.starlightuniverse.benefit;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

public class BenefitListener implements Listener {

    private final JavaPlugin plugin;
    private final BenefitManager mgr;

    public BenefitListener(JavaPlugin plugin, BenefitManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        mgr.loadPlayer(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) event.joinMessage(mgr.buildJoinMessage(player));
        }, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(mgr.buildQuitMessage(player));
        mgr.unloadPlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        String key = mgr.getActiveKillEffect(killer.getUniqueId());
        if (key == null) return;
        KillEffect fx = KillEffect.byKey(key);
        if (fx == null) return;
        fx.play(killer, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        String key = mgr.getActiveKillEffect(killer.getUniqueId());
        if (key == null) return;
        KillEffect fx = KillEffect.byKey(key);
        if (fx == null) return;
        fx.play(killer, victim);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof BenefitCommands.BenefitHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getSlot();
        if (slot == 22) {
            if (holder.getType() == BenefitCommands.BenefitHolder.Type.GLOW) {
                mgr.activateGlow(player, null);
                BenefitCommands.openGlowShop(mgr, player);
            } else {
                mgr.activateKillEffect(player, null);
                BenefitCommands.openKillShop(mgr, player);
            }
            return;
        }
        int idx = slot - 9;
        if (holder.getType() == BenefitCommands.BenefitHolder.Type.GLOW) {
            if (idx < 0 || idx >= BodyGlow.values().length) return;
            BodyGlow g = BodyGlow.values()[idx];
            boolean owned = mgr.hasUnlock(player.getUniqueId(), BenefitManager.CAT_GLOW, g.getKey());
            if (!owned) {
                if (!mgr.purchaseGlow(player, g)) { BenefitCommands.openGlowShop(mgr, player); return; }
            }
            if (g.getKey().equals(mgr.getActiveGlow(player.getUniqueId()))) {
                mgr.activateGlow(player, null);
            } else {
                mgr.activateGlow(player, g);
            }
            BenefitCommands.openGlowShop(mgr, player);
        } else {
            if (idx < 0 || idx >= KillEffect.values().length) return;
            KillEffect fx = KillEffect.values()[idx];
            boolean owned = mgr.hasUnlock(player.getUniqueId(), BenefitManager.CAT_KILL, fx.getKey());
            if (!owned) {
                if (!mgr.purchaseKillEffect(player, fx)) { BenefitCommands.openKillShop(mgr, player); return; }
            }
            if (fx.getKey().equals(mgr.getActiveKillEffect(player.getUniqueId()))) {
                mgr.activateKillEffect(player, null);
            } else {
                mgr.activateKillEffect(player, fx);
            }
            BenefitCommands.openKillShop(mgr, player);
        }
    }
}
