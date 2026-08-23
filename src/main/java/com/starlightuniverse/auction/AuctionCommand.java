package com.starlightuniverse.auction;

import com.starlightuniverse.util.Msg;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AuctionCommand extends Command {

    private final AuctionManager auctionManager;

    public AuctionCommand(AuctionManager auctionManager) {
        super("ah");
        setDescription("Open the Auction House");
        setUsage("/ah [sell <price> | collect | history <item> | blacklist <add|remove> <material>]");
        setAliases(List.of("auctionhouse", "auction"));
    this.auctionManager = auctionManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            auctionManager.openBrowse(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "sell" -> {
                if (args.length < 2) {
                    Msg.error(player, "Usage: /ah sell <price per unit>");
                    return true;
                }
                double price;
                try {
                    price = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    Msg.error(player, "Invalid price! Use a number like 100 or 1500.");
                    return true;
                }
                auctionManager.createListing(player, price);
            }

            case "collect" -> auctionManager.openCollect(player);

            case "history" -> {
                if (args.length < 2) {
                    Msg.error(player, "Usage: /ah history <item name>");
                    return true;
                }
                StringBuilder query = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    if (!query.isEmpty()) query.append('_');
                    query.append(args[i]);
                }
                auctionManager.showHistory(player, query.toString());
            }

            case "blacklist" -> {
                if (!player.isOp()) {
                    Msg.error(player, "No permission!");
                    return true;
                }
                if (args.length < 3) {
                    Msg.error(player, "Usage: /ah blacklist <add|remove> <material>");
                    return true;
                }
                String action = args[1].toLowerCase();
                String matName = args[2].toUpperCase();
                Material mat;
                try {
                    mat = Material.valueOf(matName);
                } catch (IllegalArgumentException e) {
                    Msg.error(player, "Unknown material: " + args[2]);
                    return true;
                }
                if (action.equals("add")) {
                    auctionManager.addBlacklist(player, mat);
                } else if (action.equals("remove")) {
                    auctionManager.removeBlacklist(player, mat);
                } else {
                    Msg.error(player, "Usage: /ah blacklist <add|remove> <material>");
                }
            }

            default -> Msg.error(player, "Usage: /ah [sell <price> | collect | history <item> | blacklist <add|remove> <material>]");
        }

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> options = new ArrayList<>();
            for (String opt : List.of("sell", "collect", "history")) {
                if (opt.startsWith(input)) options.add(opt);
            }
            if (sender.isOp() && "blacklist".startsWith(input)) {
                options.add("blacklist");
            }
            return options;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("blacklist")) {
            String input = args[1].toLowerCase();
            List<String> options = new ArrayList<>();
            for (String opt : List.of("add", "remove")) {
                if (opt.startsWith(input)) options.add(opt);
            }
            return options;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("history")) {
            String input = args[1].toLowerCase();
            return Arrays.stream(Material.values())
                    .filter(Material::isItem)
                    .map(m -> m.name().toLowerCase())
                    .filter(n -> n.startsWith(input))
                    .limit(20)
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("blacklist")) {
            String input = args[2].toLowerCase();
            return Arrays.stream(Material.values())
                    .filter(Material::isItem)
                    .map(m -> m.name().toLowerCase())
                    .filter(n -> n.startsWith(input))
                    .limit(20)
                    .toList();
        }

        return List.of();
    }
}
