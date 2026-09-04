package com.starlightuniverse.scoreboard;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.admin.AdminRank;
import com.starlightuniverse.auth.AuthManager;
import com.starlightuniverse.chat.ChatManager;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.premium.PremiumManager;
import com.starlightuniverse.premium.PremiumRank;
import com.starlightuniverse.team.Team;
import com.starlightuniverse.team.TeamManager;
import com.starlightuniverse.team.TeamRank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import io.papermc.paper.scoreboard.numbers.NumberFormat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScoreboardManager {

    // Scoreboard icon glyphs (mapped in resourcepack/assets/minecraft/font/default.json)
    public static final String ICON_NAME    = "";
    public static final String ICON_ADMIN   = "";
    public static final String ICON_PREMIUM = "";
    public static final String ICON_TEAM    = "";
    public static final String ICON_MONEY   = "";
    public static final String ICON_GEMS    = "";
    public static final String ICON_STARS   = "";
    public static final String ICON_EXP     = "";
    public static final String ICON_PVP     = "";
    public static final String ICON_PVM     = "";
    public static final String ICON_DEATHS  = "";
    public static final String ICON_HEAD    = "";

    // Colors
    private static final TextColor TITLE_COLOR  = TextColor.color(0xFFF5A0);
    private static final TextColor LABEL_COLOR  = NamedTextColor.WHITE;
    private static final TextColor NAME_DEFAULT = NamedTextColor.WHITE;
    private static final TextColor MONEY_COLOR  = TextColor.color(0xFFFF00);
    private static final TextColor GEMS_COLOR   = TextColor.color(0x9900CC);
    private static final TextColor STARS_COLOR  = TextColor.color(0xFFF5A0);
    private static final TextColor EXP_COLOR    = TextColor.color(0x55FF55);
    private static final TextColor PVP_COLOR    = TextColor.color(0xFF944D);
    private static final TextColor PVM_COLOR    = TextColor.color(0xFF8080);
    private static final TextColor DEATHS_COLOR = TextColor.color(0xFF0000);
    private static final TextColor EMPTY_COLOR  = TextColor.color(0x666666);

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;
    private final AdminManager admin;
    private final PremiumManager premium;
    private final TeamManager team;
    private final AuthManager auth;
    private final ChatManager chat;

    // Per-player boards
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    // Cached stats (kills/deaths/exp) refreshed from DB
    private final Map<UUID, int[]> statsCache = new HashMap<>();
    // Cached exp (int for level, int for current, long for total)
    private final Map<UUID, long[]> expCache = new HashMap<>();

    private int taskId = -1;

    public ScoreboardManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy,
                             AdminManager admin, PremiumManager premium, TeamManager team,
                             AuthManager auth, ChatManager chat) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
        this.admin = admin;
        this.premium = premium;
        this.team = team;
        this.auth = auth;
        this.chat = chat;
    }

    public void start() {
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 20L).getTaskId();
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        boards.clear();
        statsCache.clear();
        expCache.clear();
    }

    public void createFor(Player player) {
        if (!auth.isAuthenticated(player.getUniqueId())) return;
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective(
                "su_sb", Criteria.DUMMY,
                Component.text("Starlight Universe", TITLE_COLOR));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());
        boards.put(player.getUniqueId(), board);
        loadStatsAsync(player);
        loadExpAsync(player);
        player.setScoreboard(board);
        render(player);
    }

    public void removeFor(Player player) {
        boards.remove(player.getUniqueId());
        statsCache.remove(player.getUniqueId());
        expCache.remove(player.getUniqueId());
        if (player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void refresh(Player player) {
        if (boards.containsKey(player.getUniqueId())) render(player);
    }

    public void refreshStats(Player player) {
        loadStatsAsync(player);
    }

    /** Returns cached [pvp_kills, pvm_kills, deaths]. Returns [0,0,0] if not loaded. */
    public int[] getStatsFor(UUID uuid) {
        return statsCache.getOrDefault(uuid, new int[]{0, 0, 0});
    }

    public void refreshExp(Player player) {
        loadExpAsync(player);
    }

    private void tickAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!boards.containsKey(p.getUniqueId())) {
                if (auth.isAuthenticated(p.getUniqueId())) createFor(p);
                continue;
            }
            render(p);
        }
    }

    private void render(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;
        Objective obj = board.getObjective("su_sb");
        if (obj == null) return;

        // Clear old entries
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        int score = 15;
        setLine(obj, "l_top_blank", score--, Component.empty());
        setLine(obj, "l_name", score--, nameLine(player));
        setLine(obj, "l_admin", score--, adminLine(player));
        setLine(obj, "l_premium", score--, premiumLine(player));
        setLine(obj, "l_team", score--, teamLine(player));
        setLine(obj, "l_blank1", score--, Component.text(" ", NamedTextColor.WHITE));
        setLine(obj, "l_money", score--, moneyLine(player));
        setLine(obj, "l_gems", score--, gemsLine(player));
        setLine(obj, "l_stars", score--, starsLine(player));
        setLine(obj, "l_exp", score--, expLine(player));
        setLine(obj, "l_blank2", score--, Component.text("  ", NamedTextColor.WHITE));
        setLine(obj, "l_pvp", score--, pvpLine(player));
        setLine(obj, "l_pvm", score--, pvmLine(player));
        setLine(obj, "l_deaths", score--, deathsLine(player));
    }

    private static void setLine(Objective obj, String entry, int scoreValue, Component text) {
        Score s = obj.getScore(entry);
        s.setScore(scoreValue);
        s.customName(text);
        s.numberFormat(NumberFormat.blank());
    }

    // --------------------------------------------------------
    // Line builders
    // --------------------------------------------------------

    private Component nameLine(Player p) {
        TextColor color = playerNameColor(p);
        return Component.text()
                .append(icon(ICON_NAME))
                .append(Component.text(" Name: ", LABEL_COLOR))
                .append(Component.text(p.getName(), color))
                .append(Component.text(" ", LABEL_COLOR))
                .append(icon(ICON_HEAD))
                .build();
    }

    private Component adminLine(Player p) {
        AdminRank rank = admin.getAdminRank(p.getUniqueId());
        Component value;
        if (rank == null || rank == AdminRank.NONE) {
            value = Component.empty();
        } else {
            value = Component.text(rank.getDisplayName(), rank.getColor());
        }
        return Component.text()
                .append(icon(ICON_ADMIN))
                .append(Component.text(" Admin Level: ", LABEL_COLOR))
                .append(value)
                .build();
    }

    private Component premiumLine(Player p) {
        PremiumRank rank = premium.getPlayerRank(p.getUniqueId());
        Component value;
        if (rank == null || rank == PremiumRank.NONE) {
            value = Component.empty();
        } else {
            // Uses gradient for Galaxy (matches nameplate); solid color for the rest.
            value = rank.getColoredDisplayName();
        }
        return Component.text()
                .append(icon(ICON_PREMIUM))
                .append(Component.text(" Premium Level: ", LABEL_COLOR))
                .append(value)
                .build();
    }

    private Component teamLine(Player p) {
        Team playerTeam = team.getPlayerTeam(p.getUniqueId());
        Component value;
        if (playerTeam == null) {
            value = Component.empty();
        } else {
            TextColor teamColor = firstTeamColor(playerTeam);
            Component name = Component.text(playerTeam.getName(), teamColor);
            TeamRank rank = playerTeam.getMemberRank(p.getName());
            if (rank != null) {
                value = Component.text()
                        .append(name)
                        .append(Component.text(" (", LABEL_COLOR))
                        .append(Component.text(rank.getDisplayName(), teamColor))
                        .append(Component.text(")", LABEL_COLOR))
                        .build();
            } else {
                value = name;
            }
        }
        return Component.text()
                .append(icon(ICON_TEAM))
                .append(Component.text(" Team: ", LABEL_COLOR))
                .append(value)
                .build();
    }

    private static TextColor firstTeamColor(Team t) {
        if (t == null) return NAME_DEFAULT;
        var colors = t.getColors();
        if (colors == null || colors.isEmpty()) return NAME_DEFAULT;
        String hex = colors.get(0);
        try {
            return TextColor.fromHexString(hex);
        } catch (Exception e) {
            return NAME_DEFAULT;
        }
    }

    private Component moneyLine(Player p) {
        double money = economy.getMoney(p.getUniqueId());
        return Component.text()
                .append(icon(ICON_MONEY))
                .append(Component.text(" Money: ", LABEL_COLOR))
                .append(Component.text(formatNumber(money), MONEY_COLOR))
                .build();
    }

    private Component gemsLine(Player p) {
        double gems = economy.getGems(p.getUniqueId());
        return Component.text()
                .append(icon(ICON_GEMS))
                .append(Component.text(" Gems: ", LABEL_COLOR))
                .append(Component.text(formatNumber(gems), GEMS_COLOR))
                .build();
    }

    private Component starsLine(Player p) {
        double stars = economy.getStars(p.getUniqueId());
        return Component.text()
                .append(icon(ICON_STARS))
                .append(Component.text(" Stars: ", LABEL_COLOR))
                .append(Component.text(formatNumber(stars), STARS_COLOR))
                .build();
    }

    private Component expLine(Player p) {
        long total = 0;
        long[] cached = expCache.get(p.getUniqueId());
        if (cached != null && cached.length >= 3) {
            total = cached[2];
        } else {
            total = p.getTotalExperience();
        }
        return Component.text()
                .append(icon(ICON_EXP))
                .append(Component.text(" EXP: ", LABEL_COLOR))
                .append(Component.text(String.format("%,d", total), EXP_COLOR))
                .build();
    }

    private Component pvpLine(Player p) {
        int[] stats = statsCache.getOrDefault(p.getUniqueId(), new int[]{0, 0, 0});
        return Component.text()
                .append(icon(ICON_PVP))
                .append(Component.text(" PVP Kills: ", LABEL_COLOR))
                .append(Component.text(String.format("%,d", stats[0]), PVP_COLOR))
                .build();
    }

    private Component pvmLine(Player p) {
        int[] stats = statsCache.getOrDefault(p.getUniqueId(), new int[]{0, 0, 0});
        return Component.text()
                .append(icon(ICON_PVM))
                .append(Component.text(" PVM Kills: ", LABEL_COLOR))
                .append(Component.text(String.format("%,d", stats[1]), PVM_COLOR))
                .build();
    }

    private Component deathsLine(Player p) {
        int[] stats = statsCache.getOrDefault(p.getUniqueId(), new int[]{0, 0, 0});
        return Component.text()
                .append(icon(ICON_DEATHS))
                .append(Component.text(" Deaths: ", LABEL_COLOR))
                .append(Component.text(String.format("%,d", stats[2]), DEATHS_COLOR))
                .build();
    }

    // --------------------------------------------------------
    // Helpers
    // --------------------------------------------------------

    private static Component icon(String glyph) {
        return Component.text(glyph, NamedTextColor.WHITE);
    }

    private TextColor playerNameColor(Player p) {
        if (chat != null) {
            String hex = chat.getNameColor(p.getUniqueId());
            if (hex != null) {
                TextColor c = TextColor.fromHexString(hex);
                if (c != null) return c;
            }
        }
        return NAME_DEFAULT;
    }

    private static String formatNumber(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.format("%,d", (long) v);
        }
        return String.format("%,.2f", v);
    }

    private void loadStatsAsync(Player player) {
        String username = player.getName();
        UUID uuid = player.getUniqueId();
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT pvp_kills, pvm_kills, deaths FROM su_players WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new int[]{
                                rs.getInt("pvp_kills"),
                                rs.getInt("pvm_kills"),
                                rs.getInt("deaths")
                        };
                    }
                }
            }
            return new int[]{0, 0, 0};
        }).thenAccept(stats -> {
            if (stats != null) statsCache.put(uuid, stats);
        });
    }

    private void loadExpAsync(Player player) {
        String username = player.getName();
        UUID uuid = player.getUniqueId();
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT level, current_exp, total_exp FROM su_players WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new long[]{
                                rs.getInt("level"),
                                rs.getInt("current_exp"),
                                rs.getLong("total_exp")
                        };
                    }
                }
            }
            return new long[]{1, 0, 0};
        }).thenAccept(exp -> {
            if (exp != null) expCache.put(uuid, exp);
        });
    }

    public void incrementPvpKill(String username) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET pvp_kills = pvp_kills + 1 WHERE username = ?")) {
                ps.setString(1, username);
                ps.executeUpdate();
            }
        }).thenRun(() -> refreshAllStatsFor(username));
    }

    public void incrementPvmKill(String username) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET pvm_kills = pvm_kills + 1 WHERE username = ?")) {
                ps.setString(1, username);
                ps.executeUpdate();
            }
        }).thenRun(() -> refreshAllStatsFor(username));
    }

    public void incrementDeath(String username) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET deaths = deaths + 1 WHERE username = ?")) {
                ps.setString(1, username);
                ps.executeUpdate();
            }
        }).thenRun(() -> refreshAllStatsFor(username));
    }

    private void refreshAllStatsFor(String username) {
        Player p = Bukkit.getPlayerExact(username);
        if (p != null) loadStatsAsync(p);
    }
}
