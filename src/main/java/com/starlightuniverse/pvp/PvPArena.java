package com.starlightuniverse.pvp;

import com.starlightuniverse.arena.ArenaWorlds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public final class PvPArena {

    public static final String ARENA_WORLD = ArenaWorlds.PVP_WORLD;

    public static final double P1_X = -50.5;
    public static final double P1_Y = ArenaWorlds.SPAWN_Y;
    public static final double P1_Z = 0.5;
    public static final float  P1_YAW = -90f;
    public static final float  P1_PITCH = 0f;

    public static final double P2_X = 50.5;
    public static final double P2_Y = ArenaWorlds.SPAWN_Y;
    public static final double P2_Z = 0.5;
    public static final float  P2_YAW = 90f;
    public static final float  P2_PITCH = 0f;

    public static final double SPEC_X = 0.5;
    public static final double SPEC_Y = 250;
    public static final double SPEC_Z = 0.5;

    public static final int CENTER_X = ArenaWorlds.CENTER_X;
    public static final int CENTER_Y = ArenaWorlds.SPAWN_Y;
    public static final int CENTER_Z = ArenaWorlds.CENTER_Z;
    public static final int ARENA_RADIUS = ArenaWorlds.RADIUS - 1;
    public static final int ANTI_CAMP_MIN_MOVE = 3;
    public static final int ANTI_CAMP_WARN_SECONDS = 15;
    public static final int ANTI_CAMP_TP_SECONDS = 30;

    public static final int COUNTDOWN_SECONDS = 5;
    public static final int ROUND_MAX_SECONDS = 300;
    public static final int PAUSE_SECONDS = 5;
    public static final int ROUNDS_TO_WIN = 2;
    public static final int MAX_ROUNDS = 3;

    public static final double WIN_MONEY = 200;
    public static final double WIN_GEMS = 5;

    public static final int STREAK_2_MONEY = 100;
    public static final int STREAK_3_MONEY = 250;
    public static final int STREAK_3_GEMS = 10;
    public static final int STREAK_5_MONEY = 500;
    public static final int STREAK_5_GEMS = 25;
    public static final int STREAK_10_MONEY = 1500;
    public static final int STREAK_10_GEMS = 50;

    public static final int STARTING_ELO = 1000;
    public static final int K_FACTOR = 32;
    public static final int MATCH_ELO_MAX_DIFF = 500;

    private PvPArena() {}

    public static Location pos1() {
        World w = Bukkit.getWorld(ARENA_WORLD);
        if (w == null) return null;
        return new Location(w, P1_X, P1_Y, P1_Z, P1_YAW, P1_PITCH);
    }

    public static Location pos2() {
        World w = Bukkit.getWorld(ARENA_WORLD);
        if (w == null) return null;
        return new Location(w, P2_X, P2_Y, P2_Z, P2_YAW, P2_PITCH);
    }

    public static Location spectate() {
        World w = Bukkit.getWorld(ARENA_WORLD);
        if (w == null) return null;
        return new Location(w, SPEC_X, SPEC_Y, SPEC_Z, 0f, 0f);
    }

    public static Location center() {
        World w = Bukkit.getWorld(ARENA_WORLD);
        if (w == null) return null;
        return new Location(w, CENTER_X + 0.5, CENTER_Y, CENTER_Z + 0.5, 0f, 0f);
    }

    public static boolean isAllowedItem(Material m) {
        if (m == null || m == Material.AIR) return true;
        String n = m.name();
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) return true;
        if (n.endsWith("_SWORD") || n.endsWith("_PICKAXE")
                || n.endsWith("_AXE") || n.endsWith("_SHOVEL")
                || n.endsWith("_HOE")) return true;
        if (n.endsWith("_SPEAR")) return true;
        return switch (m) {
            case BOW, CROSSBOW, TRIDENT, MACE,
                 ARROW, SPECTRAL_ARROW, TIPPED_ARROW -> true;
            default -> false;
        };
    }

    public enum Tier {
        BRONZE(0,    "Bronze",    0xCD7F32),
        SILVER(800,  "Silver",    0xC0C0C0),
        GOLD(1200,   "Gold",      0xFFD700),
        PLATINUM(1500,"Platinum", 0x00CED1),
        DIAMOND(1800,"Diamond",   0x5865F2),
        MASTER(2100, "Master",    0x9932CC),
        CHAMPION(2400,"Champion", 0xFF4500);

        public final int minElo;
        public final String display;
        public final int color;

        Tier(int minElo, String display, int color) {
            this.minElo = minElo;
            this.display = display;
            this.color = color;
        }

        public static Tier of(int elo) {
            Tier[] tiers = values();
            for (int i = tiers.length - 1; i >= 0; i--) {
                if (elo >= tiers[i].minElo) return tiers[i];
            }
            return BRONZE;
        }
    }
}
