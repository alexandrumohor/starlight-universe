package com.starlightuniverse.admin;

import com.starlightuniverse.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public final class RankCommands {

    private RankCommands() {}

    public static List<Command> create(AdminManager am, JavaPlugin plugin) {
        return List.of(new SetAdminCmd(am, plugin), new RemoveAdminCmd(am, plugin),
                new SetPremiumCmd(am, plugin), new RemovePremiumCmd(am, plugin));
    }

    private static class SetAdminCmd extends AdminCommand {
        private final JavaPlugin plugin;
        SetAdminCmd(AdminManager am, JavaPlugin plugin) {
            super("setadmin", "Set a player's admin rank", "/setadmin <player> <rank>", am, AdminRank.OWNER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 2) { Msg.error(player, "Usage: /setadmin <player> <owner|moderator|helper|trialhelper>"); return true; }
            AdminRank rank = AdminRank.fromName(args[1]);
            if (rank == null || rank == AdminRank.NONE) { Msg.error(player, "Invalid rank! Use: owner, moderator, helper, trialhelper"); return true; }
            String target = args[0];
            adminManager.setAdminLevel(target, rank.getLevel()).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Msg.success(player, "Set " + target + " to " + rank.getDisplayName() + ".");
                    Player tp = Bukkit.getPlayer(target);
                    if (tp != null) {
                        adminManager.loadPlayer(tp.getUniqueId(), tp.getName());
                        if (rank.getLevel() >= AdminRank.OWNER.getLevel()) tp.setOp(true);
                        Msg.info(tp, "You are now " + rank.getDisplayName() + "!");
                    }
                }));
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            if (args.length == 1) return playerCompletions(args[0]);
            if (args.length == 2) return Arrays.stream(AdminRank.values()).filter(r -> r != AdminRank.NONE)
                    .map(r -> r.name().toLowerCase()).filter(n -> n.startsWith(args[1].toLowerCase())).toList();
            return List.of();
        }
    }

    private static class RemoveAdminCmd extends AdminCommand {
        private final JavaPlugin plugin;
        RemoveAdminCmd(AdminManager am, JavaPlugin plugin) {
            super("removeadmin", "Remove a player's admin rank", "/removeadmin <player>", am, AdminRank.OWNER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /removeadmin <player>"); return true; }
            String target = args[0];
            adminManager.setAdminLevel(target, 0).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Msg.success(player, "Removed admin rank from " + target + ".");
                    Player tp = Bukkit.getPlayer(target);
                    if (tp != null) {
                        adminManager.loadPlayer(tp.getUniqueId(), tp.getName());
                        tp.setOp(false);
                        Msg.info(tp, "Your admin rank has been removed.");
                    }
                }));
            return true;
        }
    }

    private static class SetPremiumCmd extends AdminCommand {
        private final JavaPlugin plugin;
        SetPremiumCmd(AdminManager am, JavaPlugin plugin) {
            super("setpremium", "Set a player's premium rank", "/setpremium <player> <0-5>", am, AdminRank.OWNER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 2) { Msg.error(player, "Usage: /setpremium <player> <0-5> (0=None,1=Meteor,2=Comet,3=Nebula,4=Supernova,5=Galaxy)"); return true; }
            int level;
            try { level = Integer.parseInt(args[1]); } catch (NumberFormatException e) { Msg.error(player, "Invalid level!"); return true; }
            if (level < 0 || level > 5) { Msg.error(player, "Level must be 0-5!"); return true; }
            String target = args[0];
            adminManager.setPremiumLevel(target, level).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Msg.success(player, "Set " + target + " to " + AdminRank.premiumName(level) + ".");
                    Player tp = Bukkit.getPlayer(target);
                    if (tp != null) {
                        adminManager.loadPlayer(tp.getUniqueId(), tp.getName());
                        Msg.info(tp, "Your premium rank is now " + AdminRank.premiumName(level) + "!");
                    }
                }));
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            if (args.length == 1) return playerCompletions(args[0]);
            if (args.length == 2) return List.of("0", "1", "2", "3", "4", "5");
            return List.of();
        }
    }

    private static class RemovePremiumCmd extends AdminCommand {
        private final JavaPlugin plugin;
        RemovePremiumCmd(AdminManager am, JavaPlugin plugin) {
            super("removepremium", "Remove a player's premium rank", "/removepremium <player>", am, AdminRank.OWNER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /removepremium <player>"); return true; }
            String target = args[0];
            adminManager.setPremiumLevel(target, 0).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Msg.success(player, "Removed premium rank from " + target + ".");
                    Player tp = Bukkit.getPlayer(target);
                    if (tp != null) {
                        adminManager.loadPlayer(tp.getUniqueId(), tp.getName());
                        Msg.info(tp, "Your premium rank has been removed.");
                    }
                }));
            return true;
        }
    }
}
