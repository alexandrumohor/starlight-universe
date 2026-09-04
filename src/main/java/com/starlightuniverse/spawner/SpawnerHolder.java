package com.starlightuniverse.spawner;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class SpawnerHolder implements InventoryHolder {

    public enum Type { SHOP, MANAGE_MENU, STORAGE }

    private final Type type;
    private final int spawnerId;
    private int page;
    private Inventory inventory;

    public SpawnerHolder(Type type, int spawnerId) {
        this.type = type;
        this.spawnerId = spawnerId;
        this.page = 0;
    }

    public SpawnerHolder(Type type, int spawnerId, int page) {
        this.type = type;
        this.spawnerId = spawnerId;
        this.page = page;
    }

    public Type getType() { return type; }
    public int getSpawnerId() { return spawnerId; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
