package com.starlightuniverse.job;

import org.bukkit.Material;

public enum JobType {

    MINER("Miner", Material.IRON_PICKAXE, "#FF8C00", "Mine ores and stone"),
    WOODCUTTER("Woodcutter", Material.IRON_AXE, "#228B22", "Chop logs and stems"),
    FARMER("Farmer", Material.IRON_HOE, "#32CD32", "Harvest mature crops"),
    HUNTER("Hunter", Material.IRON_SWORD, "#DC143C", "Kill hostile mobs"),
    FISHERMAN("Fisherman", Material.FISHING_ROD, "#1E90FF", "Catch fish and treasure"),
    BUILDER("Builder", Material.BRICKS, "#DAA520", "Place blocks"),
    DIGGER("Digger", Material.IRON_SHOVEL, "#8B4513", "Dig soil and sand"),
    BREWER("Brewer", Material.BREWING_STAND, "#9932CC", "Brew potions"),
    ENCHANTER("Enchanter", Material.ENCHANTING_TABLE, "#6A5ACD", "Enchant items"),
    SMELTER("Smelter", Material.FURNACE, "#FF4500", "Smelt items in furnaces");

    private final String displayName;
    private final Material icon;
    private final String hexColor;
    private final String description;

    JobType(String displayName, Material icon, String hexColor, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.hexColor = hexColor;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }

    public Material getIcon() { return icon; }

    public String getHexColor() { return hexColor; }

    public String getDescription() { return description; }
}
