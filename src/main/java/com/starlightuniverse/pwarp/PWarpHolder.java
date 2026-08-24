package com.starlightuniverse.pwarp;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class PWarpHolder implements InventoryHolder {

    public enum Type {
        BROWSE,        // /pwarps main list (with pagination + category filter)
        MY_WARPS,      // /pwarp mine (list of your warps)
        SETTINGS,      // per-warp settings (perms + category + cost + description)
        CATEGORY_PICK, // pick a category
        RATE           // rate 1-5
    }

    private final Type type;
    private Inventory inventory;
    private int pwarpId = -1;
    private String category = null;
    private int page = 0;
    private PWarpManager.Sort sort = PWarpManager.Sort.BEST_RATING;

    public PWarpHolder(Type type) { this.type = type; }

    public Type getType() { return type; }

    public Inventory getInv() { return inventory; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    public int getPwarpId() { return pwarpId; }
    public void setPwarpId(int pwarpId) { this.pwarpId = pwarpId; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public PWarpManager.Sort getSort() { return sort; }
    public void setSort(PWarpManager.Sort sort) { this.sort = sort; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
