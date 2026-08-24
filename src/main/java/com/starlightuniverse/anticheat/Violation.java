package com.starlightuniverse.anticheat;

public enum Violation {
    FLY("Fly"),
    SPEED("Speed"),
    NOFALL("NoFall"),
    REACH("Reach"),
    FASTBREAK("FastBreak"),
    AUTOCLICK("AutoClick"),
    SCAFFOLD("Scaffold"),
    KILLAURA("KillAura");

    private final String label;

    Violation(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
