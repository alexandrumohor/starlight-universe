package com.starlightuniverse.admin;

import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class BanCommands {

    private BanCommands() {}

    public static List<Command> create(AdminManager am, JavaPlugin plugin) {
        return List.of(new BanCmd(am, plugin), new OfflineBanCmd(am, plugin),
                new UnbanCmd(am, plugin), new TempBanCmd(am, plugin));
    }

    private static class BanCmd extends AdminCommand {
        private final JavaPlugin plugin;
        BanCmd(AdminManager am, JavaPlugin plugin) {
            super("ban", "Permanently ban a player", "/ban <player> <reason>", am, AdminRank.OWNER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length < 2) { Msg.error(player, "Usage: /ban <player> <reason>"); return true; }
            String target = args[0];
            String reason = joinArgs(args, 1);
            Player tp = Bukkit.getPlayer(target);
            adminManager.banPlayer(target, player.getName(), reason, 0).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Msg.success(player, "Permanently banned " + target + ". Reason: " + reason);
                    if (tp != null && tp.isOnline()) {
                        tp.kick(Component.text("[SU] You have been banned!\nReason: " + reason + "\nDuration: Permanent"));
                    }
                    broadcastToStaff(player.getName() + " banned " + target + ": " + reason);
                }));
            return true;
        }
    }

    private static class OfflineBanCmd extends AdminCommand {
        private final JavaPlugin plugin;
        OfflineBanCmd(AdminManager am, JavaPlugin plugin) {
            super("offlineban", "Ban an offline player", "/offlineban <player> <reason>", am, AdminRank.OWNER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length < 2) { Msg.error(player, "Usage: /offlineban <player> <reason>"); return true; }
            String target = args[0];
            String reason = joinArgs(args, 1);
            adminManager.banPlayer(target, player.getName(), reason, 0).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Msg.success(player, "Banned " + target + " (offline). Reason: " + reason);
                    broadcastToStaff(player.getName() + " offline-banned " + target + ": " + reason);
                }));
            return true;
        }
    }

    private static class UnbanCmd extends AdminCommand {
        private final JavaPlugin plugin;
        UnbanCmd(AdminManager am, JavaPlugin plugin) {
            super("unban", "Unban a player", "/unban <player>", am, AdminRank.OWNER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /unban <player>"); return true; }
            adminManager.unbanPlayer(args[0]).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Msg.success(player, "Unbanned " + args[0] + ".");
                    broadcastToStaff(player.getName() + " unbanned " + args[0]);
                }));
            return true;
        }
    }

    private static class TempBanCmd extends AdminCommand {
        private final JavaPlugin plugin;
        TempBanCmd(AdminManager am, JavaPlugin plugin) {
            super("tempban", "Temporarily ban a player", "/tempban <player> <unit> <duration> <reason>", am, AdminRank.MODERATOR.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length < 4) { Msg.error(player, "Usage: /tempban <player> <min/hours/days/weeks/months> <amount> <reason>"); return true; }
            String target = args[0];
            int minutes = parseDuration(args[1], args[2]);
            if (minutes < 0) { Msg.error(player, "Invalid duration! Use: min/hours/days/weeks/months"); return true; }
            if (minutes == 0) { Msg.error(player, "Use /ban for permanent bans."); return true; }
            String reason = joinArgs(args, 3);
            Player tp = Bukkit.getPlayer(target);
            adminManager.banPlayer(target, player.getName(), reason, minutes).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Msg.success(player, "Temp-banned " + target + " for " + formatDuration(minutes) + ". Reason: " + reason);
                    if (tp != null && tp.isOnline()) {
                        tp.kick(Component.text("[SU] You have been banned!\nReason: " + reason + "\nDuration: " + formatDuration(minutes)));
                    }
                    broadcastToStaff(player.getName() + " temp-banned " + target + " (" + formatDuration(minutes) + "): " + reason);
                }));
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            if (args.length == 1) return playerCompletions(args[0]);
            if (args.length == 2) return List.of("min", "hours", "days", "weeks", "months").stream()
                    .filter(u -> u.startsWith(args[1].toLowerCase())).toList();
            return List.of();
        }
    }

    private static void broadcastToStaff(String message) {
        Component msg = Msg.prefix().append(Component.text(message, TextColor.color(0xAAAAAA)));
        AdminManager am = com.starlightuniverse.StarlightUniverse.getInstance().getAdminManager();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (am.getAdminLevel(online.getUniqueId()) > 0) online.sendMessage(msg);
        }
    }
}
