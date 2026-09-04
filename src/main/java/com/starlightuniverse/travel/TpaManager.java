package com.starlightuniverse.travel;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.premium.PremiumRank;
import com.starlightuniverse.util.Msg;
import com.starlightuniverse.world.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TpaManager {

    public static final long DEFAULT_COOLDOWN_MS = 10_000L;
    public static final long DEFAULT_REQUEST_TTL_MS = 60_000L;
    public static final long WARMUP_MS = 3_000L;

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);

    public enum RequestType { TO_TARGET, TO_SENDER }

    public static class Request {
        public final UUID sender;
        public final UUID target;
        public final RequestType type;
        public final long expiresAt;

        public Request(UUID sender, UUID target, RequestType type, long expiresAt) {
            this.sender = sender;
            this.target = target;
            this.type = type;
            this.expiresAt = expiresAt;
        }
    }

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final AdminManager adminManager;

    private final Map<UUID, Request> outgoing = new ConcurrentHashMap<>();
    private final Map<UUID, List<Request>> incoming = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> disabledReceive = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<String>> blocked = new ConcurrentHashMap<>();

    public TpaManager(JavaPlugin plugin, DatabaseManager db, AdminManager adminManager) {
        this.plugin = plugin;
        this.db = db;
        this.adminManager = adminManager;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::expireRequests, 20L, 20L);
    }

    private void expireRequests() {
        long now = System.currentTimeMillis();
        outgoing.entrySet().removeIf(e -> {
            if (now >= e.getValue().expiresAt) {
                Player sender = Bukkit.getPlayer(e.getValue().sender);
                if (sender != null && sender.isOnline()) Msg.gray(sender, "Your teleport request expired.");
                return true;
            }
            return false;
        });
        for (List<Request> list : incoming.values()) {
            list.removeIf(r -> now >= r.expiresAt);
        }
    }

    // ============================================================
    // LOAD / UNLOAD PLAYER STATE
    // ============================================================

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        String lower = player.getName().toLowerCase();

        db.queryAsync(conn -> {
            boolean disabled = false;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT tpa_disabled FROM su_players WHERE username = ?")) {
                ps.setString(1, lower);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) disabled = rs.getBoolean("tpa_disabled");
                }
            }
            Set<String> blockedSet = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT blocked_username FROM su_tp_blocks WHERE blocker_username = ?")) {
                ps.setString(1, lower);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) blockedSet.add(rs.getString("blocked_username"));
                }
            }
            return new Object[]{disabled, blockedSet};
        }).thenAccept(result -> {
            if (result == null) return;
            boolean disabled = (boolean) ((Object[]) result)[0];
            @SuppressWarnings("unchecked")
            Set<String> blockedSet = (Set<String>) ((Object[]) result)[1];
            if (disabled) disabledReceive.add(uuid);
            blocked.put(uuid, blockedSet);
        });
    }

    public void unloadPlayer(UUID uuid) {
        outgoing.remove(uuid);
        incoming.remove(uuid);
        cooldowns.remove(uuid);
        disabledReceive.remove(uuid);
        blocked.remove(uuid);
    }

    // ============================================================
    // /tpa & /tpahere
    // ============================================================

    public void sendRequest(Player sender, Player target, RequestType type) {
        if (sender.equals(target)) {
            Msg.error(sender, "You can't teleport to yourself!");
            return;
        }
        if (!isTpaAllowedWorld(sender.getWorld().getName())) {
            Msg.error(sender, "TPA is not allowed from your current world!");
            return;
        }
        if (!isTpaAllowedWorld(target.getWorld().getName())) {
            Msg.error(sender, target.getName() + " is in a world where TPA is not allowed!");
            return;
        }
        UUID sUuid = sender.getUniqueId();
        long now = System.currentTimeMillis();
        Long cd = cooldowns.get(sUuid);
        if (cd != null && now < cd) {
            Msg.error(sender, "Please wait " + ((cd - now) / 1000 + 1) + "s before sending another request!");
            return;
        }

        if (disabledReceive.contains(target.getUniqueId())) {
            Msg.error(sender, target.getName() + " has TPA disabled!");
            return;
        }
        Set<String> targetBlocks = blocked.getOrDefault(target.getUniqueId(), Set.of());
        if (targetBlocks.contains(sender.getName().toLowerCase())) {
            Msg.error(sender, "You are blocked by " + target.getName() + ".");
            return;
        }
        if (outgoing.containsKey(sUuid)) {
            Msg.error(sender, "You already have a pending request! Use /tpcancel first.");
            return;
        }

        long ttl = DEFAULT_REQUEST_TTL_MS;
        PremiumRank rank = PremiumRank.fromLevel(adminManager.getPremiumLevel(sUuid));
        if (rank != PremiumRank.NONE) ttl = rank.getTpaDuration() * 1000L;

        Request req = new Request(sUuid, target.getUniqueId(), type, now + ttl);
        outgoing.put(sUuid, req);
        incoming.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>()).add(req);
        cooldowns.put(sUuid, now + DEFAULT_COOLDOWN_MS);

        String verb = type == RequestType.TO_TARGET ? "teleport to you" : "have you teleport to them";
        target.sendMessage(Component.text("[SU] ", GOLD)
                .append(Component.text(sender.getName(), YELLOW))
                .append(Component.text(" wants to " + verb + ". ", GRAY))
                .append(Component.text("[Accept]", GREEN)
                        .clickEvent(ClickEvent.runCommand("/tpaccept " + sender.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to accept", GREEN))))
                .append(Component.text(" ", GRAY))
                .append(Component.text("[Deny]", RED)
                        .clickEvent(ClickEvent.runCommand("/tpdeny " + sender.getName()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to deny", RED)))));
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        sender.sendMessage(Component.text("[SU] ", GOLD)
                .append(Component.text("Request sent to " + target.getName() + ". ", GRAY))
                .append(Component.text("[Cancel]", RED)
                        .clickEvent(ClickEvent.runCommand("/tpcancel"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to cancel", RED)))));
    }

    // ============================================================
    // /tpaccept & /tpdeny
    // ============================================================

    public void acceptRequest(Player target, String senderName) {
        Request req = findIncoming(target.getUniqueId(), senderName);
        if (req == null) {
            Msg.error(target, "No pending request!");
            return;
        }
        Player sender = Bukkit.getPlayer(req.sender);
        if (sender == null || !sender.isOnline()) {
            Msg.error(target, "That player is offline!");
            removeRequest(req);
            return;
        }

        removeRequest(req);
        Player toTeleport = req.type == RequestType.TO_TARGET ? sender : target;
        Player anchor = req.type == RequestType.TO_TARGET ? target : sender;

        if (toTeleport.equals(target)) {
            Msg.success(target, "You will teleport to " + anchor.getName() + " in 3s...");
            Msg.success(sender, target.getName() + " accepted! They will teleport to you in 3s...");
        } else {
            Msg.success(target, "Accepted! " + toTeleport.getName() + " will teleport to you in 3s...");
            Msg.success(sender, target.getName() + " accepted your request! You will teleport in 3s...");
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!toTeleport.isOnline() || !anchor.isOnline()) {
                Msg.error(toTeleport, "Teleport failed — player left.");
                return;
            }
            for (Entity passenger : new ArrayList<>(toTeleport.getPassengers())) {
                if (passenger instanceof TextDisplay) {
                    toTeleport.removePassenger(passenger);
                    passenger.remove();
                }
            }
            if (toTeleport.isInsideVehicle()) toTeleport.leaveVehicle();

            toTeleport.teleportAsync(anchor.getLocation(), PlayerTeleportEvent.TeleportCause.COMMAND)
                    .thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!toTeleport.isOnline()) return;
                        if (Boolean.TRUE.equals(success)) {
                            Msg.success(toTeleport, "Teleported to " + anchor.getName() + "!");
                            toTeleport.playSound(toTeleport.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        } else {
                            Msg.error(toTeleport, "Teleport was cancelled!");
                        }
                    })).exceptionally(ex -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (toTeleport.isOnline()) {
                                Msg.error(toTeleport, "Teleport failed: " + ex.getClass().getSimpleName());
                            }
                        });
                        return null;
                    });
        }, WARMUP_MS / 50L);
    }

    public void denyRequest(Player target, String senderName) {
        Request req = findIncoming(target.getUniqueId(), senderName);
        if (req == null) {
            Msg.error(target, "No pending request!");
            return;
        }
        Player sender = Bukkit.getPlayer(req.sender);
        removeRequest(req);
        Msg.info(target, "Denied request from " + senderName + ".");
        if (sender != null && sender.isOnline()) {
            Msg.error(sender, target.getName() + " denied your teleport request.");
        }
    }

    public void cancelOutgoing(Player sender) {
        Request req = outgoing.remove(sender.getUniqueId());
        if (req == null) {
            Msg.error(sender, "You have no pending outgoing request!");
            return;
        }
        List<Request> list = incoming.get(req.target);
        if (list != null) list.remove(req);
        Msg.info(sender, "Cancelled your teleport request.");
        Player target = Bukkit.getPlayer(req.target);
        if (target != null && target.isOnline()) {
            Msg.gray(target, sender.getName() + " cancelled their teleport request.");
        }
    }

    private Request findIncoming(UUID target, String senderName) {
        List<Request> list = incoming.get(target);
        if (list == null || list.isEmpty()) return null;
        long now = System.currentTimeMillis();
        if (senderName == null || senderName.isEmpty()) {
            for (int i = list.size() - 1; i >= 0; i--) {
                Request r = list.get(i);
                if (now < r.expiresAt) return r;
            }
            return null;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            Request r = list.get(i);
            if (now >= r.expiresAt) continue;
            Player p = Bukkit.getPlayer(r.sender);
            if (p != null && p.getName().equalsIgnoreCase(senderName)) return r;
        }
        return null;
    }

    private void removeRequest(Request req) {
        outgoing.remove(req.sender);
        List<Request> list = incoming.get(req.target);
        if (list != null) list.remove(req);
    }

    // ============================================================
    // /tptoggle
    // ============================================================

    public void toggle(Player player) {
        UUID uuid = player.getUniqueId();
        boolean nowDisabled;
        if (disabledReceive.contains(uuid)) {
            disabledReceive.remove(uuid);
            nowDisabled = false;
            Msg.success(player, "TPA requests ENABLED. Others can send you requests.");
        } else {
            disabledReceive.add(uuid);
            nowDisabled = true;
            Msg.info(player, "TPA requests DISABLED. Others cannot send you requests.");
            List<Request> list = incoming.remove(uuid);
            if (list != null) {
                for (Request r : list) outgoing.remove(r.sender);
            }
        }
        String lower = player.getName().toLowerCase();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET tpa_disabled = ? WHERE username = ?")) {
                ps.setBoolean(1, nowDisabled);
                ps.setString(2, lower);
                ps.executeUpdate();
            }
        });
    }

    // ============================================================
    // /tpblock
    // ============================================================

    public void toggleBlock(Player player, String targetName) {
        UUID uuid = player.getUniqueId();
        String lowerTarget = targetName.toLowerCase();
        String lowerPlayer = player.getName().toLowerCase();
        if (lowerTarget.equals(lowerPlayer)) {
            Msg.error(player, "You can't block yourself!");
            return;
        }
        Set<String> set = blocked.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        boolean nowBlocked;
        if (set.contains(lowerTarget)) {
            set.remove(lowerTarget);
            nowBlocked = false;
            Msg.success(player, "Unblocked " + targetName + ". They can send you TPA requests again.");
        } else {
            set.add(lowerTarget);
            nowBlocked = true;
            Msg.info(player, "Blocked " + targetName + ". They can no longer send you TPA requests.");
        }
        db.executeAsync(conn -> {
            if (nowBlocked) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT IGNORE INTO su_tp_blocks (blocker_username, blocked_username) VALUES (?, ?)")) {
                    ps.setString(1, lowerPlayer);
                    ps.setString(2, lowerTarget);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM su_tp_blocks WHERE blocker_username = ? AND blocked_username = ?")) {
                    ps.setString(1, lowerPlayer);
                    ps.setString(2, lowerTarget);
                    ps.executeUpdate();
                }
            }
        });
    }

    public boolean hasDisabled(UUID uuid) { return disabledReceive.contains(uuid); }
    public Set<String> getBlockedBy(UUID uuid) { return blocked.getOrDefault(uuid, Set.of()); }

    /**
     * TPA works only between the 7 permanent survival worlds:
     * survivallobby, overworld, nether, end, and the 3 resource worlds.
     * Dragon world, the main lobby, and any minigame/arena world are excluded.
     */
    private static boolean isTpaAllowedWorld(String worldName) {
        if (WorldManager.getWorldGroup(worldName) != WorldManager.WorldGroup.SURVIVAL) return false;
        String stripped = worldName;
        int colon = worldName.indexOf(':');
        if (colon >= 0) stripped = worldName.substring(colon + 1);
        return !stripped.equals(WorldManager.WORLD_DRAGON);
    }
}
