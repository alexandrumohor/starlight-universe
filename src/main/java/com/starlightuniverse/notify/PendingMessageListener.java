package com.starlightuniverse.notify;

import com.starlightuniverse.StarlightUniverse;
import com.starlightuniverse.auth.AuthManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PendingMessageListener implements Listener {

    private final PendingMessageManager pending;
    private final AuthManager authManager;

    public PendingMessageListener(PendingMessageManager pending, AuthManager authManager) {
        this.pending = pending;
        this.authManager = authManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Wait until the player is authenticated so the notification doesn't
        // land mid-auth screen. Retry every second for up to ~60s.
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                if (authManager.isAuthenticated(player.getUniqueId())) {
                    pending.flush(player);
                    cancel();
                    return;
                }
                if (++ticks >= 60) cancel();
            }
        }.runTaskTimer(StarlightUniverse.getInstance(), 20L, 20L);
    }
}
