package com.starlightuniverse.emoji;

public enum Emoji {
    SMILE("smile", '\uE100', "Faces"),
    LAUGH("laugh", '\uE101', "Faces"),
    HAPPY("happy", '\uE102', "Faces"),
    WINK("wink", '\uE103', "Faces"),
    LOVE("love", '\uE104', "Faces"),
    KISS("kiss", '\uE105', "Faces"),
    CRY("cry", '\uE106', "Faces"),
    SAD("sad", '\uE107', "Faces"),
    ANGRY("angry", '\uE108', "Faces"),
    RAGE("rage", '\uE109', "Faces"),
    COOL("cool", '\uE10A', "Faces"),
    THINK("think", '\uE10B', "Faces"),

    HEART("heart", '\uE10C', "Symbols"),
    BROKEN("broken", '\uE10D', "Symbols"),
    STAR("star", '\uE10E', "Symbols"),
    SPARKLE("sparkle", '\uE10F', "Symbols"),
    FIRE("fire", '\uE110', "Symbols"),
    WATER("water", '\uE111', "Symbols"),
    ICE("ice", '\uE112', "Symbols"),
    LIGHTNING("lightning", '\uE113', "Symbols"),
    MOON("moon", '\uE114', "Symbols"),
    SUN("sun", '\uE115', "Symbols"),
    MUSIC("music", '\uE116', "Symbols"),
    CROWN("crown", '\uE117', "Symbols"),

    THUMBS_UP("thumbsup", '\uE118', "Hands"),
    THUMBS_DOWN("thumbsdown", '\uE119', "Hands"),
    CLAP("clap", '\uE11A', "Hands"),
    WAVE("wave", '\uE11B', "Hands"),
    POINT("point", '\uE11C', "Hands"),
    OK_HAND("ok", '\uE11D', "Hands"),
    FIST("fist", '\uE11E', "Hands"),
    PRAY("pray", '\uE11F', "Hands"),

    SWORD("sword", '\uE120', "Game"),
    SHIELD("shield", '\uE121', "Game"),
    BOW("bow", '\uE122', "Game"),
    POTION("potion", '\uE123', "Game"),
    PICKAXE("pickaxe", '\uE124', "Game"),
    DIAMOND("diamond", '\uE125', "Game"),
    EMERALD("emerald", '\uE126', "Game"),
    GOLD("gold", '\uE127', "Game"),

    CHECK("check", '\uE128', "Misc"),
    CROSS("cross", '\uE129', "Misc"),
    WARNING("warning", '\uE12A', "Misc"),
    QUESTION("question", '\uE12B', "Misc"),
    ROCKET("rocket", '\uE12C', "Misc");

    public static final int UNLOCK_GEM_COST = 200;

    private final String name;
    private final char unicode;
    private final String category;

    Emoji(String name, char unicode, String category) {
        this.name = name;
        this.unicode = unicode;
        this.category = category;
    }

    public String getName() { return name; }
    public char getUnicode() { return unicode; }
    public String getUnicodeString() { return String.valueOf(unicode); }
    public String getCategory() { return category; }
    public String getToken() { return ":" + name + ":"; }

    public static Emoji byName(String name) {
        String n = name.toLowerCase();
        for (Emoji e : values()) if (e.name.equals(n)) return e;
        return null;
    }

    public static Emoji byUnicode(char c) {
        for (Emoji e : values()) if (e.unicode == c) return e;
        return null;
    }
}
