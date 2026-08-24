package com.starlightuniverse.crate;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class CrateHolder implements InventoryHolder {

    private final CrateType crateType;
    private Inventory inventory;

    public CrateHolder(CrateType crateType) {
        this.crateType = crateType;
    }

    public CrateType getCrateType() { return crateType; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
