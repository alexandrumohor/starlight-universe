package com.starlightuniverse.home;

public class Protection {

    private final int id;
    private final String owner;
    private final String world;
    private final int centerX;
    private final int centerZ;
    private int radius;

    public Protection(int id, String owner, String world, int centerX, int centerZ, int radius) {
        this.id = id;
        this.owner = owner;
        this.world = world;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
    }

    public int getId() { return id; }
    public String getOwner() { return owner; }
    public String getWorld() { return world; }
    public int getCenterX() { return centerX; }
    public int getCenterZ() { return centerZ; }
    public int getRadius() { return radius; }
    public void setRadius(int radius) { this.radius = radius; }

    public int getMinX() { return centerX - radius; }
    public int getMaxX() { return centerX + radius; }
    public int getMinZ() { return centerZ - radius; }
    public int getMaxZ() { return centerZ + radius; }

    public boolean contains(String world, int x, int z) {
        return this.world.equals(world)
                && x >= getMinX() && x <= getMaxX()
                && z >= getMinZ() && z <= getMaxZ();
    }

    public boolean overlaps(Protection other) {
        if (!this.world.equals(other.world)) return false;
        return this.getMinX() <= other.getMaxX() && this.getMaxX() >= other.getMinX()
                && this.getMinZ() <= other.getMaxZ() && this.getMaxZ() >= other.getMinZ();
    }

    public int getSizeLevel() {
        for (int i = 0; i < HomeManager.EXPAND_RADII.length; i++) {
            if (radius <= HomeManager.EXPAND_RADII[i]) return i;
        }
        return HomeManager.EXPAND_RADII.length - 1;
    }
}
