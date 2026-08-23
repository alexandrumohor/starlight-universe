package com.starlightuniverse.admin;

import com.starlightuniverse.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class AdminCommand extends Command {

    protected final AdminManager adminManager;
    private final int requiredLevel;

    protected AdminCommand(String name, String description, String usage, AdminManager adminManager, int requiredLevel) {
        super(name);
        setDescription(description);
        setUsage(usage);
        this.adminManager = adminManager;
        this.requiredLevel = requiredLevel;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.errorComponent("Only players can use this command!"));
            return true;
        }
        if (!adminManager.hasPermission(player.getUniqueId(), requiredLevel)) {
            Msg.error(player, "No permission!");
            return true;
        }
        return onCommand(player, args);
    }

    protected abstract boolean onCommand(Player player, String[] args);

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return playerCompletions(args[0]);
        }
        return List.of();
    }

    protected List<String> playerCompletions(String prefix) {
        String lower = prefix.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(lower))
                .toList();
    }

    protected int parseDuration(String unit, String amountStr) {
        int amount;
        try { amount = Integer.parseInt(amountStr); } catch (NumberFormatException e) { return -1; }
        if (amount <= 0) return 0;
        return switch (unit.toLowerCase()) {
            case "min", "minutes" -> amount;
            case "hours", "hour", "h" -> amount * 60;
            case "days", "day", "d" -> amount * 60 * 24;
            case "weeks", "week", "w" -> amount * 60 * 24 * 7;
            case "months", "month", "m" -> amount * 60 * 24 * 30;
            default -> -1;
        };
    }

    protected String formatDuration(int minutes) {
        if (minutes <= 0) return "Permanent";
        if (minutes < 60) return minutes + " minute" + (minutes != 1 ? "s" : "");
        if (minutes < 1440) return (minutes / 60) + " hour" + (minutes / 60 != 1 ? "s" : "");
        return (minutes / 1440) + " day" + (minutes / 1440 != 1 ? "s" : "");
    }

    protected String joinArgs(String[] args, int fromIndex) {
        if (fromIndex >= args.length) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = fromIndex; i < args.length; i++) {
            if (i > fromIndex) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
