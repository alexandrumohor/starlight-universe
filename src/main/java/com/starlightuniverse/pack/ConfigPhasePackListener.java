package com.starlightuniverse.pack;

import com.starlightuniverse.StarlightUniverse;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Pushes the resource pack DURING the configuration phase (before the player
 * spawns in the world). This makes the "downloading resource pack" screen appear
 * on the initial connect loading — no second reload after entering the world.
 */
public final class ConfigPhasePackListener implements Listener {

    private final StarlightUniverse plugin;
    private final PackServer mainPack;
    private final PlayerHeadPackManager headManager;

    public ConfigPhasePackListener(StarlightUniverse plugin, PackServer mainPack,
                                    PlayerHeadPackManager headManager) {
        this.plugin = plugin;
        this.mainPack = mainPack;
        this.headManager = headManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInitialConfigure(PlayerConnectionInitialConfigureEvent event) {
        plugin.getLogger().info("[SU][pack] INITIAL config event for "
                + event.getConnection().getProfile().getName());
        pushPackTo(event.getConnection());
    }

    private void pushPackTo(PlayerConfigurationConnection conn) {
        if (conn == null || mainPack == null || !mainPack.isReady()) {
            plugin.getLogger().warning("[SU][pack] skipped push: conn=" + (conn != null)
                    + " mainPack=" + (mainPack != null)
                    + " ready=" + (mainPack != null && mainPack.isReady()));
            return;
        }

        String username = conn.getProfile().getName();
        UUID playerUuid = conn.getProfile().getId();
        String host = resolveHost(conn);
        if (host == null) return;

        // Try to build a merged pack (main pack with the player's head baked in) so
        // the client sees ONE pack (1/1 loading), not two stacked (2/2).
        byte[] mergedHash = null;
        String packUrl;
        try {
            var auth = plugin.getAuthManager();
            String premiumUuid = auth != null ? auth.getPremiumUuid(username) : null;
            if (premiumUuid != null) {
                var skinMgr = plugin.getSkinManager();
                var skin = skinMgr != null ? skinMgr.fetchMojangSkin(premiumUuid) : null;
                if (skin != null) {
                    byte[] headPng = HeadExtractor.renderHeadFromTexturesValue(skin.value());
                    if (headPng != null) {
                        byte[] mergedZip = HeadExtractor.buildMergedPack(mainPack.getPackBytes(), headPng);
                        mergedHash = sha1(mergedZip);
                        headManager.putMergedCache(playerUuid, mergedZip, mergedHash);
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[SU] Config-phase merged pack build failed for "
                    + username + ": " + t.getMessage());
        }

        String packHash;
        if (mergedHash != null) {
            packUrl = "http://" + host + ":" + PlayerHeadPackManager.HEAD_PACK_PORT
                    + PlayerHeadPackManager.MERGED_PATH_PREFIX + playerUuid + ".zip";
            packHash = HexFormat.of().formatHex(mergedHash);
        } else {
            // Fallback to shared main pack — new accounts before /register.
            packUrl = mainPack.buildUrl(host);
            packHash = mainPack.getPackHashHex();
        }
        // Random pack ID per push — makes MC show the "Downloading pack" dialog on
        // every connect (even after server restart), since client no longer sees
        // it as the same pack it had loaded before. SHA is still stable, so the
        // actual file is served from cache — no real download work.
        UUID packId = UUID.randomUUID();

        try {
            ResourcePackInfo pack = ResourcePackInfo.resourcePackInfo()
                    .id(packId)
                    .uri(URI.create(packUrl))
                    .hash(packHash)
                    .build();
            ResourcePackRequest req = ResourcePackRequest.resourcePackRequest()
                    .packs(pack)
                    .required(true)
                    .replace(true)
                    .prompt(Component.text(
                            "Starlight Universe cere resource pack-ul custom.",
                            NamedTextColor.GOLD))
                    .build();
            conn.getAudience().sendResourcePacks(req);
            headManager.markConfigPhaseSent(playerUuid);
            plugin.getLogger().info("[SU][pack] pushed at config phase for " + username
                    + (mergedHash != null ? " (merged per-player pack)" : " (shared main pack fallback)"));
        } catch (Throwable t) {
            plugin.getLogger().warning("[SU] Config-phase pack push failed for "
                    + username + ": " + t.getMessage());
        }
    }

    private String resolveHost(PlayerConfigurationConnection conn) {
        // Config-phase connection doesn't expose client address; use bound IP or localhost.
        String bind = plugin.getServer().getIp();
        if (bind != null && !bind.isBlank()) return bind;
        return "127.0.0.1";
    }

    private static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (Exception e) {
            return new byte[20];
        }
    }
}
