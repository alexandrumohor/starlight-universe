package com.starlightuniverse.chestshop;

import com.starlightuniverse.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ChestShopCommand extends Command {

    private final ChestShopManager manager;

    public ChestShopCommand(ChestShopManager manager) {
        super("chestshop");
        setDescription("Create and manage chest shops");
        setUsage("/chestshop <create|menu|bank|finditem>");
        setAliases(List.of("cs"));
        this.manager = manager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length < 1) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 3) {
                    Msg.error(player, "Usage: /chestshop create <price> <BUY|SELL>");
                    return true;
                }

                double price;
                try {
                    price = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    Msg.error(player, "Invalid price!");
                    return true;
                }

                ChestShop.ShopType type;
                try {
                    type = ChestShop.ShopType.valueOf(args[2].toUpperCase());
                } catch (IllegalArgumentException e) {
                    Msg.error(player, "Invalid shop type! Use BUY or SELL.");
                    return true;
                }

                manager.startCreation(player, price, type);
            }
            case "menu" -> manager.startMenu(player);
            case "bank" -> manager.openBankGui(player, 0);
            case "finditem" -> {
                if (args.length < 2) {
                    Msg.error(player, "Usage: /chestshop finditem <item_name>");
                    return true;
                }
                StringBuilder query = new StringBuilder(args[1]);
                for (int i = 2; i < args.length; i++) {
                    query.append(' ').append(args[i]);
                }
                manager.openFindItemGui(player, query.toString(), 0);
            }
            default -> sendUsage(player);
        }
        return true;
    }

    private void sendUsage(Player player) {
        Msg.info(player, "Usage: /chestshop <create|menu|bank|finditem>");
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String sub : List.of("create", "menu", "bank", "finditem")) {
                if (sub.startsWith(input)) completions.add(sub);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            String input = args[2].toUpperCase();
            if ("BUY".startsWith(input)) completions.add("BUY");
            if ("SELL".startsWith(input)) completions.add("SELL");
        }
        return completions;
    }
}
