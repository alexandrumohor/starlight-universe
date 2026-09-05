package com.starlightuniverse.cosmetic;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

public class CosmeticListener implements Listener {

    private final JavaPlugin plugin;
    private final PetManager petManager;
    private final TrailManager trailManager;
    private final DisguiseManager disguiseManager;

    public CosmeticListener(JavaPlugin plugin, PetManager petManager, TrailManager trailManager, DisguiseManager disguiseManager) {
        this.plugin = plugin;
        this.petManager = petManager;
        this.trailManager = trailManager;
        this.disguiseManager = disguiseManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        petManager.loadPets(player.getUniqueId(), player.getName());
        trailManager.loadTrails(player.getUniqueId(), player.getName());
        disguiseManager.loadDisguises(player.getUniqueId(), player.getName());
        disguiseManager.onPlayerJoin(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        petManager.onPlayerQuit(event.getPlayer().getUniqueId());
        trailManager.onPlayerQuit(event.getPlayer().getUniqueId());
        disguiseManager.onPlayerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof PetHolder petHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getCurrentItem() == null) return;
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            petManager.handleMenuClick(player, event.getSlot(), petHolder.isScrollMode());
            return;
        }

        if (holder instanceof TrailHolder trailHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getCurrentItem() == null) return;
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            trailManager.handleMenuClick(player, event.getSlot(), trailHolder.isScrollMode());
            return;
        }

        if (holder instanceof DisguiseHolder disguiseHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getCurrentItem() == null) return;
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            disguiseManager.handleMenuClick(player, event.getSlot(), disguiseHolder.isScrollMode());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageEvent event) {
        if (petManager.isPetEntity(event.getEntity()) || disguiseManager.isDisguiseEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPetTarget(EntityTargetEvent event) {
        if (petManager.isPetEntity(event.getEntity()) || disguiseManager.isDisguiseEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPetInteract(PlayerInteractEntityEvent event) {
        if (petManager.isPetEntity(event.getRightClicked()) || disguiseManager.isDisguiseEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityHit(EntityDamageByEntityEvent event) {
        if (petManager.isPetEntity(event.getEntity()) || disguiseManager.isDisguiseEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
