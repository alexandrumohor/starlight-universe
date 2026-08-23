package com.starlightuniverse.premium;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public enum PremiumRank {
    NONE(0, "None", "#FFFFFF", ""),
    METEOR(1, "Meteor", "#AAAAAA", "[Meteor]"),
    COMET(2, "Comet", "#55FFFF", "[Comet]"),
    NEBULA(3, "Nebula", "#AA00AA", "[Nebula]"),
    SUPERNOVA(4, "Supernova", "#FFAA00", "[Supernova]"),
    GALAXY(5, "Galaxy", "#FF5555", "[Galaxy]");

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
        return switch (level) { case 1 -> 500; case 2 -> 1_500; case 3 -> 5_000; case 4 -> 15_000; case 5 -> 50_000; default -> 0; };
    }

    public int getGemsCost() {
        return switch (level) { case 1 -> 2_000; case 2 -> 5_000; case 3 -> 10_000; case 4 -> 20_000; case 5 -> 40_000; default -> 0; };
    }

    public int getMaxHomes() {
        return switch (level) { case 1 -> 3; case 2 -> 5; case 3 -> 7; case 4 -> 10; case 5 -> 15; default -> 2; };
    }

    public int getMaxProtectionRadius() {
        return switch (level) { case 1 -> 75; case 2 -> 100; case 3 -> 150; case 4 -> 200; case 5 -> 300; default -> 25; };
    }

    public int getExtraWarps() {
        return switch (level) { case 1 -> 1; case 2 -> 2; case 3 -> 3; case 4 -> 4; case 5 -> 5; default -> 0; };
    }

    public int getKeepXpPercent() {
        return switch (level) { case 1 -> 25; case 2 -> 50; case 3 -> 75; case 4 -> 90; case 5 -> 100; default -> 0; };
    }

    public int getCooldownSeconds() {
        return switch (level) { case 1 -> 4; case 2 -> 3; case 3 -> 2; case 4 -> 1; case 5 -> 0; default -> 5; };
    }

    public int getTpaDuration() {
        return switch (level) { case 1 -> 90; case 2 -> 120; case 3 -> 180; case 4 -> 240; case 5 -> 300; default -> 60; };
    }

    public int getMaxTrails() {
        return switch (level) { case 1 -> 1; case 2 -> 2; case 3 -> 3; case 4 -> 5; case 5 -> -1; default -> 0; };
    }

    public int getMobKillMoneyBonus() {
        return switch (level) { case 2 -> 10; case 3 -> 20; case 4 -> 30; case 5 -> 50; default -> 0; };
    }

    public int getMonthlyStars() {
        return switch (level) { case 3 -> 5; case 4 -> 10; case 5 -> 15; default -> 0; };
    }

    public int getKeepArmorPercent() {
        return switch (level) { case 2, 4, 5 -> 100; default -> 0; };
    }

    public int getKeepInventoryPercent() {
        return switch (level) { case 3 -> 30; case 4 -> 60; case 5 -> 100; default -> 0; };
    }

    public double getXpBoost() {
        return switch (level) { case 4 -> 1.25; case 5 -> 1.5; default -> 1.0; };
    }

    public int getDailyBonus() {
        return switch (level) { case 1 -> 500; case 2 -> 750; case 3 -> 1_000; case 4 -> 1_500; case 5 -> 2_000; default -> 0; };
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
        if (this == GALAXY) {
            String text = prefix;
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
        return Component.text(prefix, color);
    }

    public static PremiumRank fromLevel(int level) {
        for (PremiumRank rank : values()) {
            if (rank.level == level) return rank;
        }
        return NONE;
    }
}
