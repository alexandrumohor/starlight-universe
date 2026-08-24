package com.starlightuniverse.minigame;

public enum MinigameType {
    SCRAMBLED_WORD("Scrambled Word", "Unscramble the word!"),
    MATH("Math", "Solve the math problem!"),
    FILL_THE_BLANK("Fill the Blank", "Fill in the blank!"),
    TYPE_RACE("Type Race", "Type the phrase exactly!"),
    TRIVIA("Trivia", "Answer the trivia!"),
    REVERSE_WORD("Reverse Word", "Type the word forwards!"),
    COLOR_CODE("Color Code", "Match the color!"),
    COUNT("Count", "Count it correctly!"),
    CAPITAL("Capital", "Name the capital city!"),
    ITEM_GUESS("Item Guess", "Guess the Minecraft item!");

    private final String displayName;
    private final String description;

    MinigameType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
