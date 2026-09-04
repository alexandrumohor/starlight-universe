package com.starlightuniverse.premium;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public enum PremiumRank {
    NONE(0, "None", "#FFFFFF", ""),
    METEOR(1, "Meteor", "#AAAAAA", "[Meteor]"),
    COMET(2, "Comet", "#55FFFF", "[Comet]"),
    NEBULA(3, "Nebula", "#AA00AA", "[Nebula]"),
    SUPERNOVA(4, "Supernova", "#FFAA00", "[Supernova]"),
    GALAXY(5, "Galaxy", "#FF5555", "[Galaxy]"),
    UNIVERSE(6, "Universe", "#FFD700", "[Universe]");

    private final int level;
    private final String displayName;
    private final TextColor color;
    private final String prefix;

    PremiumRank(int level, String displayName, String hex, String prefix) {
        this.level = level;
        this.displayName = displayName;
        this.color = TextColor.fromHexString(hex);
        this.prefix = prefix;
    }

    public int getLevel() { return level; }
    public String getDisplayName() { return displayName; }
    public TextColor getColor() { return color; }
    public String getPrefix() { return prefix; }

    public int getStarsCost() {
        return switch (level) { case 1 -> 100; case 2 -> 100; case 3 -> 100; case 4 -> 100; case 5 -> 100; case 6 -> 100; default -> 0; };
    }

    public int getGemsCost() {
        return switch (level) { case 1 -> 2_000; case 2 -> 5_000; case 3 -> 10_000; case 4 -> 20_000; case 5 -> 40_000; case 6 -> 80_000; default -> 0; };
    }

    public int getMaxHomes() {
        return switch (level) { case 1 -> 3; case 2 -> 5; case 3 -> 10; case 4 -> 20; case 5 -> 40; case 6 -> -1; default -> 2; };
    }

    public int getMaxProtectionBlocks() {
        return switch (level) { case 1 -> 2_500; case 2 -> 5_000; case 3 -> 10_000; case 4 -> 25_000; case 5 -> 75_000; case 6 -> 150_000; default -> 1_000; };
    }

    public int getExtraWarps() {
        return switch (level) { case 1 -> 1; case 2 -> 2; case 3 -> 3; case 4 -> 4; case 5 -> 5; case 6 -> 7; default -> 0; };
    }

    public int getKeepXpPercent() {
        return switch (level) { case 1 -> 25; case 2 -> 50; case 3 -> 75; case 4 -> 90; case 5, 6 -> 100; default -> 0; };
    }

    public int getCooldownSeconds() {
        return switch (level) { case 1 -> 4; case 2 -> 3; case 3 -> 2; case 4 -> 1; case 5, 6 -> 0; default -> 5; };
    }

    public int getTpaDuration() {
        return switch (level) { case 1 -> 90; case 2 -> 120; case 3 -> 180; case 4 -> 240; case 5 -> 300; case 6 -> 600; default -> 60; };
    }

    public int getMaxTrails() {
        return switch (level) { case 1 -> 1; case 2 -> 2; case 3 -> 3; case 4 -> 5; case 5, 6 -> -1; default -> 0; };
    }

    public int getMobKillMoneyBonus() {
        return switch (level) { case 2 -> 10; case 3 -> 20; case 4 -> 30; case 5 -> 50; case 6 -> 75; default -> 0; };
    }

    public int getMonthlyStars() {
        return switch (level) { case 3 -> 5; case 4 -> 10; case 5 -> 15; case 6 -> 25; default -> 0; };
    }

    public int getKeepArmorPercent() {
        return switch (level) { case 2, 4, 5, 6 -> 100; default -> 0; };
    }

    public int getKeepInventoryPercent() {
        return switch (level) { case 3 -> 30; case 4 -> 60; case 5, 6 -> 100; default -> 0; };
    }

    public double getXpBoost() {
        return switch (level) { case 4 -> 1.25; case 5 -> 1.5; case 6 -> 2.0; default -> 1.0; };
    }

    public int getDailyBonus() {
        return switch (level) { case 1 -> 500; case 2 -> 750; case 3 -> 1_000; case 4 -> 1_500; case 5 -> 2_000; case 6 -> 3_000; default -> 0; };
    }

    public boolean hasAutoPickup() { return level >= 2; }
    public boolean hasPriorityQueue() { return level >= 4; }
    public boolean hasColoredChat() { return level >= 3; }
    public boolean hasCustomJoinQuit() { return level >= 3; }
    public boolean canFlyLobby() { return level >= 3; }
    public boolean canFlyProtections() { return level >= 5; }
    public boolean hasRainbowTag() { return level >= 5; }
    public boolean hasParticleAura() { return level >= 5; }

    public Component getColoredPrefix() {
        if (this == NONE) return Component.empty();
        if (this == GALAXY) return galaxyGradient(prefix);
        if (this == UNIVERSE) return universeGradient(prefix);
        return Component.text(prefix, color);
    }

    public Component getColoredDisplayName() {
        if (this == NONE) return Component.empty();
        if (this == GALAXY) return galaxyGradient(displayName);
        if (this == UNIVERSE) return universeGradient(displayName);
        return Component.text(displayName, color);
    }

    private static Component galaxyGradient(String text) {
        Component comp = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            float ratio = text.length() > 1 ? (float) i / (text.length() - 1) : 0;
            int r = (int) (0xFF + (0xAA - 0xFF) * ratio);
            int g = (int) (0x55 + (0x00 - 0x55) * ratio);
            int b = (int) (0x55 + (0xAA - 0x55) * ratio);
            comp = comp.append(Component.text(text.charAt(i), TextColor.color(r, g, b)));
        }
        return comp;
    }

    private static final int[][] UNIVERSE_COLORS = {
            {0xFF, 0xD7, 0x00}, {0xFF, 0x55, 0x55}, {0xAA, 0x00, 0xFF},
            {0x55, 0xFF, 0xFF}, {0x55, 0xFF, 0x55}, {0xFF, 0xD7, 0x00}
    };

    private static Component universeGradient(String text) {
        Component comp = Component.empty();
        int segments = UNIVERSE_COLORS.length - 1;
        for (int i = 0; i < text.length(); i++) {
            float ratio = text.length() > 1 ? (float) i / (text.length() - 1) : 0;
            float scaled = ratio * segments;
            int seg = Math.min((int) scaled, segments - 1);
            float t = scaled - seg;
            int r = (int) (UNIVERSE_COLORS[seg][0] + (UNIVERSE_COLORS[seg + 1][0] - UNIVERSE_COLORS[seg][0]) * t);
            int g = (int) (UNIVERSE_COLORS[seg][1] + (UNIVERSE_COLORS[seg + 1][1] - UNIVERSE_COLORS[seg][1]) * t);
            int b = (int) (UNIVERSE_COLORS[seg][2] + (UNIVERSE_COLORS[seg + 1][2] - UNIVERSE_COLORS[seg][2]) * t);
            comp = comp.append(Component.text(text.charAt(i), TextColor.color(r, g, b)));
        }
        return comp;
    }

    public static PremiumRank fromLevel(int level) {
        for (PremiumRank rank : values()) {
            if (rank.level == level) return rank;
        }
        return NONE;
    }
}
