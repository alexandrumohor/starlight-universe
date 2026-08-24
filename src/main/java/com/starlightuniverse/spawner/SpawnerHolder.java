package com.starlightuniverse.spawner;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class SpawnerHolder implements InventoryHolder {

    public enum Type { SHOP, MANAGE }

    private final Type type;
    private final int spawnerId;
    private Inventory inventory;

    public SpawnerHolder(Type type, int spawnerId) {
        this.type = type;
        this.spawnerId = spawnerId;
    }

    public Type getType() { return type; }
    public int getSpawnerId() { return spawnerId; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
