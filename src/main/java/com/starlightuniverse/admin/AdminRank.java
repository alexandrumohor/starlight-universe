package com.starlightuniverse.admin;

import net.kyori.adventure.text.format.TextColor;

public enum AdminRank {
    NONE(0, "#FFFFFF", ""),
    TRIAL_HELPER(1, "#0099FF", "Trial Helper"),
    HELPER(2, "#FF6600", "Helper"),
    MODERATOR(3, "#00CC00", "Moderator"),
    OWNER(4, "#FF0000", "Owner");

    private final int level;
    private final TextColor color;
    private final String displayName;

    AdminRank(int level, String hex, String displayName) {
        this.level = level;
        this.color = TextColor.fromHexString(hex);
        this.displayName = displayName;
    }

    public int getLevel() { return level; }
    public TextColor getColor() { return color; }
    public String getDisplayName() { return displayName; }

    public String getPrefix() {
        return displayName.isEmpty() ? "" : "[" + displayName + "]";
    }

    public static AdminRank fromLevel(int level) {
        for (AdminRank rank : values()) {
            if (rank.level == level) return rank;
        }
        return NONE;
    }

    public static AdminRank fromName(String name) {
        if (name == null) return null;
        String cleaned = name.replace(" ", "").replace("_", "");
        for (AdminRank rank : values()) {
            if (rank == NONE) continue;
            if (rank.name().equalsIgnoreCase(name) ||
                rank.displayName.replace(" ", "").equalsIgnoreCase(cleaned)) {
                return rank;
            }
        }
        return null;
    }

    public static final String[] PREMIUM_NAMES = {"None", "Meteor", "Comet", "Nebula", "Supernova", "Galaxy"};

    public static String premiumName(int level) {
        if (level < 0 || level >= PREMIUM_NAMES.length) return "Unknown";
        return PREMIUM_NAMES[level];
    }
}
