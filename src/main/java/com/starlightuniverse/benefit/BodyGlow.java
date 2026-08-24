package com.starlightuniverse.benefit;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;

public enum BodyGlow {
    WHITE("white", "#FFFFFF", ChatColor.WHITE, NamedTextColor.WHITE),
    RED("red", "#FF5555", ChatColor.RED, NamedTextColor.RED),
    ORANGE("orange", "#FFAA00", ChatColor.GOLD, NamedTextColor.GOLD),
    YELLOW("yellow", "#FFFF55", ChatColor.YELLOW, NamedTextColor.YELLOW),
    GREEN("green", "#55FF55", ChatColor.GREEN, NamedTextColor.GREEN),
    CYAN("cyan", "#55FFFF", ChatColor.AQUA, NamedTextColor.AQUA),
    BLUE("blue", "#5555FF", ChatColor.BLUE, NamedTextColor.BLUE),
    PURPLE("purple", "#AA00AA", ChatColor.LIGHT_PURPLE, NamedTextColor.LIGHT_PURPLE);

    public static final int UNLOCK_GEM_COST = 100;

    private final String key;
    private final String hex;
    private final ChatColor bukkitColor;
    private final NamedTextColor namedColor;

    BodyGlow(String key, String hex, ChatColor bukkitColor, NamedTextColor namedColor) {
        this.key = key;
        this.hex = hex;
        this.bukkitColor = bukkitColor;
        this.namedColor = namedColor;
    }

    public String getKey() { return key; }
    public String getHex() { return hex; }
    public ChatColor getBukkitColor() { return bukkitColor; }
    public NamedTextColor getNamedColor() { return namedColor; }
    public String getDisplayName() { return key.substring(0,1).toUpperCase() + key.substring(1); }

    public static BodyGlow byKey(String key) {
        if (key == null) return null;
        for (BodyGlow g : values()) if (g.key.equalsIgnoreCase(key)) return g;
        return null;
    }
}
