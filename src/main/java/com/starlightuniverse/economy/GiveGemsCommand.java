package com.starlightuniverse.economy;

import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class GiveGemsCommand extends Command {

    private static final TextColor CYAN = TextColor.color(0x55FFFF);

    private final JavaPlugin plugin;
    private final EconomyManager economyManager;

    public GiveGemsCommand(JavaPlugin plugin, EconomyManager economyManager) {
        super("givegems");
        setDescription("Give gems to a player");
        setUsage("/givegems <player> <amount>");
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            if (sender instanceof Player p) Msg.error(p, "No permission!");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(Component.text("[SU] Usage: /givegems <player> <amount>", TextColor.color(0xFF5555)));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("[SU] Invalid amount!", TextColor.color(0xFF5555)));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(Component.text("[SU] Amount must be positive!", TextColor.color(0xFF5555)));
            return true;
        }

        amount = Math.floor(amount);
        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target != null && target.isOnline()) {
            economyManager.addGems(target.getUniqueId(), amount);
            sender.sendMessage(Msg.prefix()
                    .append(Component.text("Gave ", CYAN))
                    .append(EconomyManager.gemsText(amount))
                    .append(Component.text(" to " + target.getName(), CYAN)));
            target.sendMessage(Msg.prefix()
                    .append(Component.text("You received ", CYAN))
                    .append(EconomyManager.gemsText(amount)));
        } else {
            double finalAmount = amount;
            economyManager.giveOffline(targetName, "gems", amount).thenAccept(success -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        sender.sendMessage(Msg.prefix()
                                .append(Component.text("Gave ", CYAN))
                                .append(EconomyManager.gemsText(finalAmount))
                                .append(Component.text(" to " + targetName + " (offline)", CYAN)));
                        com.starlightuniverse.StarlightUniverse.getInstance().getPendingMessageManager()
                                .enqueue(targetName, Msg.prefix()
                                        .append(Component.text("You received ", CYAN))
                                        .append(EconomyManager.gemsText(finalAmount))
                                        .append(Component.text(" (while you were offline)", TextColor.color(0xAAAAAA))));
                    } else {
                        sender.sendMessage(Component.text("[SU] Player not found!", TextColor.color(0xFF5555)));
                    }
                });
            });
        }

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
