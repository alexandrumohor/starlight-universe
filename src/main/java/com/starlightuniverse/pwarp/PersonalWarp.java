package com.starlightuniverse.pwarp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class PersonalWarp {

    private final int id;
    private final String owner;
    private String name;
    private final String worldName;
    private final double x, y, z;
    private final float yaw, pitch;
    private String category;
    private String description;
    private double entryCost;
    private int visitors;
    private long createdMillis;

    private boolean allowPvp;
    private boolean allowBreak;
    private boolean allowPlace;
    private boolean allowContainers;
    private boolean allowInteract;

    public PersonalWarp(int id, String owner, String name, String worldName,
                        double x, double y, double z, float yaw, float pitch,
                        String category, String description, double entryCost, int visitors,
                        boolean allowPvp, boolean allowBreak, boolean allowPlace,
                        boolean allowContainers, boolean allowInteract) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.category = category;
        this.description = description;
        this.entryCost = entryCost;
        this.visitors = visitors;
        this.allowPvp = allowPvp;
        this.allowBreak = allowBreak;
        this.allowPlace = allowPlace;
        this.allowContainers = allowContainers;
        this.allowInteract = allowInteract;
    }

    public int getId() { return id; }
    public String getOwner() { return owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getWorldName() { return worldName; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getEntryCost() { return entryCost; }
    public void setEntryCost(double entryCost) { this.entryCost = entryCost; }

    public int getVisitors() { return visitors; }
    public void setVisitors(int visitors) { this.visitors = visitors; }
    public void incrementVisitors() { this.visitors++; }

    public long getCreatedMillis() { return createdMillis; }
    public void setCreatedMillis(long createdMillis) { this.createdMillis = createdMillis; }

    public boolean isAllowPvp() { return allowPvp; }
    public void setAllowPvp(boolean v) { allowPvp = v; }
    public boolean isAllowBreak() { return allowBreak; }
    public void setAllowBreak(boolean v) { allowBreak = v; }
    public boolean isAllowPlace() { return allowPlace; }
    public void setAllowPlace(boolean v) { allowPlace = v; }
    public boolean isAllowContainers() { return allowContainers; }
    public void setAllowContainers(boolean v) { allowContainers = v; }
    public boolean isAllowInteract() { return allowInteract; }
    public void setAllowInteract(boolean v) { allowInteract = v; }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean containsBlock(String world, int bx, int bz) {
        if (!worldName.equals(world)) return false;
        int cx = (int) x;
        int cz = (int) z;
        int r = PWarpManager.PROTECTION_RADIUS;
        return Math.abs(bx - cx) <= r && Math.abs(bz - cz) <= r;
    }
}
