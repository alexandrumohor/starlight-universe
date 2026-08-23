package com.starlightuniverse;

import org.bukkit.plugin.java.JavaPlugin;

public final class StarlightUniverse extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("[SU] Enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("[SU] Disabled!");
    }
}
