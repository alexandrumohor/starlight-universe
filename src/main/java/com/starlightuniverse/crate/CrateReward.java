package com.starlightuniverse.crate;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

public record CrateReward(
        String name,
        Material displayMaterial,
        int displayAmount,
        String rarity,
        TextColor rarityColor,
        double weight,
        Consumer<Player> giveAction
) {}
