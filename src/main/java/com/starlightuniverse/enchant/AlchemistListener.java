package com.starlightuniverse.enchant;

import com.starlightuniverse.auth.AuthManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AlchemistListener implements Listener {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor PURPLE = TextColor.color(0xAA00AA);

    private static final int[] COMBINE_XP_COSTS = {500, 1_500, 4_000, 10_000, 25_000, 50_000, 100_000, 200_000, 500_000};

    private final JavaPlugin plugin;
    private final EnchantManager enchantManager;
    private final AuthManager authManager;

    public AlchemistListener(JavaPlugin plugin, EnchantManager enchantManager, AuthManager authManager) {
        this.plugin = plugin;
        this.enchantManager = enchantManager;
        this.authManager = authManager;
    }

    public void openAlchemistGui(Player player) {
        AlchemistHolder holder = new AlchemistHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("✦ Alchemist ✦", PURPLE)
                        .decoration(TextDecoration.BOLD, true));
        holder.setInventory(inv);

        ItemStack glass = createGlassPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

        ItemStack info = new ItemStack(Material.BREWING_STAND);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("Alchemist", GOLD)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.empty());
        infoLore.add(Component.text("Combine 2 enchant books of the", GRAY).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("same type and level into one", GRAY).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("book with level +1!", GRAY).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.empty());
        infoLore.add(Component.text("Works with custom & vanilla books.", YELLOW).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("No \"Too Expensive\" limit!", GREEN).decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("Costs XP points, not levels.", CYAN).decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(AlchemistHolder.INFO_SLOT, info);

        inv.setItem(AlchemistHolder.INPUT_SLOT_1, null);
        inv.setItem(AlchemistHolder.INPUT_SLOT_2, null);

        inv.setItem(AlchemistHolder.PLUS_SLOT, createGlassPane(Material.LIME_STAINED_GLASS_PANE, "+"));

        updateResultDisplay(inv, null, 0);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1f, 1.2f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AlchemistHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int rawSlot = event.getRawSlot();

        if (rawSlot >= 0 && rawSlot < 27) {
            if (holder.isInputSlot(rawSlot)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    validateInputSlots(event.getInventory(), player);
                    refreshResult(event.getInventory());
                });
            } else if (rawSlot == AlchemistHolder.RESULT_SLOT) {
                event.setCancelled(true);
                processCombine(player, event.getInventory());
            } else {
                event.setCancelled(true);
            }
        } else {
            if (event.isShiftClick()) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() == Material.ENCHANTED_BOOK) {
                    event.setCancelled(true);
                    Inventory inv = event.getInventory();
                    if (inv.getItem(AlchemistHolder.INPUT_SLOT_1) == null) {
                        inv.setItem(AlchemistHolder.INPUT_SLOT_1, clicked.clone());
                        clicked.setAmount(0);
                    } else if (inv.getItem(AlchemistHolder.INPUT_SLOT_2) == null) {
                        inv.setItem(AlchemistHolder.INPUT_SLOT_2, clicked.clone());
                        clicked.setAmount(0);
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> refreshResult(inv));
                } else {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof AlchemistHolder holder)) return;
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < 27 && !holder.isInputSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getWhoClicked() instanceof Player player) {
                validateInputSlots(event.getInventory(), player);
            }
            refreshResult(event.getInventory());
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AlchemistHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        returnItem(inv, AlchemistHolder.INPUT_SLOT_1, player);
        returnItem(inv, AlchemistHolder.INPUT_SLOT_2, player);
    }

    private void returnItem(Inventory inv, int slot, Player player) {
        ItemStack item = inv.getItem(slot);
        if (item != null && item.getType() != Material.AIR) {
            inv.setItem(slot, null);
            var remaining = player.getInventory().addItem(item);
            for (ItemStack leftover : remaining.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private void validateInputSlots(Inventory inv, Player player) {
        validateSlot(inv, AlchemistHolder.INPUT_SLOT_1, player);
        validateSlot(inv, AlchemistHolder.INPUT_SLOT_2, player);
    }

    private void validateSlot(Inventory inv, int slot, Player player) {
        ItemStack item = inv.getItem(slot);
        if (item != null && item.getType() != Material.AIR && item.getType() != Material.ENCHANTED_BOOK) {
            inv.setItem(slot, null);
            var remaining = player.getInventory().addItem(item);
            for (ItemStack leftover : remaining.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private void refreshResult(Inventory inv) {
        ItemStack input1 = inv.getItem(AlchemistHolder.INPUT_SLOT_1);
        ItemStack input2 = inv.getItem(AlchemistHolder.INPUT_SLOT_2);

        CombineResult result = checkCombination(input1, input2);
        if (result != null) {
            updateResultDisplay(inv, result.resultBook, result.xpCost);
        } else {
            updateResultDisplay(inv, null, 0);
        }
    }

    private void updateResultDisplay(Inventory inv, ItemStack resultBook, int xpCost) {
        if (resultBook != null) {
            inv.setItem(AlchemistHolder.ARROW_SLOT, createGlassPane(Material.GREEN_STAINED_GLASS_PANE, "=>"));

            ItemStack display = resultBook.clone();
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = meta.lore();
            if (lore == null) lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("XP Cost: ", GRAY).decoration(TextDecoration.ITALIC, false)
                    .append(Component.text(EconomyManager.format(xpCost) + " XP", CYAN)));
            lore.add(Component.empty());
            lore.add(Component.text("Click to combine!", YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            display.setItemMeta(meta);
            inv.setItem(AlchemistHolder.RESULT_SLOT, display);
        } else {
            inv.setItem(AlchemistHolder.ARROW_SLOT, createGlassPane(Material.GRAY_STAINED_GLASS_PANE, "=>"));

            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.displayName(Component.text("No valid combination", RED)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Place 2 books of the same", GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("enchant and level.", GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            empty.setItemMeta(meta);
            inv.setItem(AlchemistHolder.RESULT_SLOT, empty);
        }
    }

    private void processCombine(Player player, Inventory inv) {
        ItemStack input1 = inv.getItem(AlchemistHolder.INPUT_SLOT_1);
        ItemStack input2 = inv.getItem(AlchemistHolder.INPUT_SLOT_2);

        CombineResult result = checkCombination(input1, input2);
        if (result == null) {
            Msg.error(player, "No valid combination!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        if (player.getTotalExperience() < result.xpCost) {
            Msg.error(player, "You need " + EconomyManager.format(result.xpCost) + " XP to combine!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        player.giveExp(-result.xpCost);

        if (input1.getAmount() > 1) {
            input1.setAmount(input1.getAmount() - 1);
        } else {
            inv.setItem(AlchemistHolder.INPUT_SLOT_1, null);
        }

        if (input2.getAmount() > 1) {
            input2.setAmount(input2.getAmount() - 1);
        } else {
            inv.setItem(AlchemistHolder.INPUT_SLOT_2, null);
        }

        var remaining = player.getInventory().addItem(result.resultBook);
        for (ItemStack leftover : remaining.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        refreshResult(inv);

        Msg.success(player, "Combined into " + result.resultName + "! (-" + EconomyManager.format(result.xpCost) + " XP)");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.5f);
    }

    private CombineResult checkCombination(ItemStack item1, ItemStack item2) {
        if (item1 == null || item2 == null) return null;
        if (item1.getType() != Material.ENCHANTED_BOOK || item2.getType() != Material.ENCHANTED_BOOK) return null;

        boolean isCustom1 = enchantManager.isEnchantBook(item1);
        boolean isCustom2 = enchantManager.isEnchantBook(item2);

        if (isCustom1 && isCustom2) {
            return checkCustomCombination(item1, item2);
        } else if (!isCustom1 && !isCustom2) {
            return checkVanillaCombination(item1, item2);
        }
        return null;
    }

    private CombineResult checkCustomCombination(ItemStack item1, ItemStack item2) {
        CustomEnchant enchant1 = enchantManager.getBookEnchant(item1);
        int level1 = enchantManager.getBookLevel(item1);
        CustomEnchant enchant2 = enchantManager.getBookEnchant(item2);
        int level2 = enchantManager.getBookLevel(item2);

        if (enchant1 == null || enchant2 == null) return null;
        if (enchant1 != enchant2) return null;
        if (level1 != level2) return null;

        int newLevel = level1 + 1;
        if (newLevel > enchant1.getMaxLevel()) return null;

        int xpCost = getXpCost(newLevel);
        if (xpCost < 0) return null;

        ItemStack resultBook = enchantManager.createBook(enchant1, newLevel);
        String name = enchant1.getDisplayName() + " " + EnchantManager.toRoman(newLevel);

        return new CombineResult(resultBook, xpCost, name);
    }

    private CombineResult checkVanillaCombination(ItemStack item1, ItemStack item2) {
        if (!(item1.getItemMeta() instanceof EnchantmentStorageMeta meta1)) return null;
        if (!(item2.getItemMeta() instanceof EnchantmentStorageMeta meta2)) return null;

        Map<Enchantment, Integer> stored1 = meta1.getStoredEnchants();
        Map<Enchantment, Integer> stored2 = meta2.getStoredEnchants();

        if (stored1.size() != 1 || stored2.size() != 1) return null;

        var entry1 = stored1.entrySet().iterator().next();
        var entry2 = stored2.entrySet().iterator().next();

        if (!entry1.getKey().equals(entry2.getKey())) return null;
        if (!entry1.getValue().equals(entry2.getValue())) return null;

        Enchantment enchant = entry1.getKey();
        int currentLevel = entry1.getValue();
        int newLevel = currentLevel + 1;

        if (newLevel > enchant.getMaxLevel()) return null;

        int xpCost = getXpCost(newLevel);
        if (xpCost < 0) return null;

        ItemStack resultBook = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta resultMeta = (EnchantmentStorageMeta) resultBook.getItemMeta();
        resultMeta.addStoredEnchant(enchant, newLevel, true);
        resultBook.setItemMeta(resultMeta);

        String enchantName = formatEnchantName(enchant);
        String name = enchantName + " " + EnchantManager.toRoman(newLevel);

        return new CombineResult(resultBook, xpCost, name);
    }

    private int getXpCost(int resultLevel) {
        int index = resultLevel - 2;
        if (index >= 0 && index < COMBINE_XP_COSTS.length) {
            return COMBINE_XP_COSTS[index];
        }
        return -1;
    }

    private String formatEnchantName(Enchantment enchant) {
        String key = enchant.getKey().getKey();
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }

    private ItemStack createGlassPane(Material material, String name) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(name, GRAY).decoration(TextDecoration.ITALIC, false));
        glass.setItemMeta(meta);
        return glass;
    }

    private record CombineResult(ItemStack resultBook, int xpCost, String resultName) {}
}
