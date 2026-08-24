package com.starlightuniverse.spear;

import com.starlightuniverse.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SpearCommand extends Command {

    private static final List<Material> TIERS = List.of(
            Material.WOODEN_SPEAR,
            Material.STONE_SPEAR,
            Material.IRON_SPEAR,
            Material.GOLDEN_SPEAR,
            Material.COPPER_SPEAR,
            Material.DIAMOND_SPEAR,
            Material.NETHERITE_SPEAR
    );

    public SpearCommand() {
        super("spear");
        setDescription("Give a spear (any tier)");
        setUsage("/spear [tier] [player]");
        setPermission("su.admin.spear");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        Material tier = Material.NETHERITE_SPEAR;
        if (args.length >= 1) {
            Material parsed = parseTier(args[0]);
            if (parsed == null) {
                sender.sendMessage(Msg.errorComponent("Unknown tier. Use: wooden, stone, iron, golden, copper, diamond, netherite"));
                return true;
            }
            tier = parsed;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Msg.errorComponent("Player not found: " + args[1]));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(Msg.errorComponent("Specify a player from console."));
            return true;
        }

        ItemStack spear = new ItemStack(tier);
        target.getInventory().addItem(spear).values().forEach(leftover ->
                target.getWorld().dropItemNaturally(target.getLocation(), leftover));

        if (target != sender) {
            sender.sendMessage(Msg.errorComponent("Gave " + tier.name() + " to " + target.getName()));
        }
        Msg.success(target, "Received " + tier.name().replace('_', ' ').toLowerCase() + "!");
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> tiers = new ArrayList<>();
            for (Material m : TIERS) {
                String name = m.name().replace("_SPEAR", "").toLowerCase();
                if (name.startsWith(input)) tiers.add(name);
            }
            return tiers;
        }
        return List.of();
    }

    private Material parseTier(String s) {
        String upper = s.toUpperCase() + "_SPEAR";
        for (Material m : TIERS) {
            if (m.name().equals(upper)) return m;
        }
        return null;
    }
}
