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

public final class MuteWarnCommands {

    private MuteWarnCommands() {}

    public static List<Command> create(AdminManager am, JavaPlugin plugin) {
        return List.of(new MuteCmd(am, plugin), new UnmuteCmd(am, plugin),
                new WarnCmd(am, plugin), new UnwarnCmd(am, plugin), new RemoveWarnsCmd(am, plugin));
    }

    private static class MuteCmd extends AdminCommand {
        private final JavaPlugin plugin;
        MuteCmd(AdminManager am, JavaPlugin plugin) {
            super("mute", "Mute a player", "/mute <player> <unit> <duration> <reason>", am, AdminRank.HELPER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length < 3) { Msg.error(player, "Usage: /mute <player> <min/hours/days> <amount> <reason> (amount 0 = permanent)"); return true; }
            String target = args[0];
            int minutes;
            String reason;
            if (args.length >= 4) {
                minutes = parseDuration(args[1], args[2]);
                reason = joinArgs(args, 3);
            } else {
                try { minutes = Integer.parseInt(args[1]); } catch (NumberFormatException e) { Msg.error(player, "Invalid duration!"); return true; }
                reason = joinArgs(args, 2);
            }
            if (minutes < 0) { Msg.error(player, "Invalid duration!"); return true; }
            if (reason.isEmpty()) reason = "No reason";
            String finalReason = reason;
            adminManager.mutePlayer(target, player.getName(), finalReason, minutes).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String dur = minutes == 0 ? "permanently" : "for " + formatDuration(minutes);
                    Msg.success(player, "Muted " + target + " " + dur + ". Reason: " + finalReason);
                    Player tp = Bukkit.getPlayer(target);
                    if (tp != null) {
                        adminManager.addMuted(tp.getUniqueId());
                        Msg.error(tp, "You have been muted " + dur + "! Reason: " + finalReason);
                    }
                    staffBroadcast(player.getName() + " muted " + target + " " + dur + ": " + finalReason);
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

    private static class UnmuteCmd extends AdminCommand {
        private final JavaPlugin plugin;
        UnmuteCmd(AdminManager am, JavaPlugin plugin) {
            super("unmute", "Unmute a player", "/unmute <player>", am, AdminRank.HELPER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /unmute <player>"); return true; }
            String target = args[0];
            adminManager.unmutePlayer(target).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Msg.success(player, "Unmuted " + target + ".");
                    Player tp = Bukkit.getPlayer(target);
                    if (tp != null) {
                        adminManager.removeMuted(tp.getUniqueId());
                        Msg.success(tp, "You have been unmuted.");
                    }
                }));
            return true;
        }
    }

    private static class WarnCmd extends AdminCommand {
        private final JavaPlugin plugin;
        WarnCmd(AdminManager am, JavaPlugin plugin) {
            super("warn", "Warn a player", "/warn <player> <reason>", am, AdminRank.HELPER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length < 2) { Msg.error(player, "Usage: /warn <player> <reason>"); return true; }
            String target = args[0];
            String reason = joinArgs(args, 1);
            adminManager.warnPlayer(target, player.getName(), reason).thenAccept(count ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (count < 0) { Msg.error(player, "Player not found!"); return; }
                    Msg.success(player, "Warned " + target + " (" + count + "/5). Reason: " + reason);
                    Player tp = Bukkit.getPlayer(target);
                    if (tp != null) {
                        Msg.error(tp, "You have been warned! (" + count + "/5) Reason: " + reason);
                        if (count >= 5) {
                            tp.kick(Component.text("[SU] Kicked: Too many warnings (5/5)"));
                            Msg.info(player, target + " was auto-kicked for reaching 5 warnings.");
                        }
                    }
                    staffBroadcast(player.getName() + " warned " + target + " (" + count + "/5): " + reason);
                }));
            return true;
        }
    }

    private static class UnwarnCmd extends AdminCommand {
        private final JavaPlugin plugin;
        UnwarnCmd(AdminManager am, JavaPlugin plugin) {
            super("unwarn", "Remove a specific warning", "/unwarn <player> <warnId>", am, AdminRank.HELPER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 2) { Msg.error(player, "Usage: /unwarn <player> <warnId>"); return true; }
            int warnId;
            try { warnId = Integer.parseInt(args[1]); } catch (NumberFormatException e) { Msg.error(player, "Invalid warn ID!"); return true; }
            adminManager.removeWarn(warnId).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> Msg.success(player, "Removed warning #" + warnId + ".")));
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            return args.length == 1 ? playerCompletions(args[0]) : List.of();
        }
    }

    private static class RemoveWarnsCmd extends AdminCommand {
        private final JavaPlugin plugin;
        RemoveWarnsCmd(AdminManager am, JavaPlugin plugin) {
            super("removewarns", "Remove all warnings from a player", "/removewarns <player>", am, AdminRank.HELPER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /removewarns <player>"); return true; }
            adminManager.removeAllWarns(args[0]).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> Msg.success(player, "Removed all warnings from " + args[0] + ".")));
            return true;
        }
    }

    private static void staffBroadcast(String message) {
        Component msg = Msg.prefix().append(Component.text(message, TextColor.color(0xAAAAAA)));
        AdminManager am = com.starlightuniverse.StarlightUniverse.getInstance().getAdminManager();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (am.getAdminLevel(online.getUniqueId()) > 0) online.sendMessage(msg);
        }
    }
}
