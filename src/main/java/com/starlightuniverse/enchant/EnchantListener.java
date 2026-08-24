package com.starlightuniverse.enchant;

import com.starlightuniverse.auth.AuthManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantListener implements Listener {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);

    private final JavaPlugin plugin;
    private final EnchantManager manager;
    private final AuthManager auth;
    private final EconomyManager economy;

    private final BukkitTask passiveTask;
    private final BukkitTask repairTask;

    private final Map<UUID, Integer> consecutiveHits = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHitTime = new ConcurrentHashMap<>();

    private final Set<Location> processingBlocks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Map<UUID, Integer> sunlightTouchUses = new ConcurrentHashMap<>();

    public EnchantListener(JavaPlugin plugin, EnchantManager manager, AuthManager auth, EconomyManager economy) {
        this.plugin = plugin;
        this.manager = manager;
        this.auth = auth;
        this.economy = economy;

        passiveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPassiveEffects, 20L, 20L);
        repairTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSelfRepair, 1200L, 1200L);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> sunlightTouchUses.clear(), 1200L, 1200L);
    }

    public void shutdown() {
        passiveTask.cancel();
        repairTask.cancel();
    }

    // ==================== PASSIVE TICK (every 1 second) ====================

    private void tickPassiveEffects() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!auth.isAuthenticated(player.getUniqueId())) continue;

            // HELMET passives
            if (manager.getArmorEnchantLevel(player, CustomEnchant.STARGAZER) > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false, false));
            }

            int nebulaSight = manager.getArmorEnchantLevel(player, CustomEnchant.NEBULA_SIGHT);
            if (nebulaSight > 0) {
                int range = nebulaSight * 8;
                for (Entity entity : player.getNearbyEntities(range, range, range)) {
                    if (entity instanceof LivingEntity mob && !(entity instanceof Player)) {
                        mob.setGlowing(true);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> mob.setGlowing(false), 25L);
                    }
                }
            }

            int astralBreath = manager.getArmorEnchantLevel(player, CustomEnchant.ASTRAL_BREATH);
            if (astralBreath > 0 && player.getRemainingAir() < player.getMaximumAir()) {
                int bonus = astralBreath * 20;
                player.setMaximumAir(300 + bonus);
            }

            int solarClarity = manager.getArmorEnchantLevel(player, CustomEnchant.SOLAR_CLARITY);
            if (solarClarity > 0) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.NAUSEA);
                player.removePotionEffect(PotionEffectType.DARKNESS);
            }

            if (manager.getArmorEnchantLevel(player, CustomEnchant.CONSTELLATION) > 0) {
                World world = player.getWorld();
                long time = world.getTime();
                String timeStr;
                if (time < 6000) timeStr = "Morning";
                else if (time < 12000) timeStr = "Noon";
                else if (time < 18000) timeStr = "Evening";
                else timeStr = "Night";
                String weather = world.hasStorm() ? (world.isThundering() ? "Thunder" : "Rain") : "Clear";
                player.sendActionBar(Component.text("☀ " + timeStr + " | " + weather + " ☁", GOLD));
            }

            int stellarNourish = manager.getArmorEnchantLevel(player, CustomEnchant.STELLAR_NOURISH);
            if (stellarNourish > 0 && player.getFoodLevel() < 20) {
                if (ThreadLocalRandom.current().nextInt(5) < stellarNourish) {
                    player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
                }
            }

            int orbitalScan = manager.getArmorEnchantLevel(player, CustomEnchant.ORBITAL_SCAN);
            if (orbitalScan > 0) {
                int range = orbitalScan * 6;
                for (Entity entity : player.getNearbyEntities(range, range, range)) {
                    if (entity instanceof LivingEntity mob && !(entity instanceof Player)) {
                        double hp = mob.getHealth();
                        AttributeInstance maxHpAttr = mob.getAttribute(Attribute.MAX_HEALTH);
                        double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20;
                        String hpStr = String.format("%.0f/%.0f", hp, maxHp);
                        TextColor hpColor = hp > maxHp * 0.5 ? GREEN : (hp > maxHp * 0.25 ? YELLOW : RED);
                        mob.customName(Component.text("❤ " + hpStr, hpColor));
                        mob.setCustomNameVisible(true);
                    }
                }
            }

            // CHESTPLATE passives
            int cometFall = manager.getArmorEnchantLevel(player, CustomEnchant.COMET_FALL);
            if (cometFall > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, true, false, false));
            }

            int supernovaBurst = manager.getArmorEnchantLevel(player, CustomEnchant.SUPERNOVA_BURST);
            if (supernovaBurst > 0 && player.getHealth() <= 6.0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, supernovaBurst > 3 ? 1 : 0, true, false, false));
            }

            int pulsarRegen = manager.getArmorEnchantLevel(player, CustomEnchant.PULSAR_REGEN);
            if (pulsarRegen > 0) {
                AttributeInstance maxHpAttr = player.getAttribute(Attribute.MAX_HEALTH);
                double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20;
                if (player.getHealth() < maxHp) {
                    double regen = pulsarRegen * 0.2;
                    player.setHealth(Math.min(maxHp, player.getHealth() + regen));
                }
            }

            // LEGGINGS passives
            int lightSpeed = manager.getArmorEnchantLevel(player, CustomEnchant.LIGHT_SPEED);
            if (lightSpeed > 0) {
                int amplifier = Math.min(lightSpeed / 3, 2);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, amplifier, true, false, false));
            }

            int lunarLeap = manager.getArmorEnchantLevel(player, CustomEnchant.LUNAR_LEAP);
            if (lunarLeap > 0) {
                int amplifier = Math.min(lunarLeap / 2, 2);
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, amplifier, true, false, false));
            }

            int cosmicStride = manager.getArmorEnchantLevel(player, CustomEnchant.COSMIC_STRIDE);
            if (cosmicStride > 0) {
                player.removePotionEffect(PotionEffectType.SLOWNESS);
            }

            int anchorPoint = manager.getArmorEnchantLevel(player, CustomEnchant.ANCHOR_POINT);
            if (anchorPoint > 0) {
                player.removePotionEffect(PotionEffectType.LEVITATION);
            }

            int lastLight = manager.getArmorEnchantLevel(player, CustomEnchant.LAST_LIGHT);
            if (lastLight > 0 && player.getHealth() <= 4.0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, lastLight - 1, true, false, false));
            }

            // BOOTS passives
            int warpDrive = manager.getArmorEnchantLevel(player, CustomEnchant.WARP_DRIVE);
            if (warpDrive > 0) {
                int amplifier = Math.min(warpDrive / 3, 2);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, amplifier, true, false, false));
            }

            int deepSpace = manager.getArmorEnchantLevel(player, CustomEnchant.DEEP_SPACE);
            if (deepSpace > 0 && player.isInWater()) {
                int amplifier = Math.min(deepSpace / 2, 2);
                player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, amplifier, true, false, false));
            }

            // Trident - Deep Star
            ItemStack mainHand = player.getEquipment().getItemInMainHand();
            int deepStar = manager.getEnchantLevel(mainHand, CustomEnchant.DEEP_STAR);
            if (deepStar > 0 && player.isInWater()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, deepStar * 100, 0, true, false, false));
            }

            // Star Heart attribute
            manager.applyStarHeartAttribute(player);
        }
    }

    // ==================== SELF-REPAIR TICK (every 60 seconds) ====================

    private void tickSelfRepair() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!auth.isAuthenticated(player.getUniqueId())) continue;

            // Boots - Star Mend
            ItemStack boots = player.getEquipment().getBoots();
            if (boots != null) {
                int starMend = manager.getEnchantLevel(boots, CustomEnchant.STAR_MEND_BOOTS);
                if (starMend > 0) repairItem(boots, starMend);
            }

            // Check main hand for tool self-repair
            ItemStack hand = player.getEquipment().getItemInMainHand();
            if (hand.getType() != Material.AIR) {
                int nebulaMendShovel = manager.getEnchantLevel(hand, CustomEnchant.NEBULA_MEND_SHOVEL);
                if (nebulaMendShovel > 0) repairItem(hand, nebulaMendShovel);
                int pulsarMend = manager.getEnchantLevel(hand, CustomEnchant.PULSAR_MEND);
                if (pulsarMend > 0) repairItem(hand, pulsarMend);
            }
        }
    }

    private void repairItem(ItemStack item, int level) {
        if (item.getType().getMaxDurability() <= 0) return;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            int damage = damageable.getDamage();
            if (damage > 0) {
                damageable.setDamage(Math.max(0, damage - level));
                item.setItemMeta(meta);
            }
        }
    }

    // ==================== DAMAGE TAKEN (armor defense enchants) ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!auth.isAuthenticated(player.getUniqueId())) return;

        double damage = event.getDamage();
        EntityDamageEvent.DamageCause cause = event.getCause();

        // Void Dodge - chance to dodge
        int voidDodge = manager.getArmorEnchantLevel(player, CustomEnchant.VOID_DODGE);
        if (voidDodge > 0 && cause != EntityDamageEvent.DamageCause.VOID
                && cause != EntityDamageEvent.DamageCause.STARVATION) {
            if (ThreadLocalRandom.current().nextDouble() < voidDodge * 0.03) {
                event.setCancelled(true);
                player.getWorld().spawnParticle(Particle.POOF, player.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.05);
                return;
            }
        }

        // Dark Matter - flat damage reduction
        int darkMatter = manager.getArmorEnchantLevel(player, CustomEnchant.DARK_MATTER);
        if (darkMatter > 0) {
            damage -= darkMatter * 0.5;
        }

        // Nova Shield - fire/lava reduction
        int novaShield = manager.getArmorEnchantLevel(player, CustomEnchant.NOVA_SHIELD);
        if (novaShield > 0 && (cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA)) {
            damage *= 1.0 - (novaShield * 0.10);
        }

        // Plasma Shield - explosion reduction
        int plasmaShield = manager.getArmorEnchantLevel(player, CustomEnchant.PLASMA_SHIELD);
        if (plasmaShield > 0 && (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
            damage *= 1.0 - (plasmaShield * 0.10);
        }

        // Soft Landing - fall damage reduction
        int softLanding = manager.getArmorEnchantLevel(player, CustomEnchant.SOFT_LANDING);
        if (softLanding > 0 && cause == EntityDamageEvent.DamageCause.FALL) {
            damage *= 1.0 - (softLanding * 0.10);
        }

        // Zero Gravity - negate fall damage for distance
        int zeroGravity = manager.getArmorEnchantLevel(player, CustomEnchant.ZERO_GRAVITY);
        if (zeroGravity > 0 && cause == EntityDamageEvent.DamageCause.FALL) {
            float fallDist = player.getFallDistance();
            float safe = zeroGravity * 2.0f;
            if (fallDist <= safe + 3) {
                event.setCancelled(true);
                return;
            }
        }

        // Antivenom Orbit - reduce poison damage
        int antivenom = manager.getArmorEnchantLevel(player, CustomEnchant.ANTIVENOM_ORBIT);
        if (antivenom > 0 && cause == EntityDamageEvent.DamageCause.POISON) {
            damage *= 1.0 - (antivenom * 0.15);
        }

        // Meteor Guard - reduce crit damage
        int meteorGuard = manager.getArmorEnchantLevel(player, CustomEnchant.METEOR_GUARD);
        if (meteorGuard > 0 && event instanceof EntityDamageByEntityEvent edb) {
            if (edb.getDamager() instanceof Player attacker && attacker.getFallDistance() > 0 && !attacker.isOnGround()) {
                damage *= 1.0 - (meteorGuard * 0.05);
            }
        }

        // Gravity Well - reduce knockback
        int gravityWell = manager.getArmorEnchantLevel(player, CustomEnchant.GRAVITY_WELL);
        if (gravityWell > 0 && event instanceof EntityDamageByEntityEvent) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Vector vel = player.getVelocity();
                double reduction = 1.0 - (gravityWell * 0.08);
                player.setVelocity(vel.multiply(reduction));
            }, 1L);
        }

        // Solar Flare - fire thorns
        if (event instanceof EntityDamageByEntityEvent edb) {
            int solarFlare = manager.getArmorEnchantLevel(player, CustomEnchant.SOLAR_FLARE);
            if (solarFlare > 0 && edb.getDamager() instanceof LivingEntity attacker) {
                attacker.setFireTicks(solarFlare * 40);
            }

            // Dark Matter Skin - resistance after being hit (on mace holder being hit)
            int darkMatterSkin = manager.getArmorEnchantLevel(player, CustomEnchant.DARK_MATTER_SKIN);
            if (darkMatterSkin > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, darkMatterSkin * 20, 0, true, false, true));
            }
        }

        // Meteor Impact - AOE on landing
        if (cause == EntityDamageEvent.DamageCause.FALL) {
            int meteorImpact = manager.getArmorEnchantLevel(player, CustomEnchant.METEOR_IMPACT);
            if (meteorImpact > 0 && player.getFallDistance() > 5) {
                double radius = 2 + meteorImpact * 0.5;
                double aoeDamage = player.getFallDistance() * 0.3 * meteorImpact;
                Location loc = player.getLocation();
                player.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 1, 0.5, 1, 0);
                for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
                    if (nearby instanceof LivingEntity mob && !(nearby instanceof Player)) {
                        mob.damage(aoeDamage, player);
                    }
                }
            }
        }

        damage = Math.max(0, damage);
        event.setDamage(damage);
    }

    // ==================== MELEE ATTACK (sword/axe/mace/trident/spear enchants) ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        Player player;
        if (event.getDamager() instanceof Player p) {
            player = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            player = p;
            handleProjectileHit(event, player, proj);
            return;
        } else {
            return;
        }

        if (!auth.isAuthenticated(player.getUniqueId())) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack weapon = player.getEquipment().getItemInMainHand();
        if (weapon.getType() == Material.AIR) return;

        double damage = event.getDamage();
        UUID pid = player.getUniqueId();

        // --- SWORD enchants ---

        // Star Drain - lifesteal
        int starDrain = manager.getEnchantLevel(weapon, CustomEnchant.STAR_DRAIN);
        if (starDrain > 0) {
            double heal = damage * (starDrain * 0.05);
            AttributeInstance maxHpAttr = player.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20;
            player.setHealth(Math.min(maxHp, player.getHealth() + heal));
        }

        // Cosmic Bleed - DOT
        int cosmicBleed = manager.getEnchantLevel(weapon, CustomEnchant.COSMIC_BLEED);
        if (cosmicBleed > 0) {
            int ticks = cosmicBleed * 2;
            final double dot = 1.0;
            for (int i = 1; i <= ticks; i++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (victim.isValid() && !victim.isDead()) {
                        victim.damage(dot);
                        victim.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, victim.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0);
                    }
                }, i * 20L);
            }
        }

        // Eclipse Strike - bonus when target is low HP
        int eclipseStrike = manager.getEnchantLevel(weapon, CustomEnchant.ECLIPSE_STRIKE);
        if (eclipseStrike > 0) {
            AttributeInstance maxHpAttr = victim.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20;
            if (victim.getHealth() / maxHp <= 0.30) {
                damage *= 1.0 + (eclipseStrike * 0.10);
            }
        }

        // Frost Nova - slowness
        int frostNova = manager.getEnchantLevel(weapon, CustomEnchant.FROST_NOVA);
        if (frostNova > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, frostNova * 30, Math.min(frostNova / 2, 2), true, true, true));
        }

        // Venom Star - poison
        int venomStar = manager.getEnchantLevel(weapon, CustomEnchant.VENOM_STAR);
        if (venomStar > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, venomStar * 30, Math.min(venomStar / 4, 1), true, true, true));
        }

        // Thunderstorm - lightning chance
        int thunderstorm = manager.getEnchantLevel(weapon, CustomEnchant.THUNDERSTORM);
        if (thunderstorm > 0) {
            if (ThreadLocalRandom.current().nextDouble() < thunderstorm * 0.05) {
                victim.getWorld().strikeLightningEffect(victim.getLocation());
                victim.damage(4.0, player);
            }
        }

        // Soul Harvest - bonus XP (handled in EntityDeathEvent)

        // Gravity Disarm - drop weapon
        int gravityDisarm = manager.getEnchantLevel(weapon, CustomEnchant.GRAVITY_DISARM);
        if (gravityDisarm > 0 && victim instanceof Player targetPlayer) {
            if (ThreadLocalRandom.current().nextDouble() < gravityDisarm * 0.02) {
                ItemStack targetWeapon = targetPlayer.getEquipment().getItemInMainHand();
                if (targetWeapon.getType() != Material.AIR) {
                    targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), targetWeapon.clone());
                    targetPlayer.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
                    Msg.info(targetPlayer, "You've been disarmed!");
                }
            }
        }

        // Wither Star - wither
        int witherStar = manager.getEnchantLevel(weapon, CustomEnchant.WITHER_STAR);
        if (witherStar > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherStar * 20, 0, true, true, true));
        }

        // --- AXE enchants ---

        // Cosmic Rage - consecutive hits bonus
        int cosmicRage = manager.getEnchantLevel(weapon, CustomEnchant.COSMIC_RAGE);
        if (cosmicRage > 0) {
            long now = System.currentTimeMillis();
            Long last = lastHitTime.get(pid);
            if (last != null && now - last < 3000) {
                int streak = consecutiveHits.merge(pid, 1, Integer::sum);
                damage *= 1.0 + (streak * cosmicRage * 0.03);
            } else {
                consecutiveHits.put(pid, 1);
            }
            lastHitTime.put(pid, now);
        }

        // Orbit Shred - reduce armor
        int orbitShred = manager.getEnchantLevel(weapon, CustomEnchant.ORBIT_SHRED);
        if (orbitShred > 0 && victim instanceof Player targetPlayer) {
            for (ItemStack armor : targetPlayer.getEquipment().getArmorContents()) {
                if (armor != null && armor.getType().getMaxDurability() > 0) {
                    if (armor.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d) {
                        d.setDamage(d.getDamage() + orbitShred * 2);
                        armor.setItemMeta((ItemMeta) d);
                    }
                }
            }
        }

        // Nova Cleave - AOE
        int novaCleave = manager.getEnchantLevel(weapon, CustomEnchant.NOVA_CLEAVE);
        if (novaCleave > 0) {
            double radius = novaCleave * 0.5 + 1;
            for (Entity nearby : victim.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof LivingEntity mob && mob != player && mob != victim) {
                    mob.damage(damage * 0.4, player);
                }
            }
        }

        // Plasma Drain - heal on combat
        int plasmaDrain = manager.getEnchantLevel(weapon, CustomEnchant.PLASMA_DRAIN);
        if (plasmaDrain > 0) {
            double heal = damage * (plasmaDrain * 0.03);
            AttributeInstance maxHpAttr = player.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20;
            player.setHealth(Math.min(maxHp, player.getHealth() + heal));
        }

        // Stellar Edge - mob head chance (handled in EntityDeathEvent)

        // --- MACE enchants ---

        // Earthquake Star - AOE knockback
        int earthquakeStar = manager.getEnchantLevel(weapon, CustomEnchant.EARTHQUAKE_STAR);
        if (earthquakeStar > 0) {
            double radius = earthquakeStar;
            for (Entity nearby : victim.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof LivingEntity mob && mob != player) {
                    Vector push = mob.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(earthquakeStar * 0.3);
                    push.setY(0.3);
                    mob.setVelocity(push);
                }
            }
        }

        // Neutron Crush - bonus vs armored
        int neutronCrush = manager.getEnchantLevel(weapon, CustomEnchant.NEUTRON_CRUSH);
        if (neutronCrush > 0 && victim instanceof Player targetPlayer) {
            int armorPieces = 0;
            for (ItemStack armor : targetPlayer.getEquipment().getArmorContents()) {
                if (armor != null && armor.getType() != Material.AIR) armorPieces++;
            }
            damage *= 1.0 + (armorPieces * neutronCrush * 0.05);
        }

        // Pulsar Stun
        int pulsarStun = manager.getEnchantLevel(weapon, CustomEnchant.PULSAR_STUN);
        if (pulsarStun > 0) {
            if (ThreadLocalRandom.current().nextDouble() < pulsarStun * 0.04) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2, true, true, true));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, true, true));
            }
        }

        // Graviton Pull
        int gravitonPull = manager.getEnchantLevel(weapon, CustomEnchant.GRAVITON_PULL);
        if (gravitonPull > 0) {
            double radius = gravitonPull * 2;
            for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof LivingEntity mob && mob != player) {
                    Vector pull = player.getLocation().toVector().subtract(mob.getLocation().toVector()).normalize().multiply(0.5);
                    pull.setY(0.2);
                    mob.setVelocity(pull);
                }
            }
        }

        // Solar Breaker - disable shield
        int solarBreaker = manager.getEnchantLevel(weapon, CustomEnchant.SOLAR_BREAKER);
        if (solarBreaker > 0 && victim instanceof Player targetPlayer) {
            if (targetPlayer.isBlocking()) {
                if (ThreadLocalRandom.current().nextDouble() < solarBreaker * 0.15) {
                    targetPlayer.setCooldown(Material.SHIELD, 100);
                    Msg.info(targetPlayer, "Your shield was broken!");
                }
            }
        }

        // Cosmic Momentum - consecutive hits
        int cosmicMomentum = manager.getEnchantLevel(weapon, CustomEnchant.COSMIC_MOMENTUM);
        if (cosmicMomentum > 0) {
            long now = System.currentTimeMillis();
            Long last = lastHitTime.get(pid);
            if (last != null && now - last < 3000) {
                int streak = consecutiveHits.merge(pid, 1, Integer::sum);
                damage *= 1.0 + (streak * cosmicMomentum * 0.01);
            } else {
                consecutiveHits.put(pid, 1);
            }
            lastHitTime.put(pid, now);
        }

        // Meteor Force - extra knockback
        int meteorForce = manager.getEnchantLevel(weapon, CustomEnchant.METEOR_FORCE);
        if (meteorForce > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Vector push = victim.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(meteorForce * 0.20);
                push.setY(0.3);
                victim.setVelocity(victim.getVelocity().add(push));
            }, 1L);
        }

        // Star Crush - bonus based on enemy armor value
        int starCrush = manager.getEnchantLevel(weapon, CustomEnchant.STAR_CRUSH);
        if (starCrush > 0) {
            AttributeInstance armorAttr = victim.getAttribute(Attribute.ARMOR);
            double armor = armorAttr != null ? armorAttr.getValue() : 0;
            damage += armor * starCrush * 0.05;
        }

        // --- TRIDENT enchants ---

        // Poseidon Star - bonus underwater
        int poseidonStar = manager.getEnchantLevel(weapon, CustomEnchant.POSEIDON_STAR);
        if (poseidonStar > 0 && player.isInWater()) {
            damage *= 1.0 + (poseidonStar * 0.10);
        }

        // Tidal Nova - wave knockback
        int tidalNova = manager.getEnchantLevel(weapon, CustomEnchant.TIDAL_NOVA);
        if (tidalNova > 0) {
            double radius = tidalNova * 2;
            for (Entity nearby : victim.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof LivingEntity mob && mob != player) {
                    Vector push = mob.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(tidalNova * 0.4);
                    push.setY(0.4);
                    mob.setVelocity(push);
                }
            }
        }

        // Chain Lightning
        int chainLightning = manager.getEnchantLevel(weapon, CustomEnchant.CHAIN_LIGHTNING);
        if (chainLightning > 0) {
            List<LivingEntity> targets = new ArrayList<>();
            targets.add(victim);
            LivingEntity current = victim;
            for (int i = 0; i < chainLightning; i++) {
                LivingEntity next = null;
                double closest = 8;
                for (Entity e : current.getNearbyEntities(8, 8, 8)) {
                    if (e instanceof LivingEntity mob && mob != player && !targets.contains(mob)) {
                        double dist = mob.getLocation().distance(current.getLocation());
                        if (dist < closest) {
                            closest = dist;
                            next = mob;
                        }
                    }
                }
                if (next == null) break;
                targets.add(next);
                current = next;
            }
            for (int i = 1; i < targets.size(); i++) {
                LivingEntity target = targets.get(i);
                target.getWorld().strikeLightningEffect(target.getLocation());
                target.damage(damage * 0.5, player);
            }
        }

        // Whirlpool Star - vortex
        int whirlpoolStar = manager.getEnchantLevel(weapon, CustomEnchant.WHIRLPOOL_STAR);
        if (whirlpoolStar > 0) {
            double radius = whirlpoolStar * 3;
            for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
                if (nearby instanceof LivingEntity mob && mob != player) {
                    Vector pull = victim.getLocation().toVector().subtract(mob.getLocation().toVector()).normalize().multiply(0.6);
                    pull.setY(0.2);
                    mob.setVelocity(pull);
                }
            }
        }

        // Frozen Comet - slowness + freeze
        int frozenComet = manager.getEnchantLevel(weapon, CustomEnchant.FROZEN_COMET);
        if (frozenComet > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, frozenComet * 20, 1, true, true, true));
            victim.setFreezeTicks(frozenComet * 40);
        }

        // Stellar Tide - heal in water
        int stellarTide = manager.getEnchantLevel(weapon, CustomEnchant.STELLAR_TIDE);
        if (stellarTide > 0 && player.isInWater()) {
            double heal = stellarTide * 0.5;
            AttributeInstance maxHpAttr = player.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 20;
            player.setHealth(Math.min(maxHp, player.getHealth() + heal));
        }

        // Universal Impale - bonus vs all mobs
        int universalImpale = manager.getEnchantLevel(weapon, CustomEnchant.UNIVERSAL_IMPALE);
        if (universalImpale > 0 && !(victim instanceof Player)) {
            damage *= 1.0 + (universalImpale * 0.05);
        }

        // --- SPEAR enchants ---

        // Starlight Pierce - extra base damage
        int starlightPierce = manager.getEnchantLevel(weapon, CustomEnchant.STARLIGHT_PIERCE);
        if (starlightPierce > 0) {
            damage += starlightPierce * 0.5;
        }

        // Barbed Star - bleed DOT
        int barbedStar = manager.getEnchantLevel(weapon, CustomEnchant.BARBED_STAR);
        if (barbedStar > 0) {
            double bleed = barbedStar * 0.5;
            for (int i = 1; i <= 3; i++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (victim.isValid() && !victim.isDead()) {
                        victim.damage(bleed);
                    }
                }, i * 20L);
            }
        }

        // Solar Sweep - line AOE
        int solarSweep = manager.getEnchantLevel(weapon, CustomEnchant.SOLAR_SWEEP);
        if (solarSweep > 0) {
            double radius = solarSweep + 2;
            Vector dir = player.getLocation().getDirection().normalize();
            for (Entity nearby : player.getNearbyEntities(radius, 2, radius)) {
                if (nearby instanceof LivingEntity mob && mob != player && mob != victim) {
                    Vector toMob = mob.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                    if (dir.dot(toMob) > 0.7) {
                        mob.damage(damage * 0.5, player);
                    }
                }
            }
        }

        // Void Skewer - ignore armor
        int voidSkewer = manager.getEnchantLevel(weapon, CustomEnchant.VOID_SKEWER);
        if (voidSkewer > 0) {
            double armorBypass = voidSkewer * 0.10;
            AttributeInstance armorAttr = victim.getAttribute(Attribute.ARMOR);
            if (armorAttr != null) {
                damage += armorAttr.getValue() * armorBypass * 0.5;
            }
        }

        // Sentinel Star - bonus vs approaching mobs
        int sentinelStar = manager.getEnchantLevel(weapon, CustomEnchant.SENTINEL_STAR);
        if (sentinelStar > 0 && victim.getVelocity().dot(player.getLocation().toVector().subtract(victim.getLocation().toVector())) > 0) {
            damage *= 1.0 + (sentinelStar * 0.05);
        }

        // Gravity Trap effect handled in projectile

        event.setDamage(damage);
    }

    // ==================== PROJECTILE HIT (bow/trident enchants) ====================

    private void handleProjectileHit(EntityDamageByEntityEvent event, Player shooter, Projectile proj) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack bow = shooter.getEquipment().getItemInMainHand();
        if (bow.getType() == Material.AIR) return;

        double damage = event.getDamage();

        // Sniper Nova - bonus at long range
        int sniperNova = manager.getEnchantLevel(bow, CustomEnchant.SNIPER_NOVA);
        if (sniperNova > 0) {
            double distance = shooter.getLocation().distance(victim.getLocation());
            if (distance > 30) {
                damage *= 1.0 + (sniperNova * 0.10);
            }
        }

        // Meteor Arrow - explosion
        int meteorArrow = manager.getEnchantLevel(bow, CustomEnchant.METEOR_ARROW);
        if (meteorArrow > 0) {
            float power = meteorArrow * 0.5f;
            victim.getWorld().createExplosion(victim.getLocation(), power, false, false, shooter);
        }

        // Venom Comet - poison
        int venomComet = manager.getEnchantLevel(bow, CustomEnchant.VENOM_COMET);
        if (venomComet > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, venomComet * 40, 0, true, true, true));
        }

        // Thunder Bolt - lightning
        int thunderBolt = manager.getEnchantLevel(bow, CustomEnchant.THUNDER_BOLT);
        if (thunderBolt > 0) {
            if (ThreadLocalRandom.current().nextDouble() < thunderBolt * 0.05) {
                victim.getWorld().strikeLightningEffect(victim.getLocation());
                victim.damage(4.0, shooter);
            }
        }

        // Gravity Trap - slowness + fatigue
        int gravityTrap = manager.getEnchantLevel(bow, CustomEnchant.GRAVITY_TRAP);
        if (gravityTrap > 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, gravityTrap * 20, 1, true, true, true));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, gravityTrap * 20, 0, true, true, true));
        }

        // Orbit Return - arrow recovery
        int orbitReturn = manager.getEnchantLevel(bow, CustomEnchant.ORBIT_RETURN_BOW);
        if (orbitReturn > 0 && proj instanceof Arrow) {
            if (ThreadLocalRandom.current().nextDouble() < orbitReturn * 0.10) {
                shooter.getInventory().addItem(new ItemStack(Material.ARROW));
            }
        }

        event.setDamage(damage);
    }

    // ==================== BOW SHOOT (draw speed, multi-arrow) ====================

    @EventHandler(ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!auth.isAuthenticated(player.getUniqueId())) return;

        ItemStack bow = event.getBow();
        if (bow == null) return;

        // Star Burst - multi arrow
        int starBurst = manager.getEnchantLevel(bow, CustomEnchant.STAR_BURST);
        if (starBurst > 0 && event.getProjectile() instanceof Arrow arrow) {
            for (int i = 0; i < starBurst; i++) {
                Vector direction = arrow.getVelocity().clone();
                double spread = 0.1;
                direction.add(new Vector(
                        (ThreadLocalRandom.current().nextDouble() - 0.5) * spread,
                        (ThreadLocalRandom.current().nextDouble() - 0.5) * spread,
                        (ThreadLocalRandom.current().nextDouble() - 0.5) * spread
                ));
                Arrow extra = player.launchProjectile(Arrow.class, direction);
                extra.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
                extra.setDamage(arrow.getDamage() * 0.7);
                Bukkit.getScheduler().runTaskLater(plugin, extra::remove, 100L);
            }
        }

        // Solar Draw handled via attribute modifier or velocity boost
        int solarDraw = manager.getEnchantLevel(bow, CustomEnchant.SOLAR_DRAW);
        if (solarDraw > 0 && event.getProjectile() instanceof Arrow arrow) {
            arrow.setVelocity(arrow.getVelocity().multiply(1.0 + solarDraw * 0.08));
        }

        // Piercing Light - pierce through entities
        int piercingLight = manager.getEnchantLevel(bow, CustomEnchant.PIERCING_LIGHT);
        if (piercingLight > 0 && event.getProjectile() instanceof Arrow arrow) {
            arrow.setPierceLevel(piercingLight);
        }

        // Homing Star - tracking (simplified: faster projectile toward nearest entity)
        int homingStar = manager.getEnchantLevel(bow, CustomEnchant.HOMING_STAR);
        if (homingStar > 0 && event.getProjectile() instanceof Arrow arrow) {
            double trackAngle = homingStar * 10;
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                if (!arrow.isValid() || arrow.isDead() || arrow.isOnGround()) {
                    task.cancel();
                    return;
                }
                LivingEntity closest = null;
                double closestDist = 32;
                for (Entity e : arrow.getNearbyEntities(32, 32, 32)) {
                    if (e instanceof LivingEntity mob && mob != player) {
                        double dist = mob.getLocation().distance(arrow.getLocation());
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = mob;
                        }
                    }
                }
                if (closest != null) {
                    Vector toTarget = closest.getLocation().add(0, 1, 0).toVector().subtract(arrow.getLocation().toVector()).normalize();
                    Vector current = arrow.getVelocity().normalize();
                    double speed = arrow.getVelocity().length();
                    double factor = Math.toRadians(trackAngle) / 20.0;
                    Vector newDir = current.add(toTarget.subtract(current).multiply(factor)).normalize().multiply(speed);
                    arrow.setVelocity(newDir);
                }
            }, 2L, 2L);
        }
    }

    // ==================== ENTITY DEATH (XP, head drops, kill effects) ====================

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || !auth.isAuthenticated(killer.getUniqueId())) return;

        ItemStack weapon = killer.getEquipment().getItemInMainHand();

        // Celestial Mind - double XP chance
        int celestialMind = manager.getArmorEnchantLevel(killer, CustomEnchant.CELESTIAL_MIND);
        if (celestialMind > 0) {
            if (ThreadLocalRandom.current().nextDouble() < celestialMind * 0.05) {
                event.setDroppedExp(event.getDroppedExp() * 2);
            }
        }

        // Cosmic Wisdom - bonus XP
        int cosmicWisdom = manager.getArmorEnchantLevel(killer, CustomEnchant.COSMIC_WISDOM);
        if (cosmicWisdom > 0) {
            event.setDroppedExp((int) (event.getDroppedExp() * (1.0 + cosmicWisdom * 0.05)));
        }

        // Soul Harvest - bonus XP on kill
        int soulHarvest = manager.getEnchantLevel(weapon, CustomEnchant.SOUL_HARVEST);
        if (soulHarvest > 0) {
            event.setDroppedExp((int) (event.getDroppedExp() * (1.0 + soulHarvest * 0.10)));
        }

        // Constellation Cut - mob head drop
        int constellationCut = manager.getEnchantLevel(weapon, CustomEnchant.CONSTELLATION_CUT);
        if (constellationCut > 0) {
            if (ThreadLocalRandom.current().nextDouble() < constellationCut * 0.02) {
                Material headMat = getMobHead(entity.getType());
                if (headMat != null) {
                    entity.getWorld().dropItemNaturally(entity.getLocation(), new ItemStack(headMat));
                }
            }
        }

        // Stellar Edge - mob head drop (axe)
        int stellarEdge = manager.getEnchantLevel(weapon, CustomEnchant.STELLAR_EDGE);
        if (stellarEdge > 0) {
            if (ThreadLocalRandom.current().nextDouble() < stellarEdge * 0.04) {
                Material headMat = getMobHead(entity.getType());
                if (headMat != null) {
                    entity.getWorld().dropItemNaturally(entity.getLocation(), new ItemStack(headMat));
                }
            }
        }

        // Supernova Cry - Strength on kill
        int supernovaCry = manager.getEnchantLevel(weapon, CustomEnchant.SUPERNOVA_CRY);
        if (supernovaCry > 0) {
            killer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, supernovaCry * 40, 0, true, true, true));
        }
    }

    private Material getMobHead(EntityType type) {
        return switch (type) {
            case ZOMBIE -> Material.ZOMBIE_HEAD;
            case SKELETON -> Material.SKELETON_SKULL;
            case CREEPER -> Material.CREEPER_HEAD;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case PIGLIN -> Material.PIGLIN_HEAD;
            default -> null;
        };
    }

    // ==================== BLOCK BREAK (mining/tool enchants) ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!auth.isAuthenticated(player.getUniqueId())) return;

        Block block = event.getBlock();
        ItemStack tool = player.getEquipment().getItemInMainHand();
        if (tool.getType() == Material.AIR) return;

        Location bLoc = block.getLocation();
        if (processingBlocks.contains(bLoc)) return;

        // --- PICKAXE enchants ---

        // Solar Smelt - auto smelt
        int solarSmelt = manager.getEnchantLevel(tool, CustomEnchant.SOLAR_SMELT);
        if (solarSmelt > 0) {
            Material smelted = getSmeltedResult(block.getType());
            if (smelted != null) {
                event.setDropItems(false);
                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(smelted));
            }
        }

        // Black Hole / Void Pocket - drops to inventory
        boolean directInventory = manager.getEnchantLevel(tool, CustomEnchant.BLACK_HOLE) > 0
                || manager.getEnchantLevel(tool, CustomEnchant.VOID_POCKET) > 0;

        if (directInventory) {
            event.setDropItems(false);
            for (ItemStack drop : block.getDrops(tool)) {
                var remaining = player.getInventory().addItem(drop);
                for (ItemStack leftover : remaining.values()) {
                    block.getWorld().dropItemNaturally(block.getLocation(), leftover);
                }
            }
        }

        // Cosmic Fortune - bonus XP from ores
        int cosmicFortune = manager.getEnchantLevel(tool, CustomEnchant.COSMIC_FORTUNE);
        if (cosmicFortune > 0 && isOre(block.getType())) {
            event.setExpToDrop((int) (event.getExpToDrop() + cosmicFortune * 2));
        }

        // Asteroid Haste
        int asteroidHaste = manager.getEnchantLevel(tool, CustomEnchant.ASTEROID_HASTE);
        if (asteroidHaste > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, Math.min(asteroidHaste - 1, 4), true, false, false));
        }

        // Star Vein - vein mine connected blocks
        int starVein = manager.getEnchantLevel(tool, CustomEnchant.STAR_VEIN);
        if (starVein > 0 && isVeinMineable(block.getType())) {
            int maxBlocks = starVein;
            veinMine(player, block, block.getType(), maxBlocks, tool);
        }

        // Supernova Mine - area mine
        int supernovaMine = manager.getEnchantLevel(tool, CustomEnchant.SUPERNOVA_MINE);
        if (supernovaMine > 0) {
            int radius = supernovaMine >= 2 ? 2 : 1;
            areaMine(player, block, radius, tool);
        }

        // Photon Beam - line mine
        int photonBeam = manager.getEnchantLevel(tool, CustomEnchant.PHOTON_BEAM);
        if (photonBeam > 0) {
            lineMine(player, block, photonBeam, tool);
        }

        // Magnetar / Magnetar Pull - attract drops
        int magnetar = manager.getEnchantLevel(tool, CustomEnchant.MAGNETAR);
        int magnetarPull = manager.getEnchantLevel(tool, CustomEnchant.MAGNETAR_PULL);
        int magnetRange = Math.max(magnetar, magnetarPull) * 3;
        if (magnetRange > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Entity e : player.getNearbyEntities(magnetRange, magnetRange, magnetRange)) {
                    if (e instanceof Item item) {
                        e.teleport(player.getLocation());
                    }
                }
            }, 5L);
        }

        // Stardust Finder - bonus gems
        int stardustFinder = manager.getEnchantLevel(tool, CustomEnchant.STARDUST_FINDER);
        if (stardustFinder > 0 && isOre(block.getType())) {
            if (ThreadLocalRandom.current().nextDouble() < stardustFinder * 0.03) {
                economy.addGems(player.getUniqueId(), 1);
                player.sendActionBar(Component.text("+1 ◆", CYAN));
            }
        }

        // Energy Core - XP instead of durability
        int energyCore = manager.getEnchantLevel(tool, CustomEnchant.ENERGY_CORE);
        if (energyCore > 0) {
            if (player.getTotalExperience() >= 5) {
                player.giveExp(-5);
                if (tool.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d) {
                    if (d.getDamage() > 0) {
                        d.setDamage(d.getDamage() - 1);
                        tool.setItemMeta((ItemMeta) d);
                    }
                }
            }
        }

        // --- AXE enchants ---

        // Meteor Timber - fell multiple logs
        int meteorTimber = manager.getEnchantLevel(tool, CustomEnchant.METEOR_TIMBER);
        if (meteorTimber > 0 && isLog(block.getType())) {
            int maxLogs = meteorTimber * 5;
            fellTree(player, block, block.getType(), maxLogs, tool);
        }

        // Solar Chop - speed
        int solarChop = manager.getEnchantLevel(tool, CustomEnchant.SOLAR_CHOP);
        if (solarChop > 0 && isLog(block.getType())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, solarChop * 40, 0, true, false, false));
        }

        // Star Seed - auto-plant sapling
        int starSeed = manager.getEnchantLevel(tool, CustomEnchant.STAR_SEED);
        if (starSeed > 0 && isLog(block.getType())) {
            Material sapling = getSapling(block.getType());
            if (sapling != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Block below = block.getRelative(BlockFace.DOWN);
                    if (below.getType() == Material.DIRT || below.getType() == Material.GRASS_BLOCK
                            || below.getType() == Material.PODZOL || below.getType() == Material.COARSE_DIRT) {
                        if (block.getType() == Material.AIR) {
                            block.setType(sapling);
                        }
                    }
                }, 2L);
            }
        }

        // Galaxy Harvest - bonus drops from leaves
        int galaxyHarvest = manager.getEnchantLevel(tool, CustomEnchant.GALAXY_HARVEST);
        if (galaxyHarvest > 0 && isLeaves(block.getType())) {
            if (ThreadLocalRandom.current().nextDouble() < galaxyHarvest * 0.10) {
                for (ItemStack drop : block.getDrops(tool)) {
                    block.getWorld().dropItemNaturally(block.getLocation(), drop);
                }
            }
        }

        // --- SHOVEL enchants ---

        // Solar Touch - transform blocks
        int solarTouch = manager.getEnchantLevel(tool, CustomEnchant.SOLAR_TOUCH);
        if (solarTouch > 0) {
            Material transformed = getTransformed(block.getType());
            if (transformed != null) {
                event.setDropItems(false);
                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(transformed));
            }
        }

        // Crater Dig - area dig
        int craterDig = manager.getEnchantLevel(tool, CustomEnchant.CRATER_DIG);
        if (craterDig > 0 && isShovelBlock(block.getType())) {
            int radius = Math.min(craterDig, 3);
            areaMine(player, block, radius, tool);
        }

        // Photon Tunnel - line dig
        int photonTunnel = manager.getEnchantLevel(tool, CustomEnchant.PHOTON_TUNNEL);
        if (photonTunnel > 0 && isShovelBlock(block.getType())) {
            lineMine(player, block, photonTunnel, tool);
        }

        // Star Hunter - rare loot chance
        int starHunter = manager.getEnchantLevel(tool, CustomEnchant.STAR_HUNTER);
        if (starHunter > 0 && isShovelBlock(block.getType())) {
            if (ThreadLocalRandom.current().nextDouble() < starHunter * 0.01) {
                Material[] rareLoot = {Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT};
                Material loot = rareLoot[ThreadLocalRandom.current().nextInt(rareLoot.length)];
                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(loot));
                player.sendActionBar(Component.text("★ Rare find!", GOLD));
            }
        }

        // Warp Dig - speed while digging
        int warpDig = manager.getEnchantLevel(tool, CustomEnchant.WARP_DIG);
        if (warpDig > 0 && isShovelBlock(block.getType())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, warpDig * 20, 0, true, false, false));
        }

        // Dust Storm - water source chance
        int dustStorm = manager.getEnchantLevel(tool, CustomEnchant.DUST_STORM);
        if (dustStorm > 0 && isShovelBlock(block.getType())) {
            if (ThreadLocalRandom.current().nextDouble() < dustStorm * 0.05) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> block.setType(Material.WATER), 1L);
            }
        }

        // Gravity Flatten - level terrain
        int gravityFlatten = manager.getEnchantLevel(tool, CustomEnchant.GRAVITY_FLATTEN);
        if (gravityFlatten > 0 && isShovelBlock(block.getType())) {
            int radius = gravityFlatten * 2;
            int y = block.getY();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Block target = block.getWorld().getBlockAt(block.getX() + dx, y, block.getZ() + dz);
                    if (isShovelBlock(target.getType()) && target.getRelative(BlockFace.UP).getType() == Material.AIR) {
                        Location tLoc = target.getLocation();
                        if (!processingBlocks.add(tLoc)) continue;
                        target.breakNaturally(tool);
                        processingBlocks.remove(tLoc);
                    }
                }
            }
        }

        // --- HOE enchants ---

        // Cosmic Seeds - bonus seed drops
        int cosmicSeeds = manager.getEnchantLevel(tool, CustomEnchant.COSMIC_SEEDS);
        if (cosmicSeeds > 0 && isCrop(block.getType())) {
            if (ThreadLocalRandom.current().nextDouble() < cosmicSeeds * 0.05) {
                Material seed = getCropSeed(block.getType());
                if (seed != null) {
                    block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(seed, 1));
                }
            }
        }

        // Stellar Yield - bonus crop yield
        int stellarYield = manager.getEnchantLevel(tool, CustomEnchant.STELLAR_YIELD);
        if (stellarYield > 0 && isCrop(block.getType())) {
            if (block.getBlockData() instanceof Ageable ageable && ageable.getAge() == ageable.getMaximumAge()) {
                if (ThreadLocalRandom.current().nextDouble() < stellarYield * 0.10) {
                    for (ItemStack drop : block.getDrops(tool)) {
                        block.getWorld().dropItemNaturally(block.getLocation(), drop);
                    }
                }
            }
        }

        // Galaxy Bloom - bonus flower drops
        int galaxyBloom = manager.getEnchantLevel(tool, CustomEnchant.GALAXY_BLOOM);
        if (galaxyBloom > 0 && isFlower(block.getType())) {
            if (ThreadLocalRandom.current().nextDouble() < galaxyBloom * 0.10) {
                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(block.getType()));
            }
        }

        // Star Harvest - auto-harvest and replant
        int starHarvest = manager.getEnchantLevel(tool, CustomEnchant.STAR_HARVEST);
        if (starHarvest > 0 && isCrop(block.getType())) {
            int radius = starHarvest;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Block target = block.getWorld().getBlockAt(block.getX() + dx, block.getY(), block.getZ() + dz);
                    if (isCrop(target.getType()) && target.getBlockData() instanceof Ageable ageable
                            && ageable.getAge() == ageable.getMaximumAge()) {
                        Location tLoc = target.getLocation();
                        if (!processingBlocks.add(tLoc)) continue;
                        Material cropType = target.getType();
                        for (ItemStack drop : target.getDrops(tool)) {
                            target.getWorld().dropItemNaturally(target.getLocation(), drop);
                        }
                        target.setType(cropType);
                        processingBlocks.remove(tLoc);
                    }
                }
            }
        }
    }

    // ==================== PLAYER INTERACT (hoe enchants, magma walk) ====================

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!auth.isAuthenticated(player.getUniqueId())) return;

        ItemStack tool = player.getEquipment().getItemInMainHand();

        // Sunlight Touch - bonemeal on right-click with hoe
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            int sunlightTouch = manager.getEnchantLevel(tool, CustomEnchant.SUNLIGHT_TOUCH);
            if (sunlightTouch > 0 && isCrop(event.getClickedBlock().getType())) {
                UUID pid = player.getUniqueId();
                int uses = sunlightTouchUses.getOrDefault(pid, 0);
                int maxUses = sunlightTouch;
                if (uses < maxUses) {
                    sunlightTouchUses.put(pid, uses + 1);
                    Block crop = event.getClickedBlock();
                    if (crop.getBlockData() instanceof Ageable ageable) {
                        int newAge = Math.min(ageable.getAge() + 1, ageable.getMaximumAge());
                        ageable.setAge(newAge);
                        crop.setBlockData(ageable);
                        crop.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, crop.getLocation().add(0.5, 0.5, 0.5), 5, 0.3, 0.3, 0.3, 0);
                    }
                }
            }

            // Orbit Path - create paths in radius
            int orbitPath = manager.getEnchantLevel(tool, CustomEnchant.ORBIT_PATH);
            if (orbitPath > 0 && event.getClickedBlock() != null) {
                Block clicked = event.getClickedBlock();
                if (clicked.getType() == Material.GRASS_BLOCK || clicked.getType() == Material.DIRT) {
                    int radius = orbitPath;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            Block target = clicked.getWorld().getBlockAt(clicked.getX() + dx, clicked.getY(), clicked.getZ() + dz);
                            if ((target.getType() == Material.GRASS_BLOCK || target.getType() == Material.DIRT)
                                    && target.getRelative(BlockFace.UP).getType() == Material.AIR) {
                                target.setType(Material.DIRT_PATH);
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== FARMLAND TRAMPLE (Solar Step, Star Fence) ====================

    @EventHandler(ignoreCancelled = true)
    public void onFarmlandTrample(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.FARMLAND) return;

        Player player = event.getPlayer();
        if (!auth.isAuthenticated(player.getUniqueId())) return;

        int solarStep = manager.getArmorEnchantLevel(player, CustomEnchant.SOLAR_STEP);
        if (solarStep > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityFarmlandTrample(EntityInteractEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) return;
        if (!(event.getEntity() instanceof Player)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!auth.isAuthenticated(player.getUniqueId())) continue;
                ItemStack hoe = player.getEquipment().getItemInMainHand();
                int starFence = manager.getEnchantLevel(hoe, CustomEnchant.STAR_FENCE);
                if (starFence > 0) {
                    double range = starFence * 3;
                    if (event.getBlock().getLocation().distance(player.getLocation()) <= range) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    // ==================== ITEM DURABILITY (armor durability enchants) ====================

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        if (!auth.isAuthenticated(player.getUniqueId())) return;

        ItemStack item = event.getItem();

        // Starborn Armor - reduce chestplate durability loss
        int starbornArmor = manager.getEnchantLevel(item, CustomEnchant.STARBORN_ARMOR);
        if (starbornArmor > 0) {
            if (ThreadLocalRandom.current().nextDouble() < starbornArmor * 0.05) {
                event.setCancelled(true);
                return;
            }
        }

        // Nebula Weave - reduce leggings durability loss
        int nebulaWeave = manager.getEnchantLevel(item, CustomEnchant.NEBULA_WEAVE);
        if (nebulaWeave > 0) {
            if (ThreadLocalRandom.current().nextDouble() < nebulaWeave * 0.05) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ==================== FOOD LEVEL (Star Fuel) ====================

    @EventHandler(ignoreCancelled = true)
    public void onHungerChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!auth.isAuthenticated(player.getUniqueId())) return;

        if (event.getFoodLevel() < player.getFoodLevel()) {
            int starFuel = manager.getArmorEnchantLevel(player, CustomEnchant.STAR_FUEL);
            if (starFuel > 0 && player.isSprinting()) {
                if (ThreadLocalRandom.current().nextDouble() < starFuel * 0.05) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // ==================== PLAYER MOVE (Magma Orbit, Star Path, Constellation Guard, Nebula Rain) ====================

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        if (!auth.isAuthenticated(player.getUniqueId())) return;

        // Magma Orbit - walk on lava
        int magmaOrbit = manager.getArmorEnchantLevel(player, CustomEnchant.MAGMA_ORBIT);
        if (magmaOrbit > 0) {
            Block below = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
            if (below.getType() == Material.LAVA) {
                below.setType(Material.OBSIDIAN);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (below.getType() == Material.OBSIDIAN) {
                        below.setType(Material.LAVA);
                    }
                }, 60L);
            }
        }

        // Star Path - speed bonus on paths
        int starPath = manager.getArmorEnchantLevel(player, CustomEnchant.STAR_PATH);
        if (starPath > 0) {
            Block below = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
            if (below.getType() == Material.DIRT_PATH) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, Math.min(starPath / 3, 2), true, false, false));
            }
        }

        // Tide Lock - water current immunity
        int tideLock = manager.getArmorEnchantLevel(player, CustomEnchant.TIDE_LOCK);
        if (tideLock > 0 && player.isInWater()) {
            player.setVelocity(player.getVelocity().multiply(new Vector(0.3, 1, 0.3)));
        }

        // Constellation Guard (hoe) - prevent mob spawns in radius
        ItemStack mainHand = player.getEquipment().getItemInMainHand();
        int constellationGuard = manager.getEnchantLevel(mainHand, CustomEnchant.CONSTELLATION_GUARD);
        if (constellationGuard > 0) {
            int range = constellationGuard * 5;
            for (Entity e : player.getNearbyEntities(range, range, range)) {
                if (e instanceof Monster && e.getTicksLived() <= 2) {
                    e.remove();
                }
            }
        }

        // Nebula Rain (hoe) - hydrate farmland without water
        int nebulaRain = manager.getEnchantLevel(mainHand, CustomEnchant.NEBULA_RAIN);
        if (nebulaRain > 0) {
            int range = nebulaRain * 2;
            Location loc = player.getLocation();
            for (int dx = -range; dx <= range; dx++) {
                for (int dz = -range; dz <= range; dz++) {
                    Block b = loc.getWorld().getBlockAt(loc.getBlockX() + dx, loc.getBlockY() - 1, loc.getBlockZ() + dz);
                    if (b.getType() == Material.FARMLAND) {
                        Farmland farmland = (Farmland) b.getBlockData();
                        farmland.setMoisture(farmland.getMaximumMoisture());
                        b.setBlockData(farmland);
                    }
                }
            }
        }

        // Solar Growth - crops grow faster nearby (boost random tick)
        int solarGrowth = manager.getEnchantLevel(mainHand, CustomEnchant.SOLAR_GROWTH);
        if (solarGrowth > 0) {
            if (ThreadLocalRandom.current().nextDouble() < solarGrowth * 0.01) {
                int range = 3;
                Location loc = player.getLocation();
                for (int dx = -range; dx <= range; dx++) {
                    for (int dz = -range; dz <= range; dz++) {
                        Block b = loc.getWorld().getBlockAt(loc.getBlockX() + dx, loc.getBlockY(), loc.getBlockZ() + dz);
                        if (isCrop(b.getType()) && b.getBlockData() instanceof Ageable ageable) {
                            if (ageable.getAge() < ageable.getMaximumAge()) {
                                ageable.setAge(ageable.getAge() + 1);
                                b.setBlockData(ageable);
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== PLAYER JOIN/QUIT (attribute cleanup) ====================

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                manager.applyStarHeartAttribute(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        manager.removeStarHeartAttribute(player);
        consecutiveHits.remove(player.getUniqueId());
        lastHitTime.remove(player.getUniqueId());
        sunlightTouchUses.remove(player.getUniqueId());
        player.setMaximumAir(300);
    }

    // ==================== ANVIL (apply enchant books) ====================

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory anvil = event.getInventory();
        ItemStack first = anvil.getItem(0);
        ItemStack second = anvil.getItem(1);

        if (first == null || second == null) return;
        if (!manager.isEnchantBook(second)) return;

        CustomEnchant enchant = manager.getBookEnchant(second);
        int bookLevel = manager.getBookLevel(second);
        if (enchant == null || bookLevel <= 0) return;

        if (!enchant.getTarget().matches(first)) return;

        int existingLevel = manager.getEnchantLevel(first, enchant);
        int newLevel;
        if (existingLevel == bookLevel && existingLevel < enchant.getMaxLevel()) {
            newLevel = existingLevel + 1;
        } else if (bookLevel > existingLevel) {
            newLevel = bookLevel;
        } else {
            newLevel = existingLevel;
        }

        ItemStack result = first.clone();
        manager.applyEnchant(result, enchant, newLevel);

        event.setResult(result);
        anvil.setRepairCost(1);
    }

    // ==================== HELPER METHODS ====================

    private void veinMine(Player player, Block origin, Material type, int maxBlocks, ItemStack tool) {
        Queue<Block> queue = new LinkedList<>();
        Set<Location> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin.getLocation());
        int count = 0;

        while (!queue.isEmpty() && count < maxBlocks) {
            Block current = queue.poll();
            for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block neighbor = current.getRelative(face);
                if (neighbor.getType() == type && !visited.contains(neighbor.getLocation())) {
                    visited.add(neighbor.getLocation());
                    Location nLoc = neighbor.getLocation();
                    if (processingBlocks.add(nLoc)) {
                        neighbor.breakNaturally(tool);
                        processingBlocks.remove(nLoc);
                        queue.add(neighbor);
                        count++;
                    }
                }
            }
        }
    }

    private void areaMine(Player player, Block center, int radius, ItemStack tool) {
        BlockFace face = getTargetFace(player);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block target;
                    if (face == BlockFace.UP || face == BlockFace.DOWN) {
                        target = center.getWorld().getBlockAt(center.getX() + dx, center.getY() + (face == BlockFace.UP ? -dy : dy), center.getZ() + dz);
                    } else if (face == BlockFace.NORTH || face == BlockFace.SOUTH) {
                        target = center.getWorld().getBlockAt(center.getX() + dx, center.getY() + dy, center.getZ());
                    } else {
                        target = center.getWorld().getBlockAt(center.getX(), center.getY() + dy, center.getZ() + dz);
                    }
                    if (target.getType() != Material.AIR && target.getType() != Material.BEDROCK) {
                        Location tLoc = target.getLocation();
                        if (processingBlocks.add(tLoc)) {
                            target.breakNaturally(tool);
                            processingBlocks.remove(tLoc);
                        }
                    }
                }
            }
        }
    }

    private void lineMine(Player player, Block origin, int length, ItemStack tool) {
        BlockFace face = getTargetFace(player);
        for (int i = 1; i <= length; i++) {
            Block target = origin.getRelative(face, i);
            if (target.getType() != Material.AIR && target.getType() != Material.BEDROCK) {
                Location tLoc = target.getLocation();
                if (processingBlocks.add(tLoc)) {
                    target.breakNaturally(tool);
                    processingBlocks.remove(tLoc);
                }
            }
        }
    }

    private void fellTree(Player player, Block origin, Material logType, int maxLogs, ItemStack tool) {
        Queue<Block> queue = new LinkedList<>();
        Set<Location> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin.getLocation());
        int count = 0;

        while (!queue.isEmpty() && count < maxLogs) {
            Block current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block neighbor = current.getWorld().getBlockAt(current.getX() + dx, current.getY() + dy, current.getZ() + dz);
                        if (neighbor.getType() == logType && !visited.contains(neighbor.getLocation())) {
                            visited.add(neighbor.getLocation());
                            Location nLoc = neighbor.getLocation();
                            if (processingBlocks.add(nLoc)) {
                                neighbor.breakNaturally(tool);
                                processingBlocks.remove(nLoc);
                                queue.add(neighbor);
                                count++;
                            }
                        }
                    }
                }
            }
        }
    }

    private BlockFace getTargetFace(Player player) {
        float pitch = player.getLocation().getPitch();
        if (pitch < -45) return BlockFace.UP;
        if (pitch > 45) return BlockFace.DOWN;
        float yaw = player.getLocation().getYaw();
        if (yaw < 0) yaw += 360;
        if (yaw >= 315 || yaw < 45) return BlockFace.SOUTH;
        if (yaw < 135) return BlockFace.WEST;
        if (yaw < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    // --- Material helpers ---

    private Material getSmeltedResult(Material mat) {
        return switch (mat) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.GOLD_INGOT;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            case COBBLESTONE -> Material.STONE;
            case SAND -> Material.GLASS;
            default -> null;
        };
    }

    private boolean isOre(Material mat) {
        return mat.name().contains("_ORE") || mat == Material.ANCIENT_DEBRIS;
    }

    private boolean isVeinMineable(Material mat) {
        return isOre(mat) || mat.name().contains("_LOG") || mat == Material.GLOWSTONE || mat == Material.OBSIDIAN;
    }

    private boolean isLog(Material mat) {
        return mat.name().endsWith("_LOG") || mat.name().endsWith("_WOOD");
    }

    private boolean isLeaves(Material mat) {
        return mat.name().endsWith("_LEAVES");
    }

    private boolean isShovelBlock(Material mat) {
        return switch (mat) {
            case DIRT, GRASS_BLOCK, SAND, GRAVEL, CLAY, SOUL_SAND, SOUL_SOIL,
                 COARSE_DIRT, PODZOL, MYCELIUM, MUD, ROOTED_DIRT, SNOW_BLOCK,
                 RED_SAND -> true;
            default -> false;
        };
    }

    private boolean isCrop(Material mat) {
        return switch (mat) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART, SWEET_BERRY_BUSH,
                 MELON_STEM, PUMPKIN_STEM, TORCHFLOWER_CROP, PITCHER_CROP -> true;
            default -> false;
        };
    }

    private boolean isFlower(Material mat) {
        return switch (mat) {
            case DANDELION, POPPY, BLUE_ORCHID, ALLIUM, AZURE_BLUET,
                 RED_TULIP, ORANGE_TULIP, WHITE_TULIP, PINK_TULIP,
                 OXEYE_DAISY, CORNFLOWER, LILY_OF_THE_VALLEY,
                 SUNFLOWER, LILAC, ROSE_BUSH, PEONY, TORCHFLOWER -> true;
            default -> false;
        };
    }

    private Material getCropSeed(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case MELON_STEM -> Material.MELON_SEEDS;
            case PUMPKIN_STEM -> Material.PUMPKIN_SEEDS;
            default -> null;
        };
    }

    private Material getSapling(Material log) {
        String name = log.name();
        if (name.contains("OAK")) return Material.OAK_SAPLING;
        if (name.contains("BIRCH")) return Material.BIRCH_SAPLING;
        if (name.contains("SPRUCE")) return Material.SPRUCE_SAPLING;
        if (name.contains("JUNGLE")) return Material.JUNGLE_SAPLING;
        if (name.contains("ACACIA")) return Material.ACACIA_SAPLING;
        if (name.contains("DARK_OAK")) return Material.DARK_OAK_SAPLING;
        if (name.contains("CHERRY")) return Material.CHERRY_SAPLING;
        if (name.contains("MANGROVE")) return Material.MANGROVE_PROPAGULE;
        return null;
    }

    private Material getTransformed(Material mat) {
        return switch (mat) {
            case SAND, RED_SAND -> Material.GLASS;
            case CLAY -> Material.BRICK;
            default -> null;
        };
    }
}
