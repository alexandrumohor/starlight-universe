package com.starlightuniverse.admin;

import com.starlightuniverse.StarlightUniverse;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class StaffToolCommands {

    private StaffToolCommands() {}

    public static List<Command> create(AdminManager am, JavaPlugin plugin) {
        return List.of(new StaffChatCmd(am), new SpyCmd(am), new VanishCmd(am, plugin),
                new ClearChatCmd(am), new SlowModeCmd(am),
                new RemoveBlockCmd(am), new NotesCmd(am, plugin));
    }

    private static class StaffChatCmd extends AdminCommand {
        StaffChatCmd(AdminManager am) {
            super("staffchat", "Toggle staff chat mode", "/staffchat", am, AdminRank.MODERATOR.getLevel());
            setAliases(List.of("sc"));
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            boolean enabled = adminManager.toggleStaffChat(player.getUniqueId());
            if (enabled) Msg.success(player, "Staff chat enabled. Your messages go only to staff.");
            else Msg.success(player, "Staff chat disabled. Typing in normal chat.");
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            return List.of();
        }
    }

    private static class SpyCmd extends AdminCommand {
        SpyCmd(AdminManager am) {
            super("spy", "Toggle command spy mode", "/spy", am, AdminRank.MODERATOR.getLevel());
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            boolean enabled = adminManager.toggleSpy(player.getUniqueId());
            if (enabled) Msg.success(player, "Command spy enabled.");
            else Msg.success(player, "Command spy disabled.");
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            return List.of();
        }
    }

    private static class VanishCmd extends AdminCommand {
        private final JavaPlugin plugin;
        VanishCmd(AdminManager am, JavaPlugin plugin) {
            super("vanish", "Toggle vanish mode", "/vanish", am, AdminRank.MODERATOR.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            boolean vanished = adminManager.toggleVanish(player.getUniqueId());
            if (vanished) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (adminManager.getAdminLevel(online.getUniqueId()) == 0) {
                        online.hidePlayer(plugin, player);
                    }
                }
                Msg.success(player, "You are now vanished.");
            } else {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.showPlayer(plugin, player);
                }
                Msg.success(player, "You are now visible.");
            }
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            return List.of();
        }
    }

    private static class ClearChatCmd extends AdminCommand {
        ClearChatCmd(AdminManager am) {
            super("clearchat", "Clear the chat for all non-staff", "/clearchat", am, AdminRank.MODERATOR.getLevel());
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            Component empty = Component.empty();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (adminManager.getAdminLevel(online.getUniqueId()) == 0) {
                    for (int i = 0; i < 100; i++) online.sendMessage(empty);
                }
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(Msg.prefix().append(Component.text("Chat cleared by " + player.getName() + ".", TextColor.color(0xAAAAAA))));
            }
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            return List.of();
        }
    }

    private static class SlowModeCmd extends AdminCommand {
        SlowModeCmd(AdminManager am) {
            super("slowmode", "Set chat slow mode", "/slowmode <seconds>", am, AdminRank.MODERATOR.getLevel());
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /slowmode <seconds> (0 to disable)"); return true; }
            int seconds;
            try { seconds = Integer.parseInt(args[0]); } catch (NumberFormatException e) { Msg.error(player, "Invalid number!"); return true; }
            if (seconds < 0 || seconds > 300) { Msg.error(player, "Seconds must be 0-300!"); return true; }
            adminManager.setSlowMode(seconds);
            if (seconds == 0) {
                for (Player online : Bukkit.getOnlinePlayers())
                    online.sendMessage(Msg.prefix().append(Component.text("Slow mode disabled.", TextColor.color(0x55FF55))));
            } else {
                for (Player online : Bukkit.getOnlinePlayers())
                    online.sendMessage(Msg.prefix().append(Component.text("Slow mode: " + seconds + "s between messages.", TextColor.color(0xFFFF55))));
            }
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            if (args.length == 1) return List.of("0", "3", "5", "10", "30");
            return List.of();
        }
    }

    private static class RemoveBlockCmd extends AdminCommand {
        RemoveBlockCmd(AdminManager am) {
            super("removeblock", "Remove the block you're looking at", "/removeblock", am, AdminRank.OWNER.getLevel());
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            Block block = player.getTargetBlockExact(5);
            if (block == null || block.getType().isAir()) { Msg.error(player, "Look at a block first!"); return true; }
            String blockName = block.getType().name();
            block.setType(Material.AIR);
            Msg.success(player, "Removed " + blockName + ".");
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            return List.of();
        }
    }

    private static class NotesCmd extends AdminCommand {
        private final JavaPlugin plugin;
        NotesCmd(AdminManager am, JavaPlugin plugin) {
            super("notes", "View or add staff notes on a player", "/notes <player> [add <text>]", am, AdminRank.HELPER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length < 1) { Msg.error(player, "Usage: /notes <player> [add <text>]"); return true; }
            String target = args[0];
            if (args.length >= 3 && args[1].equalsIgnoreCase("add")) {
                String note = joinArgs(args, 2);
                adminManager.addNote(target, player.getName(), note).thenRun(() ->
                    Bukkit.getScheduler().runTask(plugin, () -> Msg.success(player, "Note added to " + target + ".")));
            } else {
                adminManager.getNotes(target).thenAccept(notes ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (notes.isEmpty()) { Msg.info(player, "No notes for " + target + "."); return; }
                        player.sendMessage(Component.text("=== Notes: " + target + " ===", TextColor.color(0xFFD700)));
                        for (AdminManager.NoteInfo n : notes) {
                            player.sendMessage(Component.text("[" + n.noteDate() + "] ", TextColor.color(0xAAAAAA))
                                    .append(Component.text(n.noteBy() + ": ", TextColor.color(0xFFFFFF)))
                                    .append(Component.text(n.note(), TextColor.color(0x55FFFF))));
                        }
                    }));
            }
            return true;
        }
        @Override public @NotNull List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
            if (args.length == 1) return playerCompletions(args[0]);
            if (args.length == 2) return List.of("add").stream().filter(c -> c.startsWith(args[1].toLowerCase())).toList();
            return List.of();
        }
    }
}
