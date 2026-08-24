package com.starlightuniverse.enchant;

import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

import java.util.*;

public class EnchantCommand {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);

    public static List<Command> create(EnchantManager manager) {
        List<Command> commands = new ArrayList<>();

        commands.add(new Command("enchantbook") {
            {
                setDescription("Give a custom enchant book");
                setUsage("/enchantbook <enchant> [level] [player]");
                setPermission("su.admin.enchantbook");
            }

            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (args.length < 1) {
                    if (sender instanceof Player player) {
                        openEnchantListGui(player, manager);
                    } else {
                        sender.sendMessage(Msg.errorComponent("Usage: /enchantbook <enchant> [level] [player]"));
                    }
                    return true;
                }

                CustomEnchant enchant = CustomEnchant.byName(args[0]);
                if (enchant == null) {
                    sender.sendMessage(Msg.errorComponent("Unknown enchant: " + args[0]));
                    return true;
                }

                int level = 1;
                if (args.length >= 2) {
                    try {
                        level = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Msg.errorComponent("Invalid level: " + args[1]));
                        return true;
                    }
                    if (level < 1 || level > enchant.getMaxLevel()) {
                        sender.sendMessage(Msg.errorComponent("Level must be 1-" + enchant.getMaxLevel()));
                        return true;
                    }
                }

                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        sender.sendMessage(Msg.errorComponent("Player not found: " + args[2]));
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    sender.sendMessage(Msg.errorComponent("Specify a player from console."));
                    return true;
                }

                ItemStack book = manager.createBook(enchant, level);
                var remaining = target.getInventory().addItem(book);
                for (ItemStack leftover : remaining.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                }

                TextColor rarityColor = enchant.getRarity().getColor();
                Msg.success(target, "Received " + enchant.getDisplayName() + " " + EnchantManager.toRoman(level) + " book!");
                if (sender != target) {
                    sender.sendMessage(Component.text("[SU] ", GOLD)
                            .append(Component.text("Gave ", GREEN))
                            .append(Component.text(enchant.getDisplayName() + " " + EnchantManager.toRoman(level), rarityColor))
                            .append(Component.text(" to " + target.getName(), GREEN)));
                }
                return true;
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                if (args.length == 1) {
                    String prefix = args[0].toLowerCase();
                    List<String> completions = new ArrayList<>();
                    for (CustomEnchant e : CustomEnchant.values()) {
                        String name = e.name().toLowerCase();
                        if (name.startsWith(prefix)) completions.add(name);
                    }
                    return completions;
                }
                if (args.length == 3) {
                    String prefix = args[2].toLowerCase();
                    List<String> completions = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(prefix)) completions.add(p.getName());
                    }
                    return completions;
                }
                return List.of();
            }
        });

        commands.add(new Command("enchantlist") {
            {
                setDescription("Browse all custom enchantments");
                setUsage("/enchantlist [target]");
                setPermission("su.admin.enchantbook");
            }

            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Msg.errorComponent("Players only."));
                    return true;
                }

                ItemTarget filter = null;
                if (args.length >= 1) {
                    try {
                        filter = ItemTarget.valueOf(args[0].toUpperCase());
                    } catch (IllegalArgumentException ignored) {}
                }

                openEnchantListGui(player, manager, filter);
                return true;
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                if (args.length == 1) {
                    String prefix = args[0].toLowerCase();
                    List<String> completions = new ArrayList<>();
                    for (ItemTarget t : ItemTarget.values()) {
                        if (t.name().toLowerCase().startsWith(prefix)) completions.add(t.name().toLowerCase());
                    }
                    return completions;
                }
                return List.of();
            }
        });

        return commands;
    }

    private static void openEnchantListGui(Player player, EnchantManager manager) {
        openEnchantListGui(player, manager, null);
    }

    private static void openEnchantListGui(Player player, EnchantManager manager, ItemTarget filter) {
        List<CustomEnchant> enchants;
        String title;
        if (filter != null) {
            enchants = Arrays.stream(CustomEnchant.values())
                    .filter(e -> e.getTarget() == filter)
                    .toList();
            title = filter.getDisplayName() + " Enchants";
        } else {
            enchants = List.of(CustomEnchant.values());
            title = "All Enchants (" + enchants.size() + ")";
        }

        int size = Math.min(54, ((enchants.size() + 8) / 9) * 9);
        if (size < 9) size = 9;

        EnchantListHolder holder = new EnchantListHolder(manager);
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text(title, TextColor.color(0xAA00AA)).decoration(TextDecoration.BOLD, true));
        holder.setInventory(inv);

        for (int i = 0; i < enchants.size() && i < size; i++) {
            CustomEnchant enchant = enchants.get(i);
            TextColor rarityColor = enchant.getRarity().getColor();

            ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(enchant.getDisplayName(), rarityColor)
                    .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Target: ", GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(enchant.getTarget().getDisplayName(), WHITE)));
            lore.add(Component.text("Max Level: ", GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(EnchantManager.toRoman(enchant.getMaxLevel()), WHITE)));
            lore.add(Component.text("Rarity: ", GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(enchant.getRarity().getBookName(), rarityColor)));
            lore.add(Component.empty());
            lore.add(Component.text(enchant.getDescription(), GRAY).decoration(TextDecoration.ITALIC, true));
            lore.add(Component.empty());
            lore.add(Component.text("Click to get max level book", YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        player.openInventory(inv);
    }
}
