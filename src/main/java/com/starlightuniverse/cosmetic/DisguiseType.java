package com.starlightuniverse.cosmetic;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public enum DisguiseType {
    CHICKEN("Chicken", EntityType.CHICKEN, Material.EGG, "#FFFFFF"),
    PIG("Pig", EntityType.PIG, Material.PORKCHOP, "#FFB6C1"),
    COW("Cow", EntityType.COW, Material.BEEF, "#8B4513"),
    SHEEP("Sheep", EntityType.SHEEP, Material.WHITE_WOOL, "#EEEEEE"),
    CREEPER("Creeper", EntityType.CREEPER, Material.GUNPOWDER, "#55FF55"),
    SKELETON("Skeleton", EntityType.SKELETON, Material.BONE, "#CCCCCC"),
    ZOMBIE("Zombie", EntityType.ZOMBIE, Material.ROTTEN_FLESH, "#55AA55"),
    ENDERMAN("Enderman", EntityType.ENDERMAN, Material.ENDER_PEARL, "#9955FF"),
    SPIDER("Spider", EntityType.SPIDER, Material.STRING, "#555555"),
    VILLAGER("Villager", EntityType.VILLAGER, Material.EMERALD, "#AA8855"),
    IRON_GOLEM("Iron Golem", EntityType.IRON_GOLEM, Material.IRON_INGOT, "#CCCCCC"),
    SNOW_GOLEM("Snow Golem", EntityType.SNOW_GOLEM, Material.SNOWBALL, "#FFFFFF"),
    BLAZE("Blaze", EntityType.BLAZE, Material.BLAZE_ROD, "#FF8800"),
    WITCH("Witch", EntityType.WITCH, Material.SPIDER_EYE, "#AA00AA"),
    WOLF("Wolf", EntityType.WOLF, Material.BONE, "#AAAAAA");

    private final String displayName;
    private final EntityType entityType;
    private final Material icon;
    private final TextColor color;

    DisguiseType(String displayName, EntityType entityType, Material icon, String hex) {
        this.displayName = displayName;
        this.entityType = entityType;
        this.icon = icon;
        this.color = TextColor.fromHexString(hex);
    }

    public String getDisplayName() { return displayName; }
    public EntityType getEntityType() { return entityType; }
    public Material getIcon() { return icon; }
    public TextColor getColor() { return color; }
}
