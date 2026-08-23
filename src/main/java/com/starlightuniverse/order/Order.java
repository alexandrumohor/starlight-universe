package com.starlightuniverse.order;

public class Order {

    private int id;
    private String creatorUsername;
    private String itemMaterial;
    private int itemAmount;
    private int deliveredAmount;
    private double pricePerUnit;
    private double escrowAmount;
    private long createdDate;
    private boolean active;

    public Order(int id, String creatorUsername, String itemMaterial, int itemAmount,
                 int deliveredAmount, double pricePerUnit, double escrowAmount,
                 long createdDate, boolean active) {
        this.id = id;
        this.creatorUsername = creatorUsername;
        this.itemMaterial = itemMaterial;
        this.itemAmount = itemAmount;
        this.deliveredAmount = deliveredAmount;
        this.pricePerUnit = pricePerUnit;
        this.escrowAmount = escrowAmount;
        this.createdDate = createdDate;
        this.active = active;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getCreatorUsername() { return creatorUsername; }
    public String getItemMaterial() { return itemMaterial; }
    public int getItemAmount() { return itemAmount; }
    public int getDeliveredAmount() { return deliveredAmount; }
    public void setDeliveredAmount(int deliveredAmount) { this.deliveredAmount = deliveredAmount; }
    public int getRemainingAmount() { return itemAmount - deliveredAmount; }
    public double getPricePerUnit() { return pricePerUnit; }
    public double getEscrowAmount() { return escrowAmount; }
    public void setEscrowAmount(double escrowAmount) { this.escrowAmount = escrowAmount; }
    public long getCreatedDate() { return createdDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
