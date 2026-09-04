package com.starlightuniverse.booster;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

public enum BoosterType {

    XP_VANILLA("Vanilla XP Booster", Material.EXPERIENCE_BOTTLE, "#55FF55", "Boosts all vanilla XP gains"),
    XP_JOB("Job XP Booster", Material.KNOWLEDGE_BOOK, "#FFFF55", "Boosts job XP gains"),
    MONEY_JOB("Job Money Booster", Material.GOLD_INGOT, "#FFD700", "Boosts job money earnings"),
    AH_MULTIPLIER("Auction House Booster", Material.CHEST, "#FF8C00", "Boosts auction house sale earnings"),
    CHESTSHOP_MULTIPLIER("ChestShop Booster", Material.OAK_SIGN, "#55FFFF", "Boosts chestshop sale earnings");

    private final String displayName;
    private final Material icon;
    private final TextColor color;
    private final String description;

    BoosterType(String displayName, Material icon, String hex, String description) {
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
