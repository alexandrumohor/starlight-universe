package com.starlightuniverse.crate;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.Particle;

public enum CrateType {
    STAR("Star Crate", "#55FF55", Material.GREEN_SHULKER_BOX, Particle.COMPOSTER, 0, 0),
    COSMIC("Cosmic Crate", "#55FFFF", Material.LIGHT_BLUE_SHULKER_BOX, Particle.SOUL_FIRE_FLAME, 100, 0),
    GALAXY("Galaxy Crate", "#AA00FF", Material.PURPLE_SHULKER_BOX, Particle.DRAGON_BREATH, 0, 25),
    SEASONAL("Seasonal Crate", "#FFFF55", Material.YELLOW_SHULKER_BOX, Particle.FLAME, 0, 0);

    private final String displayName;
    private final TextColor color;
    private final Material shulkerMaterial;
    private final Particle particle;
    private final int gemsCost;
    private final int starsCost;

    CrateType(String displayName, String hex, Material shulkerMaterial, Particle particle, int gemsCost, int starsCost) {
        this.displayName = displayName;
        this.color = TextColor.fromHexString(hex);
        this.shulkerMaterial = shulkerMaterial;
        this.particle = particle;
        this.gemsCost = gemsCost;
        this.starsCost = starsCost;
    }

    public String getDisplayName() { return displayName; }
    public TextColor getColor() { return color; }
    public Material getShulkerMaterial() { return shulkerMaterial; }
    public Particle getParticle() { return particle; }
    public int getGemsCost() { return gemsCost; }
    public int getStarsCost() { return starsCost; }
    public boolean isBuyable() { return gemsCost > 0 || starsCost > 0; }

    public static CrateType fromName(String name) {
        if (name == null) return null;
        for (CrateType t : values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return null;
    }
}
