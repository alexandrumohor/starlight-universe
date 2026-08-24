package com.starlightuniverse.boss;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.EntityType;

public enum BossType {
    WARDEN("Warden", "warden", 2000, EntityType.WARDEN, 0x1E4F6B, BarColor.BLUE),
    WITHER("Wither", "wither", 1500, EntityType.WITHER, 0x2A2A2A, BarColor.PURPLE),
    DRAGON("Ender Dragon", "dragon", 3000, EntityType.ENDER_DRAGON, 0xAA00AA, BarColor.PINK),
    INFERNAL_GOLEM("Infernal Golem", "infernalgolem", 5000, EntityType.IRON_GOLEM, 0xFF4500, BarColor.RED);

    private final String displayName;
    private final String alias;
    private final double maxHealth;
    private final EntityType entityType;
    private final int hexColor;
    private final BarColor barColor;

    BossType(String displayName, String alias, double maxHealth, EntityType entityType,
             int hexColor, BarColor barColor) {
        this.displayName = displayName;
        this.alias = alias;
        this.maxHealth = maxHealth;
        this.entityType = entityType;
        this.hexColor = hexColor;
        this.barColor = barColor;
    }

    public String getDisplayName() { return displayName; }
    public String getAlias() { return alias; }
    public double getMaxHealth() { return maxHealth; }
    public EntityType getEntityType() { return entityType; }
    public TextColor getColor() { return TextColor.color(hexColor); }
    public BarColor getBarColor() { return barColor; }

    public static BossType fromAlias(String s) {
        if (s == null) return null;
        String needle = s.toLowerCase().replace("_", "").replace(" ", "");
        for (BossType t : values()) {
            if (t.alias.equals(needle) || t.name().toLowerCase().replace("_", "").equals(needle)) return t;
        }
        return null;
    }
}
