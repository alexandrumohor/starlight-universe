package com.starlightuniverse.anticheat;

import com.starlightuniverse.auth.AuthManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AntiCheatListener implements Listener {

    private static final double MAX_REACH_SURVIVAL = 6.0;
    private static final int MAX_CPS = 20;
    private static final double MAX_HORIZONTAL_SPEED = 0.90;
    private static final long AIR_TIME_MS_BEFORE_FLY_FLAG = 2500L;
    private static final long BREAK_INTERVAL_MS_MIN = 45L;
    private static final double SCAFFOLD_MIN_FALL_ANGLE = -0.35;

    private final AntiCheatManager manager;
    private final AuthManager authManager;

    private final Map<UUID, Deque<Long>> clickTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBreakTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> airborneSince = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFallDistance = new ConcurrentHashMap<>();

    public AntiCheatListener(AntiCheatManager manager, AuthManager authManager) {
        this.manager = manager;
        this.authManager = authManager;
    }

    private boolean skip(Player player) {
        return player == null
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || !authManager.isAuthenticated(player.getUniqueId())
                || manager.isExempt(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        clickTimes.remove(uuid);
        lastBreakTime.remove(uuid);
        airborneSince.remove(uuid);
        lastFallDistance.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (skip(player)) return;
        if (event.getFrom().getWorld() != event.getTo().getWorld()) return;

        UUID uuid = player.getUniqueId();

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        boolean hasSpeedPot = player.hasPotionEffect(PotionEffectType.SPEED);
        double speedCap = MAX_HORIZONTAL_SPEED + (hasSpeedPot ? 0.30 : 0)
                + (player.isSprinting() ? 0.15 : 0);
        if (player.isGliding() || player.isRiptiding() || player.isInsideVehicle()) speedCap = 5.0;

        if (horizontal > speedCap) {
            manager.flag(player, Violation.SPEED, String.format(Locale.ROOT, "%.2f > %.2f", horizontal, speedCap));
        }

        boolean allowedFly = player.getAllowFlight()
                || player.isGliding()
                || player.isRiptiding()
                || player.isInsideVehicle()
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING);

        boolean airborne = !player.isOnGround()
                && !isNearSolid(player.getLocation(), 0.35)
                && !isInLiquid(player);

        if (!allowedFly && airborne && event.getTo().getY() >= event.getFrom().getY() && horizontal > 0.05) {
            long now = System.currentTimeMillis();
            Long since = airborneSince.get(uuid);
            if (since == null) {
                airborneSince.put(uuid, now);
            } else if (now - since > AIR_TIME_MS_BEFORE_FLY_FLAG) {
                airborneSince.put(uuid, now);
                manager.flag(player, Violation.FLY, "airborne " + (now - since) + "ms");
            }
        } else if (!airborne) {
            airborneSince.remove(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            lastFallDistance.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMoveNoFall(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (skip(player)) return;
        if (player.getFallDistance() >= 4.0f && player.isOnGround() && !player.isGliding()
                && !player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                && player.getHealth() > 0) {
            Long lastFall = lastFallDistance.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (lastFall == null || now - lastFall > 500) {
                manager.flag(player, Violation.NOFALL, "fell " + player.getFallDistance());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (skip(player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        Location eye = player.getEyeLocation();
        Location target = victim.getLocation();
        double distance = eye.distance(target);
        double reach = MAX_REACH_SURVIVAL;
        if (distance > reach) {
            manager.flag(player, Violation.REACH, String.format(Locale.ROOT, "%.2fm", distance));
        }

        Vector look = eye.getDirection().normalize();
        Vector toTarget = target.toVector().subtract(eye.toVector()).normalize();
        double dot = look.dot(toTarget);
        if (dot < -0.3) {
            manager.flag(player, Violation.KILLAURA, String.format(Locale.ROOT, "dot %.2f", dot));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        if (skip(player)) return;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Deque<Long> times = clickTimes.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(now);
            while (!times.isEmpty() && now - times.peekFirst() > 1000L) times.pollFirst();
            if (times.size() > MAX_CPS) {
                manager.flag(player, Violation.AUTOCLICK, times.size() + " CPS");
                times.clear();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (skip(player)) return;
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastBreakTime.put(uuid, now);
        if (last == null) return;
        long delta = now - last;
        Material type = event.getBlock().getType();
        if (delta < BREAK_INTERVAL_MS_MIN && isHardBlock(type)) {
            manager.flag(player, Violation.FASTBREAK, type.name() + " in " + delta + "ms");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (skip(player)) return;
        if (player.isOnGround()) return;
        if (player.getAllowFlight() || player.isGliding() || player.isRiptiding()) return;
        Location eye = player.getEyeLocation();
        double pitchNormalized = -Math.sin(Math.toRadians(eye.getPitch()));
        if (pitchNormalized > SCAFFOLD_MIN_FALL_ANGLE) return;
        Block placed = event.getBlock();
        Location feet = player.getLocation();
        if (placed.getY() < feet.getY() && Math.abs(placed.getX() + 0.5 - feet.getX()) < 1.2
                && Math.abs(placed.getZ() + 0.5 - feet.getZ()) < 1.2) {
            Long airTime = airborneSince.get(player.getUniqueId());
            if (airTime != null && System.currentTimeMillis() - airTime > 400) {
                manager.flag(player, Violation.SCAFFOLD, "airborne " + (System.currentTimeMillis() - airTime) + "ms");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (event.isFlying()) return;
        airborneSince.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    private boolean isNearSolid(Location loc, double margin) {
        for (double dx = -margin; dx <= margin; dx += margin) {
            for (double dz = -margin; dz <= margin; dz += margin) {
                Block below = loc.clone().add(dx, -0.05, dz).getBlock();
                if (below.getType().isSolid()) return true;
            }
        }
        return false;
    }

    private boolean isInLiquid(Player player) {
        Material type = player.getLocation().getBlock().getType();
        return type == Material.WATER || type == Material.LAVA
                || type == Material.BUBBLE_COLUMN || type == Material.SCAFFOLDING
                || type == Material.COBWEB || type == Material.POWDER_SNOW
                || type == Material.LADDER || type == Material.VINE
                || type == Material.TWISTING_VINES || type == Material.WEEPING_VINES;
    }

    private boolean isHardBlock(Material type) {
        return switch (type) {
            case OBSIDIAN, CRYING_OBSIDIAN, ANCIENT_DEBRIS, NETHERITE_BLOCK, END_STONE,
                    BEDROCK, RESPAWN_ANCHOR, ENCHANTING_TABLE, ANVIL, IRON_BLOCK,
                    DIAMOND_BLOCK, GOLD_BLOCK, EMERALD_BLOCK -> true;
            default -> false;
        };
    }
}
