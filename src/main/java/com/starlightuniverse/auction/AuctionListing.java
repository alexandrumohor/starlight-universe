package com.starlightuniverse.auction;

public class AuctionListing {

    private int id;
    private String sellerUsername;
    private String itemData;
    private String itemMaterial;
    private int itemAmount;
    private int remainingAmount;
    private double pricePerUnit;
    private long listedDate;
    private long expireDate;
    private boolean active;
    private boolean collected;

    public AuctionListing(int id, String sellerUsername, String itemData, String itemMaterial,
                          int itemAmount, int remainingAmount, double pricePerUnit,
                          long listedDate, long expireDate, boolean active, boolean collected) {
        this.id = id;
        this.sellerUsername = sellerUsername;
        this.itemData = itemData;
        this.itemMaterial = itemMaterial;
        this.itemAmount = itemAmount;
        this.remainingAmount = remainingAmount;
        this.pricePerUnit = pricePerUnit;
        this.listedDate = listedDate;
        this.expireDate = expireDate;
        this.active = active;
        this.collected = collected;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getSellerUsername() { return sellerUsername; }
    public String getItemData() { return itemData; }
    public String getItemMaterial() { return itemMaterial; }
    public int getItemAmount() { return itemAmount; }
    public int getRemainingAmount() { return remainingAmount; }
    public void setRemainingAmount(int remainingAmount) { this.remainingAmount = remainingAmount; }
    public double getPricePerUnit() { return pricePerUnit; }
    public long getListedDate() { return listedDate; }
    public long getExpireDate() { return expireDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isCollected() { return collected; }
    public void setCollected(boolean collected) { this.collected = collected; }
}
