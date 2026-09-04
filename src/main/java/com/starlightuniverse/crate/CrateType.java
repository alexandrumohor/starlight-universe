package com.starlightuniverse.crate;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Material;

public enum CrateType {
    STAR("Star Crate", "#55FF55", Material.GREEN_SHULKER_BOX),
    COSMIC("Cosmic Crate", "#55FFFF", Material.LIGHT_BLUE_SHULKER_BOX),
    GALAXY("Galaxy Crate", "#AA00FF", Material.PURPLE_SHULKER_BOX),
    CELESTIAL("Celestial Crate", "#FFAA00", Material.ORANGE_SHULKER_BOX),
    UNIVERSE("Universe Crate", "#FF5555", Material.RED_SHULKER_BOX);

    private final String displayName;
    private final TextColor color;
    private final Material shulkerMaterial;

    CrateType(String displayName, String hex, Material shulkerMaterial) {
        this.displayName = displayName;
        this.color = TextColor.fromHexString(hex);
        this.shulkerMaterial = shulkerMaterial;
    }

    public String getDisplayName() { return displayName; }
    public TextColor getColor() { return color; }
    public Material getShulkerMaterial() { return shulkerMaterial; }
    public Color getBukkitColor() { return Color.fromRGB(color.red(), color.green(), color.blue()); }

    public static CrateType fromName(String name) {
        if (name == null) return null;
        for (CrateType t : values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return null;
    }
}
