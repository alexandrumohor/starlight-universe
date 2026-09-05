package com.starlightuniverse.cosmetic;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TrailCommand extends Command {

    private final TrailManager trailManager;

    public TrailCommand(TrailManager trailManager) {
        super("trail");
        setDescription("Open the trail menu");
        setUsage("/trail");
        setPermission("su.trail");
        this.trailManager = trailManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        trailManager.handleTrailCommand(player);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
