package com.starlightuniverse.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

public final class Msg {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);

    private Msg() {}

    public static Component prefix() {
        return Component.text("[SU] ", GOLD);
    }

    public static void error(Player player, String message) {
        player.sendMessage(prefix().append(Component.text(message, RED)));
    }

    public static void success(Player player, String message) {
        player.sendMessage(prefix().append(Component.text(message, GREEN)));
    }

    public static void info(Player player, String message) {
        player.sendMessage(prefix().append(Component.text(message, YELLOW)));
    }

    public static void gray(Player player, String message) {
        player.sendMessage(prefix().append(Component.text(message, GRAY)));
    }

    public static void cyan(Player player, String message) {
        player.sendMessage(prefix().append(Component.text(message, CYAN)));
    }

    public static Component errorComponent(String message) {
        return prefix().append(Component.text(message, RED));
    }
}
