package com.starlightuniverse.home;

import com.starlightuniverse.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.Map;

public class HomeListener implements Listener {

    private final JavaPlugin plugin;
    private final HomeManager homeManager;

    public HomeListener(JavaPlugin plugin, HomeManager homeManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
    }

    // ==================== PLAYER JOIN/QUIT ====================

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        homeManager.loadPlayerHomes(event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        homeManager.cancelSelection(player.getUniqueId());
        homeManager.unloadPlayer(player.getName());
        homeManager.endAddMemberMode(player.getUniqueId());
    }

    // ==================== GOLDEN SHOVEL CLAIM ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.GOLDEN_SHOVEL) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        event.setCancelled(true);

        if (!homeManager.hasCornerA(player.getUniqueId())) {
            homeManager.setCornerA(player, block.getLocation());
        } else {
            homeManager.tryCreateProtection(player, block.getLocation());
        }
    }

    // ==================== BLOCK PROTECTION ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!homeManager.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            Msg.error(event.getPlayer(), "You can't break blocks in this protected area!");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!homeManager.canPlaceInHomeSpawnColumn(event.getPlayer(), event.getBlock().getLocation())) {
            Msg.error(event.getPlayer(), "You cannot place a block over a home spawn!");
            event.setCancelled(true);
            return;
        }
        if (!homeManager.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            Msg.error(event.getPlayer(), "You can't place blocks in this protected area!");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractPhysical(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                && event.getAction() != org.bukkit.event.block.Action.PHYSICAL) return;
        if (event.getItem() != null && event.getItem().getType() == Material.GOLDEN_SHOVEL) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Material type = block.getType();
        boolean isContainer = type == Material.CHEST || type == Material.TRAPPED_CHEST
                || type == Material.BARREL || type == Material.HOPPER
                || type == Material.DROPPER || type == Material.DISPENSER
                || type == Material.SHULKER_BOX || type == Material.FURNACE
                || type == Material.BLAST_FURNACE || type == Material.SMOKER
                || type == Material.BREWING_STAND || type == Material.ANVIL
                || type == Material.CHIPPED_ANVIL || type == Material.DAMAGED_ANVIL
                || type == Material.BEACON || type == Material.LECTERN;

        if (isContainer && !homeManager.canInteract(event.getPlayer(), block.getLocation())) {
            Msg.error(event.getPlayer(), "You can't interact with this in a protected area!");
            event.setCancelled(true);
        }
    }

    // ==================== ANTI-GRIEF: TNT + CREEPER ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            Protection prot = homeManager.getProtectionAt(
                    block.getWorld().getName(), block.getX(), block.getZ());
            if (prot != null) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            Protection prot = homeManager.getProtectionAt(
                    block.getWorld().getName(), block.getX(), block.getZ());
            if (prot != null) {
                it.remove();
            }
        }
    }

    // ==================== ANTI-GRIEF: WATER/LAVA FLOW ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block from = event.getBlock();
        Block to = event.getToBlock();
        String world = to.getWorld().getName();

        Protection protTo = homeManager.getProtectionAt(world, to.getX(), to.getZ());
        if (protTo == null) return;

        Protection protFrom = homeManager.getProtectionAt(from.getWorld().getName(), from.getX(), from.getZ());
        if (protFrom == null || protFrom.getId() != protTo.getId()) {
            event.setCancelled(true);
        }
    }

    // ==================== ANTI-GRIEF: ENDERMAN ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Enderman)) return;
        Block block = event.getBlock();
        Protection prot = homeManager.getProtectionAt(
                block.getWorld().getName(), block.getX(), block.getZ());
        if (prot != null) {
            event.setCancelled(true);
        }
    }

    // ==================== ANTI-GRIEF: FIRE SPREAD ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() != Material.FIRE) return;
        Block target = event.getBlock();
        Protection prot = homeManager.getProtectionAt(
                target.getWorld().getName(), target.getX(), target.getZ());
        if (prot != null) {
            Protection sourceProt = homeManager.getProtectionAt(
                    event.getSource().getWorld().getName(), event.getSource().getX(), event.getSource().getZ());
            if (sourceProt == null || sourceProt.getId() != prot.getId()) {
                event.setCancelled(true);
            }
        }
    }

    // ==================== ANTI-GRIEF: PISTONS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Block piston = event.getBlock();
        String world = piston.getWorld().getName();
        Protection pistonProt = homeManager.getProtectionAt(world, piston.getX(), piston.getZ());

        for (Block moved : event.getBlocks()) {
            Protection movedProt = homeManager.getProtectionAt(world, moved.getX(), moved.getZ());
            if (movedProt != null && (pistonProt == null || pistonProt.getId() != movedProt.getId())) {
                event.setCancelled(true);
                return;
            }
        }

        org.bukkit.block.BlockFace dir = event.getDirection();
        for (Block moved : event.getBlocks()) {
            Block dest = moved.getRelative(dir);
            Protection destProt = homeManager.getProtectionAt(world, dest.getX(), dest.getZ());
            if (destProt != null && (pistonProt == null || pistonProt.getId() != destProt.getId())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Block piston = event.getBlock();
        String world = piston.getWorld().getName();
        Protection pistonProt = homeManager.getProtectionAt(world, piston.getX(), piston.getZ());

        for (Block moved : event.getBlocks()) {
            Protection movedProt = homeManager.getProtectionAt(world, moved.getX(), moved.getZ());
            if (movedProt != null && (pistonProt == null || pistonProt.getId() != movedProt.getId())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ==================== ANTI-GRIEF: PVP IN PROTECTION ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null) return;

        Location victimLoc = victim.getLocation();
        Protection prot = homeManager.getProtectionAt(
                victimLoc.getWorld().getName(), victimLoc.getBlockX(), victimLoc.getBlockZ());
        if (prot != null) {
            Msg.error(attacker, "PvP is disabled in protected areas!");
            event.setCancelled(true);
        }
    }

    // ==================== GOLEM TARGETING ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        if (!homeManager.isProtectionGolem(golem.getUniqueId())) return;

        if (!(event.getTarget() instanceof Player target)) {
            event.setCancelled(true);
            return;
        }

        Integer protId = homeManager.getGolemProtectionId(golem.getUniqueId());
        if (protId == null) { event.setCancelled(true); return; }

        Protection prot = homeManager.getProtectionById(protId);
        if (prot == null) { event.setCancelled(true); return; }

        Location targetLoc = target.getLocation();
        if (!prot.contains(targetLoc.getWorld().getName(), targetLoc.getBlockX(), targetLoc.getBlockZ())) {
            event.setCancelled(true);
            return;
        }

        String lower = target.getName().toLowerCase();
        if (prot.getOwner().equals(lower)) { event.setCancelled(true); return; }

        ProtectionLevel level = homeManager.getMemberLevel(protId, lower);
        if (level.getLevel() >= ProtectionLevel.BUILDER.getLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGolemDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof IronGolem golem)) return;
        if (!homeManager.isProtectionGolem(golem.getUniqueId())) return;
        if (!(event.getEntity() instanceof Player target)) return;

        Integer protId = homeManager.getGolemProtectionId(golem.getUniqueId());
        if (protId == null) return;

        Protection prot = homeManager.getProtectionById(protId);
        if (prot == null) return;

        Location targetLoc = target.getLocation();
        if (!prot.contains(targetLoc.getWorld().getName(), targetLoc.getBlockX(), targetLoc.getBlockZ())) {
            event.setCancelled(true);
        }
    }

    // ==================== GOLEM KILL = KEEP INVENTORY ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage instanceof EntityDamageByEntityEvent edbe) {
            Entity damager = edbe.getDamager();
            if (damager instanceof IronGolem golem && homeManager.isProtectionGolem(golem.getUniqueId())) {
                event.setKeepInventory(true);
                event.getDrops().clear();
                event.setKeepLevel(true);
                event.setDroppedExp(0);
            }
        }
    }

    // ==================== GOLEM DEATH CLEANUP ====================

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        if (homeManager.isProtectionGolem(golem.getUniqueId())) {
            homeManager.removeGolem(golem.getUniqueId());
        }
    }

    // ==================== BUCKET PROTECTION ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        if (!homeManager.canBuild(event.getPlayer(), block.getLocation())) {
            Msg.error(event.getPlayer(), "You can't use buckets in this protected area!");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlock();
        if (!homeManager.canBuild(event.getPlayer(), block.getLocation())) {
            Msg.error(event.getPlayer(), "You can't use buckets in this protected area!");
            event.setCancelled(true);
        }
    }

    // ==================== GUI CLICK HANDLING ====================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HomeHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        switch (holder.getType()) {
            case HOMES_LIST -> handleHomesClick(event, player, holder);
            case HOME_MANAGE -> handleManageClick(event, player, holder);
            case ICON_SELECT -> handleIconSelectClick(event, player, holder);
            case BUY_HOME_SLOT -> handleBuySlotClick(event, player);
            case PROTECT_MAIN -> handleProtectMainClick(event, player, holder);
            case PROTECT_MEMBERS -> handleMembersClick(event, player, holder);
            case PROTECT_EXPAND -> handleExpandClick(event, player, holder);
        }
    }

    private void handleHomesClick(InventoryClickEvent event, Player player, HomeHolder holder) {
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < 9) {
            int homeNum = slot + 1;
            Home home = homeManager.getHome(player.getName(), homeNum);
            if (home != null) {
                if (event.isRightClick()) {
                    player.closeInventory();
                    homeManager.openManageGui(player, homeNum);
                } else {
                    player.closeInventory();
                    homeManager.teleportHome(player, homeNum);
                }
            }
        } else if (slot == 22) {
            player.closeInventory();
            homeManager.openBuySlotGui(player);
        }
    }

    private void handleManageClick(InventoryClickEvent event, Player player, HomeHolder holder) {
        int slot = event.getRawSlot();
        int homeNum = holder.getSelectedHome();
        switch (slot) {
            case 3 -> {
                player.closeInventory();
                homeManager.openIconSelectGui(player, homeNum);
            }
            case 6 -> {
                homeManager.deleteHome(player, homeNum);
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) homeManager.openHomesGui(player);
                }, 5L);
            }
            case 8 -> {
                player.closeInventory();
                homeManager.openHomesGui(player);
            }
        }
    }

    private void handleIconSelectClick(InventoryClickEvent event, Player player, HomeHolder holder) {
        int slot = event.getRawSlot();
        if (slot == 35) {
            player.closeInventory();
            homeManager.openManageGui(player, holder.getSelectedHome());
            return;
        }
        if (slot >= 0 && slot < HomeManager.ICON_OPTIONS.length) {
            String material = HomeManager.ICON_OPTIONS[slot].name();
            homeManager.setHomeIcon(player, holder.getSelectedHome(), material);
            player.closeInventory();
            homeManager.openManageGui(player, holder.getSelectedHome());
        }
    }

    private void handleBuySlotClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();
        switch (slot) {
            case 2 -> {
                homeManager.buyExtraSlot(player, "money");
                player.closeInventory();
            }
            case 6 -> {
                homeManager.buyExtraSlot(player, "gems");
                player.closeInventory();
            }
            case 4 -> player.closeInventory();
        }
    }

    private void handleProtectMainClick(InventoryClickEvent event, Player player, HomeHolder holder) {
        int slot = event.getRawSlot();
        int protId = holder.getProtectionId();
        switch (slot) {
            case 12 -> {
                player.closeInventory();
                homeManager.openMembersGui(player, protId);
            }
            case 14 -> {
                player.closeInventory();
                homeManager.openExpandGui(player, protId);
            }
            case 20 -> {
                Protection prot = homeManager.getProtectionById(protId);
                if (prot != null) {
                    player.closeInventory();
                    homeManager.spawnGolem(player, prot);
                }
            }
            case 22 -> {
                player.closeInventory();
                homeManager.showLogs(player, protId);
            }
            case 24 -> {
                Protection prot = homeManager.getProtectionById(protId);
                if (prot != null) {
                    player.closeInventory();
                    homeManager.showVisualizer(player, prot);
                    Msg.info(player, "Showing protection boundaries for 10 seconds...");
                }
            }
            case 16 -> {
                player.closeInventory();
                homeManager.deleteProtection(player);
            }
        }
    }

    private void handleMembersClick(InventoryClickEvent event, Player player, HomeHolder holder) {
        int slot = event.getRawSlot();
        int protId = holder.getProtectionId();
        int lastRow = (event.getInventory().getSize() / 9) - 1;

        if (slot == lastRow * 9) {
            player.closeInventory();
            Msg.info(player, "Type the player name in chat to add them as a member:");
            homeManager.startAddMemberMode(player.getUniqueId(), protId);
            return;
        }
        if (slot == lastRow * 9 + 8) {
            player.closeInventory();
            homeManager.openProtectGui(player);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PAPER) return;
        if (clicked.getItemMeta() == null || clicked.getItemMeta().displayName() == null) return;

        String memberName = PlainTextComponentSerializer.plainText()
                .serialize(clicked.getItemMeta().displayName());

        if (event.isShiftClick() && event.isRightClick()) {
            homeManager.removeMember(protId, memberName);
            Msg.success(player, "Removed " + memberName + " from protection.");
        } else {
            ProtectionLevel current = homeManager.getMemberLevel(protId, memberName);
            ProtectionLevel next = current.next();
            homeManager.setMember(protId, memberName, next);
            Msg.success(player, memberName + " is now " + next.getDisplay() + ".");
        }
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) homeManager.openMembersGui(player, protId);
        }, 2L);
    }

    private void handleExpandClick(InventoryClickEvent event, Player player, HomeHolder holder) {
        int slot = event.getRawSlot();
        int index = switch (slot) {
            case 10 -> 0;
            case 12 -> 1;
            case 14 -> 2;
            case 16 -> 3;
            default -> -1;
        };
        if (index >= 0) {
            player.closeInventory();
            homeManager.purchaseBlocks(player, index);
        }
        if (slot == 22) {
            player.closeInventory();
            homeManager.openProtectGui(player);
        }
    }

    // ==================== ADD MEMBER CHAT INPUT ====================

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!homeManager.isInAddMemberMode(player.getUniqueId())) return;

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        int protId = homeManager.getAddMemberProtId(player.getUniqueId());
        homeManager.endAddMemberMode(player.getUniqueId());

        Bukkit.getScheduler().runTask(plugin, () -> {
            homeManager.setMember(protId, message.trim(), ProtectionLevel.VISITOR);
            Msg.success(player, "Added " + message.trim() + " as Visitor. Use the Members menu to change their level.");
            homeManager.openMembersGui(player, protId);
        });
    }
}
