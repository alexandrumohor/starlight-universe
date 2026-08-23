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

public final class ReportCommands {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);

    private ReportCommands() {}

    public static List<Command> create(AdminManager am, JavaPlugin plugin) {
        return List.of(new ReportCmd(am, plugin), new ReportsCmd(am, plugin),
                new RespondCmd(am, plugin), new HelpOpCmd(am), new AdminReplyCmd(am));
    }

    private static class ReportCmd extends AdminCommand {
        private final JavaPlugin plugin;
        ReportCmd(AdminManager am, JavaPlugin plugin) {
            super("report", "Report a player", "/report <player> <reason>", am, AdminRank.TRIAL_HELPER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length < 2) { Msg.error(player, "Usage: /report <player> <reason>"); return true; }
            String target = args[0];
            String reason = joinArgs(args, 1);
            adminManager.addReport(player.getName(), target, reason).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Msg.success(player, "Report submitted against " + target + ".");
                    Component staffMsg = Msg.prefix()
                            .append(Component.text("[Report] ", RED))
                            .append(Component.text(player.getName(), WHITE))
                            .append(Component.text(" reported ", GRAY))
                            .append(Component.text(target, WHITE))
                            .append(Component.text(": " + reason, GRAY));
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (adminManager.getAdminLevel(online.getUniqueId()) >= AdminRank.HELPER.getLevel())
                            online.sendMessage(staffMsg);
                    }
                }));
            return true;
        }
    }

    private static class ReportsCmd extends AdminCommand {
        private final JavaPlugin plugin;
        ReportsCmd(AdminManager am, JavaPlugin plugin) {
            super("reports", "View active reports", "/reports", am, AdminRank.HELPER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            adminManager.getActiveReports().thenAccept(reports ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (reports.isEmpty()) { Msg.info(player, "No active reports."); return; }
                    player.sendMessage(Component.text("=== Active Reports ===", GOLD));
                    for (AdminManager.ReportInfo r : reports) {
                        player.sendMessage(Component.text("#" + r.id() + " ", YELLOW)
                                .append(Component.text(r.reporter(), WHITE))
                                .append(Component.text(" -> ", GRAY))
                                .append(Component.text(r.reported(), WHITE))
                                .append(Component.text(": " + r.reason(), GRAY))
                                .append(Component.text(" (" + r.reportDate() + ")", GRAY)));
                    }
                    player.sendMessage(Component.text("Use /respond <id> to close a report.", GRAY));
                }));
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            return List.of();
        }
    }

    private static class RespondCmd extends AdminCommand {
        private final JavaPlugin plugin;
        RespondCmd(AdminManager am, JavaPlugin plugin) {
            super("respond", "Respond to and close a report", "/respond <reportId>", am, AdminRank.HELPER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /respond <reportId>"); return true; }
            int id;
            try { id = Integer.parseInt(args[0]); } catch (NumberFormatException e) { Msg.error(player, "Invalid report ID!"); return true; }
            adminManager.respondToReport(id, player.getName()).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> Msg.success(player, "Report #" + id + " closed.")));
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            return List.of();
        }
    }

    private static class HelpOpCmd extends AdminCommand {
        HelpOpCmd(AdminManager am) {
            super("helpop", "Request help from staff", "/helpop <message>", am, AdminRank.TRIAL_HELPER.getLevel());
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length < 1) { Msg.error(player, "Usage: /helpop <message>"); return true; }
            String message = joinArgs(args, 0);
            Msg.success(player, "Help request sent to staff.");
            Component staffMsg = Msg.prefix()
                    .append(Component.text("[HelpOp] ", TextColor.color(0x55FFFF)))
                    .append(Component.text(player.getName(), WHITE))
                    .append(Component.text(": " + message, GRAY));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (adminManager.getAdminLevel(online.getUniqueId()) >= AdminRank.HELPER.getLevel())
                    online.sendMessage(staffMsg);
            }
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            return List.of();
        }
    }

    private static class AdminReplyCmd extends AdminCommand {
        AdminReplyCmd(AdminManager am) {
            super("reply", "Publicly reply to a player", "/reply <player> <message>", am, AdminRank.TRIAL_HELPER.getLevel());
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length < 2) { Msg.error(player, "Usage: /reply <player> <message>"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { Msg.error(player, "Player not online!"); return true; }
            String message = joinArgs(args, 1);
            AdminRank rank = adminManager.getAdminRank(player.getUniqueId());
            Component replyMsg = Component.text(rank.getPrefix() + " ", rank.getColor())
                    .append(Component.text(player.getName(), WHITE))
                    .append(Component.text(" replies to ", GRAY))
                    .append(Component.text(target.getName(), WHITE))
                    .append(Component.text(" >>> ", GOLD))
                    .append(Component.text(message, GREEN));
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(replyMsg);
            }
            return true;
        }
    }
}
