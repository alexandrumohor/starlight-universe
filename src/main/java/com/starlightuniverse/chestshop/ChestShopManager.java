package com.starlightuniverse.chestshop;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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
import java.util.concurrent.ConcurrentHashMap;

public class ChestShopManager {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);

    private static final int SIGN_MAX_CHARS = 15;
    private static final BlockFace[] SIGN_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;

    private final Map<String, ChestShop> shopsByLocation = new ConcurrentHashMap<>();
    private final Map<Integer, ChestShop> shopsById = new ConcurrentHashMap<>();

    private final Map<UUID, PendingCreation> pendingCreations = new ConcurrentHashMap<>();
    private final Map<UUID, PendingTransaction> pendingTransactions = new ConcurrentHashMap<>();

    private final Map<String, MarqueeEntry> marqueeEntries = new ConcurrentHashMap<>();
    private BukkitTask marqueeTask;

    private record PendingCreation(double price, ChestShop.ShopType shopType) {}
    private record PendingTransaction(ChestShop shop) {}

    private static class MarqueeEntry {
        final String fullText;
        int offset;
        MarqueeEntry(String fullText) {
            this.fullText = fullText + "   ";
            this.offset = 0;
        }
        String currentFrame() {
            String looped = fullText + fullText;
            return looped.substring(offset, offset + SIGN_MAX_CHARS);
        }
        void advance() {
            offset = (offset + 1) % fullText.length();
        }
    }

    public ChestShopManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
    }

    public void initialize() {
        db.queryAsync(conn -> {
            List<ChestShop> shops = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, owner_username, world, x, y, z, item_type, item_data, price, shop_type, shop_name FROM su_chestshops")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ChestShop.ShopType type;
                        try {
                            type = ChestShop.ShopType.valueOf(rs.getString("shop_type"));
                        } catch (IllegalArgumentException e) {
                            continue;
                        }
                        shops.add(new ChestShop(
                                rs.getInt("id"),
                                rs.getString("owner_username"),
                                null,
                                rs.getString("world"),
                                rs.getInt("x"),
                                rs.getInt("y"),
                                rs.getInt("z"),
                                rs.getString("item_type"),
                                rs.getString("item_data"),
                                rs.getDouble("price"),
                                type,
                                rs.getString("shop_name")
                        ));
                    }
                }
            }
            return shops;
        }).thenAccept(shops -> {
            if (shops == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (ChestShop shop : shops) {
                    registerShop(shop);
                }
                startMarquee();
                plugin.getLogger().info("[SU] Loaded " + shops.size() + " chest shops.");
            });
        });
    }

    public void shutdown() {
        if (marqueeTask != null) {
            marqueeTask.cancel();
            marqueeTask = null;
        }
    }

    // ========== Creation ==========

    public void startCreation(Player player, double price, ChestShop.ShopType shopType) {
        if (price <= 0) {
            Msg.error(player, "Price must be greater than 0!");
            return;
        }
        pendingCreations.put(player.getUniqueId(), new PendingCreation(price, shopType));
        Msg.info(player, "Right-click on the desired chest to create the shop.");
    }

    public boolean hasPendingCreation(UUID uuid) {
        return pendingCreations.containsKey(uuid);
    }

    public void cancelPendingCreation(UUID uuid) {
        pendingCreations.remove(uuid);
    }

    public void handleChestClick(Player player, Block block) {
        PendingCreation pending = pendingCreations.remove(player.getUniqueId());
        if (pending == null) return;

        Block canonical = getCanonicalChest(block);
        String locKey = locationKey(canonical);

        if (shopsByLocation.containsKey(locKey)) {
            Msg.error(player, "This chest already has a shop!");
            return;
        }

        Container container = (Container) canonical.getState();
        Inventory inv = getFullInventory(canonical);
        ItemStack firstItem = findFirstItem(inv);
        if (firstItem == null) {
            Msg.error(player, "The chest is empty! Put items in first.");
            return;
        }

        Material itemMat = firstItem.getType();
        ItemStack template = firstItem.clone();
        template.setAmount(1);
        String itemData = serializeItem(template);
        if (itemData == null) {
            Msg.error(player, "Failed to process item data!");
            return;
        }

        String itemName = formatMaterialName(itemMat);
        String ownerUsername = player.getName().toLowerCase();
        String world = canonical.getWorld().getName();
        int cx = canonical.getX(), cy = canonical.getY(), cz = canonical.getZ();

        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_chestshops (owner_username, world, x, y, z, item_type, item_data, price, shop_type, shop_name) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, ownerUsername);
                ps.setString(2, world);
                ps.setInt(3, cx);
                ps.setInt(4, cy);
                ps.setInt(5, cz);
                ps.setString(6, itemMat.name());
                ps.setString(7, itemData);
                ps.setDouble(8, pending.price());
                ps.setString(9, pending.shopType().name());
                ps.setString(10, itemName);
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        int shopId = keys.getInt(1);
                        try (PreparedStatement bankPs = conn.prepareStatement(
                                "INSERT INTO su_chestshop_bank (shop_id, balance) VALUES (?, 0)")) {
                            bankPs.setInt(1, shopId);
                            bankPs.executeUpdate();
                        }
                        return shopId;
                    }
                }
            }
            return -1;
        }).thenAccept(shopId -> {
            if (shopId == null || shopId < 0) {
                Bukkit.getScheduler().runTask(plugin, () -> Msg.error(player, "Failed to create shop!"));
                return;
            }

            ChestShop shop = new ChestShop(shopId, ownerUsername, player.getUniqueId(),
                    world, cx, cy, cz, itemMat.name(), itemData,
                    pending.price(), pending.shopType(), itemName);

            Bukkit.getScheduler().runTask(plugin, () -> {
                registerShop(shop);
                int stock = countStock(canonical, itemMat);
                placeSignsOnChest(canonical, shop, stock);
                Msg.success(player, Component.text("Shop created! Selling ")
                        .append(Component.text(itemName, GOLD))
                        .append(Component.text(" for "))
                        .append(EconomyManager.moneyText(pending.price()))
                        .append(Component.text(" each.")));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            });
        });
    }

    // ========== Buy/Sell Flow ==========

    public void startTransaction(Player player, ChestShop shop) {
        if (shop.getOwnerUsername().equals(player.getName().toLowerCase())) {
            Msg.info(player, "This is your own shop.");
            return;
        }
        pendingTransactions.put(player.getUniqueId(), new PendingTransaction(shop));
        if (shop.getShopType() == ChestShop.ShopType.BUY) {
            Msg.info(player, "Type the amount you want to buy in chat (or 'cancel'):");
        } else {
            Msg.info(player, "Type the amount you want to sell in chat (or 'cancel'):");
        }
    }

    public boolean hasPendingTransaction(UUID uuid) {
        return pendingTransactions.containsKey(uuid);
    }

    public void cancelPendingTransaction(UUID uuid) {
        pendingTransactions.remove(uuid);
    }

    public void handleTransactionInput(Player player, String input) {
        PendingTransaction pending = pendingTransactions.remove(player.getUniqueId());
        if (pending == null) return;

        if (input.equalsIgnoreCase("cancel")) {
            Msg.info(player, "Transaction cancelled.");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            Msg.error(player, "Invalid number! Transaction cancelled.");
            return;
        }
        if (amount <= 0) {
            Msg.error(player, "Amount must be greater than 0!");
            return;
        }

        ChestShop shop = pending.shop();
        if (!shopsById.containsKey(shop.getId())) {
            Msg.error(player, "This shop no longer exists!");
            return;
        }

        World w = Bukkit.getWorld(shop.getWorld());
        if (w == null) {
            Msg.error(player, "Shop world is not loaded!");
            return;
        }
        Block chestBlock = w.getBlockAt(shop.getX(), shop.getY(), shop.getZ());
        if (!(chestBlock.getState() instanceof Container)) {
            Msg.error(player, "Shop chest is missing!");
            return;
        }

        Material itemMat;
        try {
            itemMat = Material.valueOf(shop.getItemType());
        } catch (IllegalArgumentException e) {
            Msg.error(player, "Unknown item type!");
            return;
        }

        if (shop.getShopType() == ChestShop.ShopType.BUY) {
            handleBuy(player, shop, chestBlock, itemMat, amount);
        } else {
            handleSell(player, shop, chestBlock, itemMat, amount);
        }
    }

    private void handleBuy(Player player, ChestShop shop, Block chestBlock, Material itemMat, int amount) {
        Inventory inv = getFullInventory(chestBlock);
        int stock = countStock(chestBlock, itemMat);

        if (stock < amount) {
            Msg.error(player, "Not enough stock! Available: " + stock);
            return;
        }

        double totalCost = shop.getPrice() * amount;
        if (!economy.hasMoney(player.getUniqueId(), totalCost)) {
            Msg.error(player, Component.text("You need ")
                    .append(EconomyManager.moneyText(totalCost))
                    .append(Component.text(" but only have "))
                    .append(EconomyManager.moneyText(economy.getMoney(player.getUniqueId()))));
            return;
        }

        ItemStack template = deserializeItem(shop.getItemData());
        if (template == null) {
            template = new ItemStack(itemMat);
        }

        int removed = removeItemsFromChest(chestBlock, itemMat, amount);
        if (removed < amount) {
            Msg.error(player, "Could not remove items from chest. Try again.");
            return;
        }

        economy.removeMoney(player.getUniqueId(), totalCost);

        ItemStack toGive = template.clone();
        int remaining = amount;
        while (remaining > 0) {
            int batch = Math.min(remaining, toGive.getMaxStackSize());
            ItemStack give = toGive.clone();
            give.setAmount(batch);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(give);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= batch;
        }

        addToBank(shop.getId(), totalCost);

        int newStock = countStock(chestBlock, itemMat);
        updateSignStock(chestBlock, shop, newStock);

        Msg.success(player, Component.text("Bought " + amount + "x ")
                .append(Component.text(shop.getShopName(), GOLD))
                .append(Component.text(" for "))
                .append(EconomyManager.moneyText(totalCost)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        notifyOwner(shop, player.getName(), amount, totalCost, true);
    }

    private void handleSell(Player player, ChestShop shop, Block chestBlock, Material itemMat, int amount) {
        int playerHas = countPlayerItems(player, itemMat);
        if (playerHas < amount) {
            Msg.error(player, "You don't have enough items! You have: " + playerHas);
            return;
        }

        double totalPay = shop.getPrice() * amount;

        int shopId = shop.getId();
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT balance FROM su_chestshop_bank WHERE shop_id = ?")) {
                ps.setInt(1, shopId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("balance");
                }
            }
            return 0.0;
        }).thenAccept(bankBalance -> {
            if (bankBalance == null) bankBalance = 0.0;
            double bal = bankBalance;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (bal < totalPay) {
                    Msg.error(player, Component.text("Shop doesn't have enough funds! Bank: ")
                            .append(EconomyManager.moneyText(bal)));
                    return;
                }

                Inventory chestInv = getFullInventory(chestBlock);
                int freeSpace = countFreeSpace(chestInv, itemMat);
                if (freeSpace < amount) {
                    Msg.error(player, "Shop chest is full! Only " + freeSpace + " slots available.");
                    return;
                }

                removePlayerItems(player, itemMat, amount);
                addItemsToChest(chestBlock, itemMat, amount, shop);
                withdrawFromBank(shopId, totalPay);
                economy.addMoney(player.getUniqueId(), totalPay);

                int newStock = countStock(chestBlock, itemMat);
                updateSignStock(chestBlock, shop, newStock);

                Msg.success(player, Component.text("Sold " + amount + "x ")
                        .append(Component.text(shop.getShopName(), GOLD))
                        .append(Component.text(" for "))
                        .append(EconomyManager.moneyText(totalPay)));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

                notifyOwner(shop, player.getName(), amount, totalPay, false);
            });
        });
    }

    private void notifyOwner(ChestShop shop, String customerName, int amount, double total, boolean wasBuy) {
        Player owner = Bukkit.getPlayerExact(shop.getOwnerUsername());
        if (owner != null && owner.isOnline()) {
            String action = wasBuy ? "bought" : "sold";
            Msg.info(owner, Component.text(customerName + " " + action + " " + amount + "x ")
                    .append(Component.text(shop.getShopName(), GOLD))
                    .append(Component.text(wasBuy ? " — " : " — paid "))
                    .append(EconomyManager.moneyText(total)));
        }
    }

    // ========== Sign Placement ==========

    private void placeSignsOnChest(Block chestBlock, ChestShop shop, int stock) {
        Block placed = null;
        for (BlockFace face : SIGN_FACES) {
            Block adj = chestBlock.getRelative(face);
            if (adj.getType().isAir()) {
                placed = adj;
                placeSign(adj, face, shop, stock);
                break;
            }
        }

        Block otherHalf = getOtherChestHalf(chestBlock);
        if (otherHalf != null) {
            for (BlockFace face : SIGN_FACES) {
                Block adj = otherHalf.getRelative(face);
                if (adj.getType().isAir() && (placed == null || !adj.getLocation().equals(placed.getLocation()))) {
                    placeSign(adj, face, shop, stock);
                    break;
                }
            }
        }
    }

    private void placeSign(Block signBlock, BlockFace face, ChestShop shop, int stock) {
        signBlock.setType(Material.OAK_WALL_SIGN);
        org.bukkit.block.data.type.WallSign wallData =
                (org.bukkit.block.data.type.WallSign) signBlock.getBlockData();
        wallData.setFacing(face);
        signBlock.setBlockData(wallData);

        Sign sign = (Sign) signBlock.getState();

        String itemName = shop.getShopName();
        if (itemName.length() > SIGN_MAX_CHARS) {
            sign.line(0, Component.text(itemName.substring(0, SIGN_MAX_CHARS), GOLD));
            String signLocKey = locKey(signBlock);
            marqueeEntries.put(signLocKey, new MarqueeEntry(itemName));
        } else {
            sign.line(0, Component.text(itemName, GOLD));
        }

        String shopTypeLabel = shop.getShopType() == ChestShop.ShopType.BUY ? "[BUY]" : "[SELL]";
        sign.line(1, Component.text(shopTypeLabel + " Stock: " + stock, WHITE));

        sign.line(2, Component.text("$" + EconomyManager.format(shop.getPrice()) + " each", GREEN));

        sign.line(3, Component.text(shop.getOwnerUsername(), CYAN));

        sign.setWaxed(true);
        sign.update();
    }

    void updateSignStock(Block chestBlock, ChestShop shop, int stock) {
        updateSignsAround(chestBlock, shop, stock);
        Block other = getOtherChestHalf(chestBlock);
        if (other != null) updateSignsAround(other, shop, stock);
    }

    private void updateSignsAround(Block block, ChestShop shop, int stock) {
        for (BlockFace face : SIGN_FACES) {
            Block adj = block.getRelative(face);
            if (isShopSign(adj, block)) {
                Sign sign = (Sign) adj.getState();
                String shopTypeLabel = shop.getShopType() == ChestShop.ShopType.BUY ? "[BUY]" : "[SELL]";
                if (stock <= 0 && shop.getShopType() == ChestShop.ShopType.BUY) {
                    sign.line(1, Component.text("OUT OF STOCK", RED));
                } else {
                    sign.line(1, Component.text(shopTypeLabel + " Stock: " + stock, WHITE));
                }
                sign.update();
            }
        }
    }

    private boolean isShopSign(Block signBlock, Block chestBlock) {
        if (!isWallSign(signBlock.getType())) return false;
        if (!(signBlock.getBlockData() instanceof Directional dir)) return false;
        Block attached = signBlock.getRelative(dir.getFacing().getOppositeFace());
        return attached.getX() == chestBlock.getX()
                && attached.getY() == chestBlock.getY()
                && attached.getZ() == chestBlock.getZ();
    }

    // ========== Marquee ==========

    private void startMarquee() {
        if (marqueeTask != null) return;
        marqueeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMarquee, 10L, 10L);
    }

    private void tickMarquee() {
        for (var entry : marqueeEntries.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts.length != 4) continue;
            World w = Bukkit.getWorld(parts[0]);
            if (w == null) continue;
            int x, y, z;
            try {
                x = Integer.parseInt(parts[1]);
                y = Integer.parseInt(parts[2]);
                z = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) { continue; }

            Block block = w.getBlockAt(x, y, z);
            if (!isWallSign(block.getType())) {
                marqueeEntries.remove(entry.getKey());
                continue;
            }

            if (!block.getWorld().isChunkLoaded(x >> 4, z >> 4)) continue;

            MarqueeEntry me = entry.getValue();
            me.advance();
            Sign sign = (Sign) block.getState();
            sign.line(0, Component.text(me.currentFrame(), GOLD));
            sign.update();
        }
    }

    // ========== Inventory Helpers ==========

    Inventory getFullInventory(Block chestBlock) {
        if (chestBlock.getState() instanceof org.bukkit.block.Chest chestState) {
            return chestState.getInventory();
        }
        return ((Container) chestBlock.getState()).getInventory();
    }

    int countStock(Block chestBlock, Material itemMat) {
        Inventory inv = getFullInventory(chestBlock);
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == itemMat) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private int countPlayerItems(Player player, Material mat) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) count += item.getAmount();
        }
        return count;
    }

    private int removeItemsFromChest(Block chestBlock, Material mat, int amount) {
        Inventory inv = getFullInventory(chestBlock);
        int removed = 0;
        for (int i = 0; i < inv.getSize() && removed < amount; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() != mat) continue;
            int take = Math.min(item.getAmount(), amount - removed);
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) inv.setItem(i, null);
            removed += take;
        }
        return removed;
    }

    private void removePlayerItems(Player player, Material mat, int amount) {
        Inventory inv = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() != mat) continue;
            int take = Math.min(item.getAmount(), remaining);
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) inv.setItem(i, null);
            remaining -= take;
        }
    }

    private void addItemsToChest(Block chestBlock, Material mat, int amount, ChestShop shop) {
        Inventory inv = getFullInventory(chestBlock);
        ItemStack template = deserializeItem(shop.getItemData());
        if (template == null) template = new ItemStack(mat);

        int remaining = amount;
        while (remaining > 0) {
            int batch = Math.min(remaining, template.getMaxStackSize());
            ItemStack toAdd = template.clone();
            toAdd.setAmount(batch);
            inv.addItem(toAdd);
            remaining -= batch;
        }
    }

    private int countFreeSpace(Inventory inv, Material mat) {
        int maxStack = mat.getMaxStackSize();
        int free = 0;
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                free += maxStack;
            } else if (item.getType() == mat && item.getAmount() < maxStack) {
                free += maxStack - item.getAmount();
            }
        }
        return free;
    }

    private ItemStack findFirstItem(Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) return item;
        }
        return null;
    }

    // ========== Double Chest Helpers ==========

    Block getCanonicalChest(Block block) {
        if (!(block.getBlockData() instanceof Chest chestData)) return block;
        if (chestData.getType() == Chest.Type.SINGLE) return block;

        Block other = getOtherChestHalf(block);
        if (other == null) return block;

        if (chestData.getType() == Chest.Type.LEFT) return block;
        return other;
    }

    Block getOtherChestHalf(Block block) {
        if (!(block.getBlockData() instanceof Chest chestData)) return null;
        if (chestData.getType() == Chest.Type.SINGLE) return null;

        BlockFace facing = chestData.getFacing();
        BlockFace otherFace;
        if (chestData.getType() == Chest.Type.LEFT) {
            otherFace = switch (facing) {
                case NORTH -> BlockFace.EAST;
                case SOUTH -> BlockFace.WEST;
                case EAST -> BlockFace.SOUTH;
                case WEST -> BlockFace.NORTH;
                default -> null;
            };
        } else {
            otherFace = switch (facing) {
                case NORTH -> BlockFace.WEST;
                case SOUTH -> BlockFace.EAST;
                case EAST -> BlockFace.NORTH;
                case WEST -> BlockFace.SOUTH;
                default -> null;
            };
        }
        if (otherFace == null) return null;
        Block other = block.getRelative(otherFace);
        if (other.getType() == block.getType()) return other;
        return null;
    }

    // ========== Shop Lookup ==========

    public ChestShop getShopAt(Block block) {
        Block canonical = getCanonicalChest(block);
        return shopsByLocation.get(locationKey(canonical));
    }

    public ChestShop getShopBySignBlock(Block signBlock) {
        if (!isWallSign(signBlock.getType())) return null;
        if (!(signBlock.getBlockData() instanceof Directional dir)) return null;
        Block attached = signBlock.getRelative(dir.getFacing().getOppositeFace());
        if (!isChest(attached.getType())) return null;
        return getShopAt(attached);
    }

    public ChestShop getShopById(int id) {
        return shopsById.get(id);
    }

    public List<ChestShop> getShopsByOwner(String username) {
        String lower = username.toLowerCase();
        List<ChestShop> result = new ArrayList<>();
        for (ChestShop shop : shopsById.values()) {
            if (shop.getOwnerUsername().equals(lower)) result.add(shop);
        }
        return result;
    }

    public Collection<ChestShop> getAllShops() {
        return Collections.unmodifiableCollection(shopsById.values());
    }

    // ========== Shop Registration ==========

    private void registerShop(ChestShop shop) {
        shopsById.put(shop.getId(), shop);
        shopsByLocation.put(shop.locationKey(), shop);

        World w = Bukkit.getWorld(shop.getWorld());
        if (w != null) {
            Block chestBlock = w.getBlockAt(shop.getX(), shop.getY(), shop.getZ());
            for (BlockFace face : SIGN_FACES) {
                Block adj = chestBlock.getRelative(face);
                if (isWallSign(adj.getType())) {
                    if (isShopSign(adj, chestBlock)) {
                        String name = shop.getShopName();
                        if (name.length() > SIGN_MAX_CHARS) {
                            marqueeEntries.put(locKey(adj), new MarqueeEntry(name));
                        }
                    }
                }
            }
            Block other = getOtherChestHalf(chestBlock);
            if (other != null) {
                for (BlockFace face : SIGN_FACES) {
                    Block adj = other.getRelative(face);
                    if (isWallSign(adj.getType()) && isShopSign(adj, other)) {
                        String name = shop.getShopName();
                        if (name.length() > SIGN_MAX_CHARS) {
                            marqueeEntries.put(locKey(adj), new MarqueeEntry(name));
                        }
                    }
                }
            }
        }
    }

    public void removeShop(ChestShop shop) {
        shopsById.remove(shop.getId());
        shopsByLocation.remove(shop.locationKey());

        World w = Bukkit.getWorld(shop.getWorld());
        if (w != null) {
            Block chestBlock = w.getBlockAt(shop.getX(), shop.getY(), shop.getZ());
            removeSignsFromChest(chestBlock);
            Block other = getOtherChestHalf(chestBlock);
            if (other != null) removeSignsFromChest(other);
        }

        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM su_chestshops WHERE id = ?")) {
                ps.setInt(1, shop.getId());
                ps.executeUpdate();
            }
        });
    }

    private void removeSignsFromChest(Block chestBlock) {
        for (BlockFace face : SIGN_FACES) {
            Block adj = chestBlock.getRelative(face);
            if (isShopSign(adj, chestBlock)) {
                marqueeEntries.remove(locKey(adj));
                adj.setType(Material.AIR);
            }
        }
    }

    // ========== Bank Operations ==========

    private void addToBank(int shopId, double amount) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_chestshop_bank SET balance = balance + ? WHERE shop_id = ?")) {
                ps.setDouble(1, amount);
                ps.setInt(2, shopId);
                ps.executeUpdate();
            }
        });
    }

    private void withdrawFromBank(int shopId, double amount) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_chestshop_bank SET balance = balance - ? WHERE shop_id = ?")) {
                ps.setDouble(1, amount);
                ps.setInt(2, shopId);
                ps.executeUpdate();
            }
        });
    }

    public java.util.concurrent.CompletableFuture<Double> getShopBank(int shopId) {
        return db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT balance FROM su_chestshop_bank WHERE shop_id = ?")) {
                ps.setInt(1, shopId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble("balance");
                }
            }
            return 0.0;
        });
    }

    // ========== Serialization ==========

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
        if (data == null || data.isEmpty()) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
            return (ItemStack) bois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }

    // ========== Utilities ==========

    static String formatMaterialName(Material mat) {
        String raw = mat.name().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : raw.toCharArray()) {
            if (c == ' ') {
                sb.append(' ');
                cap = true;
            } else {
                sb.append(cap ? Character.toUpperCase(c) : Character.toLowerCase(c));
                cap = false;
            }
        }
        return sb.toString();
    }

    static boolean isChest(Material mat) {
        return mat == Material.CHEST || mat == Material.TRAPPED_CHEST;
    }

    static boolean isWallSign(Material mat) {
        return mat.name().endsWith("_WALL_SIGN");
    }

    private static String locationKey(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    private static String locKey(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    public boolean isShopChest(Block block) {
        if (!isChest(block.getType())) return false;
        return getShopAt(block) != null;
    }

    public boolean isShopSignBlock(Block block) {
        return getShopBySignBlock(block) != null;
    }

    public void cleanupPlayer(UUID uuid) {
        pendingCreations.remove(uuid);
        pendingTransactions.remove(uuid);
    }
}
