package com.starlightuniverse.announce;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class AnnouncementHolder implements InventoryHolder {

    public enum Type { LIST, EDIT, TYPE_PICKER }

    private final Type type;
    private final int announcementId;
    private Inventory inventory;

    public AnnouncementHolder(Type type, int announcementId) {
        this.type = type;
        this.announcementId = announcementId;
    }

    public Type getType() { return type; }
    public int getAnnouncementId() { return announcementId; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
