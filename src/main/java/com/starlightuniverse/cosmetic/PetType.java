package com.starlightuniverse.cosmetic;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public enum PetType {
    WOLF("Wolf", EntityType.WOLF, Material.BONE, "#AAAAAA"),
    CAT("Cat", EntityType.CAT, Material.COD, "#FFAA00"),
    PARROT("Parrot", EntityType.PARROT, Material.COOKIE, "#FF5555"),
    FOX("Fox", EntityType.FOX, Material.SWEET_BERRIES, "#FF8C00"),
    AXOLOTL("Axolotl", EntityType.AXOLOTL, Material.TROPICAL_FISH, "#FF69B4"),
    RABBIT("Rabbit", EntityType.RABBIT, Material.CARROT, "#FFFF55"),
    BEE("Bee", EntityType.BEE, Material.HONEYCOMB, "#FFD700"),
    FROG("Frog", EntityType.FROG, Material.SLIME_BALL, "#55FF55"),
    ALLAY("Allay", EntityType.ALLAY, Material.AMETHYST_SHARD, "#55FFFF"),
    SNIFFER("Sniffer", EntityType.SNIFFER, Material.TORCHFLOWER_SEEDS, "#AA6600"),
    ARMADILLO("Armadillo", EntityType.ARMADILLO, Material.BRUSH, "#CC8855"),
    PANDA("Panda", EntityType.PANDA, Material.BAMBOO, "#55FF55"),
    TURTLE("Turtle", EntityType.TURTLE, Material.SEAGRASS, "#00AA00"),
    OCELOT("Ocelot", EntityType.OCELOT, Material.COD, "#FFFF55"),
    STRIDER("Strider", EntityType.STRIDER, Material.WARPED_FUNGUS, "#FF5555");

    private final String displayName;
    private final EntityType entityType;
    private final Material icon;
    private final TextColor color;

    PetType(String displayName, EntityType entityType, Material icon, String hex) {
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
