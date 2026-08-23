package com.starlightuniverse.admin;

import com.starlightuniverse.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;

import java.util.List;

public final class TeleportCommands {

    private TeleportCommands() {}

    public static List<Command> create(AdminManager am) {
        return List.of(new TpCmd(am), new TpHereCmd(am));
    }

    private static class TpCmd extends AdminCommand {
        TpCmd(AdminManager am) {
            super("tp", "Teleport to a player", "/tp <player>", am, AdminRank.MODERATOR.getLevel());
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /tp <player>"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { Msg.error(player, "Player not online!"); return true; }
            player.teleport(target.getLocation());
            Msg.success(player, "Teleported to " + target.getName() + ".");
            return true;
        }
    }

    private static class TpHereCmd extends AdminCommand {
        TpHereCmd(AdminManager am) {
            super("tphere", "Teleport a player to you", "/tphere <player>", am, AdminRank.MODERATOR.getLevel());
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /tphere <player>"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { Msg.error(player, "Player not online!"); return true; }
            target.teleport(player.getLocation());
            Msg.success(player, "Teleported " + target.getName() + " to you.");
            Msg.info(target, "You were teleported to " + player.getName() + ".");
            return true;
        }
    }
}
