package com.starlightuniverse.voucher;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class EnchantRemoverConfirmHolder implements InventoryHolder {

    static final int YES_SLOT = 11;
    static final int ICON_SLOT = 13;
    static final int NO_SLOT = 15;

    private Inventory inventory;
    private final int targetSlot;
    private final boolean isCustom;
    private final String enchantKey;
    private final Component enchantDisplayName;

    public EnchantRemoverConfirmHolder(int targetSlot, boolean isCustom, String enchantKey, Component enchantDisplayName) {
        this.targetSlot = targetSlot;
        this.isCustom = isCustom;
        this.enchantKey = enchantKey;
        this.enchantDisplayName = enchantDisplayName;
    }

    public int getTargetSlot() { return targetSlot; }
    public boolean isCustom() { return isCustom; }
    public String getEnchantKey() { return enchantKey; }
    public Component getEnchantDisplayName() { return enchantDisplayName; }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inv) { this.inventory = inv; }
}
