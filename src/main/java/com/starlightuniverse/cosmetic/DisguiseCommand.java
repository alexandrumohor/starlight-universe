package com.starlightuniverse.cosmetic;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DisguiseCommand extends Command {

    private final DisguiseManager disguiseManager;

    public DisguiseCommand(DisguiseManager disguiseManager) {
        super("disguise");
        setDescription("Open the disguise menu");
        setUsage("/disguise");
        setPermission("su.disguise");
        this.disguiseManager = disguiseManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        disguiseManager.handleDisguiseCommand(player);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
