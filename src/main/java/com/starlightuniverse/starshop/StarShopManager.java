package com.starlightuniverse.starshop;

import com.starlightuniverse.crate.CrateManager;
import com.starlightuniverse.crate.CrateType;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.enchant.CustomEnchant;
import com.starlightuniverse.enchant.EnchantManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class StarShopManager {

    public enum Category {
        MAIN,
        ENCHANT_BOOKS,
        CRATE_KEYS,
        PREMIUM_ITEMS
    }

    public sealed interface Entry permits BookEntry, KeyEntry, MaterialEntry {
        int starCost();
        String displayName();
        ItemStack render(EnchantManager enchantManager, CrateManager crateManager);
        ItemStack claim(EnchantManager enchantManager, CrateManager crateManager);
    }

    public record BookEntry(CustomEnchant enchant, int level, int starCost) implements Entry {
        @Override public String displayName() { return enchant.getDisplayName() + " " + EnchantManager.toRoman(level); }
        @Override public ItemStack render(EnchantManager em, CrateManager cm) { return em.createBook(enchant, level); }
        @Override public ItemStack claim(EnchantManager em, CrateManager cm) { return em.createBook(enchant, level); }
    }

    public record KeyEntry(CrateType type, int amount, int starCost) implements Entry {
        @Override public String displayName() { return type.getDisplayName() + " Key x" + amount; }
        @Override public ItemStack render(EnchantManager em, CrateManager cm) { return cm.createKey(type, amount); }
        @Override public ItemStack claim(EnchantManager em, CrateManager cm) { return cm.createKey(type, amount); }
    }

    public record MaterialEntry(Material material, int amount, int starCost, String name) implements Entry {
        @Override public String displayName() { return name; }
        @Override public ItemStack render(EnchantManager em, CrateManager cm) { return new ItemStack(material, amount); }
        @Override public ItemStack claim(EnchantManager em, CrateManager cm) { return new ItemStack(material, amount); }
    }

    private static final List<Entry> BOOKS = List.of(
            new BookEntry(CustomEnchant.STAR_HEART, 5, 200),
            new BookEntry(CustomEnchant.STAR_DRAIN, 5, 200),
            new BookEntry(CustomEnchant.VOID_DODGE, 5, 200),
            new BookEntry(CustomEnchant.PULSAR_REGEN, 5, 200),
            new BookEntry(CustomEnchant.UNIVERSAL_IMPALE, 5, 200),
            new BookEntry(CustomEnchant.HOMING_STAR, 2, 150),
            new BookEntry(CustomEnchant.MAGMA_ORBIT, 2, 150),
            new BookEntry(CustomEnchant.SUPERNOVA_CRY, 2, 150),
            new BookEntry(CustomEnchant.SOLAR_BREAKER, 3, 175),
            new BookEntry(CustomEnchant.VOID_SKEWER, 2, 150),
            new BookEntry(CustomEnchant.CHAIN_LIGHTNING, 3, 175),
            new BookEntry(CustomEnchant.PLASMA_DRAIN, 3, 175),
            new BookEntry(CustomEnchant.GRAVITY_DISARM, 3, 175),
            new BookEntry(CustomEnchant.LAST_LIGHT, 2, 150),
            new BookEntry(CustomEnchant.NEBULA_RAIN, 2, 150),
            new BookEntry(CustomEnchant.THUNDER_BOLT, 2, 150),
            new BookEntry(CustomEnchant.ENERGY_CORE, 1, 150),
            new BookEntry(CustomEnchant.SUPERNOVA_MINE, 2, 175)
    );

    private static final List<Entry> KEYS = List.of(
            new KeyEntry(CrateType.STAR, 1, 25),
            new KeyEntry(CrateType.STAR, 5, 100),
            new KeyEntry(CrateType.COSMIC, 1, 100),
            new KeyEntry(CrateType.COSMIC, 5, 400),
            new KeyEntry(CrateType.GALAXY, 1, 250),
            new KeyEntry(CrateType.GALAXY, 3, 600),
            new KeyEntry(CrateType.SEASONAL, 1, 75)
    );

    private static final List<Entry> PREMIUM = List.of(
            new MaterialEntry(Material.NETHER_STAR, 1, 10, "Nether Star"),
            new MaterialEntry(Material.BEACON, 1, 75, "Beacon"),
            new MaterialEntry(Material.ELYTRA, 1, 200, "Elytra"),
            new MaterialEntry(Material.TOTEM_OF_UNDYING, 1, 30, "Totem of Undying"),
            new MaterialEntry(Material.ENCHANTED_GOLDEN_APPLE, 1, 15, "Enchanted Golden Apple"),
            new MaterialEntry(Material.NETHERITE_INGOT, 1, 50, "Netherite Ingot"),
            new MaterialEntry(Material.SHULKER_SHELL, 2, 20, "Shulker Shells x2"),
            new MaterialEntry(Material.DRAGON_EGG, 1, 500, "Dragon Egg"),
            new MaterialEntry(Material.END_CRYSTAL, 4, 15, "End Crystals x4"),
            new MaterialEntry(Material.MACE, 1, 350, "Mace"),
            new MaterialEntry(Material.TRIDENT, 1, 40, "Trident"),
            new MaterialEntry(Material.HEAVY_CORE, 1, 200, "Heavy Core"),
            new MaterialEntry(Material.CONDUIT, 1, 100, "Conduit"),
            new MaterialEntry(Material.WITHER_SKELETON_SKULL, 1, 30, "Wither Skeleton Skull"),
            new MaterialEntry(Material.DRAGON_HEAD, 1, 100, "Dragon Head"),
            new MaterialEntry(Material.RESPAWN_ANCHOR, 1, 15, "Respawn Anchor"),
            new MaterialEntry(Material.EXPERIENCE_BOTTLE, 64, 10, "XP Bottles x64")
    );

    private static final TextColor STAR_COLOR = TextColor.color(0xFFD700);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);

    private final EconomyManager economy;
    private final EnchantManager enchantManager;
    private final CrateManager crateManager;
    private final Map<UUID, Category> sessions = new HashMap<>();

    public StarShopManager(EconomyManager economy, EnchantManager enchantManager, CrateManager crateManager) {
        this.economy = economy;
        this.enchantManager = enchantManager;
        this.crateManager = crateManager;
    }

    public void openMainMenu(Player player) {
        sessions.put(player.getUniqueId(), Category.MAIN);
        Inventory inv = createInventory(27, "Star Shop " + EconomyManager.STARS_ICON);

        fillBorder(inv);

        inv.setItem(4, balanceIcon(player));

        inv.setItem(11, categoryIcon(Material.ENCHANTED_BOOK, "Legendary Enchant Books",
                0xFFAA00, BOOKS.size() + " books"));
        inv.setItem(13, categoryIcon(Material.TRIPWIRE_HOOK, "Crate Keys",
                0xAA00FF, KEYS.size() + " keys"));
        inv.setItem(15, categoryIcon(Material.NETHER_STAR, "Premium Items",
                0x55FFFF, PREMIUM.size() + " items"));

        player.openInventory(inv);
    }

    public void openCategory(Player player, Category category) {
        if (category == Category.MAIN) { openMainMenu(player); return; }
        sessions.put(player.getUniqueId(), category);
        List<Entry> entries = entriesFor(category);

        Inventory inv = createInventory(54, "Star Shop - " + categoryName(category));
        fillRow(inv, 5);

        for (int i = 0; i < entries.size() && i < 45; i++) {
            inv.setItem(i, renderEntry(player, entries.get(i)));
        }

        inv.setItem(45, backButton());
        inv.setItem(49, balanceIcon(player));
        player.openInventory(inv);
    }

    public void handleClick(Player player, int slot) {
        Category current = sessions.get(player.getUniqueId());
        if (current == null) return;

        if (current == Category.MAIN) {
            switch (slot) {
                case 11 -> openCategory(player, Category.ENCHANT_BOOKS);
                case 13 -> openCategory(player, Category.CRATE_KEYS);
                case 15 -> openCategory(player, Category.PREMIUM_ITEMS);
            }
            return;
        }

        if (slot == 45) { openMainMenu(player); return; }

        if (slot < 0 || slot >= 45) return;
        List<Entry> entries = entriesFor(current);
        if (slot >= entries.size()) return;

        Entry entry = entries.get(slot);
        performPurchase(player, entry, current);
    }

    private void performPurchase(Player player, Entry entry, Category category) {
        UUID uuid = player.getUniqueId();
        if (!economy.hasStars(uuid, entry.starCost())) {
            Msg.error(player, "Not enough Stars! You need " + entry.starCost() + " " + EconomyManager.STARS_ICON + ".");
            return;
        }

        economy.removeStars(uuid, entry.starCost());
        ItemStack claimed = entry.claim(enchantManager, crateManager);

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(claimed);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        Msg.success(player, "Purchased " + entry.displayName() + " for " + entry.starCost() + " " + EconomyManager.STARS_ICON + "!");
        openCategory(player, category);
    }

    public void removeSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    private List<Entry> entriesFor(Category cat) {
        return switch (cat) {
            case ENCHANT_BOOKS -> BOOKS;
            case CRATE_KEYS -> KEYS;
            case PREMIUM_ITEMS -> PREMIUM;
            default -> List.of();
        };
    }

    private String categoryName(Category cat) {
        return switch (cat) {
            case ENCHANT_BOOKS -> "Enchant Books";
            case CRATE_KEYS -> "Crate Keys";
            case PREMIUM_ITEMS -> "Premium Items";
            default -> "Star Shop";
        };
    }

    private Inventory createInventory(int size, String title) {
        StarShopHolder holder = new StarShopHolder();
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text(title, STAR_COLOR).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);
        return inv;
    }

    private ItemStack renderEntry(Player player, Entry entry) {
        ItemStack icon = entry.render(enchantManager, crateManager);
        ItemMeta meta = icon.getItemMeta();
        if (meta == null) return icon;

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        else lore = new ArrayList<>(lore);

        boolean canAfford = economy.hasStars(player.getUniqueId(), entry.starCost());
        TextColor priceColor = canAfford ? GREEN : RED;

        lore.add(Component.empty());
        lore.add(Component.text("Price: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(entry.starCost() + " " + EconomyManager.STARS_ICON, priceColor)));
        lore.add(Component.text(canAfford ? "Click to buy!" : "Not enough Stars",
                canAfford ? YELLOW : RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack categoryIcon(Material material, String name, int color, String detail) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(name, TextColor.color(color))
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(detail, GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to browse!", YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack balanceIcon(Player player) {
        ItemStack icon = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text("Your Stars", STAR_COLOR)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Balance: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(
                        EconomyManager.format(economy.getStars(player.getUniqueId())) + " " + EconomyManager.STARS_ICON,
                        STAR_COLOR)));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack backButton() {
        ItemStack icon = new ItemStack(Material.BARRIER);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text("Back", RED).decoration(TextDecoration.ITALIC, false));
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorder(Inventory inv) {
        ItemStack filler = filler();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        for (int slot : new int[]{4, 11, 13, 15}) inv.setItem(slot, null);
    }

    private void fillRow(Inventory inv, int row) {
        ItemStack filler = filler();
        int start = row * 9;
        for (int i = start; i < start + 9 && i < inv.getSize(); i++) inv.setItem(i, filler);
    }
}
