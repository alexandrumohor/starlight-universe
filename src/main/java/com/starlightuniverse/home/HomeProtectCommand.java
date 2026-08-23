package com.starlightuniverse.home;

import com.starlightuniverse.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HomeProtectCommand extends Command {

    private final HomeManager homeManager;

    public HomeProtectCommand(HomeManager homeManager) {
        super("homeprotect");
        setDescription("Manage your home protection");
        setUsage("/homeprotect");
    this.homeManager = homeManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.errorComponent("Only players can use this command!"));
            return true;
        }
        homeManager.openProtectGui(player);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
