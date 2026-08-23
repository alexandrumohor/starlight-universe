package com.starlightuniverse.economy;

import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class PayCommand extends Command {

    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor GOLD = TextColor.color(0xFFD700);

    private final EconomyManager economyManager;

    public PayCommand(EconomyManager economyManager) {
        super("pay");
        setDescription("Send money to another player");
        setUsage("/pay <player> <money/gems/stars> <amount>");
        this.economyManager = economyManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length != 3) {
            Msg.error(player, "Usage: /pay <player> <money/gems/stars> <amount>");
            return true;
        }

        String targetName = args[0];
        String currencyType = args[1].toLowerCase();
        double amount;

        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            Msg.error(player, "Invalid amount!");
            return true;
        }

        if (amount <= 0) {
            Msg.error(player, "Amount must be positive!");
            return true;
        }

        amount = Math.floor(amount);

        if (!currencyType.equals("money")) {
            if (currencyType.equals("gems") || currencyType.equals("stars")) {
                Msg.error(player, "Gems and Stars are non-tradeable!");
            } else {
                Msg.error(player, "Invalid currency! Use: money, gems, stars");
            }
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            Msg.error(player, "Player not found or offline!");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            Msg.error(player, "You cannot pay yourself!");
            return true;
        }

        double tax = Math.floor(amount * EconomyManager.PAY_TAX_RATE);
        double received = amount - tax;

        if (!economyManager.hasMoney(player.getUniqueId(), amount)) {
            Msg.error(player, "Insufficient funds! You need $" + EconomyManager.format(amount));
            return true;
        }

        economyManager.removeMoney(player.getUniqueId(), amount);
        economyManager.addMoney(target.getUniqueId(), received);

        player.sendMessage(Msg.prefix()
                .append(Component.text("Sent ", GREEN))
                .append(Component.text("$" + EconomyManager.format(received), GOLD))
                .append(Component.text(" to " + target.getName(), GREEN))
                .append(Component.text(" (tax: $" + EconomyManager.format(tax) + ")", GREEN)));

        target.sendMessage(Msg.prefix()
                .append(Component.text("Received ", GREEN))
                .append(Component.text("$" + EconomyManager.format(received), GOLD))
                .append(Component.text(" from " + player.getName(), GREEN)));

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
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return List.of("money", "gems", "stars").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
