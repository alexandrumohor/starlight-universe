package com.starlightuniverse.spawner;

import com.starlightuniverse.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SpawnerCommand extends Command {

    private final SpawnerManager spawnerManager;

    public SpawnerCommand(SpawnerManager spawnerManager) {
        super("spawnershop");
        this.spawnerManager = spawnerManager;
        setDescription("Open the Virtual Spawner Shop");
        setAliases(java.util.List.of("vspawner", "vspawnershop"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("give")
                && (sender.isOp() || sender.hasPermission("su.admin"))) {
            if (args.length < 2) {
                Msg.error(player, "Usage: /spawnershop give <TYPE> [tier] [stack]");
                return true;
            }
            VirtualSpawnerType type = VirtualSpawnerType.fromName(args[1]);
            if (type == null) {
                Msg.error(player, "Unknown spawner type. Available: ZOMBIE, CREEPER, BLAZE, WITHER_SKELETON");
                return true;
            }
            int tier = 1, stack = 1;
            try { if (args.length >= 3) tier = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
            try { if (args.length >= 4) stack = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
            var item = spawnerManager.createSpawnerItem(type, tier, stack);
            var overflow = player.getInventory().addItem(item);
            for (var leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            Msg.success(player, "Given: " + type.getDisplayName() + " Spawner (Tier " + tier + ", Stack " + stack + ")");
            return true;
        }

        spawnerManager.openShopGui(player);
        return true;
    }

    @Override
    public java.util.List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("su.admin")) return java.util.List.of();
        if (args.length == 1) {
            return java.util.List.of("give").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String pfx = args[1].toUpperCase();
            return java.util.stream.Stream.of(VirtualSpawnerType.values())
                    .map(Enum::name).filter(n -> n.startsWith(pfx)).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return java.util.List.of("1", "2", "3");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return java.util.List.of("1", "16", "32", "64");
        }
        return java.util.List.of();
    }
}
