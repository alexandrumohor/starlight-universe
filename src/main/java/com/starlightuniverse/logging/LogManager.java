package com.starlightuniverse.logging;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LogManager {

    public static final String ENDPOINT_URL = "http://localhost:8080/starlight/log";
    public static final long FLUSH_INTERVAL_TICKS = 100L;
    public static final int CONNECT_TIMEOUT_MS = 2000;
    public static final int READ_TIMEOUT_MS = 3000;
    public static final int MAX_BATCH = 200;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final JavaPlugin plugin;
    private final ConcurrentLinkedQueue<Map<String, Object>> queue = new ConcurrentLinkedQueue<>();
    private final File fallbackDir;
    private BukkitTask flushTask;
    private volatile boolean shutdown = false;

    public LogManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.fallbackDir = new File(plugin.getDataFolder(), "logs");
        if (!fallbackDir.exists()) fallbackDir.mkdirs();
    }

    public void start() {
        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flush,
                FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
        plugin.getLogger().info("[SU] Logging system started (endpoint: " + ENDPOINT_URL + ").");
    }

    public void shutdown() {
        shutdown = true;
        if (flushTask != null) flushTask.cancel();
        flush();
    }

    public void log(String category, Player player, Map<String, Object> data) {
        if (shutdown) return;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", LocalDateTime.now().format(ISO));
        entry.put("category", category);
        if (player != null) {
            entry.put("player", player.getName());
            entry.put("uuid", player.getUniqueId().toString());
            entry.put("world", player.getWorld().getName());
            entry.put("x", (int) player.getLocation().getX());
            entry.put("y", (int) player.getLocation().getY());
            entry.put("z", (int) player.getLocation().getZ());
            if (player.getAddress() != null && player.getAddress().getAddress() != null) {
                entry.put("ip", player.getAddress().getAddress().getHostAddress());
            }
        }
        if (data != null) entry.putAll(data);
        queue.add(entry);
    }

    public void log(String category, Map<String, Object> data) {
        log(category, null, data);
    }

    private void flush() {
        if (queue.isEmpty()) return;
        List<Map<String, Object>> batch = new ArrayList<>();
        while (batch.size() < MAX_BATCH) {
            Map<String, Object> e = queue.poll();
            if (e == null) break;
            batch.add(e);
        }
        if (batch.isEmpty()) return;

        String json = toJsonArray(batch);
        boolean sent = false;
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(ENDPOINT_URL).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent", "StarlightUniverse-Log/1.0");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            sent = code >= 200 && code < 300;
            conn.disconnect();
        } catch (IOException ignored) {
        }

        if (!sent) writeFallback(batch);
    }

    private void writeFallback(List<Map<String, Object>> batch) {
        File file = new File(fallbackDir, "log-" + LocalDate.now() + ".jsonl");
        Path path = file.toPath();
        try (BufferedWriter writer = Files.newBufferedWriter(path,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (Map<String, Object> entry : batch) {
                writer.write(toJson(entry));
                writer.newLine();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[SU] Failed to write fallback log: " + e.getMessage());
        }
    }

    private String toJsonArray(List<Map<String, Object>> items) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map<String, Object> item : items) {
            if (!first) sb.append(',');
            sb.append(toJson(item));
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(escape(entry.getKey())).append("\":");
            appendValue(sb, entry.getValue());
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    private void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            sb.append('"').append(escape(value.toString())).append('"');
        }
    }

    private String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
