package com.starlightuniverse.shop;

import java.util.List;

public class ShopSession {

    enum State {
        MAIN_MENU, CATEGORY, BUY, SEARCH_RESULTS
    }

    State state;
    ShopCategory category;
    int page;
    ShopItem selectedItem;
    int quantity;
    List<ShopItem> searchResults;
    int dealDiscount;
    State returnTo;

    ShopSession(State state) {
        this.state = state;
        this.page = 0;
        this.quantity = 1;
        this.dealDiscount = 0;
        this.returnTo = State.MAIN_MENU;
    }
}
