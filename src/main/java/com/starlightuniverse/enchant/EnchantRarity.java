package com.starlightuniverse.enchant;

import net.kyori.adventure.text.format.TextColor;

public enum EnchantRarity {

    COMMON("Stardust Enchant", 0xAAAAAA),
    UNCOMMON("Starlight Enchant", 0x55FF55),
    RARE("Starborn Enchant", 0x5555FF),
    EPIC("Stellar Enchant", 0xAA00AA),
    LEGENDARY("Celestial Enchant", 0xFFAA00);

    private final String bookName;
    private final TextColor color;

    EnchantRarity(String bookName, int hex) {
        this.bookName = bookName;
        this.color = TextColor.color(hex);
    }

    public String getBookName() { return bookName; }
    public TextColor getColor() { return color; }
}
