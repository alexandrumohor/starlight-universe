package com.starlightuniverse.minigame;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ActiveMinigame {

    private final MinigameType type;
    private final String prompt;
    private final String primaryAnswer;
    private final Set<String> acceptedAnswers;
    private final long startTime;

    public ActiveMinigame(MinigameType type, String prompt, String primaryAnswer, Collection<String> acceptedAnswers) {
        this.type = type;
        this.prompt = prompt;
        this.primaryAnswer = primaryAnswer;
        this.acceptedAnswers = new HashSet<>();
        for (String a : acceptedAnswers) {
            if (a != null) this.acceptedAnswers.add(a.trim().toLowerCase());
        }
        this.startTime = System.currentTimeMillis();
    }

    public MinigameType getType() { return type; }
    public String getPrompt() { return prompt; }
    public String getPrimaryAnswer() { return primaryAnswer; }
    public long getStartTime() { return startTime; }

    public boolean isCorrect(String input) {
        if (input == null) return false;
        return acceptedAnswers.contains(input.trim().toLowerCase());
    }
}
