package com.starlightuniverse.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        File configFile = new File(plugin.getDataFolder(), "database.yml");

        if (!configFile.exists()) {
            plugin.getDataFolder().mkdirs();
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.set("host", "localhost");
            defaults.set("port", 3306);
            defaults.set("database", "starlightuniverse");
            defaults.set("username", "starlight");
            defaults.set("password", "starlight");
            try {
                defaults.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().severe("[SU] Failed to create database.yml: " + e.getMessage());
                return false;
            }
            plugin.getLogger().warning("[SU] database.yml created with default values. Configure it and restart the server.");
            return false;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String host = config.getString("host", "localhost");
        int port = config.getInt("port", 3306);
        String database = config.getString("database", "starlightuniverse");
        String username = config.getString("username", "starlight");
        String password = config.getString("password", "starlight");

        // Best-effort: try to create the DB if the user has permission.
        // If they don't (which is the common case for a locked-down plugin user),
        // we swallow the error and rely on the DB already existing — the actual
        // pool creation below will produce a clear error if it truly doesn't.
        try {
            ensureDatabaseExists(host, port, username, password, database);
        } catch (SQLException e) {
            plugin.getLogger().info("[SU] Skipping DB auto-create ("
                    + e.getMessage() + "). Will try to connect to existing DB '" + database + "'.");
        }

        try {
            HikariConfig hikari = new HikariConfig();
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true"
                    + "&useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci");
            hikari.setUsername(username);
            hikari.setPassword(password);
            hikari.setMaximumPoolSize(10);
            hikari.setMinimumIdle(2);
            hikari.setIdleTimeout(300000);
            hikari.setMaxLifetime(600000);
            hikari.setConnectionTimeout(10000);
            hikari.setPoolName("StarlightUniverse-Pool");
            hikari.addDataSourceProperty("cachePrepStmts", "true");
            hikari.addDataSourceProperty("prepStmtCacheSize", "250");
            hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikari.addDataSourceProperty("useServerPrepStmts", "true");

            dataSource = new HikariDataSource(hikari);

            try (Connection conn = dataSource.getConnection()) {
                plugin.getLogger().info("[SU] Database connected successfully!");
            }

            createTables();
            runMigrations();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[SU] Failed to connect to database: " + e.getMessage());
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            return false;
        }
    }

    private void ensureDatabaseExists(String host, int port, String user, String pass, String database)
            throws SQLException {
        // Server-only URL (no database specified) so we can CREATE DATABASE if missing.
        String serverUrl = "jdbc:mysql://" + host + ":" + port + "/"
                + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true"
                + "&useUnicode=true&characterEncoding=UTF-8";
        try (Connection conn = java.sql.DriverManager.getConnection(serverUrl, user, pass);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE IF NOT EXISTS `" + database
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            plugin.getLogger().info("[SU] Database '" + database + "' verified/created.");
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_players (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(16) NOT NULL UNIQUE,
                        password_hash VARCHAR(255) DEFAULT NULL,
                        register_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        last_active DATETIME DEFAULT CURRENT_TIMESTAMP,
                        money DOUBLE DEFAULT 0,
                        gems DOUBLE DEFAULT 0,
                        stars DOUBLE DEFAULT 0,
                        admin_level INT DEFAULT 0,
                        premium_level INT DEFAULT 0,
                        team_id INT DEFAULT NULL,
                        team_rank VARCHAR(32) DEFAULT NULL,
                        playtime BIGINT DEFAULT 0,
                        level INT DEFAULT 1,
                        current_exp BIGINT DEFAULT 0,
                        total_exp BIGINT DEFAULT 0,
                        pvp_kills INT DEFAULT 0,
                        pvm_kills INT DEFAULT 0,
                        deaths INT DEFAULT 0,
                        premium_uuid VARCHAR(36) DEFAULT NULL,
                        name_color VARCHAR(7) DEFAULT NULL,
                        chat_color VARCHAR(7) DEFAULT NULL,
                        name_tag VARCHAR(32) DEFAULT NULL,
                        custom_join_msg VARCHAR(255) DEFAULT NULL,
                        custom_quit_msg VARCHAR(255) DEFAULT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_bans (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player_id INT NOT NULL,
                        banned_by VARCHAR(16) NOT NULL,
                        reason VARCHAR(50) NOT NULL,
                        duration_minutes INT DEFAULT 0,
                        ban_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        expire_date DATETIME DEFAULT NULL,
                        active TINYINT(1) DEFAULT 1,
                        login_attempts INT DEFAULT 0,
                        FOREIGN KEY (player_id) REFERENCES su_players(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_mutes (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player_id INT NOT NULL,
                        muted_by VARCHAR(16) NOT NULL,
                        reason VARCHAR(50) NOT NULL,
                        duration_minutes INT DEFAULT 0,
                        mute_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        expire_date DATETIME DEFAULT NULL,
                        active TINYINT(1) DEFAULT 1,
                        FOREIGN KEY (player_id) REFERENCES su_players(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_warns (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player_id INT NOT NULL,
                        warned_by VARCHAR(16) NOT NULL,
                        reason VARCHAR(255) NOT NULL,
                        warn_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        active TINYINT(1) DEFAULT 1,
                        FOREIGN KEY (player_id) REFERENCES su_players(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_inventories (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        inventory_group VARCHAR(16) NOT NULL,
                        inventory_data MEDIUMTEXT,
                        armor_data MEDIUMTEXT,
                        offhand_data MEDIUMTEXT,
                        exp_level INT DEFAULT 0,
                        exp_progress FLOAT DEFAULT 0,
                        health DOUBLE DEFAULT 20,
                        food_level INT DEFAULT 20,
                        saturation FLOAT DEFAULT 5,
                        UNIQUE KEY uk_player_group (username, inventory_group)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_server_data (
                        data_key VARCHAR(64) PRIMARY KEY,
                        data_value VARCHAR(255)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_auction_listings (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        seller_username VARCHAR(16) NOT NULL,
                        item_data MEDIUMTEXT NOT NULL,
                        item_material VARCHAR(64) NOT NULL,
                        item_amount INT NOT NULL,
                        remaining_amount INT NOT NULL,
                        price_per_unit DOUBLE NOT NULL,
                        listed_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        expire_date DATETIME NOT NULL,
                        active TINYINT(1) DEFAULT 1,
                        collected TINYINT(1) DEFAULT 0,
                        INDEX idx_active (active),
                        INDEX idx_seller (seller_username),
                        INDEX idx_expire (expire_date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_auction_history (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        item_material VARCHAR(64) NOT NULL,
                        item_amount INT NOT NULL,
                        price_per_unit DOUBLE NOT NULL,
                        total_price DOUBLE NOT NULL,
                        seller_username VARCHAR(16) NOT NULL,
                        buyer_username VARCHAR(16) NOT NULL,
                        sold_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_material (item_material),
                        INDEX idx_sold_date (sold_date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_auction_blacklist (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        material VARCHAR(64) NOT NULL UNIQUE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_orders (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        creator_username VARCHAR(16) NOT NULL,
                        item_material VARCHAR(64) NOT NULL,
                        item_amount INT NOT NULL,
                        delivered_amount INT DEFAULT 0,
                        price_per_unit DOUBLE NOT NULL,
                        escrow_amount DOUBLE NOT NULL,
                        created_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        active TINYINT(1) DEFAULT 1,
                        INDEX idx_active (active),
                        INDEX idx_creator (creator_username)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_order_storage (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        item_data MEDIUMTEXT NOT NULL,
                        item_material VARCHAR(64) NOT NULL,
                        item_amount INT NOT NULL,
                        stored_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_username (username)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_admin_notes (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        player_id INT NOT NULL,
                        note_by VARCHAR(16) NOT NULL,
                        note_text TEXT NOT NULL,
                        note_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (player_id) REFERENCES su_players(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_reports (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        reporter_username VARCHAR(16) NOT NULL,
                        reported_username VARCHAR(16) NOT NULL,
                        reason TEXT NOT NULL,
                        report_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        active TINYINT(1) DEFAULT 1,
                        responded_by VARCHAR(16) DEFAULT NULL,
                        INDEX idx_active (active)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_homes (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        home_number INT NOT NULL,
                        home_name VARCHAR(32) DEFAULT NULL,
                        world VARCHAR(64) NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        yaw FLOAT NOT NULL,
                        pitch FLOAT NOT NULL,
                        icon_material VARCHAR(64) DEFAULT 'GRASS_BLOCK',
                        UNIQUE KEY uk_player_home (username, home_number),
                        INDEX idx_username (username)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_protections (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        owner_username VARCHAR(16) NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        min_x INT NOT NULL,
                        min_z INT NOT NULL,
                        max_x INT NOT NULL,
                        max_z INT NOT NULL,
                        created_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_owner (owner_username),
                        INDEX idx_world (world)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_protection_members (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        protection_id INT NOT NULL,
                        username VARCHAR(16) NOT NULL,
                        permission_level INT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_prot_player (protection_id, username),
                        FOREIGN KEY (protection_id) REFERENCES su_protections(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_protection_logs (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        protection_id INT NOT NULL,
                        username VARCHAR(16) NOT NULL,
                        action VARCHAR(255) NOT NULL,
                        log_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (protection_id) REFERENCES su_protections(id) ON DELETE CASCADE,
                        INDEX idx_prot (protection_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_golems (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        protection_id INT NOT NULL,
                        golem_uuid VARCHAR(36) NOT NULL,
                        FOREIGN KEY (protection_id) REFERENCES su_protections(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_borders (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        world VARCHAR(64) NOT NULL,
                        min_x INT NOT NULL,
                        min_y INT NOT NULL,
                        min_z INT NOT NULL,
                        max_x INT NOT NULL,
                        max_y INT NOT NULL,
                        max_z INT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_teams (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        name VARCHAR(32) NOT NULL UNIQUE,
                        leader_username VARCHAR(16) NOT NULL,
                        color1 VARCHAR(7) DEFAULT '#FFFFFF',
                        color2 VARCHAR(7) DEFAULT NULL,
                        color3 VARCHAR(7) DEFAULT NULL,
                        color4 VARCHAR(7) DEFAULT NULL,
                        color5 VARCHAR(7) DEFAULT NULL,
                        friendly_fire TINYINT(1) DEFAULT 0,
                        level INT DEFAULT 1,
                        xp BIGINT DEFAULT 0,
                        bank_money DOUBLE DEFAULT 0,
                        bank_gems DOUBLE DEFAULT 0,
                        bank_stars DOUBLE DEFAULT 0,
                        vault_data MEDIUMTEXT DEFAULT NULL,
                        created_date DATETIME DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_team_allies (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        team_id INT NOT NULL,
                        ally_team_id INT NOT NULL,
                        allied_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_ally (team_id, ally_team_id),
                        FOREIGN KEY (team_id) REFERENCES su_teams(id) ON DELETE CASCADE,
                        FOREIGN KEY (ally_team_id) REFERENCES su_teams(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_team_homes (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        team_id INT NOT NULL UNIQUE,
                        world VARCHAR(64) NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        yaw FLOAT NOT NULL,
                        pitch FLOAT NOT NULL,
                        FOREIGN KEY (team_id) REFERENCES su_teams(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_team_missions (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        team_id INT NOT NULL,
                        mission_type VARCHAR(32) NOT NULL,
                        target_amount INT NOT NULL,
                        current_amount INT DEFAULT 0,
                        mission_date DATE NOT NULL,
                        completed TINYINT(1) DEFAULT 0,
                        UNIQUE KEY uk_mission (team_id, mission_type, mission_date),
                        FOREIGN KEY (team_id) REFERENCES su_teams(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_team_vault (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        team_id INT NOT NULL UNIQUE,
                        vault_data MEDIUMTEXT,
                        FOREIGN KEY (team_id) REFERENCES su_teams(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_crates (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        crate_type VARCHAR(32) NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        UNIQUE KEY uk_location (world, x, y, z)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_jobs (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        job_type VARCHAR(32) NOT NULL,
                        level INT DEFAULT 1,
                        xp BIGINT DEFAULT 0,
                        UNIQUE KEY uk_player_job (username, job_type),
                        INDEX idx_username (username)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_skills (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        skill_type VARCHAR(32) NOT NULL,
                        level INT DEFAULT 1,
                        xp BIGINT DEFAULT 0,
                        UNIQUE KEY uk_player_skill (username, skill_type),
                        INDEX idx_username (username)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_pvp_stats (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(16) NOT NULL UNIQUE,
                        elo INT DEFAULT 1000,
                        wins INT DEFAULT 0,
                        losses INT DEFAULT 0,
                        arena_kills INT DEFAULT 0,
                        arena_deaths INT DEFAULT 0,
                        current_streak INT DEFAULT 0,
                        best_streak INT DEFAULT 0,
                        INDEX idx_elo (elo)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_mobraid_stats (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(16) NOT NULL UNIQUE,
                        total_kills INT DEFAULT 0,
                        best_wave INT DEFAULT 0,
                        INDEX idx_total_kills (total_kills)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_player_unlocks (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        category VARCHAR(32) NOT NULL,
                        unlock_key VARCHAR(64) NOT NULL,
                        unlock_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_unlock (username, category, unlock_key),
                        INDEX idx_user (username)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_virtual_spawners (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        owner_username VARCHAR(16) NOT NULL,
                        entity_type VARCHAR(32) NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        tier INT DEFAULT 1,
                        stack_count INT DEFAULT 1,
                        storage_data TEXT DEFAULT NULL,
                        stored_xp INT DEFAULT 0,
                        created_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_location (world, x, y, z),
                        INDEX idx_owner (owner_username)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_pwarps (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        owner_username VARCHAR(16) NOT NULL,
                        name VARCHAR(24) NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        yaw FLOAT NOT NULL,
                        pitch FLOAT NOT NULL,
                        category VARCHAR(24) DEFAULT 'Other',
                        description VARCHAR(50) DEFAULT '',
                        entry_cost DOUBLE DEFAULT 0,
                        visitors INT DEFAULT 0,
                        allow_pvp TINYINT(1) DEFAULT 0,
                        allow_break TINYINT(1) DEFAULT 0,
                        allow_place TINYINT(1) DEFAULT 0,
                        allow_containers TINYINT(1) DEFAULT 0,
                        allow_interact TINYINT(1) DEFAULT 1,
                        created_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_pwarp_owner_name (owner_username, name),
                        INDEX idx_pwarp_owner (owner_username),
                        INDEX idx_pwarp_name (name)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_pwarp_ratings (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        pwarp_id INT NOT NULL,
                        username VARCHAR(16) NOT NULL,
                        stars INT NOT NULL,
                        UNIQUE KEY uk_rating (pwarp_id, username),
                        FOREIGN KEY (pwarp_id) REFERENCES su_pwarps(id) ON DELETE CASCADE
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_pwarp_bans (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        owner_username VARCHAR(16) NOT NULL,
                        banned_username VARCHAR(16) NOT NULL,
                        pwarp_id INT NOT NULL DEFAULT 0,
                        UNIQUE KEY uk_pwarp_ban_v2 (owner_username, banned_username, pwarp_id),
                        INDEX idx_owner_ban (owner_username)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_tp_blocks (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        blocker_username VARCHAR(16) NOT NULL,
                        blocked_username VARCHAR(16) NOT NULL,
                        UNIQUE KEY uk_block (blocker_username, blocked_username),
                        INDEX idx_blocker (blocker_username)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_announcements (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        message VARCHAR(255) NOT NULL,
                        ann_type VARCHAR(16) NOT NULL,
                        frequency_minutes INT NOT NULL DEFAULT 15,
                        duration_seconds INT NOT NULL DEFAULT 5,
                        enabled TINYINT(1) NOT NULL DEFAULT 1,
                        created_date DATETIME DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_pending_messages (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(16) NOT NULL,
                        message TEXT NOT NULL,
                        created_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_username (username)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_votes (
                        username VARCHAR(16) NOT NULL,
                        link_id INT NOT NULL,
                        last_vote DATETIME NOT NULL,
                        PRIMARY KEY (username, link_id),
                        INDEX idx_username (username)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_buffs (
                        username VARCHAR(16) NOT NULL,
                        buff_type VARCHAR(32) NOT NULL,
                        expire_time DATETIME NOT NULL,
                        PRIMARY KEY (username, buff_type),
                        INDEX idx_username (username)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS su_boosters (
                        username VARCHAR(16) NOT NULL,
                        booster_type VARCHAR(32) NOT NULL,
                        multiplier DOUBLE NOT NULL,
                        expire_time DATETIME NOT NULL,
                        PRIMARY KEY (username, booster_type),
                        INDEX idx_username (username)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            plugin.getLogger().info("[SU] Database tables created/verified successfully!");
        }
    }

    private void runMigrations() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            try {
                stmt.execute("ALTER TABLE su_players ADD COLUMN last_login_ip VARCHAR(45) DEFAULT NULL");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE su_players ADD COLUMN last_login_time BIGINT DEFAULT NULL");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE su_players ADD COLUMN extra_home_slots INT DEFAULT 0");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE su_players ADD COLUMN premium_expire_date DATETIME DEFAULT NULL");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE su_players ADD COLUMN daily_bonus_date DATE DEFAULT NULL");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE su_players ADD COLUMN monthly_stars_date VARCHAR(7) DEFAULT NULL");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE su_players ADD COLUMN referred_by VARCHAR(16) DEFAULT NULL");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE su_players ADD COLUMN custom_prefix VARCHAR(16) DEFAULT NULL");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE su_players ADD COLUMN active_glow VARCHAR(32) DEFAULT NULL");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE su_players ADD COLUMN active_kill_effect VARCHAR(32) DEFAULT NULL");
            } catch (SQLException ignored) {}
            try {
                stmt.execute("ALTER TABLE su_players ADD COLUMN tpa_disabled TINYINT(1) DEFAULT 0");
            } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE su_pwarps DROP INDEX uk_pwarp_name"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE su_pwarps ADD UNIQUE KEY uk_pwarp_owner_name (owner_username, name)"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE su_pwarp_bans ADD COLUMN pwarp_id INT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE su_pwarp_bans DROP INDEX uk_pwarp_ban"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE su_pwarp_bans ADD UNIQUE KEY uk_pwarp_ban_v2 (owner_username, banned_username, pwarp_id)"); } catch (SQLException ignored) {}

            // Protection refactor: center+radius → rectangle (min/max corners) + block budget
            try { stmt.execute("ALTER TABLE su_protections ADD COLUMN min_x INT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE su_protections ADD COLUMN min_z INT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE su_protections ADD COLUMN max_x INT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE su_protections ADD COLUMN max_z INT NOT NULL DEFAULT 0"); } catch (SQLException ignored) {}
            try { stmt.execute("UPDATE su_protections SET min_x = center_x - radius, max_x = center_x + radius, min_z = center_z - radius, max_z = center_z + radius WHERE min_x = 0 AND max_x = 0"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE su_protections DROP COLUMN center_x"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE su_protections DROP COLUMN center_z"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE su_protections DROP COLUMN radius"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE su_players ADD COLUMN protection_blocks INT NOT NULL DEFAULT 1000"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE su_players ADD COLUMN daily_blocks_date DATE DEFAULT NULL"); } catch (SQLException ignored) {}

            plugin.getLogger().info("[SU] Database migrations applied.");
        } catch (SQLException e) {
            plugin.getLogger().warning("[SU] Migration check failed: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is not initialized");
        }
        return dataSource.getConnection();
    }

    public CompletableFuture<Void> executeAsync(DatabaseRunnable task) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection()) {
                task.run(conn);
            } catch (SQLException e) {
                plugin.getLogger().severe("[SU] Database error: " + e.getMessage());
            }
        });
    }

    public <T> CompletableFuture<T> queryAsync(DatabaseCallable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = getConnection()) {
                return task.call(conn);
            } catch (SQLException e) {
                plugin.getLogger().severe("[SU] Database query error: " + e.getMessage());
                return null;
            }
        });
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("[SU] Database connection pool closed.");
        }
    }

    @FunctionalInterface
    public interface DatabaseRunnable {
        void run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface DatabaseCallable<T> {
        T call(Connection connection) throws SQLException;
    }
}
