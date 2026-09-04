package com.starlightuniverse.voucher;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class VoteFlyCommand extends Command {

    private final VoucherManager manager;

    public VoteFlyCommand(VoucherManager manager) {
        super("votefly");
        setDescription("Toggle voucher flight");
        setUsage("/votefly");
        this.manager = manager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        manager.toggleFly(player);
        return true;
    }
}
