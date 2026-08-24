package com.starlightuniverse.pwarp;

import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PWarpCommand {

    private PWarpCommand() {}

    public static List<Command> create(PWarpManager pm) {
        return List.of(new PWarpCmd(pm), new PWarpsCmd(pm));
    }

    // /pwarps → GUI
    private static class PWarpsCmd extends Command {
        private final PWarpManager pm;
        PWarpsCmd(PWarpManager pm) {
            super("pwarps");
            setDescription("Browse personal warps");
            setUsage("/pwarps");
            this.pm = pm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            pm.openBrowseGui(player, 0, null, PWarpManager.Sort.BEST_RATING);
            return true;
        }
    }

    // /pwarp <name> | /pwarp create <name> <description...> | /pwarp delete <name>
    // /pwarp rate <name> <stars> | /pwarp ban <player> [warp] | /pwarp unban <player> [warp]
    // /pwarp mine | /pwarp list
    private static class PWarpCmd extends Command {
        private final PWarpManager pm;
        PWarpCmd(PWarpManager pm) {
            super("pwarp");
            setDescription("Personal warp commands");
            setUsage("/pwarp <name|create|delete|rate|ban|unban|mine>");
            this.pm = pm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            if (args.length == 0) { sendHelp(player); return true; }
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "create" -> {
                    if (args.length < 3) {
                        Msg.error(player, "Usage: /pwarp create <name> <description> (description is required, min "
                                + PWarpManager.MIN_DESCRIPTION + " chars)");
                        return true;
                    }
                    String name = args[1];
                    StringBuilder desc = new StringBuilder();
                    for (int i = 2; i < args.length; i++) {
                        if (i > 2) desc.append(' ');
                        desc.append(args[i]);
                    }
                    pm.createWarp(player, name, desc.toString());
                }
                case "delete", "remove" -> {
                    if (args.length < 2) { Msg.error(player, "Usage: /pwarp delete <name>"); return true; }
                    pm.deleteWarp(player, args[1]);
                }
                case "rate" -> {
                    if (args.length < 3) { Msg.error(player, "Usage: /pwarp rate <name> <1-5>"); return true; }
                    int stars;
                    try { stars = Integer.parseInt(args[2]); }
                    catch (NumberFormatException e) { Msg.error(player, "Invalid stars value!"); return true; }
                    PersonalWarp w = pm.resolveWarp(player, args[1]);
                    if (w == null) return true;
                    pm.rateWarp(player, w, stars);
                }
                case "ban" -> {
                    if (args.length < 2) {
                        Msg.error(player, "Usage: /pwarp ban <player> [warp]  (no warp = ban from ALL your warps)");
                        return true;
                    }
                    if (args.length >= 3) {
                        PersonalWarp target = pm.getWarp(player.getName(), args[2]);
                        if (target == null) { Msg.error(player, "You don't own a warp named \"" + args[2] + "\"!"); return true; }
                        pm.banFromWarp(player, args[1], target);
                    } else {
                        pm.banGlobal(player, args[1]);
                    }
                }
                case "unban" -> {
                    if (args.length < 2) {
                        Msg.error(player, "Usage: /pwarp unban <player> [warp]");
                        return true;
                    }
                    if (args.length >= 3) {
                        PersonalWarp target = pm.getWarp(player.getName(), args[2]);
                        if (target == null) { Msg.error(player, "You don't own a warp named \"" + args[2] + "\"!"); return true; }
                        pm.unbanFromWarp(player, args[1], target);
                    } else {
                        pm.unbanGlobal(player, args[1]);
                    }
                }
                case "mine", "my", "list" -> pm.openMyWarpsGui(player);
                case "help" -> sendHelp(player);
                default -> {
                    PersonalWarp w = pm.resolveWarp(player, args[0]);
                    if (w != null) pm.teleport(player, w);
                }
            }
            return true;
        }
        private void sendHelp(Player p) {
            TextColor gold = TextColor.color(0xFFD700);
            TextColor gray = TextColor.color(0xAAAAAA);
            p.sendMessage(Component.text("[SU] Personal Warps:", gold));
            p.sendMessage(Component.text("  /pwarp <name> — teleport (use owner:name if ambiguous)", gray));
            p.sendMessage(Component.text("  /pwarp create <name> <description> — create warp here ($2,500)", gray));
            p.sendMessage(Component.text("  /pwarp delete <name> — delete YOUR warp with that name", gray));
            p.sendMessage(Component.text("  /pwarp rate <name> <1-5> — rate a warp", gray));
            p.sendMessage(Component.text("  /pwarp ban <player> [warp] — ban from all your warps, or one specific warp", gray));
            p.sendMessage(Component.text("  /pwarp unban <player> [warp] — remove a ban", gray));
            p.sendMessage(Component.text("  /pwarp mine — manage your warps", gray));
            p.sendMessage(Component.text("  /pwarps — browse all warps", gray));
        }
        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (args.length == 1) {
                List<String> options = new ArrayList<>(Arrays.asList(
                        "create", "delete", "rate", "ban", "unban", "mine", "help"));
                for (PersonalWarp w : pm.getAllWarps()) {
                    if (!options.contains(w.getName())) options.add(w.getName());
                }
                String lower = args[0].toLowerCase();
                return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
            }
            if (args.length == 2) {
                switch (args[0].toLowerCase()) {
                    case "delete", "rate" -> {
                        if (sender instanceof Player p) {
                            return pm.getWarpsOf(p.getName()).stream().map(PersonalWarp::getName)
                                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
                        }
                    }
                    case "ban", "unban" -> {
                        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                                .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
                    }
                }
            }
            if (args.length == 3 && (args[0].equalsIgnoreCase("ban") || args[0].equalsIgnoreCase("unban"))) {
                if (sender instanceof Player p) {
                    return pm.getWarpsOf(p.getName()).stream().map(PersonalWarp::getName)
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).toList();
                }
            }
            return List.of();
        }
    }
}
