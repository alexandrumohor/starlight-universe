package com.starlightuniverse.cosmetic;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class TrailHolder implements InventoryHolder {

    private Inventory inventory;
    private boolean scrollMode;

    public void setInventory(Inventory inventory) { this.inventory = inventory; }
    public void setScrollMode(boolean scrollMode) { this.scrollMode = scrollMode; }
    public boolean isScrollMode() { return scrollMode; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
