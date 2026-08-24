package com.starlightuniverse.pack;

import com.starlightuniverse.util.Msg;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public final class ResourcePackListener implements Listener {

    private final ResourcePackManager manager;

    public ResourcePackListener(ResourcePackManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.sendTo(event.getPlayer());
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        switch (event.getStatus()) {
            case DECLINED -> Msg.error(event.getPlayer(),
                    "Ai nevoie de resource pack pentru a juca. Reconnectează și acceptă.");
            case FAILED_DOWNLOAD -> Msg.error(event.getPlayer(),
                    "Descărcarea pack-ului a eșuat. Reconnectează.");
            case FAILED_RELOAD -> Msg.error(event.getPlayer(),
                    "Pack-ul nu s-a putut încărca. Reconnectează.");
            case SUCCESSFULLY_LOADED -> Msg.success(event.getPlayer(),
                    "Resource pack încărcat.");
            default -> {
            }
        }
    }
}
