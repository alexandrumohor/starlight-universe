package com.starlightuniverse;

import com.starlightuniverse.auction.*;
import com.starlightuniverse.auth.*;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.*;
import com.starlightuniverse.shop.*;
import com.starlightuniverse.world.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class StarlightUniverse extends JavaPlugin {

    private static StarlightUniverse instance;
    private DatabaseManager databaseManager;
    private AuthManager authManager;
    private SkinManager skinManager;
    private WorldManager worldManager;
    private InventoryManager inventoryManager;
    private QueueManager queueManager;
    private LobbyManager lobbyManager;
    private EconomyManager economyManager;
    private ShopManager shopManager;
    private AuctionManager auctionManager;

    @Override
    public void onEnable() {
        instance = this;

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("[SU] Database initialization failed! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        authManager = new AuthManager(databaseManager);

        skinManager = new SkinManager(this);
        skinManager.loadRandomSkins();

        worldManager = new WorldManager(this, databaseManager);
        worldManager.initialize();

        inventoryManager = new InventoryManager(this, databaseManager);

        queueManager = new QueueManager(this, databaseManager);
        queueManager.start();

        lobbyManager = new LobbyManager(this, queueManager);

        economyManager = new EconomyManager(databaseManager);

        shopManager = new ShopManager(this, economyManager);

        auctionManager = new AuctionManager(this, economyManager, databaseManager);
        auctionManager.initialize();

        Bukkit.getPluginManager().registerEvents(new AuthListener(this, authManager, skinManager), this);
        Bukkit.getPluginManager().registerEvents(worldManager, this);
        Bukkit.getPluginManager().registerEvents(lobbyManager, this);
        Bukkit.getPluginManager().registerEvents(new EconomyListener(economyManager), this);
        Bukkit.getPluginManager().registerEvents(new ShopListener(this, shopManager), this);
        Bukkit.getPluginManager().registerEvents(new AuctionListener(this, auctionManager), this);

        Bukkit.getCommandMap().register("starlightuniverse", new RegisterCommand(this, authManager));
        Bukkit.getCommandMap().register("starlightuniverse", new LoginCommand(this, authManager));
        Bukkit.getCommandMap().register("starlightuniverse", new ChangePassCommand(this, authManager));
        Bukkit.getCommandMap().register("starlightuniverse", new BalCommand(economyManager));
        Bukkit.getCommandMap().register("starlightuniverse", new PayCommand(economyManager));
        Bukkit.getCommandMap().register("starlightuniverse", new GiveMoneyCommand(this, economyManager));
        Bukkit.getCommandMap().register("starlightuniverse", new GiveGemsCommand(this, economyManager));
        Bukkit.getCommandMap().register("starlightuniverse", new GiveStarsCommand(this, economyManager));
        Bukkit.getCommandMap().register("starlightuniverse", new ShopCommand(shopManager));
        Bukkit.getCommandMap().register("starlightuniverse", new AuctionCommand(auctionManager));

        getLogger().info("[SU] Enabled!");
    }

    @Override
    public void onDisable() {
        if (inventoryManager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (authManager != null && authManager.isAuthenticated(player.getUniqueId())) {
                    WorldManager.WorldGroup group = WorldManager.getWorldGroup(player.getWorld());
                    if (group != WorldManager.WorldGroup.UNKNOWN) {
                        inventoryManager.saveInventorySync(player, group);
                    }
                }
            }
        }

        if (auctionManager != null) {
            auctionManager.shutdown();
        }

        if (queueManager != null) {
            queueManager.stop();
        }

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

    public AuthManager getAuthManager() {
        return authManager;
    }

    public SkinManager getSkinManager() {
        return skinManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }
}
