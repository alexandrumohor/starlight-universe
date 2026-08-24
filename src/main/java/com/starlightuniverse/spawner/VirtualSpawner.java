package com.starlightuniverse.spawner;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.EnumMap;
import java.util.Map;

public class VirtualSpawner {

    private int id;
    private final String ownerUsername;
    private final VirtualSpawnerType type;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private int tier;
    private int stackCount;
    private final Map<Material, Integer> storage = new EnumMap<>(Material.class);
    private int storedXp;
    private long lastSpawnMillis;

    public VirtualSpawner(int id, String ownerUsername, VirtualSpawnerType type,
                          String worldName, int x, int y, int z,
                          int tier, int stackCount, int storedXp) {
        this.id = id;
        this.ownerUsername = ownerUsername;
        this.type = type;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tier = tier;
        this.stackCount = stackCount;
        this.storedXp = storedXp;
        this.lastSpawnMillis = System.currentTimeMillis();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getOwnerUsername() { return ownerUsername; }
    public VirtualSpawnerType getType() { return type; }
    public String getWorldName() { return worldName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public int getTier() { return tier; }
    public void setTier(int tier) { this.tier = tier; }

    public int getStackCount() { return stackCount; }
    public void setStackCount(int stackCount) { this.stackCount = stackCount; }

    public Map<Material, Integer> getStorage() { return storage; }

    public int getStoredXp() { return storedXp; }
    public void setStoredXp(int storedXp) { this.storedXp = storedXp; }
    public void addStoredXp(int amount) {
        this.storedXp = Math.min(VirtualSpawnerType.MAX_STORED_XP, this.storedXp + amount);
    }

    public long getLastSpawnMillis() { return lastSpawnMillis; }
    public void setLastSpawnMillis(long lastSpawnMillis) { this.lastSpawnMillis = lastSpawnMillis; }

    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    public String locKey() {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    public void addToStorage(Material material, int amount) {
        int current = storage.getOrDefault(material, 0);
        int newTotal = Math.min(VirtualSpawnerType.MAX_STORAGE_PER_MATERIAL, current + amount);
        storage.put(material, newTotal);
    }

    public boolean isStorageFullFor(Material material) {
        return storage.getOrDefault(material, 0) >= VirtualSpawnerType.MAX_STORAGE_PER_MATERIAL;
    }

    public int totalStorageCount() {
        int total = 0;
        for (int v : storage.values()) total += v;
        return total;
    }

    public void clearStorage() { storage.clear(); }
}
