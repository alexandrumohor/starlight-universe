package com.starlightuniverse.starshop;

import com.starlightuniverse.buff.BuffManager;
import com.starlightuniverse.buff.BuffType;
import com.starlightuniverse.crate.CrateManager;
import com.starlightuniverse.crate.CrateType;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.premium.PremiumManager;
import com.starlightuniverse.premium.PremiumRank;
import com.starlightuniverse.util.Msg;
import com.starlightuniverse.voucher.VoucherManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class StarShopManager {

    public enum Category {
        MAIN, RANKS, RANK_UPGRADE, CRATE_KEYS, PROTECTION_PACKAGES, SERVER_BUFFS
    }

    private static final TextColor STAR_COLOR = TextColor.color(0xFFD700);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor PURPLE = TextColor.color(0xAA00FF);

    private static final PremiumRank[] PURCHASABLE_RANKS = {
            PremiumRank.METEOR, PremiumRank.COMET, PremiumRank.NEBULA,
            PremiumRank.SUPERNOVA, PremiumRank.GALAXY, PremiumRank.UNIVERSE
    };

    private static final int[] KEY_BUNDLES = {1, 3, 5, 10, 20};

    private static final CrateType[] KEY_TYPES = {
            CrateType.COSMIC, CrateType.GALAXY, CrateType.CELESTIAL, CrateType.UNIVERSE
    };
    private static final int[][] KEY_PRICES = {
            {4, 10, 20, 30, 50},
            {8, 20, 40, 60, 100},
            {16, 40, 80, 120, 200},
            {32, 80, 160, 240, 400}
    };

    private static final int[] PROTECTION_RADII = {50, 125, 250, 500, 1250, 2500, 5000};
    private static final String[] PROTECTION_LABELS = {
            "100x100", "250x250", "500x500", "1000x1000", "2500x2500", "5000x5000", "10000x10000"
    };
    private static final int PROTECTION_PRICE = 100;

    private static final int BUFF_PRICE = 100;

    private final JavaPlugin plugin;
    private final EconomyManager economy;
    private final CrateManager crateManager;
    private final PremiumManager premiumManager;
    private final VoucherManager voucherManager;
    private final BuffManager buffManager;

    private final Map<UUID, Category> sessions = new HashMap<>();

    public StarShopManager(JavaPlugin plugin, EconomyManager economy, CrateManager crateManager,
                           PremiumManager premiumManager, VoucherManager voucherManager,
                           BuffManager buffManager) {
        this.plugin = plugin;
        this.economy = economy;
        this.crateManager = crateManager;
        this.premiumManager = premiumManager;
        this.voucherManager = voucherManager;
        this.buffManager = buffManager;
    }

    public void openMainMenu(Player player) {
        sessions.put(player.getUniqueId(), Category.MAIN);
        Inventory inv = createInventory(27, "Star Shop " + EconomyManager.STARS_ICON);
        fillBorder(inv);
        inv.setItem(4, balanceIcon(player));

        inv.setItem(11, categoryIcon(Material.DIAMOND_CHESTPLATE, "Ranks",
                0xFFD700, "Purchase premium ranks"));
        inv.setItem(12, categoryIcon(Material.EXPERIENCE_BOTTLE, "Rank Upgrade",
                0x55FF55, "Upgrade to a higher rank"));
        inv.setItem(13, categoryIcon(Material.TRIPWIRE_HOOK, "Crate Keys",
                0xAA00FF, "Key bundles for all crates"));
        inv.setItem(14, categoryIcon(Material.HEART_OF_THE_SEA, "Protection Packages",
                0x55FFFF, "Expand your protection area"));
        inv.setItem(15, categoryIcon(Material.BEACON, "Server Buffs",
                0xFF5555, "12h personal buffs"));

        player.openInventory(inv);
    }

    // ── Category openers ──

    public void openCategory(Player player, Category category) {
        if (category == Category.MAIN) { openMainMenu(player); return; }
        sessions.put(player.getUniqueId(), category);
        switch (category) {
            case RANKS -> openRanks(player);
            case RANK_UPGRADE -> openRankUpgrade(player);
            case CRATE_KEYS -> openCrateKeys(player);
            case PROTECTION_PACKAGES -> openProtection(player);
            case SERVER_BUFFS -> openBuffs(player);
            default -> openMainMenu(player);
        }
    }

    private void openRanks(Player player) {
        Inventory inv = createInventory(27, "Star Shop - Ranks");
        fillBorder(inv);
        inv.setItem(45 % 27 == 0 ? 18 : 18, backButton());

        PremiumRank current = premiumManager.getPlayerRank(player.getUniqueId());
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < PURCHASABLE_RANKS.length; i++) {
            PremiumRank rank = PURCHASABLE_RANKS[i];
            boolean owned = current.getLevel() >= rank.getLevel();
            int cost = rank.getStarsCost();

            ItemStack icon = new ItemStack(owned ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(rank.getColoredPrefix()
                    .append(Component.text(" Rank", rank.getColor()))
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            String homesStr = rank.getMaxHomes() < 0 ? "Unlimited" : String.valueOf(rank.getMaxHomes());
            lore.add(Component.text("Homes: " + homesStr, GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Protection: " + String.format("%,d", rank.getMaxProtectionBlocks()) + " blocks", GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Keep XP: " + rank.getKeepXpPercent() + "%", GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());

            if (owned) {
                lore.add(Component.text("Already owned!", GREEN).decoration(TextDecoration.ITALIC, false));
            } else {
                boolean canAfford = economy.hasStars(player.getUniqueId(), cost);
                lore.add(Component.text("Price: " + cost + " " + EconomyManager.STARS_ICON,
                        canAfford ? GREEN : RED).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(canAfford ? "Click to buy!" : "Not enough Stars",
                        canAfford ? YELLOW : RED).decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slots[i], icon);
        }

        inv.setItem(18, backButton());
        inv.setItem(22, balanceIcon(player));
        player.openInventory(inv);
    }

    private void openRankUpgrade(Player player) {
        Inventory inv = createInventory(27, "Star Shop - Rank Upgrade");
        fillBorder(inv);

        PremiumRank current = premiumManager.getPlayerRank(player.getUniqueId());

        if (current == PremiumRank.GALAXY) {
            ItemStack info = new ItemStack(Material.BARRIER);
            ItemMeta meta = info.getItemMeta();
            meta.displayName(Component.text("Max Rank!", STAR_COLOR)
                    .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(Component.text("You already have the highest rank.", GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            info.setItemMeta(meta);
            inv.setItem(13, info);
        } else {
            PremiumRank next = PremiumRank.fromLevel(current.getLevel() + 1);
            int upgradeCost = next.getStarsCost() - current.getStarsCost();
            if (upgradeCost < 0) upgradeCost = next.getStarsCost();

            ItemStack icon = new ItemStack(Material.ANVIL);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text("Upgrade: ", WHITE)
                    .append(current.getColoredDisplayName())
                    .append(Component.text(" → ", GRAY))
                    .append(next.getColoredDisplayName())
                    .decoration(TextDecoration.ITALIC, false));

            boolean canAfford = economy.hasStars(player.getUniqueId(), upgradeCost);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Upgrade cost: " + upgradeCost + " " + EconomyManager.STARS_ICON,
                    canAfford ? GREEN : RED).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("(Full price minus your current rank)", GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text(canAfford ? "Click to upgrade!" : "Not enough Stars",
                    canAfford ? YELLOW : RED).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(13, icon);
        }

        inv.setItem(18, backButton());
        inv.setItem(22, balanceIcon(player));
        player.openInventory(inv);
    }

    private void openCrateKeys(Player player) {
        Inventory inv = createInventory(54, "Star Shop - Crate Keys");
        fillRow(inv, 5);

        int slot = 0;
        for (int t = 0; t < KEY_TYPES.length; t++) {
            CrateType type = KEY_TYPES[t];
            for (int b = 0; b < KEY_BUNDLES.length; b++) {
                if (slot >= 45) break;
                int col = slot % 9;
                if (col == 8) { slot++; col = slot % 9; }

                int amount = KEY_BUNDLES[b];
                int cost = KEY_PRICES[t][b];

                ItemStack icon = crateManager.createKey(type, Math.min(amount, 64));
                ItemMeta meta = icon.getItemMeta();

                boolean canAfford = economy.hasStars(player.getUniqueId(), cost);
                List<Component> lore = meta.lore();
                if (lore == null) lore = new ArrayList<>();
                else lore = new ArrayList<>(lore);
                lore.add(Component.empty());
                lore.add(Component.text("Bundle: x" + amount, WHITE).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Price: " + cost + " " + EconomyManager.STARS_ICON,
                        canAfford ? GREEN : RED).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(canAfford ? "Click to buy!" : "Not enough Stars",
                        canAfford ? YELLOW : RED).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                icon.setItemMeta(meta);
                inv.setItem(slot, icon);
                slot++;
            }
            slot++;
        }

        inv.setItem(45, backButton());
        inv.setItem(49, balanceIcon(player));
        player.openInventory(inv);
    }

    private void openProtection(Player player) {
        Inventory inv = createInventory(27, "Star Shop - Protection Packages");
        fillBorder(inv);

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < PROTECTION_RADII.length; i++) {
            int radius = PROTECTION_RADII[i];
            String label = PROTECTION_LABELS[i];

            ItemStack icon = new ItemStack(Material.HEART_OF_THE_SEA);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(label + " Protection", CYAN)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            boolean canAfford = economy.hasStars(player.getUniqueId(), PROTECTION_PRICE);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Radius: " + radius + " blocks", GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Gives a Protection Expansion Token", GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Price: " + PROTECTION_PRICE + " " + EconomyManager.STARS_ICON,
                    canAfford ? GREEN : RED).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(canAfford ? "Click to buy!" : "Not enough Stars",
                    canAfford ? YELLOW : RED).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slots[i], icon);
        }

        inv.setItem(18, backButton());
        inv.setItem(22, balanceIcon(player));
        player.openInventory(inv);
    }

    private void openBuffs(Player player) {
        Inventory inv = createInventory(54, "Star Shop - Server Buffs");
        fillRow(inv, 5);

        BuffType[] buffs = BuffType.values();
        for (int i = 0; i < buffs.length && i < 45; i++) {
            BuffType buff = buffs[i];
            boolean active = buffManager.hasBuff(player.getUniqueId(), buff);

            ItemStack icon = new ItemStack(buff.getIcon());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(buff.getDisplayName(), buff.getColor())
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(buff.getDescription(), GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Duration: 12 hours", GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());

            if (active) {
                lore.add(Component.text("Already active!", GREEN).decoration(TextDecoration.ITALIC, false));
            } else {
                boolean canAfford = economy.hasStars(player.getUniqueId(), BUFF_PRICE);
                lore.add(Component.text("Price: " + BUFF_PRICE + " " + EconomyManager.STARS_ICON,
                        canAfford ? GREEN : RED).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text(canAfford ? "Click to buy!" : "Not enough Stars",
                        canAfford ? YELLOW : RED).decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(i, icon);
        }

        inv.setItem(45, backButton());
        inv.setItem(49, balanceIcon(player));
        player.openInventory(inv);
    }

    // ── Click handler ──

    public void handleClick(Player player, int slot) {
        Category current = sessions.get(player.getUniqueId());
        if (current == null) return;

        if (current == Category.MAIN) {
            switch (slot) {
                case 11 -> openCategory(player, Category.RANKS);
                case 12 -> openCategory(player, Category.RANK_UPGRADE);
                case 13 -> openCategory(player, Category.CRATE_KEYS);
                case 14 -> openCategory(player, Category.PROTECTION_PACKAGES);
                case 15 -> openCategory(player, Category.SERVER_BUFFS);
            }
            return;
        }

        if (slot == 45 || slot == 18 && isSmallPage(current)) {
            openMainMenu(player);
            return;
        }

        switch (current) {
            case RANKS -> handleRankClick(player, slot);
            case RANK_UPGRADE -> handleRankUpgradeClick(player, slot);
            case CRATE_KEYS -> handleKeyClick(player, slot);
            case PROTECTION_PACKAGES -> handleProtectionClick(player, slot);
            case SERVER_BUFFS -> handleBuffClick(player, slot);
            default -> {}
        }
    }

    private boolean isSmallPage(Category cat) {
        return cat == Category.RANKS || cat == Category.RANK_UPGRADE || cat == Category.PROTECTION_PACKAGES;
    }

    private void handleRankClick(Player player, int slot) {
        int[] slots = {10, 11, 12, 14, 15, 16};
        int index = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) { index = i; break; }
        }
        if (index < 0) return;

        PremiumRank rank = PURCHASABLE_RANKS[index];
        PremiumRank current = premiumManager.getPlayerRank(player.getUniqueId());
        if (current.getLevel() >= rank.getLevel()) {
            Msg.error(player, "You already have this rank or higher!");
            return;
        }

        int cost = rank.getStarsCost();
        if (!economy.hasStars(player.getUniqueId(), cost)) {
            Msg.error(player, "Not enough Stars! Need " + cost + " " + EconomyManager.STARS_ICON);
            return;
        }

        premiumManager.buyRank(player, rank, "stars");
        player.closeInventory();
    }

    private void handleRankUpgradeClick(Player player, int slot) {
        if (slot != 13) return;

        UUID uuid = player.getUniqueId();
        PremiumRank current = premiumManager.getPlayerRank(uuid);
        if (current == PremiumRank.GALAXY) return;

        PremiumRank next = PremiumRank.fromLevel(current.getLevel() + 1);
        int upgradeCost = next.getStarsCost() - current.getStarsCost();
        if (upgradeCost < 0) upgradeCost = next.getStarsCost();

        if (!economy.hasStars(uuid, upgradeCost)) {
            Msg.error(player, "Not enough Stars! Need " + upgradeCost + " " + EconomyManager.STARS_ICON);
            return;
        }

        economy.removeStars(uuid, upgradeCost);
        String username = player.getName().toLowerCase();
        premiumManager.getAdminManager().setPremiumLevel(username, next.getLevel()).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        premiumManager.getAdminManager().loadPlayer(uuid, player.getName());
                        Msg.success(player, "Upgraded to " + next.getDisplayName() + " rank!");
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    }
                }));
        player.closeInventory();
    }

    private void handleKeyClick(Player player, int slot) {
        if (slot >= 45) return;

        int pos = 0;
        for (int t = 0; t < KEY_TYPES.length; t++) {
            for (int b = 0; b < KEY_BUNDLES.length; b++) {
                int col = pos % 9;
                if (col == 8) pos++;
                if (pos == slot) {
                    int amount = KEY_BUNDLES[b];
                    int cost = KEY_PRICES[t][b];
                    purchaseItem(player, crateManager.createKey(KEY_TYPES[t], amount),
                            KEY_TYPES[t].getDisplayName() + " Key x" + amount, cost, Category.CRATE_KEYS);
                    return;
                }
                pos++;
            }
            pos++;
        }
    }

    private void handleProtectionClick(Player player, int slot) {
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int index = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) { index = i; break; }
        }
        if (index < 0) return;

        int radius = PROTECTION_RADII[index];
        purchaseItem(player, voucherManager.createProtectionToken(radius),
                PROTECTION_LABELS[index] + " Protection Token", PROTECTION_PRICE, Category.PROTECTION_PACKAGES);
    }

    private void handleBuffClick(Player player, int slot) {
        BuffType[] buffs = BuffType.values();
        if (slot < 0 || slot >= buffs.length) return;

        BuffType buff = buffs[slot];
        if (buffManager.hasBuff(player.getUniqueId(), buff)) {
            Msg.error(player, buff.getDisplayName() + " is already active!");
            return;
        }

        if (!economy.hasStars(player.getUniqueId(), BUFF_PRICE)) {
            Msg.error(player, "Not enough Stars! Need " + BUFF_PRICE + " " + EconomyManager.STARS_ICON);
            return;
        }

        economy.removeStars(player.getUniqueId(), BUFF_PRICE);
        buffManager.activateBuff(player, buff);
        openBuffs(player);
    }

    private void purchaseItem(Player player, ItemStack item, String displayName, int cost, Category reopenCat) {
        if (!economy.hasStars(player.getUniqueId(), cost)) {
            Msg.error(player, "Not enough Stars! Need " + cost + " " + EconomyManager.STARS_ICON);
            return;
        }

        economy.removeStars(player.getUniqueId(), cost);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        Msg.success(player, "Purchased " + displayName + " for " + cost + " " + EconomyManager.STARS_ICON + "!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
        openCategory(player, reopenCat);
    }

    // ── Session management ──

    public void removeSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    // ── GUI helpers ──

    private Inventory createInventory(int size, String title) {
        StarShopHolder holder = new StarShopHolder();
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text(title, STAR_COLOR).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);
        return inv;
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
    }

    private void fillRow(Inventory inv, int row) {
        ItemStack filler = filler();
        int start = row * 9;
        for (int i = start; i < start + 9 && i < inv.getSize(); i++) inv.setItem(i, filler);
    }
}
