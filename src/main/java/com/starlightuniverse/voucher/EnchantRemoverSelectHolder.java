package com.starlightuniverse.voucher;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public class EnchantRemoverSelectHolder implements InventoryHolder {

    private Inventory inventory;
    private final int targetSlot;
    private final List<EnchantEntry> enchants;

    public EnchantRemoverSelectHolder(int targetSlot, List<EnchantEntry> enchants) {
        this.targetSlot = targetSlot;
        this.enchants = enchants;
    }

    public int getTargetSlot() { return targetSlot; }

    public List<EnchantEntry> getEnchants() { return enchants; }

    public EnchantEntry getEnchant(int guiSlot) {
        if (guiSlot < 0 || guiSlot >= enchants.size()) return null;
        return enchants.get(guiSlot);
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void setInventory(Inventory inv) { this.inventory = inv; }

    public record EnchantEntry(boolean isCustom, String key) {}
}
