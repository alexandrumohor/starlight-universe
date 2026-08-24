package com.starlightuniverse.enchant;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum ItemTarget {

    HELMET, CHESTPLATE, LEGGINGS, BOOTS,
    SWORD, PICKAXE, AXE, SHOVEL, HOE,
    BOW, MACE, TRIDENT, SPEAR;

    public boolean matches(ItemStack item) {
        if (item == null) return false;
        Material mat = item.getType();
        return switch (this) {
            case HELMET -> mat.name().endsWith("_HELMET") || mat == Material.TURTLE_HELMET;
            case CHESTPLATE -> mat.name().endsWith("_CHESTPLATE") || mat == Material.ELYTRA;
            case LEGGINGS -> mat.name().endsWith("_LEGGINGS");
            case BOOTS -> mat.name().endsWith("_BOOTS");
            case SWORD -> mat.name().endsWith("_SWORD");
            case PICKAXE -> mat.name().endsWith("_PICKAXE");
            case AXE -> mat.name().endsWith("_AXE") && !mat.name().endsWith("_PICKAXE");
            case SHOVEL -> mat.name().endsWith("_SHOVEL");
            case HOE -> mat.name().endsWith("_HOE");
            case BOW -> mat == Material.BOW;
            case MACE -> mat == Material.MACE;
            case TRIDENT -> mat == Material.TRIDENT;
            case SPEAR -> false;
        };
    }

    public String getDisplayName() {
        return switch (this) {
            case HELMET -> "Helmet";
            case CHESTPLATE -> "Chestplate";
            case LEGGINGS -> "Leggings";
            case BOOTS -> "Boots";
            case SWORD -> "Sword";
            case PICKAXE -> "Pickaxe";
            case AXE -> "Axe";
            case SHOVEL -> "Shovel";
            case HOE -> "Hoe";
            case BOW -> "Bow";
            case MACE -> "Mace";
            case TRIDENT -> "Trident";
            case SPEAR -> "Spear";
        };
    }
}
