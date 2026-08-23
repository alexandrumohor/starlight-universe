package com.starlightuniverse.shop;

import com.starlightuniverse.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ShopCommand extends Command {

    private final ShopManager shopManager;

    public ShopCommand(ShopManager shopManager) {
        super("shop");
        setDescription("Open the server shop");
        setUsage("/shop [search <query>]");
        this.shopManager = shopManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length >= 2 && args[0].equalsIgnoreCase("search")) {
            StringBuilder query = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (!query.isEmpty()) query.append(' ');
                query.append(args[i]);
            }
            shopManager.openSearchResults(player, query.toString());
            return true;
        }

        if (args.length == 0) {
            shopManager.openMainMenu(player);
            return true;
        }

        Msg.error(player, "Usage: /shop or /shop search <query>");
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if ("search".startsWith(input)) return List.of("search");
        }
        return List.of();
    }
}
