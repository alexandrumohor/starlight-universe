package com.starlightuniverse.enchant;

import com.starlightuniverse.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class AlchemistCommand extends Command {

    private final AlchemistListener alchemistListener;

    public AlchemistCommand(AlchemistListener alchemistListener) {
        super("alchemist");
        this.alchemistListener = alchemistListener;
        setDescription("Open the Alchemist to combine enchant books");
        setUsage("/alchemist");
        setAliases(List.of("alchimist"));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.errorComponent("Players only."));
            return true;
        }
        alchemistListener.openAlchemistGui(player);
        return true;
    }
}
