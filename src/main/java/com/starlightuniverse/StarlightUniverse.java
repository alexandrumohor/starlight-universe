package com.starlightuniverse;

import com.starlightuniverse.admin.*;
import com.starlightuniverse.announce.*;
import com.starlightuniverse.booster.*;
import com.starlightuniverse.border.*;
import com.starlightuniverse.anticheat.*;
import com.starlightuniverse.antigrief.*;
import com.starlightuniverse.arena.*;
import com.starlightuniverse.auction.*;
import com.starlightuniverse.auth.*;
import com.starlightuniverse.benefit.*;
import com.starlightuniverse.boss.*;
import com.starlightuniverse.chat.*;
import com.starlightuniverse.chestshop.*;
import com.starlightuniverse.crate.*;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.diag.*;
import com.starlightuniverse.emoji.*;
import com.starlightuniverse.enchant.*;
import com.starlightuniverse.hottime.*;
import com.starlightuniverse.logging.*;
import com.starlightuniverse.job.*;
import com.starlightuniverse.maintenance.*;
import com.starlightuniverse.minigame.*;
import com.starlightuniverse.mob.*;
import com.starlightuniverse.nameplate.*;
import com.starlightuniverse.pack.*;
import com.starlightuniverse.skill.*;
import com.starlightuniverse.economy.*;
import com.starlightuniverse.home.*;
import com.starlightuniverse.order.*;
import com.starlightuniverse.premium.*;
import com.starlightuniverse.pvp.*;
import com.starlightuniverse.pwarp.*;
import com.starlightuniverse.scoreboard.*;
import com.starlightuniverse.shop.*;
import com.starlightuniverse.spawner.*;
import com.starlightuniverse.spear.*;
import com.starlightuniverse.starshop.*;
import com.starlightuniverse.buff.*;
import com.starlightuniverse.tool.*;
import com.starlightuniverse.team.*;
import com.starlightuniverse.travel.*;
import com.starlightuniverse.vote.*;
import com.starlightuniverse.voucher.*;
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
    private PremiumSessionVerifier premiumSessionVerifier;
    private WorldManager worldManager;
    private InventoryManager inventoryManager;
    private QueueManager queueManager;
    private LobbyManager lobbyManager;
    private EconomyManager economyManager;
    private com.starlightuniverse.notify.PendingMessageManager pendingMessageManager;
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
    private EmojiManager emojiManager;
    private BenefitManager benefitManager;
    private NameplateManager nameplateManager;
    private SpawnerManager spawnerManager;
    private RtpManager rtpManager;
    private TpaManager tpaManager;
    private PWarpManager pwarpManager;
    private AntiCheatManager antiCheatManager;
    private LogManager logManager;
    private LogListener logListener;
    private AnnouncementManager announcementManager;
    private MaintenanceManager maintenanceManager;
    private HotTimeManager hotTimeManager;
    private PackServer packServer;
    private ResourcePackManager resourcePackManager;
    private PlayerHeadPackManager playerHeadPackManager;
    private VoucherManager voucherManager;
    private VoteManager voteManager;
    private BuffManager buffManager;
    private UniverseToolManager universeToolManager;
    private BoosterManager boosterManager;
    private ChestShopManager chestShopManager;
    private DiagnosticsService diagnosticsService;
    private ScoreboardManager scoreboardManager;
    private BorderManager borderManager;

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

        premiumSessionVerifier = new PremiumSessionVerifier(this);
        premiumSessionVerifier.register();

        skinManager = new SkinManager(this);
        skinManager.loadRandomSkins();

        worldManager = new WorldManager(this, databaseManager);
        worldManager.initialize();

        inventoryManager = new InventoryManager(this, databaseManager);

        queueManager = new QueueManager(this, databaseManager);
        queueManager.start();

        lobbyManager = new LobbyManager(this, queueManager);

        economyManager = new EconomyManager(databaseManager);
        pendingMessageManager = new com.starlightuniverse.notify.PendingMessageManager(this, databaseManager);
        Bukkit.getPluginManager().registerEvents(
                new com.starlightuniverse.notify.PendingMessageListener(pendingMessageManager, authManager), this);
        Bukkit.getPluginManager().registerEvents(
                new com.starlightuniverse.notify.DeathMessageListener(), this);

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

        borderManager = new BorderManager(this, databaseManager);
        borderManager.initialize();

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
        Bukkit.getPluginManager().registerEvents(new BorderListener(borderManager, premiumManager), this);
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
        Bukkit.getCommandMap().register("starlightuniverse", new BannerCommand(this));
        Bukkit.getCommandMap().register("starlightuniverse", BannerCommand.createRemoveCommand());

        for (Command cmd : HomeCommands.create(homeManager))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);
        Bukkit.getCommandMap().register("starlightuniverse", new HomeProtectCommand(homeManager));

        for (Command cmd : PremiumCommands.create(premiumManager, borderManager))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);

        for (Command cmd : BorderManager.createCommands(borderManager))
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

        boosterManager = new BoosterManager(this, databaseManager);
        boosterManager.start();

        chestShopManager = new ChestShopManager(this, databaseManager, economyManager);
        chestShopManager.initialize();
        Bukkit.getPluginManager().registerEvents(new ChestShopListener(this, chestShopManager), this);
        Bukkit.getCommandMap().register("starlightuniverse", new ChestShopCommand(chestShopManager));

        voucherManager = new VoucherManager(this, economyManager, homeManager, crateManager);
        voucherManager.setBoosterManager(boosterManager);
        voucherManager.start();
        crateManager.setVoucherManager(voucherManager);

        EnchantRemoverListener enchantRemoverListener = new EnchantRemoverListener(this, voucherManager, enchantManager);
        voucherManager.setEnchantRemoverListener(enchantRemoverListener);
        Bukkit.getPluginManager().registerEvents(enchantRemoverListener, this);

        Bukkit.getPluginManager().registerEvents(new VoucherListener(voucherManager, boosterManager), this);
        Bukkit.getCommandMap().register("starlightuniverse", new VoteFlyCommand(voucherManager));

        voteManager = new VoteManager(this, databaseManager, economyManager, crateManager);
        Bukkit.getPluginManager().registerEvents(new VoteListener(voteManager), this);
        for (Command cmd : VoteCommand.create(voteManager))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);

        buffManager = new BuffManager(this, databaseManager);
        buffManager.start();
        Bukkit.getPluginManager().registerEvents(new BuffListener(this, buffManager), this);

        universeToolManager = new UniverseToolManager(this);
        Bukkit.getPluginManager().registerEvents(universeToolManager, this);

        crateManager.setBuffManager(buffManager);
        crateManager.setUniverseToolManager(universeToolManager);

        starShopManager = new StarShopManager(this, economyManager, crateManager,
                premiumManager, voucherManager, buffManager);
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

        emojiManager = new EmojiManager(this, databaseManager, economyManager);
        chatManager.setEmojiManager(emojiManager);
        Bukkit.getPluginManager().registerEvents(new EmojiListener(emojiManager), this);
        Bukkit.getCommandMap().register("starlightuniverse", new EmojiCommand(emojiManager));

        benefitManager = new BenefitManager(this, databaseManager, economyManager, premiumManager, chatManager);
        chatManager.setBenefitManager(benefitManager);
        Bukkit.getPluginManager().registerEvents(new BenefitListener(this, benefitManager), this);
        for (Command cmd : BenefitCommands.create(benefitManager))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);

        nameplateManager = new NameplateManager(this, chatManager, adminManager,
                premiumManager, teamManager, economyManager);
        nameplateManager.setBenefitManager(benefitManager);
        chatManager.setNameplateManager(nameplateManager);
        nameplateManager.start();
        Bukkit.getPluginManager().registerEvents(new NameplateListener(this, nameplateManager, authManager), this);

        spawnerManager = new SpawnerManager(this, databaseManager, economyManager);
        spawnerManager.initialize();
        crateManager.setSpawnerManager(spawnerManager);
        Bukkit.getPluginManager().registerEvents(new SpawnerListener(spawnerManager), this);
        Bukkit.getCommandMap().register("starlightuniverse", new SpawnerCommand(spawnerManager));

        rtpManager = new RtpManager(this, worldManager);
        tpaManager = new TpaManager(this, databaseManager, adminManager);
        tpaManager.start();
        TpDragonCommand tpDragonCommand = new TpDragonCommand(this);
        Bukkit.getPluginManager().registerEvents(
                new TravelListener(rtpManager, tpaManager, tpDragonCommand), this);
        Bukkit.getCommandMap().register("starlightuniverse", new RtpCommand(rtpManager, adminManager));
        Bukkit.getCommandMap().register("starlightuniverse", new SpawnCommand());
        Bukkit.getCommandMap().register("starlightuniverse", tpDragonCommand);
        for (Command cmd : TpaCommands.create(tpaManager))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);

        pwarpManager = new PWarpManager(this, databaseManager, economyManager);
        pwarpManager.initialize();
        Bukkit.getPluginManager().registerEvents(new PWarpListener(this, pwarpManager, adminManager), this);
        for (Command cmd : PWarpCommand.create(pwarpManager))
            Bukkit.getCommandMap().register("starlightuniverse", cmd);

        Bukkit.getPluginManager().registerEvents(new AntiGriefListener(this), this);

        antiCheatManager = new AntiCheatManager(this, adminManager);
        Bukkit.getPluginManager().registerEvents(new AntiCheatListener(antiCheatManager, authManager), this);
        Bukkit.getCommandMap().register("starlightuniverse", new AntiCheatCommand(antiCheatManager, adminManager));

        logManager = new LogManager(this);
        logManager.start();
        logListener = new LogListener(this, logManager, authManager);
        logListener.start();
        Bukkit.getPluginManager().registerEvents(logListener, this);

        announcementManager = new AnnouncementManager(this, databaseManager);
        announcementManager.initialize();
        announcementManager.start();
        Bukkit.getPluginManager().registerEvents(new AnnouncementListener(this, announcementManager), this);
        Bukkit.getPluginManager().registerEvents(new MotdListener(), this);
        Bukkit.getCommandMap().register("starlightuniverse", new AnnounceCommand(announcementManager, adminManager));

        maintenanceManager = new MaintenanceManager(this, adminManager, databaseManager);
        maintenanceManager.loadPersistentState();
        Bukkit.getPluginManager().registerEvents(new MaintenanceListener(maintenanceManager, databaseManager), this);
        Bukkit.getCommandMap().register("starlightuniverse", new MaintenanceCommand(maintenanceManager, adminManager));

        hotTimeManager = new HotTimeManager(this);
        Bukkit.getPluginManager().registerEvents(new HotTimeListener(hotTimeManager), this);
        Bukkit.getCommandMap().register("starlightuniverse", new HotTimeCommand(hotTimeManager, adminManager));
        jobManager.setHotTimeManager(hotTimeManager);
        jobManager.setBoosterManager(boosterManager);

        packServer = new PackServer(this);
        if (packServer.start()) {
            resourcePackManager = new ResourcePackManager(this, packServer);
            playerHeadPackManager = new PlayerHeadPackManager(this, packServer);
            playerHeadPackManager.start();
            Bukkit.getPluginManager().registerEvents(
                    new ResourcePackListener(resourcePackManager, playerHeadPackManager), this);
            Bukkit.getPluginManager().registerEvents(
                    new ConfigPhasePackListener(this, packServer, playerHeadPackManager), this);
        }

        diagnosticsService = new DiagnosticsService(this);
        Bukkit.getCommandMap().register("starlightuniverse", new DiagCommand(adminManager, diagnosticsService));

        scoreboardManager = new ScoreboardManager(this, databaseManager, economyManager,
                adminManager, premiumManager, teamManager, authManager, chatManager);
        scoreboardManager.start();
        Bukkit.getPluginManager().registerEvents(
                new ScoreboardListener(this, scoreboardManager, authManager), this);

        getLogger().info("[SU] Enabled!");
        getLogger().info("[SU] Startup summary: " + diagnosticsService.buildStartupSummary());
    }

    @Override
    public void onDisable() {
        if (premiumSessionVerifier != null) {
            premiumSessionVerifier.unregister();
        }

        if (scoreboardManager != null) {
            scoreboardManager.shutdown();
        }

        if (playerHeadPackManager != null) {
            playerHeadPackManager.stop();
        }

        if (packServer != null) {
            packServer.stop();
        }

        if (hotTimeManager != null) {
            hotTimeManager.stop();
        }

        if (announcementManager != null) {
            announcementManager.shutdown();
        }

        if (logListener != null) {
            logListener.shutdown();
        }

        if (logManager != null) {
            logManager.shutdown();
        }

        if (spawnerManager != null) {
            spawnerManager.shutdown();
        }

        if (nameplateManager != null) {
            nameplateManager.shutdown();
        }

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

        if (chestShopManager != null) {
            chestShopManager.shutdown();
        }

        if (boosterManager != null) {
            boosterManager.shutdown();
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

    public PremiumSessionVerifier getPremiumSessionVerifier() {
        return premiumSessionVerifier;
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

    public com.starlightuniverse.notify.PendingMessageManager getPendingMessageManager() {
        return pendingMessageManager;
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

    public EmojiManager getEmojiManager() {
        return emojiManager;
    }

    public BenefitManager getBenefitManager() {
        return benefitManager;
    }

    public NameplateManager getNameplateManager() {
        return nameplateManager;
    }

    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }

    public RtpManager getRtpManager() {
        return rtpManager;
    }

    public TpaManager getTpaManager() {
        return tpaManager;
    }

    public PWarpManager getPwarpManager() {
        return pwarpManager;
    }

    public AntiCheatManager getAntiCheatManager() {
        return antiCheatManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public AnnouncementManager getAnnouncementManager() {
        return announcementManager;
    }

    public MaintenanceManager getMaintenanceManager() {
        return maintenanceManager;
    }

    public HotTimeManager getHotTimeManager() {
        return hotTimeManager;
    }

    public PackServer getPackServer() {
        return packServer;
    }

    public ResourcePackManager getResourcePackManager() {
        return resourcePackManager;
    }

    public PlayerHeadPackManager getPlayerHeadPackManager() {
        return playerHeadPackManager;
    }

    public UniverseToolManager getUniverseToolManager() {
        return universeToolManager;
    }

    public BoosterManager getBoosterManager() {
        return boosterManager;
    }

    public DiagnosticsService getDiagnosticsService() {
        return diagnosticsService;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public BorderManager getBorderManager() {
        return borderManager;
    }

    public ChestShopManager getChestShopManager() {
        return chestShopManager;
    }
}
