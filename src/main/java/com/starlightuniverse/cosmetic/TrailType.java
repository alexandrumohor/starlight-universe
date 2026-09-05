package com.starlightuniverse.cosmetic;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.Particle;

public enum TrailType {
    FLAME("Flame", Particle.FLAME, Material.BLAZE_POWDER, "#FF6600"),
    HEART("Heart", Particle.HEART, Material.RED_DYE, "#FF5555"),
    NOTE("Music Note", Particle.NOTE, Material.NOTE_BLOCK, "#55FF55"),
    ENCHANT("Enchant", Particle.ENCHANT, Material.ENCHANTING_TABLE, "#AA55FF"),
    REDSTONE("Redstone", Particle.DUST, Material.REDSTONE, "#FF0000"),
    SMOKE("Smoke", Particle.CAMPFIRE_COSY_SMOKE, Material.CAMPFIRE, "#AAAAAA"),
    PORTAL("Portal", Particle.PORTAL, Material.OBSIDIAN, "#9955FF"),
    CHERRY_BLOSSOM("Cherry Blossom", Particle.CHERRY_LEAVES, Material.CHERRY_LEAVES, "#FFB7C5"),
    STAR("Star", Particle.END_ROD, Material.END_ROD, "#FFFFAA"),
    SOUL("Soul", Particle.SOUL_FIRE_FLAME, Material.SOUL_LANTERN, "#55FFFF"),
    DRIP("Honey Drip", Particle.DRIPPING_HONEY, Material.HONEY_BLOCK, "#FFD700"),
    SNOW("Snowflake", Particle.SNOWFLAKE, Material.SNOWBALL, "#FFFFFF"),
    WAX("Wax", Particle.WAX_ON, Material.HONEYCOMB, "#FF8C00"),
    SPORE("Spore Blossom", Particle.SPORE_BLOSSOM_AIR, Material.SPORE_BLOSSOM, "#FF69B4"),
    TRIAL("Trial Spark", Particle.TRIAL_SPAWNER_DETECTION, Material.TRIAL_KEY, "#55AAFF");

    private final String displayName;
    private final Particle particle;
    private final Material icon;
    private final TextColor color;

    TrailType(String displayName, Particle particle, Material icon, String hex) {
        this.displayName = displayName;
        this.particle = particle;
        this.icon = icon;
        this.color = TextColor.fromHexString(hex);
    }

    public String getDisplayName() { return displayName; }
    public Particle getParticle() { return particle; }
    public Material getIcon() { return icon; }
    public TextColor getColor() { return color; }
}
