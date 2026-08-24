package com.starlightuniverse.shop;

import com.starlightuniverse.economy.EconomyManager;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.util.*;

import static com.starlightuniverse.shop.ShopCategory.*;

public class ShopManager {

    static final int BULK_THRESHOLD = 320;
    static final double BULK_DISCOUNT = 0.10;
    static final int ITEMS_PER_PAGE = 45;
    static final int MAX_QUANTITY = 2304;

    private static final int[] DEAL_SLOTS = {2, 4, 6};
    private static final int[] CAT_ROW1 = {18, 19, 20, 21, 22, 23, 24, 25, 26};
    private static final int[] CAT_ROW2 = {28, 30, 32, 34};

    private static final List<ShopItem> ALL_ITEMS = new ArrayList<>();
    private static final Map<ShopCategory, List<ShopItem>> BY_CATEGORY = new EnumMap<>(ShopCategory.class);

    static {
        add(Material.DIRT, 1, BUILDING);
        add(Material.GRASS_BLOCK, 3, BUILDING);
        add(Material.COBBLESTONE, 2, BUILDING);
        add(Material.STONE, 5, BUILDING);
        add(Material.SMOOTH_STONE, 6, BUILDING);
        add(Material.STONE_BRICKS, 8, BUILDING);
        add(Material.DEEPSLATE, 4, BUILDING);
        add(Material.DEEPSLATE_BRICKS, 10, BUILDING);
        add(Material.BRICKS, 10, BUILDING);
        add(Material.SANDSTONE, 5, BUILDING);
        add(Material.RED_SANDSTONE, 5, BUILDING);
        add(Material.OBSIDIAN, 50, BUILDING);
        add(Material.GLASS, 5, BUILDING);
        add(Material.TERRACOTTA, 4, BUILDING);
        add(Material.WHITE_CONCRETE, 4, BUILDING);
        add(Material.BLACK_CONCRETE, 4, BUILDING);
        add(Material.OAK_PLANKS, 3, BUILDING);
        add(Material.SPRUCE_PLANKS, 3, BUILDING);
        add(Material.BIRCH_PLANKS, 3, BUILDING);
        add(Material.DARK_OAK_PLANKS, 3, BUILDING);
        add(Material.CHERRY_PLANKS, 3, BUILDING);
        add(Material.CRIMSON_PLANKS, 3, BUILDING);
        add(Material.WARPED_PLANKS, 3, BUILDING);
        add(Material.OAK_LOG, 3, BUILDING);
        add(Material.SPRUCE_LOG, 3, BUILDING);
        add(Material.SAND, 2, BUILDING);
        add(Material.GRAVEL, 2, BUILDING);
        add(Material.GLOWSTONE, 15, BUILDING);
        add(Material.PRISMARINE, 20, BUILDING);
        add(Material.SEA_LANTERN, 30, BUILDING);
        add(Material.QUARTZ_BLOCK, 15, BUILDING);
        add(Material.PURPUR_BLOCK, 15, BUILDING);
        add(Material.END_STONE_BRICKS, 12, BUILDING);
        add(Material.BLACKSTONE, 5, BUILDING);
        add(Material.MOSS_BLOCK, 5, BUILDING);
        add(Material.MUD_BRICKS, 5, BUILDING);
        add(Material.TUFF, 3, BUILDING);
        add(Material.CALCITE, 5, BUILDING);

        add(Material.LANTERN, 10, DECORATION);
        add(Material.SOUL_LANTERN, 15, DECORATION);
        add(Material.CAMPFIRE, 15, DECORATION);
        add(Material.SOUL_CAMPFIRE, 20, DECORATION);
        add(Material.PAINTING, 10, DECORATION);
        add(Material.ITEM_FRAME, 15, DECORATION);
        add(Material.GLOW_ITEM_FRAME, 25, DECORATION);
        add(Material.ARMOR_STAND, 20, DECORATION);
        add(Material.IRON_CHAIN, 10, DECORATION);
        add(Material.BELL, 100, DECORATION);
        add(Material.CANDLE, 5, DECORATION);
        add(Material.FLOWER_POT, 5, DECORATION);
        add(Material.SCAFFOLDING, 5, DECORATION);
        add(Material.LADDER, 3, DECORATION);
        add(Material.BOOKSHELF, 20, DECORATION);
        add(Material.LECTERN, 25, DECORATION);
        add(Material.HAY_BLOCK, 8, DECORATION);
        add(Material.JACK_O_LANTERN, 10, DECORATION);
        add(Material.LIGHTNING_ROD, 30, DECORATION);
        add(Material.DECORATED_POT, 15, DECORATION);

        add(Material.REDSTONE, 3, REDSTONE);
        add(Material.REDSTONE_BLOCK, 25, REDSTONE);
        add(Material.REDSTONE_TORCH, 3, REDSTONE);
        add(Material.REPEATER, 10, REDSTONE);
        add(Material.COMPARATOR, 15, REDSTONE);
        add(Material.PISTON, 20, REDSTONE);
        add(Material.STICKY_PISTON, 30, REDSTONE);
        add(Material.DISPENSER, 20, REDSTONE);
        add(Material.DROPPER, 15, REDSTONE);
        add(Material.OBSERVER, 25, REDSTONE);
        add(Material.HOPPER, 30, REDSTONE);
        add(Material.REDSTONE_LAMP, 15, REDSTONE);
        add(Material.TNT, 50, REDSTONE);
        add(Material.TARGET, 20, REDSTONE);
        add(Material.DAYLIGHT_DETECTOR, 20, REDSTONE);

        add(Material.MINECART, 20, TRANSPORT);
        add(Material.CHEST_MINECART, 25, TRANSPORT);
        add(Material.HOPPER_MINECART, 35, TRANSPORT);
        add(Material.RAIL, 5, TRANSPORT);
        add(Material.POWERED_RAIL, 25, TRANSPORT);
        add(Material.DETECTOR_RAIL, 15, TRANSPORT);
        add(Material.ACTIVATOR_RAIL, 15, TRANSPORT);
        add(Material.SADDLE, 75, TRANSPORT);
        add(Material.OAK_BOAT, 10, TRANSPORT);
        add(Material.LEAD, 15, TRANSPORT);

        add(Material.COOKED_CHICKEN, 6, FOOD);
        add(Material.COOKED_BEEF, 8, FOOD);
        add(Material.COOKED_PORKCHOP, 8, FOOD);
        add(Material.COOKED_MUTTON, 7, FOOD);
        add(Material.COOKED_COD, 5, FOOD);
        add(Material.COOKED_SALMON, 7, FOOD);
        add(Material.BREAD, 5, FOOD);
        add(Material.BAKED_POTATO, 4, FOOD);
        add(Material.PUMPKIN_PIE, 8, FOOD);
        add(Material.CAKE, 25, FOOD);
        add(Material.COOKIE, 2, FOOD);
        add(Material.GOLDEN_APPLE, 500, FOOD);
        add(Material.ENCHANTED_GOLDEN_APPLE, 25_000, FOOD);
        add(Material.GOLDEN_CARROT, 50, FOOD);
        add(Material.MELON_SLICE, 2, FOOD);

        add(Material.DIAMOND_PICKAXE, 7_500, TOOLS);
        add(Material.DIAMOND_SHOVEL, 7_500, TOOLS);
        add(Material.DIAMOND_AXE, 8_000, TOOLS);
        add(Material.DIAMOND_HOE, 7_500, TOOLS);
        add(Material.NETHERITE_PICKAXE, 50_000, TOOLS);
        add(Material.NETHERITE_SHOVEL, 50_000, TOOLS);
        add(Material.NETHERITE_AXE, 55_000, TOOLS);
        add(Material.NETHERITE_HOE, 50_000, TOOLS);
        add(Material.SHEARS, 10, TOOLS);
        add(Material.FLINT_AND_STEEL, 15, TOOLS);
        add(Material.FISHING_ROD, 15, TOOLS);
        add(Material.COMPASS, 20, TOOLS);
        add(Material.SPYGLASS, 30, TOOLS);
        add(Material.NAME_TAG, 100, TOOLS);
        add(Material.BRUSH, 20, TOOLS);

        add(Material.DIAMOND_SWORD, 10_000, WEAPONS);
        add(Material.NETHERITE_SWORD, 75_000, WEAPONS);
        add(Material.BOW, 30, WEAPONS);
        add(Material.CROSSBOW, 50, WEAPONS);
        add(Material.TRIDENT, 20_000, WEAPONS);
        add(Material.MACE, 350_000, WEAPONS);
        add(Material.ARROW, 2, WEAPONS);
        add(Material.SPECTRAL_ARROW, 5, WEAPONS);
        add(Material.SHIELD, 30, WEAPONS);
        add(Material.TOTEM_OF_UNDYING, 15_000, WEAPONS);

        add(Material.DIAMOND_HELMET, 7_500, ARMOR);
        add(Material.DIAMOND_CHESTPLATE, 10_000, ARMOR);
        add(Material.DIAMOND_LEGGINGS, 9_000, ARMOR);
        add(Material.DIAMOND_BOOTS, 7_500, ARMOR);
        add(Material.NETHERITE_HELMET, 50_000, ARMOR);
        add(Material.NETHERITE_CHESTPLATE, 75_000, ARMOR);
        add(Material.NETHERITE_LEGGINGS, 65_000, ARMOR);
        add(Material.NETHERITE_BOOTS, 50_000, ARMOR);
        add(Material.ELYTRA, 150_000, ARMOR);
        add(Material.TURTLE_HELMET, 5_000, ARMOR);
        add(Material.LEATHER_HORSE_ARMOR, 25, ARMOR);
        add(Material.DIAMOND_HORSE_ARMOR, 500, ARMOR);

        add(Material.BREWING_STAND, 50, BREWING);
        add(Material.CAULDRON, 30, BREWING);
        add(Material.GLASS_BOTTLE, 3, BREWING);
        add(Material.BLAZE_ROD, 150, BREWING);
        add(Material.BLAZE_POWDER, 80, BREWING);
        add(Material.NETHER_WART, 10, BREWING);
        add(Material.SPIDER_EYE, 5, BREWING);
        add(Material.FERMENTED_SPIDER_EYE, 10, BREWING);
        add(Material.GHAST_TEAR, 100, BREWING);
        add(Material.MAGMA_CREAM, 25, BREWING);
        add(Material.SUGAR, 2, BREWING);
        add(Material.GLISTERING_MELON_SLICE, 20, BREWING);
        add(Material.RABBIT_FOOT, 50, BREWING);
        add(Material.PHANTOM_MEMBRANE, 30, BREWING);
        add(Material.DRAGON_BREATH, 200, BREWING);

        add(Material.IRON_INGOT, 15, MATERIALS);
        add(Material.GOLD_INGOT, 25, MATERIALS);
        add(Material.DIAMOND, 500, MATERIALS);
        add(Material.EMERALD, 300, MATERIALS);
        add(Material.NETHERITE_SCRAP, 2_000, MATERIALS);
        add(Material.NETHERITE_INGOT, 10_000, MATERIALS);
        add(Material.ANCIENT_DEBRIS, 3_000, MATERIALS);
        add(Material.COPPER_INGOT, 8, MATERIALS);
        add(Material.LAPIS_LAZULI, 10, MATERIALS);
        add(Material.COAL, 3, MATERIALS);
        add(Material.QUARTZ, 5, MATERIALS);
        add(Material.AMETHYST_SHARD, 15, MATERIALS);
        add(Material.BONE, 3, MATERIALS);
        add(Material.GUNPOWDER, 10, MATERIALS);
        add(Material.SLIME_BALL, 10, MATERIALS);
        add(Material.ENDER_PEARL, 100, MATERIALS);
        add(Material.SHULKER_SHELL, 1_000, MATERIALS);
        add(Material.NETHER_STAR, 25_000, MATERIALS);
        add(Material.STRING, 3, MATERIALS);
        add(Material.LEATHER, 5, MATERIALS);

        add(Material.WHITE_DYE, 3, DYES);
        add(Material.ORANGE_DYE, 3, DYES);
        add(Material.MAGENTA_DYE, 3, DYES);
        add(Material.LIGHT_BLUE_DYE, 3, DYES);
        add(Material.YELLOW_DYE, 3, DYES);
        add(Material.LIME_DYE, 3, DYES);
        add(Material.PINK_DYE, 3, DYES);
        add(Material.GRAY_DYE, 3, DYES);
        add(Material.LIGHT_GRAY_DYE, 3, DYES);
        add(Material.CYAN_DYE, 3, DYES);
        add(Material.PURPLE_DYE, 3, DYES);
        add(Material.BLUE_DYE, 3, DYES);
        add(Material.BROWN_DYE, 3, DYES);
        add(Material.GREEN_DYE, 3, DYES);
        add(Material.RED_DYE, 3, DYES);
        add(Material.BLACK_DYE, 3, DYES);

        add(Material.ZOMBIE_SPAWN_EGG, 3_000, SPAWN_EGGS);
        add(Material.SKELETON_SPAWN_EGG, 3_000, SPAWN_EGGS);
        add(Material.SPIDER_SPAWN_EGG, 3_000, SPAWN_EGGS);
        add(Material.CREEPER_SPAWN_EGG, 5_000, SPAWN_EGGS);
        add(Material.ENDERMAN_SPAWN_EGG, 7_500, SPAWN_EGGS);
        add(Material.BLAZE_SPAWN_EGG, 10_000, SPAWN_EGGS);
        add(Material.GHAST_SPAWN_EGG, 10_000, SPAWN_EGGS);
        add(Material.WITCH_SPAWN_EGG, 7_500, SPAWN_EGGS);
        add(Material.VILLAGER_SPAWN_EGG, 5_000, SPAWN_EGGS);
        add(Material.IRON_GOLEM_SPAWN_EGG, 15_000, SPAWN_EGGS);
        add(Material.COW_SPAWN_EGG, 3_000, SPAWN_EGGS);
        add(Material.SHEEP_SPAWN_EGG, 3_000, SPAWN_EGGS);
        add(Material.PIG_SPAWN_EGG, 3_000, SPAWN_EGGS);
        add(Material.WOLF_SPAWN_EGG, 5_000, SPAWN_EGGS);
        add(Material.HORSE_SPAWN_EGG, 5_000, SPAWN_EGGS);
        add(Material.AXOLOTL_SPAWN_EGG, 10_000, SPAWN_EGGS);

        add(Material.EXPERIENCE_BOTTLE, 50, MISC);
        add(Material.FIREWORK_ROCKET, 10, MISC);
        add(Material.BONE_MEAL, 3, MISC);
        add(Material.BUCKET, 10, MISC);
        add(Material.CHEST, 5, MISC);
        add(Material.ENDER_CHEST, 500, MISC);
        add(Material.END_CRYSTAL, 200, MISC);
        add(Material.BEACON, 30_000, MISC);
        add(Material.CONDUIT, 5_000, MISC);
        add(Material.RESPAWN_ANCHOR, 500, MISC);

        for (ShopItem item : ALL_ITEMS) {
            BY_CATEGORY.computeIfAbsent(item.category(), k -> new ArrayList<>()).add(item);
        }
    }

