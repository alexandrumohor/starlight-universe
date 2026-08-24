package com.starlightuniverse.mob;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class BlacksmithHolder implements InventoryHolder {
    private Inventory inventory;
    public void setInventory(Inventory inv) { this.inventory = inv; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
