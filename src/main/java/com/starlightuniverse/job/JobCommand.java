package com.starlightuniverse.job;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JobCommand extends Command {

    private final JobManager jobManager;

    public JobCommand(JobManager jobManager) {
        super("jobs");
        setDescription("View your jobs and progress");
        setUsage("/jobs");
        this.jobManager = jobManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        jobManager.openJobsGui(player);
        return true;
    }
}
