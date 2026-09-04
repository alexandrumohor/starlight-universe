package com.starlightuniverse.team;

import com.starlightuniverse.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TeamPvPCommand extends Command {

    private final TeamManager manager;

    public TeamPvPCommand(TeamManager manager) {
        super("teampvp");
        this.manager = manager;
        setDescription("Team PvP battles");
        setUsage("/teampvp <request/accept/cancel>");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0) {
            Msg.info(player, "Usage: /teampvp request <team> <minutes>");
            Msg.gray(player, "/teampvp accept - Accept a PvP challenge");
            Msg.gray(player, "/teampvp cancel - Cancel/surrender");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "request" -> {
                if (args.length < 3) {
                    Msg.error(player, "Usage: /teampvp request <team> <minutes>");
                    return true;
                }
                try {
                    int minutes = Integer.parseInt(args[2]);
                    manager.requestPvP(player, args[1], minutes);
                } catch (NumberFormatException e) {
                    Msg.error(player, "Invalid minutes!");
                }
            }
            case "accept" -> manager.acceptPvP(player);
            case "cancel" -> manager.cancelPvP(player);
            default -> Msg.error(player, "Unknown subcommand. Use: request, accept, cancel");
        }
        return true;
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender s, String a, String[] args) {
        if (args.length == 1) {
            return java.util.List.of("request", "accept", "cancel").stream()
                    .filter(x -> x.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("request")) {
            return java.util.List.of("5", "10", "15", "30");
        }
        return java.util.List.of();
    }
}
