package com.starlightuniverse.auction;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.shop.ShopCategory;
import com.starlightuniverse.shop.ShopManager;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuctionManager {

    static final int ITEMS_PER_PAGE = 45;
    static final double LISTING_FEE_RATE = 0.05;
    static final long EXPIRE_DURATION_MS = 48L * 60 * 60 * 1000;

    private final JavaPlugin plugin;
    private final EconomyManager economy;
    private final DatabaseManager db;
    private final List<AuctionListing> listings = new CopyOnWriteArrayList<>();
    private final Set<Material> blacklist = new HashSet<>();
    private final Map<UUID, AuctionSession> sessions = new HashMap<>();
    private BukkitTask expiryTask;

    public AuctionManager(JavaPlugin plugin, EconomyManager economy, DatabaseManager db) {
        this.plugin = plugin;
        this.economy = economy;
        this.db = db;
    }

    public void initialize() {
        loadListings();
        loadBlacklist();
        startExpiryTask();
    }

    public void shutdown() {
        if (expiryTask != null) expiryTask.cancel();
    }

    private void loadListings() {
        db.queryAsync(conn -> {
            List<AuctionListing> loaded = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM su_auction_listings WHERE collected = 0")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        loaded.add(new AuctionListing(
                                rs.getInt("id"),
                                rs.getString("seller_username"),
                                rs.getString("item_data"),
                                rs.getString("item_material"),
                                rs.getInt("item_amount"),
                                rs.getInt("remaining_amount"),
                                rs.getDouble("price_per_unit"),
                                rs.getTimestamp("listed_date").getTime(),
                                rs.getTimestamp("expire_date").getTime(),
                                rs.getBoolean("active"),
                                rs.getBoolean("collected")
                        ));
                    }
                }
            }
            return loaded;
        }).thenAccept(loaded -> {
            if (loaded != null) {
                listings.clear();
                listings.addAll(loaded);
                plugin.getLogger().info("[SU] Loaded " + listings.size() + " auction listings.");
            }
        });
    }

    private void loadBlacklist() {
        db.queryAsync(conn -> {
            Set<Material> loaded = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT material FROM su_auction_blacklist")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            loaded.add(Material.valueOf(rs.getString("material")));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            return loaded;
        }).thenAccept(loaded -> {
            if (loaded != null) {
                blacklist.clear();
                blacklist.addAll(loaded);
            }
        });
    }

    private void startExpiryTask() {
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (AuctionListing listing : listings) {
                if (listing.isActive() && listing.getExpireDate() <= now) {
                    listing.setActive(false);
                    db.executeAsync(conn -> {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE su_auction_listings SET active = 0 WHERE id = ?")) {
                            ps.setInt(1, listing.getId());
                            ps.executeUpdate();
                        }
                    });
                }
            }
        }, 1200L, 1200L);
    }

    // --- Item Serialization ---

    static String serializeItem(ItemStack item) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeObject(item);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    static ItemStack deserializeItem(String data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
            return (ItemStack) bois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }

    // --- Category Mapping ---

    public static ShopCategory categorize(Material mat) {
        String name = mat.name();
        if (name.endsWith("_SWORD") || name.equals("BOW") || name.equals("CROSSBOW")
                || name.equals("TRIDENT") || name.equals("MACE") || name.endsWith("_ARROW")
                || name.equals("ARROW") || name.equals("SHIELD") || name.equals("TOTEM_OF_UNDYING"))
            return ShopCategory.WEAPONS;
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS") || name.equals("ELYTRA") || name.endsWith("_HORSE_ARMOR")
                || name.equals("TURTLE_HELMET"))
            return ShopCategory.ARMOR;
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
                || name.equals("SHEARS") || name.equals("FISHING_ROD") || name.equals("FLINT_AND_STEEL")
                || name.equals("SPYGLASS") || name.equals("BRUSH") || name.equals("COMPASS"))
            return ShopCategory.TOOLS;
        if (name.endsWith("_AXE") && !name.endsWith("_PICKAXE")) return ShopCategory.TOOLS;
        if (name.endsWith("_DYE")) return ShopCategory.DYES;
        if (name.endsWith("_SPAWN_EGG")) return ShopCategory.SPAWN_EGGS;
        if (mat.isEdible()) return ShopCategory.FOOD;
        if (name.contains("POTION") || name.equals("BREWING_STAND") || name.equals("CAULDRON")
                || name.equals("BLAZE_ROD") || name.equals("BLAZE_POWDER") || name.equals("NETHER_WART")
                || name.equals("DRAGON_BREATH") || name.equals("GLASS_BOTTLE"))
            return ShopCategory.BREWING;
        if (name.contains("REDSTONE") || name.contains("PISTON") || name.equals("DISPENSER")
                || name.equals("DROPPER") || name.equals("HOPPER") || name.equals("OBSERVER")
                || name.equals("REPEATER") || name.equals("COMPARATOR") || name.equals("TNT")
                || name.equals("TARGET") || name.equals("DAYLIGHT_DETECTOR"))
            return ShopCategory.REDSTONE;
        if (name.contains("RAIL") || name.contains("MINECART") || name.contains("BOAT")
                || name.equals("SADDLE") || name.equals("LEAD"))
            return ShopCategory.TRANSPORT;
        if (name.contains("INGOT") || name.equals("DIAMOND") || name.equals("EMERALD")
                || name.equals("COAL") || name.contains("LAPIS") || name.equals("QUARTZ")
                || name.contains("AMETHYST") || name.contains("DEBRIS") || name.contains("NETHERITE_SCRAP")
                || name.equals("BONE") || name.equals("GUNPOWDER") || name.equals("SLIME_BALL")
                || name.equals("ENDER_PEARL") || name.equals("STRING") || name.equals("LEATHER")
                || name.equals("SHULKER_SHELL") || name.equals("NETHER_STAR"))
            return ShopCategory.MATERIALS;
        if (name.contains("LANTERN") || name.contains("CAMPFIRE") || name.contains("CANDLE")
                || name.equals("PAINTING") || name.contains("ITEM_FRAME") || name.equals("ARMOR_STAND")
                || name.equals("FLOWER_POT") || name.equals("BELL") || name.equals("CHAIN"))
            return ShopCategory.DECORATION;
        if (mat.isBlock()) return ShopCategory.BUILDING;
        return ShopCategory.MISC;
    }

    // --- Listing Creation ---

    public void createListing(Player player, double pricePerUnit) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() == 0) {
            Msg.error(player, "You must hold an item to sell!");
            return;
        }
        if (blacklist.contains(hand.getType())) {
            Msg.error(player, "This item is blacklisted from the Auction House!");
            return;
        }
        if (pricePerUnit <= 0) {
            Msg.error(player, "Price must be greater than $0!");
            return;
        }

        int amount = hand.getAmount();
        double totalValue = pricePerUnit * amount;
        double fee = Math.ceil(totalValue * LISTING_FEE_RATE);

        if (!economy.hasMoney(player.getUniqueId(), fee)) {
            Msg.error(player, "Insufficient funds for the listing fee of $" + EconomyManager.format(fee) + "!");
            return;
        }

        ItemStack template = hand.clone();
        template.setAmount(1);
        String itemData = serializeItem(template);
        if (itemData == null) {
            Msg.error(player, "Failed to serialize item!");
            return;
        }

        economy.removeMoney(player.getUniqueId(), fee);
        player.getInventory().setItemInMainHand(null);

        String material = hand.getType().name();
        long now = System.currentTimeMillis();
        long expire = now + EXPIRE_DURATION_MS;
        String username = player.getName().toLowerCase();

        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_auction_listings (seller_username, item_data, item_material, item_amount, " +
                            "remaining_amount, price_per_unit, listed_date, expire_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, username);
                ps.setString(2, itemData);
                ps.setString(3, material);
                ps.setInt(4, amount);
                ps.setInt(5, amount);
                ps.setDouble(6, pricePerUnit);
                ps.setTimestamp(7, new java.sql.Timestamp(now));
                ps.setTimestamp(8, new java.sql.Timestamp(expire));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
            return -1;
        }).thenAccept(id -> {
            if (id != null && id > 0) {
                AuctionListing listing = new AuctionListing(id, username, itemData, material,
                        amount, amount, pricePerUnit, now, expire, true, false);
                listings.add(listing);
                Bukkit.getScheduler().runTask(plugin, () ->
                        Msg.success(player, "Listed " + amount + "x " + ShopManager.formatMaterial(hand.getType())
                                + " at $" + EconomyManager.format(pricePerUnit) + " each! Fee: $"
                                + EconomyManager.format(fee)));
            }
        });
    }

    // --- GUI Opening ---

    public void openBrowse(Player player) {
        AuctionSession session = new AuctionSession(AuctionSession.State.BROWSE);
        sessions.put(player.getUniqueId(), session);
        renderBrowse(player, session);
    }

    private void renderBrowse(Player player, AuctionSession session) {
        List<AuctionListing> filtered = getFilteredListings(session);
        session.filteredCache = filtered;

        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) ITEMS_PER_PAGE));
        session.page = Math.max(0, Math.min(session.page, totalPages - 1));

        String title = "Auction House";
        if (session.filterCategory != null) {
            title += " - " + session.filterCategory.displayName();
        }
        Inventory inv = createInventory(54, title);
        fillRow(inv, 5);

        int start = session.page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, filtered.size());
        for (int i = start; i < end; i++) {
            AuctionListing listing = filtered.get(i);
            inv.setItem(i - start, createListingIcon(listing));
        }

        inv.setItem(45, createGuiItem(Material.ENDER_CHEST, "My Listings", 0x55FFFF,
                "View your active listings"));
        inv.setItem(46, createGuiItem(Material.HOPPER, "Collect Items", 0xFFAA00,
                "Collect expired/cancelled items"));

        ItemStack sortItem = createGuiItem(Material.COMPARATOR, "Sort: " + session.sortMode.display(), 0xFFFF55,
                "Click to change sort order");
        inv.setItem(47, sortItem);

        if (session.page > 0) {
            inv.setItem(48, createGuiItem(Material.ARROW, "Previous Page", 0xFFFF55));
        }
        inv.setItem(49, createGuiItem(Material.PAPER,
                "Page " + (session.page + 1) + "/" + totalPages + " (" + filtered.size() + " listings)", 0xAAAAAA));
        if (session.page < totalPages - 1) {
            inv.setItem(50, createGuiItem(Material.ARROW, "Next Page", 0xFFFF55));
        }

        String filterLabel = session.filterCategory == null ? "All Categories" : session.filterCategory.displayName();
        inv.setItem(51, createGuiItem(Material.BOOK, "Filter: " + filterLabel, 0xAA00AA,
                "Click to choose a category"));

        player.openInventory(inv);
    }

    public void openBuyScreen(Player player, AuctionListing listing) {
        AuctionSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new AuctionSession(AuctionSession.State.BUY));
        session.state = AuctionSession.State.BUY;
        session.selectedListingId = listing.getId();
        session.buyQuantity = 1;

        Inventory inv = createInventory(54, "Auction House - Buy");
        fillAll(inv);

        inv.setItem(9, createQuantityButton(Material.LIME_STAINED_GLASS_PANE, "+1", 0x55FF55, 1));
        inv.setItem(10, createQuantityButton(Material.LIME_STAINED_GLASS_PANE, "+10", 0x55FF55, 10));
        inv.setItem(11, createQuantityButton(Material.LIME_STAINED_GLASS_PANE, "+64", 0x55FF55, 64));
        inv.setItem(15, createQuantityButton(Material.RED_STAINED_GLASS_PANE, "-1", 0xFF5555, 1));
        inv.setItem(16, createQuantityButton(Material.RED_STAINED_GLASS_PANE, "-10", 0xFF5555, 10));
        inv.setItem(17, createQuantityButton(Material.RED_STAINED_GLASS_PANE, "-64", 0xFF5555, 64));

        inv.setItem(47, createGuiItem(Material.LIME_STAINED_GLASS_PANE, "Confirm Purchase", 0x55FF55));
        inv.setItem(51, createGuiItem(Material.RED_STAINED_GLASS_PANE, "Cancel", 0xFF5555));

        updateBuyDisplay(inv, session, listing);
        player.openInventory(inv);
    }

    private void updateBuyDisplay(Inventory inv, AuctionSession session, AuctionListing listing) {
        ItemStack template = deserializeItem(listing.getItemData());
        if (template == null) template = new ItemStack(Material.BARRIER);

        Material mat = template.getType();
        ItemStack display = template.clone();
        display.setAmount(Math.min(session.buyQuantity, mat.getMaxStackSize()));
        ItemMeta dm = display.getItemMeta();
        List<Component> existingLore = dm.lore();
        List<Component> lore = existingLore != null ? new ArrayList<>(existingLore) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Quantity: " + session.buyQuantity + " / " + listing.getRemainingAmount(),
                        TextColor.color(0xFFFF55))
                .decoration(TextDecoration.ITALIC, false));
        dm.lore(lore);
        display.setItemMeta(dm);
        inv.setItem(13, display);

        double total = listing.getPricePerUnit() * session.buyQuantity;
        List<String> infoLines = new ArrayList<>();
        infoLines.add("Seller: " + listing.getSellerUsername());
        infoLines.add("Price: $" + EconomyManager.format(listing.getPricePerUnit()) + " each");
        infoLines.add("Buying: " + session.buyQuantity + " / " + listing.getRemainingAmount());
        infoLines.add("");
        infoLines.add("Total: $" + EconomyManager.format(total));

        inv.setItem(31, createGuiItem(Material.PAPER, ShopManager.formatMaterial(mat), 0xFFD700,
                infoLines.toArray(new String[0])));

        ItemStack confirm = createGuiItem(Material.LIME_STAINED_GLASS_PANE, "Confirm Purchase", 0x55FF55,
                "Total: $" + EconomyManager.format(total), "Click to buy!");
        inv.setItem(47, confirm);
    }

    public void openMyListings(Player player) {
        AuctionSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new AuctionSession(AuctionSession.State.MY_LISTINGS));
        session.state = AuctionSession.State.MY_LISTINGS;
        session.page = 0;
        renderMyListings(player, session);
    }

    private void renderMyListings(Player player, AuctionSession session) {
        String username = player.getName().toLowerCase();
        List<AuctionListing> mine = listings.stream()
                .filter(l -> l.isActive() && l.getSellerUsername().equals(username))
                .sorted(Comparator.comparingLong(AuctionListing::getListedDate).reversed())
                .toList();
        session.filteredCache = mine;

        int totalPages = Math.max(1, (int) Math.ceil(mine.size() / (double) ITEMS_PER_PAGE));
        session.page = Math.max(0, Math.min(session.page, totalPages - 1));

        Inventory inv = createInventory(54, "Auction House - My Listings");
        fillRow(inv, 5);

        int start = session.page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, mine.size());
        for (int i = start; i < end; i++) {
            AuctionListing listing = mine.get(i);
            inv.setItem(i - start, createMyListingIcon(listing));
        }

        inv.setItem(45, createGuiItem(Material.BARRIER, "Back", 0xFF5555));
        if (session.page > 0) {
            inv.setItem(48, createGuiItem(Material.ARROW, "Previous Page", 0xFFFF55));
        }
        inv.setItem(49, createGuiItem(Material.PAPER,
                "Page " + (session.page + 1) + "/" + totalPages + " (" + mine.size() + " listings)", 0xAAAAAA));
        if (session.page < totalPages - 1) {
            inv.setItem(50, createGuiItem(Material.ARROW, "Next Page", 0xFFFF55));
        }

        player.openInventory(inv);
    }

    public void openCollect(Player player) {
        AuctionSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new AuctionSession(AuctionSession.State.COLLECT));
        session.state = AuctionSession.State.COLLECT;
        session.page = 0;
        renderCollect(player, session);
    }

    private void renderCollect(Player player, AuctionSession session) {
        String username = player.getName().toLowerCase();
        List<AuctionListing> collectible = listings.stream()
                .filter(l -> !l.isActive() && !l.isCollected() && l.getSellerUsername().equals(username)
                        && l.getRemainingAmount() > 0)
                .sorted(Comparator.comparingLong(AuctionListing::getExpireDate).reversed())
                .toList();
        session.filteredCache = collectible;

        int totalPages = Math.max(1, (int) Math.ceil(collectible.size() / (double) ITEMS_PER_PAGE));
        session.page = Math.max(0, Math.min(session.page, totalPages - 1));

        Inventory inv = createInventory(54, "Auction House - Collect");
        fillRow(inv, 5);

        int start = session.page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, collectible.size());
        for (int i = start; i < end; i++) {
            AuctionListing listing = collectible.get(i);
            inv.setItem(i - start, createCollectIcon(listing));
        }

        inv.setItem(45, createGuiItem(Material.BARRIER, "Back", 0xFF5555));
        if (session.page > 0) {
            inv.setItem(48, createGuiItem(Material.ARROW, "Previous Page", 0xFFFF55));
        }
        inv.setItem(49, createGuiItem(Material.PAPER,
                "Page " + (session.page + 1) + "/" + totalPages, 0xAAAAAA));
        if (session.page < totalPages - 1) {
            inv.setItem(50, createGuiItem(Material.ARROW, "Next Page", 0xFFFF55));
        }
        if (!collectible.isEmpty()) {
            inv.setItem(53, createGuiItem(Material.CHEST, "Collect All", 0x55FF55,
                    "Click to collect all items"));
        }

        player.openInventory(inv);
    }

    private void openCategoryFilter(Player player) {
        AuctionSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new AuctionSession(AuctionSession.State.CATEGORY_FILTER));
        session.state = AuctionSession.State.CATEGORY_FILTER;

        Inventory inv = createInventory(54, "Auction House - Filter");
        fillAll(inv);

        inv.setItem(4, createGuiItem(Material.NETHER_STAR, "All Categories", 0xFFFFFF,
                "Show all listings"));

        ShopCategory[] cats = ShopCategory.values();
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33};
        for (int i = 0; i < slots.length && i < cats.length; i++) {
            ShopCategory cat = cats[i];
            inv.setItem(slots[i], createGuiItem(cat.icon(), cat.displayName(), cat.color()));
        }

        inv.setItem(49, createGuiItem(Material.BARRIER, "Back", 0xFF5555));

        player.openInventory(inv);
    }

    // --- Click Handling ---

    public void handleClick(Player player, int slot) {
        AuctionSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        switch (session.state) {
            case BROWSE -> handleBrowseClick(player, session, slot);
            case BUY -> handleBuyClick(player, session, slot);
            case MY_LISTINGS -> handleMyListingsClick(player, session, slot);
            case COLLECT -> handleCollectClick(player, session, slot);
            case CATEGORY_FILTER -> handleFilterClick(player, session, slot);
        }
    }

    private void handleBrowseClick(Player player, AuctionSession session, int slot) {
        if (slot >= 0 && slot < ITEMS_PER_PAGE && session.filteredCache != null) {
            int idx = session.page * ITEMS_PER_PAGE + slot;
            if (idx < session.filteredCache.size()) {
                AuctionListing listing = session.filteredCache.get(idx);
                if (listing.getSellerUsername().equals(player.getName().toLowerCase())) {
                    Msg.error(player, "You cannot buy your own listing! Use My Listings to cancel it.");
                    return;
                }
                openBuyScreen(player, listing);
            }
        } else if (slot == 45) {
            openMyListings(player);
        } else if (slot == 46) {
            openCollect(player);
        } else if (slot == 47) {
            session.sortMode = session.sortMode.next();
            session.page = 0;
            renderBrowse(player, session);
        } else if (slot == 48 && session.page > 0) {
            session.page--;
            renderBrowse(player, session);
        } else if (slot == 50) {
            session.page++;
            renderBrowse(player, session);
        } else if (slot == 51) {
            openCategoryFilter(player);
        }
    }

    private void handleBuyClick(Player player, AuctionSession session, int slot) {
        AuctionListing listing = findListing(session.selectedListingId);
        if (listing == null || !listing.isActive()) {
            Msg.error(player, "This listing is no longer available!");
            openBrowse(player);
            return;
        }

        Inventory inv = player.getOpenInventory().getTopInventory();
        int maxQty = listing.getRemainingAmount();

        switch (slot) {
            case 9 -> { session.buyQuantity = Math.min(session.buyQuantity + 1, maxQty); updateBuyDisplay(inv, session, listing); }
            case 10 -> { session.buyQuantity = Math.min(session.buyQuantity + 10, maxQty); updateBuyDisplay(inv, session, listing); }
            case 11 -> { session.buyQuantity = Math.min(session.buyQuantity + 64, maxQty); updateBuyDisplay(inv, session, listing); }
            case 15 -> { session.buyQuantity = Math.max(session.buyQuantity - 1, 1); updateBuyDisplay(inv, session, listing); }
            case 16 -> { session.buyQuantity = Math.max(session.buyQuantity - 10, 1); updateBuyDisplay(inv, session, listing); }
            case 17 -> { session.buyQuantity = Math.max(session.buyQuantity - 64, 1); updateBuyDisplay(inv, session, listing); }
            case 47 -> performPurchase(player, session, listing);
            case 51 -> openBrowse(player);
        }
    }

    private void handleMyListingsClick(Player player, AuctionSession session, int slot) {
        if (slot >= 0 && slot < ITEMS_PER_PAGE && session.filteredCache != null) {
            int idx = session.page * ITEMS_PER_PAGE + slot;
            if (idx < session.filteredCache.size()) {
                cancelListing(player, session.filteredCache.get(idx));
            }
        } else if (slot == 45) {
            openBrowse(player);
        } else if (slot == 48 && session.page > 0) {
            session.page--;
            renderMyListings(player, session);
        } else if (slot == 50) {
            session.page++;
            renderMyListings(player, session);
        }
    }

    private void handleCollectClick(Player player, AuctionSession session, int slot) {
        if (slot == 53 && session.filteredCache != null && !session.filteredCache.isEmpty()) {
            collectAll(player, session);
            return;
        }
        if (slot >= 0 && slot < ITEMS_PER_PAGE && session.filteredCache != null) {
            int idx = session.page * ITEMS_PER_PAGE + slot;
            if (idx < session.filteredCache.size()) {
                collectItem(player, session.filteredCache.get(idx));
                openCollect(player);
            }
        } else if (slot == 45) {
            openBrowse(player);
        } else if (slot == 48 && session.page > 0) {
            session.page--;
            renderCollect(player, session);
        } else if (slot == 50) {
            session.page++;
            renderCollect(player, session);
        }
    }

    private void handleFilterClick(Player player, AuctionSession session, int slot) {
        ShopCategory[] cats = ShopCategory.values();
        int[] catSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33};

        if (slot == 4) {
            session.filterCategory = null;
            session.page = 0;
            session.state = AuctionSession.State.BROWSE;
            renderBrowse(player, session);
            return;
        }
        if (slot == 49) {
            session.state = AuctionSession.State.BROWSE;
            renderBrowse(player, session);
            return;
        }

        for (int i = 0; i < catSlots.length && i < cats.length; i++) {
            if (slot == catSlots[i]) {
                session.filterCategory = cats[i];
                session.page = 0;
                session.state = AuctionSession.State.BROWSE;
                renderBrowse(player, session);
                return;
            }
        }
    }

    // --- Purchase Logic ---

    private void performPurchase(Player player, AuctionSession session, AuctionListing listing) {
        if (!listing.isActive() || listing.getRemainingAmount() < session.buyQuantity) {
            Msg.error(player, "This listing is no longer available for that quantity!");
            openBrowse(player);
            return;
        }

        double total = listing.getPricePerUnit() * session.buyQuantity;
        UUID buyerUuid = player.getUniqueId();

        if (!economy.hasMoney(buyerUuid, total)) {
            Msg.error(player, "Insufficient funds! You need $" + EconomyManager.format(total) + ".");
            return;
        }

        economy.removeMoney(buyerUuid, total);

        Player seller = Bukkit.getPlayerExact(listing.getSellerUsername());
        if (seller != null) {
            economy.addMoney(seller.getUniqueId(), total);
            Msg.success(seller, player.getName() + " bought " + session.buyQuantity + "x "
                    + ShopManager.formatMaterial(Material.valueOf(listing.getItemMaterial()))
                    + " for $" + EconomyManager.format(total) + "!");
        } else {
            economy.giveOffline(listing.getSellerUsername(), "money", total);
        }

        ItemStack template = deserializeItem(listing.getItemData());
        if (template != null) {
            giveItems(player, template, session.buyQuantity);
        }

        int newRemaining = listing.getRemainingAmount() - session.buyQuantity;
        listing.setRemainingAmount(newRemaining);
        if (newRemaining <= 0) {
            listing.setActive(false);
            listing.setCollected(true);
        }

        int purchasedQty = session.buyQuantity;
        String materialName = listing.getItemMaterial();
        String sellerName = listing.getSellerUsername();
        String buyerName = player.getName().toLowerCase();
        double ppu = listing.getPricePerUnit();
        int listingId = listing.getId();

        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_auction_listings SET remaining_amount = ?, active = ?, collected = ? WHERE id = ?")) {
                ps.setInt(1, newRemaining);
                ps.setBoolean(2, newRemaining > 0);
                ps.setBoolean(3, newRemaining <= 0);
                ps.setInt(4, listingId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_auction_history (item_material, item_amount, price_per_unit, total_price, " +
                            "seller_username, buyer_username) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, materialName);
                ps.setInt(2, purchasedQty);
                ps.setDouble(3, ppu);
                ps.setDouble(4, total);
                ps.setString(5, sellerName);
                ps.setString(6, buyerName);
                ps.executeUpdate();
            }
        });

        player.closeInventory();
        Msg.success(player, "Purchased " + purchasedQty + "x "
                + ShopManager.formatMaterial(Material.valueOf(materialName))
                + " for $" + EconomyManager.format(total) + "!");
    }

    // --- Cancel & Collect ---

    private void cancelListing(Player player, AuctionListing listing) {
        listing.setActive(false);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_auction_listings SET active = 0 WHERE id = ?")) {
                ps.setInt(1, listing.getId());
                ps.executeUpdate();
            }
        });
        Msg.success(player, "Listing cancelled! Use /ah collect to retrieve your items.");
        openMyListings(player);
    }

    private void collectItem(Player player, AuctionListing listing) {
        ItemStack template = deserializeItem(listing.getItemData());
        if (template == null) {
            Msg.error(player, "Failed to retrieve item data!");
            return;
        }

        giveItems(player, template, listing.getRemainingAmount());
        listing.setCollected(true);
        listings.remove(listing);

        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_auction_listings SET collected = 1 WHERE id = ?")) {
                ps.setInt(1, listing.getId());
                ps.executeUpdate();
            }
        });
        Msg.success(player, "Collected " + listing.getRemainingAmount() + "x "
                + ShopManager.formatMaterial(Material.valueOf(listing.getItemMaterial())) + "!");
    }

    private void collectAll(Player player, AuctionSession session) {
        List<AuctionListing> toCollect = new ArrayList<>(session.filteredCache);
        int totalItems = 0;
        for (AuctionListing listing : toCollect) {
            ItemStack template = deserializeItem(listing.getItemData());
            if (template != null) {
                giveItems(player, template, listing.getRemainingAmount());
                totalItems += listing.getRemainingAmount();
            }
            listing.setCollected(true);
            listings.remove(listing);

            db.executeAsync(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE su_auction_listings SET collected = 1 WHERE id = ?")) {
                    ps.setInt(1, listing.getId());
                    ps.executeUpdate();
                }
            });
        }
        Msg.success(player, "Collected " + totalItems + " items from " + toCollect.size() + " listings!");
        openCollect(player);
    }

    // --- History ---

    public void showHistory(Player player, String materialQuery) {
        String upper = materialQuery.toUpperCase().replace(" ", "_");
        Material mat;
        try {
            mat = Material.valueOf(upper);
        } catch (IllegalArgumentException e) {
            Msg.error(player, "Unknown item: " + materialQuery + ". Use material name like 'diamond' or 'iron_ingot'.");
            return;
        }

        db.queryAsync(conn -> {
            List<String> lines = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM su_auction_history WHERE item_material = ? ORDER BY sold_date DESC LIMIT 10")) {
                ps.setString(1, mat.name());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String seller = rs.getString("seller_username");
                        String buyer = rs.getString("buyer_username");
                        int amount = rs.getInt("item_amount");
                        double ppu = rs.getDouble("price_per_unit");
                        double total = rs.getDouble("total_price");
                        lines.add(buyer + " bought " + amount + "x from " + seller
                                + " at $" + EconomyManager.format(ppu) + "/ea ($"
                                + EconomyManager.format(total) + " total)");
                    }
                }
            }
            return lines;
        }).thenAccept(lines -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (lines == null || lines.isEmpty()) {
                Msg.info(player, "No sale history for " + ShopManager.formatMaterial(mat) + ".");
                return;
            }
            Msg.info(player, "Last " + lines.size() + " sales of " + ShopManager.formatMaterial(mat) + ":");
            for (String line : lines) {
                Msg.gray(player, "  " + line);
            }
        }));
    }

    // --- Blacklist ---

    public void addBlacklist(Player player, Material material) {
        if (blacklist.contains(material)) {
            Msg.error(player, ShopManager.formatMaterial(material) + " is already blacklisted!");
            return;
        }
        blacklist.add(material);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT IGNORE INTO su_auction_blacklist (material) VALUES (?)")) {
                ps.setString(1, material.name());
                ps.executeUpdate();
            }
        });
        Msg.success(player, ShopManager.formatMaterial(material) + " added to blacklist.");
    }

    public void removeBlacklist(Player player, Material material) {
        if (!blacklist.contains(material)) {
            Msg.error(player, ShopManager.formatMaterial(material) + " is not blacklisted!");
            return;
        }
        blacklist.remove(material);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM su_auction_blacklist WHERE material = ?")) {
                ps.setString(1, material.name());
                ps.executeUpdate();
            }
        });
        Msg.success(player, ShopManager.formatMaterial(material) + " removed from blacklist.");
    }

    // --- Helpers ---

    private List<AuctionListing> getFilteredListings(AuctionSession session) {
        Comparator<AuctionListing> sorter = switch (session.sortMode) {
            case NEWEST -> Comparator.comparingLong(AuctionListing::getListedDate).reversed();
            case CHEAPEST -> Comparator.comparingDouble(AuctionListing::getPricePerUnit);
            case MOST_EXPENSIVE -> Comparator.comparingDouble(AuctionListing::getPricePerUnit).reversed();
        };

        return listings.stream()
                .filter(AuctionListing::isActive)
                .filter(l -> {
                    if (session.filterCategory == null) return true;
                    try {
                        return categorize(Material.valueOf(l.getItemMaterial())) == session.filterCategory;
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .sorted(sorter)
                .toList();
    }

    private AuctionListing findListing(int id) {
        for (AuctionListing listing : listings) {
            if (listing.getId() == id) return listing;
        }
        return null;
    }

    private void giveItems(Player player, ItemStack template, int amount) {
        int remaining = amount;
        int maxStack = template.getType().getMaxStackSize();
        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStack);
            ItemStack give = template.clone();
            give.setAmount(stackSize);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(give);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= stackSize;
        }
    }

    private ItemStack createListingIcon(AuctionListing listing) {
        ItemStack template = deserializeItem(listing.getItemData());
        Material mat;
        if (template != null) {
            mat = template.getType();
        } else {
            try {
                mat = Material.valueOf(listing.getItemMaterial());
            } catch (IllegalArgumentException e) {
                mat = Material.BARRIER;
            }
        }

        ItemStack icon;
        if (template != null) {
            icon = template.clone();
            icon.setAmount(Math.min(listing.getRemainingAmount(), mat.getMaxStackSize()));
        } else {
            icon = new ItemStack(mat, Math.min(listing.getRemainingAmount(), mat.getMaxStackSize()));
        }

        ItemMeta meta = icon.getItemMeta();
        List<Component> existingLore = meta.lore();
        List<Component> lore = existingLore != null ? new ArrayList<>(existingLore) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Seller: " + listing.getSellerUsername(), TextColor.color(0xFFFF55))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Price: $" + EconomyManager.format(listing.getPricePerUnit()) + " each",
                        TextColor.color(0x55FF55))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Amount: " + listing.getRemainingAmount() + " / " + listing.getItemAmount(),
                        TextColor.color(0xAAAAAA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Expires in: " + formatTimeLeft(listing.getExpireDate()),
                        TextColor.color(0xAAAAAA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to buy!", TextColor.color(0x55FFFF))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createMyListingIcon(AuctionListing listing) {
        ItemStack template = deserializeItem(listing.getItemData());
        Material mat;
        if (template != null) {
            mat = template.getType();
        } else {
            try {
                mat = Material.valueOf(listing.getItemMaterial());
            } catch (IllegalArgumentException e) {
                mat = Material.BARRIER;
            }
        }

        ItemStack icon;
        if (template != null) {
            icon = template.clone();
            icon.setAmount(Math.min(listing.getRemainingAmount(), mat.getMaxStackSize()));
        } else {
            icon = new ItemStack(mat, Math.min(listing.getRemainingAmount(), mat.getMaxStackSize()));
        }

        ItemMeta meta = icon.getItemMeta();
        List<Component> existingLore = meta.lore();
        List<Component> lore = existingLore != null ? new ArrayList<>(existingLore) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Price: $" + EconomyManager.format(listing.getPricePerUnit()) + " each",
                        TextColor.color(0x55FF55))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Remaining: " + listing.getRemainingAmount() + " / " + listing.getItemAmount(),
                        TextColor.color(0xAAAAAA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Expires in: " + formatTimeLeft(listing.getExpireDate()),
                        TextColor.color(0xAAAAAA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to cancel!", TextColor.color(0xFF5555))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createCollectIcon(AuctionListing listing) {
        ItemStack template = deserializeItem(listing.getItemData());
        Material mat;
        if (template != null) {
            mat = template.getType();
        } else {
            try {
                mat = Material.valueOf(listing.getItemMaterial());
            } catch (IllegalArgumentException e) {
                mat = Material.BARRIER;
            }
        }

        ItemStack icon;
        if (template != null) {
            icon = template.clone();
            icon.setAmount(Math.min(listing.getRemainingAmount(), mat.getMaxStackSize()));
        } else {
            icon = new ItemStack(mat, Math.min(listing.getRemainingAmount(), mat.getMaxStackSize()));
        }

        ItemMeta meta = icon.getItemMeta();
        List<Component> existingLore = meta.lore();
        List<Component> lore = existingLore != null ? new ArrayList<>(existingLore) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Amount: " + listing.getRemainingAmount(), TextColor.color(0xFFFF55))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Status: Expired / Cancelled", TextColor.color(0xFF5555))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to collect!", TextColor.color(0x55FF55))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    static String formatTimeLeft(long expireMillis) {
        long diff = expireMillis - System.currentTimeMillis();
        if (diff <= 0) return "Expired";
        long hours = diff / 3600000;
        long minutes = (diff % 3600000) / 60000;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    // --- GUI Utilities ---

    private Inventory createInventory(int size, String title) {
        AuctionHolder holder = new AuctionHolder();
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

    private ItemStack createQuantityButton(Material material, String label, int color, int displayAmount) {
        ItemStack item = new ItemStack(material, Math.max(1, displayAmount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, TextColor.color(color))
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    public void removeSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }
}
