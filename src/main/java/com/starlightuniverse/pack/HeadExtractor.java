package com.starlightuniverse.pack;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a player's head PNG from their skin.
 * Layer 1 (base head) + Layer 2 (hat) are composited into a single 128×128 PNG.
 */
public final class HeadExtractor {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final Pattern SKIN_URL_PATTERN =
            Pattern.compile("\"SKIN\"\\s*:\\s*\\{\\s*\"url\"\\s*:\\s*\"([^\"]+)\"");

    private HeadExtractor() {}

    /**
     * Extracts the head for the given player and returns a PNG byte[].
     * Falls back to a solid gray silhouette if no skin can be resolved.
     */
    public static byte[] extractHead(Player player) {
        try {
            String skinUrl = resolveSkinUrl(player);
            if (skinUrl != null) {
                BufferedImage skin = downloadImage(skinUrl);
                if (skin != null) {
                    return renderHead(skin);
                }
            }
        } catch (Exception ignored) {}
        return renderFallback();
    }

    private static String resolveSkinUrl(Player player) {
        PlayerProfile profile = player.getPlayerProfile();
        if (profile == null) return null;
        for (ProfileProperty prop : profile.getProperties()) {
            if ("textures".equals(prop.getName())) {
                String decoded = new String(Base64.getDecoder().decode(prop.getValue()), StandardCharsets.UTF_8);
                Matcher m = SKIN_URL_PATTERN.matcher(decoded);
                if (m.find()) {
                    String url = m.group(1);
                    // JSON strings sometimes escape / as \/
                    return url.replace("\\/", "/");
                }
            }
        }
        return null;
    }

    private static BufferedImage downloadImage(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) return null;
        return ImageIO.read(new ByteArrayInputStream(resp.body()));
    }

    private static byte[] renderHead(BufferedImage skin) throws IOException {
        // Standard skin layout: head layer 1 at (8,8)-(16,16), head layer 2 (hat) at (40,8)-(48,16)
        int scale = 16; // final size 128 = 8 * 16
        int size = 8 * scale;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        // Base head
        g.drawImage(skin, 0, 0, size, size, 8, 8, 16, 16, null);
        // Hat overlay
        g.drawImage(skin, 0, 0, size, size, 40, 8, 48, 16, null);
        g.dispose();
        return toPng(out);
    }

    private static byte[] renderFallback() {
        int size = 128;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(0xC68C62));
        g.fillRect(16, 16, 96, 96);
        g.setColor(new java.awt.Color(0x3F2B1E));
        g.fillRect(16, 16, 96, 24);
        g.setColor(new java.awt.Color(0x000000));
        g.drawRect(16, 16, 96, 96);
        g.dispose();
        try {
            return toPng(img);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static byte[] toPng(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", out);
        return out.toByteArray();
    }

    /**
     * Same head extraction as {@link #extractHead(Player)}, but starts from a base64
     * "textures" property value (as returned by Mojang session server). Used at join
     * before the player's profile has textures applied.
     */
    public static byte[] renderHeadFromTexturesValue(String base64Value) {
        try {
            String decoded = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
            Matcher m = SKIN_URL_PATTERN.matcher(decoded);
            if (!m.find()) return null;
            String url = m.group(1).replace("\\/", "/");
            BufferedImage skin = downloadImage(url);
            if (skin == null) return null;
            return renderHead(skin);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Builds a full-fat per-player resource pack by rewriting the main pack ZIP
     * and overwriting the head texture entry with the player's PNG. Returns a
     * single self-contained ZIP that the client sees as one pack (1/1 loading).
     */
    public static byte[] buildMergedPack(byte[] mainPackBytes, byte[] headPng) throws IOException {
        java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(mainPackBytes));
        ByteArrayOutputStream out = new ByteArrayOutputStream(mainPackBytes.length + 4096);
        try (java.util.zip.ZipOutputStream zout = new java.util.zip.ZipOutputStream(out)) {
            java.util.zip.ZipEntry entry;
            boolean headWritten = false;
            String headPath = "assets/starlight/textures/font/heads/self.png";
            while ((entry = zin.getNextEntry()) != null) {
                zout.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                if (headPath.equals(entry.getName())) {
                    zout.write(headPng);
                    headWritten = true;
                } else {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zin.read(buf)) > 0) zout.write(buf, 0, n);
                }
                zout.closeEntry();
            }
            if (!headWritten) {
                zout.putNextEntry(new java.util.zip.ZipEntry(headPath));
                zout.write(headPng);
                zout.closeEntry();
            }
        }
        zin.close();
        return out.toByteArray();
    }

    /**
     * Wraps the head PNG in a tiny resource pack overlay ZIP.
     * The overlay only contains pack.mcmeta + assets/starlight/textures/font/heads/self.png,
     * so it overrides only that single texture when stacked on top of the main pack.
     */
    public static byte[] buildOverlayPack(byte[] headPng) throws IOException {
        String mcmeta = "{\n" +
                "  \"pack\": {\n" +
                "    \"pack_format\": 42,\n" +
                "    \"description\": \"Starlight Universe head overlay\"\n" +
                "  }\n" +
                "}\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("pack.mcmeta"));
            zip.write(mcmeta.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("assets/starlight/textures/font/heads/self.png"));
            zip.write(headPng);
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
