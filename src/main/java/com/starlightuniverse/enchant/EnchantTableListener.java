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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantTableListener implements Listener {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);

    private static final long[] MONEY_COSTS = {1_000_000, 3_000_000, 8_000_000, 20_000_000, 75_000_000};
    private static final int[] XP_COSTS = {5_000, 15_000, 40_000, 100_000, 375_000};
    private static final EnchantRarity[] TIERS = EnchantRarity.values();

    private static final Material[] TIER_MATERIALS = {
            Material.GRAY_DYE, Material.LIME_DYE, Material.LAPIS_LAZULI,
            Material.PURPLE_DYE, Material.ORANGE_DYE
    };

    private static final int[] TIER_SLOTS = {11, 12, 13, 14, 15};

    private final EnchantManager enchantManager;
    private final EconomyManager economyManager;
    private final AuthManager authManager;

    public EnchantTableListener(EnchantManager enchantManager, EconomyManager economyManager, AuthManager authManager) {
        this.enchantManager = enchantManager;
        this.economyManager = economyManager;
        this.authManager = authManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEnchantTableClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.ENCHANTING_TABLE) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!authManager.isAuthenticated(player.getUniqueId())) return;
        if (player.isSneaking()) return;

        event.setCancelled(true);
        openEnchantTableGui(player);
    }

    private void openEnchantTableGui(Player player) {
        EnchantTableHolder holder = new EnchantTableHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("✦ Custom Enchanting Table ✦", TextColor.color(0xAA00AA))
                        .decoration(TextDecoration.BOLD, true));
        holder.setInventory(inv);

        ItemStack glass = createGlassPane();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

        ItemStack titleItem = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta titleMeta = titleItem.getItemMeta();
        titleMeta.displayName(Component.text("Custom Enchanting", GOLD)
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<Component> titleLore = new ArrayList<>();
        titleLore.add(Component.empty());
        titleLore.add(Component.text("Select a tier to roll a random", GRAY).decoration(TextDecoration.ITALIC, false));
        titleLore.add(Component.text("enchant book from that rarity.", GRAY).decoration(TextDecoration.ITALIC, false));
        titleLore.add(Component.empty());
        titleLore.add(Component.text("Priority: Money > XP Points", YELLOW).decoration(TextDecoration.ITALIC, false));
        titleMeta.lore(titleLore);
        titleItem.setItemMeta(titleMeta);
        inv.setItem(4, titleItem);

        UUID uuid = player.getUniqueId();
        double playerMoney = economyManager.getMoney(uuid);
        int playerXp = player.getTotalExperience();

        for (int i = 0; i < TIERS.length; i++) {
            inv.setItem(TIER_SLOTS[i], createTierItem(TIERS[i], i, playerMoney, playerXp));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
    }

    private ItemStack createTierItem(EnchantRarity rarity, int tierIndex, double playerMoney, int playerXp) {
        ItemStack item = new ItemStack(TIER_MATERIALS[tierIndex]);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(rarity.getBookName(), rarity.getColor())
                .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));

        long moneyCost = MONEY_COSTS[tierIndex];
        int xpCost = XP_COSTS[tierIndex];

        long enchantCount = Arrays.stream(CustomEnchant.values())
                .filter(e -> e.getRarity() == rarity)
                .count();

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Gives a random ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(rarity.getBookName(), rarity.getColor())));
        lore.add(Component.empty());
        lore.add(Component.text("Pool: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(enchantCount + " enchantments", WHITE)));
        lore.add(Component.empty());

        TextColor moneyColor = playerMoney >= moneyCost ? GREEN : RED;
        lore.add(Component.text("Cost: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text("$" + EconomyManager.format(moneyCost), moneyColor)));

        TextColor xpColor = playerXp >= xpCost ? GREEN : RED;
        lore.add(Component.text("  or: ", GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(EconomyManager.format(xpCost) + " XP", xpColor)));

        lore.add(Component.empty());
        if (playerMoney >= moneyCost || playerXp >= xpCost) {
            lore.add(Component.text("Click to roll!", YELLOW).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Not enough funds!", RED).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof EnchantTableHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        int tierIndex = -1;
        for (int i = 0; i < TIER_SLOTS.length; i++) {
            if (TIER_SLOTS[i] == slot) {
                tierIndex = i;
                break;
            }
        }
        if (tierIndex < 0) return;

        UUID uuid = player.getUniqueId();
        long moneyCost = MONEY_COSTS[tierIndex];
        int xpCost = XP_COSTS[tierIndex];
        EnchantRarity rarity = TIERS[tierIndex];

        boolean paid = false;
        String payMethod = "";

        if (economyManager.hasMoney(uuid, moneyCost)) {
            economyManager.removeMoney(uuid, moneyCost);
            paid = true;
            payMethod = "$" + EconomyManager.format(moneyCost);
        } else if (player.getTotalExperience() >= xpCost) {
            player.giveExp(-xpCost);
            paid = true;
            payMethod = EconomyManager.format(xpCost) + " XP";
        }

        if (!paid) {
            Msg.error(player, "You need $" + EconomyManager.format(moneyCost)
                    + " or " + EconomyManager.format(xpCost) + " XP!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        List<CustomEnchant> pool = Arrays.stream(CustomEnchant.values())
                .filter(e -> e.getRarity() == rarity)
                .toList();

        if (pool.isEmpty()) {
            Msg.error(player, "No enchantments available for this tier!");
            return;
        }

        CustomEnchant rolled = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        ItemStack book = enchantManager.createBook(rolled, 1);

        var remaining = player.getInventory().addItem(book);
        for (ItemStack leftover : remaining.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        player.closeInventory();
        Msg.success(player, "Rolled: " + rolled.getDisplayName() + " I! (-" + payMethod + ")");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    private ItemStack createGlassPane() {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" "));
        glass.setItemMeta(meta);
        return glass;
    }
}
