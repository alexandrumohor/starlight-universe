package com.starlightuniverse.skill;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SkillCommand extends Command {

    private final SkillManager skillManager;

    public SkillCommand(SkillManager skillManager) {
        super("skills");
        setDescription("View your skills and progress");
        setUsage("/skills");
        this.skillManager = skillManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        skillManager.openSkillsGui(player);
        return true;
    }
}
