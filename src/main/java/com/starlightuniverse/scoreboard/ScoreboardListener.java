package com.starlightuniverse.scoreboard;

import com.starlightuniverse.auth.AuthManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ScoreboardListener implements Listener {

    private final JavaPlugin plugin;
    private final ScoreboardManager scoreboards;
    private final AuthManager auth;

    public ScoreboardListener(JavaPlugin plugin, ScoreboardManager scoreboards, AuthManager auth) {
        this.plugin = plugin;
        this.scoreboards = scoreboards;
        this.auth = auth;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        scoreboards.removeFor(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        int[] handle = new int[]{-1};
        long deadline = System.currentTimeMillis() + 30_000L;
        handle[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline() || System.currentTimeMillis() > deadline) {
                if (handle[0] != -1) Bukkit.getScheduler().cancelTask(handle[0]);
                return;
            }
            if (auth.isAuthenticated(p.getUniqueId())) {
                scoreboards.createFor(p);
                if (handle[0] != -1) Bukkit.getScheduler().cancelTask(handle[0]);
            }
        }, 20L, 20L).getTaskId();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        // Deaths ++
        scoreboards.incrementDeath(victim.getName());
        // If killer is a player, that's a PvP kill
        Player killer = victim.getKiller();
        if (killer != null && killer != victim) {
            scoreboards.incrementPvpKill(killer.getName());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity ent = event.getEntity();
        if (ent instanceof Player) return; // handled by onPlayerDeath
        Player killer = ent.getKiller();
        if (killer != null) {
            scoreboards.incrementPvmKill(killer.getName());
        }
    }

}
