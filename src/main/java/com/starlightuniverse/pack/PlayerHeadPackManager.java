package com.starlightuniverse.pack;

import com.starlightuniverse.StarlightUniverse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerHeadPackManager {

    public static final int HEAD_PACK_PORT = 25567;
    private static final String PATH_PREFIX = "/head-";
    private static final String PATH_SUFFIX = ".zip";
    public static final String MERGED_PATH_PREFIX = "/pack-";

    private final StarlightUniverse plugin;
    private final PackServer mainPack;
    private HttpServer server;

    // Cache: uuid → {zipBytes, sha1}
    private final Map<UUID, CachedPack> cache = new ConcurrentHashMap<>();

    public PlayerHeadPackManager(StarlightUniverse plugin, PackServer mainPack) {
        this.plugin = plugin;
        this.mainPack = mainPack;
    }

    public boolean start() {
        try {
            server = HttpServer.create(new InetSocketAddress(HEAD_PACK_PORT), 0);
            server.createContext("/", this::handle);
            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("[SU] Head pack server started on port " + HEAD_PACK_PORT);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("[SU] Head pack server failed on port " + HEAD_PACK_PORT + ": " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        cache.clear();
        mergedCache.clear();
        configPhaseSent.clear();
    }

    // Second cache for MERGED packs (main + head in one zip)
    private final Map<UUID, CachedPack> mergedCache = new ConcurrentHashMap<>();

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path == null || !path.endsWith(PATH_SUFFIX)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        boolean merged = path.startsWith(MERGED_PATH_PREFIX);
        boolean overlay = path.startsWith(PATH_PREFIX);
        if (!merged && !overlay) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        String prefix = merged ? MERGED_PATH_PREFIX : PATH_PREFIX;
        String uuidStr = path.substring(prefix.length(), path.length() - PATH_SUFFIX.length());
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
        }
        CachedPack cached = merged ? mergedCache.get(uuid) : cache.get(uuid);
        if (cached == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.sendResponseHeaders(200, cached.bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(cached.bytes);
        }
    }

    /** Registers a pre-built merged pack (main + head baked in) for a player. */
    public void putMergedCache(UUID playerUuid, byte[] zipBytes, byte[] sha1) {
        mergedCache.put(playerUuid, new CachedPack(zipBytes, sha1));
    }

    /**
     * At-join combined push: main pack + head overlay in ONE ResourcePackRequest,
     * so the client only shows one loading prompt. Uses the stored premium_uuid
     * from DB to fetch the skin without waiting for auth. If no premium_uuid,
     * falls back to sending just the main pack via the supplied fallback runnable.
     */
    public void sendCombinedAtJoin(Player player, Runnable mainOnlyFallback) {
        if (mainPack == null || !mainPack.isReady()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var auth = plugin.getAuthManager();
            String uuid = auth != null ? auth.getPremiumUuid(player.getName()) : null;
            if (uuid == null) {
                // No stored premium UUID yet (first-ever join). Just push main pack;
                // head overlay will follow later after /register.
                Bukkit.getScheduler().runTask(plugin, mainOnlyFallback);
                return;
            }
            byte[] headPng;
            try {
                var skinMgr = plugin.getSkinManager();
                var skin = skinMgr != null ? skinMgr.fetchMojangSkin(uuid) : null;
                if (skin == null) {
                    Bukkit.getScheduler().runTask(plugin, mainOnlyFallback);
                    return;
                }
                headPng = HeadExtractor.renderHeadFromTexturesValue(skin.value());
                if (headPng == null) {
                    Bukkit.getScheduler().runTask(plugin, mainOnlyFallback);
                    return;
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[SU] Head extract at join failed for "
                        + player.getName() + ": " + t.getMessage());
                Bukkit.getScheduler().runTask(plugin, mainOnlyFallback);
                return;
            }
            byte[] overlayZip;
            byte[] overlayHash;
            try {
                overlayZip = HeadExtractor.buildOverlayPack(headPng);
                overlayHash = sha1(overlayZip);
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, mainOnlyFallback);
                return;
            }
            cache.put(player.getUniqueId(), new CachedPack(overlayZip, overlayHash));
            Bukkit.getScheduler().runTask(plugin, () -> pushBothStacked(player, overlayHash));
        });
    }

    private void pushBothStacked(Player player, byte[] overlayHash) {
        if (!player.isOnline()) return;
        String host = resolveHost(player);
        if (host == null) return;

        String mainUrl = mainPack.buildUrl(host);
        String headUrl = "http://" + host + ":" + HEAD_PACK_PORT + PATH_PREFIX + player.getUniqueId() + PATH_SUFFIX;

        try {
            ResourcePackInfo main = ResourcePackInfo.resourcePackInfo()
                    .id(java.util.UUID.fromString("534c1717-5555-5555-5555-5354415250"))
                    .uri(URI.create(mainUrl))
                    .hash(mainPack.getPackHashHex())
                    .build();
            ResourcePackInfo overlay = ResourcePackInfo.resourcePackInfo()
                    .id(overlayIdFor(player.getUniqueId()))
                    .uri(URI.create(headUrl))
                    .hash(HexFormat.of().formatHex(overlayHash))
                    .build();
            ResourcePackRequest req = ResourcePackRequest.resourcePackRequest()
                    .packs(main, overlay)
                    .required(true)
                    .replace(true)
                    .prompt(net.kyori.adventure.text.Component.text(
                            "Starlight Universe cere resource pack-ul.",
                            net.kyori.adventure.text.format.NamedTextColor.GOLD))
                    .build();
            player.sendResourcePacks(req);
        } catch (Throwable t) {
            plugin.getLogger().warning("[SU] Combined pack push failed for "
                    + player.getName() + ": " + t.getMessage());
        }
    }

    /**
     * Called after the player authenticates (and their skin has been applied).
     * Delays briefly so the skin is definitely on the profile, then extracts
     * the head and pushes an additive overlay pack on top of the main pack.
     */
    public void sendHeadOverlayAfterAuth(Player player) {
        // Config-phase listener bundles main + head overlay into a single dialog
        // at connection time, so this post-auth push is no longer needed. Left as
        // a no-op to keep existing call sites happy; enable only if config phase
        // failed and we still need to catch up.
    }

    private void buildAndSendOverlay(Player player) {
        if (!player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        byte[] headPng = HeadExtractor.extractHead(player);
        byte[] overlayZip;
        byte[] overlayHash;
        try {
            overlayZip = HeadExtractor.buildOverlayPack(headPng);
            overlayHash = sha1(overlayZip);
        } catch (Exception e) {
            plugin.getLogger().warning("[SU] Failed to build head pack for " + player.getName() + ": " + e.getMessage());
            return;
        }
        cache.put(uuid, new CachedPack(overlayZip, overlayHash));
        Bukkit.getScheduler().runTask(plugin, () -> pushOverlayOnly(player, overlayHash));
    }

    private void pushOverlayOnly(Player player, byte[] overlayHash) {
        if (!player.isOnline()) return;
        String host = resolveHost(player);
        if (host == null) return;
        String headUrl = "http://" + host + ":" + HEAD_PACK_PORT + PATH_PREFIX + player.getUniqueId() + PATH_SUFFIX;
        try {
            ResourcePackInfo overlay = ResourcePackInfo.resourcePackInfo()
                    .id(overlayIdFor(player.getUniqueId()))
                    .uri(URI.create(headUrl))
                    .hash(HexFormat.of().formatHex(overlayHash))
                    .build();
            // replace(false) = additive: keep the main pack loaded, layer this on top.
            ResourcePackRequest req = ResourcePackRequest.resourcePackRequest()
                    .packs(overlay)
                    .required(false)
                    .replace(false)
                    .build();
            player.sendResourcePacks(req);
        } catch (Throwable t) {
            plugin.getLogger().warning("[SU] Failed to push head overlay to " + player.getName() + ": " + t.getMessage());
        }
    }

    public void sendPacksTo(Player player) {
        if (mainPack == null || !mainPack.isReady()) return;
        UUID uuid = player.getUniqueId();

        // Build the overlay pack async (network + image decode), then push both packs from the main thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            byte[] headPng = HeadExtractor.extractHead(player);
            byte[] overlayZip;
            byte[] overlayHash;
            try {
                overlayZip = HeadExtractor.buildOverlayPack(headPng);
                overlayHash = sha1(overlayZip);
            } catch (Exception e) {
                plugin.getLogger().warning("[SU] Failed to build head pack for " + player.getName() + ": " + e.getMessage());
                return;
            }
            cache.put(uuid, new CachedPack(overlayZip, overlayHash));
            Bukkit.getScheduler().runTask(plugin, () -> pushPacks(player, overlayHash));
        });
    }

    private void pushPacks(Player player, byte[] overlayHash) {
        if (!player.isOnline()) return;

        String host = resolveHost(player);
        if (host == null) return;

        String mainUrl = mainPack.buildUrl(host);
        String headUrl = "http://" + host + ":" + HEAD_PACK_PORT + PATH_PREFIX + player.getUniqueId() + PATH_SUFFIX;

        try {
            ResourcePackInfo main = ResourcePackInfo.resourcePackInfo()
                    .id(UUID.fromString("534c1717-5555-5555-5555-5354415250"))
                    .uri(URI.create(mainUrl))
                    .hash(mainPack.getPackHashHex())
                    .build();
            // Overlay uses a UUID derived from the player UUID so client caches per-player
            ResourcePackInfo overlay = ResourcePackInfo.resourcePackInfo()
                    .id(overlayIdFor(player.getUniqueId()))
                    .uri(URI.create(headUrl))
                    .hash(HexFormat.of().formatHex(overlayHash))
                    .build();
            ResourcePackRequest req = ResourcePackRequest.resourcePackRequest()
                    .packs(main, overlay)
                    .required(true)
                    .replace(true)
                    .prompt(Component.text("Starlight Universe — se încarcă capul tău.", NamedTextColor.AQUA))
                    .build();
            player.sendResourcePacks(req);
        } catch (Throwable t) {
            plugin.getLogger().warning("[SU] Failed to push head pack to " + player.getName() + ": " + t.getMessage());
        }
    }

    public void refreshHeadFor(Player player) {
        cache.remove(player.getUniqueId());
        sendPacksTo(player);
    }

    private static UUID overlayIdFor(UUID playerUuid) {
        return overlayIdForStatic(playerUuid);
    }

    /** Static helper so ConfigPhasePackListener can compute the same overlay id. */
    public static UUID overlayIdForStatic(UUID playerUuid) {
        long msb = playerUuid.getMostSignificantBits() ^ 0x5354415250484541L;
        long lsb = playerUuid.getLeastSignificantBits() ^ 0x4453555253555555L;
        return new UUID(msb, lsb);
    }

    /** Registers a pre-built overlay in the cache so the HTTP server can serve it. */
    public void putCache(UUID playerUuid, byte[] zipBytes, byte[] sha1) {
        cache.put(playerUuid, new CachedPack(zipBytes, sha1));
    }

    // Tracks players whose pack was already sent during the config phase, so
    // sendHeadOverlayAfterAuth is a no-op for them (no duplicate reload).
    private final java.util.Set<UUID> configPhaseSent =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void markConfigPhaseSent(UUID uuid) { configPhaseSent.add(uuid); }
    public boolean wasConfigPhaseSent(UUID uuid) { return configPhaseSent.contains(uuid); }
    public void clearConfigPhaseMark(UUID uuid) { configPhaseSent.remove(uuid); }

    private String resolveHost(Player player) {
        InetSocketAddress virtual = player.getVirtualHost();
        if (virtual != null) {
            String vh = virtual.getHostString();
            if (vh != null && !vh.isBlank()) return vh;
        }
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

    private static final class CachedPack {
        final byte[] bytes;
        final byte[] hash;
        CachedPack(byte[] bytes, byte[] hash) {
            this.bytes = bytes;
            this.hash = hash;
        }
    }
}
