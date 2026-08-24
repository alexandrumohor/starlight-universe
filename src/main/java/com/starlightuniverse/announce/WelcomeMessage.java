package com.starlightuniverse.announce;

import com.starlightuniverse.StarlightUniverse;
import com.starlightuniverse.hottime.HotTimeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class WelcomeMessage {

    public static final String MOTD_LINE_1 = "Welcome to Starlight Universe";
    public static final String MOTD_LINE_2 = "Survival • Economy • PvP • Bosses • Jobs • Crates";

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor ORANGE = TextColor.color(0xFF8C00);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);

    private WelcomeMessage() {}

    public static void send(Player player) {
        StarlightUniverse plugin = StarlightUniverse.getInstance();

        Component sep = Component.text("─".repeat(48), GRAY);
        player.sendMessage(sep);

        player.sendMessage(Component.text("  ★ ", GOLD, TextDecoration.BOLD)
                .append(Component.text(MOTD_LINE_1, GOLD, TextDecoration.BOLD))
                .append(Component.text(" ★", GOLD, TextDecoration.BOLD)));
        player.sendMessage(Component.text("  " + MOTD_LINE_2, ORANGE));

        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  Players online: ", GRAY)
                .append(Component.text(online + " / " + max, WHITE, TextDecoration.BOLD)));

        HotTimeManager ht = plugin.getHotTimeManager();
        AnnouncementManager am = plugin.getAnnouncementManager();

        boolean anyEvent = false;
        if (ht != null && ht.isActive()) {
            anyEvent = true;
            int remaining = ht.getRemainingSeconds();
            int m = remaining / 60;
            int s = remaining % 60;
            String time = String.format("%d:%02d", m, s);
            player.sendMessage(Component.text("  Active event: ", GRAY)
                    .append(Component.text("HOT TIME ", RED, TextDecoration.BOLD))
                    .append(Component.text(String.format("x%.2f ", ht.getRawMultiplier()), GOLD, TextDecoration.BOLD))
                    .append(Component.text("— " + time + " left", YELLOW)));
        }

        if (am != null) {
            for (Announcement a : am.getAll()) {
                if (a.isEnabled() && a.getType() == AnnouncementType.EVENT) {
                    anyEvent = true;
                    player.sendMessage(Component.text("  Active event: ", GRAY)
                            .append(Component.text(a.getMessage(), a.getType().getColor())));
                    break;
                }
            }
        }

        if (!anyEvent) {
            player.sendMessage(Component.text("  No active events. Have fun!", CYAN));
        }

        player.sendMessage(sep);
    }
}
