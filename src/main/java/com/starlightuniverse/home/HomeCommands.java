package com.starlightuniverse.home;

import com.starlightuniverse.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.IntStream;

public final class HomeCommands {

    private HomeCommands() {}

    public static List<Command> create(HomeManager hm) {
        return List.of(
                new SetHomeCmd(hm), new DelHomeCmd(hm), new HomeCmd(hm),
                new HomesCmd(hm), new SetHomeNameCmd(hm), new ShareHomeCmd(hm)
        );
    }

    private static List<String> numberCompletions(String prefix, int max) {
        return IntStream.rangeClosed(1, max)
                .mapToObj(String::valueOf)
                .filter(s -> s.startsWith(prefix))
                .toList();
    }

    private static List<String> playerCompletions(String prefix) {
        String lower = prefix.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(lower))
                .toList();
    }

    // ==================== /sethome <nr> ====================
    private static class SetHomeCmd extends Command {
        private final HomeManager hm;
        SetHomeCmd(HomeManager hm) {
            super("sethome");
            setDescription("Set a home at your location");
            setUsage("/sethome <number>");
            this.hm = hm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            if (args.length < 1) { Msg.error(player, "Usage: /sethome <number>"); return true; }
            int num;
            try { num = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) { Msg.error(player, "Invalid number!"); return true; }
            hm.setHome(player, num);
            return true;
        }
        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (args.length == 1 && sender instanceof Player player) {
                return numberCompletions(args[0], hm.getMaxHomes(player.getUniqueId()));
            }
            return List.of();
        }
    }

    // ==================== /delhome <nr> ====================
    private static class DelHomeCmd extends Command {
        private final HomeManager hm;
        DelHomeCmd(HomeManager hm) {
            super("delhome");
            setDescription("Delete a home");
            setUsage("/delhome <number>");
            this.hm = hm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            if (args.length < 1) { Msg.error(player, "Usage: /delhome <number>"); return true; }
            int num;
            try { num = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) { Msg.error(player, "Invalid number!"); return true; }
            hm.deleteHome(player, num);
            return true;
        }
        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (args.length == 1 && sender instanceof Player player) {
                return hm.getHomes(player.getName()).stream()
                        .map(h -> String.valueOf(h.getNumber()))
                        .filter(s -> s.startsWith(args[0]))
                        .toList();
            }
            return List.of();
        }
    }

    // ==================== /home <nr> ====================
    private static class HomeCmd extends Command {
        private final HomeManager hm;
        HomeCmd(HomeManager hm) {
            super("home");
            setDescription("Teleport to a home");
            setUsage("/home <number>");
            this.hm = hm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            if (args.length < 1) { Msg.error(player, "Usage: /home <number>"); return true; }
            int num;
            try { num = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) { Msg.error(player, "Invalid number!"); return true; }
            hm.teleportHome(player, num);
            return true;
        }
        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (args.length == 1 && sender instanceof Player player) {
                return hm.getHomes(player.getName()).stream()
                        .map(h -> String.valueOf(h.getNumber()))
                        .filter(s -> s.startsWith(args[0]))
                        .toList();
            }
            return List.of();
        }
    }

    // ==================== /homes ====================
    private static class HomesCmd extends Command {
        private final HomeManager hm;
        HomesCmd(HomeManager hm) {
            super("homes");
            setDescription("Open homes GUI");
            setUsage("/homes");
            this.hm = hm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            hm.openHomesGui(player);
            return true;
        }
    }

    // ==================== /sethomename <nr> <name> ====================
    private static class SetHomeNameCmd extends Command {
        private final HomeManager hm;
        SetHomeNameCmd(HomeManager hm) {
            super("sethomename");
            setDescription("Set a home name");
            setUsage("/sethomename <number> <name>");
            this.hm = hm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            if (args.length < 2) { Msg.error(player, "Usage: /sethomename <number> <name>"); return true; }
            int num;
            try { num = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) { Msg.error(player, "Invalid number!"); return true; }
            StringBuilder name = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (i > 1) name.append(' ');
                name.append(args[i]);
            }
            hm.setHomeName(player, num, name.toString());
            return true;
        }
        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (args.length == 1 && sender instanceof Player player) {
                return hm.getHomes(player.getName()).stream()
                        .map(h -> String.valueOf(h.getNumber()))
                        .filter(s -> s.startsWith(args[0]))
                        .toList();
            }
            return List.of();
        }
    }

    // ==================== /sharehome <nr> <player> ====================
    private static class ShareHomeCmd extends Command {
        private final HomeManager hm;
        ShareHomeCmd(HomeManager hm) {
            super("sharehome");
            setDescription("Share home coordinates with a player");
            setUsage("/sharehome <number> <player>");
            this.hm = hm;
        }
        @Override
        public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
            if (!(sender instanceof Player player)) return true;
            if (args.length < 2) { Msg.error(player, "Usage: /sharehome <number> <player>"); return true; }
            int num;
            try { num = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) { Msg.error(player, "Invalid number!"); return true; }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { Msg.error(player, "Player not found!"); return true; }
            if (target.equals(player)) { Msg.error(player, "You can't share a home with yourself!"); return true; }
            hm.shareHome(player, num, target);
            return true;
        }
        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
            if (args.length == 1 && sender instanceof Player player) {
                return hm.getHomes(player.getName()).stream()
                        .map(h -> String.valueOf(h.getNumber()))
                        .filter(s -> s.startsWith(args[0]))
                        .toList();
            }
            if (args.length == 2) return playerCompletions(args[1]);
            return List.of();
        }
    }
}
