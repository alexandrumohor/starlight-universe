package com.starlightuniverse.skill;

import org.bukkit.Material;

public enum SkillType {

    MINING("Mining", Material.DIAMOND_PICKAXE, "#FF8C00", "Mine ores and stone"),
    EXCAVATION("Excavation", Material.DIAMOND_SHOVEL, "#8B4513", "Dig soil, sand, and gravel"),
    WOODCUTTING("Woodcutting", Material.DIAMOND_AXE, "#228B22", "Chop logs and stems"),
    FARMING("Farming", Material.DIAMOND_HOE, "#32CD32", "Harvest mature crops"),
    COMBAT("Combat", Material.NETHERITE_SWORD, "#DC143C", "Defeat enemies in melee"),
    ARCHERY("Archery", Material.BOW, "#FF6600", "Kill with projectiles"),
    FISHING("Fishing", Material.FISHING_ROD, "#1E90FF", "Catch fish and treasure"),
    ACROBATICS("Acrobatics", Material.FEATHER, "#55FFFF", "Survive falls gracefully"),
    REPAIR("Repair", Material.ANVIL, "#AAAAAA", "Repair items at an anvil"),
    ALCHEMY("Alchemy", Material.BREWING_STAND, "#9932CC", "Brew potions"),
    TAMING("Taming", Material.LEAD, "#FFAA00", "Tame and train animals"),
    COOKING("Cooking", Material.CAMPFIRE, "#FF4500", "Cook food items");

    private final String displayName;
    private final Material icon;
    private final String hexColor;
    private final String description;

    SkillType(String displayName, Material icon, String hexColor, String description) {
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
