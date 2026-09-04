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

public class GiveStarsCommand extends Command {

    private static final TextColor VIOLET = TextColor.color(0xAA00AA);

    private final JavaPlugin plugin;
    private final EconomyManager economyManager;

    public GiveStarsCommand(JavaPlugin plugin, EconomyManager economyManager) {
        super("givestars");
        setDescription("Give stars to a player");
        setUsage("/givestars <player> <amount>");
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
            sender.sendMessage(Component.text("[SU] Usage: /givestars <player> <amount>", TextColor.color(0xFF5555)));
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
            economyManager.addStars(target.getUniqueId(), amount);
            sender.sendMessage(Msg.prefix()
                    .append(Component.text("Gave ", VIOLET))
                    .append(EconomyManager.starsText(amount))
                    .append(Component.text(" to " + target.getName(), VIOLET)));
            target.sendMessage(Msg.prefix()
                    .append(Component.text("You received ", VIOLET))
                    .append(EconomyManager.starsText(amount)));
        } else {
            double finalAmount = amount;
            economyManager.giveOffline(targetName, "stars", amount).thenAccept(success -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        sender.sendMessage(Msg.prefix()
                                .append(Component.text("Gave ", VIOLET))
                                .append(EconomyManager.starsText(finalAmount))
                                .append(Component.text(" to " + targetName + " (offline)", VIOLET)));
                        com.starlightuniverse.StarlightUniverse.getInstance().getPendingMessageManager()
                                .enqueue(targetName, Msg.prefix()
                                        .append(Component.text("You received ", VIOLET))
                                        .append(EconomyManager.starsText(finalAmount))
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
