package com.starlightuniverse.admin;

import com.starlightuniverse.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PasswordNameCommands {

    private PasswordNameCommands() {}

    public static List<Command> create(AdminManager am, JavaPlugin plugin) {
        return List.of(new SetPassCmd(am, plugin), new OSetPassCmd(am, plugin),
                new SetNameCmd(am, plugin), new OSetNameCmd(am, plugin));
    }

    private static class SetPassCmd extends AdminCommand {
        private final JavaPlugin plugin;
        SetPassCmd(AdminManager am, JavaPlugin plugin) {
            super("setpass", "Set your password (Owner)", "/setpass <newpassword>", am, AdminRank.OWNER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /setpass <newpassword>"); return true; }
            if (args[0].length() < 3) { Msg.error(player, "Password must be at least 3 characters!"); return true; }
            adminManager.setPassword(player.getName(), args[0]).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) Msg.success(player, "Password changed.");
                    else Msg.error(player, "Failed to change password.");
                }));
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            return List.of();
        }
    }

    private static class OSetPassCmd extends AdminCommand {
        private final JavaPlugin plugin;
        OSetPassCmd(AdminManager am, JavaPlugin plugin) {
            super("osetpass", "Set another player's password", "/osetpass <player> <newpassword>", am, AdminRank.OWNER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 2) { Msg.error(player, "Usage: /osetpass <player> <newpassword>"); return true; }
            if (args[1].length() < 3) { Msg.error(player, "Password must be at least 3 characters!"); return true; }
            adminManager.setPassword(args[0], args[1]).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) Msg.success(player, "Password set for " + args[0] + ".");
                    else Msg.error(player, "Player not found!");
                }));
            return true;
        }
    }

    private static class SetNameCmd extends AdminCommand {
        private final JavaPlugin plugin;
        SetNameCmd(AdminManager am, JavaPlugin plugin) {
            super("setname", "Change your username (Owner)", "/setname <newname>", am, AdminRank.OWNER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /setname <newname>"); return true; }
            String newName = args[0];
            if (newName.length() < 3 || newName.length() > 16) { Msg.error(player, "Name must be 3-16 characters!"); return true; }
            if (!newName.matches("[a-zA-Z0-9_]+")) { Msg.error(player, "Name can only contain letters, numbers, and underscores!"); return true; }
            adminManager.changeName(player.getName(), newName).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) {
                        Msg.success(player, "Name changed to " + newName + ". Please relog.");
                        player.kick(Msg.errorComponent("Your name was changed to " + newName + ". Please reconnect."));
                    } else {
                        Msg.error(player, "Name '" + newName + "' is already taken!");
                    }
                }));
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            return List.of();
        }
    }

    private static class OSetNameCmd extends AdminCommand {
        private final JavaPlugin plugin;
        OSetNameCmd(AdminManager am, JavaPlugin plugin) {
            super("osetname", "Change another player's username", "/osetname <player> <newname>", am, AdminRank.OWNER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 2) { Msg.error(player, "Usage: /osetname <player> <newname>"); return true; }
            String oldName = args[0];
            String newName = args[1];
            if (newName.length() < 3 || newName.length() > 16) { Msg.error(player, "Name must be 3-16 characters!"); return true; }
            if (!newName.matches("[a-zA-Z0-9_]+")) { Msg.error(player, "Invalid characters in name!"); return true; }
            adminManager.changeName(oldName, newName).thenAccept(ok ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (ok) {
                        Msg.success(player, "Changed " + oldName + "'s name to " + newName + ".");
                        Player tp = Bukkit.getPlayer(oldName);
                        if (tp != null) {
                            tp.kick(Msg.errorComponent("Your name was changed to " + newName + ". Please reconnect."));
                        }
                    } else {
                        Msg.error(player, "Failed! Name may be taken or player not found.");
                    }
                }));
            return true;
        }
    }
}
