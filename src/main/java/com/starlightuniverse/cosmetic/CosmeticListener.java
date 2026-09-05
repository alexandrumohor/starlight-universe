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

    public CosmeticListener(JavaPlugin plugin, PetManager petManager) {
        this.plugin = plugin;
        this.petManager = petManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        petManager.loadPets(player.getUniqueId(), player.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        petManager.onPlayerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof PetHolder petHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        petManager.handleMenuClick(player, event.getSlot(), petHolder.isScrollMode());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPetDamage(EntityDamageEvent event) {
        if (petManager.isPetEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPetTarget(EntityTargetEvent event) {
        if (petManager.isPetEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPetInteract(PlayerInteractEntityEvent event) {
        if (petManager.isPetEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPetHit(EntityDamageByEntityEvent event) {
        if (petManager.isPetEntity(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
