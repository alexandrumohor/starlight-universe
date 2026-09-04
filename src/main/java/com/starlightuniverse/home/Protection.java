package com.starlightuniverse.home;

public class Protection {

    private final int id;
    private final String owner;
    private final String world;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;

    public Protection(int id, String owner, String world, int minX, int minZ, int maxX, int maxZ) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    public int getId() { return id; }
    public String getOwner() { return owner; }
    public String getWorld() { return world; }
    public int getMinX() { return minX; }
    public int getMaxX() { return maxX; }
    public int getMinZ() { return minZ; }
    public int getMaxZ() { return maxZ; }

    public int getArea() {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    public int getWidth() { return maxX - minX + 1; }
    public int getLength() { return maxZ - minZ + 1; }

    public boolean contains(String world, int x, int z) {
        return this.world.equals(world)
                && x >= minX && x <= maxX
                && z >= minZ && z <= maxZ;
    }

    public boolean overlaps(Protection other) {
        if (!this.world.equals(other.world)) return false;
        return this.minX <= other.maxX && this.maxX >= other.minX
                && this.minZ <= other.maxZ && this.maxZ >= other.minZ;
    }
}
