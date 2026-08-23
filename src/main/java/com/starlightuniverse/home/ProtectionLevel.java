package com.starlightuniverse.home;

import net.kyori.adventure.text.format.TextColor;

public enum ProtectionLevel {

    VISITOR(0, "Visitor", TextColor.color(0xAAAAAA)),
    BUILDER(1, "Builder", TextColor.color(0x55FF55)),
    FULL(2, "Full Access", TextColor.color(0x55FFFF));

    private final int level;
    private final String display;
    private final TextColor color;

    ProtectionLevel(int level, String display, TextColor color) {
        this.level = level;
        this.display = display;
        this.color = color;
    }

    public int getLevel() { return level; }
    public String getDisplay() { return display; }
    public TextColor getColor() { return color; }

    public static ProtectionLevel fromLevel(int level) {
        for (ProtectionLevel pl : values()) {
            if (pl.level == level) return pl;
        }
        return VISITOR;
    }

    public ProtectionLevel next() {
        return switch (this) {
            case VISITOR -> BUILDER;
            case BUILDER -> FULL;
            case FULL -> VISITOR;
        };
    }
}
