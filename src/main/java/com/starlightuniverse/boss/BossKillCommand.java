package com.starlightuniverse.boss;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BossKillCommand extends Command {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);

    private final BossKillManager manager;
    private final AdminManager adminManager;

    public BossKillCommand(BossKillManager manager, AdminManager adminManager) {
        super("bosskill");
        setDescription("Boss Arena — fight custom bosses");
        setUsage("/bosskill <start|join|leave|info>");
        this.manager = manager;
        this.adminManager = adminManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /bosskill.");
            return true;
        }

        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (adminManager.getAdminLevel(player.getUniqueId()) < 4) {
                    Msg.error(player, "Only Owner can start a boss fight.");
                    return true;
                }
                if (args.length < 2) {
                    Msg.error(player, "Usage: /bosskill start <warden|wither|dragon|infernalgolem>");
                    return true;
                }
                BossType type = BossType.fromAlias(args[1]);
                if (type == null) {
                    Msg.error(player, "Unknown boss type. Options: warden, wither, dragon, infernalgolem");
                    return true;
                }
                manager.startBoss(type, player);
            }
            case "join" -> {
                long respawn = manager.playerRespawnRemaining(player.getUniqueId());
                if (respawn > 0) {
                    Msg.error(player, "You died recently. Wait " + respawn + "s before rejoining.");
                    return true;
                }
                manager.joinBoss(player);
            }
            case "leave" -> manager.leaveBoss(player);
            case "info" -> showInfo(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Msg.prefix().append(Component.text("Boss Arena", GOLD, TextDecoration.BOLD)));
        player.sendMessage(Msg.prefix().append(Component.text("/bosskill start <type>", CYAN))
                .append(Component.text(" — Owner: summon a boss (warden, wither, dragon, infernalgolem)", GRAY)));
        player.sendMessage(Msg.prefix().append(Component.text("/bosskill join", CYAN))
                .append(Component.text(" — join the active boss fight", GRAY)));
        player.sendMessage(Msg.prefix().append(Component.text("/bosskill leave", CYAN))
                .append(Component.text(" — leave the fight", GRAY)));
        player.sendMessage(Msg.prefix().append(Component.text("/bosskill info", CYAN))
                .append(Component.text(" — show current boss info / cooldown", GRAY)));
    }

    private void showInfo(Player player) {
        if (manager.hasActiveBoss()) {
            var boss = manager.getActive();
            player.sendMessage(Msg.prefix().append(Component.text("Active boss: ", GOLD))
                    .append(Component.text(boss.type.getDisplayName(), boss.type.getColor(), TextDecoration.BOLD)));
            player.sendMessage(Msg.prefix().append(Component.text("Fighters: ", GRAY))
                    .append(Component.text(String.valueOf(boss.participants.size()), CYAN)));
            player.sendMessage(Msg.prefix().append(Component.text("HP left: ", GRAY))
                    .append(Component.text((int) boss.entity.getHealth() + " / " + (int) boss.type.getMaxHealth(), CYAN)));
        } else {
            long cd = manager.remainingCooldownSeconds();
            if (cd > 0) {
                Msg.gray(player, "No boss active. Next boss can be summoned in " + formatDuration(cd) + ".");
            } else {
                Msg.gray(player, "No boss active. Ready to summon.");
            }
        }
    }

    private String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("start"); out.add("join"); out.add("leave"); out.add("info");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            out.add("warden"); out.add("wither"); out.add("dragon"); out.add("infernalgolem");
        }
        return out;
    }
}
