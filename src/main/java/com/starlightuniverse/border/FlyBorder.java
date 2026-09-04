package com.starlightuniverse.border;

public record FlyBorder(int id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public boolean contains(String worldName, int x, int y, int z) {
        return this.world.equals(worldName)
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public int volume() {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
