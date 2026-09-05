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
        setUsage("/chestshop create <price> <BUY|SELL>");
        setAliases(List.of("cs"));
        this.manager = manager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length < 1) {
            Msg.info(player, "Usage: /chestshop create <price> <BUY|SELL>");
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
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
            return true;
        }

        Msg.info(player, "Usage: /chestshop create <price> <BUY|SELL>");
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("create".startsWith(input)) completions.add("create");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            String input = args[2].toUpperCase();
            if ("BUY".startsWith(input)) completions.add("BUY");
            if ("SELL".startsWith(input)) completions.add("SELL");
        }
        return completions;
    }
}
