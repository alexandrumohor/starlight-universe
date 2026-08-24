package com.starlightuniverse.crate;

import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CrateCommands {

    private static final int ADMIN_REQUIRED = 4;

    private CrateCommands() {}

    public static List<Command> create(CrateManager crateManager) {
        List<Command> commands = new ArrayList<>();

        commands.add(new Command("spawncrate") {
            { setDescription("Spawn a crate at the block you're looking at"); }

            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                if (!(sender instanceof Player player)) return false;
                UUID uuid = player.getUniqueId();
                if (!crateManager.getAdminManager().hasPermission(uuid, ADMIN_REQUIRED)) {
                    Msg.error(player, "No permission.");
                    return true;
                }

                if (args.length < 1) {
                    Msg.error(player, "Usage: /spawncrate <star|cosmic|galaxy|seasonal>");
                    return true;
                }

                CrateType type = CrateType.fromName(args[0]);
                if (type == null) {
                    Msg.error(player, "Invalid crate type! Use: star, cosmic, galaxy, seasonal");
                    return true;
                }

                Block target = player.getTargetBlockExact(5);
                if (target == null) {
                    Msg.error(player, "Look at a block to place the crate!");
                    return true;
                }

                Location loc = target.getLocation();
                if (crateManager.isCrateLocation(loc)) {
                    Msg.error(player, "A crate already exists at this location!");
                    return true;
                }

                crateManager.spawnCrate(loc, type);
                Msg.success(player, type.getDisplayName() + " spawned!");
                return true;
            }
        });

        commands.add(new Command("removecrate") {
            { setDescription("Remove the crate you're looking at"); }

            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                if (!(sender instanceof Player player)) return false;
                UUID uuid = player.getUniqueId();
                if (!crateManager.getAdminManager().hasPermission(uuid, ADMIN_REQUIRED)) {
                    Msg.error(player, "No permission.");
                    return true;
                }

                Block target = player.getTargetBlockExact(5);
                if (target == null) {
                    Msg.error(player, "Look at a crate to remove it!");
                    return true;
                }

                Location loc = target.getLocation();
                if (!crateManager.isCrateLocation(loc)) {
                    Msg.error(player, "That's not a crate!");
                    return true;
                }

                CrateType type = crateManager.getCrateType(loc);
                crateManager.removeCrate(loc);
                Msg.success(player, type.getDisplayName() + " removed!");
                return true;
            }
        });

        commands.add(new Command("givekey") {
            { setDescription("Give crate keys to a player"); }

            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                if (sender instanceof Player player) {
                    if (!crateManager.getAdminManager().hasPermission(player.getUniqueId(), ADMIN_REQUIRED)) {
                        Msg.error(player, "No permission.");
                        return true;
                    }
                }

                if (args.length < 2) {
                    if (sender instanceof Player p) Msg.error(p, "Usage: /givekey <player> <star|cosmic|galaxy|seasonal> [amount]");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    if (sender instanceof Player p) Msg.error(p, "Player not found!");
                    return true;
                }

                CrateType type = CrateType.fromName(args[1]);
                if (type == null) {
                    if (sender instanceof Player p) Msg.error(p, "Invalid crate type! Use: star, cosmic, galaxy, seasonal");
                    return true;
                }

                int amount = 1;
                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                        if (amount < 1 || amount > 64) {
                            if (sender instanceof Player p) Msg.error(p, "Amount must be 1-64.");
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        if (sender instanceof Player p) Msg.error(p, "Invalid amount.");
                        return true;
                    }
                }

                ItemStack key = crateManager.createKey(type, amount);
                var overflow = target.getInventory().addItem(key);
                for (ItemStack leftover : overflow.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                }

                if (sender instanceof Player p) {
                    Msg.success(p, "Gave " + amount + "x " + type.getDisplayName() + " Key to " + target.getName() + "!");
                }
                Msg.success(target, "You received " + amount + "x " + type.getDisplayName() + " Key!");
                return true;
            }
        });

        commands.add(new Command("buykey") {
            { setDescription("Buy a crate key"); }

            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                if (!(sender instanceof Player player)) return false;

                if (args.length < 1) {
                    Msg.error(player, "Usage: /buykey <cosmic|galaxy> [amount]");
                    Msg.gray(player, "Cosmic Key: " + EconomyManager.GEMS_ICON + "100 Gems each");
                    Msg.gray(player, "Galaxy Key: " + EconomyManager.STARS_ICON + "25 Stars each");
                    return true;
                }

                CrateType type = CrateType.fromName(args[0]);
                if (type == null || !type.isBuyable()) {
                    Msg.error(player, "You can only buy: cosmic (" + EconomyManager.GEMS_ICON + "100), galaxy (" + EconomyManager.STARS_ICON + "25)");
                    return true;
                }

                int amount = 1;
                if (args.length >= 2) {
                    try {
                        amount = Integer.parseInt(args[1]);
                        if (amount < 1 || amount > 64) {
                            Msg.error(player, "Amount must be 1-64.");
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        Msg.error(player, "Invalid amount.");
                        return true;
                    }
                }

                UUID uuid = player.getUniqueId();
                EconomyManager economy = crateManager.getEconomy();

                if (type.getGemsCost() > 0) {
                    double totalCost = (double) type.getGemsCost() * amount;
                    if (!economy.hasGems(uuid, totalCost)) {
                        Msg.error(player, "You need " + EconomyManager.GEMS_ICON + EconomyManager.format(totalCost) + " Gems!");
                        return true;
                    }
                    economy.removeGems(uuid, totalCost);
                    Msg.success(player, "Bought " + amount + "x " + type.getDisplayName() + " Key for " + EconomyManager.GEMS_ICON + EconomyManager.format(totalCost) + " Gems!");
                } else if (type.getStarsCost() > 0) {
                    double totalCost = (double) type.getStarsCost() * amount;
                    if (!economy.hasStars(uuid, totalCost)) {
                        Msg.error(player, "You need " + EconomyManager.STARS_ICON + EconomyManager.format(totalCost) + " Stars!");
                        return true;
                    }
                    economy.removeStars(uuid, totalCost);
                    Msg.success(player, "Bought " + amount + "x " + type.getDisplayName() + " Key for " + EconomyManager.STARS_ICON + EconomyManager.format(totalCost) + " Stars!");
                }

                ItemStack key = crateManager.createKey(type, amount);
                var overflow = player.getInventory().addItem(key);
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }

                return true;
            }
        });

        return commands;
    }
}
