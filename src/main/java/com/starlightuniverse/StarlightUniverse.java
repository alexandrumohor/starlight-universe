package com.starlightuniverse;

import com.starlightuniverse.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class StarlightUniverse extends JavaPlugin {

    private static StarlightUniverse instance;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        instance = this;

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("[SU] Database initialization failed! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("[SU] Enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("[SU] Disabled!");
    }

    public static StarlightUniverse getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
