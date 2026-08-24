package com.starlightuniverse.enchant;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class AlchemistHolder implements InventoryHolder {

    public static final int INPUT_SLOT_1 = 10;
    public static final int INPUT_SLOT_2 = 14;
    public static final int PLUS_SLOT = 12;
    public static final int ARROW_SLOT = 16;
    public static final int RESULT_SLOT = 22;
    public static final int INFO_SLOT = 4;

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public boolean isInputSlot(int slot) {
        return slot == INPUT_SLOT_1 || slot == INPUT_SLOT_2;
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
