package com.starlightuniverse.mob;

import com.starlightuniverse.arena.ArenaWorldManager;
import com.starlightuniverse.arena.ArenaWorlds;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MobRaidManager {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor PURPLE = TextColor.color(0xAA00AA);

    public static final int STARTING_LIVES = 3;
    public static final int WAVE_PAUSE_SECONDS = 20;
    public static final int REPAIR_COST = 2000;
    public static final int MAX_WAVES = 100;

    public static final NamespacedKey RAID_MOB_KEY = new NamespacedKey("starlightuniverse", "raid_mob");
    public static final NamespacedKey BLACKSMITH_KEY = new NamespacedKey("starlightuniverse", "raid_blacksmith");

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;
    private final ArenaWorldManager arenaWorldManager;

    private MobRaid active;
    private BukkitTask tickTask;

    public MobRaidManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy,
                          ArenaWorldManager arenaWorldManager) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
        this.arenaWorldManager = arenaWorldManager;
    }

    public void start() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        if (active != null) forceEnd();
    }

    public boolean hasActiveRaid() { return active != null && active.state != MobRaid.State.ENDED; }
    public MobRaid getActive() { return active; }

    // ── Start / stop ──

    public boolean startRaid(Player initiator) {
        if (hasActiveRaid()) {
            Msg.error(initiator, "A mob raid is already active!");
            return false;
        }
        if (!arenaWorldManager.isReady(ArenaWorlds.MOBS_WORLD)) {
            Msg.error(initiator, "The mob arena is not ready yet.");
            return false;
        }
        World world = Bukkit.getWorld(ArenaWorlds.MOBS_WORLD);
        if (world == null) {
            Msg.error(initiator, "Mob arena world is not loaded!");
            return false;
        }

        BossBar bar = Bukkit.createBossBar("§lMob Raid §r§7— §fPreparing…", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);
        active = new MobRaid(bar, Bukkit.getCurrentTick());
        active.state = MobRaid.State.PREPARING;
        active.pauseTicks = 0;

        for (Player online : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(online);
        }

        Component announce = Msg.prefix()
                .append(Component.text("A ", GOLD))
                .append(Component.text("Mob Invasion", RED, TextDecoration.BOLD))
                .append(Component.text(" has begun in the Mob Arena!", GOLD));
        Component join = Msg.prefix()
                .append(Component.text("Use ", GRAY))
                .append(Component.text("/mobraid join", CYAN))
                .append(Component.text(" to fight through the waves. You have 3 lives!", GRAY));

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(announce);
            online.sendMessage(join);
            online.playSound(online.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
        }
        plugin.getLogger().info("[SU] Mob Raid started by " + initiator.getName());
        return true;
    }

    public void stopRaid(Player initiator) {
        if (!hasActiveRaid()) {
            Msg.error(initiator, "There is no active mob raid!");
            return;
        }
        Msg.info(initiator, "Mob raid stopped.");
        forceEnd();
    }

    public void joinRaid(Player player) {
        if (!hasActiveRaid()) {
            Msg.error(player, "There is no active mob raid!");
            return;
        }
        if (active.livesLeft.containsKey(player.getUniqueId())) {
            Msg.error(player, "You are already in the raid!");
            return;
        }
        if (active.outPlayers.contains(player.getUniqueId())) {
            Msg.error(player, "You already used all your lives this raid.");
            return;
        }
        World world = Bukkit.getWorld(ArenaWorlds.MOBS_WORLD);
        if (world == null) {
            Msg.error(player, "Mob arena world is not loaded!");
            return;
        }

        active.livesLeft.put(player.getUniqueId(), STARTING_LIVES);
        active.waveKills.put(player.getUniqueId(), 0);
        active.totalKills.put(player.getUniqueId(), 0);
        active.returnLocations.put(player.getUniqueId(), player.getLocation());
        active.returnModes.put(player.getUniqueId(), player.getGameMode());

        Location target = new Location(world,
                ArenaWorlds.CENTER_X + ThreadLocalRandom.current().nextInt(-10, 10) + 0.5,
                ArenaWorlds.SPAWN_Y,
                ArenaWorlds.CENTER_Z + ThreadLocalRandom.current().nextInt(-10, 10) + 0.5);
        player.teleport(target);
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(Math.min(20, player.getMaxHealth()));
        player.setFoodLevel(20);
        player.setSaturation(5);
        active.bossBar.addPlayer(player);
        Msg.success(player, "You joined the mob raid! Lives: " + STARTING_LIVES);
    }

    public void leaveRaid(Player player) {
        if (!hasActiveRaid()) {
            Msg.error(player, "There is no active mob raid!");
            return;
        }
        if (!active.livesLeft.containsKey(player.getUniqueId())) {
            Msg.error(player, "You are not in the raid!");
            return;
        }
        returnPlayer(player);
        active.livesLeft.remove(player.getUniqueId());
        active.bossBar.removePlayer(player);
        Msg.info(player, "You left the raid.");
        checkAllOut();
    }

    private void returnPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Location back = active.returnLocations.remove(uuid);
        GameMode mode = active.returnModes.remove(uuid);
        if (back != null && back.getWorld() != null) player.teleport(back);
        player.setGameMode(mode != null ? mode : GameMode.SURVIVAL);
        player.setHealth(Math.min(20, player.getMaxHealth()));
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.setFireTicks(0);
    }

    // ── Tick ──

    private void tick() {
        if (active == null || active.state == MobRaid.State.ENDED) return;

        switch (active.state) {
            case PREPARING -> tickPreparing();
            case WAVE_ACTIVE -> tickWaveActive();
            case WAVE_PAUSE -> tickWavePause();
            case ENDED -> {}
        }
    }

    private void tickPreparing() {
        active.pauseTicks++;
        if (active.livesLeft.isEmpty()) {
            active.bossBar.setTitle("§lMob Raid §r§7— Waiting for players (/mobraid join)");
            if (active.pauseTicks >= 60) {
                plugin.getLogger().info("[SU] Mob raid ended — no players joined after 60s.");
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendMessage(Msg.prefix().append(Component.text("Mob raid ended — no players joined.", GRAY)));
                }
                forceEnd();
            }
            return;
        }
        if (active.pauseTicks >= 10) {
            beginWave(1);
        } else {
            int remaining = 10 - active.pauseTicks;
            active.bossBar.setTitle("§lMob Raid §r§7— Wave 1 begins in " + remaining + "s");
        }
    }

    private void tickWavePause() {
        active.pauseTicks++;
        int remaining = WAVE_PAUSE_SECONDS - active.pauseTicks;
        if (remaining <= 0) {
            despawnBlacksmith();
            beginWave(active.currentWave + 1);
            return;
        }
        active.bossBar.setTitle("§lMob Raid §r§7— Next wave (" + (active.currentWave + 1) + ") in " + remaining + "s");
        for (UUID uuid : active.livesLeft.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendActionBar(Component.text("Wave " + (active.currentWave + 1) + " in " + remaining + "s — Blacksmith is available!", YELLOW));
            }
        }
    }

    private void tickWaveActive() {
        active.waveTicks++;

        // Clean dead mobs
        active.aliveMobs.removeIf(id -> {
            var e = Bukkit.getEntity(id);
            return e == null || e.isDead() || !e.isValid();
        });

        int aliveCount = active.aliveMobs.size();
        active.bossBar.setProgress(Math.max(0, Math.min(1, aliveCount / (double) Math.max(1, waveMobCount(active.currentWave)))));
        active.bossBar.setTitle("§lWave " + active.currentWave + " §r§7— §f" + aliveCount + " mobs remaining");

        if (aliveCount <= 0) {
            completeWave();
        }

        // Safety: if all players out or offline, end
        if (allPlayersOut()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(Msg.prefix().append(Component.text("Mob raid ended — no players left standing.", RED)));
            }
            forceEnd();
        }
    }

    private boolean allPlayersOut() {
        if (active.livesLeft.isEmpty()) return true;
        for (UUID uuid : active.livesLeft.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) return false;
        }
        return true;
    }

    // ── Waves ──

    private int waveMobCount(int wave) {
        if (wave > 0 && wave % 5 == 0) return 1;
        return Math.min(30, 5 + wave * 2);
    }

    private void beginWave(int wave) {
        active.currentWave = wave;
        active.waveTicks = 0;
        active.state = MobRaid.State.WAVE_ACTIVE;
        active.aliveMobs.clear();
        active.waveKills.clear();
        for (UUID uuid : active.livesLeft.keySet()) active.waveKills.put(uuid, 0);

        boolean bossWave = active.isBossWave();
        String label = bossWave ? "BOSS WAVE" : "Wave";
        TextColor c = bossWave ? PURPLE : GOLD;

        Component announce = Msg.prefix()
                .append(Component.text(label + " " + wave, c, TextDecoration.BOLD))
                .append(Component.text(" — get ready!", GRAY));
        for (UUID uuid : active.livesLeft.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(announce);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
            }
        }

        spawnMobsForWave(wave);
    }

    private void spawnMobsForWave(int wave) {
        World world = Bukkit.getWorld(ArenaWorlds.MOBS_WORLD);
        if (world == null) return;

        double hpMult = 1.0 + (wave - 1) * 0.10;
        double dmgMult = 1.0 + (wave - 1) * 0.10;

        int count = waveMobCount(wave);
        for (int i = 0; i < count; i++) {
            MobType type;
            if (active.isBossWave()) {
                // Boss wave: 1 tough mob, prefer wither skeleton/vindicator/warden-ish
                MobType[] boss = { MobType.WITHER_SKELETON, MobType.VINDICATOR, MobType.BLAZE, MobType.ENDERMAN };
                type = boss[ThreadLocalRandom.current().nextInt(boss.length)];
                hpMult *= 5;
                dmgMult *= 2;
            } else {
                type = MobType.ALL[ThreadLocalRandom.current().nextInt(MobType.ALL.length)];
            }
            spawnMob(world, type, hpMult, dmgMult, active.isBossWave());
        }
    }

    private void spawnMob(World world, MobType type, double hpMult, double dmgMult, boolean bossVariant) {
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        int radius = 40 + ThreadLocalRandom.current().nextInt(0, 100);
        int x = ArenaWorlds.CENTER_X + (int) (Math.cos(angle) * radius);
        int z = ArenaWorlds.CENTER_Z + (int) (Math.sin(angle) * radius);
        Location loc = new Location(world, x + 0.5, ArenaWorlds.SPAWN_Y, z + 0.5);
        try {
            LivingEntity e = (LivingEntity) world.spawnEntity(loc, type.entityType);
            e.getPersistentDataContainer().set(RAID_MOB_KEY, PersistentDataType.STRING, "1");
            e.setRemoveWhenFarAway(false);
            e.setPersistent(true);
            var maxHp = e.getAttribute(Attribute.MAX_HEALTH);
            if (maxHp != null) {
                maxHp.setBaseValue(type.baseHp * hpMult);
                e.setHealth(type.baseHp * hpMult);
            }
            var atk = e.getAttribute(Attribute.ATTACK_DAMAGE);
            if (atk != null) {
                atk.setBaseValue(type.baseDamage * dmgMult);
            }
            if (bossVariant) {
                e.customName(Component.text("Elite " + type.displayName, PURPLE, TextDecoration.BOLD));
                e.setCustomNameVisible(true);
                e.setGlowing(true);
            }
            active.aliveMobs.add(e.getUniqueId());
        } catch (IllegalArgumentException ignored) {
            // Some entity types may not spawn — skip.
        }
    }

    private void completeWave() {
        int wave = active.currentWave;
        distributeWaveRewards(wave);

        if (wave >= MAX_WAVES) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(Msg.prefix().append(Component.text("Mob raid completed! You beat wave " + MAX_WAVES + "!", GREEN, TextDecoration.BOLD)));
            }
            forceEnd();
            return;
        }

        active.state = MobRaid.State.WAVE_PAUSE;
        active.pauseTicks = 0;
        spawnBlacksmith();

        Component divider = Msg.prefix().append(Component.text("═══════════════════════════════════", GOLD));
        Component completeLine = Msg.prefix()
                .append(Component.text("Wave " + wave + " complete!", GREEN, TextDecoration.BOLD))
                .append(Component.text(" Pause: " + WAVE_PAUSE_SECONDS + "s before wave " + (wave + 1) + ".", GRAY));
        Component smithLine1 = Msg.prefix()
                .append(Component.text("A ", GRAY))
                .append(Component.text("Blacksmith", GOLD, TextDecoration.BOLD))
                .append(Component.text(" has appeared at the arena center!", GRAY));
        Component smithLine2 = Msg.prefix()
                .append(Component.text("Right-click him to fully repair ALL your gear for ", GRAY))
                .append(Component.text(EconomyManager.MONEY_ICON + " $" + EconomyManager.format(REPAIR_COST), GREEN, TextDecoration.BOLD))
                .append(Component.text(".", GRAY));
        Component smithLine3 = Msg.prefix()
                .append(Component.text("He disappears when the next wave starts — use him before then!", YELLOW));

        for (UUID uuid : active.livesLeft.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(divider);
                p.sendMessage(completeLine);
                p.sendMessage(smithLine1);
                p.sendMessage(smithLine2);
                p.sendMessage(smithLine3);
                p.sendMessage(divider);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.2f);
            }
        }
    }

    private void distributeWaveRewards(int wave) {
        int totalKills = active.waveKills.values().stream().mapToInt(Integer::intValue).sum();
        int baseMoney = Math.min(5000, 100 + wave * 200);
        int baseGems = Math.min(50, 5 + wave / 2);
        int stars = wave >= 20 ? (wave % 20 == 0 ? 2 : 1) : 0;

        for (UUID uuid : active.livesLeft.keySet()) {
            int kills = active.waveKills.getOrDefault(uuid, 0);
            double share = totalKills == 0 ? 1.0 / active.livesLeft.size() : (double) kills / totalKills;

            int money = baseMoney + (int) (baseMoney * share);
            int gems = baseGems + (int) (baseGems * share);
            economy.addMoney(uuid, money);
            economy.addGems(uuid, gems);
            if (stars > 0) economy.addStars(uuid, stars);

            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                Component line = Msg.prefix()
                        .append(Component.text("Wave " + wave + " reward: ", GRAY))
                        .append(Component.text(EconomyManager.MONEY_ICON + " $" + EconomyManager.format(money), GREEN))
                        .append(Component.text("  ◆" + gems, CYAN));
                if (stars > 0) {
                    line = line.append(Component.text("  ★" + stars, PURPLE));
                }
                line = line.append(Component.text("  (" + kills + " kills)", GRAY));
                p.sendMessage(line);
            }
        }
    }

    // ── Damage / kills / deaths ──

    public boolean isRaidMob(LivingEntity entity) {
        if (entity == null) return false;
        return entity.getPersistentDataContainer().has(RAID_MOB_KEY);
    }

    public void handleMobDeath(LivingEntity mob, Player killer) {
        if (active == null || active.state != MobRaid.State.WAVE_ACTIVE) return;
        active.aliveMobs.remove(mob.getUniqueId());
        if (killer == null) return;
        UUID uuid = killer.getUniqueId();
        if (!active.livesLeft.containsKey(uuid)) return;
        active.waveKills.merge(uuid, 1, Integer::sum);
        active.totalKills.merge(uuid, 1, Integer::sum);
    }

    public void handlePlayerDeath(Player player) {
        if (active == null || active.state == MobRaid.State.ENDED) return;
        UUID uuid = player.getUniqueId();
        if (!active.livesLeft.containsKey(uuid)) return;

        int lives = active.livesLeft.get(uuid) - 1;
        if (lives <= 0) {
            active.livesLeft.remove(uuid);
            active.outPlayers.add(uuid);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    returnPlayer(player);
                    Msg.error(player, "You are out of lives. Better luck next raid!");
                }
                if (active != null) active.bossBar.removePlayer(player);
            }, 5L);
            saveKills(player.getName(), active.totalKills.getOrDefault(uuid, 0), active.currentWave);
        } else {
            active.livesLeft.put(uuid, lives);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                World world = Bukkit.getWorld(ArenaWorlds.MOBS_WORLD);
                if (world == null) return;
                Location target = new Location(world,
                        ArenaWorlds.CENTER_X + 0.5,
                        ArenaWorlds.SPAWN_Y,
                        ArenaWorlds.CENTER_Z + 0.5);
                player.teleport(target);
                player.setHealth(Math.min(20, player.getMaxHealth()));
                player.setFoodLevel(20);
                player.setSaturation(5);
                Msg.info(player, "You died! " + lives + " life" + (lives == 1 ? "" : "s") + " remaining.");
            }, 5L);
        }
        checkAllOut();
    }

    public void handlePlayerQuit(Player player) {
        if (active == null || active.state == MobRaid.State.ENDED) return;
        UUID uuid = player.getUniqueId();
        if (active.livesLeft.remove(uuid) != null) {
            active.bossBar.removePlayer(player);
            active.returnLocations.remove(uuid);
            active.returnModes.remove(uuid);
            saveKills(player.getName(), active.totalKills.getOrDefault(uuid, 0), active.currentWave);
        }
        checkAllOut();
    }

    private void checkAllOut() {
        if (active == null || active.state == MobRaid.State.ENDED) return;
        if (active.livesLeft.isEmpty() && active.state != MobRaid.State.PREPARING) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(Msg.prefix().append(Component.text("Mob raid ended — all players are out.", RED)));
            }
            forceEnd();
        }
    }

    private void forceEnd() {
        if (active == null) return;
        // Save any remaining players' kills
        for (Map.Entry<UUID, Integer> entry : active.totalKills.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) saveKills(p.getName(), entry.getValue(), active.currentWave);
        }

        despawnBlacksmith();

        // Kill all remaining raid mobs
        World world = Bukkit.getWorld(ArenaWorlds.MOBS_WORLD);
        if (world != null) {
            for (org.bukkit.entity.Entity e : world.getEntities()) {
                if (e instanceof LivingEntity le && isRaidMob(le)) le.remove();
            }
        }
        active.bossBar.removeAll();

        // Return remaining players
        for (UUID uuid : new ArrayList<>(active.livesLeft.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) returnPlayer(p);
        }
        active.state = MobRaid.State.ENDED;
        active = null;
    }

    // ── Blacksmith ──

    private void spawnBlacksmith() {
        World world = Bukkit.getWorld(ArenaWorlds.MOBS_WORLD);
        if (world == null) return;
        Location loc = new Location(world,
                ArenaWorlds.CENTER_X + 0.5,
                ArenaWorlds.SPAWN_Y,
                ArenaWorlds.CENTER_Z + 0.5);
        Villager v = (Villager) world.spawnEntity(loc, org.bukkit.entity.EntityType.VILLAGER);
        try {
            var prof = org.bukkit.Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft("weaponsmith"));
            if (prof != null) v.setProfession(prof);
        } catch (Throwable ignored) {}
        v.setVillagerLevel(5);
        v.setAI(false);
        v.setInvulnerable(true);
        v.setPersistent(true);
        v.customName(Component.text("Blacksmith — Right-click to Repair", GOLD, TextDecoration.BOLD));
        v.setCustomNameVisible(true);
        v.getPersistentDataContainer().set(BLACKSMITH_KEY, PersistentDataType.STRING, "1");
        active.blacksmith = v;
    }

    private void despawnBlacksmith() {
        if (active != null && active.blacksmith != null) {
            active.blacksmith.remove();
            active.blacksmith = null;
        }
        World world = Bukkit.getWorld(ArenaWorlds.MOBS_WORLD);
        if (world != null) {
            for (org.bukkit.entity.Entity e : world.getEntities()) {
                if (e instanceof Villager v && v.getPersistentDataContainer().has(BLACKSMITH_KEY)) {
                    v.remove();
                }
            }
        }
    }

    public boolean isBlacksmith(org.bukkit.entity.Entity e) {
        return e instanceof Villager && e.getPersistentDataContainer().has(BLACKSMITH_KEY);
    }

    public void tryRepairAll(Player player) {
        if (!economy.hasMoney(player.getUniqueId(), REPAIR_COST)) {
            Msg.error(player, "Repair costs " + EconomyManager.MONEY_ICON + " $" + EconomyManager.format(REPAIR_COST) + " — insufficient funds.");
            return;
        }
        boolean anyRepaired = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getItemMeta() instanceof Damageable dmg && dmg.hasDamage()) {
                dmg.setDamage(0);
                item.setItemMeta((ItemMeta) dmg);
                anyRepaired = true;
            }
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && item.getItemMeta() instanceof Damageable dmg && dmg.hasDamage()) {
                dmg.setDamage(0);
                item.setItemMeta((ItemMeta) dmg);
                anyRepaired = true;
            }
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off.getItemMeta() instanceof Damageable dmg && dmg.hasDamage()) {
            dmg.setDamage(0);
            off.setItemMeta((ItemMeta) dmg);
            anyRepaired = true;
        }
        if (!anyRepaired) {
            Msg.gray(player, "Nothing needed repairing.");
            return;
        }
        economy.removeMoney(player.getUniqueId(), REPAIR_COST);
        Msg.success(player, "All gear repaired for " + EconomyManager.MONEY_ICON + " $" + EconomyManager.format(REPAIR_COST) + "!");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
    }

    // ── Leaderboard ──

    private void saveKills(String username, int killsToAdd, int bestWave) {
        if (killsToAdd <= 0 && bestWave <= 0) return;
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_mobraid_stats (username, total_kills, best_wave) VALUES (?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE total_kills = total_kills + VALUES(total_kills), " +
                            "best_wave = GREATEST(best_wave, VALUES(best_wave))")) {
                ps.setString(1, username.toLowerCase());
                ps.setInt(2, killsToAdd);
                ps.setInt(3, bestWave);
                ps.executeUpdate();
            }
        });
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET pvm_kills = pvm_kills + ? WHERE username = ?")) {
                ps.setInt(1, killsToAdd);
                ps.setString(2, username.toLowerCase());
                ps.executeUpdate();
            }
        });
    }

    public List<TopEntry> getTop() {
        List<TopEntry> out = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT username, total_kills, best_wave FROM su_mobraid_stats " +
                             "WHERE total_kills > 0 ORDER BY total_kills DESC LIMIT 10")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TopEntry(rs.getString("username"),
                            rs.getInt("total_kills"), rs.getInt("best_wave")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[SU] Failed to load mob raid top: " + e.getMessage());
        }
        return out;
    }

    public record TopEntry(String username, int kills, int bestWave) {}

    public void handleJoin(Player player) {
        if (active == null || active.state == MobRaid.State.ENDED) return;
        active.bossBar.addPlayer(player);
    }
}
