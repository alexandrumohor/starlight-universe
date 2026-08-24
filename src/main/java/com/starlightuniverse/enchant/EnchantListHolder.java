package com.starlightuniverse.enchant;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class EnchantListHolder implements InventoryHolder {

    private final EnchantManager manager;
    private Inventory inventory;

    public EnchantListHolder(EnchantManager manager) {
        this.manager = manager;
    }

    public EnchantManager getManager() { return manager; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
