package com.starlightuniverse.announce;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

public enum AnnouncementType {
    ALERT("Alert", "#FF5555", "!", Material.REDSTONE_BLOCK),
    INFO("Info", "#55AAFF", "i", Material.LAPIS_BLOCK),
    EVENT("Event", "#FFD700", "*", Material.GOLD_BLOCK),
    UPDATE("Update", "#55FF55", "+", Material.EMERALD_BLOCK),
    TIP("Tip", "#FFFF55", "?", Material.GLOWSTONE);

    private final String displayName;
    private final String hexColor;
    private final String tag;
    private final Material icon;

    AnnouncementType(String displayName, String hexColor, String tag, Material icon) {
        this.displayName = displayName;
        this.hexColor = hexColor;
        this.tag = tag;
        this.icon = icon;
    }

    public String getDisplayName() { return displayName; }
    public String getHexColor() { return hexColor; }
    public String getTag() { return tag; }
    public Material getIcon() { return icon; }
    public TextColor getColor() { return TextColor.fromHexString(hexColor); }

    public static AnnouncementType fromName(String name) {
        if (name == null) return null;
        for (AnnouncementType t : values()) {
            if (t.name().equalsIgnoreCase(name) || t.displayName.equalsIgnoreCase(name)) return t;
        }
        return null;
    }
}
