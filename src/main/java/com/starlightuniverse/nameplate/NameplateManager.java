package com.starlightuniverse.nameplate;

import com.starlightuniverse.StarlightUniverse;
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

    // Spec colors for currency + kills/deaths line
    private static final TextColor MONEY_COLOR  = TextColor.color(0xFFFF00);
    private static final TextColor GEMS_COLOR   = TextColor.color(0x9900CC);
    private static final TextColor STARS_COLOR  = TextColor.color(0xFFF5A0);
    private static final TextColor PVP_COLOR    = TextColor.color(0xFF944D);
    private static final TextColor PVM_COLOR    = TextColor.color(0xFF8080);
    private static final TextColor DEATHS_COLOR = TextColor.color(0xFF0000);

    private static final float VIEW_RANGE = 1.5f;
    private static final long BUBBLE_DURATION_MS = 8000L;
    private static final long UPDATE_TICKS = 1L;
    private static final int SIMPLE_MODE_NEARBY = 30;
    private static final double NEARBY_RADIUS_SQ = 48 * 48;

    // Compact 4-line stack just above the head. Vanilla nametag is suppressed.
    // Order top → bottom:  Team → Name+prefix → Currency → Bubble
    // Values are Transformation.translation Y — display is a player passenger,
    // Bukkit adds a ~1.62 wu "seat" offset on top of these. Text scale is 0.65
    // (smaller than the default 0.9), so line gaps of ~0.22 wu keep the stack tight.
    private static final float TEAM_OFFSET_Y     = 0.70f;
    private static final float NAME_OFFSET_Y     = 0.54f;
    private static final float CURRENCY_OFFSET_Y = 0.38f;
    private static final float BUBBLE_OFFSET_Y   = 0.22f;

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
        Location base = player.getLocation();
        World world = base.getWorld();
        if (world == null) return;

        // Idempotent: if displays already exist in the same world, skip full
        // respawn (which would flash a fresh set on screen). Just re-mount.
        Nameplate existing = plates.get(uuid);
        if (existing != null
                && existing.teamDisplay != null && existing.teamDisplay.isValid()
                && existing.nameDisplay != null && existing.nameDisplay.isValid()
                && existing.bubbleDisplay != null && existing.bubbleDisplay.isValid()
                && existing.currencyDisplay != null && existing.currencyDisplay.isValid()
                && world.equals(existing.teamDisplay.getWorld())) {
            ensureMounted(player, existing.teamDisplay);
            ensureMounted(player, existing.nameDisplay);
            ensureMounted(player, existing.bubbleDisplay);
            ensureMounted(player, existing.currencyDisplay);
            updateTextDisplays(player, existing);
            return;
        }

        removeFor(player);

        TextDisplay teamDisp = spawnDisplay(world, base, TEAM_OFFSET_Y);
        TextDisplay nameDisp = spawnDisplay(world, base, NAME_OFFSET_Y);
        TextDisplay bubbleDisp = spawnDisplay(world, base, BUBBLE_OFFSET_Y);
        TextDisplay currencyDisp = spawnDisplay(world, base, CURRENCY_OFFSET_Y);

        Nameplate np = new Nameplate();
        np.teamDisplay = teamDisp;
        np.nameDisplay = nameDisp;
        np.bubbleDisplay = bubbleDisp;
        np.currencyDisplay = currencyDisp;
        plates.put(uuid, np);

        // Mount all four displays as passengers so they follow the player 1:1
        // with zero lag. Y position lives in each display's Transformation.translation.
        mount(player, teamDisp);
        mount(player, nameDisp);
        mount(player, bubbleDisp);
        mount(player, currencyDisp);

        ensureNameplateTeam(player);
        refreshTeamPrefix(player);
        updateTextDisplays(player, np);
    }

    private TextDisplay spawnDisplay(World world, Location loc, float yOffset) {
        // Pre-mount render height = spawn.y + translation.y.
        // Post-mount render height = vehicle.y + seat(1.62) + translation.y.
        // Spawning at player.y + 1.62 makes both equal → no visible fall on mount.
        Location spawnLoc = loc.clone().add(0, 1.62, 0);
        TextDisplay td = world.spawn(spawnLoc, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange(VIEW_RANGE);
            d.setSeeThrough(true);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setShadowed(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(2000);
            // Zero-out ALL interpolation knobs so any position change is instant.
            d.setInterpolationDuration(0);
            d.setInterpolationDelay(0);
            d.setTeleportDuration(0);
            // Set the translation transformation immediately (before spawn packet
            // reaches clients — the consumer runs pre-broadcast).
            d.setTransformation(new Transformation(
                    new Vector3f(0f, yOffset, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(0.49f, 0.49f, 0.49f),
                    new AxisAngle4f(0f, 0f, 0f, 1f)));
            d.text(Component.empty());
        });
        // Re-assert zero interpolation after spawn just to be safe — some Paper
        // versions apply a 1-tick default interpolation window on entity creation.
        td.setInterpolationDuration(0);
        td.setInterpolationDelay(0);
        td.setTeleportDuration(0);
        return td;
    }

    private void mount(Player player, TextDisplay td) {
        if (td == null || !td.isValid()) return;
        try {
            boolean ok = player.addPassenger(td);
            if (!ok) {
                plugin.getLogger().warning("[SU][nameplate] addPassenger returned false for "
                        + player.getName() + " — display will float free at spawn position.");
            }
            td.setInterpolationDuration(0);
            td.setInterpolationDelay(0);
            td.setTeleportDuration(0);
        } catch (Throwable t) {
            plugin.getLogger().warning("[SU][nameplate] addPassenger threw for "
                    + player.getName() + ": " + t.getMessage());
        }
    }

    private void ensureMounted(Player player, TextDisplay td) {
        if (td == null || !td.isValid()) return;
        if (!player.getPassengers().contains(td)) {
            try { player.addPassenger(td); } catch (Throwable ignored) {}
        }
    }

    /**
     * Prefer passenger mount, but fall back to manual teleport if it can't
     * attach. Guarantees the text always follows the player, no matter what.
     */
    private void followOrTeleport(Player player, TextDisplay td, float yOffset) {
        if (td == null || !td.isValid()) return;
        if (player.getPassengers().contains(td)) return;
        try { player.addPassenger(td); } catch (Throwable ignored) {}
        if (!player.getPassengers().contains(td)) {
            // Mount failed — manually teleport display to just above the player.
            // 1.62 wu matches the seat offset the passenger version would use.
            org.bukkit.Location target = player.getLocation().add(0, 1.62 + yOffset, 0);
            target.setYaw(0f);
            target.setPitch(0f);
            if (!td.getWorld().equals(player.getWorld())
                    || td.getLocation().distanceSquared(target) > 0.001) {
                td.teleport(target);
            }
        }
    }

    /**
     * Force-remount the four displays as passengers of the player. This
     * triggers Minecraft's SetPassengers packet to every client that tracks
     * the player, so a newly-joined observer whose entity tracker missed the
     * original mount will now receive fresh passenger data and render the
     * nameplate.
     */
    public void remount(Player player) {
        Nameplate np = plates.get(player.getUniqueId());
        if (np == null) return;
        for (TextDisplay td : new TextDisplay[]{np.teamDisplay, np.nameDisplay,
                np.currencyDisplay, np.bubbleDisplay}) {
            if (td == null || !td.isValid()) continue;
            try {
                player.removePassenger(td);
                player.addPassenger(td);
                td.setInterpolationDuration(0);
                td.setInterpolationDelay(0);
                td.setTeleportDuration(0);
            } catch (Throwable ignored) {}
        }
    }

    public void removeFor(Player player) {
        Nameplate np = plates.remove(player.getUniqueId());
        if (np != null) {
            if (np.teamDisplay != null && np.teamDisplay.isValid()) np.teamDisplay.remove();
            if (np.nameDisplay != null && np.nameDisplay.isValid()) np.nameDisplay.remove();
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
        // Hide vanilla name tag — our TextDisplay handles the name row.
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
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

        // Passenger keeps them glued to the player — just re-mount if detached
        // (world change, respawn, etc.). If passenger add keeps failing, fall
        // back to manual per-tick teleport so text still follows the player.
        followOrTeleport(player, np.teamDisplay, TEAM_OFFSET_Y);
        followOrTeleport(player, np.nameDisplay, NAME_OFFSET_Y);
        followOrTeleport(player, np.bubbleDisplay, BUBBLE_OFFSET_Y);
        followOrTeleport(player, np.currencyDisplay, CURRENCY_OFFSET_Y);

        Component teamText;
        if (simple) {
            teamText = Component.empty();
        } else {
            com.starlightuniverse.team.Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team != null) {
                TextColor bracketColor = WHITE;
                try {
                    if (team.getColors() != null && !team.getColors().isEmpty()) {
                        TextColor c = TextColor.fromHexString(team.getColors().get(0));
                        if (c != null) bracketColor = c;
                    }
                } catch (Exception ignored) {}
                com.starlightuniverse.team.TeamRank rank = team.getMemberRank(player.getName());
                Component rankSuffix = rank != null
                        ? Component.text(" - " + rank.getDisplayName(), bracketColor)
                        : Component.empty();
                teamText = Component.text("[", bracketColor)
                        .append(teamManager.buildGradientName(team.getName(), team.getColors()))
                        .append(rankSuffix)
                        .append(Component.text("]", bracketColor));
            } else {
                teamText = Component.empty();
            }
        }
        if (np.teamDisplay.isValid()) np.teamDisplay.text(teamText);

        // Name line: [Admin] [Premium/Tag] PlayerName. Visually center the NAME (not the
        // whole line) so it sits under the team block regardless of how wide the prefix is.
        // We shift the visual center by padding the right side with as many pixels of
        // spacing font glyphs as the prefix is wide (measured in default-font pixels).
        Component nameText;
        if (simple) {
            nameText = Component.text(player.getName(), WHITE);
        } else {
            Component prefix = buildInlinePrefix(player);
            String prefixPlain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(prefix);
            TextColor nameColor = resolveNameColor(player);
            int prefixPixels = textPixelWidth(prefixPlain);
            String pad = positiveSpacing(prefixPixels);
            nameText = Component.text()
                    .append(prefix)
                    .append(Component.text(player.getName(), nameColor))
                    .append(Component.text(pad, WHITE))
                    .build();
        }
        if (np.nameDisplay.isValid()) np.nameDisplay.text(nameText);

        long now = System.currentTimeMillis();
        Component bubbleText;
        if (np.bubbleMessage != null && now < np.bubbleExpireMs) {
            String msg = np.bubbleMessage;
            if (msg.length() > 60) msg = msg.substring(0, 57) + "...";
            bubbleText = Component.text(msg, WHITE);
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
            int[] stats = readStats(uuid);
            int pvpKills = stats[0];
            int pvmKills = stats[1];
            int deaths = stats[2];

            currencyText = Component.text()
                    .append(icon(com.starlightuniverse.scoreboard.ScoreboardManager.ICON_MONEY))
                    .append(Component.text(EconomyManager.format(money), MONEY_COLOR))
                    .append(sep())
                    .append(icon(com.starlightuniverse.scoreboard.ScoreboardManager.ICON_GEMS))
                    .append(Component.text(EconomyManager.format(gems), GEMS_COLOR))
                    .append(sep())
                    .append(icon(com.starlightuniverse.scoreboard.ScoreboardManager.ICON_STARS))
                    .append(Component.text(EconomyManager.format(stars), STARS_COLOR))
                    .append(sep())
                    .append(icon(com.starlightuniverse.scoreboard.ScoreboardManager.ICON_PVP))
                    .append(Component.text(String.valueOf(pvpKills), PVP_COLOR))
                    .append(sep())
                    .append(icon(com.starlightuniverse.scoreboard.ScoreboardManager.ICON_PVM))
                    .append(Component.text(String.valueOf(pvmKills), PVM_COLOR))
                    .append(sep())
                    .append(icon(com.starlightuniverse.scoreboard.ScoreboardManager.ICON_DEATHS))
                    .append(Component.text(String.valueOf(deaths), DEATHS_COLOR))
                    .build();
        }
        if (np.currencyDisplay.isValid()) np.currencyDisplay.text(currencyText);
    }

    private static Component icon(String glyph) {
        return Component.text(glyph, WHITE);
    }

    private static Component sep() {
        return Component.text(" | ", GRAY);
    }

    private static int textPixelWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ("!.,;:'|iIl".indexOf(c) >= 0) w += 2;
            else if (c == ' ') w += 4;
            else if ("()[]{}\"tf".indexOf(c) >= 0) w += 4;
            else if ("MW@~".indexOf(c) >= 0) w += 7;
            else w += 6;
        }
        return w;
    }

    // Positive spacing glyphs defined in resourcepack/assets/minecraft/font/default.json
    private static String positiveSpacing(int pixels) {
        // Greedy decompose using widths 128, 64, 32, 16, 8, 4, 2, 1 → chars  … 
        int[] widths = {128, 64, 32, 16, 8, 4, 2, 1};
        char[] chars = {'', '', '', '', '', '', '', ''};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < widths.length && pixels > 0; i++) {
            while (pixels >= widths[i]) { sb.append(chars[i]); pixels -= widths[i]; }
        }
        return sb.toString();
    }

    private TextColor resolveNameColor(Player player) {
        String hex = chatManager.getNameColor(player.getUniqueId());
        if (hex != null) {
            TextColor c = TextColor.fromHexString(hex);
            if (c != null) return c;
        }
        return WHITE;
    }

    private int[] readStats(UUID uuid) {
        var sbMgr = StarlightUniverse.getInstance().getScoreboardManager();
        if (sbMgr != null) return sbMgr.getStatsFor(uuid);
        return new int[]{0, 0, 0};
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
        TextDisplay nameDisplay;
        TextDisplay bubbleDisplay;
        TextDisplay currencyDisplay;
        String bubbleMessage;
        long bubbleExpireMs;
    }
}
