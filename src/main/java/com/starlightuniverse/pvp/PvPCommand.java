package com.starlightuniverse.pvp;

import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class PvPCommand extends Command {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);

    private final PvPManager manager;

    public PvPCommand(PvPManager manager) {
        super("pvp");
        setDescription("PvP Arena — 1v1 battles");
        setUsage("/pvp <join|leave|top|spectate|stats>");
        this.manager = manager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /pvp.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "join" -> {
                boolean ranked = true;
                if (args.length >= 2) {
                    if (args[1].equalsIgnoreCase("unranked")) ranked = false;
                    else if (!args[1].equalsIgnoreCase("ranked")) {
                        Msg.error(player, "Usage: /pvp join [ranked|unranked]");
                        return true;
                    }
                }
                manager.joinQueue(player, ranked);
            }
            case "leave" -> manager.leaveQueue(player);
            case "spectate" -> {
                if (manager.isSpectating(player.getUniqueId())) {
                    manager.leaveSpectate(player);
                } else {
                    manager.enterSpectate(player);
                }
            }
            case "top" -> showTop(player);
            case "stats" -> {
                if (args.length >= 2) {
                    showStats(player, args[1]);
                } else {
                    showStats(player, player.getName());
                }
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Msg.prefix().append(
                Component.text("PvP Arena Commands", GOLD, TextDecoration.BOLD)));
        player.sendMessage(Msg.prefix().append(Component.text("/pvp join [ranked|unranked]", CYAN))
                .append(Component.text(" — join the queue", GRAY)));
        player.sendMessage(Msg.prefix().append(Component.text("/pvp leave", CYAN))
                .append(Component.text(" — leave queue or forfeit match", GRAY)));
        player.sendMessage(Msg.prefix().append(Component.text("/pvp spectate", CYAN))
                .append(Component.text(" — watch a match / return", GRAY)));
        player.sendMessage(Msg.prefix().append(Component.text("/pvp top", CYAN))
                .append(Component.text(" — show top players", GRAY)));
        player.sendMessage(Msg.prefix().append(Component.text("/pvp stats [player]", CYAN))
                .append(Component.text(" — show PvP stats", GRAY)));
    }

    private void showTop(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("StarlightUniverse"), () -> {
            List<PvPManager.TopEntry> top = manager.getTop();
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("StarlightUniverse"), () -> {
                if (!player.isOnline()) return;
                player.sendMessage(Msg.prefix().append(
                        Component.text("Top PvP Players", GOLD, TextDecoration.BOLD)));
                if (top.isEmpty()) {
                    player.sendMessage(Msg.prefix().append(
                            Component.text("No ranked matches played yet.", GRAY)));
                    return;
                }
                int rank = 1;
                for (PvPManager.TopEntry e : top) {
                    PvPArena.Tier tier = PvPArena.Tier.of(e.elo());
                    Component tierC = Component.text("[" + tier.display + "] ", TextColor.color(tier.color));
                    Component line = Component.text("#" + rank + " ", GOLD)
                            .append(tierC)
                            .append(Component.text(e.username(), YELLOW))
                            .append(Component.text(" — " + e.elo() + " Elo ", GRAY))
                            .append(Component.text("(" + e.wins() + "W / " + e.losses() + "L)", GRAY));
                    player.sendMessage(Msg.prefix().append(line));
                    rank++;
                }
            });
        });
    }

    private void showStats(Player player, String targetName) {
        UUID targetUuid = null;
        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null) targetUuid = target.getUniqueId();

        if (targetUuid != null) {
            PvPStats stats = manager.getStats(targetUuid);
            renderStats(player, targetName, stats);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugin("StarlightUniverse"), () -> {
                PvPStats stats = loadOffline(targetName);
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("StarlightUniverse"), () -> {
                    if (!player.isOnline()) return;
                    if (stats == null) {
                        Msg.error(player, "Player not found: " + targetName);
                        return;
                    }
                    renderStats(player, targetName, stats);
                });
            });
        }
    }

    private PvPStats loadOffline(String username) {
        try (var conn = com.starlightuniverse.StarlightUniverse.getInstance().getDatabaseManager().getConnection();
             var ps = conn.prepareStatement(
                     "SELECT elo, wins, losses, arena_kills, arena_deaths, current_streak, best_streak " +
                             "FROM su_pvp_stats WHERE username = ?")) {
            ps.setString(1, username.toLowerCase());
            try (var rs = ps.executeQuery()) {
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
        } catch (Exception ignored) {}
        return null;
    }

    private void renderStats(Player viewer, String name, PvPStats s) {
        PvPArena.Tier tier = s.tier();
        Component tierC = Component.text("[" + tier.display + "]", TextColor.color(tier.color), TextDecoration.BOLD);

        viewer.sendMessage(Msg.prefix().append(
                Component.text("PvP Stats — " + name, GOLD, TextDecoration.BOLD)));
        viewer.sendMessage(Msg.prefix().append(Component.text("Rank: ", GRAY))
                .append(tierC).append(Component.text(" (" + s.elo + " Elo)", YELLOW)));
        viewer.sendMessage(Msg.prefix().append(Component.text("Wins: ", GRAY))
                .append(Component.text(String.valueOf(s.wins), GREEN))
                .append(Component.text("   Losses: ", GRAY))
                .append(Component.text(String.valueOf(s.losses), RED)));
        int total = s.wins + s.losses;
        double winRate = total == 0 ? 0 : (s.wins * 100.0 / total);
        viewer.sendMessage(Msg.prefix().append(Component.text("Win rate: ", GRAY))
                .append(Component.text(String.format("%.1f%%", winRate), CYAN)));
        viewer.sendMessage(Msg.prefix().append(Component.text("Kills: ", GRAY))
                .append(Component.text(String.valueOf(s.arenaKills), CYAN))
                .append(Component.text("   Deaths: ", GRAY))
                .append(Component.text(String.valueOf(s.arenaDeaths), CYAN)));
        viewer.sendMessage(Msg.prefix().append(Component.text("Current streak: ", GRAY))
                .append(Component.text(String.valueOf(s.currentStreak), YELLOW))
                .append(Component.text("   Best: ", GRAY))
                .append(Component.text(String.valueOf(s.bestStreak), YELLOW)));
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("join", "leave", "spectate", "top", "stats");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            return List.of("ranked", "unranked");
        }
        return List.of();
    }
}