    private static void add(Material material, int price, ShopCategory category) {
        ALL_ITEMS.add(new ShopItem(material, price, category));
    }

    public int getShopPrice(Material material) {
        for (ShopItem item : ALL_ITEMS) {
            if (item.material() == material) return item.price();
        }
        return -1;
    }

    public record DailyDeal(ShopItem item, int discountPercent) {}

    private final JavaPlugin plugin;
    private final EconomyManager economy;
    private final Map<UUID, ShopSession> sessions = new HashMap<>();
    private final List<DailyDeal> dailyDeals = new ArrayList<>();
    private long lastDealEpochDay;

    public ShopManager(JavaPlugin plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
        refreshDailyDeals();
    }

    private void refreshDailyDeals() {
        long today = LocalDate.now().toEpochDay();
        if (today == lastDealEpochDay && !dailyDeals.isEmpty()) return;
        lastDealEpochDay = today;
        dailyDeals.clear();
        Random rng = new Random(today * 31337);
        List<ShopItem> pool = new ArrayList<>(ALL_ITEMS);
        for (int i = 0; i < 3 && !pool.isEmpty(); i++) {
            int idx = rng.nextInt(pool.size());
            ShopItem item = pool.remove(idx);
            int discount = 20 + rng.nextInt(21);
            dailyDeals.add(new DailyDeal(item, discount));
        }
    }

