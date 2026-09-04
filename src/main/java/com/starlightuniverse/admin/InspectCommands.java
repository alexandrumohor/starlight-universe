package com.starlightuniverse.admin;

import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class InspectCommands {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);

    private InspectCommands() {}

    public static List<Command> create(AdminManager am, JavaPlugin plugin) {
        return List.of(new InvseeCmd(am), new EnderseeCmd(am), new CheckCmd(am, plugin), new HistoryCmd(am, plugin));
    }

    private static class InvseeCmd extends AdminCommand {
        InvseeCmd(AdminManager am) {
            super("invsee", "View a player's inventory", "/invsee <player>", am, AdminRank.MODERATOR.getLevel());
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /invsee <player>"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { Msg.error(player, "Player not online!"); return true; }
            player.openInventory(target.getInventory());
            Msg.success(player, "Viewing " + target.getName() + "'s inventory.");
            return true;
        }
        @Override
        public boolean execute(@org.jetbrains.annotations.NotNull org.bukkit.command.CommandSender sender,
                               @org.jetbrains.annotations.NotNull String label,
                               @org.jetbrains.annotations.NotNull String[] args) {
            if (sender instanceof Player p && adminManager.getPremiumLevel(p.getUniqueId()) >= 5)
                return onCommand(p, args);
            return super.execute(sender, label, args);
        }
    }

    private static class EnderseeCmd extends AdminCommand {
        EnderseeCmd(AdminManager am) {
            super("endersee", "View a player's ender chest", "/endersee <player>", am, AdminRank.MODERATOR.getLevel());
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /endersee <player>"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { Msg.error(player, "Player not online!"); return true; }
            player.openInventory(target.getEnderChest());
            Msg.success(player, "Viewing " + target.getName() + "'s ender chest.");
            return true;
        }
    }

    private static class CheckCmd extends AdminCommand {
        private final JavaPlugin plugin;
        CheckCmd(AdminManager am, JavaPlugin plugin) {
            super("check", "View player information", "/check <player>", am, AdminRank.MODERATOR.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /check <player>"); return true; }
            adminManager.getPlayerInfo(args[0]).thenAccept(info ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (info == null) { Msg.error(player, "Player not found!"); return; }
                    player.sendMessage(Component.text("=== Player Check: " + info.username() + " ===", GOLD));
                    AdminRank rank = AdminRank.fromLevel(info.adminLevel());
                    player.sendMessage(Component.text("Admin: ", GRAY).append(Component.text(
                            rank == AdminRank.NONE ? "None" : rank.getDisplayName(), rank.getColor())));
                    player.sendMessage(Component.text("Premium: ", GRAY).append(Component.text(
                            AdminRank.premiumName(info.premiumLevel()), CYAN)));
                    player.sendMessage(Component.text("Money: " + EconomyManager.MONEY_ICON + " $" + EconomyManager.format(info.money()) +
                            "  Gems: " + EconomyManager.format(info.gems()) +
                            "  Stars: " + EconomyManager.format(info.stars()), GREEN));
                    player.sendMessage(Component.text("Level: " + info.level() +
                            "  Playtime: " + (info.playtime() / 3600) + "h", WHITE));
                    player.sendMessage(Component.text("PvP Kills: " + info.pvpKills() +
                            "  PvM Kills: " + info.pvmKills() + "  Deaths: " + info.deaths(), WHITE));
                    player.sendMessage(Component.text("Active Bans: " + info.activeBans() +
                            "  Mutes: " + info.activeMutes() + "  Warns: " + info.activeWarns(),
                            info.activeBans() > 0 || info.activeMutes() > 0 ? RED : GREEN));
                    if (info.lastActive() != null)
                        player.sendMessage(Component.text("Last Active: " + info.lastActive(), GRAY));
                    if (info.lastIp() != null)
                        player.sendMessage(Component.text("Last IP: " + info.lastIp(), GRAY));
                }));
            return true;
        }
    }

    private static class HistoryCmd extends AdminCommand {
        private final JavaPlugin plugin;
        HistoryCmd(AdminManager am, JavaPlugin plugin) {
            super("history", "View player punishment history", "/history <player>", am, AdminRank.HELPER.getLevel());
            this.plugin = plugin;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /history <player>"); return true; }
            adminManager.getHistory(args[0]).thenAccept(history ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (history.isEmpty()) { Msg.info(player, "No punishment history for " + args[0] + "."); return; }
                    player.sendMessage(Component.text("=== History: " + args[0] + " ===", GOLD));
                    for (AdminManager.HistoryEntry e : history) {
                        TextColor typeColor = switch (e.type()) {
                            case "BAN" -> RED;
                            case "MUTE" -> TextColor.color(0xFFAA00);
                            default -> TextColor.color(0xFFFF55);
                        };
                        String status = e.active() ? " [ACTIVE]" : "";
                        player.sendMessage(Component.text("[" + e.type() + "]" + status, typeColor)
                                .append(Component.text(" by " + e.by() + ": " + e.reason() + " (" + e.date() + ")", GRAY)));
                    }
                }));
            return true;
        }
    }
}
