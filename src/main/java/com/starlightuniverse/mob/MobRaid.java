package com.starlightuniverse.mob;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.boss.BossBar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MobRaid {

    public enum State { PREPARING, WAVE_ACTIVE, WAVE_PAUSE, ENDED }

    public State state = State.PREPARING;
    public int currentWave = 0;
    public int waveTicks = 0;
    public int pauseTicks = 0;
    public final long startTick;

    public final BossBar bossBar;

    public final Map<UUID, Integer> livesLeft = new ConcurrentHashMap<>();
    public final Map<UUID, Integer> waveKills = new ConcurrentHashMap<>();
    public final Map<UUID, Integer> totalKills = new ConcurrentHashMap<>();
    public final Map<UUID, Location> returnLocations = new HashMap<>();
    public final Map<UUID, GameMode> returnModes = new HashMap<>();

    public final Set<UUID> aliveMobs = ConcurrentHashMap.newKeySet();
    public final Set<UUID> outPlayers = ConcurrentHashMap.newKeySet();

    public Villager blacksmith;

    public MobRaid(BossBar bossBar, long startTick) {
        this.bossBar = bossBar;
        this.startTick = startTick;
    }

    public boolean isBossWave() { return currentWave > 0 && currentWave % 5 == 0; }
}
