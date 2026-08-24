package com.starlightuniverse;

import com.starlightuniverse.admin.*;
import com.starlightuniverse.arena.*;
import com.starlightuniverse.auction.*;
import com.starlightuniverse.auth.*;
import com.starlightuniverse.boss.*;
import com.starlightuniverse.chat.*;
import com.starlightuniverse.crate.*;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.enchant.*;
import com.starlightuniverse.job.*;
import com.starlightuniverse.minigame.*;
import com.starlightuniverse.mob.*;
import com.starlightuniverse.skill.*;
import com.starlightuniverse.economy.*;
import com.starlightuniverse.home.*;
import com.starlightuniverse.order.*;
import com.starlightuniverse.premium.*;
import com.starlightuniverse.pvp.*;
import com.starlightuniverse.shop.*;
import com.starlightuniverse.spear.*;
import com.starlightuniverse.starshop.*;
import com.starlightuniverse.team.*;
import com.starlightuniverse.world.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
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
    private OrderManager orderManager;
    private AdminManager adminManager;
    private HomeManager homeManager;
    private PremiumManager premiumManager;
    private TeamManager teamManager;
    private ChatManager chatManager;
    private CrateManager crateManager;
    private JobManager jobManager;
    private SkillManager skillManager;
    private EnchantManager enchantManager;
    private EnchantListener enchantListener;
    private AlchemistListener alchemistListener;
    private StarShopManager starShopManager;
    private ArenaWorldManager arenaWorldManager;
    private PvPManager pvpManager;
    private BossKillManager bossKillManager;
    private MobRaidManager mobRaidManager;
    private MinigameManager minigameManager;

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

        orderManager = new OrderManager(this, economyManager, databaseManager, shopManager);
        orderManager.initialize();

        adminManager = new AdminManager(this, databaseManager);

        homeManager = new HomeManager(this, databaseManager, economyManager, adminManager);
        homeManager.initialize();
        homeManager.loadGolems();

        premiumManager = new PremiumManager(this, databaseManager, economyManager, adminManager);
        premiumManager.initialize();

        teamManager = new TeamManager(this, databaseManager, economyManager);
        teamManager.initialize();

        chatManager = new ChatManager(this, databaseManager, adminManager, premiumManager, teamManager);

        crateManager = new CrateManager(this, databaseManager, economyManager, adminManager);
        crateManager.initialize();

        jobManager = new JobManager(this, databaseManager, economyManager);
        jobManager.initialize();

        skillManager = new SkillManager(this, databaseManager, economyManager);
        skillManager.initialize();

        enchantManager = new EnchantManager(this);

        Bukkit.getPluginManager().registerEvents(new AuthListener(this, authManager, skinManager), this);
        Bukkit.getPluginManager().registerEvents(new AdminListener(this, adminManager), this);
        Bukkit.getPluginManager().registerEvents(worldManager, this);
        Bukkit.getPluginManager().registerEvents(lobbyManager, this);
        Bukkit.getPluginManager().registerEvents(new EconomyListener(economyManager), this);
        Bukkit.getPluginManager().registerEvents(new ShopListener(this, shopManager), this);
        Bukkit.getPluginManager().registerEvents(new AuctionListener(this, auctionManager), this);
        Bukkit.getPluginManager().registerEvents(new OrderListener(this, orderManager), this);
        Bukkit.getPluginManager().registerEvents(new HomeListener(this, homeManager), this);
        Bukkit.getPluginManager().registerEvents(new PremiumListener(this, premiumManager, adminManager, economyManager), this);
        Bukkit.getPluginManager().registerEvents(new TeamListener(this, teamManager), this);

        ChatListener chatListener = new ChatListener(chatManager);
        Bukkit.getPluginManager().registerEvents(chatListener, this);

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
        Bukkit.getCommandMap().register("starlightuniverse", new OrderCommand(orderManager));

        for (Command cmd : RankCommands.create(adminManager, this))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);
        for (Command cmd : BanCommands.create(adminManager, this))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);
        for (Command cmd : MuteWarnCommands.create(adminManager, this))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);
        for (Command cmd : PunishCommands.create(adminManager))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);
        for (Command cmd : TeleportCommands.create(adminManager))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);
        for (Command cmd : InspectCommands.create(adminManager, this))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);
        for (Command cmd : StaffToolCommands.create(adminManager, this))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);
        for (Command cmd : ReportCommands.create(adminManager, this))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);
        for (Command cmd : PasswordNameCommands.create(adminManager, this))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);

        for (Command cmd : HomeCommands.create(homeManager))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);
        Bukkit.getCommandMap().register("starlightuniverse", new HomeProtectCommand(homeManager));

        for (Command cmd : PremiumCommands.create(premiumManager))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);

        for (Command cmd : TeamCommand.create(teamManager))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);
        Bukkit.getCommandMap().register("starlightuniverse", new TeamPvPCommand(teamManager));

        for (Command cmd : ChatCommands.create(chatManager, chatListener))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);

        Bukkit.getPluginManager().registerEvents(new CrateListener(crateManager), this);
        for (Command cmd : CrateCommands.create(crateManager))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);

        Bukkit.getPluginManager().registerEvents(new JobListener(this, jobManager, authManager), this);
        Bukkit.getPluginManager().registerEvents(new JobDataListener(jobManager), this);
        Bukkit.getCommandMap().register("starlightuniverse", new JobCommand(jobManager));

        Bukkit.getPluginManager().registerEvents(new SkillListener(this, skillManager, authManager, jobManager), this);
        Bukkit.getPluginManager().registerEvents(new SkillDataListener(skillManager), this);
        Bukkit.getCommandMap().register("starlightuniverse", new SkillCommand(skillManager));

        enchantListener = new EnchantListener(this, enchantManager, authManager, economyManager);
        Bukkit.getPluginManager().registerEvents(enchantListener, this);
        Bukkit.getPluginManager().registerEvents(new EnchantGuiListener(), this);
        Bukkit.getPluginManager().registerEvents(new EnchantTableListener(enchantManager, economyManager, authManager), this);
        alchemistListener = new AlchemistListener(this, enchantManager, authManager);
        Bukkit.getPluginManager().registerEvents(alchemistListener, this);
        for (Command cmd : EnchantCommand.create(enchantManager))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);
        Bukkit.getCommandMap().register("starlightuniverse", new AlchemistCommand(alchemistListener));

        Bukkit.getCommandMap().register("starlightuniverse", new SpearCommand());

        starShopManager = new StarShopManager(economyManager, enchantManager, crateManager);
        Bukkit.getPluginManager().registerEvents(new StarShopListener(this, starShopManager), this);
        Bukkit.getCommandMap().register("starlightuniverse", new StarShopCommand(starShopManager));

        arenaWorldManager = new ArenaWorldManager(this, databaseManager);
        arenaWorldManager.initialize();
        Bukkit.getPluginManager().registerEvents(new ArenaWorldListener(this, adminManager), this);

        pvpManager = new PvPManager(this, databaseManager, economyManager, arenaWorldManager);
        pvpManager.start();
        Bukkit.getPluginManager().registerEvents(new PvPListener(pvpManager), this);
        Bukkit.getCommandMap().register("starlightuniverse", new PvPCommand(pvpManager));

        bossKillManager = new BossKillManager(this, economyManager, crateManager, arenaWorldManager);
        bossKillManager.start();
        Bukkit.getPluginManager().registerEvents(new BossKillListener(bossKillManager), this);
        Bukkit.getCommandMap().register("starlightuniverse", new BossKillCommand(bossKillManager, adminManager));

        mobRaidManager = new MobRaidManager(this, databaseManager, economyManager, arenaWorldManager);
        mobRaidManager.start();
        Bukkit.getPluginManager().registerEvents(new MobRaidListener(mobRaidManager), this);
        Bukkit.getCommandMap().register("starlightuniverse", new MobRaidCommand(mobRaidManager, adminManager));

        minigameManager = new MinigameManager(this, economyManager, authManager);
        Bukkit.getPluginManager().registerEvents(new MinigameListener(minigameManager), this);
        Bukkit.getCommandMap().register("starlightuniverse", new MinigameCommand(minigameManager));
        minigameManager.start();

        getLogger().info("[SU] Enabled!");
    }

    @Override
    public void onDisable() {
        if (minigameManager != null) {
            minigameManager.shutdown();
        }

        if (mobRaidManager != null) {
            mobRaidManager.shutdown();
        }

        if (bossKillManager != null) {
            bossKillManager.shutdown();
        }

        if (pvpManager != null) {
            pvpManager.shutdown();
        }

        if (arenaWorldManager != null) {
            arenaWorldManager.shutdown();
        }

        if (enchantListener != null) {
            enchantListener.shutdown();
        }

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

        if (skillManager != null) {
            skillManager.shutdown();
        }

        if (jobManager != null) {
            jobManager.shutdown();
        }

        if (crateManager != null) {
            crateManager.shutdown();
        }

        if (teamManager != null) {
            teamManager.shutdown();
        }

        if (premiumManager != null) {
            premiumManager.shutdown();
        }

        if (homeManager != null) {
            homeManager.shutdown();
        }

        if (orderManager != null) {
            orderManager.shutdown();
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

    public OrderManager getOrderManager() {
        return orderManager;
    }

    public AdminManager getAdminManager() {
        return adminManager;
    }

    public HomeManager getHomeManager() {
        return homeManager;
    }

    public PremiumManager getPremiumManager() {
        return premiumManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public CrateManager getCrateManager() {
        return crateManager;
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public EnchantManager getEnchantManager() {
        return enchantManager;
    }

    public StarShopManager getStarShopManager() {
        return starShopManager;
    }

    public PvPManager getPvpManager() {
        return pvpManager;
    }

    public ArenaWorldManager getArenaWorldManager() {
        return arenaWorldManager;
    }

    public BossKillManager getBossKillManager() {
        return bossKillManager;
    }

    public MobRaidManager getMobRaidManager() {
        return mobRaidManager;
    }

    public MinigameManager getMinigameManager() {
        return minigameManager;
    }
}