    public void openMainMenu(Player player) {
        refreshDailyDeals();
        ShopSession session = new ShopSession(ShopSession.State.MAIN_MENU);
        sessions.put(player.getUniqueId(), session);

        Inventory inv = createInventory(54, "Shop");

        fillBorder(inv);

        for (int i = 0; i < dailyDeals.size(); i++) {
            DailyDeal deal = dailyDeals.get(i);
            ShopItem si = deal.item();
            int discounted = (int) (si.price() * (1.0 - deal.discountPercent() / 100.0));
            ItemStack icon = new ItemStack(si.material());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(formatMaterial(si.material()), TextColor.color(0xFFD700))
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("DAILY DEAL!", TextColor.color(0xFF5555))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("-" + deal.discountPercent() + "% OFF", TextColor.color(0x55FF55))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Was: $" + EconomyManager.format(si.price()), TextColor.color(0xFF5555))
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.STRIKETHROUGH, true));
            lore.add(Component.text("Now: $" + EconomyManager.format(discounted), TextColor.color(0x55FF55))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Click to buy!", TextColor.color(0xAAAAAA))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(DEAL_SLOTS[i], icon);
        }

        ShopCategory[] cats = ShopCategory.values();
        for (int i = 0; i < CAT_ROW1.length && i < cats.length; i++) {
            inv.setItem(CAT_ROW1[i], createCategoryIcon(cats[i]));
        }
        for (int i = 0; i < CAT_ROW2.length && (9 + i) < cats.length; i++) {
            inv.setItem(CAT_ROW2[i], createCategoryIcon(cats[9 + i]));
        }

