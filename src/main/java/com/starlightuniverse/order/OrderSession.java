package com.starlightuniverse.order;

import com.starlightuniverse.shop.ShopCategory;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class OrderSession {

    enum State {
        MAIN_MENU,
        BROWSE,
        MY_ORDERS,
        CREATE_CATEGORY,
        CREATE_ITEMS,
        CREATE_SETUP,
        DELIVER,
        DELIVER_CONFIRM,
        STORAGE
    }

    State state;
    int page;

    // Browse / My Orders
    List<Order> filteredCache;

    // Create
    ShopCategory createCategory;
    List<Material> createItemList;
    Material selectedMaterial;
    int createQuantity;
    double createPrice;

    // Deliver
    int deliverOrderId;
    ItemStack[] deliveryContents;
    int deliveryMatchCount;

    // Storage
    List<OrderStorageItem> storageCache;

    // Search
    boolean awaitingSearch;

    OrderSession(State state) {
        this.state = state;
        this.page = 0;
        this.createQuantity = 1;
        this.createPrice = 100;
        this.deliverOrderId = -1;
    }
}
