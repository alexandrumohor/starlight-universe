package com.starlightuniverse.team;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TeamHolder implements InventoryHolder {

    public enum Type {
        TEAM_LIST, TEAM_INFO, TEAM_VAULT, TEAM_MISSIONS, TEAM_TOP
    }

    private final Type type;
    private Inventory inventory;
    private int page;
    private int teamId;

    public TeamHolder(Type type) {
        this.type = type;
    }

    public Type getType() { return type; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getTeamId() { return teamId; }
    public void setTeamId(int teamId) { this.teamId = teamId; }

    @Override
    public Inventory getInventory() { return inventory; }
    public void setInventory(Inventory inv) { this.inventory = inv; }
}
