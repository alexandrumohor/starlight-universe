package com.starlightuniverse.economy;

import com.starlightuniverse.StarlightUniverse;
import com.starlightuniverse.auth.AuthManager;
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
import java.util.UUID;
import java.util.stream.Collectors;

public class PayCommand extends Command {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor VIOLET = TextColor.color(0xAA00AA);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);

    private enum Currency {
        MONEY("money", "$", EconomyManager.MONEY_ICON, EconomyManager.MONEY_COLOR, "money"),
        GEMS("gems", "◆", EconomyManager.GEMS_ICON, EconomyManager.GEMS_COLOR, "gems"),
        STARS("stars", "★", EconomyManager.STARS_ICON, EconomyManager.STARS_COLOR, "stars");

        final String key;
        final String symbol;
        final String icon;
        final TextColor color;
        final String column;

        Currency(String key, String symbol, String icon, TextColor color, String column) {
            this.key = key;
            this.symbol = symbol;
            this.icon = icon;
            this.color = color;
            this.column = column;
        }

        static Currency byKey(String s) {
            for (Currency c : values()) if (c.key.equalsIgnoreCase(s)) return c;
            return null;
        }
    }

    private final EconomyManager economyManager;
    private final JavaPlugin plugin;

    public PayCommand(EconomyManager economyManager) {
        super("pay");
        setDescription("Send currency to another player");
        setUsage("/pay <player> <money/gems/stars> <amount>");
        this.economyManager = economyManager;
        this.plugin = StarlightUniverse.getInstance();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length != 3) {
            Msg.error(player, "Usage: /pay <player> <money/gems/stars> <amount>");
            return true;
        }

        String targetName = args[0];
        Currency currency = Currency.byKey(args[1]);
        if (currency == null) {
            Msg.error(player, "Invalid currency! Use: money, gems, stars");
            return true;
        }

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

        UUID from = player.getUniqueId();

        // Balance check happens up-front so the sender is not charged if they can't afford.
        double balance = switch (currency) {
            case MONEY -> economyManager.getMoney(from);
            case GEMS -> economyManager.getGems(from);
            case STARS -> economyManager.getStars(from);
        };
        if (balance < amount) {
            double missing = Math.floor(amount - balance);
            player.sendMessage(Msg.prefix()
                    .append(Component.text("Insufficient funds! You need ", TextColor.color(0xFF5555)))
                    .append(currencyText(currency, missing))
                    .append(Component.text(" more (have ", TextColor.color(0xFF5555)))
                    .append(currencyText(currency, balance))
                    .append(Component.text(" / ", TextColor.color(0xFF5555)))
                    .append(currencyText(currency, amount))
                    .append(Component.text(").", TextColor.color(0xFF5555))));
            return true;
        }

        if (targetName.equalsIgnoreCase(player.getName())) {
            Msg.error(player, "You cannot pay yourself!");
            return true;
        }

        Player online = Bukkit.getPlayerExact(targetName);
        double tax = Math.floor(amount * EconomyManager.PAY_TAX_RATE);
        double received = amount - tax;

        if (online != null && online.isOnline()) {
            // Online path — deduct + credit right now.
            if (!removeFrom(from, currency, amount)) {
                Msg.error(player, "Insufficient funds!");
                return true;
            }
            addTo(online.getUniqueId(), currency, received);
            sendReceipts(player, online.getName(), currency, received, tax, false);
            Component recv = receivedComponent(player.getName(), currency, received);
            online.sendMessage(recv);
            return true;
        }

        // Offline path — verify the username exists in DB before touching the sender's
        // balance so a typo can never send money into the void.
        final double finalAmount = amount;
        final double finalReceived = received;
        final double finalTax = tax;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthManager auth = StarlightUniverse.getInstance().getAuthManager();
            boolean exists = auth != null && auth.isRegistered(targetName);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (!exists) {
                    Msg.error(player, "Player '" + targetName + "' does not exist!");
                    return;
                }
                if (!removeFrom(from, currency, finalAmount)) {
                    Msg.error(player, "Insufficient funds!");
                    return;
                }
                economyManager.giveOffline(targetName, currency.column, finalReceived)
                        .thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!Boolean.TRUE.equals(success)) {
                                // Roll back: refund the sender because credit failed.
                                addTo(from, currency, finalAmount);
                                if (player.isOnline()) Msg.error(player, "Payment failed — refunded.");
                                return;
                            }
                            sendReceipts(player, targetName, currency, finalReceived, finalTax, true);
                            Component recv = receivedComponent(player.getName(), currency, finalReceived)
                                    .append(Component.text(" (while you were offline)", GRAY));
                            StarlightUniverse.getInstance().getPendingMessageManager()
                                    .enqueue(targetName, recv);
                        }));
            });
        });

        return true;
    }

    private boolean removeFrom(UUID uuid, Currency currency, double amount) {
        return switch (currency) {
            case MONEY -> economyManager.removeMoney(uuid, amount);
            case GEMS -> economyManager.removeGems(uuid, amount);
            case STARS -> economyManager.removeStars(uuid, amount);
        };
    }

    private void addTo(UUID uuid, Currency currency, double amount) {
        switch (currency) {
            case MONEY -> economyManager.addMoney(uuid, amount);
            case GEMS -> economyManager.addGems(uuid, amount);
            case STARS -> economyManager.addStars(uuid, amount);
        }
    }

    private void sendReceipts(Player sender, String targetName, Currency currency,
                              double received, double tax, boolean offline) {
        Component line = Msg.prefix()
                .append(Component.text("Sent ", GREEN))
                .append(currencyText(currency, received))
                .append(Component.text(" to " + targetName + (offline ? " (offline)" : ""), GREEN))
                .append(Component.text(" (tax: ", GRAY))
                .append(currencyText(currency, tax))
                .append(Component.text(")", GRAY));
        sender.sendMessage(line);
    }

    private Component receivedComponent(String senderName, Currency currency, double received) {
        return Msg.prefix()
                .append(Component.text("Received ", GREEN))
                .append(currencyText(currency, received))
                .append(Component.text(" from " + senderName, GREEN));
    }

    private Component currencyText(Currency currency, double amount) {
        return switch (currency) {
            case MONEY -> EconomyManager.moneyText(amount);
            case GEMS -> EconomyManager.gemsText(amount);
            case STARS -> EconomyManager.starsText(amount);
        };
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
