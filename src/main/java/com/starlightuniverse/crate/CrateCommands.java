package com.starlightuniverse.crate;

import com.starlightuniverse.StarlightUniverse;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.enchant.CustomEnchant;
import com.starlightuniverse.enchant.EnchantManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
                    Msg.error(player, "Usage: /spawncrate <star|cosmic|galaxy|celestial|universe>");
                    return true;
                }

                CrateType type = CrateType.fromName(args[0]);
                if (type == null) {
                    Msg.error(player, "Invalid crate type! Use: star, cosmic, galaxy, celestial, universe");
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
            @Override
            public List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
                if (args.length == 1) {
                    return List.of("star", "cosmic", "galaxy", "celestial", "universe").stream()
                            .filter(x -> x.startsWith(args[0].toLowerCase())).toList();
                }
                return List.of();
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
                    if (sender instanceof Player p) Msg.error(p, "Usage: /givekey <player> <star|cosmic|galaxy|celestial|universe> [amount]");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    if (sender instanceof Player p) Msg.error(p, "Player not found!");
                    return true;
                }

                CrateType type = CrateType.fromName(args[1]);
                if (type == null) {
                    if (sender instanceof Player p) Msg.error(p, "Invalid crate type! Use: star, cosmic, galaxy, celestial, universe");
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
            @Override
            public List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
                if (args.length == 1) {
                    String pfx = args[0].toLowerCase();
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(pfx)).toList();
                }
                if (args.length == 2) {
                    return List.of("star", "cosmic", "galaxy", "celestial", "universe").stream()
                            .filter(x -> x.startsWith(args[1].toLowerCase())).toList();
                }
                if (args.length == 3) return List.of("1", "8", "16", "32", "64");
                return List.of();
            }
        });

        commands.add(new Command("givegearticket") {
            { setDescription("Give a gear ticket to a player"); }

            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                if (sender instanceof Player player) {
                    if (!crateManager.getAdminManager().hasPermission(player.getUniqueId(), ADMIN_REQUIRED)) {
                        Msg.error(player, "No permission.");
                        return true;
                    }
                }

                if (args.length < 2) {
                    if (sender instanceof Player p) Msg.error(p, "Usage: /givegearticket <player> <star|cosmic|galaxy|celestial|universe>");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    if (sender instanceof Player p) Msg.error(p, "Player not found!");
                    return true;
                }

                CrateType type = CrateType.fromName(args[1]);
                if (type == null) {
                    if (sender instanceof Player p) Msg.error(p, "Invalid tier! Use: star, cosmic, galaxy, celestial, universe");
                    return true;
                }

                ItemStack ticket = crateManager.getVoucherManager().createGearTicket(type);
                var overflow = target.getInventory().addItem(ticket);
                for (ItemStack leftover : overflow.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                }

                if (sender instanceof Player p) {
                    Msg.success(p, "Gave " + type.getDisplayName() + " Gear Ticket to " + target.getName() + "!");
                }
                Msg.success(target, "You received a " + type.getDisplayName() + " Gear Ticket!");
                return true;
            }
            @Override
            public List<String> tabComplete(@NotNull CommandSender s, @NotNull String a, @NotNull String[] args) {
                if (args.length == 1) {
                    String pfx = args[0].toLowerCase();
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(pfx)).toList();
                }
                if (args.length == 2) {
                    return List.of("star", "cosmic", "galaxy", "celestial", "universe").stream()
                            .filter(x -> x.startsWith(args[1].toLowerCase())).toList();
                }
                return List.of();
            }
        });

        commands.add(new Command("buykey") {
            { setDescription("Buy crate keys from the Star Shop"); }

            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                if (!(sender instanceof Player player)) return false;
                Msg.info(player, "Use /starshop to buy crate keys!");
                return true;
            }
        });

        commands.add(new Command("agive") {
            { setDescription("Give any crate/voucher item to a player"); }

            private static final List<String> ITEM_TYPES = List.of(
                    "key", "flyvoucher", "protection", "booster", "gearticket", "enchant_book");

            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                if (!sender.isOp()) {
                    if (sender instanceof Player p) Msg.error(p, "Only operators can use this!");
                    return true;
                }

                if (args.length < 2) {
                    if (sender instanceof Player p) Msg.error(p, "Usage: /agive <player> <key|flyvoucher|protection|booster|gearticket|enchant_book> [args...]");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    if (sender instanceof Player p) Msg.error(p, "Player not found!");
                    return true;
                }

                String type = args[1].toLowerCase();
                ItemStack item = null;
                String desc = "";

                switch (type) {
                    case "key" -> {
                        if (args.length < 3) {
                            if (sender instanceof Player p) Msg.error(p, "Usage: /agive <player> key <star|cosmic|galaxy|celestial|universe> [amount]");
                            return true;
                        }
                        CrateType crate = CrateType.fromName(args[2]);
                        if (crate == null) { if (sender instanceof Player p) Msg.error(p, "Invalid crate type!"); return true; }
                        int amount = args.length >= 4 ? parseAmount(sender, args[3]) : 1;
                        if (amount < 1) return true;
                        item = crateManager.createKey(crate, amount);
                        desc = amount + "x " + crate.getDisplayName() + " Key";
                    }
                    case "flyvoucher" -> {
                        int minutes = args.length >= 3 ? parseAmount(sender, args[2]) : 10;
                        if (minutes < 1) return true;
                        item = crateManager.getVoucherManager().createFlyVoucher(minutes);
                        desc = "Fly Voucher (" + minutes + " min)";
                    }
                    case "protection" -> {
                        int blocks = args.length >= 3 ? parseAmount(sender, args[2]) : 100;
                        if (blocks < 1) return true;
                        item = crateManager.getVoucherManager().createProtectionToken(blocks);
                        desc = "Protection Token (+" + blocks + " blocks)";
                    }
                    case "booster" -> {
                        double mult = args.length >= 3 ? parseDouble(sender, args[2]) : 2.0;
                        if (mult <= 0) return true;
                        int dur = args.length >= 4 ? parseAmount(sender, args[3]) : 15;
                        if (dur < 1) return true;
                        com.starlightuniverse.booster.BoosterType bType = com.starlightuniverse.booster.BoosterType.XP_VANILLA;
                        if (args.length >= 5) {
                            try { bType = com.starlightuniverse.booster.BoosterType.valueOf(args[4].toUpperCase()); }
                            catch (IllegalArgumentException ignored) {}
                        }
                        item = crateManager.getVoucherManager().createBooster(bType, mult, dur);
                        desc = bType.getDisplayName() + " " + mult + "x (" + dur + " min)";
                    }
                    case "gearticket" -> {
                        if (args.length < 3) {
                            if (sender instanceof Player p) Msg.error(p, "Usage: /agive <player> gearticket <star|cosmic|galaxy|celestial|universe>");
                            return true;
                        }
                        CrateType tier = CrateType.fromName(args[2]);
                        if (tier == null) { if (sender instanceof Player p) Msg.error(p, "Invalid tier!"); return true; }
                        item = crateManager.getVoucherManager().createGearTicket(tier);
                        desc = tier.getDisplayName() + " Gear Ticket";
                    }
                    case "enchant_book" -> {
                        if (args.length < 3) {
                            if (sender instanceof Player p) Msg.error(p, "Usage: /agive <player> enchant_book <enchant_name> [level]");
                            return true;
                        }
                        String enchantName = args[2].toUpperCase();
                        int level = args.length >= 4 ? parseAmount(sender, args[3]) : 1;
                        if (level < 1) return true;

                        CustomEnchant custom = CustomEnchant.byName(enchantName);
                        if (custom != null) {
                            EnchantManager em = StarlightUniverse.getInstance().getEnchantManager();
                            int capped = Math.min(level, custom.getMaxLevel());
                            item = em.createBook(custom, capped);
                            desc = custom.getDisplayName() + " " + EnchantManager.toRoman(capped);
                        } else {
                            Enchantment vanilla = Registry.ENCHANTMENT.match(enchantName);
                            if (vanilla == null) {
                                if (sender instanceof Player p) Msg.error(p, "Unknown enchantment: " + args[2]);
                                return true;
                            }
                            item = new ItemStack(Material.ENCHANTED_BOOK);
                            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
                            meta.addStoredEnchant(vanilla, level, true);
                            String niceName = vanilla.getKey().getKey().replace("_", " ");
                            niceName = niceName.substring(0, 1).toUpperCase() + niceName.substring(1);
                            meta.displayName(Component.text(niceName + " " + EnchantManager.toRoman(level),
                                    TextColor.color(0x55FFFF))
                                    .decoration(TextDecoration.ITALIC, false)
                                    .decoration(TextDecoration.BOLD, true));
                            meta.setItemModel(NamespacedKey.fromString("starlight:shop_enchant_book"));
                            item.setItemMeta(meta);
                            desc = niceName + " " + EnchantManager.toRoman(level);
                        }
                    }
                    default -> {
                        if (sender instanceof Player p) Msg.error(p, "Unknown item type! Use: key, flyvoucher, protection, booster, gearticket, enchant_book");
                        return true;
                    }
                }

                var overflow = target.getInventory().addItem(item);
                for (ItemStack leftover : overflow.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                }
                if (sender instanceof Player p) Msg.success(p, "Gave " + desc + " to " + target.getName() + "!");
                Msg.success(target, "You received " + desc + "!");
                return true;
            }

            private int parseAmount(CommandSender sender, String s) {
                try {
                    int v = Integer.parseInt(s);
                    if (v < 1 || v > 64000) { if (sender instanceof Player p) Msg.error(p, "Invalid amount!"); return -1; }
                    return v;
                } catch (NumberFormatException e) { if (sender instanceof Player p) Msg.error(p, "Invalid number!"); return -1; }
            }

            private double parseDouble(CommandSender sender, String s) {
                try {
                    double v = Double.parseDouble(s);
                    if (v <= 0 || v > 100) { if (sender instanceof Player p) Msg.error(p, "Invalid multiplier!"); return -1; }
                    return v;
                } catch (NumberFormatException e) { if (sender instanceof Player p) Msg.error(p, "Invalid number!"); return -1; }
            }

            @Override
            public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
                if (!sender.isOp()) return List.of();
                if (args.length == 1) {
                    String pfx = args[0].toLowerCase();
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(pfx)).toList();
                }
                if (args.length == 2) {
                    return ITEM_TYPES.stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
                }
                if (args.length == 3) {
                    String t = args[1].toLowerCase();
                    if (t.equals("key") || t.equals("gearticket")) {
                        return List.of("star", "cosmic", "galaxy", "celestial", "universe").stream()
                                .filter(s -> s.startsWith(args[2].toLowerCase())).toList();
                    }
                    if (t.equals("flyvoucher")) return List.of("10", "25", "50", "90", "120");
                    if (t.equals("protection")) return List.of("100", "500", "1500", "10000", "50000");
                    if (t.equals("booster")) return List.of("1.5", "2.0", "3.0", "5.0", "10.0");
                    if (t.equals("enchant_book")) {
                        String pfx = args[2].toLowerCase();
                        Stream<String> customNames = java.util.Arrays.stream(CustomEnchant.values())
                                .map(e -> e.name().toLowerCase());
                        Stream<String> vanillaNames = StreamSupport.stream(Registry.ENCHANTMENT.spliterator(), false)
                                .map(e -> e.getKey().getKey());
                        return Stream.concat(customNames, vanillaNames)
                                .filter(n -> n.startsWith(pfx))
                                .limit(30)
                                .toList();
                    }
                }
                if (args.length == 4) {
                    String t = args[1].toLowerCase();
                    if (t.equals("key")) return List.of("1", "8", "16", "32", "64");
                    if (t.equals("booster")) return List.of("15", "30", "60");
                    if (t.equals("enchant_book")) {
                        CustomEnchant custom = CustomEnchant.byName(args[2]);
                        int max = custom != null ? custom.getMaxLevel() : 10;
                        List<String> levels = new ArrayList<>();
                        for (int i = 1; i <= max; i++) levels.add(String.valueOf(i));
                        return levels;
                    }
                }
                return List.of();
            }
        });

        return commands;
    }
}
