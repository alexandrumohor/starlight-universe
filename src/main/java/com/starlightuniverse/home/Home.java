package com.starlightuniverse.home;

public class Home {

    private final int id;
    private final String username;
    private final int number;
    private String name;
    private final String world;
    private final double x, y, z;
    private final float yaw, pitch;
    private String iconMaterial;

    public Home(int id, String username, int number, String name, String world,
                double x, double y, double z, float yaw, float pitch, String iconMaterial) {
        this.id = id;
        this.username = username;
        this.number = number;
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.iconMaterial = iconMaterial;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public int getNumber() { return number; }
    public String getName() { return name; }
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public String getIconMaterial() { return iconMaterial; }

    public void setName(String name) { this.name = name; }
    public void setIconMaterial(String iconMaterial) { this.iconMaterial = iconMaterial; }

    public String getDisplayName() {
        return name != null ? name : "Home #" + number;
    }
}
