package com.starlightuniverse.crate;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

public record CrateReward(
        String name,
        Material displayMaterial,
        int displayAmount,
        String rarity,
        TextColor rarityColor,
        double weight,
        Consumer<Player> giveAction,
        NamespacedKey displayModel
) {
    public CrateReward(String name, Material displayMaterial, int displayAmount,
                       String rarity, TextColor rarityColor, double weight,
                       Consumer<Player> giveAction) {
        this(name, displayMaterial, displayAmount, rarity, rarityColor, weight, giveAction, null);
    }
}
