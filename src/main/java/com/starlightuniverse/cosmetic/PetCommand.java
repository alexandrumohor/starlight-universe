package com.starlightuniverse.cosmetic;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PetCommand extends Command {

    private final PetManager petManager;

    public PetCommand(PetManager petManager) {
        super("pet");
        setDescription("Open the pet menu");
        setUsage("/pet");
        setPermission("su.pet");
        this.petManager = petManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        petManager.handlePetCommand(player);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
