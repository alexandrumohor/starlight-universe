package com.starlightuniverse.home;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class HomeHolder implements InventoryHolder {

    public enum Type {
        HOMES_LIST, HOME_MANAGE, ICON_SELECT,
        PROTECT_MAIN, PROTECT_MEMBERS, PROTECT_EXPAND, PROTECT_LOGS,
        BUY_HOME_SLOT
    }

    private final Type type;
    private Inventory inventory;
    private int selectedHome;
    private int protectionId;
    private int page;

    public HomeHolder(Type type) {
        this.type = type;
    }

    public Type getType() { return type; }

    public int getSelectedHome() { return selectedHome; }
    public void setSelectedHome(int selectedHome) { this.selectedHome = selectedHome; }

    public int getProtectionId() { return protectionId; }
    public void setProtectionId(int protectionId) { this.protectionId = protectionId; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }
}
