package com.starlightuniverse.pack;

import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public final class ResourcePackListener implements Listener {

    private final ResourcePackManager manager;
    private final PlayerHeadPackManager headManager;

    public ResourcePackListener(ResourcePackManager manager, PlayerHeadPackManager headManager) {
        this.manager = manager;
        this.headManager = headManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Pack push now happens in ConfigPhasePackListener BEFORE the player
        // spawns in the world, so the client shows the download dialog on the
        // initial connect loading screen instead of reloading after join.
        // This handler stays only as a safety net for edge cases where the
        // config-phase event doesn't fire.
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        switch (event.getStatus()) {
            case DECLINED -> event.getPlayer().kick(kickComponent(
                    "Ai refuzat resource pack-ul.",
                    "Serverul are nevoie de pack-ul custom ca sa functioneze corect.",
                    "Reconnecteaza-te si accepta pack-ul pentru a intra."));
            case FAILED_DOWNLOAD -> event.getPlayer().kick(kickComponent(
                    "Descarcarea pack-ului a esuat.",
                    "Verifica conexiunea la internet si reconnecteaza-te.",
                    ""));
            case FAILED_RELOAD -> event.getPlayer().kick(kickComponent(
                    "Pack-ul nu s-a putut incarca in client.",
                    "Restart la Minecraft (fara sa lasi clientul deschis) si reconnecteaza-te.",
                    ""));
            case SUCCESSFULLY_LOADED -> { /* silent success */ }
            default -> {
            }
        }
    }

    private static Component kickComponent(String title, String line1, String line2) {
        Component c = Component.text("[SU] ", TextColor.color(0xFFD700))
                .append(Component.text(title + "\n\n", TextColor.color(0xFF5555)))
                .append(Component.text(line1 + "\n", TextColor.color(0xFFFFFF)));
        if (!line2.isEmpty()) {
            c = c.append(Component.text(line2, TextColor.color(0xAAAAAA)));
        }
        return c;
    }
}
