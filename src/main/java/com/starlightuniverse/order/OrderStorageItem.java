package com.starlightuniverse.order;

public class OrderStorageItem {

    private int id;
    private String username;
    private String itemData;
    private String itemMaterial;
    private int itemAmount;
    private long storedDate;

    public OrderStorageItem(int id, String username, String itemData, String itemMaterial,
                            int itemAmount, long storedDate) {
        this.id = id;
        this.username = username;
        this.itemData = itemData;
        this.itemMaterial = itemMaterial;
        this.itemAmount = itemAmount;
        this.storedDate = storedDate;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getItemData() { return itemData; }
    public String getItemMaterial() { return itemMaterial; }
    public int getItemAmount() { return itemAmount; }
    public long getStoredDate() { return storedDate; }
}
