package com.starlightuniverse.diag;

import com.starlightuniverse.StarlightUniverse;
import com.starlightuniverse.auth.AuthManager;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.job.JobManager;
import com.starlightuniverse.pack.PackServer;
import com.starlightuniverse.skill.SkillManager;
import com.starlightuniverse.world.InventoryManager;
import com.starlightuniverse.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DiagnosticsService {

    private static final String[] TRACKED_TABLES = {
            "su_players", "su_bans", "su_mutes", "su_warns",
            "su_homes", "su_protections", "su_protection_members",
            "su_teams", "su_team_members", "su_team_bank", "su_team_allies",
            "su_auctions", "su_orders", "su_order_items", "su_order_storage",
            "su_crates", "su_pwarps", "su_pwarp_ratings", "su_pwarp_bans",
            "su_announcements", "su_emojis", "su_benefits",
            "su_maintenance", "su_pvp_stats", "su_mob_raid_stats",
            "su_jobs", "su_skills", "su_inventories", "su_spawners"
    };

    private final StarlightUniverse plugin;

    public DiagnosticsService(StarlightUniverse plugin) {
        this.plugin = plugin;
    }

    public String buildStartupSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("worlds=").append(Bukkit.getWorlds().size());
        PackServer pack = plugin.getPackServer();
        sb.append(", pack=");
        if (pack != null && pack.isReady()) {
            sb.append("ok(sha1=").append(pack.getPackHashHex(), 0, Math.min(8, pack.getPackHashHex().length())).append(")");
        } else {
            sb.append("off");
        }
        DatabaseManager db = plugin.getDatabaseManager();
        sb.append(", db=");
        try (Connection c = db.getConnection()) {
            sb.append(c.isValid(2) ? "ok" : "bad");
        } catch (SQLException e) {
            sb.append("bad");
        }
        long playerCount = countRowsSafe("su_players");
        sb.append(", players_in_db=").append(playerCount);
        return sb.toString();
    }

    public Map<String, String> buildStatus() {
        Map<String, String> out = new LinkedHashMap<>();

        double[] tps = Bukkit.getServer().getTPS();
        out.put("TPS 1m/5m/15m",
                fmt(tps[0]) + " / " + fmt(tps[1]) + " / " + fmt(tps[2]));

        long free = Runtime.getRuntime().freeMemory();
        long total = Runtime.getRuntime().totalMemory();
        long max = Runtime.getRuntime().maxMemory();
        long used = total - free;
        out.put("Memory (used/max)",
                (used / 1024 / 1024) + " MB / " + (max / 1024 / 1024) + " MB");

        int worlds = Bukkit.getWorlds().size();
        int loadedChunks = 0;
        for (World w : Bukkit.getWorlds()) loadedChunks += w.getLoadedChunks().length;
        out.put("Worlds / chunks", worlds + " / " + loadedChunks);

        out.put("Online players", String.valueOf(Bukkit.getOnlinePlayers().size()));

        DatabaseManager db = plugin.getDatabaseManager();
        String dbState;
        try (Connection c = db.getConnection()) {
            dbState = c.isValid(2) ? "ok" : "unhealthy";
        } catch (SQLException e) {
            dbState = "error: " + e.getMessage();
        }
        out.put("Database", dbState);

        PackServer pack = plugin.getPackServer();
        if (pack != null && pack.isReady()) {
            out.put("Resource pack", "ok (sha1=" + shortHash(pack.getPackHashHex()) + ", port=" + PackServer.PACK_PORT + ")");
        } else {
            out.put("Resource pack", "not started");
        }

        out.put("Scheduled Bukkit tasks", String.valueOf(Bukkit.getScheduler().getPendingTasks().size()));

        List<String> managers = new ArrayList<>();
        addManager(managers, "auth", plugin.getAuthManager());
        addManager(managers, "world", plugin.getWorldManager());
        addManager(managers, "inventory", plugin.getInventoryManager());
        addManager(managers, "queue", plugin.getQueueManager());
        addManager(managers, "lobby", plugin.getLobbyManager());
        addManager(managers, "economy", plugin.getEconomyManager());
        addManager(managers, "shop", plugin.getShopManager());
        addManager(managers, "auction", plugin.getAuctionManager());
        addManager(managers, "order", plugin.getOrderManager());
        addManager(managers, "admin", plugin.getAdminManager());
        addManager(managers, "home", plugin.getHomeManager());
        addManager(managers, "premium", plugin.getPremiumManager());
        addManager(managers, "team", plugin.getTeamManager());
        addManager(managers, "chat", plugin.getChatManager());
        addManager(managers, "crate", plugin.getCrateManager());
        addManager(managers, "job", plugin.getJobManager());
        addManager(managers, "skill", plugin.getSkillManager());
        addManager(managers, "enchant", plugin.getEnchantManager());
        addManager(managers, "starshop", plugin.getStarShopManager());
        addManager(managers, "arenaworld", plugin.getArenaWorldManager());
        addManager(managers, "pvp", plugin.getPvpManager());
        addManager(managers, "boss", plugin.getBossKillManager());
        addManager(managers, "mobraid", plugin.getMobRaidManager());
        addManager(managers, "minigame", plugin.getMinigameManager());
        addManager(managers, "emoji", plugin.getEmojiManager());
        addManager(managers, "benefit", plugin.getBenefitManager());
        addManager(managers, "nameplate", plugin.getNameplateManager());
        addManager(managers, "spawner", plugin.getSpawnerManager());
        addManager(managers, "rtp", plugin.getRtpManager());
        addManager(managers, "tpa", plugin.getTpaManager());
        addManager(managers, "pwarp", plugin.getPwarpManager());
        addManager(managers, "anticheat", plugin.getAntiCheatManager());
        addManager(managers, "log", plugin.getLogManager());
        addManager(managers, "announce", plugin.getAnnouncementManager());
        addManager(managers, "maintenance", plugin.getMaintenanceManager());
        addManager(managers, "hottime", plugin.getHotTimeManager());
        out.put("Managers online", managers.size() + " (" + String.join(", ", managers) + ")");

        return out;
    }

    public Map<String, Long> tableCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : TRACKED_TABLES) {
            counts.put(table, countRowsSafe(table));
        }
        return counts;
    }

    public int forceSaveAll() {
        int saved = 0;
        AuthManager auth = plugin.getAuthManager();
        InventoryManager inv = plugin.getInventoryManager();
        JobManager jobs = plugin.getJobManager();
        SkillManager skills = plugin.getSkillManager();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (auth == null || !auth.isAuthenticated(p.getUniqueId())) continue;
            if (inv != null) {
                WorldManager.WorldGroup group = WorldManager.getWorldGroup(p.getWorld());
                if (group != WorldManager.WorldGroup.UNKNOWN) {
                    inv.saveInventorySync(p, group);
                }
            }
            if (jobs != null) jobs.savePlayer(p);
            if (skills != null) skills.savePlayer(p);
            saved++;
        }
        Bukkit.savePlayers();
        for (World w : Bukkit.getWorlds()) {
            w.save();
        }
        return saved;
    }

    private long countRowsSafe(String table) {
        DatabaseManager db = plugin.getDatabaseManager();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : -1L;
        } catch (SQLException e) {
            return -1L;
        }
    }

    private static void addManager(List<String> list, String name, Object mgr) {
        if (mgr != null) list.add(name);
    }

    private static String fmt(double d) {
        return String.format("%.2f", Math.min(20.0, d));
    }

    private static String shortHash(String hex) {
        if (hex == null || hex.isEmpty()) return "?";
        return hex.substring(0, Math.min(8, hex.length()));
    }
}
