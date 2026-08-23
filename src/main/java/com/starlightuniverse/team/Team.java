package com.starlightuniverse.team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Team {

    private final int id;
    private String name;
    private String leaderUsername;
    private final List<String> colors;
    private boolean friendlyFire;
    private int level;
    private long xp;
    private double bankMoney;
    private double bankGems;
    private double bankStars;
    private final Map<String, TeamRank> members;
    private final Set<Integer> allyIds;

    public Team(int id, String name, String leaderUsername) {
        this.id = id;
        this.name = name;
        this.leaderUsername = leaderUsername;
        this.colors = new ArrayList<>();
        this.colors.add("#FFFFFF");
        this.level = 1;
        this.xp = 0;
        this.members = new ConcurrentHashMap<>();
        this.allyIds = ConcurrentHashMap.newKeySet();
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLeaderUsername() { return leaderUsername; }
    public void setLeaderUsername(String leader) { this.leaderUsername = leader; }
    public List<String> getColors() { return colors; }

    public void setColors(List<String> newColors) {
        colors.clear();
        colors.addAll(newColors);
    }

    public boolean isFriendlyFire() { return friendlyFire; }
    public void setFriendlyFire(boolean ff) { this.friendlyFire = ff; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public long getXp() { return xp; }
    public void setXp(long xp) { this.xp = xp; }
    public double getBankMoney() { return bankMoney; }
    public void setBankMoney(double m) { this.bankMoney = m; }
    public double getBankGems() { return bankGems; }
    public void setBankGems(double g) { this.bankGems = g; }
    public double getBankStars() { return bankStars; }
    public void setBankStars(double s) { this.bankStars = s; }
    public Map<String, TeamRank> getMembers() { return members; }
    public Set<Integer> getAllyIds() { return allyIds; }

    public int getMemberCount() { return members.size(); }

    public TeamRank getMemberRank(String username) {
        return members.get(username.toLowerCase());
    }

    public int getMaxAllies() {
        return level >= 25 ? 4 : 3;
    }

    public boolean hasMoneyBonus() { return level >= 5; }
    public double getMoneyBonusPercent() { return level >= 5 ? 5.0 : 0; }
    public boolean hasVault() { return level >= 10; }
    public boolean hasXpBonus() { return level >= 15; }
    public double getXpBonusPercent() { return level >= 15 ? 10.0 : 0; }
    public boolean hasBanner() { return level >= 20; }
    public boolean hasTerritory() { return level >= 30; }
    public boolean hasParticleBorder() { return level >= 50; }

    public static long xpForLevel(int level) {
        if (level <= 1) return 0;
        return 500L * level * (level - 1);
    }

    public static int levelForXp(long xp) {
        int level = 1;
        while (xpForLevel(level + 1) <= xp) level++;
        return level;
    }
}
