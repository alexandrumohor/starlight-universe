package com.starlightuniverse.voucher;

import com.starlightuniverse.enchant.CustomEnchant;
import com.starlightuniverse.enchant.EnchantManager;
import com.starlightuniverse.util.Msg;
import com.starlightuniverse.voucher.EnchantRemoverSelectHolder.EnchantEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EnchantRemoverListener implements Listener {

    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor PURPLE = TextColor.color(0xAA00FF);

    private final JavaPlugin plugin;
    private final VoucherManager voucherManager;
    private final EnchantManager enchantManager;

    private final Set<UUID> selecting = ConcurrentHashMap.newKeySet();

    public EnchantRemoverListener(JavaPlugin plugin, VoucherManager voucherManager, EnchantManager enchantManager) {
        this.plugin = plugin;
        this.voucherManager = voucherManager;
        this.enchantManager = enchantManager;
    }

    public void startSelection(Player player) {
        selecting.add(player.getUniqueId());
        Msg.info(player, "Open your inventory and click on the item you want to remove an enchant from.");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getInventory().getHolder() instanceof EnchantRemoverSelectHolder holder) {
            event.setCancelled(true);
            handleSelectClick(player, event.getRawSlot(), holder);
            return;
        }

        if (event.getInventory().getHolder() instanceof EnchantRemoverConfirmHolder holder) {
            event.setCancelled(true);
            handleConfirmClick(player, event.getRawSlot(), holder);
            return;
        }

        if (!selecting.contains(player.getUniqueId())) return;
        if (event.getClickedInventory() != player.getInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        Map<Enchantment, Integer> vanilla = clicked.getEnchantments();
        Map<CustomEnchant, Integer> custom = enchantManager.getAllEnchants(clicked);
        if (vanilla.isEmpty() && custom.isEmpty()) {
            Msg.error(player, "That item has no enchantments to remove!");
            return;
        }

        event.setCancelled(true);
        selecting.remove(player.getUniqueId());
        openSelectGui(player, clicked, event.getSlot(), vanilla, custom);
    }

    private void openSelectGui(Player player, ItemStack target, int targetSlot,
                               Map<Enchantment, Integer> vanilla, Map<CustomEnchant, Integer> custom) {
        List<EnchantEntry> entries = new ArrayList<>();

        for (Enchantment ench : vanilla.keySet()) {
            entries.add(new EnchantEntry(false, ench.getKey().toString()));
        }
        for (CustomEnchant ench : custom.keySet()) {
            entries.add(new EnchantEntry(true, ench.name()));
        }

        int guiSize = Math.max(9, ((entries.size() + 8) / 9) * 9);
        guiSize = Math.min(54, guiSize);

        EnchantRemoverSelectHolder holder = new EnchantRemoverSelectHolder(targetSlot, entries);
        Inventory gui = Bukkit.createInventory(holder, guiSize,
                Component.text("Select Enchant to Remove", PURPLE)
                        .decoration(TextDecoration.BOLD, true));
        holder.setInventory(gui);

        int slot = 0;
        for (Map.Entry<Enchantment, Integer> e : vanilla.entrySet()) {
            if (slot >= guiSize) break;
            gui.setItem(slot++, vanillaBookIcon(e.getKey(), e.getValue()));
        }
        for (Map.Entry<CustomEnchant, Integer> e : custom.entrySet()) {
            if (slot >= guiSize) break;
            gui.setItem(slot++, customBookIcon(e.getKey(), e.getValue()));
        }

        ItemStack glass = glassPane();
        for (int i = slot; i < guiSize; i++) gui.setItem(i, glass);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
    }

    private void handleSelectClick(Player player, int rawSlot, EnchantRemoverSelectHolder holder) {
        EnchantEntry entry = holder.getEnchant(rawSlot);
        if (entry == null) return;

        ItemStack clicked = player.getOpenInventory().getItem(rawSlot);
        Component displayName = (clicked != null && clicked.hasItemMeta())
                ? clicked.getItemMeta().displayName()
                : Component.text("Unknown", GRAY);

        Bukkit.getScheduler().runTask(plugin, () ->
                openConfirmGui(player, holder.getTargetSlot(), entry, displayName));
    }

    private void openConfirmGui(Player player, int targetSlot, EnchantEntry entry, Component displayName) {
        EnchantRemoverConfirmHolder holder = new EnchantRemoverConfirmHolder(
                targetSlot, entry.isCustom(), entry.key(), displayName);
        Inventory gui = Bukkit.createInventory(holder, 27,
                Component.text("Are you sure?", RED)
                        .decoration(TextDecoration.BOLD, true));
        holder.setInventory(gui);

        ItemStack glass = glassPane();
        for (int i = 0; i < 27; i++) gui.setItem(i, glass);

        ItemStack icon = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta iconMeta = icon.getItemMeta();
        iconMeta.displayName(displayName);
        iconMeta.lore(List.of(
                Component.text("This enchantment will be removed", GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        icon.setItemMeta(iconMeta);
        gui.setItem(EnchantRemoverConfirmHolder.ICON_SLOT, icon);

        ItemStack yes = new ItemStack(Material.GREEN_WOOL);
        ItemMeta yesMeta = yes.getItemMeta();
        yesMeta.displayName(Component.text("Yes, remove it", GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        yes.setItemMeta(yesMeta);
        gui.setItem(EnchantRemoverConfirmHolder.YES_SLOT, yes);

        ItemStack no = new ItemStack(Material.RED_WOOL);
        ItemMeta noMeta = no.getItemMeta();
        noMeta.displayName(Component.text("No, cancel", RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        no.setItemMeta(noMeta);
        gui.setItem(EnchantRemoverConfirmHolder.NO_SLOT, no);

        player.openInventory(gui);
    }

    private void handleConfirmClick(Player player, int rawSlot, EnchantRemoverConfirmHolder holder) {
        if (rawSlot == EnchantRemoverConfirmHolder.YES_SLOT) {
            if (!consumeRemoverScroll(player)) {
                Msg.error(player, "You no longer have an Enchant Remover Scroll!");
                player.closeInventory();
                return;
            }

            ItemStack target = player.getInventory().getItem(holder.getTargetSlot());
            if (target == null || target.getType() == Material.AIR) {
                Msg.error(player, "The item is no longer in your inventory!");
                player.closeInventory();
                return;
            }

            if (holder.isCustom()) {
                CustomEnchant ce = CustomEnchant.valueOf(holder.getEnchantKey());
                if (enchantManager.getEnchantLevel(target, ce) <= 0) {
                    Msg.error(player, "That enchantment is no longer on the item!");
                    player.closeInventory();
                    return;
                }
                enchantManager.removeEnchant(target, ce);
            } else {
                NamespacedKey nk = NamespacedKey.fromString(holder.getEnchantKey());
                Enchantment ench = nk != null ? Registry.ENCHANTMENT.get(nk) : null;
                if (ench == null || !target.containsEnchantment(ench)) {
                    Msg.error(player, "That enchantment is no longer on the item!");
                    player.closeInventory();
                    return;
                }
                target.removeEnchantment(ench);
            }

            player.closeInventory();
            Msg.success(player, "Enchantment successfully removed!");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.5f);
            player.getWorld().spawnParticle(Particle.ENCHANT,
                    player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.5);

        } else if (rawSlot == EnchantRemoverConfirmHolder.NO_SLOT) {
            player.closeInventory();
            Msg.info(player, "Enchant removal cancelled.");
        }
    }

    private boolean consumeRemoverScroll(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null) continue;
            if (!"ENCHANT_REMOVER_SCROLL".equals(voucherManager.getVoucherType(item))) continue;
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItem(i, null);
            }
            return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        selecting.remove(event.getPlayer().getUniqueId());
    }

    private ItemStack vanillaBookIcon(Enchantment ench, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        String name = formatEnchantName(ench) + " " + EnchantManager.toRoman(level);
        meta.displayName(Component.text(name, CYAN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Vanilla Enchantment", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Click to remove", YELLOW).decoration(TextDecoration.ITALIC, false)));
        book.setItemMeta(meta);
        return book;
    }

    private ItemStack customBookIcon(CustomEnchant ench, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        String name = ench.getDisplayName() + " " + EnchantManager.toRoman(level);
        meta.displayName(Component.text(name, ench.getRarity().getColor())
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Custom Enchantment", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(ench.getDescription(), GRAY).decoration(TextDecoration.ITALIC, true),
                Component.empty(),
                Component.text("Click to remove", YELLOW).decoration(TextDecoration.ITALIC, false)));
        book.setItemMeta(meta);
        return book;
    }

    private ItemStack glassPane() {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" ", GRAY).decoration(TextDecoration.ITALIC, false));
        glass.setItemMeta(meta);
        return glass;
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
}
