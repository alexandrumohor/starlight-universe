package com.starlightuniverse.enchant;

import net.kyori.adventure.text.format.TextColor;

public enum EnchantRarity {

    COMMON("Stardust Enchant", 0xAAAAAA, 90),
    UNCOMMON("Starlight Enchant", 0x55FF55, 80),
    RARE("Starborn Enchant", 0x5555FF, 60),
    EPIC("Stellar Enchant", 0xAA00AA, 50),
    LEGENDARY("Celestial Enchant", 0xFFAA00, 20);

    private final String bookName;
    private final TextColor color;
    private final int successRate;

    EnchantRarity(String bookName, int hex, int successRate) {
        this.bookName = bookName;
        this.color = TextColor.color(hex);
        this.successRate = successRate;
    }

    public String getBookName() { return bookName; }
    public TextColor getColor() { return color; }
    public int getSuccessRate() { return successRate; }
}
