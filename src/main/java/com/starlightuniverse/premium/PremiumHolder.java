package com.starlightuniverse.premium;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class PremiumHolder implements InventoryHolder {

    public enum Type {
        RANK_OVERVIEW, RANK_BUY, TRASH, TRAIL_SELECT, HEAD_DATABASE
    }

    private final Type type;
    private Inventory inventory;
    private int selectedRank;

    public PremiumHolder(Type type) {
        this.type = type;
    }

    public Type getType() { return type; }
    public int getSelectedRank() { return selectedRank; }
    public void setSelectedRank(int rank) { this.selectedRank = rank; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv) { this.inventory = inv; }
}
