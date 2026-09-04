package com.starlightuniverse.buff;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

public enum BuffType {
    GOD_MODE("God Mode", Material.TOTEM_OF_UNDYING, "#FF5555", "Immune to all damage"),
    FLY_MODE("Fly Mode", Material.FEATHER, "#55FFFF", "Permanent flight for 12h"),
    NIGHT_VISION("Night Vision", Material.GOLDEN_CARROT, "#FFFF55", "See in the dark"),
    JUMP_BOOST("Jump Boost", Material.RABBIT_FOOT, "#55FF55", "Jump higher"),
    SPEED_WALK("Speed Walk", Material.SUGAR, "#AAAAFF", "Walk faster"),
    MOB_LOOT_5X("Mob Loot 5x", Material.ROTTEN_FLESH, "#FF5555", "5x mob drops"),
    ORE_LOOT_5X("Ore Loot 5x", Material.DIAMOND_ORE, "#55FFFF", "5x ore drops"),
    TREE_LOOT_5X("Tree Loot 5x", Material.OAK_LOG, "#55FF55", "5x log drops"),
    BLOCK_LOOT_5X("Block Loot 5x", Material.STONE, "#AAAAAA", "5x block drops"),
    FISH_LOOT_5X("Fish Loot 5x", Material.COD, "#5555FF", "5x fishing drops"),
    CROP_LOOT_5X("Crop Loot 5x", Material.WHEAT, "#FFAA00", "5x crop drops"),
    EXTRA_CHUNKS("Extra Chunks +10", Material.ENDER_EYE, "#AA00FF", "+10 view distance chunks"),
    VILLAGER_REFILL("Villager Refill 3x", Material.EMERALD, "#55FF55", "Villager trades refill 3x faster");

    private final String displayName;
    private final Material icon;
    private final TextColor color;
    private final String description;

    BuffType(String displayName, Material icon, String hex, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = TextColor.fromHexString(hex);
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public TextColor getColor() { return color; }
    public String getDescription() { return description; }
}
