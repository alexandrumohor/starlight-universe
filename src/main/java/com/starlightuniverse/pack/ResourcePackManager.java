package com.starlightuniverse.pack;

import com.starlightuniverse.StarlightUniverse;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.UUID;

public final class ResourcePackManager {

    private static final UUID PACK_UUID = UUID.fromString("534c1717-5555-5555-5555-5354415250");
    private static final boolean REQUIRED = true;
    private static final Component PROMPT =
            Component.text("Starlight Universe cere resource pack-ul custom.\n", NamedTextColor.GOLD)
                    .append(Component.text("Alege ", NamedTextColor.WHITE))
                    .append(Component.text("[Yes]", NamedTextColor.GREEN))
                    .append(Component.text(" ca sa descarci si intri, sau ", NamedTextColor.WHITE))
                    .append(Component.text("[No]", NamedTextColor.RED))
                    .append(Component.text(" ca sa te deconectezi.", NamedTextColor.WHITE));

    private static final String PACK_HOST_OVERRIDE = "";

    private final StarlightUniverse plugin;
    private final PackServer server;

    public ResourcePackManager(StarlightUniverse plugin, PackServer server) {
        this.plugin = plugin;
        this.server = server;
    }

    public void sendTo(Player player) {
        if (!server.isReady()) return;

        String host = resolveHost(player);
        if (host == null || host.isBlank()) {
            plugin.getLogger().warning("[SU] Cannot send pack to " + player.getName()
                    + " — no reachable host detected.");
            return;
        }

        String url = server.buildUrl(host);
        try {
            player.setResourcePack(PACK_UUID, url, server.getPackHash(), PROMPT, REQUIRED);
        } catch (Throwable t) {
            plugin.getLogger().warning("[SU] Failed to send pack to " + player.getName()
                    + ": " + t.getMessage());
        }
    }

    private String resolveHost(Player player) {
        if (!PACK_HOST_OVERRIDE.isBlank()) return PACK_HOST_OVERRIDE;

        InetSocketAddress virtual = player.getVirtualHost();
        if (virtual != null) {
            String vh = virtual.getHostString();
            if (vh != null && !vh.isBlank()) return vh;
        }

        String bind = plugin.getServer().getIp();
        if (bind != null && !bind.isBlank()) return bind;

        return "127.0.0.1";
    }
}
