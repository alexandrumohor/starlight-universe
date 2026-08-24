package com.starlightuniverse.benefit;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

public enum KillEffect {
    LIGHTNING("lightning", 300, "Cosmetic lightning strike"),
    FIREWORK("firework", 350, "Colorful firework"),
    EXPLOSION("explosion", 400, "Big particle blast"),
    FLAMES("flames", 300, "Ring of fire"),
    SOUL("soul", 500, "Soul rise + ghostly sound"),
    MAGIC("magic", 500, "Enchanted swirl");

    private final String key;
    private final int gemCost;
    private final String description;

    KillEffect(String key, int gemCost, String description) {
        this.key = key;
        this.gemCost = gemCost;
        this.description = description;
    }

    public String getKey() { return key; }
    public int getGemCost() { return gemCost; }
    public String getDescription() { return description; }
    public String getDisplayName() { return key.substring(0,1).toUpperCase() + key.substring(1); }

    public static KillEffect byKey(String key) {
        if (key == null) return null;
        for (KillEffect k : values()) if (k.key.equalsIgnoreCase(key)) return k;
        return null;
    }

    public void play(Player killer, LivingEntity victim) {
        Location loc = victim.getLocation().add(0, 1, 0);
        World world = loc.getWorld();
        if (world == null) return;
        switch (this) {
            case LIGHTNING -> {
                world.strikeLightningEffect(loc);
                world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.5f);
            }
            case FIREWORK -> {
                Firework fw = world.spawn(loc, Firework.class);
                FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder()
                        .with(FireworkEffect.Type.BALL_LARGE)
                        .withColor(Color.YELLOW, Color.ORANGE, Color.RED)
                        .withFade(Color.WHITE)
                        .trail(true)
                        .flicker(true)
                        .build());
                meta.setPower(0);
                fw.setFireworkMeta(meta);
                fw.detonate();
            }
            case EXPLOSION -> {
                world.spawnParticle(Particle.EXPLOSION, loc, 3, 0.5, 0.5, 0.5, 0);
                world.spawnParticle(Particle.LARGE_SMOKE, loc, 40, 1, 1, 1, 0.05);
                world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
            }
            case FLAMES -> {
                for (int i = 0; i < 30; i++) {
                    double angle = Math.PI * 2 * i / 30.0;
                    double x = Math.cos(angle) * 1.2;
                    double z = Math.sin(angle) * 1.2;
                    world.spawnParticle(Particle.FLAME, loc.clone().add(x, 0, z), 2, 0, 0.1, 0, 0.02);
                }
                world.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 0.8f);
            }
            case SOUL -> {
                world.spawnParticle(Particle.SOUL, loc, 40, 0.4, 0.6, 0.4, 0.06);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 20, 0.4, 0.6, 0.4, 0.02);
                world.playSound(loc, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 0.7f);
            }
            case MAGIC -> {
                for (int i = 0; i < 40; i++) {
                    double angle = Math.PI * 2 * i / 40.0;
                    double x = Math.cos(angle) * 1.5;
                    double z = Math.sin(angle) * 1.5;
                    world.spawnParticle(Particle.ENCHANT, loc.clone().add(x, 0.5, z), 3, 0, 0.2, 0, 0.05);
                    world.spawnParticle(Particle.WITCH, loc.clone().add(x, 0.5, z), 1, 0, 0.1, 0, 0);
                }
                world.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
            }
        }
    }
}
