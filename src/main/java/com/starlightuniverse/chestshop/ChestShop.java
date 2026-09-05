package com.starlightuniverse.chestshop;

import java.util.UUID;

public class ChestShop {

    public enum ShopType { BUY, SELL }

    private final int id;
    private final String ownerUsername;
    private UUID ownerUuid;
    private final String world;
    private final int x, y, z;
    private String itemType;
    private String itemData;
    private double price;
    private ShopType shopType;
    private String shopName;

    public ChestShop(int id, String ownerUsername, UUID ownerUuid,
                     String world, int x, int y, int z,
                     String itemType, String itemData,
                     double price, ShopType shopType, String shopName) {
        this.id = id;
        this.ownerUsername = ownerUsername;
        this.ownerUuid = ownerUuid;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.itemType = itemType;
        this.itemData = itemData;
        this.price = price;
        this.shopType = shopType;
        this.shopName = shopName;
    }

    public int getId() { return id; }
    public String getOwnerUsername() { return ownerUsername; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public void setOwnerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; }
    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public String getItemData() { return itemData; }
    public void setItemData(String itemData) { this.itemData = itemData; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public ShopType getShopType() { return shopType; }
    public void setShopType(ShopType shopType) { this.shopType = shopType; }
    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }

    public String locationKey() {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
