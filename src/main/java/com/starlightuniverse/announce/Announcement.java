package com.starlightuniverse.announce;

public class Announcement {

    private final int id;
    private String message;
    private AnnouncementType type;
    private int frequencyMinutes;
    private int durationSeconds;
    private boolean enabled;
    private volatile long lastBroadcastMillis;

    public Announcement(int id, String message, AnnouncementType type,
                        int frequencyMinutes, int durationSeconds, boolean enabled) {
        this.id = id;
        this.message = message;
        this.type = type;
        this.frequencyMinutes = frequencyMinutes;
        this.durationSeconds = durationSeconds;
        this.enabled = enabled;
        this.lastBroadcastMillis = System.currentTimeMillis();
    }

    public int getId() { return id; }
    public String getMessage() { return message; }
    public AnnouncementType getType() { return type; }
    public int getFrequencyMinutes() { return frequencyMinutes; }
    public int getDurationSeconds() { return durationSeconds; }
    public boolean isEnabled() { return enabled; }
    public long getLastBroadcastMillis() { return lastBroadcastMillis; }

    public void setMessage(String message) { this.message = message; }
    public void setType(AnnouncementType type) { this.type = type; }
    public void setFrequencyMinutes(int frequencyMinutes) { this.frequencyMinutes = frequencyMinutes; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setLastBroadcastMillis(long millis) { this.lastBroadcastMillis = millis; }
}
