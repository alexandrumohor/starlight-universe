package com.starlightuniverse.team;

public enum TeamRank {

    MEMBER(0, "Member", "#AAAAAA"),
    SENIOR_MEMBER(1, "Senior Member", "#55FF55"),
    VETERAN(2, "Veteran", "#FFFF55"),
    OFFICER(3, "Officer", "#55FFFF"),
    CO_LEADER(4, "Co-Leader", "#FFAA00"),
    LEADER(5, "Leader", "#FF5555");

    private final int level;
    private final String displayName;
    private final String color;

    TeamRank(int level, String displayName, String color) {
        this.level = level;
        this.displayName = displayName;
        this.color = color;
    }

    public int getLevel() { return level; }
    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }

    public boolean canInvite() { return level >= OFFICER.level; }
    public boolean canKick() { return level >= OFFICER.level; }
    public boolean canPromote() { return level >= CO_LEADER.level; }
    public boolean canDemote() { return level >= CO_LEADER.level; }
    public boolean canWithdraw() { return level >= CO_LEADER.level; }
    public boolean canSetHome() { return level >= CO_LEADER.level; }
    public boolean canDisband() { return this == LEADER; }
    public boolean canSetColor() { return this == LEADER; }
    public boolean canSetName() { return this == LEADER; }
    public boolean canToggleFriendlyFire() { return this == LEADER; }
    public boolean canAlly() { return this == LEADER; }
    public boolean canDeclareWar() { return this == LEADER; }

    public static TeamRank fromLevel(int level) {
        for (TeamRank rank : values()) {
            if (rank.level == level) return rank;
        }
        return MEMBER;
    }

    public static TeamRank fromDbName(String name) {
        if (name == null) return MEMBER;
        for (TeamRank rank : values()) {
            if (rank.name().equalsIgnoreCase(name)) return rank;
        }
        return MEMBER;
    }

    public TeamRank next() {
        return switch (this) {
            case MEMBER -> SENIOR_MEMBER;
            case SENIOR_MEMBER -> VETERAN;
            case VETERAN -> OFFICER;
            case OFFICER -> CO_LEADER;
            case CO_LEADER, LEADER -> null;
        };
    }

    public TeamRank previous() {
        return switch (this) {
            case MEMBER, LEADER -> null;
            case SENIOR_MEMBER -> MEMBER;
            case VETERAN -> SENIOR_MEMBER;
            case OFFICER -> VETERAN;
            case CO_LEADER -> OFFICER;
        };
    }
}
