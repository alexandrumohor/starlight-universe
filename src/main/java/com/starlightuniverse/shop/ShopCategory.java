package com.starlightuniverse.shop;

import org.bukkit.Material;

public enum ShopCategory {
    STONE_BLOCKS("Stone Blocks", Material.STONE_BRICKS, 0xAAAAAA),
    WOOD_BLOCKS("Wood Blocks", Material.OAK_LOG, 0xA0522D),
    NATURAL_BLOCKS("Natural Blocks", Material.GRASS_BLOCK, 0x55FF55),
    MINERALS("Minerals", Material.DIAMOND, 0x55FFFF),
    FARMING("Farming", Material.WHEAT, 0xFFFF55),
    SPAWNERS("Spawners", Material.SPAWNER, 0xFF5555),
    MOB_DROPS("Mob Drops", Material.BONE, 0xAAAAAA),
    FOOD("Food", Material.COOKED_BEEF, 0xFFAA00),
    DECORATION("Decoration", Material.FLOWER_POT, 0xFF55FF),
    MISCELLANEOUS("Miscellaneous", Material.ENDER_PEARL, 0xFFD700),
    SAPLINGS("Saplings", Material.OAK_SAPLING, 0x55FF55);

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
