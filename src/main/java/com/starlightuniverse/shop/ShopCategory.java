package com.starlightuniverse.shop;

import org.bukkit.Material;

public enum ShopCategory {
    BUILDING("Building", Material.BRICKS, 0xFF8C00),
    DECORATION("Decoration", Material.FLOWER_POT, 0xFF55FF),
    REDSTONE("Redstone", Material.REDSTONE, 0xFF0000),
    TRANSPORT("Transport", Material.MINECART, 0xFFFF55),
    FOOD("Food", Material.COOKED_BEEF, 0xFFAA00),
    TOOLS("Tools", Material.DIAMOND_PICKAXE, 0x55FFFF),
    WEAPONS("Weapons", Material.DIAMOND_SWORD, 0xFF5555),
    ARMOR("Armor", Material.DIAMOND_CHESTPLATE, 0x5555FF),
    BREWING("Brewing", Material.BREWING_STAND, 0xAA00AA),
    MATERIALS("Materials", Material.IRON_INGOT, 0xAAAAAA),
    DYES("Dyes", Material.RED_DYE, 0x55FF55),
    SPAWN_EGGS("Spawn Eggs", Material.ZOMBIE_SPAWN_EGG, 0x00AA00),
    MISC("Misc", Material.ENDER_PEARL, 0xFFD700);

    private final String displayName;
    private final Material icon;
    private final int color;

    ShopCategory(String displayName, Material icon, int color) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
    }

    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public int color() { return color; }
}
