package com.starlightuniverse.travel;

import com.starlightuniverse.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class TpaCommands {

    private TpaCommands() {}

    public static List<Command> create(TpaManager tm) {
        return List.of(
                new TpaCmd(tm),
                new TpaHereCmd(tm),
                new TpAcceptCmd(tm),
                new TpDenyCmd(tm),
                new TpToggleCmd(tm),
                new TpCancelCmd(tm),
                new TpBlockCmd(tm)
        );
    }

    private static List<String> playerCompletions(String prefix) {
        String lower = prefix.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(lower))
                .toList();
    }

    // /tpa <player>
    private static class TpaCmd extends Command {
        private final TpaManager tm;
        TpaCmd(TpaManager tm) {
            super("tpa");
            setDescription("Request to teleport to a player");
            setUsage("/tpa <player>");
            this.tm = tm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            if (args.length < 1) { Msg.error(player, "Usage: /tpa <player>"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { Msg.error(player, "Player not found or offline!"); return true; }
            tm.sendRequest(player, target, TpaManager.RequestType.TO_TARGET);
            return true;
        }
        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (args.length == 1) return playerCompletions(args[0]);
            return List.of();
        }
    }

    // /tpahere <player>
    private static class TpaHereCmd extends Command {
        private final TpaManager tm;
        TpaHereCmd(TpaManager tm) {
            super("tpahere");
            setDescription("Request a player to teleport to you");
            setUsage("/tpahere <player>");
            this.tm = tm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            if (args.length < 1) { Msg.error(player, "Usage: /tpahere <player>"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { Msg.error(player, "Player not found or offline!"); return true; }
            tm.sendRequest(player, target, TpaManager.RequestType.TO_SENDER);
            return true;
        }
        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (args.length == 1) return playerCompletions(args[0]);
            return List.of();
        }
    }

    // /tpaccept [player]
    private static class TpAcceptCmd extends Command {
        private final TpaManager tm;
        TpAcceptCmd(TpaManager tm) {
            super("tpaccept");
            setDescription("Accept a teleport request");
            setUsage("/tpaccept [player]");
            this.tm = tm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            tm.acceptRequest(player, args.length > 0 ? args[0] : null);
            return true;
        }
        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (args.length == 1) return playerCompletions(args[0]);
            return List.of();
        }
    }

    // /tpdeny [player]
    private static class TpDenyCmd extends Command {
        private final TpaManager tm;
        TpDenyCmd(TpaManager tm) {
            super("tpdeny");
            setDescription("Deny a teleport request");
            setUsage("/tpdeny [player]");
            this.tm = tm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            tm.denyRequest(player, args.length > 0 ? args[0] : null);
            return true;
        }
        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (args.length == 1) return playerCompletions(args[0]);
            return List.of();
        }
    }

    // /tptoggle
    private static class TpToggleCmd extends Command {
        private final TpaManager tm;
        TpToggleCmd(TpaManager tm) {
            super("tptoggle");
            setDescription("Toggle receiving TPA requests");
            setUsage("/tptoggle");
            this.tm = tm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            tm.toggle(player);
            return true;
        }
    }

    // /tpcancel
    private static class TpCancelCmd extends Command {
        private final TpaManager tm;
        TpCancelCmd(TpaManager tm) {
            super("tpcancel");
            setDescription("Cancel your outgoing teleport request");
            setUsage("/tpcancel");
            this.tm = tm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            tm.cancelOutgoing(player);
            return true;
        }
    }

    // /tpblock <player>
    private static class TpBlockCmd extends Command {
        private final TpaManager tm;
        TpBlockCmd(TpaManager tm) {
            super("tpblock");
            setDescription("Block a player from sending you TPA requests");
            setUsage("/tpblock <player>");
            this.tm = tm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            if (args.length < 1) { Msg.error(player, "Usage: /tpblock <player>"); return true; }
            tm.toggleBlock(player, args[0]);
            return true;
        }
        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (args.length == 1) return playerCompletions(args[0]);
            return List.of();
        }
    }
}
