package com.starlightuniverse.chestshop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class ChestShopHolder implements InventoryHolder {

    public enum Type {
        MENU,
        BANK,
        FIND_ITEM
    }

    private final Type type;
    private Inventory inventory;
    private int page;
    private int shopId = -1;

    public ChestShopHolder(Type type) {
        this.type = type;
    }

    public Type getType() { return type; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getShopId() { return shopId; }
    public void setShopId(int shopId) { this.shopId = shopId; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
