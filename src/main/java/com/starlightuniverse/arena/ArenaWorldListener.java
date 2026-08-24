package com.starlightuniverse.arena;

import com.starlightuniverse.admin.AdminManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaWorldListener implements Listener {

    private static final int STAFF_BYPASS_LEVEL = 3;

    private static final int FLUID_LIFETIME_TICKS = 200;

    private static final TextColor ORANGE = TextColor.color(0xFFAA00);

    private final JavaPlugin plugin;
    private final AdminManager admin;

    private final Map<Location, PlacedFluid> placedFluids = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Material, Integer>> owedBuckets = new ConcurrentHashMap<>();

    public ArenaWorldListener(JavaPlugin plugin, AdminManager admin) {
        this.plugin = plugin;
        this.admin = admin;
    }

    public UUID getFluidPlacer(Location loc) {
        PlacedFluid pf = placedFluids.get(normalize(loc));
        return pf == null ? null : pf.placer;
    }

    private boolean isArena(World world) {
        return world != null && ArenaWorlds.isArenaWorld(world.getName());
    }

    private boolean isMobOrBoss(World world) {
        if (world == null) return false;
        String n = world.getName();
        return n.equals(ArenaWorlds.MOBS_WORLD) || n.equals(ArenaWorlds.BOSS_WORLD);
    }

    private boolean bypass(Player p) {
        return admin.hasPermission(p.getUniqueId(), STAFF_BYPASS_LEVEL);
    }

    private Location normalize(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    // ── Break / place / burn / ignite ──

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (!isArena(p.getWorld())) return;
        if (bypass(p)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        if (!isArena(p.getWorld())) return;
        if (bypass(p)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        if (!isArena(event.getBlock().getWorld())) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!isArena(event.getBlock().getWorld())) return;
        if (event.getPlayer() != null && bypass(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (!isArena(event.getBlock().getWorld())) return;
        event.setCancelled(true);
    }

    // ── Bucket empty (water/lava in mob/boss arenas) ──

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player p = event.getPlayer();
        World w = p.getWorld();
        if (!isArena(w)) return;
        if (bypass(p)) return;

        if (!isMobOrBoss(w)) {
            event.setCancelled(true);
            return;
        }

        Material bucket = event.getBucket();
        if (bucket != Material.WATER_BUCKET && bucket != Material.LAVA_BUCKET) {
            event.setCancelled(true);
            return;
        }

        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        Location key = normalize(target.getLocation());

        cancelExisting(key);

        PlacedFluid pf = new PlacedFluid(p.getUniqueId(), bucket, key,
                Bukkit.getCurrentTick());
        placedFluids.put(key, pf);
        pf.countdownTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> tickCountdown(pf), 20L, 20L);
        pf.expiryTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> expire(pf), FLUID_LIFETIME_TICKS);
    }

    private void cancelExisting(Location key) {
        PlacedFluid old = placedFluids.remove(key);
        if (old != null) {
            if (old.countdownTask != null) old.countdownTask.cancel();
            if (old.expiryTask != null) old.expiryTask.cancel();
        }
    }

    private void tickCountdown(PlacedFluid pf) {
        Player p = Bukkit.getPlayer(pf.placer);
        if (p == null) return;
        long elapsed = Bukkit.getCurrentTick() - pf.placedTick;
        int remaining = (int) Math.max(0, (FLUID_LIFETIME_TICKS - elapsed) / 20);
        if (remaining <= 0) return;

        String fluidName = pf.bucketType == Material.LAVA_BUCKET ? "lava" : "water";
        Component subtitle = Component.text("The " + fluidName +
                " you placed will disappear automatically in " + remaining +
                " second" + (remaining == 1 ? "" : "s"), ORANGE);

        p.showTitle(Title.title(
                Component.empty(),
                subtitle,
                Title.Times.times(Duration.ZERO,
                        Duration.ofMillis(1100),
                        Duration.ofMillis(200))
        ));
    }

    private void expire(PlacedFluid pf) {
        if (pf.scooped) return;
        placedFluids.remove(pf.location);
        if (pf.countdownTask != null) pf.countdownTask.cancel();

        Block b = pf.location.getWorld().getBlockAt(pf.location);
        Material t = b.getType();
        if (t == Material.LAVA || t == Material.WATER
                || t == Material.BUBBLE_COLUMN || t == Material.FIRE) {
            b.setType(Material.AIR, false);
        }

        owedBuckets.computeIfAbsent(pf.placer, k -> new EnumMap<>(Material.class))
                .merge(pf.bucketType, 1, Integer::sum);

        Player p = Bukkit.getPlayer(pf.placer);
        if (p != null && p.isOnline()) {
            String fluidName = pf.bucketType == Material.LAVA_BUCKET ? "lava" : "water";
            p.showTitle(Title.title(
                    Component.empty(),
                    Component.text("Your " + fluidName + " has disappeared. " +
                            "Your bucket will be returned when you leave the arena.",
                            ORANGE),
                    Title.Times.times(Duration.ZERO,
                            Duration.ofSeconds(3),
                            Duration.ofMillis(500))
            ));
        }
    }

    // ── Bucket fill (scoop back a placed fluid) ──

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player p = event.getPlayer();
        World w = p.getWorld();
        if (!isMobOrBoss(w)) return;

        Location key = normalize(event.getBlockClicked().getLocation());
        PlacedFluid pf = placedFluids.remove(key);
        if (pf == null) {
            // No tracked fluid at this spot — nothing natural exists in the arena, but be safe
            return;
        }
        pf.scooped = true;
        if (pf.countdownTask != null) pf.countdownTask.cancel();
        if (pf.expiryTask != null) pf.expiryTask.cancel();
    }

    // ── World change: restore owed buckets on leaving arena ──

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        String fromWorld = event.getFrom().getName();
        if (!ArenaWorlds.isArenaWorld(fromWorld)) return;

        Map<Material, Integer> owed = owedBuckets.remove(p.getUniqueId());
        if (owed == null || owed.isEmpty()) return;

        restoreBuckets(p, owed);
    }

    private void restoreBuckets(Player p, Map<Material, Integer> owed) {
        for (Map.Entry<Material, Integer> e : owed.entrySet()) {
            Material filled = e.getKey();
            int count = e.getValue();
            if (count <= 0) continue;

            var inv = p.getInventory();
            for (int i = 0; i < inv.getSize() && count > 0; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() == Material.BUCKET) {
                    if (item.getAmount() == 1) {
                        inv.setItem(i, new ItemStack(filled));
                        count--;
                    } else {
                        item.setAmount(item.getAmount() - 1);
                        addOrDrop(p, new ItemStack(filled));
                        count--;
                    }
                }
            }
            while (count > 0) {
                addOrDrop(p, new ItemStack(filled));
                count--;
            }
        }

        String parts = summarizeOwed(owed);
        p.sendMessage(Component.text("[SU] ", TextColor.color(0xFFD700))
                .append(Component.text("Your bucket" +
                        (totalOwed(owed) == 1 ? "" : "s") +
                        " have been returned: " + parts, ORANGE)));
    }

    private void addOrDrop(Player p, ItemStack stack) {
        HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(is ->
                    p.getWorld().dropItemNaturally(p.getLocation(), is));
        }
    }

    private String summarizeOwed(Map<Material, Integer> owed) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Material, Integer> e : owed.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            String name = e.getKey() == Material.LAVA_BUCKET ? "lava bucket" : "water bucket";
            sb.append(e.getValue()).append(" ").append(name);
            if (e.getValue() > 1) sb.append("s");
        }
        return sb.toString();
    }

    private int totalOwed(Map<Material, Integer> owed) {
        int t = 0;
        for (Integer v : owed.values()) t += v;
        return t;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        owedBuckets.remove(event.getPlayer().getUniqueId());
    }

    // ── Damage rules in mob/boss arenas ──

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!isMobOrBoss(p.getWorld())) return;

        EntityDamageEvent.DamageCause c = event.getCause();
        if (c == EntityDamageEvent.DamageCause.LAVA
                || c == EntityDamageEvent.DamageCause.FIRE
                || c == EntityDamageEvent.DamageCause.FIRE_TICK
                || c == EntityDamageEvent.DamageCause.HOT_FLOOR
                || c == EntityDamageEvent.DamageCause.DROWNING) {
            event.setCancelled(true);
            p.setFireTicks(0);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPvPDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!isMobOrBoss(victim.getWorld())) return;

        Entity damager = event.getDamager();
        Player attacker = null;
        if (damager instanceof Player p) attacker = p;
        else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p)
            attacker = p;

        if (attacker != null && !attacker.equals(victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEndermanDamage(EntityDamageEvent event) {
        if (!isMobOrBoss(event.getEntity().getWorld())) return;
        if (!(event.getEntity() instanceof Enderman)) return;
        EntityDamageEvent.DamageCause c = event.getCause();
        if (c == EntityDamageEvent.DamageCause.DROWNING
                || c == EntityDamageEvent.DamageCause.SUFFOCATION
                || c == EntityDamageEvent.DamageCause.CUSTOM) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!isArena(event.getEntity().getWorld())) return;
        event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!isArena(event.getBlock().getWorld())) return;
        event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onEndermanPickup(EntityChangeBlockEvent event) {
        if (!isArena(event.getEntity().getWorld())) return;
        if (event.getEntity() instanceof Enderman) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobFluidDamage(EntityDamageEvent event) {
        if (!isMobOrBoss(event.getEntity().getWorld())) return;
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        if (mob instanceof Player) return;

        EntityDamageEvent.DamageCause c = event.getCause();
        if (c != EntityDamageEvent.DamageCause.LAVA
                && c != EntityDamageEvent.DamageCause.FIRE
                && c != EntityDamageEvent.DamageCause.FIRE_TICK) return;

        UUID placer = findFluidPlacer(mob.getLocation(), Material.LAVA);
        if (placer != null) {
            mob.setMetadata("su_lava_placer",
                    new FixedMetadataValue(plugin, placer.toString()));
        }
    }

    private UUID findFluidPlacer(Location loc, Material fluid) {
        World w = loc.getWorld();
        if (w == null) return null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = w.getBlockAt(loc.getBlockX() + dx,
                            loc.getBlockY() + dy, loc.getBlockZ() + dz);
                    if (b.getType() == fluid) {
                        PlacedFluid pf = placedFluids.get(normalize(b.getLocation()));
                        if (pf != null) return pf.placer;
                    }
                }
            }
        }
        return null;
    }

    private static class PlacedFluid {
        final UUID placer;
        final Material bucketType;
        final Location location;
        final long placedTick;
        BukkitTask countdownTask;
        BukkitTask expiryTask;
        boolean scooped = false;

        PlacedFluid(UUID placer, Material bucketType, Location location, long placedTick) {
            this.placer = placer;
            this.bucketType = bucketType;
            this.location = location;
            this.placedTick = placedTick;
        }
    }
}
