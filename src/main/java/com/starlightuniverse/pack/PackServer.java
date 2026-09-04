package com.starlightuniverse.pack;

import com.starlightuniverse.StarlightUniverse;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class PackServer {

    public static final int PACK_PORT = 25566;
    public static final String PACK_PATH = "/starlight-pack.zip";

    private final StarlightUniverse plugin;
    private HttpServer server;
    private byte[] packBytes;
    private byte[] packHash;
    private String packHashHex;
    private Path packFile;

    public PackServer(StarlightUniverse plugin) {
        this.plugin = plugin;
    }

    public boolean start() {
        try (InputStream in = plugin.getResource("starlight-pack.zip")) {
            if (in == null) {
                plugin.getLogger().warning("[SU] starlight-pack.zip not bundled in jar — resource pack disabled.");
                return false;
            }
            packBytes = in.readAllBytes();
        } catch (IOException e) {
            plugin.getLogger().warning("[SU] Failed to read bundled pack: " + e.getMessage());
            return false;
        }

        try {
            packFile = plugin.getDataFolder().toPath().resolve("starlight-pack.zip");
            Files.createDirectories(packFile.getParent());
            Files.write(packFile, packBytes);
        } catch (IOException e) {
            plugin.getLogger().warning("[SU] Failed to write pack to disk: " + e.getMessage());
        }

        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            packHash = sha1.digest(packBytes);
            packHashHex = HexFormat.of().formatHex(packHash);
        } catch (Exception e) {
            plugin.getLogger().warning("[SU] Failed to hash pack: " + e.getMessage());
            return false;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(PACK_PORT), 0);
            server.createContext(PACK_PATH, exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, packBytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(packBytes);
                }
            });
            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("[SU] Pack server started on port " + PACK_PORT
                    + " (SHA-1: " + packHashHex.substring(0, 12) + "…, "
                    + packBytes.length + " bytes)");
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("[SU] Failed to bind pack server on port " + PACK_PORT
                    + ": " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public boolean isReady() {
        return server != null && packHash != null;
    }

    public byte[] getPackHash() {
        return packHash;
    }

    public String getPackHashHex() {
        return packHashHex;
    }

    public byte[] getPackBytes() {
        return packBytes;
    }

    public String buildUrl(String host) {
        return "http://" + host + ":" + PACK_PORT + PACK_PATH;
    }
}
