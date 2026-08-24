package com.starlightuniverse.boss;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BossFight {
    public final BossType type;
    public final LivingEntity entity;
    public final BossBar bossBar;
    public final long startTick;

    public final Map<UUID, Double> damageDealt = new ConcurrentHashMap<>();
    public final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    public final Map<UUID, Location> returnLocations = new HashMap<>();
    public final Map<UUID, GameMode> returnModes = new HashMap<>();
    public final Set<UUID> respawning = ConcurrentHashMap.newKeySet();
    public final Map<UUID, Long> respawnAvailableAt = new ConcurrentHashMap<>();

    public boolean ended = false;

    public BossFight(BossType type, LivingEntity entity, BossBar bossBar, long startTick) {
        this.type = type;
        this.entity = entity;
        this.bossBar = bossBar;
        this.startTick = startTick;
    }

    public void addDamage(UUID uuid, double amount) {
        if (amount <= 0) return;
        damageDealt.merge(uuid, amount, Double::sum);
        participants.add(uuid);
    }
}
