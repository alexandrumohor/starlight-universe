package com.starlightuniverse.boss;

import com.starlightuniverse.arena.ArenaWorldManager;
import com.starlightuniverse.arena.ArenaWorlds;
import com.starlightuniverse.crate.CrateManager;
import com.starlightuniverse.crate.CrateType;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BossKillManager {

    private static final long BOSS_COOLDOWN_TICKS = 2L * 60L * 60L * 20L; // 2 hours in ticks
    private static final long RESPAWN_COOLDOWN_MS = 30_000L;
    private static final long MAX_FIGHT_TICKS = 30L * 60L * 20L; // 30 min safety cap

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor PURPLE = TextColor.color(0xAA00AA);

    public static final NamespacedKey BOSS_MARK_KEY = new NamespacedKey("starlightuniverse", "boss_marker");

    private final JavaPlugin plugin;
    private final EconomyManager economy;
    private final CrateManager crateManager;
    private final ArenaWorldManager arenaWorldManager;

    private BossFight active;
    private BukkitTask tickTask;
    private long lastBossEndTick = -1;

    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    public BossKillManager(JavaPlugin plugin, EconomyManager economy,
                           CrateManager crateManager, ArenaWorldManager arenaWorldManager) {
        this.plugin = plugin;
        this.economy = economy;
        this.crateManager = crateManager;
        this.arenaWorldManager = arenaWorldManager;
    }

    public void start() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        if (active != null) forceEnd();
    }

    public boolean hasActiveBoss() { return active != null && !active.ended; }
    public BossFight getActive() { return active; }

    public long remainingCooldownSeconds() {
        if (lastBossEndTick < 0) return 0;
        long elapsed = Bukkit.getCurrentTick() - lastBossEndTick;
        long remaining = BOSS_COOLDOWN_TICKS - elapsed;
        if (remaining <= 0) return 0;
        return remaining / 20L;
    }

    // ── Boss spawn ──

    public boolean startBoss(BossType type, Player initiator) {
        if (hasActiveBoss()) {
            Msg.error(initiator, "A boss fight is already active!");
            return false;
        }
        long cd = remainingCooldownSeconds();
        if (cd > 0) {
            Msg.error(initiator, "Boss cooldown active: " + formatDuration(cd) + " remaining.");
            return false;
        }
        if (!arenaWorldManager.isReady(ArenaWorlds.BOSS_WORLD)) {
            Msg.error(initiator, "The boss arena is not ready yet.");
            return false;
        }
        World world = Bukkit.getWorld(ArenaWorlds.BOSS_WORLD);
        if (world == null) {
            Msg.error(initiator, "Boss arena world is not loaded!");
            return false;
        }

        Location spawn = new Location(world,
                ArenaWorlds.CENTER_X + 0.5,
                ArenaWorlds.SPAWN_Y,
                ArenaWorlds.CENTER_Z + 0.5);

        LivingEntity boss = (LivingEntity) world.spawnEntity(spawn, type.getEntityType());
        boss.getPersistentDataContainer().set(BOSS_MARK_KEY, PersistentDataType.STRING, type.name());
        boss.setRemoveWhenFarAway(false);
        boss.setPersistent(true);

        double hp = type.getMaxHealth();
        var maxHpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHpAttr != null) maxHpAttr.setBaseValue(hp);
        boss.setHealth(hp);

        boss.customName(Component.text(type.getDisplayName(), type.getColor(), TextDecoration.BOLD));
        boss.setCustomNameVisible(true);

        if (boss instanceof IronGolem golem && type == BossType.INFERNAL_GOLEM) {
            golem.setPlayerCreated(false);
            golem.setFireTicks(Integer.MAX_VALUE);
        }
        if (boss instanceof Wither wither) {
            wither.setInvulnerabilityTicks(0);
        }
        if (boss instanceof EnderDragon dragon) {
            dragon.setPhase(EnderDragon.Phase.CIRCLING);
        }

        BossBar bar = Bukkit.createBossBar(
                "§l" + type.getDisplayName() + " §r§7— §f" + (int) hp + " HP",
                type.getBarColor(), BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);

        BossFight fight = new BossFight(type, boss, bar, Bukkit.getCurrentTick());
        this.active = fight;

        for (Player online : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(online);
        }

        Component announce = Msg.prefix()
                .append(Component.text("A ", GOLD))
                .append(Component.text(type.getDisplayName(), type.getColor(), TextDecoration.BOLD))
                .append(Component.text(" has appeared in the Boss Arena! ", GOLD))
                .append(Component.text("(", GRAY))
                .append(Component.text((int) hp + " HP", YELLOW))
                .append(Component.text(")", GRAY));

        Component join = Msg.prefix()
                .append(Component.text("Use ", GRAY))
                .append(Component.text("/bosskill join", CYAN))
                .append(Component.text(" to fight it. Top damagers win Stars, Gems and Crate Keys!", GRAY));

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(announce);
            online.sendMessage(join);
            online.playSound(online.getLocation(), org.bukkit.Sound.EVENT_RAID_HORN, 1.0f, 0.5f);
        }

        plugin.getLogger().info("[SU] " + type.getDisplayName() + " boss spawned by " + initiator.getName());
        return true;
    }

    public void joinBoss(Player player) {
        if (!hasActiveBoss()) {
            Msg.error(player, "There is no active boss fight!");
            return;
        }
        if (active.participants.contains(player.getUniqueId())) {
            Msg.error(player, "You are already in the boss fight! Use /bosskill leave to leave.");
            return;
        }
        World world = Bukkit.getWorld(ArenaWorlds.BOSS_WORLD);
        if (world == null) {
            Msg.error(player, "Boss arena world is not loaded!");
            return;
        }
        active.returnLocations.put(player.getUniqueId(), player.getLocation());
        active.returnModes.put(player.getUniqueId(), player.getGameMode());
        active.participants.add(player.getUniqueId());

        Location target = new Location(world,
                ArenaWorlds.CENTER_X + ThreadLocalRandom.current().nextInt(-15, 15) + 0.5,
                ArenaWorlds.SPAWN_Y,
                ArenaWorlds.CENTER_Z + ThreadLocalRandom.current().nextInt(-15, 15) + 0.5);
        player.teleport(target);
        player.setGameMode(GameMode.SURVIVAL);
        active.bossBar.addPlayer(player);
        Msg.success(player, "You joined the " + active.type.getDisplayName() + " fight! Deal damage to earn rewards.");
    }

    public void leaveBoss(Player player) {
        if (!hasActiveBoss()) {
            Msg.error(player, "There is no active boss fight!");
            return;
        }
        if (!active.participants.contains(player.getUniqueId())) {
            Msg.error(player, "You are not in the boss fight!");
            return;
        }
        returnPlayer(player, false);
        active.participants.remove(player.getUniqueId());
        active.bossBar.removePlayer(player);
        Msg.info(player, "You left the boss fight.");
    }

    private void returnPlayer(Player player, boolean dead) {
        UUID uuid = player.getUniqueId();
        Location back = active.returnLocations.remove(uuid);
        GameMode mode = active.returnModes.remove(uuid);
        if (back != null) player.teleport(back);
        player.setGameMode(mode != null ? mode : GameMode.SURVIVAL);
        player.setHealth(Math.min(20, player.getMaxHealth()));
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.setFireTicks(0);
        if (dead) {
            Msg.info(player, "You died. Wait 30 seconds before /bosskill join again.");
            playerCooldowns.put(uuid, System.currentTimeMillis() + RESPAWN_COOLDOWN_MS);
        }
    }

    public long playerRespawnRemaining(UUID uuid) {
        Long expiry = playerCooldowns.get(uuid);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0) {
            playerCooldowns.remove(uuid);
            return 0;
        }
        return remaining / 1000L;
    }

    // ── Damage tracking ──

    public boolean isBoss(LivingEntity entity) {
        if (entity == null) return false;
        return entity.getPersistentDataContainer().has(BOSS_MARK_KEY);
    }

    public void handleBossDamage(Player attacker, LivingEntity boss, double damage) {
        if (active == null || active.ended) return;
        if (boss != active.entity) return;
        if (!active.participants.contains(attacker.getUniqueId())) {
            active.participants.add(attacker.getUniqueId());
            active.returnLocations.put(attacker.getUniqueId(), attacker.getLocation());
            active.returnModes.put(attacker.getUniqueId(), attacker.getGameMode());
            active.bossBar.addPlayer(attacker);
        }
        active.addDamage(attacker.getUniqueId(), damage);
    }

    public void handleBossDeath(LivingEntity boss) {
        if (active == null || active.ended) return;
        if (boss != active.entity) return;
        finishFight(false);
    }

    public void handlePlayerDeath(Player player) {
        if (active == null || active.ended) return;
        if (!active.participants.contains(player.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) returnPlayer(player, true);
            if (active != null) {
                active.bossBar.removePlayer(player);
                active.participants.remove(player.getUniqueId());
            }
        }, 5L);
    }

    public void handlePlayerQuit(Player player) {
        if (active == null || active.ended) return;
        if (!active.participants.contains(player.getUniqueId())) return;
        active.bossBar.removePlayer(player);
        active.participants.remove(player.getUniqueId());
        active.returnLocations.remove(player.getUniqueId());
        active.returnModes.remove(player.getUniqueId());
    }

    // ── Tick ──

    private void tick() {
        if (active == null || active.ended) return;

        LivingEntity boss = active.entity;
        if (boss == null || boss.isDead() || !boss.isValid()) {
            finishFight(false);
            return;
        }

        double maxHp = active.type.getMaxHealth();
        double hp = boss.getHealth();
        double progress = Math.max(0, Math.min(1, hp / maxHp));
        active.bossBar.setProgress(progress);
        active.bossBar.setTitle("§l" + active.type.getDisplayName() + " §r§7— §f" + (int) hp + " / " + (int) maxHp + " HP");

        long elapsed = Bukkit.getCurrentTick() - active.startTick;
        if (elapsed >= MAX_FIGHT_TICKS) {
            for (UUID uuid : new ArrayList<>(active.participants)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) Msg.error(p, "Boss fight timed out — no rewards.");
            }
            finishFight(true);
        }
    }

    private void forceEnd() {
        if (active == null) return;
        if (active.entity != null && !active.entity.isDead()) active.entity.remove();
        active.bossBar.removeAll();
        for (UUID uuid : new ArrayList<>(active.participants)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) returnPlayer(p, false);
        }
        active.ended = true;
        active = null;
    }

    private void finishFight(boolean timedOut) {
        if (active == null || active.ended) return;
        active.ended = true;

        BossFight fight = active;
        this.active = null;
        this.lastBossEndTick = Bukkit.getCurrentTick();

        fight.bossBar.removeAll();
        if (fight.entity != null && !fight.entity.isDead()) fight.entity.remove();

        if (!timedOut && !fight.damageDealt.isEmpty()) {
            distributeRewards(fight);
        }

        for (UUID uuid : new ArrayList<>(fight.participants)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) returnPlayerWithFight(fight, p);
        }
    }

    private void returnPlayerWithFight(BossFight fight, Player player) {
        UUID uuid = player.getUniqueId();
        Location back = fight.returnLocations.remove(uuid);
        GameMode mode = fight.returnModes.remove(uuid);
        if (back != null && back.getWorld() != null) player.teleport(back);
        player.setGameMode(mode != null ? mode : GameMode.SURVIVAL);
        player.setHealth(Math.min(20, player.getMaxHealth()));
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.setFireTicks(0);
    }

    // ── Rewards ──

    private void distributeRewards(BossFight fight) {
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(fight.damageDealt.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        double totalDamage = 0;
        for (var entry : sorted) totalDamage += entry.getValue();
        if (totalDamage <= 0) return;

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(Msg.prefix().append(Component.text("═══════════════════════════════════", GOLD)));
            online.sendMessage(Msg.prefix()
                    .append(Component.text("The ", GOLD))
                    .append(Component.text(fight.type.getDisplayName(), fight.type.getColor(), TextDecoration.BOLD))
                    .append(Component.text(" has been defeated!", GOLD)));
        }

        int[] starRewards = {3, 2, 1};
        for (int i = 0; i < sorted.size(); i++) {
            var entry = sorted.get(i);
            UUID uuid = entry.getKey();
            double dmg = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);
            String name = player != null ? player.getName() : uuid.toString().substring(0, 8);

            if (i < 3) {
                int stars = starRewards[i];
                int gems = 50;
                economy.addStars(uuid, stars);
                economy.addGems(uuid, gems);
                if (player != null && player.isOnline()) {
                    player.getInventory().addItem(crateManager.createKey(CrateType.COSMIC, 1));
                }

                String medal = i == 0 ? "🥇" : (i == 1 ? "🥈" : "🥉");
                TextColor c = i == 0 ? GOLD : (i == 1 ? TextColor.color(0xC0C0C0) : TextColor.color(0xCD7F32));

                Component line = Msg.prefix()
                        .append(Component.text(medal + " #" + (i + 1) + " ", c, TextDecoration.BOLD))
                        .append(Component.text(name, YELLOW, TextDecoration.BOLD))
                        .append(Component.text(" — ", GRAY))
                        .append(Component.text((int) dmg + " damage", RED))
                        .append(Component.text(" — Reward: ", GRAY))
                        .append(Component.text("★" + stars + " ", PURPLE))
                        .append(Component.text("◆" + gems + " ", CYAN))
                        .append(Component.text("+ Cosmic Key", GREEN));
                for (Player online : Bukkit.getOnlinePlayers()) online.sendMessage(line);
                if (player != null && player.isOnline()) {
                    Msg.success(player, "You got ★" + stars + ", ◆" + gems + " and a Cosmic Crate Key!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
            } else {
                double share = dmg / totalDamage;
                int money = (int) Math.max(2000, Math.min(5000, 2000 + share * 15000));
                economy.addMoney(uuid, money);
                if (player != null && player.isOnline()) {
                    Msg.info(player, "You got " + EconomyManager.MONEY_ICON + " $" + EconomyManager.format(money) + " for " + (int) dmg + " damage to the " + fight.type.getDisplayName() + "!");
                }
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(Msg.prefix().append(Component.text("═══════════════════════════════════", GOLD)));
        }
    }

    // ── Utility ──

    private String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    public void handleJoin(Player player) {
        if (active == null || active.ended) return;
        active.bossBar.addPlayer(player);
    }
}
