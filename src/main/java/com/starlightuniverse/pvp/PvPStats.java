package com.starlightuniverse.pvp;

public class PvPStats {

    public int elo;
    public int wins;
    public int losses;
    public int arenaKills;
    public int arenaDeaths;
    public int currentStreak;
    public int bestStreak;

    public PvPStats() {
        this(PvPArena.STARTING_ELO, 0, 0, 0, 0, 0, 0);
    }

    public PvPStats(int elo, int wins, int losses, int arenaKills,
                    int arenaDeaths, int currentStreak, int bestStreak) {
        this.elo = elo;
        this.wins = wins;
        this.losses = losses;
        this.arenaKills = arenaKills;
        this.arenaDeaths = arenaDeaths;
        this.currentStreak = currentStreak;
        this.bestStreak = bestStreak;
    }

    public PvPArena.Tier tier() {
        return PvPArena.Tier.of(elo);
    }
}
