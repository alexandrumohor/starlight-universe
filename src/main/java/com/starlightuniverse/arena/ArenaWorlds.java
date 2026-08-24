package com.starlightuniverse.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public final class ArenaWorlds {

    public static final String PVP_WORLD = "world_pvp";
    public static final String MOBS_WORLD = "world_mobs";
    public static final String BOSS_WORLD = "world_boss";
    public static final String[] ALL_WORLDS = {PVP_WORLD, MOBS_WORLD, BOSS_WORLD};

    public static final int RADIUS = 250;
    public static final int DIAMETER = RADIUS * 2;
    public static final int FLOOR_Y = 100;
    public static final int CEILING_Y = 300;
    public static final int SPAWN_Y = FLOOR_Y + 1;
    public static final int CENTER_X = 0;
    public static final int CENTER_Z = 0;

    public static final Material[] BLOCKS = {
            Material.DIAMOND_BLOCK,
            Material.GOLD_BLOCK,
            Material.IRON_BLOCK,
            Material.EMERALD_BLOCK,
            Material.REDSTONE_BLOCK
    };
    public static final Material FROGLIGHT = Material.OCHRE_FROGLIGHT;
    public static final int FROGLIGHT_ONE_IN = 8;

    public static final long PATTERN_SEED = 0x5FA71171L;

    private ArenaWorlds() {}

    public static Location center(String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w, CENTER_X + 0.5, SPAWN_Y, CENTER_Z + 0.5, 0f, 0f);
    }

    public static boolean isArenaWorld(String worldName) {
        for (String s : ALL_WORLDS) if (s.equals(worldName)) return true;
        return false;
    }
}
