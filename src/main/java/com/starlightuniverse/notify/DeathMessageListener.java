package com.starlightuniverse.notify;

import com.starlightuniverse.scoreboard.ScoreboardManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathMessageListener implements Listener {

    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Component vanilla = event.deathMessage();
        if (vanilla == null) return;

        Component icon = Component.text(ScoreboardManager.ICON_DEATHS + " ", RED);
        event.deathMessage(icon.append(vanilla.color(WHITE)));
    }
}
