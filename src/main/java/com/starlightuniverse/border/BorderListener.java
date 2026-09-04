package com.starlightuniverse.border;

import com.starlightuniverse.premium.PremiumManager;
import com.starlightuniverse.premium.PremiumRank;
import com.starlightuniverse.util.Msg;
import com.starlightuniverse.world.WorldManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;

public class BorderListener implements Listener {

    private final BorderManager borderManager;
    private final PremiumManager premiumManager;

    public BorderListener(BorderManager borderManager, PremiumManager premiumManager) {
        this.borderManager = borderManager;
        this.premiumManager = premiumManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!player.isOp()) return;
        if (!borderManager.isBorderShovel(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);

        Location clicked = event.getClickedBlock().getLocation();

        if (!borderManager.hasSelectionA(player.getUniqueId())) {
            borderManager.setCornerA(player.getUniqueId(), clicked);
            Msg.info(player, "Corner A set at " + clicked.getBlockX() + ", " + clicked.getBlockY() + ", " + clicked.getBlockZ()
                    + ". Right-click the opposite corner.");
        } else {
            Location a = borderManager.getCornerA(player.getUniqueId());
            borderManager.createBorder(player, a, clicked);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!hasMovedBlock(event)) return;

        Player player = event.getPlayer();
        if (!premiumManager.isFlying(player.getUniqueId())) return;

        PremiumRank rank = premiumManager.getPlayerRank(player.getUniqueId());
        if (rank.canFlyProtections()) return;

        if (!rank.canFlyLobby()) return;

        WorldManager.WorldGroup group = WorldManager.getWorldGroup(player.getWorld());
        if (group == WorldManager.WorldGroup.LOBBY) return;

        if (group == WorldManager.WorldGroup.SURVIVAL) {
            if (!borderManager.isInBorder(event.getTo())) {
                premiumManager.disableFly(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!premiumManager.isFlying(player.getUniqueId())) return;

        PremiumRank rank = premiumManager.getPlayerRank(player.getUniqueId());
        if (rank.canFlyProtections()) return;
        if (!rank.canFlyLobby()) return;

        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;

        WorldManager.WorldGroup toGroup = WorldManager.getWorldGroup(to.getWorld());
        if (toGroup == WorldManager.WorldGroup.LOBBY) return;

        if (toGroup == WorldManager.WorldGroup.SURVIVAL) {
            if (!borderManager.isInBorder(to)) {
                org.bukkit.Bukkit.getScheduler().runTaskLater(
                        com.starlightuniverse.StarlightUniverse.getInstance(), () -> {
                            if (player.isOnline() && premiumManager.isFlying(player.getUniqueId())) {
                                premiumManager.disableFly(player);
                            }
                        }, 1L);
            }
        } else {
            premiumManager.disableFly(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        borderManager.clearSelection(event.getPlayer().getUniqueId());
    }

    private boolean hasMovedBlock(PlayerMoveEvent event) {
        return event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
    }
}
