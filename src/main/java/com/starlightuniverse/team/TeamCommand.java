package com.starlightuniverse.team;

import com.starlightuniverse.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class TeamCommand {

    private TeamCommand() {}

    public static List<Command> create(TeamManager manager) {
        List<Command> cmds = new ArrayList<>();
        cmds.add(new TeamCmd(manager));
        cmds.add(new TeamChatCmd(manager));
        return cmds;
    }

    private static class TeamCmd extends Command {
        private final TeamManager manager;

        TeamCmd(TeamManager manager) {
            super("team");
            this.manager = manager;
            setDescription("Team management");
            setUsage("/team <subcommand>");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!(sender instanceof Player player)) return true;
            if (args.length == 0) {
                sendHelp(player);
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "create" -> {
                    if (args.length < 2) { Msg.error(player, "Usage: /team create <name>"); return true; }
                    manager.createTeam(player, args[1]);
                }
                case "invite" -> {
                    if (args.length < 2) { Msg.error(player, "Usage: /team invite <player>"); return true; }
                    manager.invitePlayer(player, args[1]);
                }
                case "accept" -> manager.acceptInvite(player, args.length > 1 ? args[1] : null);
                case "deny" -> manager.denyInvite(player, args.length > 1 ? args[1] : null);
                case "kick" -> {
                    if (args.length < 2) { Msg.error(player, "Usage: /team kick <player>"); return true; }
                    manager.kickPlayer(player, args[1]);
                }
                case "promote" -> {
                    if (args.length < 2) { Msg.error(player, "Usage: /team promote <player>"); return true; }
                    manager.promotePlayer(player, args[1]);
                }
                case "demote" -> {
                    if (args.length < 2) { Msg.error(player, "Usage: /team demote <player>"); return true; }
                    manager.demotePlayer(player, args[1]);
                }
                case "leave" -> manager.leaveTeam(player);
                case "disband" -> manager.disbandTeam(player);
                case "info" -> manager.showInfo(player, args.length > 1 ? args[1] : null);
                case "list" -> manager.openTeamList(player, 0);
                case "sethome" -> manager.setTeamHome(player);
                case "home" -> manager.teleportHome(player);
                case "setcolor" -> {
                    if (args.length < 2) { Msg.error(player, "Usage: /team setcolor <hex> [hex2] [hex3] [hex4] [hex5]"); return true; }
                    List<String> colors = new ArrayList<>();
                    for (int i = 1; i < Math.min(args.length, 6); i++) {
                        String c = args[i];
                        if (!c.startsWith("#")) c = "#" + c;
                        colors.add(c);
                    }
                    manager.setTeamColor(player, colors);
                }
                case "setname" -> {
                    if (args.length < 2) { Msg.error(player, "Usage: /team setname <name>"); return true; }
                    manager.setTeamName(player, args[1]);
                }
                case "friendlyfire", "ff" -> manager.toggleFriendlyFire(player);
                case "chat" -> manager.toggleTeamChat(player);
                case "ally" -> {
                    if (args.length < 2) { Msg.error(player, "Usage: /team ally <team>"); return true; }
                    manager.requestAlly(player, args[1]);
                }
                case "unally" -> {
                    if (args.length < 2) { Msg.error(player, "Usage: /team unally <team>"); return true; }
                    manager.removeAlly(player, args[1]);
                }
                case "deposit" -> {
                    if (args.length < 3) { Msg.error(player, "Usage: /team deposit <money/gems/stars> <amount>"); return true; }
                    try {
                        double amount = Double.parseDouble(args[2]);
                        manager.depositBank(player, args[1], amount);
                    } catch (NumberFormatException e) { Msg.error(player, "Invalid amount!"); }
                }
                case "withdraw" -> {
                    if (args.length < 3) { Msg.error(player, "Usage: /team withdraw <money/gems/stars> <amount>"); return true; }
                    try {
                        double amount = Double.parseDouble(args[2]);
                        manager.withdrawBank(player, args[1], amount);
                    } catch (NumberFormatException e) { Msg.error(player, "Invalid amount!"); }
                }
                case "bank" -> manager.showBank(player);
                case "vault" -> manager.openVault(player);
                case "missions" -> manager.showMissions(player);
                case "top" -> manager.showTop(player);
                case "request" -> {
                    if (args.length < 2) { Msg.error(player, "Usage: /team request <amount> (hold item)"); return true; }
                    try {
                        int amount = Integer.parseInt(args[1]);
                        manager.requestResources(player, amount);
                    } catch (NumberFormatException e) { Msg.error(player, "Invalid amount!"); }
                }
                case "war" -> {
                    if (args.length < 2) { Msg.error(player, "Usage: /team war <team> or /team war surrender"); return true; }
                    if (args[1].equalsIgnoreCase("surrender")) {
                        manager.surrenderWar(player);
                    } else {
                        manager.declareWar(player, args[1]);
                    }
                }
                default -> sendHelp(player);
            }
            return true;
        }

        private static final java.util.List<String> SUBS = java.util.List.of(
                "create", "invite", "accept", "deny", "kick", "promote", "demote",
                "leave", "disband", "info", "list", "sethome", "home",
                "setcolor", "setname", "friendlyfire", "ff", "chat",
                "ally", "unally", "deposit", "withdraw", "bank", "vault",
                "missions", "top", "request", "war");

        @Override
        public java.util.List<String> tabComplete(org.bukkit.command.CommandSender s, String a, String[] args) {
            if (args.length == 1) {
                String pfx = args[0].toLowerCase();
                return SUBS.stream().filter(x -> x.startsWith(pfx)).toList();
            }
            if (args.length == 2) {
                String sub = args[0].toLowerCase();
                switch (sub) {
                    case "invite", "kick", "promote", "demote":
                        String pfx = args[1].toLowerCase();
                        return org.bukkit.Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName).filter(n -> n.toLowerCase().startsWith(pfx)).toList();
                    case "setcolor":
                        return java.util.List.of("#FF5555", "#FFAA00", "#FFFF55",
                                "#55FF55", "#55FFFF", "#AA00AA", "#FF55FF");
                    case "deposit", "withdraw":
                        return java.util.List.of("money", "gems", "stars");
                    case "request":
                        return java.util.List.of("100", "500", "1000");
                    case "war":
                        return java.util.List.of("surrender");
                }
            }
            if (args.length == 3 && (args[0].equalsIgnoreCase("deposit")
                    || args[0].equalsIgnoreCase("withdraw"))) {
                return java.util.List.of("100", "500", "1000", "5000");
            }
            return java.util.List.of();
        }

        private void sendHelp(Player player) {
            Msg.info(player, "=== Team Commands ===");
            Msg.gray(player, "/team create <name> - Create a team ($5,000)");
            Msg.gray(player, "/team invite/accept/deny - Invitations");
            Msg.gray(player, "/team kick/promote/demote <player>");
            Msg.gray(player, "/team leave/disband");
            Msg.gray(player, "/team info [team] - Team details");
            Msg.gray(player, "/team list/top - Browse teams");
            Msg.gray(player, "/team sethome/home - Team home");
            Msg.gray(player, "/team setcolor <hex> - Set team colors (1-5)");
            Msg.gray(player, "/team setname <name> - Rename ($2,000)");
            Msg.gray(player, "/team friendlyfire - Toggle FF");
            Msg.gray(player, "/team chat - Toggle team chat");
            Msg.gray(player, "/team ally/unally <team> - Manage allies");
            Msg.gray(player, "/team deposit/withdraw/bank - Team bank");
            Msg.gray(player, "/team vault - Shared storage (Lv10+)");
            Msg.gray(player, "/team missions - Daily missions");
            Msg.gray(player, "/team request <amount> - Request from allies");
            Msg.gray(player, "/team war <team>/surrender - Team wars");
        }
    }

    private static class TeamChatCmd extends Command {
        private final TeamManager manager;

        TeamChatCmd(TeamManager manager) {
            super("tc");
            this.manager = manager;
            setDescription("Team chat");
            setUsage("/tc [message]");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!(sender instanceof Player player)) return true;
            if (args.length == 0) {
                manager.toggleTeamChat(player);
            } else {
                String message = String.join(" ", args);
                manager.sendTeamChat(player, message);
            }
            return true;
        }
    }
}