        player.openInventory(inv);
    }

    public void openCategory(Player player, ShopCategory category, int page) {
        List<ShopItem> items = BY_CATEGORY.getOrDefault(category, List.of());
        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        ShopSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new ShopSession(ShopSession.State.CATEGORY));
        session.state = ShopSession.State.CATEGORY;
        session.category = category;
        session.page = page;

        Inventory inv = createInventory(54, "Shop - " + category.displayName());
        fillRow(inv, 5);

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());
        for (int i = start; i < end; i++) {
            ShopItem si = items.get(i);
            inv.setItem(i - start, createShopItemIcon(si));
        }

        inv.setItem(45, createGuiItem(Material.BARRIER, "Back", 0xFF5555));
        if (page > 0) {
            inv.setItem(48, createGuiItem(Material.ARROW, "Previous Page", 0xFFFF55));
        }
        inv.setItem(49, createGuiItem(Material.PAPER,
                "Page " + (page + 1) + "/" + totalPages, 0xAAAAAA));
        if (page < totalPages - 1) {
            inv.setItem(50, createGuiItem(Material.ARROW, "Next Page", 0xFFFF55));
        }

        player.openInventory(inv);
    }

    public void openSearchResults(Player player, String query) {
        String lower = query.toLowerCase();
        List<ShopItem> results = ALL_ITEMS.stream()
                .filter(si -> formatMaterial(si.material()).toLowerCase().contains(lower))
                .toList();

        ShopSession session = new ShopSession(ShopSession.State.SEARCH_RESULTS);
        session.searchResults = results;
        session.page = 0;
        sessions.put(player.getUniqueId(), session);

        if (results.isEmpty()) {
            Msg.error(player, "No items found for '" + query + "'.");
            return;
        }

        openSearchPage(player, session);
    }

    private void openSearchPage(Player player, ShopSession session) {
        List<ShopItem> results = session.searchResults;
        int totalPages = Math.max(1, (int) Math.ceil(results.size() / (double) ITEMS_PER_PAGE));
        int page = Math.max(0, Math.min(session.page, totalPages - 1));
        session.page = page;

        Inventory inv = createInventory(54, "Shop - Search Results");
        fillRow(inv, 5);

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, results.size());
        for (int i = start; i < end; i++) {
            ShopItem si = results.get(i);
            inv.setItem(i - start, createShopItemIcon(si));
        }

        inv.setItem(45, createGuiItem(Material.BARRIER, "Back", 0xFF5555));
        if (page > 0) {
            inv.setItem(48, createGuiItem(Material.ARROW, "Previous Page", 0xFFFF55));
        }
        inv.setItem(49, createGuiItem(Material.PAPER,
                "Page " + (page + 1) + "/" + totalPages + " (" + results.size() + " results)", 0xAAAAAA));
        if (page < totalPages - 1) {
            inv.setItem(50, createGuiItem(Material.ARROW, "Next Page", 0xFFFF55));
        }

        player.openInventory(inv);
    }

    public void openBuyScreen(Player player, ShopItem item, int dealDiscount, ShopSession.State returnTo) {
        ShopSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new ShopSession(ShopSession.State.BUY));
        session.state = ShopSession.State.BUY;
        session.selectedItem = item;
        session.quantity = 1;
        session.dealDiscount = dealDiscount;
        session.returnTo = returnTo;

        Inventory inv = createInventory(54, "Shop - Buy");
        fillAll(inv);

        inv.setItem(9, createQuantityButton(Material.LIME_STAINED_GLASS_PANE, "+1", 0x55FF55, 1));
        inv.setItem(10, createQuantityButton(Material.LIME_STAINED_GLASS_PANE, "+10", 0x55FF55, 10));
        inv.setItem(11, createQuantityButton(Material.LIME_STAINED_GLASS_PANE, "+64", 0x55FF55, 64));
        inv.setItem(15, createQuantityButton(Material.RED_STAINED_GLASS_PANE, "-1", 0xFF5555, 1));
        inv.setItem(16, createQuantityButton(Material.RED_STAINED_GLASS_PANE, "-10", 0xFF5555, 10));
        inv.setItem(17, createQuantityButton(Material.RED_STAINED_GLASS_PANE, "-64", 0xFF5555, 64));

        inv.setItem(47, createGuiItem(Material.LIME_STAINED_GLASS_PANE, "Confirm Purchase", 0x55FF55));
        inv.setItem(51, createGuiItem(Material.RED_STAINED_GLASS_PANE, "Cancel", 0xFF5555));

        updateBuyDisplay(inv, session);
        player.openInventory(inv);
    }

    private void updateBuyDisplay(Inventory inv, ShopSession session) {
        ItemStack display = new ItemStack(session.selectedItem.material(),
                Math.min(session.quantity, session.selectedItem.material().getMaxStackSize()));
        ItemMeta dm = display.getItemMeta();
        dm.displayName(Component.text(formatMaterial(session.selectedItem.material()), TextColor.color(0xFFFFFF))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> dl = new ArrayList<>();
        dl.add(Component.text("Quantity: " + session.quantity, TextColor.color(0xFFFF55))
                .decoration(TextDecoration.ITALIC, false));
        dm.lore(dl);
        display.setItemMeta(dm);
        inv.setItem(13, display);

        inv.setItem(31, createInfoItem(session));

        double total = calculateTotal(session);
        ItemStack confirm = createGuiItem(Material.LIME_STAINED_GLASS_PANE, "Confirm Purchase", 0x55FF55,
                "Total: $" + EconomyManager.format((long) total),
                "Click to buy!");
        inv.setItem(47, confirm);
    }

    private ItemStack createInfoItem(ShopSession session) {
        ShopItem si = session.selectedItem;
        double unitPrice = si.price();
        List<String> lines = new ArrayList<>();
        lines.add("Unit Price: $" + EconomyManager.format((long) unitPrice));

        if (session.dealDiscount > 0) {
            double discounted = unitPrice * (1.0 - session.dealDiscount / 100.0);
            lines.add("Daily Deal (-" + session.dealDiscount + "%): $" + EconomyManager.format((long) discounted));
            unitPrice = discounted;
        }

        lines.add("Quantity: " + session.quantity);
        double subtotal = unitPrice * session.quantity;

        if (session.quantity >= BULK_THRESHOLD) {
            lines.add("Subtotal: $" + EconomyManager.format((long) subtotal));
            double disc = subtotal * BULK_DISCOUNT;
            lines.add("Bulk Discount (10%): -$" + EconomyManager.format((long) disc));
            subtotal -= disc;
        }

        lines.add("");
        lines.add("Total: $" + EconomyManager.format((long) subtotal));

        return createGuiItem(Material.PAPER, formatMaterial(si.material()), 0xFFD700,
                lines.toArray(new String[0]));
    }

    double calculateTotal(ShopSession session) {
        double unitPrice = session.selectedItem.price();
        if (session.dealDiscount > 0) {
            unitPrice *= (1.0 - session.dealDiscount / 100.0);
        }
        double total = unitPrice * session.quantity;
        if (session.quantity >= BULK_THRESHOLD) {
            total *= (1.0 - BULK_DISCOUNT);
        }
        return total;
    }

    public void handleClick(Player player, int slot) {
        ShopSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        switch (session.state) {
            case MAIN_MENU -> handleMainMenuClick(player, session, slot);
            case CATEGORY -> handleCategoryClick(player, session, slot);
            case BUY -> handleBuyClick(player, session, slot);
            case SEARCH_RESULTS -> handleSearchClick(player, session, slot);
        }
    }

    private void handleMainMenuClick(Player player, ShopSession session, int slot) {
        for (int i = 0; i < DEAL_SLOTS.length; i++) {
            if (slot == DEAL_SLOTS[i] && i < dailyDeals.size()) {
                DailyDeal deal = dailyDeals.get(i);
                openBuyScreen(player, deal.item(), deal.discountPercent(), ShopSession.State.MAIN_MENU);
                return;
            }
        }

        ShopCategory[] cats = ShopCategory.values();
        for (int i = 0; i < CAT_ROW1.length && i < cats.length; i++) {
            if (slot == CAT_ROW1[i]) {
                openCategory(player, cats[i], 0);
                return;
            }
        }
        for (int i = 0; i < CAT_ROW2.length && (9 + i) < cats.length; i++) {
            if (slot == CAT_ROW2[i]) {
                openCategory(player, cats[9 + i], 0);
                return;
            }
        }
    }

    private void handleCategoryClick(Player player, ShopSession session, int slot) {
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            List<ShopItem> items = BY_CATEGORY.getOrDefault(session.category, List.of());
            int idx = session.page * ITEMS_PER_PAGE + slot;
            if (idx < items.size()) {
                openBuyScreen(player, items.get(idx), 0, ShopSession.State.CATEGORY);
            }
        } else if (slot == 45) {
            openMainMenu(player);
        } else if (slot == 48 && session.page > 0) {
            openCategory(player, session.category, session.page - 1);
        } else if (slot == 50) {
            openCategory(player, session.category, session.page + 1);
        }
    }

    private void handleSearchClick(Player player, ShopSession session, int slot) {
        if (slot >= 0 && slot < ITEMS_PER_PAGE && session.searchResults != null) {
            int idx = session.page * ITEMS_PER_PAGE + slot;
            if (idx < session.searchResults.size()) {
                openBuyScreen(player, session.searchResults.get(idx), 0, ShopSession.State.SEARCH_RESULTS);
            }
        } else if (slot == 45) {
            openMainMenu(player);
        } else if (slot == 48 && session.page > 0) {
            session.page--;
            openSearchPage(player, session);
        } else if (slot == 50) {
            session.page++;
            openSearchPage(player, session);
        }
    }

    private void handleBuyClick(Player player, ShopSession session, int slot) {
        Inventory inv = player.getOpenInventory().getTopInventory();

        switch (slot) {
            case 9 -> { session.quantity = Math.min(session.quantity + 1, MAX_QUANTITY); updateBuyDisplay(inv, session); }
            case 10 -> { session.quantity = Math.min(session.quantity + 10, MAX_QUANTITY); updateBuyDisplay(inv, session); }
            case 11 -> { session.quantity = Math.min(session.quantity + 64, MAX_QUANTITY); updateBuyDisplay(inv, session); }
            case 15 -> { session.quantity = Math.max(session.quantity - 1, 1); updateBuyDisplay(inv, session); }
            case 16 -> { session.quantity = Math.max(session.quantity - 10, 1); updateBuyDisplay(inv, session); }
            case 17 -> { session.quantity = Math.max(session.quantity - 64, 1); updateBuyDisplay(inv, session); }
            case 47 -> performPurchase(player, session);
            case 51 -> handleCancel(player, session);
        }
    }

    private void handleCancel(Player player, ShopSession session) {
        switch (session.returnTo) {
            case MAIN_MENU -> openMainMenu(player);
            case CATEGORY -> openCategory(player, session.category, session.page);
            case SEARCH_RESULTS -> openSearchPage(player, session);
            default -> openMainMenu(player);
        }
    }

    private void performPurchase(Player player, ShopSession session) {
        double total = calculateTotal(session);
        UUID uuid = player.getUniqueId();

        if (!economy.hasMoney(uuid, total)) {
            Msg.error(player, "Insufficient funds! You need $" + EconomyManager.format((long) total) + ".");
            return;
        }

        economy.removeMoney(uuid, total);

        Material mat = session.selectedItem.material();
        int remaining = session.quantity;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, mat.getMaxStackSize());
            ItemStack stack = new ItemStack(mat, stackSize);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= stackSize;
        }

        player.closeInventory();
        Msg.success(player, "Purchased " + session.quantity + "x " + formatMaterial(mat) +
                " for $" + EconomyManager.format((long) total) + "!");
    }

    public void removeSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    private Inventory createInventory(int size, String title) {
        ShopHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text(title, TextColor.color(0xFFD700))
                        .decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);
        return inv;
    }

    private void fillAll(Inventory inv) {
        ItemStack filler = createFiller();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private void fillBorder(Inventory inv) {
        ItemStack filler = createFiller();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        for (int i = 0; i < DEAL_SLOTS.length; i++) {
            inv.setItem(DEAL_SLOTS[i], null);
        }
        for (int s : CAT_ROW1) inv.setItem(s, null);
        for (int s : CAT_ROW2) inv.setItem(s, null);
    }

    private void fillRow(Inventory inv, int row) {
        ItemStack filler = createFiller();
        int start = row * 9;
        for (int i = start; i < start + 9 && i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        return filler;
    }

    private ItemStack createCategoryIcon(ShopCategory cat) {
        List<ShopItem> items = BY_CATEGORY.getOrDefault(cat, List.of());
        return createGuiItem(cat.icon(), cat.displayName(), cat.color(),
                items.size() + " items", "Click to browse!");
    }

    private ItemStack createShopItemIcon(ShopItem si) {
        ItemStack icon = new ItemStack(si.material());
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(formatMaterial(si.material()), TextColor.color(0xFFFFFF))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Price: $" + EconomyManager.format(si.price()), TextColor.color(0x55FF55))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Category: " + si.category().displayName(), TextColor.color(0xAAAAAA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to buy!", TextColor.color(0xFFFF55))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createQuantityButton(Material material, String label, int color, int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, TextColor.color(color))
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuiItem(Material material, String name, int color, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, TextColor.color(color))
                .decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> loreList = new ArrayList<>();
            for (String line : lore) {
                if (line.isEmpty()) {
                    loreList.add(Component.empty());
                } else {
                    loreList.add(Component.text(line, TextColor.color(0xAAAAAA))
                            .decoration(TextDecoration.ITALIC, false));
                }
            }
            meta.lore(loreList);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static String formatMaterial(Material material) {
        String name = material.name();
        if (name.equals("TNT")) return "TNT";
        StringBuilder sb = new StringBuilder();
        for (String word : name.split("_")) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
