package com.starlightuniverse.order;

import com.starlightuniverse.auction.AuctionManager;
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

public class OrderManager {

    static final int ITEMS_PER_PAGE = 45;
    static final double ORDER_FEE_RATE = 0.03;
    static final double MINIMUM_ORDER = 100.0;
    static final int MAX_QUANTITY = 2304;
    static final long NOTIFICATION_INTERVAL = 6000L;

    private final JavaPlugin plugin;
    private final EconomyManager economy;
    private final DatabaseManager db;
    private final ShopManager shopManager;
    private final List<Order> orders = new CopyOnWriteArrayList<>();
    private final Map<UUID, OrderSession> sessions = new HashMap<>();
    private BukkitTask notificationTask;

    public OrderManager(JavaPlugin plugin, EconomyManager economy, DatabaseManager db, ShopManager shopManager) {
        this.plugin = plugin;
        this.economy = economy;
        this.db = db;
        this.shopManager = shopManager;
    }

    public void initialize() {
        loadOrders();
        startNotificationTask();
    }

    public void shutdown() {
        if (notificationTask != null) notificationTask.cancel();
    }

    private void loadOrders() {
        db.queryAsync(conn -> {
            List<Order> loaded = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM su_orders WHERE active = 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        loaded.add(new Order(
                                rs.getInt("id"),
                                rs.getString("creator_username"),
                                rs.getString("item_material"),
                                rs.getInt("item_amount"),
                                rs.getInt("delivered_amount"),
                                rs.getDouble("price_per_unit"),
                                rs.getDouble("escrow_amount"),
                                rs.getTimestamp("created_date").getTime(),
                                rs.getBoolean("active")
                        ));
                    }
                }
            }
            return loaded;
        }).thenAccept(loaded -> {
            if (loaded != null) {
                orders.clear();
                orders.addAll(loaded);
                plugin.getLogger().info("[SU] Loaded " + orders.size() + " active orders.");
            }
        });
    }

    private void startNotificationTask() {
        notificationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String username = player.getName().toLowerCase();
                checkStorageNotification(player, username);
            }
        }, NOTIFICATION_INTERVAL, NOTIFICATION_INTERVAL);
    }

    private void checkStorageNotification(Player player, String username) {
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM su_order_storage WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
            return 0;
        }).thenAccept(count -> {
            if (count != null && count > 0) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendActionBar(Component.text(
                                "You have " + count + " item(s) in Order Storage! Use /order storage",
                                TextColor.color(0xFFD700)));
                    }
                });
            }
        });
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

    // --- Main Menu ---

    public void openMainMenu(Player player) {
        OrderSession session = new OrderSession(OrderSession.State.MAIN_MENU);
        sessions.put(player.getUniqueId(), session);

        Inventory inv = createInventory(27, "Item Orders");
        fillAll(inv);

        inv.setItem(10, createGuiItem(Material.BOOK, "Browse All Orders", 0x55FFFF,
                "View all active buy orders", "Fulfill orders and earn money!"));
        inv.setItem(12, createGuiItem(Material.WRITABLE_BOOK, "My Orders", 0xFFAA00,
                "View your own orders", "Cancel or check progress"));
        inv.setItem(14, createGuiItem(Material.EMERALD, "Create Order", 0x55FF55,
                "Place a new buy order", "Money is held in escrow"));
        inv.setItem(16, createGuiItem(Material.CHEST, "Storage", 0xFFD700,
                "Collect delivered items", "Take All / Sell All"));

        player.openInventory(inv);
    }

    // --- Browse Orders ---

    public void openBrowse(Player player) {
        OrderSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new OrderSession(OrderSession.State.BROWSE));
        session.state = OrderSession.State.BROWSE;
        session.page = 0;
        renderBrowse(player, session);
    }

    private void renderBrowse(Player player, OrderSession session) {
        List<Order> active = orders.stream()
                .filter(Order::isActive)
                .filter(o -> o.getRemainingAmount() > 0)
                .sorted(Comparator.comparingLong(Order::getCreatedDate).reversed())
                .toList();
        session.filteredCache = active;

        int totalPages = Math.max(1, (int) Math.ceil(active.size() / (double) ITEMS_PER_PAGE));
        session.page = Math.max(0, Math.min(session.page, totalPages - 1));

        Inventory inv = createInventory(54, "Item Orders - Browse");
        fillRow(inv, 5);

        int start = session.page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, active.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, createOrderIcon(active.get(i)));
        }

        inv.setItem(45, createGuiItem(Material.BARRIER, "Back", 0xFF5555));
        if (session.page > 0) {
            inv.setItem(48, createGuiItem(Material.ARROW, "Previous Page", 0xFFFF55));
        }
        inv.setItem(49, createGuiItem(Material.PAPER,
                "Page " + (session.page + 1) + "/" + totalPages + " (" + active.size() + " orders)", 0xAAAAAA));
        if (session.page < totalPages - 1) {
            inv.setItem(50, createGuiItem(Material.ARROW, "Next Page", 0xFFFF55));
        }

        player.openInventory(inv);
    }

    // --- My Orders ---

    public void openMyOrders(Player player) {
        OrderSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new OrderSession(OrderSession.State.MY_ORDERS));
        session.state = OrderSession.State.MY_ORDERS;
        session.page = 0;
        renderMyOrders(player, session);
    }

    private void renderMyOrders(Player player, OrderSession session) {
        String username = player.getName().toLowerCase();
        List<Order> mine = orders.stream()
                .filter(Order::isActive)
                .filter(o -> o.getCreatorUsername().equals(username))
                .sorted(Comparator.comparingLong(Order::getCreatedDate).reversed())
                .toList();
        session.filteredCache = mine;

        int totalPages = Math.max(1, (int) Math.ceil(mine.size() / (double) ITEMS_PER_PAGE));
        session.page = Math.max(0, Math.min(session.page, totalPages - 1));

        Inventory inv = createInventory(54, "Item Orders - My Orders");
        fillRow(inv, 5);

        int start = session.page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, mine.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, createMyOrderIcon(mine.get(i)));
        }

        inv.setItem(45, createGuiItem(Material.BARRIER, "Back", 0xFF5555));
        if (session.page > 0) {
            inv.setItem(48, createGuiItem(Material.ARROW, "Previous Page", 0xFFFF55));
        }
        inv.setItem(49, createGuiItem(Material.PAPER,
                "Page " + (session.page + 1) + "/" + totalPages + " (" + mine.size() + " orders)", 0xAAAAAA));
        if (session.page < totalPages - 1) {
            inv.setItem(50, createGuiItem(Material.ARROW, "Next Page", 0xFFFF55));
        }

        player.openInventory(inv);
    }

    // --- Create Order: Category Select ---

    public void openCreateCategory(Player player) {
        OrderSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new OrderSession(OrderSession.State.CREATE_CATEGORY));
        session.state = OrderSession.State.CREATE_CATEGORY;

        Inventory inv = createInventory(54, "Create Order - Choose Category");
        fillAll(inv);

        inv.setItem(4, createGuiItem(Material.NAME_TAG, "Search by Name", 0x55FFFF,
                "Type in chat to search for an item"));

        ShopCategory[] cats = ShopCategory.values();
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33};
        for (int i = 0; i < slots.length && i < cats.length; i++) {
            ShopCategory cat = cats[i];
            inv.setItem(slots[i], createGuiItem(cat.icon(), cat.displayName(), cat.color()));
        }

        inv.setItem(49, createGuiItem(Material.BARRIER, "Back", 0xFF5555));

        player.openInventory(inv);
    }

    // --- Create Order: Item Browse ---

    private void openCreateItems(Player player, ShopCategory category, int page) {
        OrderSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new OrderSession(OrderSession.State.CREATE_ITEMS));
        session.state = OrderSession.State.CREATE_ITEMS;
        session.createCategory = category;
        session.page = page;

        List<Material> items = Arrays.stream(Material.values())
                .filter(Material::isItem)
                .filter(m -> !m.isAir())
                .filter(m -> AuctionManager.categorize(m) == category)
                .sorted(Comparator.comparing(Material::name))
                .toList();
        session.createItemList = items;

        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE));
        session.page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = createInventory(54, "Create Order - " + category.displayName());
        fillRow(inv, 5);

        int start = session.page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());
        for (int i = start; i < end; i++) {
            Material mat = items.get(i);
            inv.setItem(i - start, createMaterialIcon(mat));
        }

        inv.setItem(45, createGuiItem(Material.BARRIER, "Back", 0xFF5555));
        if (session.page > 0) {
            inv.setItem(48, createGuiItem(Material.ARROW, "Previous Page", 0xFFFF55));
        }
        inv.setItem(49, createGuiItem(Material.PAPER,
                "Page " + (session.page + 1) + "/" + totalPages + " (" + items.size() + " items)", 0xAAAAAA));
        if (session.page < totalPages - 1) {
            inv.setItem(50, createGuiItem(Material.ARROW, "Next Page", 0xFFFF55));
        }

        player.openInventory(inv);
    }

    public void openCreateSearch(Player player, String query) {
        OrderSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new OrderSession(OrderSession.State.CREATE_ITEMS));
        session.state = OrderSession.State.CREATE_ITEMS;
        session.page = 0;
        session.awaitingSearch = false;

        String lower = query.toLowerCase();
        List<Material> results = Arrays.stream(Material.values())
                .filter(Material::isItem)
                .filter(m -> !m.isAir())
                .filter(m -> ShopManager.formatMaterial(m).toLowerCase().contains(lower))
                .sorted(Comparator.comparing(Material::name))
                .toList();
        session.createItemList = results;

        if (results.isEmpty()) {
            Msg.error(player, "No items found for '" + query + "'.");
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(results.size() / (double) ITEMS_PER_PAGE));

        Inventory inv = createInventory(54, "Create Order - Search: " + query);
        fillRow(inv, 5);

        int end = Math.min(ITEMS_PER_PAGE, results.size());
        for (int i = 0; i < end; i++) {
            inv.setItem(i, createMaterialIcon(results.get(i)));
        }

        inv.setItem(45, createGuiItem(Material.BARRIER, "Back", 0xFF5555));
        inv.setItem(49, createGuiItem(Material.PAPER,
                "Page 1/" + totalPages + " (" + results.size() + " results)", 0xAAAAAA));
        if (totalPages > 1) {
            inv.setItem(50, createGuiItem(Material.ARROW, "Next Page", 0xFFFF55));
        }

        player.openInventory(inv);
    }

    // --- Create Order: Setup (Quantity + Price) ---

    private void openCreateSetup(Player player, Material material) {
        OrderSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new OrderSession(OrderSession.State.CREATE_SETUP));
        session.state = OrderSession.State.CREATE_SETUP;
        session.selectedMaterial = material;
        session.createQuantity = 1;
        session.createPrice = 100;

        Inventory inv = createInventory(54, "Create Order - Setup");
        fillAll(inv);

        // Quantity row (row 1): +1/+10/+64 left, -1/-10/-64 right
        inv.setItem(9, createQuantityButton(Material.LIME_STAINED_GLASS_PANE, "+1", 0x55FF55, 1));
        inv.setItem(10, createQuantityButton(Material.LIME_STAINED_GLASS_PANE, "+10", 0x55FF55, 10));
        inv.setItem(11, createQuantityButton(Material.LIME_STAINED_GLASS_PANE, "+64", 0x55FF55, 64));
        inv.setItem(15, createQuantityButton(Material.RED_STAINED_GLASS_PANE, "-1", 0xFF5555, 1));
        inv.setItem(16, createQuantityButton(Material.RED_STAINED_GLASS_PANE, "-10", 0xFF5555, 10));
        inv.setItem(17, createQuantityButton(Material.RED_STAINED_GLASS_PANE, "-64", 0xFF5555, 64));

        // Price row (row 2): +100/+1000/+10000 left, -100/-1000/-10000 right
        inv.setItem(18, createPriceButton("+$100", 0x55FF55));
        inv.setItem(19, createPriceButton("+$1,000", 0x55FF55));
        inv.setItem(20, createPriceButton("+$10,000", 0x55FF55));
        inv.setItem(24, createPriceButton("-$100", 0xFF5555));
        inv.setItem(25, createPriceButton("-$1,000", 0xFF5555));
        inv.setItem(26, createPriceButton("-$10,000", 0xFF5555));

        // Labels
        inv.setItem(12, createGuiItem(Material.HOPPER, "Quantity", 0xFFFF55));
        inv.setItem(21, createGuiItem(Material.GOLD_INGOT, "Price per Unit", 0xFFD700));

        inv.setItem(47, createGuiItem(Material.LIME_STAINED_GLASS_PANE, "Confirm Order", 0x55FF55));
        inv.setItem(51, createGuiItem(Material.RED_STAINED_GLASS_PANE, "Cancel", 0xFF5555));

        updateCreateDisplay(inv, session);
        player.openInventory(inv);
    }

    private void updateCreateDisplay(Inventory inv, OrderSession session) {
        Material mat = session.selectedMaterial;
        ItemStack display = new ItemStack(mat, Math.min(session.createQuantity, mat.getMaxStackSize()));
        ItemMeta dm = display.getItemMeta();
        dm.displayName(Component.text(ShopManager.formatMaterial(mat), TextColor.color(0xFFFFFF))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Quantity: " + session.createQuantity, TextColor.color(0xFFFF55))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Price: $" + EconomyManager.format(session.createPrice) + " each",
                        TextColor.color(0x55FF55))
                .decoration(TextDecoration.ITALIC, false));
        dm.lore(lore);
        display.setItemMeta(dm);
        inv.setItem(13, display);

        double subtotal = session.createPrice * session.createQuantity;
        double fee = Math.ceil(subtotal * ORDER_FEE_RATE);
        double total = subtotal + fee;

        List<String> infoLines = new ArrayList<>();
        infoLines.add("Item: " + ShopManager.formatMaterial(mat));
        infoLines.add("Quantity: " + session.createQuantity);
        infoLines.add("Price per Unit: $" + EconomyManager.format(session.createPrice));
        infoLines.add("");
        infoLines.add("Subtotal: $" + EconomyManager.format(subtotal));
        infoLines.add("Fee (3%): $" + EconomyManager.format(fee));
        infoLines.add("Total Cost: $" + EconomyManager.format(total));

        inv.setItem(31, createGuiItem(Material.PAPER, "Order Summary", 0xFFD700,
                infoLines.toArray(new String[0])));

        ItemStack confirm = createGuiItem(Material.LIME_STAINED_GLASS_PANE, "Confirm Order", 0x55FF55,
                "Total: $" + EconomyManager.format(total), "Click to place order!");
        inv.setItem(47, confirm);
    }

    // --- Delivery ---

    public void openDelivery(Player player, Order order) {
        OrderSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new OrderSession(OrderSession.State.DELIVER));
        session.state = OrderSession.State.DELIVER;
        session.deliverOrderId = order.getId();

        Material mat;
        try {
            mat = Material.valueOf(order.getItemMaterial());
        } catch (IllegalArgumentException e) {
            mat = Material.BARRIER;
        }

        String title = "Deliver: " + order.getRemainingAmount() + "x " + ShopManager.formatMaterial(mat);
        if (title.length() > 32) title = "Deliver Order #" + order.getId();

        OrderDeliveryHolder holder = new OrderDeliveryHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text(title, TextColor.color(0x55FF55))
                        .decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        player.openInventory(inv);
    }

    public void processDeliveryClose(Player player, ItemStack[] contents) {
        OrderSession session = sessions.get(player.getUniqueId());
        if (session == null || session.state != OrderSession.State.DELIVER) return;

        Order order = findOrder(session.deliverOrderId);
        if (order == null || !order.isActive()) {
            Msg.error(player, "This order is no longer active!");
            returnItems(player, contents);
            sessions.remove(player.getUniqueId());
            return;
        }

        Material targetMat;
        try {
            targetMat = Material.valueOf(order.getItemMaterial());
        } catch (IllegalArgumentException e) {
            returnItems(player, contents);
            sessions.remove(player.getUniqueId());
            return;
        }

        int matchCount = 0;
        List<ItemStack> nonMatching = new ArrayList<>();
        List<ItemStack> matching = new ArrayList<>();

        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (item.getType() == targetMat) {
                matchCount += item.getAmount();
                matching.add(item.clone());
            } else {
                nonMatching.add(item.clone());
            }
        }

        returnItems(player, nonMatching.toArray(new ItemStack[0]));

        if (matchCount == 0) {
            Msg.error(player, "No matching items found! The order requires " + ShopManager.formatMaterial(targetMat) + ".");
            sessions.remove(player.getUniqueId());
            return;
        }

        int deliverable = Math.min(matchCount, order.getRemainingAmount());
        session.deliveryContents = matching.toArray(new ItemStack[0]);
        session.deliveryMatchCount = deliverable;
        session.state = OrderSession.State.DELIVER_CONFIRM;

        int extraCount = matchCount - deliverable;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            double payment = order.getPricePerUnit() * deliverable;

            Inventory confirm = createInventory(27, "Confirm Delivery");
            fillAll(confirm);

            List<String> infoLines = new ArrayList<>();
            infoLines.add("Item: " + ShopManager.formatMaterial(targetMat));
            infoLines.add("Delivering: " + deliverable + " / " + order.getRemainingAmount() + " needed");
            infoLines.add("Payment: $" + EconomyManager.format(payment));
            if (extraCount > 0) {
                infoLines.add("");
                infoLines.add("Extra " + extraCount + " items will be returned");
            }

            confirm.setItem(13, createGuiItem(Material.PAPER, "Delivery Summary", 0xFFD700,
                    infoLines.toArray(new String[0])));

            confirm.setItem(11, createGuiItem(Material.LIME_STAINED_GLASS_PANE, "Confirm", 0x55FF55,
                    "Deliver " + deliverable + " items", "Receive $" + EconomyManager.format(payment)));
            confirm.setItem(15, createGuiItem(Material.RED_STAINED_GLASS_PANE, "Cancel", 0xFF5555,
                    "Return all items"));

            player.openInventory(confirm);
        }, 1L);
    }

    private void confirmDelivery(Player player, OrderSession session) {
        Order order = findOrder(session.deliverOrderId);
        if (order == null || !order.isActive()) {
            Msg.error(player, "This order is no longer active!");
            returnDeliveryItems(player, session);
            return;
        }

        int deliverable = session.deliveryMatchCount;
        Material targetMat;
        try {
            targetMat = Material.valueOf(order.getItemMaterial());
        } catch (IllegalArgumentException e) {
            returnDeliveryItems(player, session);
            return;
        }

        int totalInDelivery = 0;
        for (ItemStack item : session.deliveryContents) {
            if (item != null) totalInDelivery += item.getAmount();
        }
        int extraCount = totalInDelivery - deliverable;

        if (extraCount > 0) {
            int toReturn = extraCount;
            for (int i = session.deliveryContents.length - 1; i >= 0 && toReturn > 0; i--) {
                ItemStack item = session.deliveryContents[i];
                if (item == null) continue;
                if (item.getAmount() <= toReturn) {
                    giveItems(player, item, item.getAmount());
                    toReturn -= item.getAmount();
                    session.deliveryContents[i] = null;
                } else {
                    giveItems(player, item, toReturn);
                    item.setAmount(item.getAmount() - toReturn);
                    toReturn = 0;
                }
            }
        }

        double payment = order.getPricePerUnit() * deliverable;
        economy.addMoney(player.getUniqueId(), payment);

        int newDelivered = order.getDeliveredAmount() + deliverable;
        order.setDeliveredAmount(newDelivered);
        double usedEscrow = order.getPricePerUnit() * deliverable;
        order.setEscrowAmount(order.getEscrowAmount() - usedEscrow);

        if (newDelivered >= order.getItemAmount()) {
            order.setActive(false);
        }

        String creatorUsername = order.getCreatorUsername();
        ItemStack template = new ItemStack(targetMat);
        String itemData = serializeItem(template);

        int orderId = order.getId();
        boolean completed = !order.isActive();
        double remainingEscrow = order.getEscrowAmount();

        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_orders SET delivered_amount = ?, escrow_amount = ?, active = ? WHERE id = ?")) {
                ps.setInt(1, newDelivered);
                ps.setDouble(2, remainingEscrow);
                ps.setBoolean(3, !completed);
                ps.setInt(4, orderId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_order_storage (username, item_data, item_material, item_amount) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, creatorUsername);
                ps.setString(2, itemData);
                ps.setString(3, targetMat.name());
                ps.setInt(4, deliverable);
                ps.executeUpdate();
            }
        });

        Player creator = Bukkit.getPlayerExact(creatorUsername);
        if (creator != null && creator.isOnline()) {
            Msg.success(creator, player.getName() + " delivered " + deliverable + "x "
                    + ShopManager.formatMaterial(targetMat) + " to your order! Use /order storage to collect.");
        }

        player.closeInventory();
        Msg.success(player, "Delivered " + deliverable + "x " + ShopManager.formatMaterial(targetMat)
                + "! Earned $" + EconomyManager.format(payment) + ".");

        if (completed) {
            Msg.info(player, "Order #" + orderId + " is now complete!");
        }
    }

    private void cancelDelivery(Player player, OrderSession session) {
        returnDeliveryItems(player, session);
        player.closeInventory();
        Msg.info(player, "Delivery cancelled. Items returned.");
    }

    private void returnDeliveryItems(Player player, OrderSession session) {
        if (session.deliveryContents != null) {
            for (ItemStack item : session.deliveryContents) {
                if (item != null && item.getType() != Material.AIR) {
                    giveItems(player, item, item.getAmount());
                }
            }
            session.deliveryContents = null;
        }
    }

    // --- Cancel Order ---

    private void cancelOrder(Player player, Order order) {
        order.setActive(false);

        int undelivered = order.getRemainingAmount();
        double refund = order.getPricePerUnit() * undelivered;
        order.setEscrowAmount(0);

        economy.addMoney(player.getUniqueId(), refund);

        int orderId = order.getId();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_orders SET active = 0, escrow_amount = 0 WHERE id = ?")) {
                ps.setInt(1, orderId);
                ps.executeUpdate();
            }
        });

        Material mat;
        try {
            mat = Material.valueOf(order.getItemMaterial());
        } catch (IllegalArgumentException e) {
            mat = Material.BARRIER;
        }

        Msg.success(player, "Order cancelled! Refunded $" + EconomyManager.format(refund)
                + " for " + undelivered + " undelivered " + ShopManager.formatMaterial(mat) + ".");
        openMyOrders(player);
    }

    // --- Storage ---

    public void openStorage(Player player) {
        OrderSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new OrderSession(OrderSession.State.STORAGE));
        session.state = OrderSession.State.STORAGE;
        session.page = 0;

        String username = player.getName().toLowerCase();
        db.queryAsync(conn -> {
            List<OrderStorageItem> items = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM su_order_storage WHERE username = ? ORDER BY stored_date DESC")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(new OrderStorageItem(
                                rs.getInt("id"),
                                rs.getString("username"),
                                rs.getString("item_data"),
                                rs.getString("item_material"),
                                rs.getInt("item_amount"),
                                rs.getTimestamp("stored_date").getTime()
                        ));
                    }
                }
            }
            return items;
        }).thenAccept(items -> {
            if (items == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                session.storageCache = items;
                renderStorage(player, session);
            });
        });
    }

    private void renderStorage(Player player, OrderSession session) {
        List<OrderStorageItem> items = session.storageCache;
        if (items == null) items = List.of();

        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE));
        session.page = Math.max(0, Math.min(session.page, totalPages - 1));

        Inventory inv = createInventory(54, "Order Storage");
        fillRow(inv, 5);

        int start = session.page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, createStorageIcon(items.get(i)));
        }

        inv.setItem(45, createGuiItem(Material.BARRIER, "Back", 0xFF5555));

        if (!items.isEmpty()) {
            inv.setItem(46, createGuiItem(Material.CHEST, "Take All", 0x55FF55,
                    "Take all items to inventory"));
            inv.setItem(47, createGuiItem(Material.GOLD_INGOT, "Sell All", 0xFFD700,
                    "Sell all items at shop price"));
        }

        if (session.page > 0) {
            inv.setItem(48, createGuiItem(Material.ARROW, "Previous Page", 0xFFFF55));
        }
        inv.setItem(49, createGuiItem(Material.PAPER,
                "Page " + (session.page + 1) + "/" + totalPages + " (" + items.size() + " items)", 0xAAAAAA));
        if (session.page < totalPages - 1) {
            inv.setItem(50, createGuiItem(Material.ARROW, "Next Page", 0xFFFF55));
        }

        player.openInventory(inv);
    }

    private void takeStorageItem(Player player, OrderStorageItem storageItem) {
        ItemStack template = deserializeItem(storageItem.getItemData());
        if (template == null) {
            try {
                template = new ItemStack(Material.valueOf(storageItem.getItemMaterial()));
            } catch (IllegalArgumentException e) {
                Msg.error(player, "Failed to retrieve item!");
                return;
            }
        }

        giveItems(player, template, storageItem.getItemAmount());

        int storageId = storageItem.getId();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM su_order_storage WHERE id = ?")) {
                ps.setInt(1, storageId);
                ps.executeUpdate();
            }
        });

        Material mat;
        try {
            mat = Material.valueOf(storageItem.getItemMaterial());
        } catch (IllegalArgumentException e) {
            mat = Material.BARRIER;
        }
        Msg.success(player, "Collected " + storageItem.getItemAmount() + "x " + ShopManager.formatMaterial(mat) + "!");
    }

    private void takeAll(Player player, OrderSession session) {
        if (session.storageCache == null || session.storageCache.isEmpty()) return;

        int totalItems = 0;
        for (OrderStorageItem storageItem : session.storageCache) {
            ItemStack template = deserializeItem(storageItem.getItemData());
            if (template == null) {
                try {
                    template = new ItemStack(Material.valueOf(storageItem.getItemMaterial()));
                } catch (IllegalArgumentException e) { continue; }
            }
            giveItems(player, template, storageItem.getItemAmount());
            totalItems += storageItem.getItemAmount();

            int storageId = storageItem.getId();
            db.executeAsync(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM su_order_storage WHERE id = ?")) {
                    ps.setInt(1, storageId);
                    ps.executeUpdate();
                }
            });
        }

        Msg.success(player, "Collected " + totalItems + " items from storage!");
        session.storageCache = List.of();
        renderStorage(player, session);
    }

    private void sellAll(Player player, OrderSession session) {
        if (session.storageCache == null || session.storageCache.isEmpty()) return;

        double totalEarned = 0;
        int soldCount = 0;
        List<OrderStorageItem> unsellable = new ArrayList<>();

        for (OrderStorageItem storageItem : session.storageCache) {
            Material mat;
            try {
                mat = Material.valueOf(storageItem.getItemMaterial());
            } catch (IllegalArgumentException e) {
                unsellable.add(storageItem);
                continue;
            }

            int shopPrice = shopManager.getShopPrice(mat);
            if (shopPrice <= 0) {
                unsellable.add(storageItem);
                continue;
            }

            double sellPrice = shopPrice * 0.5 * storageItem.getItemAmount();
            totalEarned += sellPrice;
            soldCount += storageItem.getItemAmount();

            int storageId = storageItem.getId();
            db.executeAsync(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM su_order_storage WHERE id = ?")) {
                    ps.setInt(1, storageId);
                    ps.executeUpdate();
                }
            });
        }

        if (soldCount > 0) {
            economy.addMoney(player.getUniqueId(), totalEarned);
            Msg.success(player, "Sold " + soldCount + " items for $" + EconomyManager.format(totalEarned) + "!");
        }
        if (!unsellable.isEmpty()) {
            Msg.info(player, unsellable.size() + " item(s) have no shop price and were kept in storage.");
        }

        session.storageCache = unsellable;
        renderStorage(player, session);
    }

    // --- Order Creation Logic ---

    private void createOrder(Player player, OrderSession session) {
        Material mat = session.selectedMaterial;
        int quantity = session.createQuantity;
        double pricePerUnit = session.createPrice;
        double subtotal = pricePerUnit * quantity;

        if (subtotal < MINIMUM_ORDER) {
            Msg.error(player, "Minimum order value is $" + EconomyManager.format(MINIMUM_ORDER) + "!");
            return;
        }

        double fee = Math.ceil(subtotal * ORDER_FEE_RATE);
        double total = subtotal + fee;

        if (!economy.hasMoney(player.getUniqueId(), total)) {
            Msg.error(player, "Insufficient funds! You need $" + EconomyManager.format(total) + ".");
            return;
        }

        economy.removeMoney(player.getUniqueId(), total);

        String username = player.getName().toLowerCase();
        String materialName = mat.name();
        long now = System.currentTimeMillis();

        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_orders (creator_username, item_material, item_amount, delivered_amount, " +
                            "price_per_unit, escrow_amount) VALUES (?, ?, ?, 0, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, username);
                ps.setString(2, materialName);
                ps.setInt(3, quantity);
                ps.setDouble(4, pricePerUnit);
                ps.setDouble(5, subtotal);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
            return -1;
        }).thenAccept(id -> {
            if (id != null && id > 0) {
                Order order = new Order(id, username, materialName, quantity, 0,
                        pricePerUnit, subtotal, now, true);
                orders.add(order);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.closeInventory();
                    Msg.success(player, "Order created! Buying " + quantity + "x "
                            + ShopManager.formatMaterial(mat) + " at $"
                            + EconomyManager.format(pricePerUnit) + " each. Total: $"
                            + EconomyManager.format(total) + " (incl. 3% fee).");
                });
            }
        });
    }

    // --- Click Handling ---

    public void handleClick(Player player, int slot) {
        OrderSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        switch (session.state) {
            case MAIN_MENU -> handleMainMenuClick(player, session, slot);
            case BROWSE -> handleBrowseClick(player, session, slot);
            case MY_ORDERS -> handleMyOrdersClick(player, session, slot);
            case CREATE_CATEGORY -> handleCreateCategoryClick(player, session, slot);
            case CREATE_ITEMS -> handleCreateItemsClick(player, session, slot);
            case CREATE_SETUP -> handleCreateSetupClick(player, session, slot);
            case DELIVER_CONFIRM -> handleDeliverConfirmClick(player, session, slot);
            case STORAGE -> handleStorageClick(player, session, slot);
        }
    }

    private void handleMainMenuClick(Player player, OrderSession session, int slot) {
        switch (slot) {
            case 10 -> openBrowse(player);
            case 12 -> openMyOrders(player);
            case 14 -> openCreateCategory(player);
            case 16 -> openStorage(player);
        }
    }

    private void handleBrowseClick(Player player, OrderSession session, int slot) {
        if (slot >= 0 && slot < ITEMS_PER_PAGE && session.filteredCache != null) {
            int idx = session.page * ITEMS_PER_PAGE + slot;
            if (idx < session.filteredCache.size()) {
                Order order = session.filteredCache.get(idx);
                if (order.getCreatorUsername().equals(player.getName().toLowerCase())) {
                    Msg.error(player, "You can't fulfill your own order! Use My Orders to cancel it.");
                    return;
                }
                openDelivery(player, order);
            }
        } else if (slot == 45) {
            openMainMenu(player);
        } else if (slot == 48 && session.page > 0) {
            session.page--;
            renderBrowse(player, session);
        } else if (slot == 50) {
            session.page++;
            renderBrowse(player, session);
        }
    }

    private void handleMyOrdersClick(Player player, OrderSession session, int slot) {
        if (slot >= 0 && slot < ITEMS_PER_PAGE && session.filteredCache != null) {
            int idx = session.page * ITEMS_PER_PAGE + slot;
            if (idx < session.filteredCache.size()) {
                cancelOrder(player, session.filteredCache.get(idx));
            }
        } else if (slot == 45) {
            openMainMenu(player);
        } else if (slot == 48 && session.page > 0) {
            session.page--;
            renderMyOrders(player, session);
        } else if (slot == 50) {
            session.page++;
            renderMyOrders(player, session);
        }
    }

    private void handleCreateCategoryClick(Player player, OrderSession session, int slot) {
        if (slot == 4) {
            session.awaitingSearch = true;
            player.closeInventory();
            Msg.info(player, "Type the item name to search for:");
            return;
        }
        if (slot == 49) {
            openMainMenu(player);
            return;
        }

        ShopCategory[] cats = ShopCategory.values();
        int[] catSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33};
        for (int i = 0; i < catSlots.length && i < cats.length; i++) {
            if (slot == catSlots[i]) {
                openCreateItems(player, cats[i], 0);
                return;
            }
        }
    }

    private void handleCreateItemsClick(Player player, OrderSession session, int slot) {
        if (slot >= 0 && slot < ITEMS_PER_PAGE && session.createItemList != null) {
            int idx = session.page * ITEMS_PER_PAGE + slot;
            if (idx < session.createItemList.size()) {
                openCreateSetup(player, session.createItemList.get(idx));
            }
        } else if (slot == 45) {
            openCreateCategory(player);
        } else if (slot == 48 && session.page > 0) {
            session.page--;
            if (session.createCategory != null) {
                openCreateItems(player, session.createCategory, session.page);
            }
        } else if (slot == 50) {
            session.page++;
            if (session.createCategory != null) {
                openCreateItems(player, session.createCategory, session.page);
            }
        }
    }

    private void handleCreateSetupClick(Player player, OrderSession session, int slot) {
        Inventory inv = player.getOpenInventory().getTopInventory();

        switch (slot) {
            // Quantity buttons
            case 9 -> { session.createQuantity = Math.min(session.createQuantity + 1, MAX_QUANTITY); updateCreateDisplay(inv, session); }
            case 10 -> { session.createQuantity = Math.min(session.createQuantity + 10, MAX_QUANTITY); updateCreateDisplay(inv, session); }
            case 11 -> { session.createQuantity = Math.min(session.createQuantity + 64, MAX_QUANTITY); updateCreateDisplay(inv, session); }
            case 15 -> { session.createQuantity = Math.max(session.createQuantity - 1, 1); updateCreateDisplay(inv, session); }
            case 16 -> { session.createQuantity = Math.max(session.createQuantity - 10, 1); updateCreateDisplay(inv, session); }
            case 17 -> { session.createQuantity = Math.max(session.createQuantity - 64, 1); updateCreateDisplay(inv, session); }
            // Price buttons
            case 18 -> { session.createPrice += 100; updateCreateDisplay(inv, session); }
            case 19 -> { session.createPrice += 1000; updateCreateDisplay(inv, session); }
            case 20 -> { session.createPrice += 10000; updateCreateDisplay(inv, session); }
            case 24 -> { session.createPrice = Math.max(session.createPrice - 100, 1); updateCreateDisplay(inv, session); }
            case 25 -> { session.createPrice = Math.max(session.createPrice - 1000, 1); updateCreateDisplay(inv, session); }
            case 26 -> { session.createPrice = Math.max(session.createPrice - 10000, 1); updateCreateDisplay(inv, session); }
            // Confirm / Cancel
            case 47 -> createOrder(player, session);
            case 51 -> openCreateCategory(player);
        }
    }

    private void handleDeliverConfirmClick(Player player, OrderSession session, int slot) {
        switch (slot) {
            case 11 -> confirmDelivery(player, session);
            case 15 -> cancelDelivery(player, session);
        }
    }

    private void handleStorageClick(Player player, OrderSession session, int slot) {
        if (slot >= 0 && slot < ITEMS_PER_PAGE && session.storageCache != null) {
            int idx = session.page * ITEMS_PER_PAGE + slot;
            if (idx < session.storageCache.size()) {
                takeStorageItem(player, session.storageCache.get(idx));
                openStorage(player);
            }
        } else if (slot == 45) {
            openMainMenu(player);
        } else if (slot == 46) {
            takeAll(player, session);
        } else if (slot == 47) {
            sellAll(player, session);
        } else if (slot == 48 && session.page > 0) {
            session.page--;
            renderStorage(player, session);
        } else if (slot == 50) {
            session.page++;
            renderStorage(player, session);
        }
    }

    // --- Icon Builders ---

    private ItemStack createOrderIcon(Order order) {
        Material mat;
        try {
            mat = Material.valueOf(order.getItemMaterial());
        } catch (IllegalArgumentException e) {
            mat = Material.BARRIER;
        }

        ItemStack icon = new ItemStack(mat, Math.min(order.getRemainingAmount(), mat.getMaxStackSize()));
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(ShopManager.formatMaterial(mat), TextColor.color(0xFFFFFF))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Buyer: " + order.getCreatorUsername(), TextColor.color(0xFFFF55))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Paying: $" + EconomyManager.format(order.getPricePerUnit()) + " each",
                        TextColor.color(0x55FF55))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Needed: " + order.getRemainingAmount() + " / " + order.getItemAmount(),
                        TextColor.color(0xAAAAAA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to deliver!", TextColor.color(0x55FFFF))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createMyOrderIcon(Order order) {
        Material mat;
        try {
            mat = Material.valueOf(order.getItemMaterial());
        } catch (IllegalArgumentException e) {
            mat = Material.BARRIER;
        }

        ItemStack icon = new ItemStack(mat, Math.min(order.getRemainingAmount(), mat.getMaxStackSize()));
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(ShopManager.formatMaterial(mat), TextColor.color(0xFFFFFF))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Price: $" + EconomyManager.format(order.getPricePerUnit()) + " each",
                        TextColor.color(0x55FF55))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Progress: " + order.getDeliveredAmount() + " / " + order.getItemAmount(),
                        TextColor.color(0xFFFF55))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Remaining: " + order.getRemainingAmount(),
                        TextColor.color(0xAAAAAA))
                .decoration(TextDecoration.ITALIC, false));
        double escrow = order.getEscrowAmount();
        lore.add(Component.text("Escrow: $" + EconomyManager.format(escrow),
                        TextColor.color(0xAAAAAA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to cancel!", TextColor.color(0xFF5555))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createStorageIcon(OrderStorageItem item) {
        Material mat;
        try {
            mat = Material.valueOf(item.getItemMaterial());
        } catch (IllegalArgumentException e) {
            mat = Material.BARRIER;
        }

        ItemStack icon = new ItemStack(mat, Math.min(item.getItemAmount(), mat.getMaxStackSize()));
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(ShopManager.formatMaterial(mat), TextColor.color(0xFFFFFF))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Amount: " + item.getItemAmount(), TextColor.color(0xFFFF55))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to collect!", TextColor.color(0x55FF55))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack createMaterialIcon(Material mat) {
        ItemStack icon = new ItemStack(mat);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(ShopManager.formatMaterial(mat), TextColor.color(0xFFFFFF))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Click to order this item!", TextColor.color(0x55FFFF))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    // --- Helpers ---

    private Order findOrder(int id) {
        for (Order order : orders) {
            if (order.getId() == id) return order;
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

    private void returnItems(Player player, ItemStack[] items) {
        if (items == null) return;
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
        }
    }

    // --- GUI Utilities ---

    private Inventory createInventory(int size, String title) {
        OrderHolder holder = new OrderHolder();
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

    private ItemStack createPriceButton(String label, int color) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
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

    public OrderSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public boolean isAwaitingSearch(UUID uuid) {
        OrderSession session = sessions.get(uuid);
        return session != null && session.awaitingSearch;
    }
}
