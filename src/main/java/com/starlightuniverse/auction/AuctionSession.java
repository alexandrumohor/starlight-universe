package com.starlightuniverse.auction;

import com.starlightuniverse.shop.ShopCategory;

import java.util.List;

public class AuctionSession {

    enum State {
        BROWSE, BUY, MY_LISTINGS, COLLECT, CATEGORY_FILTER
    }

    enum SortMode {
        NEWEST("Newest First"),
        CHEAPEST("Cheapest First"),
        MOST_EXPENSIVE("Most Expensive First");

        private final String display;

        SortMode(String display) { this.display = display; }

        public String display() { return display; }

        public SortMode next() {
            SortMode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    State state;
    SortMode sortMode;
    ShopCategory filterCategory;
    int page;
    int selectedListingId;
    int buyQuantity;
    List<AuctionListing> filteredCache;

    AuctionSession(State state) {
        this.state = state;
        this.sortMode = SortMode.NEWEST;
        this.filterCategory = null;
        this.page = 0;
        this.selectedListingId = -1;
        this.buyQuantity = 1;
    }
}
