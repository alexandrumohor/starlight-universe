package com.starlightuniverse.pvp;

import com.starlightuniverse.arena.ArenaWorldManager;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PvPManager {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;
    private final ArenaWorldManager arenaWorlds;

    private final Deque<UUID> rankedQueue = new ArrayDeque<>();
    private final Deque<UUID> unrankedQueue = new ArrayDeque<>();
    private final Set<UUID> inQueue = new HashSet<>();

    private final Map<UUID, PvPMatch> activeMatches = new ConcurrentHashMap<>();
    private final Set<PvPMatch> matchSet = ConcurrentHashMap.newKeySet();

    private final Map<UUID, PvPStats> statsCache = new ConcurrentHashMap<>();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> spectatorReturns = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> spectatorReturnModes = new ConcurrentHashMap<>();

    private final Set<UUID> internalTeleport = ConcurrentHashMap.newKeySet();

    private BukkitTask tickTask;

    public PvPManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy,
                      ArenaWorldManager arenaWorlds) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
        this.arenaWorlds = arenaWorlds;
    }

    public void start() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        for (PvPMatch match : new ArrayList<>(matchSet)) {
            forceEndMatch(match, null);
        }
        for (UUID spec : new HashSet<>(spectators)) {
            Player p = Bukkit.getPlayer(spec);
            if (p != null) leaveSpectate(p);
        }
    }

    // ── Stats ──

    public PvPStats getStats(UUID uuid) {
        return statsCache.computeIfAbsent(uuid, k -> new PvPStats());
    }

    public void loadStats(UUID uuid, String username) {
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT elo, wins, losses, arena_kills, arena_deaths, current_streak, best_streak " +
                            "FROM su_pvp_stats WHERE username = ?")) {
                ps.setString(1, username.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new PvPStats(
                                rs.getInt("elo"),
                                rs.getInt("wins"),
                                rs.getInt("losses"),
                                rs.getInt("arena_kills"),
                                rs.getInt("arena_deaths"),
                                rs.getInt("current_streak"),
                                rs.getInt("best_streak")
                        );
                    }
                }
                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT IGNORE INTO su_pvp_stats (username, elo) VALUES (?, ?)")) {
                    insert.setString(1, username.toLowerCase());
                    insert.setInt(2, PvPArena.STARTING_ELO);
                    insert.executeUpdate();
                }
                return new PvPStats();
            }
        }).thenAccept(stats -> {
            if (stats != null) statsCache.put(uuid, stats);
        });
    }

    public void unloadStats(UUID uuid) {
        statsCache.remove(uuid);
    }

    private void saveStats(String username, PvPStats stats) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_pvp_stats (username, elo, wins, losses, arena_kills, arena_deaths, current_streak, best_streak) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE elo = VALUES(elo), wins = VALUES(wins), " +
                            "losses = VALUES(losses), arena_kills = VALUES(arena_kills), " +
                            "arena_deaths = VALUES(arena_deaths), current_streak = VALUES(current_streak), " +
                            "best_streak = VALUES(best_streak)")) {
                ps.setString(1, username.toLowerCase());
                ps.setInt(2, stats.elo);
                ps.setInt(3, stats.wins);
                ps.setInt(4, stats.losses);
                ps.setInt(5, stats.arenaKills);
                ps.setInt(6, stats.arenaDeaths);
                ps.setInt(7, stats.currentStreak);
                ps.setInt(8, stats.bestStreak);
                ps.executeUpdate();
            }
        });
    }

    public List<TopEntry> getTop() {
        List<TopEntry> result = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT username, elo, wins, losses FROM su_pvp_stats " +
                             "WHERE wins + losses > 0 ORDER BY elo DESC LIMIT 10")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new TopEntry(
                            rs.getString("username"),
                            rs.getInt("elo"),
                            rs.getInt("wins"),
                            rs.getInt("losses")
                    ));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[SU] Failed to load PvP top: " + e.getMessage());
        }
        return result;
    }

    public record TopEntry(String username, int elo, int wins, int losses) {}

    // ── Queue ──

    public boolean isInQueue(UUID uuid) { return inQueue.contains(uuid); }
    public boolean isInMatch(UUID uuid) { return activeMatches.containsKey(uuid); }
    public boolean isSpectating(UUID uuid) { return spectators.contains(uuid); }

    public PvPMatch getMatch(UUID uuid) { return activeMatches.get(uuid); }

    public void joinQueue(Player player, boolean ranked) {
        UUID uuid = player.getUniqueId();
        if (inQueue.contains(uuid)) {
            Msg.error(player, "You are already in the PvP queue!");
            return;
        }
        if (isInMatch(uuid)) {
            Msg.error(player, "You are already in a match!");
            return;
        }
        if (isSpectating(uuid)) {
            Msg.error(player, "Leave spectate mode first!");
            return;
        }
        if (!arenaWorlds.isReady(PvPArena.ARENA_WORLD)) {
            if (arenaWorlds.isBuilding(PvPArena.ARENA_WORLD)) {
                Msg.error(player, "The PvP arena is still being built. Please try again in a few minutes!");
            } else {
                Msg.error(player, "The PvP arena is not available right now.");
            }
            return;
        }

        inQueue.add(uuid);
        if (ranked) {
            rankedQueue.addLast(uuid);
            Msg.success(player, "Joined RANKED PvP queue!");
        } else {
            unrankedQueue.addLast(uuid);
            Msg.success(player, "Joined UNRANKED PvP queue!");
        }
        Msg.gray(player, "Type /pvp leave to leave the queue.");
    }

    public void leaveQueue(Player player) {
        UUID uuid = player.getUniqueId();
        if (!inQueue.contains(uuid)) {
            if (isInMatch(uuid)) {
                forfeitMatch(player);
                return;
            }
            Msg.error(player, "You are not in the PvP queue!");
            return;
        }
        removeFromQueue(uuid);
        Msg.info(player, "Left the PvP queue.");
    }

    private void removeFromQueue(UUID uuid) {
        inQueue.remove(uuid);
        rankedQueue.remove(uuid);
        unrankedQueue.remove(uuid);
    }

    // ── Spectate ──

    public void enterSpectate(Player player) {
        UUID uuid = player.getUniqueId();
        if (isInMatch(uuid)) {
            Msg.error(player, "You are in a match! Use /pvp leave first.");
            return;
        }
        if (isSpectating(uuid)) {
            Msg.error(player, "You are already spectating!");
            return;
        }
        if (!arenaWorlds.isReady(PvPArena.ARENA_WORLD)) {
            Msg.error(player, "The PvP arena is not ready yet.");
            return;
        }

        Location spec = PvPArena.spectate();
        if (spec == null) {
            Msg.error(player, "The arena world is not loaded!");
            return;
        }

        spectatorReturns.put(uuid, player.getLocation());
        spectatorReturnModes.put(uuid, player.getGameMode());
        spectators.add(uuid);
        internalTeleport.add(uuid);
        player.teleport(spec);
        internalTeleport.remove(uuid);
        player.setGameMode(GameMode.SPECTATOR);
        Msg.info(player, "Spectating the arena. Type /pvp leave to return.");
    }

    public void leaveSpectate(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isSpectating(uuid)) return;

        spectators.remove(uuid);
        Location back = spectatorReturns.remove(uuid);
        GameMode mode = spectatorReturnModes.remove(uuid);
        player.setGameMode(mode != null ? mode : GameMode.SURVIVAL);
        if (back != null) {
            internalTeleport.add(uuid);
            player.teleport(back);
            internalTeleport.remove(uuid);
        }
        Msg.info(player, "Left spectate mode.");
    }

    // ── Internal teleport tracking ──

    public boolean isInternalTeleport(UUID uuid) {
        return internalTeleport.contains(uuid);
    }

    // ── Tick loop ──

    private void tick() {
        matchmake();
        cleanQueue();
        updateQueueBars();

        for (PvPMatch match : new ArrayList<>(matchSet)) {
            tickMatch(match);
        }
    }

    private void cleanQueue() {
        Iterator<UUID> it = inQueue.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                it.remove();
                rankedQueue.remove(uuid);
                unrankedQueue.remove(uuid);
            }
        }
    }

    private void updateQueueBars() {
        int total = rankedQueue.size() + unrankedQueue.size();
        if (total == 0) return;

        int idx = 1;
        for (UUID uuid : rankedQueue) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendActionBar(Component.text("PvP queue (Ranked): " + idx + " / " + rankedQueue.size(), GOLD));
            }
            idx++;
        }
        idx = 1;
        for (UUID uuid : unrankedQueue) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendActionBar(Component.text("PvP queue (Unranked): " + idx + " / " + unrankedQueue.size(), GOLD));
            }
            idx++;
        }
    }

    private void matchmake() {
        matchmakeUnranked();
        matchmakeRanked();
    }

    private void matchmakeUnranked() {
        while (unrankedQueue.size() >= 2) {
            UUID a = unrankedQueue.pollFirst();
            UUID b = unrankedQueue.pollFirst();
            if (!startMatch(a, b, false)) {
                if (a != null) inQueue.remove(a);
                if (b != null) inQueue.remove(b);
            }
        }
    }

    private void matchmakeRanked() {
        if (rankedQueue.size() < 2) return;

        List<UUID> list = new ArrayList<>(rankedQueue);
        boolean[] paired = new boolean[list.size()];

        for (int i = 0; i < list.size(); i++) {
            if (paired[i]) continue;
            UUID a = list.get(i);
            PvPStats sa = getStats(a);
            int bestIdx = -1;
            int bestDiff = Integer.MAX_VALUE;

            for (int j = i + 1; j < list.size(); j++) {
                if (paired[j]) continue;
                UUID b = list.get(j);
                PvPStats sb = getStats(b);
                int diff = Math.abs(sa.elo - sb.elo);
                if (diff <= PvPArena.MATCH_ELO_MAX_DIFF && diff < bestDiff) {
                    bestDiff = diff;
                    bestIdx = j;
                }
            }

            if (bestIdx != -1) {
                UUID b = list.get(bestIdx);
                paired[i] = true;
                paired[bestIdx] = true;
                rankedQueue.remove(a);
                rankedQueue.remove(b);
                if (!startMatch(a, b, true)) {
                    inQueue.remove(a);
                    inQueue.remove(b);
                }
            }
        }
    }

    private boolean startMatch(UUID a, UUID b, boolean ranked) {
        if (a == null || b == null) return false;

        Player pa = Bukkit.getPlayer(a);
        Player pb = Bukkit.getPlayer(b);
        if (pa == null || pb == null || !pa.isOnline() || !pb.isOnline()) return false;

        Location pos1 = PvPArena.pos1();
        Location pos2 = PvPArena.pos2();
        if (pos1 == null || pos2 == null) {
            Msg.error(pa, "PvP arena world is not available!");
            Msg.error(pb, "PvP arena world is not available!");
            return false;
        }

        inQueue.remove(a);
        inQueue.remove(b);

        PvPStats sa = getStats(a);
        PvPStats sb = getStats(b);

        PvPMatch match = new PvPMatch(a, b, ranked,
                pa.getLocation(), pb.getLocation(),
                pa.getGameMode(), pb.getGameMode(),
                sa.elo, sb.elo);
        activeMatches.put(a, match);
        activeMatches.put(b, match);
        matchSet.add(match);

        internalTeleport.add(a);
        internalTeleport.add(b);
        pa.teleport(pos1);
        pb.teleport(pos2);
        internalTeleport.remove(a);
        internalTeleport.remove(b);

        pa.setGameMode(GameMode.SURVIVAL);
        pb.setGameMode(GameMode.SURVIVAL);

        resetPlayerForRound(pa);
        resetPlayerForRound(pb);

        Component title = Component.text("PvP Match", GOLD, TextDecoration.BOLD);
        Component modeText = ranked
                ? Component.text("Ranked", TextColor.color(0xFF4500))
                : Component.text("Unranked", TextColor.color(0x55FFFF));
        pa.sendMessage(Msg.prefix().append(title));
        pa.sendMessage(Msg.prefix().append(Component.text("Opponent: ", GRAY))
                .append(Component.text(pb.getName(), GOLD)));
        pa.sendMessage(Msg.prefix().append(Component.text("Mode: ", GRAY)).append(modeText));
        pa.sendMessage(Msg.prefix().append(Component.text("Best of 3 rounds — good luck!", YELLOW)));

        pb.sendMessage(Msg.prefix().append(title));
        pb.sendMessage(Msg.prefix().append(Component.text("Opponent: ", GRAY))
                .append(Component.text(pa.getName(), GOLD)));
        pb.sendMessage(Msg.prefix().append(Component.text("Mode: ", GRAY)).append(modeText));
        pb.sendMessage(Msg.prefix().append(Component.text("Best of 3 rounds — good luck!", YELLOW)));

        match.state = PvPMatch.State.COUNTDOWN;
        match.stateSeconds = 0;

        return true;
    }

    private void tickMatch(PvPMatch match) {
        Player p1 = Bukkit.getPlayer(match.p1);
        Player p2 = Bukkit.getPlayer(match.p2);

        if (p1 == null || !p1.isOnline()) {
            handleDisconnect(match, match.p1);
            return;
        }
        if (p2 == null || !p2.isOnline()) {
            handleDisconnect(match, match.p2);
            return;
        }

        match.stateSeconds++;

        switch (match.state) {
            case COUNTDOWN -> tickCountdown(match, p1, p2);
            case ROUND_ACTIVE -> tickRoundActive(match, p1, p2);
            case PAUSE -> tickPause(match, p1, p2);
            case ROUND_END, ENDED -> {}
        }
    }

    private void tickCountdown(PvPMatch match, Player p1, Player p2) {
        int remaining = PvPArena.COUNTDOWN_SECONDS - match.stateSeconds;
        if (remaining > 0) {
            Component actionBar = Component.text("Round " + match.round + " starts in " + remaining + "...", GOLD);
            p1.sendActionBar(actionBar);
            p2.sendActionBar(actionBar);
            p1.setFreezeTicks(20);
            p2.setFreezeTicks(20);
        } else {
            match.state = PvPMatch.State.ROUND_ACTIVE;
            match.stateSeconds = 0;
            match.roundSeconds = 0;
            match.p1LastPos = p1.getLocation();
            match.p2LastPos = p2.getLocation();
            match.p1IdleSeconds = 0;
            match.p2IdleSeconds = 0;
            match.p1CampWarned = false;
            match.p2CampWarned = false;

            Component go = Component.text("FIGHT!", RED, TextDecoration.BOLD);
            p1.sendActionBar(go);
            p2.sendActionBar(go);
        }
    }

    private void tickRoundActive(PvPMatch match, Player p1, Player p2) {
        match.roundSeconds++;

        int remaining = PvPArena.ROUND_MAX_SECONDS - match.roundSeconds;
        Component bar = Component.text(
                "Round " + match.round + " — " + match.p1Wins + " vs " + match.p2Wins +
                        "  |  " + formatTime(remaining), GOLD);
        p1.sendActionBar(bar);
        p2.sendActionBar(bar);

        if (isOutsideArena(p1.getLocation())) tpBack(p1);
        if (isOutsideArena(p2.getLocation())) tpBack(p2);

        checkCamp(match, p1, p2);

        if (remaining <= 0) {
            broadcast(match, "Round " + match.round + " timed out — no winner.");
            endRound(match, null);
        }
    }

    private void tickPause(PvPMatch match, Player p1, Player p2) {
        int remaining = PvPArena.PAUSE_SECONDS - match.stateSeconds;
        if (remaining > 0) {
            Component bar = Component.text("Next round in " + remaining + "...  " +
                    match.p1Wins + " vs " + match.p2Wins, YELLOW);
            p1.sendActionBar(bar);
            p2.sendActionBar(bar);
        } else {
            match.round++;
            beginRound(match, p1, p2);
        }
    }

    private void beginRound(PvPMatch match, Player p1, Player p2) {
        Location pos1 = PvPArena.pos1();
        Location pos2 = PvPArena.pos2();
        if (pos1 == null || pos2 == null) {
            forceEndMatch(match, null);
            return;
        }

        internalTeleport.add(match.p1);
        internalTeleport.add(match.p2);
        p1.teleport(pos1);
        p2.teleport(pos2);
        internalTeleport.remove(match.p1);
        internalTeleport.remove(match.p2);

        resetPlayerForRound(p1);
        resetPlayerForRound(p2);

        match.state = PvPMatch.State.COUNTDOWN;
        match.stateSeconds = 0;
    }

    // ── Round outcome ──

    public void onDeathIntercept(Player deceased) {
        PvPMatch match = activeMatches.get(deceased.getUniqueId());
        if (match == null || match.state != PvPMatch.State.ROUND_ACTIVE) return;

        UUID winnerId = match.other(deceased.getUniqueId());
        Player winner = Bukkit.getPlayer(winnerId);

        PvPStats deadStats = getStats(deceased.getUniqueId());
        deadStats.arenaDeaths++;
        if (winner != null) {
            PvPStats wStats = getStats(winnerId);
            wStats.arenaKills++;
            saveStats(winner.getName(), wStats);
        }
        saveStats(deceased.getName(), deadStats);

        endRound(match, winnerId);
    }

    private void endRound(PvPMatch match, UUID roundWinner) {
        Player p1 = Bukkit.getPlayer(match.p1);
        Player p2 = Bukkit.getPlayer(match.p2);
        if (p1 == null || p2 == null) return;

        if (roundWinner != null) {
            if (roundWinner.equals(match.p1)) match.p1Wins++;
            else match.p2Wins++;

            String winnerName = roundWinner.equals(match.p1) ? p1.getName() : p2.getName();
            broadcast(match, "Round " + match.round + " won by " + winnerName + "!");
        }

        if (match.p1Wins >= PvPArena.ROUNDS_TO_WIN) {
            finishMatch(match, match.p1);
            return;
        }
        if (match.p2Wins >= PvPArena.ROUNDS_TO_WIN) {
            finishMatch(match, match.p2);
            return;
        }
        if (match.round >= PvPArena.MAX_ROUNDS) {
            UUID matchWinner;
            if (match.p1Wins > match.p2Wins) matchWinner = match.p1;
            else if (match.p2Wins > match.p1Wins) matchWinner = match.p2;
            else matchWinner = null;
            finishMatch(match, matchWinner);
            return;
        }

        internalTeleport.add(match.p1);
        internalTeleport.add(match.p2);
        p1.teleport(PvPArena.pos1());
        p2.teleport(PvPArena.pos2());
        internalTeleport.remove(match.p1);
        internalTeleport.remove(match.p2);

        resetPlayerForRound(p1);
        resetPlayerForRound(p2);

        match.state = PvPMatch.State.PAUSE;
        match.stateSeconds = 0;
    }

    private void finishMatch(PvPMatch match, UUID matchWinner) {
        match.state = PvPMatch.State.ENDED;
        match.winner = matchWinner;

        Player p1 = Bukkit.getPlayer(match.p1);
        Player p2 = Bukkit.getPlayer(match.p2);

        PvPStats s1 = getStats(match.p1);
        PvPStats s2 = getStats(match.p2);

        int newElo1 = s1.elo;
        int newElo2 = s2.elo;

        if (matchWinner == null) {
            broadcast(match, "Match is a DRAW! Final: " + match.p1Wins + " vs " + match.p2Wins);
            s1.currentStreak = 0;
            s2.currentStreak = 0;
        } else {
            double s1Result = matchWinner.equals(match.p1) ? 1.0 : 0.0;
            if (match.ranked) {
                int[] newElos = computeElo(s1.elo, s2.elo, s1Result);
                newElo1 = newElos[0];
                newElo2 = newElos[1];
                s1.elo = newElo1;
                s2.elo = newElo2;
            }

            if (matchWinner.equals(match.p1)) {
                s1.wins++;
                s2.losses++;
                s1.currentStreak++;
                if (s1.currentStreak > s1.bestStreak) s1.bestStreak = s1.currentStreak;
                s2.currentStreak = 0;
            } else {
                s2.wins++;
                s1.losses++;
                s2.currentStreak++;
                if (s2.currentStreak > s2.bestStreak) s2.bestStreak = s2.currentStreak;
                s1.currentStreak = 0;
            }

            Player winner = Bukkit.getPlayer(matchWinner);
            Player loser = Bukkit.getPlayer(match.other(matchWinner));
            if (winner != null) {
                giveRewards(winner, matchWinner.equals(match.p1) ? s1 : s2);
                incrementPvPKillsColumn(winner.getName());
            }
            String winnerName = winner != null ? winner.getName() : "?";
            broadcast(match, "Match won by " + winnerName + "! Final: " + match.p1Wins + " vs " + match.p2Wins);
            if (match.ranked) {
                int d1 = newElo1 - match.p1StartElo;
                int d2 = newElo2 - match.p2StartElo;
                if (p1 != null) {
                    Msg.info(p1, "Elo: " + match.p1StartElo + " → " + newElo1 +
                            " (" + (d1 >= 0 ? "+" : "") + d1 + ")");
                }
                if (p2 != null) {
                    Msg.info(p2, "Elo: " + match.p2StartElo + " → " + newElo2 +
                            " (" + (d2 >= 0 ? "+" : "") + d2 + ")");
                }
            }
            if (loser != null) {
                Msg.gray(loser, "You keep your inventory. Better luck next time!");
            }
        }

        if (p1 != null) saveStats(p1.getName(), s1);
        if (p2 != null) saveStats(p2.getName(), s2);

        returnPlayer(match.p1, match.p1Return, match.p1ReturnMode);
        returnPlayer(match.p2, match.p2Return, match.p2ReturnMode);

        activeMatches.remove(match.p1);
        activeMatches.remove(match.p2);
        matchSet.remove(match);
    }

    private int[] computeElo(int eloA, int eloB, double resultA) {
        double expectedA = 1.0 / (1.0 + Math.pow(10, (eloB - eloA) / 400.0));
        double expectedB = 1.0 - expectedA;
        double resultB = 1.0 - resultA;
        int newA = (int) Math.round(eloA + PvPArena.K_FACTOR * (resultA - expectedA));
        int newB = (int) Math.round(eloB + PvPArena.K_FACTOR * (resultB - expectedB));
        if (newA < 0) newA = 0;
        if (newB < 0) newB = 0;
        return new int[]{newA, newB};
    }

    private void giveRewards(Player winner, PvPStats stats) {
        UUID uuid = winner.getUniqueId();
        double money = PvPArena.WIN_MONEY;
        double gems = PvPArena.WIN_GEMS;

        int streakMoneyBonus = 0;
        int streakGemsBonus = 0;
        String streakLabel = null;
        int streak = stats.currentStreak;
        if (streak >= 10) {
            streakMoneyBonus = PvPArena.STREAK_10_MONEY;
            streakGemsBonus = PvPArena.STREAK_10_GEMS;
            streakLabel = "10x GODLIKE";
        } else if (streak >= 5) {
            streakMoneyBonus = PvPArena.STREAK_5_MONEY;
            streakGemsBonus = PvPArena.STREAK_5_GEMS;
            streakLabel = "5x RAMPAGE";
        } else if (streak >= 3) {
            streakMoneyBonus = PvPArena.STREAK_3_MONEY;
            streakGemsBonus = PvPArena.STREAK_3_GEMS;
            streakLabel = "3x TRIPLE";
        } else if (streak >= 2) {
            streakMoneyBonus = PvPArena.STREAK_2_MONEY;
            streakLabel = "2x DOUBLE";
        }

        double totalMoney = money + streakMoneyBonus;
        double totalGems = gems + streakGemsBonus;

        economy.addMoney(uuid, totalMoney);
        economy.addGems(uuid, totalGems);

        Msg.success(winner, "Victory! +$" + (int) totalMoney + " and " + (int) totalGems + " gems.");
        if (streakLabel != null) {
            Msg.cyan(winner, "Win streak " + streakLabel + "! Bonus: +$" + streakMoneyBonus +
                    (streakGemsBonus > 0 ? " and " + streakGemsBonus + " gems." : "."));
        }
    }

    private void incrementPvPKillsColumn(String username) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET pvp_kills = pvp_kills + 1 WHERE username = ?")) {
                ps.setString(1, username.toLowerCase());
                ps.executeUpdate();
            }
        });
    }

    private void returnPlayer(UUID uuid, Location back, GameMode mode) {
        Player p = Bukkit.getPlayer(uuid);
        if (p == null || !p.isOnline()) return;
        p.setHealth(Math.min(20, p.getMaxHealth()));
        p.setFoodLevel(20);
        p.setSaturation(5);
        p.setFireTicks(0);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
        if (back != null && back.getWorld() != null) {
            internalTeleport.add(uuid);
            p.teleport(back);
            internalTeleport.remove(uuid);
        }
        if (mode != null) p.setGameMode(mode);
    }

    private void handleDisconnect(PvPMatch match, UUID disconnected) {
        UUID winner = match.other(disconnected);
        Player wp = Bukkit.getPlayer(winner);
        if (wp != null) Msg.info(wp, "Opponent disconnected — you win by forfeit!");
        finishMatch(match, winner);
    }

    public void handlePlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        removeFromQueue(uuid);
        if (isSpectating(uuid)) {
            spectators.remove(uuid);
            spectatorReturns.remove(uuid);
            spectatorReturnModes.remove(uuid);
        }
        PvPMatch match = activeMatches.get(uuid);
        if (match != null) {
            handleDisconnect(match, uuid);
        }
    }

    private void forceEndMatch(PvPMatch match, UUID winner) {
        match.state = PvPMatch.State.ENDED;
        returnPlayer(match.p1, match.p1Return, match.p1ReturnMode);
        returnPlayer(match.p2, match.p2Return, match.p2ReturnMode);
        activeMatches.remove(match.p1);
        activeMatches.remove(match.p2);
        matchSet.remove(match);
    }

    public void forfeitMatch(Player player) {
        PvPMatch match = activeMatches.get(player.getUniqueId());
        if (match == null) return;
        UUID winner = match.other(player.getUniqueId());
        Msg.info(player, "You forfeited the match.");
        finishMatch(match, winner);
    }

    // ── Anti-camping ──

    private void checkCamp(PvPMatch match, Player p1, Player p2) {
        checkOne(match, p1, true);
        checkOne(match, p2, false);
    }

    private void checkOne(PvPMatch match, Player p, boolean isP1) {
        Location current = p.getLocation();
        Location last = isP1 ? match.p1LastPos : match.p2LastPos;
        boolean moved = last == null || last.getWorld() != current.getWorld() ||
                last.distanceSquared(current) >= (double) PvPArena.ANTI_CAMP_MIN_MOVE * PvPArena.ANTI_CAMP_MIN_MOVE;

        int idle;
        if (moved) {
            idle = 0;
            if (isP1) {
                match.p1LastPos = current;
                match.p1IdleSeconds = 0;
                match.p1CampWarned = false;
            } else {
                match.p2LastPos = current;
                match.p2IdleSeconds = 0;
                match.p2CampWarned = false;
            }
            return;
        }
        idle = (isP1 ? ++match.p1IdleSeconds : ++match.p2IdleSeconds);

        if (idle == PvPArena.ANTI_CAMP_WARN_SECONDS &&
                !(isP1 ? match.p1CampWarned : match.p2CampWarned)) {
            Msg.error(p, "Stop camping! Move or you will be teleported to the arena center.");
            if (isP1) match.p1CampWarned = true;
            else match.p2CampWarned = true;
        }
        if (idle >= PvPArena.ANTI_CAMP_TP_SECONDS) {
            Location center = PvPArena.center();
            if (center != null) {
                internalTeleport.add(p.getUniqueId());
                p.teleport(center);
                internalTeleport.remove(p.getUniqueId());
                Msg.info(p, "Teleported to the arena center for camping.");
            }
            if (isP1) {
                match.p1IdleSeconds = 0;
                match.p1CampWarned = false;
                match.p1LastPos = p.getLocation();
            } else {
                match.p2IdleSeconds = 0;
                match.p2CampWarned = false;
                match.p2LastPos = p.getLocation();
            }
        }
    }

    private boolean isOutsideArena(Location loc) {
        if (loc == null || loc.getWorld() == null) return true;
        if (!loc.getWorld().getName().equals(PvPArena.ARENA_WORLD)) return true;
        int dx = loc.getBlockX() - PvPArena.CENTER_X;
        int dz = loc.getBlockZ() - PvPArena.CENTER_Z;
        return dx * dx + dz * dz > PvPArena.ARENA_RADIUS * PvPArena.ARENA_RADIUS;
    }

    private void tpBack(Player p) {
        Location c = PvPArena.center();
        if (c == null) return;
        internalTeleport.add(p.getUniqueId());
        p.teleport(c);
        internalTeleport.remove(p.getUniqueId());
        Msg.info(p, "Stay inside the arena!");
    }

    // ── Utilities ──

    private void resetPlayerForRound(Player player) {
        player.setHealth(Math.min(20, player.getMaxHealth()));
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0);
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));

        for (ItemStack item : player.getInventory().getContents()) {
            resetDurability(item);
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            resetDurability(item);
        }
        resetDurability(player.getInventory().getItemInOffHand());
    }

    private void resetDurability(ItemStack item) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable dmg && dmg.hasDamage()) {
            dmg.setDamage(0);
            item.setItemMeta((ItemMeta) dmg);
        }
    }

    private void broadcast(PvPMatch match, String text) {
        Player p1 = Bukkit.getPlayer(match.p1);
        Player p2 = Bukkit.getPlayer(match.p2);
        Component msg = Msg.prefix().append(Component.text(text, YELLOW));
        if (p1 != null) p1.sendMessage(msg);
        if (p2 != null) p2.sendMessage(msg);
        for (UUID spec : spectators) {
            Player p = Bukkit.getPlayer(spec);
            if (p != null) p.sendMessage(msg);
        }
    }

    private String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int m = seconds / 60;
        int s = seconds % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }
}
