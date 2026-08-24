package com.starlightuniverse.nameplate;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.admin.AdminRank;
import com.starlightuniverse.benefit.BenefitManager;
import com.starlightuniverse.benefit.BodyGlow;
import com.starlightuniverse.chat.ChatManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.premium.PremiumManager;
import com.starlightuniverse.premium.PremiumRank;
import com.starlightuniverse.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NameplateManager {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor PURPLE = TextColor.color(0xAA00AA);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);

    private static final float VIEW_RANGE = 0.5f;
    private static final long BUBBLE_DURATION_MS = 8000L;
    private static final long UPDATE_TICKS = 4L;
    private static final int SIMPLE_MODE_NEARBY = 30;
    private static final double NEARBY_RADIUS_SQ = 48 * 48;

    private static final double TEAM_OFFSET_Y = 2.30;
    private static final double BUBBLE_OFFSET_Y = 2.65;
    private static final double CURRENCY_OFFSET_Y = 0.30;

    private final JavaPlugin plugin;
    private final ChatManager chatManager;
    private final AdminManager adminManager;
    private final PremiumManager premiumManager;
    private final TeamManager teamManager;
    private final EconomyManager economy;
    private BenefitManager benefitManager;

    private final Map<UUID, Nameplate> plates = new ConcurrentHashMap<>();
    private BukkitTask task;

    public NameplateManager(JavaPlugin plugin, ChatManager chatManager, AdminManager adminManager,
                            PremiumManager premiumManager, TeamManager teamManager, EconomyManager economy) {
        this.plugin = plugin;
        this.chatManager = chatManager;
        this.adminManager = adminManager;
        this.premiumManager = premiumManager;
        this.teamManager = teamManager;
        this.economy = economy;
    }

    public void setBenefitManager(BenefitManager benefitManager) { this.benefitManager = benefitManager; }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, UPDATE_TICKS, UPDATE_TICKS);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) removeFor(p);
        plates.clear();
    }

    public void spawnFor(Player player) {
        UUID uuid = player.getUniqueId();
        removeFor(player);

        Location base = player.getLocation();
        World world = base.getWorld();
        if (world == null) return;

        TextDisplay teamDisp = spawnDisplay(world, base, TEAM_OFFSET_Y);
        TextDisplay bubbleDisp = spawnDisplay(world, base, BUBBLE_OFFSET_Y);
        TextDisplay currencyDisp = spawnDisplay(world, base, CURRENCY_OFFSET_Y);

        Nameplate np = new Nameplate();
        np.teamDisplay = teamDisp;
        np.bubbleDisplay = bubbleDisp;
        np.currencyDisplay = currencyDisp;
        plates.put(uuid, np);

        ensureNameplateTeam(player);
        refreshTeamPrefix(player);
        updateTextDisplays(player, np);
    }

    private TextDisplay spawnDisplay(World world, Location loc, double yOffset) {
        Location spawn = loc.clone().add(0, yOffset, 0);
        return world.spawn(spawn, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setViewRange(VIEW_RANGE);
            td.setSeeThrough(true);
            td.setPersistent(false);
            td.setInvulnerable(true);
            td.setShadowed(false);
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(0.9f, 0.9f, 0.9f),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
            td.text(Component.empty());
        });
    }

    public void removeFor(Player player) {
        Nameplate np = plates.remove(player.getUniqueId());
        if (np != null) {
            if (np.teamDisplay != null && np.teamDisplay.isValid()) np.teamDisplay.remove();
            if (np.bubbleDisplay != null && np.bubbleDisplay.isValid()) np.bubbleDisplay.remove();
            if (np.currencyDisplay != null && np.currencyDisplay.isValid()) np.currencyDisplay.remove();
        }
        removePlayerFromNameplateTeam(player);
    }

    public void showBubble(Player player, String message) {
        Nameplate np = plates.get(player.getUniqueId());
        if (np == null) return;
        np.bubbleMessage = message;
        np.bubbleExpireMs = System.currentTimeMillis() + BUBBLE_DURATION_MS;
    }

    public void refreshTeamPrefix(Player player) {
        UUID uuid = player.getUniqueId();
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(teamNameFor(player));
        if (team == null) return;

        Component prefix = buildInlinePrefix(player);
        team.prefix(prefix);

        NamedTextColor color = NamedTextColor.WHITE;
        if (benefitManager != null) {
            BodyGlow glow = BodyGlow.byKey(benefitManager.getActiveGlow(uuid));
            if (glow != null) color = glow.getNamedColor();
        }
        team.color(color);
    }

    private Component buildInlinePrefix(Player player) {
        UUID uuid = player.getUniqueId();
        Component result = Component.empty();

        AdminRank adminRank = adminManager.getAdminRank(uuid);
        if (adminRank != AdminRank.NONE) {
            result = result.append(Component.text(adminRank.getPrefix(), adminRank.getColor()))
                    .append(Component.text(" ", WHITE));
        }

        String customTag = chatManager.getNameTag(uuid);
        String customPrefix = benefitManager != null ? benefitManager.getCustomPrefix(uuid) : null;
        PremiumRank premRank = premiumManager.getPlayerRank(uuid);

        if (customPrefix != null && !customPrefix.isEmpty()) {
            TextColor c = premRank != PremiumRank.NONE ? premRank.getColor() : GOLD;
            result = result.append(Component.text("[" + customPrefix + "]", c))
                    .append(Component.text(" ", WHITE));
        } else if (customTag != null && !customTag.isEmpty()) {
            TextColor c = premRank != PremiumRank.NONE ? premRank.getColor() : GRAY;
            result = result.append(Component.text("[" + customTag + "]", c))
                    .append(Component.text(" ", WHITE));
        } else if (premRank != PremiumRank.NONE) {
            result = result.append(premRank.getColoredPrefix())
                    .append(Component.text(" ", WHITE));
        }

        return result;
    }

    private void ensureNameplateTeam(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String name = teamNameFor(player);
        Team team = sb.getTeam(name);
        if (team == null) {
            team = sb.registerNewTeam(name);
        }
        for (Team t : sb.getTeams()) {
            if (t == team) continue;
            if (t.hasEntry(player.getName())) t.removeEntry(player.getName());
        }
        if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
    }

    private void removePlayerFromNameplateTeam(Player player) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team t = sb.getTeam(teamNameFor(player));
        if (t != null) {
            t.removeEntry(player.getName());
            try { t.unregister(); } catch (IllegalStateException ignored) {}
        }
    }

    private String teamNameFor(Player player) {
        String u = player.getUniqueId().toString().replace("-", "");
        return "np_" + u.substring(0, Math.min(12, u.length()));
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Nameplate np = plates.get(player.getUniqueId());
            if (np == null) continue;
            updateTextDisplays(player, np);
        }
    }

    private void updateTextDisplays(Player player, Nameplate np) {
        Location base = player.getLocation();
        World world = base.getWorld();
        if (world == null) return;

        int nearby = countNearbyPlayers(player);
        boolean simple = nearby >= SIMPLE_MODE_NEARBY;

        moveDisplay(np.teamDisplay, base, TEAM_OFFSET_Y);
        moveDisplay(np.bubbleDisplay, base, BUBBLE_OFFSET_Y);
        moveDisplay(np.currencyDisplay, base, CURRENCY_OFFSET_Y);

        Component teamText;
        if (simple) {
            teamText = Component.empty();
        } else {
            com.starlightuniverse.team.Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team != null) {
                teamText = Component.text("[", GRAY)
                        .append(teamManager.buildGradientName(team.getName(), team.getColors()))
                        .append(Component.text("]", GRAY));
            } else {
                teamText = Component.empty();
            }
        }
        if (np.teamDisplay.isValid()) np.teamDisplay.text(teamText);

        long now = System.currentTimeMillis();
        Component bubbleText;
        if (np.bubbleMessage != null && now < np.bubbleExpireMs) {
            String msg = np.bubbleMessage;
            if (msg.length() > 60) msg = msg.substring(0, 57) + "...";
            bubbleText = Component.text("< " + msg + " >", WHITE);
        } else {
            bubbleText = Component.empty();
            np.bubbleMessage = null;
        }
        if (np.bubbleDisplay.isValid()) np.bubbleDisplay.text(bubbleText);

        Component currencyText;
        if (simple) {
            currencyText = Component.empty();
        } else {
            UUID uuid = player.getUniqueId();
            int money = (int) economy.getMoney(uuid);
            int gems = (int) economy.getGems(uuid);
            int stars = (int) economy.getStars(uuid);
            int kills = 0;
            int deaths = 0;
            currencyText = Component.text("$" + EconomyManager.format(money), GREEN)
                    .append(Component.text(" | ", GRAY))
                    .append(Component.text(EconomyManager.GEMS_ICON + EconomyManager.format(gems), CYAN))
                    .append(Component.text(" | ", GRAY))
                    .append(Component.text(EconomyManager.STARS_ICON + EconomyManager.format(stars), YELLOW))
                    .append(Component.text(" | ", GRAY))
                    .append(Component.text("⚔" + kills, RED))
                    .append(Component.text(" | ", GRAY))
                    .append(Component.text("☠" + deaths, PURPLE));
        }
        if (np.currencyDisplay.isValid()) np.currencyDisplay.text(currencyText);
    }

    private void moveDisplay(TextDisplay td, Location base, double yOffset) {
        if (td == null || !td.isValid()) return;
        Location target = base.clone().add(0, yOffset, 0);
        target.setPitch(0f);
        target.setYaw(0f);
        if (!td.getWorld().equals(base.getWorld())) {
            td.teleport(target);
        } else if (td.getLocation().distanceSquared(target) > 0.001) {
            td.teleport(target);
        }
    }

    private int countNearbyPlayers(Player player) {
        int count = 0;
        Location base = player.getLocation();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getWorld().equals(base.getWorld())) continue;
            try {
                if (other.getLocation().distanceSquared(base) <= NEARBY_RADIUS_SQ) count++;
            } catch (IllegalArgumentException ignored) {}
        }
        return count;
    }

    public JavaPlugin getPlugin() { return plugin; }

    private static class Nameplate {
        TextDisplay teamDisplay;
        TextDisplay bubbleDisplay;
        TextDisplay currencyDisplay;
        String bubbleMessage;
        long bubbleExpireMs;
    }
}
