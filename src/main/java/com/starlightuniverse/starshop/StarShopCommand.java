package com.starlightuniverse.starshop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class StarShopCommand extends Command {

    private final StarShopManager manager;

    public StarShopCommand(StarShopManager manager) {
        super("starshop");
        setDescription("Open the Star Shop");
        setUsage("/starshop");
        this.manager = manager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        manager.openMainMenu(player);
        return true;
    }
}
