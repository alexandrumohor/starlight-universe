package com.starlightuniverse.minigame;

import com.starlightuniverse.auth.AuthManager;
import com.starlightuniverse.economy.EconomyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class MinigameManager {

    private static final long ANSWER_WINDOW_MS = 30_000L;
    private static final long ANSWER_WINDOW_TICKS = 20L * 30L;

    private static final int MIN_INTERVAL_MIN = 15;
    private static final int MAX_INTERVAL_MIN = 30;
    private static final int FIRST_INTERVAL_MIN_MIN = 3;
    private static final int FIRST_INTERVAL_MIN_MAX = 5;

    private static final double MIN_MONEY_REWARD = 100.0;
    private static final double MAX_MONEY_REWARD = 500.0;
    private static final double GEM_REWARD_CHANCE = 0.25;
    private static final int MIN_GEM_REWARD = 5;
    private static final int MAX_GEM_REWARD = 20;
    private static final int STREAK_THRESHOLD = 3;

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor PURPLE = TextColor.color(0xAA00AA);

    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final AuthManager authManager;
    private final Random random = new Random();

    private final AtomicReference<ActiveMinigame> currentGame = new AtomicReference<>();
    private final Map<UUID, Integer> streaks = new ConcurrentHashMap<>();

    private BukkitTask scheduleTask;
    private BukkitTask timeoutTask;
    private boolean paused = false;

    public MinigameManager(JavaPlugin plugin, EconomyManager economyManager, AuthManager authManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.authManager = authManager;
    }

    public void start() {
        scheduleNext(true);
    }

    public void shutdown() {
        if (scheduleTask != null) scheduleTask.cancel();
        if (timeoutTask != null) timeoutTask.cancel();
        currentGame.set(null);
    }

    // ==================== SCHEDULING ====================

    private void scheduleNext(boolean firstRun) {
        if (scheduleTask != null) scheduleTask.cancel();
        long delayTicks;
        if (firstRun) {
            int mins = FIRST_INTERVAL_MIN_MIN + random.nextInt(FIRST_INTERVAL_MIN_MAX - FIRST_INTERVAL_MIN_MIN + 1);
            delayTicks = 20L * 60L * mins;
        } else {
            int mins = MIN_INTERVAL_MIN + random.nextInt(MAX_INTERVAL_MIN - MIN_INTERVAL_MIN + 1);
            delayTicks = 20L * 60L * mins;
        }
        scheduleTask = Bukkit.getScheduler().runTaskLater(plugin, this::launchRandomGame, delayTicks);
    }

    private void launchRandomGame() {
        if (paused) {
            scheduleNext(false);
            return;
        }
        if (currentGame.get() != null) {
            scheduleNext(false);
            return;
        }
        if (countEligiblePlayers() < 1) {
            scheduleNext(false);
            return;
        }
        MinigameType type = MinigameType.values()[random.nextInt(MinigameType.values().length)];
        startGame(type);
    }

    public boolean startGame(MinigameType type) {
        if (currentGame.get() != null) return false;
        ActiveMinigame game = MinigameGenerators.build(type);
        if (!currentGame.compareAndSet(null, game)) return false;
        broadcastStart(game);
        if (timeoutTask != null) timeoutTask.cancel();
        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, this::onTimeout, ANSWER_WINDOW_TICKS);
        return true;
    }

    private void onTimeout() {
        ActiveMinigame game = currentGame.getAndSet(null);
        if (game == null) return;
        broadcastTimeout(game);
        scheduleNext(false);
    }

    // ==================== ANSWER HANDLING ====================

    public boolean tryAnswer(Player player, String message) {
        if (message == null) return false;
        UUID uuid = player.getUniqueId();
        if (!authManager.isAuthenticated(uuid)) return false;
        ActiveMinigame game = currentGame.get();
        if (game == null) return false;
        if (System.currentTimeMillis() - game.getStartTime() > ANSWER_WINDOW_MS) return false;
        if (!game.isCorrect(message)) return false;
        if (!currentGame.compareAndSet(game, null)) return false;

        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        long elapsedMs = System.currentTimeMillis() - game.getStartTime();
        awardWinner(player, game, elapsedMs);
        scheduleNext(false);
        return true;
    }

    private void awardWinner(Player winner, ActiveMinigame game, long elapsedMs) {
        UUID uuid = winner.getUniqueId();
        int prevStreak = streaks.getOrDefault(uuid, 0);
        int newStreak = prevStreak + 1;

        streaks.clear();
        streaks.put(uuid, newStreak);

        double baseMoney = MIN_MONEY_REWARD + random.nextDouble() * (MAX_MONEY_REWARD - MIN_MONEY_REWARD);
        double money = Math.floor(baseMoney);
        int gems = 0;
        if (random.nextDouble() < GEM_REWARD_CHANCE) {
            gems = MIN_GEM_REWARD + random.nextInt(MAX_GEM_REWARD - MIN_GEM_REWARD + 1);
        }
        boolean streakBonus = newStreak >= STREAK_THRESHOLD;
        if (streakBonus) {
            money *= 2;
            gems *= 2;
        }

        economyManager.addMoney(uuid, money);
        if (gems > 0) economyManager.addGems(uuid, gems);

        broadcastWinner(winner, game, money, gems, newStreak, streakBonus, elapsedMs);
    }

    // ==================== BROADCASTS ====================

    private void broadcastStart(ActiveMinigame game) {
        Component header = Component.text("★ MINIGAME ★", GOLD, TextDecoration.BOLD)
                .append(Component.text(" ", WHITE))
                .append(Component.text("[" + game.getType().getDisplayName() + "]", CYAN));

        Component prompt = Component.text("  ", WHITE);
        String[] parts = game.getPrompt().split("§");
        boolean highlight = false;
        for (String p : parts) {
            if (p.isEmpty()) { highlight = !highlight; continue; }
            Component chunk = highlight
                    ? Component.text(p, YELLOW, TextDecoration.BOLD)
                    : Component.text(p, WHITE);
            prompt = prompt.append(chunk);
            highlight = !highlight;
        }

        Component footer = Component.text("  Type the answer in chat within ", GRAY)
                .append(Component.text("30 seconds!", YELLOW, TextDecoration.BOLD));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!authManager.isAuthenticated(online.getUniqueId())) continue;
            online.sendMessage(Component.empty());
            online.sendMessage(header);
            online.sendMessage(prompt);
            online.sendMessage(footer);
            online.sendMessage(Component.empty());
            online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
        }
    }

    private void broadcastWinner(Player winner, ActiveMinigame game, double money, int gems,
                                  int streak, boolean streakBonus, long elapsedMs) {
        double secs = elapsedMs / 1000.0;
        Component line1 = Component.text("★ ", GOLD, TextDecoration.BOLD)
                .append(Component.text(winner.getName(), GREEN, TextDecoration.BOLD))
                .append(Component.text(" won the ", WHITE))
                .append(Component.text(game.getType().getDisplayName(), CYAN))
                .append(Component.text(" minigame in ", WHITE))
                .append(Component.text(String.format("%.1fs", secs), YELLOW))
                .append(Component.text("!", WHITE));

        Component line2 = Component.text("  Answer: ", GRAY)
                .append(Component.text(game.getPrimaryAnswer(), WHITE));

        Component rewardLine = Component.text("  Reward: ", GRAY)
                .append(Component.text("+$" + EconomyManager.format(money), GREEN));
        if (gems > 0) {
            rewardLine = rewardLine.append(Component.text(" +◆" + gems, CYAN));
        }
        if (streakBonus) {
            rewardLine = rewardLine.append(Component.text(" (x2 STREAK BONUS!)", PURPLE, TextDecoration.BOLD));
        }

        Component streakLine = null;
        if (streak >= 2) {
            streakLine = Component.text("  Win streak: ", GRAY)
                    .append(Component.text(streak, streak >= STREAK_THRESHOLD ? PURPLE : YELLOW,
                            TextDecoration.BOLD));
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!authManager.isAuthenticated(online.getUniqueId())) continue;
            online.sendMessage(Component.empty());
            online.sendMessage(line1);
            online.sendMessage(line2);
            online.sendMessage(rewardLine);
            if (streakLine != null) online.sendMessage(streakLine);
            online.sendMessage(Component.empty());
            if (online.equals(winner)) {
                online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            } else {
                online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f);
            }
        }
    }

    private void broadcastTimeout(ActiveMinigame game) {
        Component line1 = Component.text("★ ", GOLD)
                .append(Component.text("Nobody answered the ", GRAY))
                .append(Component.text(game.getType().getDisplayName(), CYAN))
                .append(Component.text(" minigame!", GRAY));
        Component line2 = Component.text("  Answer was: ", GRAY)
                .append(Component.text(game.getPrimaryAnswer(), WHITE));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!authManager.isAuthenticated(online.getUniqueId())) continue;
            online.sendMessage(Component.empty());
            online.sendMessage(line1);
            online.sendMessage(line2);
            online.sendMessage(Component.empty());
        }
    }

    // ==================== ADMIN CONTROLS ====================

    public boolean forceStart(MinigameType type) {
        if (currentGame.get() != null) return false;
        if (scheduleTask != null) scheduleTask.cancel();
        return startGame(type);
    }

    public boolean skipCurrent() {
        ActiveMinigame game = currentGame.getAndSet(null);
        if (game == null) return false;
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        Component line = Component.text("★ ", GOLD)
                .append(Component.text("Minigame skipped by staff.", RED));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!authManager.isAuthenticated(online.getUniqueId())) continue;
            online.sendMessage(line);
        }
        scheduleNext(false);
        return true;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        if (paused) {
            ActiveMinigame game = currentGame.getAndSet(null);
            if (game != null) {
                if (timeoutTask != null) {
                    timeoutTask.cancel();
                    timeoutTask = null;
                }
            }
        }
    }

    public boolean isPaused() { return paused; }

    public ActiveMinigame getCurrentGame() { return currentGame.get(); }

    public int getStreak(UUID uuid) { return streaks.getOrDefault(uuid, 0); }

    public List<UUID> getStreakHolders() {
        return new ArrayList<>(streaks.keySet());
    }

    // ==================== HELPERS ====================

    private int countEligiblePlayers() {
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (authManager.isAuthenticated(p.getUniqueId())) count++;
        }
        return count;
    }
}
