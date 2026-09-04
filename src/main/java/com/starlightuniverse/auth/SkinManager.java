package com.starlightuniverse.auth;

import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class SkinManager {

    private final JavaPlugin plugin;
    private final HttpClient httpClient;
    private final List<SkinData> cachedSkins = new CopyOnWriteArrayList<>();

    public SkinManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void loadRandomSkins() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            fetchFromMineSkin();
            if (cachedSkins.isEmpty()) {
                fetchFromMojangProfiles();
            }
            plugin.getLogger().info("[SU] Cached " + cachedSkins.size() + " fallback skins.");
        });
    }

    private void fetchFromMineSkin() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mineskin.org/v2/skins?size=30"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("User-Agent", "StarlightUniverse/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                parseMineSkinResponse(response.body());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[SU] MineSkin API unavailable, trying fallback skin source.");
        }
    }

    private void parseMineSkinResponse(String json) {
        int searchFrom = 0;
        while (true) {
            int valueIdx = json.indexOf("\"value\"", searchFrom);
            if (valueIdx == -1) break;

            String value = extractQuotedValue(json, valueIdx);
            if (value == null) break;

            int sigIdx = json.indexOf("\"signature\"", valueIdx);
            if (sigIdx == -1) break;

            String signature = extractQuotedValue(json, sigIdx);
            if (signature == null) break;

            if (value.length() > 50 && signature.length() > 50) {
                cachedSkins.add(new SkinData(value, signature));
            }
            searchFrom = sigIdx + 1;
        }
    }

    private void fetchFromMojangProfiles() {
        String[] knownUuids = {
                "069a79f444e94726a5befca90e38aaf5",
                "853c80ef3c3749fdaa49938b674adae6",
                "ec561538f3fd461daff5086b22154bce",
                "b876ec32e396476ba1158438d83c67d4",
                "f7c77d999f154a66a87dc4a51ef30d19",
                "61699b2ed3274a019f1e0ea8c3f06bc6",
                "2f3665ccc1a249679c8bc1c0f34a9c8f",
                "7125ba8b1c864508b92bb5c042ccfe2b",
                "c06f89064c8a49119c29ea1dbd1aab82",
                "4566e69fc90748ee8d71d7ba5aa00d20"
        };

        for (String uuid : knownUuids) {
            try {
                SkinData skin = fetchMojangSkin(uuid);
                if (skin != null) {
                    cachedSkins.add(skin);
                }
                Thread.sleep(100);
            } catch (Exception ignored) {}
        }
    }

    public SkinData fetchMojangSkin(String uuid) {
        try {
            String cleanUuid = uuid.replace("-", "");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + cleanUuid + "?unsigned=false"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                int valueIdx = body.indexOf("\"value\"");
                if (valueIdx == -1) {
                    plugin.getLogger().warning("[SU] Mojang skin fetch for " + cleanUuid + " has no 'value' field.");
                    return null;
                }
                String value = extractQuotedValue(body, valueIdx);

                int sigIdx = body.indexOf("\"signature\"");
                if (sigIdx == -1) {
                    plugin.getLogger().warning("[SU] Mojang skin fetch for " + cleanUuid + " has no 'signature' field.");
                    return null;
                }
                String signature = extractQuotedValue(body, sigIdx);

                if (value != null && signature != null) {
                    plugin.getLogger().info("[SU] Fetched Mojang skin for " + cleanUuid
                            + " (value len=" + value.length() + ").");
                    return new SkinData(value, signature);
                }
                plugin.getLogger().warning("[SU] Mojang skin fetch for " + cleanUuid + " parsed empty value/signature.");
            } else if (response.statusCode() == 204) {
                // Account was deleted / migrated — quietly skip, not an error.
                plugin.getLogger().info("[SU] Skin UUID " + cleanUuid + " no longer exists (HTTP 204).");
            } else {
                plugin.getLogger().warning("[SU] Mojang skin fetch for " + cleanUuid
                        + " returned HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[SU] Mojang skin fetch threw: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
        return null;
    }

    public SkinData getRandomSkin() {
        if (cachedSkins.isEmpty()) return null;
        return cachedSkins.get(ThreadLocalRandom.current().nextInt(cachedSkins.size()));
    }

    public void applySkin(Player player, SkinData skin) {
        if (skin == null) {
            plugin.getLogger().warning("[SU] applySkin called with null skin for "
                    + (player != null ? player.getName() : "?"));
            return;
        }
        if (!player.isOnline()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            var profile = player.getPlayerProfile();
            profile.removeProperty("textures");
            profile.setProperty(new ProfileProperty("textures", skin.value(), skin.signature()));
            player.setPlayerProfile(profile);
            plugin.getLogger().info("[SU] applySkin for " + player.getName() + " done.");

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.hidePlayer(plugin, player);
                    online.showPlayer(plugin, player);
                }
            }
        }, 2L);
    }

    private String extractQuotedValue(String json, int keyIdx) {
        int colonIdx = json.indexOf(':', keyIdx);
        if (colonIdx == -1) return null;
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote == -1) return null;
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote == -1) return null;
        return json.substring(startQuote + 1, endQuote);
    }

    public record SkinData(String value, String signature) {}
}
