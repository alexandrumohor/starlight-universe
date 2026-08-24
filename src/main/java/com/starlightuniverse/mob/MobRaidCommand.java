package com.starlightuniverse.mob;

import com.starlightuniverse.StarlightUniverse;
import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MobRaidCommand extends Command {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);

    private final MobRaidManager manager;
    private final AdminManager adminManager;

    public MobRaidCommand(MobRaidManager manager, AdminManager adminManager) {
        super("mobraid");
        setDescription("Mob Invasion — survive endless waves of enemies");
        setUsage("/mobraid <start|stop|join|leave|top>");
        this.manager = manager;
        this.adminManager = adminManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /mobraid.");
            return true;
        }
        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (adminManager.getAdminLevel(player.getUniqueId()) < 4) {
                    Msg.error(player, "Only Owner can start a mob raid.");
                    return true;
                }
                manager.startRaid(player);
            }
            case "stop" -> {
                if (adminManager.getAdminLevel(player.getUniqueId()) < 4) {
                    Msg.error(player, "Only Owner can stop a mob raid.");
                    return true;
                }
                manager.stopRaid(player);
            }
            case "join" -> manager.joinRaid(player);
            case "leave" -> manager.leaveRaid(player);
            case "top" -> showTop(player);
            case "info" -> showInfo(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Msg.prefix().append(Component.text("Mob Invasion Commands", GOLD, TextDecoration.BOLD)));
        player.sendMessage(Msg.prefix().append(Component.text("/mobraid start", CYAN))
                .append(Component.text(" — Owner: start a raid", GRAY)));
        player.sendMessage(Msg.prefix().append(Component.text("/mobraid stop", CYAN))
                .append(Component.text(" — Owner: stop the raid", GRAY)));
        player.sendMessage(Msg.prefix().append(Component.text("/mobraid join", CYAN))
                .append(Component.text(" — join the active raid (3 lives)", GRAY)));
        player.sendMessage(Msg.prefix().append(Component.text("/mobraid leave", CYAN))
                .append(Component.text(" — leave the raid", GRAY)));
        player.sendMessage(Msg.prefix().append(Component.text("/mobraid top", CYAN))
                .append(Component.text(" — leaderboard of best mob killers", GRAY)));
        player.sendMessage(Msg.prefix().append(Component.text("/mobraid info", CYAN))
                .append(Component.text(" — current raid state", GRAY)));
    }

    private void showInfo(Player player) {
        if (!manager.hasActiveRaid()) {
            Msg.gray(player, "No mob raid active.");
            return;
        }
        var raid = manager.getActive();
        player.sendMessage(Msg.prefix().append(Component.text("Active Raid — Wave " + raid.currentWave, GOLD, TextDecoration.BOLD)));
        player.sendMessage(Msg.prefix().append(Component.text("Players in raid: ", GRAY))
                .append(Component.text(String.valueOf(raid.livesLeft.size()), CYAN)));
        player.sendMessage(Msg.prefix().append(Component.text("State: ", GRAY))
                .append(Component.text(raid.state.name(), YELLOW)));
    }

    private void showTop(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(StarlightUniverse.getInstance(), () -> {
            List<MobRaidManager.TopEntry> top = manager.getTop();
            Bukkit.getScheduler().runTask(StarlightUniverse.getInstance(), () -> {
                if (!player.isOnline()) return;
                player.sendMessage(Msg.prefix().append(Component.text("Top Mob Raiders", GOLD, TextDecoration.BOLD)));
                if (top.isEmpty()) {
                    player.sendMessage(Msg.prefix().append(Component.text("Nobody has raided yet.", GRAY)));
                    return;
                }
                int rank = 1;
                for (var e : top) {
                    Component line = Component.text("#" + rank + " ", GOLD)
                            .append(Component.text(e.username(), YELLOW))
                            .append(Component.text(" — ", GRAY))
                            .append(Component.text(e.kills() + " kills", GREEN))
                            .append(Component.text("  (best wave " + e.bestWave() + ")", CYAN));
                    player.sendMessage(Msg.prefix().append(line));
                    rank++;
                }
            });
        });
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("start"); out.add("stop"); out.add("join"); out.add("leave"); out.add("top"); out.add("info");
        }
        return out;
    }
}
