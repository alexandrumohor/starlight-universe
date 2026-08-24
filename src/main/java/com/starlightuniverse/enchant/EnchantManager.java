package com.starlightuniverse.enchant;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class EnchantManager {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor DARK_GRAY = TextColor.color(0x555555);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);

    private static final NamespacedKey BOOK_TYPE_KEY = NamespacedKey.fromString("starlightuniverse:ce_book_type");
    private static final NamespacedKey BOOK_LEVEL_KEY = NamespacedKey.fromString("starlightuniverse:ce_book_level");

    private static final String ENCHANT_LORE_MARKER = "​";

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private final JavaPlugin plugin;
    private final Map<CustomEnchant, NamespacedKey> enchantKeys = new EnumMap<>(CustomEnchant.class);

    private static final NamespacedKey STAR_HEART_MODIFIER_KEY = NamespacedKey.fromString("starlightuniverse:star_heart");

    public EnchantManager(JavaPlugin plugin) {
        this.plugin = plugin;
        for (CustomEnchant enchant : CustomEnchant.values()) {
            enchantKeys.put(enchant, new NamespacedKey("starlightuniverse", "ce_" + enchant.name().toLowerCase()));
        }
    }

    public NamespacedKey getKey(CustomEnchant enchant) {
        return enchantKeys.get(enchant);
    }

    public static String toRoman(int level) {
        if (level >= 1 && level <= 10) return ROMAN[level];
        return String.valueOf(level);
    }

    // --- Read enchants from item ---

    public int getEnchantLevel(ItemStack item, CustomEnchant enchant) {
        if (item == null || !item.hasItemMeta()) return 0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Integer level = pdc.get(enchantKeys.get(enchant), PersistentDataType.INTEGER);
        return level != null ? level : 0;
    }

    public Map<CustomEnchant, Integer> getAllEnchants(ItemStack item) {
        Map<CustomEnchant, Integer> result = new EnumMap<>(CustomEnchant.class);
        if (item == null || !item.hasItemMeta()) return result;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        for (CustomEnchant enchant : CustomEnchant.values()) {
            Integer level = pdc.get(enchantKeys.get(enchant), PersistentDataType.INTEGER);
            if (level != null && level > 0) {
                result.put(enchant, level);
            }
        }
        return result;
    }

    public int getArmorEnchantLevel(Player player, CustomEnchant enchant) {
        var equipment = player.getEquipment();
        if (equipment == null) return 0;
        ItemStack[] armor = equipment.getArmorContents();
        for (ItemStack piece : armor) {
            int level = getEnchantLevel(piece, enchant);
            if (level > 0) return level;
        }
        return 0;
    }

    // --- Apply / remove enchants ---

    public void applyEnchant(ItemStack item, CustomEnchant enchant, int level) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(enchantKeys.get(enchant), PersistentDataType.INTEGER, level);
        item.setItemMeta(meta);
        updateLore(item);
    }

    public void removeEnchant(ItemStack item, CustomEnchant enchant) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(enchantKeys.get(enchant));
        item.setItemMeta(meta);
        updateLore(item);
    }

    // --- Enchant books ---

    public ItemStack createBook(CustomEnchant enchant, int level) {
        level = Math.max(1, Math.min(level, enchant.getMaxLevel()));
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(BOOK_TYPE_KEY, PersistentDataType.STRING, enchant.name());
        pdc.set(BOOK_LEVEL_KEY, PersistentDataType.INTEGER, level);

        TextColor rarityColor = enchant.getRarity().getColor();
        meta.displayName(Component.text(enchant.getDisplayName() + " " + toRoman(level), rarityColor)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Rarity: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(enchant.getRarity().getBookName(), rarityColor)));
        lore.add(Component.text("Target: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(enchant.getTarget().getDisplayName(), WHITE)));
        lore.add(Component.text("Max Level: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(toRoman(enchant.getMaxLevel()), WHITE)));
        lore.add(Component.empty());
        lore.add(Component.text(enchant.getDescription(), DARK_GRAY).decoration(TextDecoration.ITALIC, true));
        lore.add(Component.empty());
        lore.add(Component.text("Apply at Anvil or Alchemist", GOLD).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        book.setItemMeta(meta);
        return book;
    }

    public boolean isEnchantBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(BOOK_TYPE_KEY, PersistentDataType.STRING);
    }

    public CustomEnchant getBookEnchant(ItemStack item) {
        if (!isEnchantBook(item)) return null;
        String name = item.getItemMeta().getPersistentDataContainer().get(BOOK_TYPE_KEY, PersistentDataType.STRING);
        if (name == null) return null;
        try {
            return CustomEnchant.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public int getBookLevel(ItemStack item) {
        if (!isEnchantBook(item)) return 0;
        Integer level = item.getItemMeta().getPersistentDataContainer().get(BOOK_LEVEL_KEY, PersistentDataType.INTEGER);
        return level != null ? level : 0;
    }

    // --- Lore management ---

    public void updateLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();

        List<Component> existingLore = meta.lore();
        List<Component> newLore = new ArrayList<>();

        if (existingLore != null) {
            for (Component line : existingLore) {
                String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
                if (!plain.startsWith(ENCHANT_LORE_MARKER)) {
                    newLore.add(line);
                }
            }
        }

        Map<CustomEnchant, Integer> enchants = getAllEnchants(item);
        if (!enchants.isEmpty()) {
            for (Map.Entry<CustomEnchant, Integer> entry : enchants.entrySet()) {
                CustomEnchant enchant = entry.getKey();
                int level = entry.getValue();
                TextColor color = enchant.getRarity().getColor();
                String text = ENCHANT_LORE_MARKER + enchant.getDisplayName() + " " + toRoman(level);
                newLore.addFirst(Component.text(text, color).decoration(TextDecoration.ITALIC, false));
            }
        }

        meta.lore(newLore.isEmpty() ? null : newLore);
        item.setItemMeta(meta);
    }

    // --- Star Heart attribute management ---

    public void applyStarHeartAttribute(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        attr.removeModifier(STAR_HEART_MODIFIER_KEY);

        int level = getArmorEnchantLevel(player, CustomEnchant.STAR_HEART);
        if (level > 0) {
            attr.addModifier(new AttributeModifier(STAR_HEART_MODIFIER_KEY, level * 2.0, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    public void removeStarHeartAttribute(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(STAR_HEART_MODIFIER_KEY);
    }
}
