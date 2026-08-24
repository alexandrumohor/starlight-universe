package com.starlightuniverse.pwarp;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class PWarpListener implements Listener {

    private final JavaPlugin plugin;
    private final PWarpManager pm;
    private final AdminManager adminManager;

    public PWarpListener(JavaPlugin plugin, PWarpManager pm, AdminManager adminManager) {
        this.plugin = plugin;
        this.pm = pm;
        this.adminManager = adminManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        pm.endDescriptionMode(event.getPlayer().getUniqueId());
    }

    // ============================================================
    // Block /sethome inside a personal warp
    // ============================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase();
        if (!(msg.startsWith("/sethome ") || msg.equals("/sethome"))) return;
        Player p = event.getPlayer();
        Location loc = p.getLocation();
        PersonalWarp w = pm.getWarpContaining(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ());
        if (w != null) {
            Msg.error(p, "You cannot set a home inside a personal warp!");
            event.setCancelled(true);
        }
    }

    // ============================================================
    // Auto-protection: break / place / bucket / container / pvp
    // ============================================================

    private boolean bypass(Player p) {
        return adminManager.getAdminLevel(p.getUniqueId()) >= 3;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        PersonalWarp w = pm.getWarpContaining(b.getWorld().getName(), b.getX(), b.getZ());
        if (w == null) return;
        Player p = event.getPlayer();
        if (bypass(p)) return;
        if (w.getOwner().equals(p.getName().toLowerCase())) return;
        if (!w.isAllowBreak()) {
            Msg.error(p, "Block breaking is disabled at this warp!");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block b = event.getBlock();
        PersonalWarp w = pm.getWarpContaining(b.getWorld().getName(), b.getX(), b.getZ());
        if (w == null) return;
        Player p = event.getPlayer();
        if (bypass(p)) return;
        if (w.getOwner().equals(p.getName().toLowerCase())) return;
        if (!w.isAllowPlace()) {
            Msg.error(p, "Block placing is disabled at this warp!");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block b = event.getBlock();
        PersonalWarp w = pm.getWarpContaining(b.getWorld().getName(), b.getX(), b.getZ());
        if (w == null) return;
        Player p = event.getPlayer();
        if (bypass(p) || w.getOwner().equals(p.getName().toLowerCase())) return;
        if (!w.isAllowPlace()) {
            Msg.error(p, "Buckets are disabled at this warp!");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block b = event.getBlock();
        PersonalWarp w = pm.getWarpContaining(b.getWorld().getName(), b.getX(), b.getZ());
        if (w == null) return;
        Player p = event.getPlayer();
        if (bypass(p) || w.getOwner().equals(p.getName().toLowerCase())) return;
        if (!w.isAllowBreak()) {
            Msg.error(p, "Buckets are disabled at this warp!");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                && event.getAction() != org.bukkit.event.block.Action.PHYSICAL) return;
        Block b = event.getClickedBlock();
        if (b == null) return;
        PersonalWarp w = pm.getWarpContaining(b.getWorld().getName(), b.getX(), b.getZ());
        if (w == null) return;
        Player p = event.getPlayer();
        if (bypass(p) || w.getOwner().equals(p.getName().toLowerCase())) return;

        Material t = b.getType();
        boolean isContainer = t == Material.CHEST || t == Material.TRAPPED_CHEST
                || t == Material.BARREL || t == Material.HOPPER || t == Material.DROPPER
                || t == Material.DISPENSER || t == Material.SHULKER_BOX
                || t == Material.FURNACE || t == Material.BLAST_FURNACE
                || t == Material.SMOKER || t == Material.BREWING_STAND
                || t == Material.ANVIL || t == Material.CHIPPED_ANVIL
                || t == Material.DAMAGED_ANVIL || t == Material.LECTERN;

        if (isContainer && !w.isAllowContainers()) {
            Msg.error(p, "Containers are locked at this warp!");
            event.setCancelled(true);
            return;
        }
        if (!isContainer && !w.isAllowInteract()) {
            Msg.error(p, "Interaction is disabled at this warp!");
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player pp) {
            attacker = pp;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player pp) {
            attacker = pp;
        }
        if (attacker == null) return;

        Location vl = victim.getLocation();
        PersonalWarp w = pm.getWarpContaining(vl.getWorld().getName(), vl.getBlockX(), vl.getBlockZ());
        if (w == null) return;
        if (bypass(attacker)) return;
        if (!w.isAllowPvp()) {
            Msg.error(attacker, "PvP is disabled at this warp!");
            event.setCancelled(true);
        }
    }

    // ============================================================
    // GUI click handling
    // ============================================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof PWarpHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        switch (holder.getType()) {
            case BROWSE -> handleBrowse(event, player, holder);
            case MY_WARPS -> handleMyWarps(event, player);
            case SETTINGS -> handleSettings(event, player, holder);
            case CATEGORY_PICK -> handleCategoryPick(event, player, holder);
            case RATE -> handleRate(event, player, holder);
        }
    }

    private PersonalWarp warpFromItem(ItemStack item) {
        Integer id = pm.getWarpIdFrom(item);
        return id == null ? null : pm.getWarpById(id);
    }

    private void handleBrowse(InventoryClickEvent event, Player player, PWarpHolder holder) {
        int slot = event.getRawSlot();

        PersonalWarp w = warpFromItem(event.getCurrentItem());
        if (w != null) {
            if (event.isRightClick()) {
                player.closeInventory();
                pm.openRateGui(player, w);
            } else {
                player.closeInventory();
                pm.teleport(player, w);
            }
            return;
        }

        if (slot == 36) {
            pm.openBrowseGui(player, holder.getPage(), holder.getCategory(), holder.getSort().next());
            return;
        }

        int[] catSlots = {45, 46, 47, 48, 49, 50, 51};
        String[] catAll = new String[]{"All", "Shop", "Farm", "PvP", "Build", "Event", "Other"};
        for (int i = 0; i < catSlots.length; i++) {
            if (catSlots[i] == slot) {
                pm.openBrowseGui(player, 0, catAll[i].equals("All") ? null : catAll[i], holder.getSort());
                return;
            }
        }
        if (slot == 52) pm.openBrowseGui(player, holder.getPage() - 1, holder.getCategory(), holder.getSort());
        if (slot == 53) pm.openBrowseGui(player, holder.getPage() + 1, holder.getCategory(), holder.getSort());
    }

    private void handleMyWarps(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= PWarpManager.MAX_WARPS_PER_PLAYER) return;
        PersonalWarp w = warpFromItem(event.getCurrentItem());
        if (w == null || !w.getOwner().equals(player.getName().toLowerCase())) return;

        if (event.isShiftClick() && event.isRightClick()) {
            pm.deleteWarp(player, w);
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) pm.openMyWarpsGui(player);
            }, 4L);
        } else if (event.isRightClick()) {
            player.closeInventory();
            pm.openSettingsGui(player, w);
        } else {
            player.closeInventory();
            pm.teleport(player, w);
        }
    }

    private void handleSettings(InventoryClickEvent event, Player player, PWarpHolder holder) {
        int slot = event.getRawSlot();
        PersonalWarp w = pm.getWarpById(holder.getPwarpId());
        if (w == null) { player.closeInventory(); return; }

        switch (slot) {
            case 10 -> togglePerm(w, "pvp", player);
            case 11 -> togglePerm(w, "break", player);
            case 12 -> togglePerm(w, "place", player);
            case 13 -> togglePerm(w, "containers", player);
            case 14 -> togglePerm(w, "interact", player);
            case 28 -> { player.closeInventory(); pm.openCategoryPickGui(player, w); return; }
            case 30 -> {
                player.closeInventory();
                Msg.info(player, "Type the new description in chat (min " + PWarpManager.MIN_DESCRIPTION +
                        ", max " + PWarpManager.MAX_DESCRIPTION + " chars). Type 'cancel' to abort.");
                pm.startDescriptionMode(player.getUniqueId(), w.getId());
                return;
            }
            case 32 -> {
                double cost = w.getEntryCost();
                if (event.isRightClick()) {
                    pm.setEntryCost(w, 0);
                    Msg.info(player, "Entry cost set to FREE.");
                } else {
                    double delta = event.isShiftClick() ? 1_000 : 100;
                    pm.setEntryCost(w, cost + delta);
                    Msg.info(player, "Entry cost: $" + EconomyManager.format(w.getEntryCost()));
                }
            }
            case 40 -> {
                player.closeInventory();
                pm.teleport(player, w);
                return;
            }
            case 44 -> {
                if (event.isShiftClick()) {
                    pm.deleteWarp(player, w);
                    player.closeInventory();
                    return;
                }
                Msg.info(player, "Shift-left-click to confirm deletion.");
                return;
            }
            case 36 -> {
                player.closeInventory();
                pm.openMyWarpsGui(player);
                return;
            }
            default -> { return; }
        }
        pm.openSettingsGui(player, w);
    }

    private void togglePerm(PersonalWarp w, String perm, Player player) {
        boolean current = switch (perm) {
            case "pvp" -> w.isAllowPvp();
            case "break" -> w.isAllowBreak();
            case "place" -> w.isAllowPlace();
            case "containers" -> w.isAllowContainers();
            case "interact" -> w.isAllowInteract();
            default -> false;
        };
        pm.setPermission(w, perm, !current);
    }

    private void handleCategoryPick(InventoryClickEvent event, Player player, PWarpHolder holder) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= PWarpManager.CATEGORIES.length) return;
        PersonalWarp w = pm.getWarpById(holder.getPwarpId());
        if (w == null) return;
        String cat = PWarpManager.CATEGORIES[slot];
        pm.setCategory(w, cat);
        Msg.success(player, "Category set to " + cat + ".");
        player.closeInventory();
        pm.openSettingsGui(player, w);
    }

    private void handleRate(InventoryClickEvent event, Player player, PWarpHolder holder) {
        int slot = event.getRawSlot();
        if (slot < 2 || slot > 6) return;
        PersonalWarp w = pm.getWarpById(holder.getPwarpId());
        if (w == null) return;
        pm.rateWarp(player, w, slot - 1);
        player.closeInventory();
    }

    // ============================================================
    // Description input (chat)
    // ============================================================

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (!pm.isInDescriptionMode(p.getUniqueId())) return;
        event.setCancelled(true);

        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        int warpId = pm.getDescriptionWarpId(p.getUniqueId());
        pm.endDescriptionMode(p.getUniqueId());

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            PersonalWarp w = pm.getWarpById(warpId);
            if (w == null) return;
            if (message.equalsIgnoreCase("cancel")) {
                Msg.info(p, "Description update cancelled.");
                pm.openSettingsGui(p, w);
                return;
            }
            if (message.length() < PWarpManager.MIN_DESCRIPTION) {
                Msg.error(p, "Description must be at least " + PWarpManager.MIN_DESCRIPTION + " characters!");
                pm.openSettingsGui(p, w);
                return;
            }
            pm.setDescription(w, message);
            Msg.success(p, "Description updated!");
            pm.openSettingsGui(p, w);
        });
    }
}
