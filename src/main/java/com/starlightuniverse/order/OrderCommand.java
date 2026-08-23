package com.starlightuniverse.order;

import com.starlightuniverse.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class OrderCommand extends Command {

    private final OrderManager orderManager;

    public OrderCommand(OrderManager orderManager) {
        super("order");
        setDescription("Open the Item Order system");
        setUsage("/order [storage | search <query>]");
        setAliases(List.of("orders", "buyorder"));
        this.orderManager = orderManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length == 0) {
            orderManager.openMainMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "storage" -> orderManager.openStorage(player);

            case "search" -> {
                if (args.length < 2) {
                    Msg.error(player, "Usage: /order search <item name>");
                    return true;
                }
                StringBuilder query = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    if (!query.isEmpty()) query.append(' ');
                    query.append(args[i]);
                }
                orderManager.openCreateSearch(player, query.toString());
            }

            default -> Msg.error(player, "Usage: /order [storage | search <query>]");
        }

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> options = new ArrayList<>();
            for (String opt : List.of("storage", "search")) {
                if (opt.startsWith(input)) options.add(opt);
            }
            return options;
        }
        return List.of();
    }
}
