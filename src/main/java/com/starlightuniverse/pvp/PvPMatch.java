package com.starlightuniverse.pvp;

import org.bukkit.GameMode;
import org.bukkit.Location;

import java.util.UUID;

public class PvPMatch {

    public enum State { COUNTDOWN, ROUND_ACTIVE, ROUND_END, PAUSE, ENDED }

    public final UUID p1;
    public final UUID p2;
    public final boolean ranked;
    public final Location p1Return;
    public final Location p2Return;
    public final GameMode p1ReturnMode;
    public final GameMode p2ReturnMode;
    public final int p1StartElo;
    public final int p2StartElo;

    public int p1Wins = 0;
    public int p2Wins = 0;
    public int round = 1;
    public State state = State.COUNTDOWN;
    public int stateSeconds = 0;
    public int roundSeconds = 0;

    public UUID winner = null;

    public Location p1LastPos;
    public Location p2LastPos;
    public int p1IdleSeconds = 0;
    public int p2IdleSeconds = 0;
    public boolean p1CampWarned = false;
    public boolean p2CampWarned = false;

    public PvPMatch(UUID p1, UUID p2, boolean ranked,
                    Location p1Return, Location p2Return,
                    GameMode p1ReturnMode, GameMode p2ReturnMode,
                    int p1StartElo, int p2StartElo) {
        this.p1 = p1;
        this.p2 = p2;
        this.ranked = ranked;
        this.p1Return = p1Return;
        this.p2Return = p2Return;
        this.p1ReturnMode = p1ReturnMode;
        this.p2ReturnMode = p2ReturnMode;
        this.p1StartElo = p1StartElo;
        this.p2StartElo = p2StartElo;
    }

    public boolean has(UUID uuid) {
        return p1.equals(uuid) || p2.equals(uuid);
    }

    public UUID other(UUID uuid) {
        return p1.equals(uuid) ? p2 : p1;
    }
}
