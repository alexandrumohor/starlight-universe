package com.starlightuniverse.shop;

import org.bukkit.Material;

public record ShopItem(Material material, int price, ShopCategory category) {}
