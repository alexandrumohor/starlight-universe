package com.starlightuniverse.announce;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Animated MOTD with a flowing nether-star gold gradient.
 * Each server list ping shifts the gradient by one frame so it appears to move.
 */
public class MotdListener implements Listener {

    // Nether-star gradient stops (deep amber → rich gold → pale gold → ivory → white)
    private static final int[][] NETHER_STAR_GRADIENT = {
            {0xB0, 0x88, 0x00},
            {0xFF, 0xAA, 0x00},
            {0xFF, 0xD7, 0x00},
            {0xF5, 0xF2, 0xC6},
            {0xFF, 0xFF, 0xEE},
            {0xF5, 0xF2, 0xC6},
            {0xFF, 0xD7, 0x00},
            {0xFF, 0xAA, 0x00}
    };

    private static final int TOTAL_FRAMES = 60;
    private static final String LINE_1 = "STARLIGHT UNIVERSE";
    private static final String LINE_2 = "SURVIVAL • JOBS • SKILLS • ENCHANTS • CRATES • HOMES • ARENAS";

    private final AtomicInteger frame = new AtomicInteger(0);

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        float offset = (float) frame.getAndUpdate(f -> (f + 1) % TOTAL_FRAMES) / TOTAL_FRAMES;

        Component line1 = buildGradient(LINE_1, NETHER_STAR_GRADIENT, offset);
        Component line2 = buildGradient(LINE_2, NETHER_STAR_GRADIENT, offset);

        // Center-ish padding (MC MOTD is ~45 chars visible wide with default font)
        event.motd(
                Component.text("             ").append(line1)
                        .append(Component.newline())
                        .append(Component.text("  ")).append(line2)
        );
    }

    private Component buildGradient(String text, int[][] stops, float offset) {
        Component result = Component.empty();
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if (ch == ' ') {
                // Preserve spaces without color (avoids stripping issues on some clients)
                result = result.append(Component.text(" "));
                continue;
            }
            float base = len > 1 ? (float) i / (len - 1) : 0f;
            float progress = (base + offset) % 1.0f;
            int[] rgb = interpolate(progress, stops);
            result = result.append(
                    Component.text(String.valueOf(ch),
                            TextColor.color(rgb[0], rgb[1], rgb[2]))
                            .decoration(TextDecoration.BOLD, true));
        }
        return result;
    }

    private int[] interpolate(float progress, int[][] stops) {
        int segments = stops.length - 1;
        float scaled = progress * segments;
        int idx = Math.min((int) scaled, segments - 1);
        float local = scaled - idx;

        int[] from = stops[idx];
        int[] to = stops[idx + 1];

        return new int[]{
                Math.round(from[0] + (to[0] - from[0]) * local),
                Math.round(from[1] + (to[1] - from[1]) * local),
                Math.round(from[2] + (to[2] - from[2]) * local)
        };
    }
}
