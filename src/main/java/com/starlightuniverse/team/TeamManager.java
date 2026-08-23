package com.starlightuniverse.team;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TeamManager {

    private static final int CREATE_COST = 5000;
    private static final int RENAME_COST = 2000;
    private static final int MAX_MEMBERS = 20;
    private static final int MAX_NAME_LENGTH = 16;
    private static final long INVITE_EXPIRE_MS = 120_000;
    private static final long HOME_COOLDOWN_MS = 5000;

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor PURPLE = TextColor.color(0xAA00AA);

    private static final String[] MISSION_TYPES = {"MINE_BLOCKS", "KILL_MOBS", "CHOP_LOGS", "FISH", "EARN_MONEY"};
    private static final Map<String, String> MISSION_NAMES = Map.of(
            "MINE_BLOCKS", "Mine Blocks",
            "KILL_MOBS", "Kill Mobs",
            "CHOP_LOGS", "Chop Logs",
            "FISH", "Catch Fish",
            "EARN_MONEY", "Earn Money"
    );
    private static final int[][] MISSION_TARGETS = {
            {200, 500, 1000},
            {50, 100, 200},
            {100, 300, 500},
            {20, 50, 100},
            {5000, 20000, 50000}
    };
    private static final int[] MISSION_XP = {100, 300, 600};

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;

    private final Map<Integer, Team> teamCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerTeamCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<TeamInvite>> pendingInvites = new ConcurrentHashMap<>();
    private final Set<UUID> teamChatToggle = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> homeCooldowns = new ConcurrentHashMap<>();

    private final Map<Integer, PvPBattle> pvpRequests = new ConcurrentHashMap<>();
    private final Map<Integer, PvPBattle> activeBattles = new ConcurrentHashMap<>();
    private final Map<String, Integer> allyRequests = new ConcurrentHashMap<>();
    private final Map<Integer, List<TeamMission>> dailyMissions = new ConcurrentHashMap<>();
    private final Map<String, TeamWar> warRequests = new ConcurrentHashMap<>();
    private final Map<String, TeamWar> activeWars = new ConcurrentHashMap<>();

    private BukkitTask cleanupTask;

    public TeamManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
    }

    public void initialize() {
        loadAllTeams();
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpired, 1200L, 1200L);
    }

    public void shutdown() {
        if (cleanupTask != null) cleanupTask.cancel();
    }

    public JavaPlugin getPlugin() { return plugin; }

    // ==================== LOADING ====================

    private void loadAllTeams() {
        db.queryAsync(conn -> {
            Map<Integer, Team> teams = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM su_teams");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Team team = new Team(rs.getInt("id"), rs.getString("name"), rs.getString("leader_username"));
                    List<String> colors = new ArrayList<>();
                    for (int i = 1; i <= 5; i++) {
                        String c = rs.getString("color" + i);
                        if (c != null) colors.add(c);
                    }
                    if (!colors.isEmpty()) team.setColors(colors);
                    team.setFriendlyFire(rs.getBoolean("friendly_fire"));
                    team.setLevel(rs.getInt("level"));
                    team.setXp(rs.getLong("xp"));
                    team.setBankMoney(rs.getDouble("bank_money"));
                    team.setBankGems(rs.getDouble("bank_gems"));
                    team.setBankStars(rs.getDouble("bank_stars"));
                    teams.put(team.getId(), team);
                }
            }
            for (Team team : teams.values()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT username, team_rank FROM su_players WHERE team_id = ?")) {
                    ps.setInt(1, team.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String username = rs.getString("username");
                            TeamRank rank = TeamRank.fromDbName(rs.getString("team_rank"));
                            team.getMembers().put(username, rank);
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT ally_team_id FROM su_team_allies WHERE team_id = ?")) {
                    ps.setInt(1, team.getId());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) team.getAllyIds().add(rs.getInt("ally_team_id"));
                    }
                }
            }
            return teams;
        }).thenAccept(teams -> {
            if (teams != null) {
                teamCache.putAll(teams);
                for (Team team : teams.values()) {
                    for (String member : team.getMembers().keySet()) {
                        Player p = Bukkit.getPlayerExact(member);
                        if (p != null) playerTeamCache.put(p.getUniqueId(), team.getId());
                    }
                }
                plugin.getLogger().info("[SU] Loaded " + teams.size() + " teams.");
            }
        });
    }

    public void loadPlayer(Player player) {
        String username = player.getName().toLowerCase();
        for (Team team : teamCache.values()) {
            if (team.getMembers().containsKey(username)) {
                playerTeamCache.put(player.getUniqueId(), team.getId());
                return;
            }
        }
    }

    public void unloadPlayer(UUID uuid) {
        playerTeamCache.remove(uuid);
        pendingInvites.remove(uuid);
        teamChatToggle.remove(uuid);
        homeCooldowns.remove(uuid);
    }

    // ==================== QUERIES ====================

    public Team getTeam(int id) { return teamCache.get(id); }

    public Team getTeamByName(String name) {
        for (Team team : teamCache.values()) {
            if (team.getName().equalsIgnoreCase(name)) return team;
        }
        return null;
    }

    public Team getPlayerTeam(UUID uuid) {
        Integer id = playerTeamCache.get(uuid);
        return id != null ? teamCache.get(id) : null;
    }

    public Team getPlayerTeam(Player player) {
        return getPlayerTeam(player.getUniqueId());
    }

    public boolean areTeammates(UUID a, UUID b) {
        Integer t1 = playerTeamCache.get(a);
        Integer t2 = playerTeamCache.get(b);
        return t1 != null && t1.equals(t2);
    }

    public boolean areAllies(UUID a, UUID b) {
        Integer t1 = playerTeamCache.get(a);
        Integer t2 = playerTeamCache.get(b);
        if (t1 == null || t2 == null || t1.equals(t2)) return false;
        Team team1 = teamCache.get(t1);
        return team1 != null && team1.getAllyIds().contains(t2);
    }

    public boolean isInTeamChat(UUID uuid) { return teamChatToggle.contains(uuid); }

    public Collection<Team> getAllTeams() { return teamCache.values(); }

    // ==================== CREATE ====================

    public void createTeam(Player player, String name) {
        UUID uuid = player.getUniqueId();
        if (getPlayerTeam(uuid) != null) {
            Msg.error(player, "You are already in a team! Leave first.");
            return;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            Msg.error(player, "Team name must be " + MAX_NAME_LENGTH + " characters or less!");
            return;
        }
        if (!name.matches("[a-zA-Z0-9_]+")) {
            Msg.error(player, "Team name can only contain letters, numbers, and underscores!");
            return;
        }
        if (getTeamByName(name) != null) {
            Msg.error(player, "A team with that name already exists!");
            return;
        }
        if (!economy.removeMoney(uuid, CREATE_COST)) {
            Msg.error(player, "You need $" + EconomyManager.format(CREATE_COST) + " to create a team!");
            return;
        }
        String username = player.getName().toLowerCase();
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_teams (name, leader_username) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, username);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
            return -1;
        }).thenAccept(teamId -> {
            if (teamId == null || teamId == -1) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    economy.addMoney(uuid, CREATE_COST);
                    Msg.error(player, "Failed to create team. Refunded.");
                });
                return;
            }
            db.executeAsync(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE su_players SET team_id = ?, team_rank = ? WHERE username = ?")) {
                    ps.setInt(1, teamId);
                    ps.setString(2, TeamRank.LEADER.name());
                    ps.setString(3, username);
                    ps.executeUpdate();
                }
            });
            Bukkit.getScheduler().runTask(plugin, () -> {
                Team team = new Team(teamId, name, username);
                team.getMembers().put(username, TeamRank.LEADER);
                teamCache.put(teamId, team);
                playerTeamCache.put(uuid, teamId);
                Msg.success(player, "Team '" + name + "' created! Cost: $" + EconomyManager.format(CREATE_COST));
                broadcastServer(player.getName() + " created team " + name + "!");
            });
        });
    }

    // ==================== INVITE ====================

    public void invitePlayer(Player inviter, String targetName) {
        Team team = getPlayerTeam(inviter);
        if (team == null) { Msg.error(inviter, "You are not in a team!"); return; }
        TeamRank rank = team.getMemberRank(inviter.getName().toLowerCase());
        if (rank == null || !rank.canInvite()) { Msg.error(inviter, "You don't have permission to invite!"); return; }
        if (team.getMemberCount() >= MAX_MEMBERS) { Msg.error(inviter, "Team is full! Max " + MAX_MEMBERS + " members."); return; }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { Msg.error(inviter, "Player not found or offline!"); return; }
        if (target.equals(inviter)) { Msg.error(inviter, "You can't invite yourself!"); return; }
        if (getPlayerTeam(target) != null) { Msg.error(inviter, target.getName() + " is already in a team!"); return; }
        UUID targetUuid = target.getUniqueId();
        List<TeamInvite> invites = pendingInvites.computeIfAbsent(targetUuid, k -> new ArrayList<>());
        invites.removeIf(i -> i.teamId == team.getId());
        invites.add(new TeamInvite(team.getId(), team.getName(), inviter.getName(), System.currentTimeMillis()));
        Msg.success(inviter, "Invited " + target.getName() + " to the team!");
        target.sendMessage(Component.text("[SU] ", GOLD)
                .append(Component.text(inviter.getName(), YELLOW))
                .append(Component.text(" invited you to team ", GREEN))
                .append(Component.text(team.getName(), CYAN))
                .append(Component.text("! Use /team accept or /team deny", GREEN)));
    }

    public void acceptInvite(Player player, String teamName) {
        UUID uuid = player.getUniqueId();
        if (getPlayerTeam(uuid) != null) { Msg.error(player, "You are already in a team!"); return; }
        List<TeamInvite> invites = pendingInvites.get(uuid);
        if (invites == null || invites.isEmpty()) { Msg.error(player, "You have no pending invites!"); return; }
        invites.removeIf(i -> System.currentTimeMillis() - i.timestamp > INVITE_EXPIRE_MS);
        if (invites.isEmpty()) { Msg.error(player, "Your invites have expired!"); return; }
        TeamInvite invite;
        if (teamName != null) {
            invite = invites.stream().filter(i -> i.teamName.equalsIgnoreCase(teamName)).findFirst().orElse(null);
            if (invite == null) { Msg.error(player, "No invite from team '" + teamName + "'!"); return; }
        } else {
            invite = invites.getLast();
        }
        Team team = teamCache.get(invite.teamId);
        if (team == null) { Msg.error(player, "That team no longer exists!"); return; }
        if (team.getMemberCount() >= MAX_MEMBERS) { Msg.error(player, "That team is full!"); return; }
        String username = player.getName().toLowerCase();
        team.getMembers().put(username, TeamRank.MEMBER);
        playerTeamCache.put(uuid, team.getId());
        pendingInvites.remove(uuid);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET team_id = ?, team_rank = ? WHERE username = ?")) {
                ps.setInt(1, team.getId());
                ps.setString(2, TeamRank.MEMBER.name());
                ps.setString(3, username);
                ps.executeUpdate();
            }
        });
        Msg.success(player, "You joined team " + team.getName() + "!");
        broadcastTeam(team, player.getName() + " joined the team!");
    }

    public void denyInvite(Player player, String teamName) {
        UUID uuid = player.getUniqueId();
        List<TeamInvite> invites = pendingInvites.get(uuid);
        if (invites == null || invites.isEmpty()) { Msg.error(player, "You have no pending invites!"); return; }
        if (teamName != null) {
            invites.removeIf(i -> i.teamName.equalsIgnoreCase(teamName));
        } else {
            invites.clear();
        }
        if (invites.isEmpty()) pendingInvites.remove(uuid);
        Msg.success(player, "Invite(s) denied.");
    }

    // ==================== KICK ====================

    public void kickPlayer(Player executor, String targetName) {
        Team team = getPlayerTeam(executor);
        if (team == null) { Msg.error(executor, "You are not in a team!"); return; }
        TeamRank executorRank = team.getMemberRank(executor.getName().toLowerCase());
        if (executorRank == null || !executorRank.canKick()) { Msg.error(executor, "You don't have permission to kick!"); return; }
        String target = targetName.toLowerCase();
        TeamRank targetRank = team.getMemberRank(target);
        if (targetRank == null) { Msg.error(executor, "That player is not in your team!"); return; }
        if (targetRank.getLevel() >= executorRank.getLevel()) { Msg.error(executor, "You can't kick someone of equal or higher rank!"); return; }
        removeFromTeam(team, target);
        Msg.success(executor, "Kicked " + targetName + " from the team!");
        broadcastTeam(team, targetName + " was kicked from the team!");
        Player targetPlayer = Bukkit.getPlayerExact(targetName);
        if (targetPlayer != null) {
            playerTeamCache.remove(targetPlayer.getUniqueId());
            Msg.error(targetPlayer, "You were kicked from team " + team.getName() + "!");
        }
    }

    // ==================== PROMOTE / DEMOTE ====================

    public void promotePlayer(Player executor, String targetName) {
        Team team = getPlayerTeam(executor);
        if (team == null) { Msg.error(executor, "You are not in a team!"); return; }
        TeamRank executorRank = team.getMemberRank(executor.getName().toLowerCase());
        if (executorRank == null || !executorRank.canPromote()) { Msg.error(executor, "You don't have permission to promote!"); return; }
        String target = targetName.toLowerCase();
        TeamRank targetRank = team.getMemberRank(target);
        if (targetRank == null) { Msg.error(executor, "That player is not in your team!"); return; }
        if (targetRank.getLevel() >= executorRank.getLevel() - 1) { Msg.error(executor, "You can't promote above your own rank!"); return; }
        TeamRank newRank = targetRank.next();
        if (newRank == null) { Msg.error(executor, "That player is at max promotable rank!"); return; }
        team.getMembers().put(target, newRank);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET team_rank = ? WHERE username = ?")) {
                ps.setString(1, newRank.name());
                ps.setString(2, target);
                ps.executeUpdate();
            }
        });
        Msg.success(executor, "Promoted " + targetName + " to " + newRank.getDisplayName() + "!");
        broadcastTeam(team, targetName + " was promoted to " + newRank.getDisplayName() + "!");
    }

    public void demotePlayer(Player executor, String targetName) {
        Team team = getPlayerTeam(executor);
        if (team == null) { Msg.error(executor, "You are not in a team!"); return; }
        TeamRank executorRank = team.getMemberRank(executor.getName().toLowerCase());
        if (executorRank == null || !executorRank.canDemote()) { Msg.error(executor, "You don't have permission to demote!"); return; }
        String target = targetName.toLowerCase();
        TeamRank targetRank = team.getMemberRank(target);
        if (targetRank == null) { Msg.error(executor, "That player is not in your team!"); return; }
        if (targetRank.getLevel() >= executorRank.getLevel()) { Msg.error(executor, "You can't demote someone of equal or higher rank!"); return; }
        TeamRank newRank = targetRank.previous();
        if (newRank == null) { Msg.error(executor, "That player is already at the lowest rank!"); return; }
        team.getMembers().put(target, newRank);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET team_rank = ? WHERE username = ?")) {
                ps.setString(1, newRank.name());
                ps.setString(2, target);
                ps.executeUpdate();
            }
        });
        Msg.success(executor, "Demoted " + targetName + " to " + newRank.getDisplayName() + ".");
        broadcastTeam(team, targetName + " was demoted to " + newRank.getDisplayName() + ".");
    }

    // ==================== LEAVE / DISBAND ====================

    public void leaveTeam(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        String username = player.getName().toLowerCase();
        TeamRank rank = team.getMemberRank(username);
        if (rank == TeamRank.LEADER) {
            if (team.getMemberCount() > 1) {
                Msg.error(player, "Transfer leadership first or disband the team!");
                return;
            }
            disbandTeam(player);
            return;
        }
        removeFromTeam(team, username);
        playerTeamCache.remove(player.getUniqueId());
        Msg.success(player, "You left team " + team.getName() + ".");
        broadcastTeam(team, player.getName() + " left the team.");
    }

    public void disbandTeam(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        TeamRank rank = team.getMemberRank(player.getName().toLowerCase());
        if (rank != TeamRank.LEADER) { Msg.error(player, "Only the Leader can disband!"); return; }

        if (team.getBankMoney() > 0) economy.addMoney(player.getUniqueId(), team.getBankMoney());
        if (team.getBankGems() > 0) economy.addGems(player.getUniqueId(), team.getBankGems());
        if (team.getBankStars() > 0) economy.addStars(player.getUniqueId(), team.getBankStars());

        String teamName = team.getName();
        int teamId = team.getId();

        for (String member : new ArrayList<>(team.getMembers().keySet())) {
            Player mp = Bukkit.getPlayerExact(member);
            if (mp != null) playerTeamCache.remove(mp.getUniqueId());
        }
        for (int allyId : team.getAllyIds()) {
            Team ally = teamCache.get(allyId);
            if (ally != null) ally.getAllyIds().remove(teamId);
        }
        teamCache.remove(teamId);

        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET team_id = NULL, team_rank = NULL WHERE team_id = ?")) {
                ps.setInt(1, teamId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM su_team_allies WHERE team_id = ? OR ally_team_id = ?")) {
                ps.setInt(1, teamId); ps.setInt(2, teamId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM su_team_homes WHERE team_id = ?")) {
                ps.setInt(1, teamId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM su_team_missions WHERE team_id = ?")) {
                ps.setInt(1, teamId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM su_team_vault WHERE team_id = ?")) {
                ps.setInt(1, teamId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM su_teams WHERE id = ?")) {
                ps.setInt(1, teamId);
                ps.executeUpdate();
            }
        });
        broadcastServer("Team " + teamName + " has been disbanded!");
        Msg.success(player, "Team disbanded. Bank funds returned to you.");
    }

    // ==================== TEAM HOME ====================

    public void setTeamHome(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        TeamRank rank = team.getMemberRank(player.getName().toLowerCase());
        if (rank == null || !rank.canSetHome()) { Msg.error(player, "You need Co-Leader+ to set team home!"); return; }
        Location loc = player.getLocation();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_team_homes (team_id, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z), yaw=VALUES(yaw), pitch=VALUES(pitch)")) {
                ps.setInt(1, team.getId());
                ps.setString(2, loc.getWorld().getName());
                ps.setDouble(3, loc.getX());
                ps.setDouble(4, loc.getY());
                ps.setDouble(5, loc.getZ());
                ps.setFloat(6, loc.getYaw());
                ps.setFloat(7, loc.getPitch());
                ps.executeUpdate();
            }
        });
        Msg.success(player, "Team home set!");
        broadcastTeam(team, player.getName() + " set a new team home.");
    }

    public void teleportHome(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        UUID uuid = player.getUniqueId();
        Long lastUse = homeCooldowns.get(uuid);
        if (lastUse != null && System.currentTimeMillis() - lastUse < HOME_COOLDOWN_MS) {
            long remaining = (HOME_COOLDOWN_MS - (System.currentTimeMillis() - lastUse)) / 1000 + 1;
            Msg.error(player, "Cooldown: " + remaining + "s");
            return;
        }
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT world, x, y, z, yaw, pitch FROM su_team_homes WHERE team_id = ?")) {
                ps.setInt(1, team.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new String[]{
                                rs.getString("world"),
                                String.valueOf(rs.getDouble("x")),
                                String.valueOf(rs.getDouble("y")),
                                String.valueOf(rs.getDouble("z")),
                                String.valueOf(rs.getFloat("yaw")),
                                String.valueOf(rs.getFloat("pitch"))
                        };
                    }
                }
            }
            return null;
        }).thenAccept(data -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (data == null) { Msg.error(player, "No team home set!"); return; }
            World world = Bukkit.getWorld(data[0]);
            if (world == null) { Msg.error(player, "World not found!"); return; }
            Location dest = new Location(world,
                    Double.parseDouble(data[1]), Double.parseDouble(data[2]), Double.parseDouble(data[3]),
                    Float.parseFloat(data[4]), Float.parseFloat(data[5]));
            player.teleport(dest);
            homeCooldowns.put(uuid, System.currentTimeMillis());
            Msg.success(player, "Teleported to team home!");
        }));
    }

    // ==================== TEAM COLORS ====================

    public void setTeamColor(Player player, List<String> hexColors) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        TeamRank rank = team.getMemberRank(player.getName().toLowerCase());
        if (rank == null || !rank.canSetColor()) { Msg.error(player, "Only the Leader can set team colors!"); return; }
        if (hexColors.isEmpty() || hexColors.size() > 5) { Msg.error(player, "Provide 1-5 hex colors!"); return; }
        for (String hex : hexColors) {
            if (!hex.matches("#[0-9a-fA-F]{6}")) { Msg.error(player, "Invalid hex color: " + hex); return; }
        }
        team.setColors(hexColors);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_teams SET color1=?, color2=?, color3=?, color4=?, color5=? WHERE id=?")) {
                for (int i = 0; i < 5; i++) {
                    ps.setString(i + 1, i < hexColors.size() ? hexColors.get(i) : null);
                }
                ps.setInt(6, team.getId());
                ps.executeUpdate();
            }
        });
        Msg.success(player, "Team colors updated!");
    }

    // ==================== TEAM NAME ====================

    public void setTeamName(Player player, String newName) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        TeamRank rank = team.getMemberRank(player.getName().toLowerCase());
        if (rank == null || !rank.canSetName()) { Msg.error(player, "Only the Leader can rename!"); return; }
        if (newName.length() > MAX_NAME_LENGTH) { Msg.error(player, "Name too long!"); return; }
        if (!newName.matches("[a-zA-Z0-9_]+")) { Msg.error(player, "Invalid name!"); return; }
        if (getTeamByName(newName) != null) { Msg.error(player, "Name already taken!"); return; }
        if (!economy.removeMoney(player.getUniqueId(), RENAME_COST)) {
            Msg.error(player, "You need $" + EconomyManager.format(RENAME_COST) + " to rename!"); return;
        }
        String oldName = team.getName();
        team.setName(newName);
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE su_teams SET name = ? WHERE id = ?")) {
                ps.setString(1, newName); ps.setInt(2, team.getId());
                ps.executeUpdate();
            }
        });
        broadcastTeam(team, "Team renamed from " + oldName + " to " + newName + "!");
    }

    // ==================== FRIENDLY FIRE ====================

    public void toggleFriendlyFire(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        TeamRank rank = team.getMemberRank(player.getName().toLowerCase());
        if (rank == null || !rank.canToggleFriendlyFire()) { Msg.error(player, "Only the Leader can toggle friendly fire!"); return; }
        team.setFriendlyFire(!team.isFriendlyFire());
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE su_teams SET friendly_fire = ? WHERE id = ?")) {
                ps.setBoolean(1, team.isFriendlyFire()); ps.setInt(2, team.getId());
                ps.executeUpdate();
            }
        });
        broadcastTeam(team, "Friendly fire " + (team.isFriendlyFire() ? "enabled" : "disabled") + "!");
    }

    // ==================== TEAM CHAT ====================

    public void toggleTeamChat(Player player) {
        UUID uuid = player.getUniqueId();
        if (getPlayerTeam(uuid) == null) { Msg.error(player, "You are not in a team!"); return; }
        if (teamChatToggle.remove(uuid)) {
            Msg.success(player, "Team chat disabled. You're now in global chat.");
        } else {
            teamChatToggle.add(uuid);
            Msg.success(player, "Team chat enabled. Messages go to your team only.");
        }
    }

    public void sendTeamChat(Player player, String message) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        Component msg = Component.text("[TC] ", CYAN)
                .append(Component.text("[" + team.getName() + "] ", TextColor.color(parseHex(team.getColors().getFirst()))))
                .append(Component.text(player.getName(), YELLOW))
                .append(Component.text(" >> ", GRAY))
                .append(Component.text(message, WHITE));
        for (String member : team.getMembers().keySet()) {
            Player mp = Bukkit.getPlayerExact(member);
            if (mp != null) mp.sendMessage(msg);
        }
    }

    // ==================== ALLIES ====================

    public void requestAlly(Player player, String targetTeamName) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        TeamRank rank = team.getMemberRank(player.getName().toLowerCase());
        if (rank == null || !rank.canAlly()) { Msg.error(player, "Only the Leader can manage allies!"); return; }
        Team target = getTeamByName(targetTeamName);
        if (target == null) { Msg.error(player, "Team not found!"); return; }
        if (target.getId() == team.getId()) { Msg.error(player, "You can't ally with yourself!"); return; }
        if (team.getAllyIds().contains(target.getId())) { Msg.error(player, "Already allied!"); return; }
        if (team.getAllyIds().size() >= team.getMaxAllies()) { Msg.error(player, "Max allies reached! (" + team.getMaxAllies() + ")"); return; }
        if (target.getAllyIds().size() >= target.getMaxAllies()) { Msg.error(player, target.getName() + " has max allies!"); return; }
        String key = Math.min(team.getId(), target.getId()) + "-" + Math.max(team.getId(), target.getId());
        Integer existing = allyRequests.get(key);
        if (existing != null && existing == target.getId()) {
            team.getAllyIds().add(target.getId());
            target.getAllyIds().add(team.getId());
            allyRequests.remove(key);
            db.executeAsync(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("INSERT IGNORE INTO su_team_allies (team_id, ally_team_id) VALUES (?, ?)")) {
                    ps.setInt(1, team.getId()); ps.setInt(2, target.getId()); ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("INSERT IGNORE INTO su_team_allies (team_id, ally_team_id) VALUES (?, ?)")) {
                    ps.setInt(1, target.getId()); ps.setInt(2, team.getId()); ps.executeUpdate();
                }
            });
            broadcastTeam(team, "Now allied with " + target.getName() + "!");
            broadcastTeam(target, "Now allied with " + team.getName() + "!");
        } else {
            allyRequests.put(key, team.getId());
            Msg.success(player, "Ally request sent to " + target.getName() + "!");
            broadcastTeam(target, team.getName() + " wants to ally! Leader: /team ally " + team.getName());
        }
    }

    public void removeAlly(Player player, String targetTeamName) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        TeamRank rank = team.getMemberRank(player.getName().toLowerCase());
        if (rank == null || !rank.canAlly()) { Msg.error(player, "Only the Leader can manage allies!"); return; }
        Team target = getTeamByName(targetTeamName);
        if (target == null) { Msg.error(player, "Team not found!"); return; }
        if (!team.getAllyIds().contains(target.getId())) { Msg.error(player, "Not allied with that team!"); return; }
        team.getAllyIds().remove(target.getId());
        target.getAllyIds().remove(team.getId());
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM su_team_allies WHERE (team_id=? AND ally_team_id=?) OR (team_id=? AND ally_team_id=?)")) {
                ps.setInt(1, team.getId()); ps.setInt(2, target.getId());
                ps.setInt(3, target.getId()); ps.setInt(4, team.getId());
                ps.executeUpdate();
            }
        });
        broadcastTeam(team, "Alliance with " + target.getName() + " ended.");
        broadcastTeam(target, "Alliance with " + team.getName() + " ended.");
    }

    // ==================== TEAM BANK ====================

    public void depositBank(Player player, String currency, double amount) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        if (amount <= 0) { Msg.error(player, "Amount must be positive!"); return; }
        UUID uuid = player.getUniqueId();
        String col;
        switch (currency.toLowerCase()) {
            case "money" -> {
                if (!economy.removeMoney(uuid, amount)) { Msg.error(player, "Not enough money!"); return; }
                team.setBankMoney(team.getBankMoney() + amount);
                col = "bank_money";
            }
            case "gems" -> {
                if (!economy.removeGems(uuid, amount)) { Msg.error(player, "Not enough gems!"); return; }
                team.setBankGems(team.getBankGems() + amount);
                col = "bank_gems";
            }
            case "stars" -> {
                if (!economy.removeStars(uuid, amount)) { Msg.error(player, "Not enough stars!"); return; }
                team.setBankStars(team.getBankStars() + amount);
                col = "bank_stars";
            }
            default -> { Msg.error(player, "Use: money, gems, or stars"); return; }
        }
        double newVal = switch (col) {
            case "bank_money" -> team.getBankMoney();
            case "bank_gems" -> team.getBankGems();
            case "bank_stars" -> team.getBankStars();
            default -> 0;
        };
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE su_teams SET " + col + " = ? WHERE id = ?")) {
                ps.setDouble(1, newVal); ps.setInt(2, team.getId());
                ps.executeUpdate();
            }
        });
        broadcastTeam(team, player.getName() + " deposited " + EconomyManager.format(amount) + " " + currency + " into team bank.");
    }

    public void withdrawBank(Player player, String currency, double amount) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        TeamRank rank = team.getMemberRank(player.getName().toLowerCase());
        if (rank == null || !rank.canWithdraw()) { Msg.error(player, "You need Co-Leader+ to withdraw!"); return; }
        if (amount <= 0) { Msg.error(player, "Amount must be positive!"); return; }
        UUID uuid = player.getUniqueId();
        String col;
        switch (currency.toLowerCase()) {
            case "money" -> {
                if (team.getBankMoney() < amount) { Msg.error(player, "Not enough money in bank!"); return; }
                team.setBankMoney(team.getBankMoney() - amount);
                economy.addMoney(uuid, amount);
                col = "bank_money";
            }
            case "gems" -> {
                if (team.getBankGems() < amount) { Msg.error(player, "Not enough gems in bank!"); return; }
                team.setBankGems(team.getBankGems() - amount);
                economy.addGems(uuid, amount);
                col = "bank_gems";
            }
            case "stars" -> {
                if (team.getBankStars() < amount) { Msg.error(player, "Not enough stars in bank!"); return; }
                team.setBankStars(team.getBankStars() - amount);
                economy.addStars(uuid, amount);
                col = "bank_stars";
            }
            default -> { Msg.error(player, "Use: money, gems, or stars"); return; }
        }
        double newVal = switch (col) {
            case "bank_money" -> team.getBankMoney();
            case "bank_gems" -> team.getBankGems();
            case "bank_stars" -> team.getBankStars();
            default -> 0;
        };
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE su_teams SET " + col + " = ? WHERE id = ?")) {
                ps.setDouble(1, newVal); ps.setInt(2, team.getId());
                ps.executeUpdate();
            }
        });
        broadcastTeam(team, player.getName() + " withdrew " + EconomyManager.format(amount) + " " + currency + " from team bank.");
    }

    public void showBank(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        player.sendMessage(Component.text("[SU] ", GOLD).append(Component.text("=== Team Bank ===", YELLOW)));
        player.sendMessage(Component.text("  " + EconomyManager.MONEY_ICON + " $", GREEN)
                .append(Component.text(EconomyManager.format(team.getBankMoney()), WHITE)));
        player.sendMessage(Component.text("  " + EconomyManager.GEMS_ICON + " ", CYAN)
                .append(Component.text(EconomyManager.format(team.getBankGems()), WHITE)));
        player.sendMessage(Component.text("  " + EconomyManager.STARS_ICON + " ", PURPLE)
                .append(Component.text(EconomyManager.format(team.getBankStars()), WHITE)));
    }

    // ==================== TEAM VAULT ====================

    public void openVault(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        if (!team.hasVault()) { Msg.error(player, "Team vault unlocks at level 10! (Current: " + team.getLevel() + ")"); return; }
        TeamHolder holder = new TeamHolder(TeamHolder.Type.TEAM_VAULT);
        holder.setTeamId(team.getId());
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Team Vault - " + team.getName(), GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT vault_data FROM su_teams WHERE id = ?")) {
                ps.setInt(1, team.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getString("vault_data");
                }
            }
            return null;
        }).thenAccept(data -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (data != null && !data.isEmpty()) {
                ItemStack[] items = deserializeItems(data);
                if (items != null) {
                    for (int i = 0; i < Math.min(items.length, 54); i++) {
                        if (items[i] != null) inv.setItem(i, items[i]);
                    }
                }
            }
            player.openInventory(inv);
        }));
    }

    public void saveVault(int teamId, Inventory inventory) {
        ItemStack[] items = new ItemStack[54];
        for (int i = 0; i < 54; i++) items[i] = inventory.getItem(i);
        String data = serializeItems(items);
        if (data == null) return;
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE su_teams SET vault_data = ? WHERE id = ?")) {
                ps.setString(1, data); ps.setInt(2, teamId);
                ps.executeUpdate();
            }
        });
    }

    // ==================== TEAM PVP ====================

    public void requestPvP(Player player, String targetTeamName, int minutes) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        Team target = getTeamByName(targetTeamName);
        if (target == null) { Msg.error(player, "Team not found!"); return; }
        if (target.getId() == team.getId()) { Msg.error(player, "You can't battle your own team!"); return; }
        if (minutes < 1 || minutes > 30) { Msg.error(player, "Duration: 1-30 minutes!"); return; }
        if (activeBattles.containsKey(team.getId())) { Msg.error(player, "You're already in a battle!"); return; }
        PvPBattle existing = pvpRequests.get(target.getId());
        if (existing != null && existing.team2Id == team.getId()) {
            startPvPBattle(team.getId(), target.getId(), minutes);
            pvpRequests.remove(target.getId());
            return;
        }
        pvpRequests.put(team.getId(), new PvPBattle(team.getId(), target.getId(), System.currentTimeMillis() + minutes * 60_000L));
        Msg.success(player, "PvP challenge sent to " + target.getName() + " for " + minutes + " minutes!");
        broadcastTeam(target, team.getName() + " challenged you to PvP! (" + minutes + "min) Use: /teampvp accept");
    }

    public void acceptPvP(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        PvPBattle request = null;
        int requestingTeamId = -1;
        for (Map.Entry<Integer, PvPBattle> entry : pvpRequests.entrySet()) {
            if (entry.getValue().team2Id == team.getId()) {
                request = entry.getValue();
                requestingTeamId = entry.getKey();
                break;
            }
        }
        if (request == null) { Msg.error(player, "No pending PvP requests!"); return; }
        pvpRequests.remove(requestingTeamId);
        long duration = request.endTime - System.currentTimeMillis();
        if (duration <= 0) duration = 600_000;
        startPvPBattle(requestingTeamId, team.getId(), (int) (duration / 60_000));
    }

    public void cancelPvP(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        if (pvpRequests.remove(team.getId()) != null) {
            Msg.success(player, "PvP request cancelled.");
            return;
        }
        PvPBattle battle = activeBattles.get(team.getId());
        if (battle != null) {
            endPvPBattle(battle);
            Msg.success(player, "PvP battle surrendered.");
            return;
        }
        Msg.error(player, "No active PvP request or battle!");
    }

    private void startPvPBattle(int team1Id, int team2Id, int minutes) {
        PvPBattle battle = new PvPBattle(team1Id, team2Id, System.currentTimeMillis() + minutes * 60_000L);
        activeBattles.put(team1Id, battle);
        activeBattles.put(team2Id, battle);
        Team t1 = teamCache.get(team1Id);
        Team t2 = teamCache.get(team2Id);
        if (t1 != null) broadcastTeam(t1, "PvP battle started vs " + (t2 != null ? t2.getName() : "?") + "! " + minutes + " minutes!");
        if (t2 != null) broadcastTeam(t2, "PvP battle started vs " + (t1 != null ? t1.getName() : "?") + "! " + minutes + " minutes!");
    }

    private void endPvPBattle(PvPBattle battle) {
        activeBattles.remove(battle.team1Id);
        activeBattles.remove(battle.team2Id);
        Team t1 = teamCache.get(battle.team1Id);
        Team t2 = teamCache.get(battle.team2Id);
        int t1Kills = battle.getTeamKills(battle.team1Id);
        int t2Kills = battle.getTeamKills(battle.team2Id);
        String result = (t1 != null ? t1.getName() : "?") + " " + t1Kills + " - " + t2Kills + " " + (t2 != null ? t2.getName() : "?");
        if (t1 != null) broadcastTeam(t1, "PvP battle ended! " + result);
        if (t2 != null) broadcastTeam(t2, "PvP battle ended! " + result);
        int winnerTeamId = t1Kills > t2Kills ? battle.team1Id : (t2Kills > t1Kills ? battle.team2Id : -1);
        if (winnerTeamId > 0) addTeamXp(winnerTeamId, 50);
    }

    public boolean isInPvPBattle(int teamId) {
        return activeBattles.containsKey(teamId);
    }

    public boolean areInPvPBattle(int team1Id, int team2Id) {
        PvPBattle battle = activeBattles.get(team1Id);
        if (battle == null) return false;
        return (battle.team1Id == team2Id || battle.team2Id == team2Id);
    }

    public void recordPvPKill(UUID killer) {
        Integer teamId = playerTeamCache.get(killer);
        if (teamId == null) return;
        PvPBattle battle = activeBattles.get(teamId);
        if (battle != null) battle.addKill(teamId, killer);
    }

    // ==================== TEAM INFO ====================

    public void showInfo(Player player, String teamName) {
        Team team;
        if (teamName != null) {
            team = getTeamByName(teamName);
        } else {
            team = getPlayerTeam(player);
        }
        if (team == null) { Msg.error(player, teamName != null ? "Team not found!" : "You are not in a team!"); return; }
        Component coloredName = buildGradientName(team.getName(), team.getColors());
        player.sendMessage(Component.text("[SU] ", GOLD).append(Component.text("=== ", GRAY)).append(coloredName).append(Component.text(" ===", GRAY)));
        player.sendMessage(Component.text("  Leader: ", GRAY).append(Component.text(team.getLeaderUsername(), YELLOW)));
        player.sendMessage(Component.text("  Level: ", GRAY).append(Component.text(team.getLevel(), GREEN))
                .append(Component.text(" (", GRAY)).append(Component.text(EconomyManager.format(team.getXp()), CYAN))
                .append(Component.text("/", GRAY)).append(Component.text(EconomyManager.format(Team.xpForLevel(team.getLevel() + 1)), CYAN))
                .append(Component.text(" XP)", GRAY)));
        player.sendMessage(Component.text("  Members: ", GRAY).append(Component.text(team.getMemberCount() + "/" + MAX_MEMBERS, WHITE)));
        StringBuilder memberList = new StringBuilder();
        team.getMembers().forEach((name, rank) -> {
            if (!memberList.isEmpty()) memberList.append(", ");
            memberList.append("[").append(rank.getDisplayName()).append("] ").append(name);
        });
        player.sendMessage(Component.text("  " + memberList, GRAY));
        player.sendMessage(Component.text("  Friendly Fire: ", GRAY)
                .append(Component.text(team.isFriendlyFire() ? "ON" : "OFF", team.isFriendlyFire() ? RED : GREEN)));
        if (!team.getAllyIds().isEmpty()) {
            StringBuilder allies = new StringBuilder();
            for (int allyId : team.getAllyIds()) {
                Team ally = teamCache.get(allyId);
                if (ally != null) {
                    if (!allies.isEmpty()) allies.append(", ");
                    allies.append(ally.getName());
                }
            }
            player.sendMessage(Component.text("  Allies: ", GRAY).append(Component.text(allies.toString(), CYAN)));
        }
    }

    // ==================== TEAM LIST ====================

    public void openTeamList(Player player, int page) {
        List<Team> sorted = teamCache.values().stream()
                .sorted(Comparator.comparingInt(Team::getLevel).reversed()
                        .thenComparing(Comparator.comparingLong(Team::getXp).reversed()))
                .collect(Collectors.toList());
        int totalPages = Math.max(1, (sorted.size() + 27) / 28);
        int p = Math.max(0, Math.min(page, totalPages - 1));
        TeamHolder holder = new TeamHolder(TeamHolder.Type.TEAM_LIST);
        holder.setPage(p);
        Inventory inv = Bukkit.createInventory(holder, 36,
                Component.text("Teams (Page " + (p + 1) + "/" + totalPages + ")", GOLD)
                        .decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);
        int start = p * 28;
        for (int i = 0; i < 28 && start + i < sorted.size(); i++) {
            Team team = sorted.get(start + i);
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(buildGradientName(team.getName(), team.getColors()).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Leader: " + team.getLeaderUsername(), GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("Level: " + team.getLevel() + " | Members: " + team.getMemberCount(), GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        if (p > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm = prev.getItemMeta();
            pm.displayName(Component.text("Previous Page", YELLOW).decoration(TextDecoration.ITALIC, false));
            prev.setItemMeta(pm);
            inv.setItem(30, prev);
        }
        if (p < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nm = next.getItemMeta();
            nm.displayName(Component.text("Next Page", YELLOW).decoration(TextDecoration.ITALIC, false));
            next.setItemMeta(nm);
            inv.setItem(32, next);
        }
        player.openInventory(inv);
    }

    // ==================== TEAM TOP ====================

    public void showTop(Player player) {
        List<Team> sorted = teamCache.values().stream()
                .sorted(Comparator.comparingInt(Team::getLevel).reversed()
                        .thenComparing(Comparator.comparingLong(Team::getXp).reversed()))
                .limit(10)
                .collect(Collectors.toList());
        player.sendMessage(Component.text("[SU] ", GOLD).append(Component.text("=== Top Teams ===", YELLOW)));
        for (int i = 0; i < sorted.size(); i++) {
            Team team = sorted.get(i);
            Component entry = Component.text("  " + (i + 1) + ". ", GRAY)
                    .append(buildGradientName(team.getName(), team.getColors()))
                    .append(Component.text(" - Lv" + team.getLevel() + " (" + team.getMemberCount() + " members)", GRAY));
            player.sendMessage(entry);
        }
        if (sorted.isEmpty()) Msg.gray(player, "  No teams exist yet.");
    }

    // ==================== TEAM MISSIONS ====================

    public void showMissions(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        ensureDailyMissions(team.getId());
        List<TeamMission> missions = dailyMissions.get(team.getId());
        if (missions == null || missions.isEmpty()) { Msg.error(player, "No missions available!"); return; }
        player.sendMessage(Component.text("[SU] ", GOLD).append(Component.text("=== Daily Missions ===", YELLOW)));
        for (int i = 0; i < missions.size(); i++) {
            TeamMission m = missions.get(i);
            TextColor color = m.completed ? GREEN : GRAY;
            String status = m.completed ? "DONE" : (m.currentAmount + "/" + m.targetAmount);
            player.sendMessage(Component.text("  " + (i + 1) + ". " +
                    MISSION_NAMES.getOrDefault(m.type, m.type) + " - " + status +
                    " (+" + m.xpReward + " XP)", color));
        }
    }

    public void ensureDailyMissions(int teamId) {
        List<TeamMission> existing = dailyMissions.get(teamId);
        if (existing != null && !existing.isEmpty()) return;
        db.queryAsync(conn -> {
            String today = LocalDate.now().toString();
            List<TeamMission> missions = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT mission_type, target_amount, current_amount, completed FROM su_team_missions " +
                    "WHERE team_id = ? AND mission_date = ?")) {
                ps.setInt(1, teamId);
                ps.setString(2, today);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int typeIdx = indexOf(MISSION_TYPES, rs.getString("mission_type"));
                        int tier = tierForTarget(typeIdx, rs.getInt("target_amount"));
                        missions.add(new TeamMission(rs.getString("mission_type"),
                                rs.getInt("target_amount"), MISSION_XP[tier]));
                        missions.getLast().currentAmount = rs.getInt("current_amount");
                        missions.getLast().completed = rs.getBoolean("completed");
                    }
                }
            }
            if (missions.isEmpty()) {
                Random rand = new Random();
                List<Integer> indices = new ArrayList<>();
                for (int i = 0; i < MISSION_TYPES.length; i++) indices.add(i);
                Collections.shuffle(indices, rand);
                for (int i = 0; i < 3; i++) {
                    int typeIdx = indices.get(i);
                    int tier = rand.nextInt(3);
                    int target = MISSION_TARGETS[typeIdx][tier];
                    missions.add(new TeamMission(MISSION_TYPES[typeIdx], target, MISSION_XP[tier]));
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO su_team_missions (team_id, mission_type, target_amount, mission_date) VALUES (?, ?, ?, ?)")) {
                        ps.setInt(1, teamId); ps.setString(2, MISSION_TYPES[typeIdx]);
                        ps.setInt(3, target); ps.setString(4, today);
                        ps.executeUpdate();
                    }
                }
            }
            return missions;
        }).thenAccept(missions -> {
            if (missions != null) dailyMissions.put(teamId, missions);
        });
    }

    public void progressMission(int teamId, String type, int amount) {
        List<TeamMission> missions = dailyMissions.get(teamId);
        if (missions == null) return;
        for (TeamMission m : missions) {
            if (m.type.equals(type) && !m.completed) {
                m.currentAmount += amount;
                if (m.currentAmount >= m.targetAmount) {
                    m.completed = true;
                    addTeamXp(teamId, m.xpReward);
                    Team team = teamCache.get(teamId);
                    if (team != null) broadcastTeam(team, "Mission complete: " +
                            MISSION_NAMES.getOrDefault(type, type) + "! +" + m.xpReward + " Team XP");
                }
                String today = LocalDate.now().toString();
                db.executeAsync(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE su_team_missions SET current_amount = ?, completed = ? " +
                            "WHERE team_id = ? AND mission_type = ? AND mission_date = ?")) {
                        ps.setInt(1, m.currentAmount); ps.setBoolean(2, m.completed);
                        ps.setInt(3, teamId); ps.setString(4, type); ps.setString(5, today);
                        ps.executeUpdate();
                    }
                });
                return;
            }
        }
    }

    // ==================== TEAM XP / LEVEL ====================

    public void addTeamXp(int teamId, long xpAmount) {
        Team team = teamCache.get(teamId);
        if (team == null) return;
        team.setXp(team.getXp() + xpAmount);
        int newLevel = Team.levelForXp(team.getXp());
        if (newLevel > team.getLevel()) {
            int oldLevel = team.getLevel();
            team.setLevel(newLevel);
            broadcastTeam(team, "Team leveled up! " + oldLevel + " -> " + newLevel);
            checkLevelPerks(team, oldLevel, newLevel);
        }
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE su_teams SET xp = ?, level = ? WHERE id = ?")) {
                ps.setLong(1, team.getXp()); ps.setInt(2, team.getLevel()); ps.setInt(3, teamId);
                ps.executeUpdate();
            }
        });
    }

    private void checkLevelPerks(Team team, int oldLevel, int newLevel) {
        if (oldLevel < 5 && newLevel >= 5) broadcastTeam(team, "Perk unlocked: +5% Money bonus for all members!");
        if (oldLevel < 10 && newLevel >= 10) broadcastTeam(team, "Perk unlocked: Team Vault! (/team vault)");
        if (oldLevel < 15 && newLevel >= 15) broadcastTeam(team, "Perk unlocked: +10% XP bonus for all members!");
        if (oldLevel < 20 && newLevel >= 20) broadcastTeam(team, "Perk unlocked: Team Banner!");
        if (oldLevel < 25 && newLevel >= 25) broadcastTeam(team, "Perk unlocked: +1 Ally slot (4 total)!");
        if (oldLevel < 30 && newLevel >= 30) broadcastTeam(team, "Perk unlocked: Team Territory!");
        if (oldLevel < 50 && newLevel >= 50) broadcastTeam(team, "Perk unlocked: Particle Border!");
    }

    public double getTeamMoneyBonus(UUID uuid) {
        Team team = getPlayerTeam(uuid);
        if (team == null) return 0;
        return team.getMoneyBonusPercent();
    }

    public double getTeamXpBonus(UUID uuid) {
        Team team = getPlayerTeam(uuid);
        if (team == null) return 0;
        return team.getXpBonusPercent();
    }

    // ==================== RESOURCE SHARING ====================

    public void requestResources(Player player, int amount) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        if (team.getAllyIds().isEmpty()) { Msg.error(player, "You have no allies!"); return; }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) { Msg.error(player, "Hold the item you want to request!"); return; }
        if (amount <= 0) { Msg.error(player, "Amount must be positive!"); return; }
        String itemName = hand.getType().name().toLowerCase().replace('_', ' ');
        for (int allyId : team.getAllyIds()) {
            Team ally = teamCache.get(allyId);
            if (ally != null) {
                broadcastTeam(ally, "[Request] " + player.getName() + " (" + team.getName() + ") needs " +
                        amount + "x " + itemName + ". Use /pay or trade to help!");
            }
        }
        Msg.success(player, "Resource request sent to allies for " + amount + "x " + itemName + ".");
    }

    // ==================== TEAM WARS ====================

    public void declareWar(Player player, String targetTeamName) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        TeamRank rank = team.getMemberRank(player.getName().toLowerCase());
        if (rank == null || !rank.canDeclareWar()) { Msg.error(player, "Only the Leader can declare war!"); return; }
        Team target = getTeamByName(targetTeamName);
        if (target == null) { Msg.error(player, "Team not found!"); return; }
        if (target.getId() == team.getId()) { Msg.error(player, "You can't war your own team!"); return; }
        if (team.getAllyIds().contains(target.getId())) { Msg.error(player, "You can't war an ally! Remove alliance first."); return; }
        String warKey = Math.min(team.getId(), target.getId()) + "-" + Math.max(team.getId(), target.getId());
        if (activeWars.containsKey(warKey)) { Msg.error(player, "Already at war!"); return; }
        TeamWar existing = warRequests.get(warKey);
        if (existing != null && existing.team1Id != team.getId()) {
            existing.accepted = true;
            activeWars.put(warKey, existing);
            warRequests.remove(warKey);
            broadcastTeam(team, "WAR declared against " + target.getName() + "! PvP kills give bonus XP!");
            broadcastTeam(target, "WAR declared by " + team.getName() + "! PvP kills give bonus XP!");
        } else {
            warRequests.put(warKey, new TeamWar(team.getId(), target.getId(), System.currentTimeMillis()));
            Msg.success(player, "War declaration sent to " + target.getName() + "!");
            broadcastTeam(target, team.getName() + " declared war! Leader: /team war " + team.getName() + " to accept.");
        }
    }

    public void surrenderWar(Player player) {
        Team team = getPlayerTeam(player);
        if (team == null) { Msg.error(player, "You are not in a team!"); return; }
        TeamRank rank = team.getMemberRank(player.getName().toLowerCase());
        if (rank != TeamRank.LEADER) { Msg.error(player, "Only the Leader can surrender!"); return; }
        String warKey = null;
        TeamWar war = null;
        for (Map.Entry<String, TeamWar> entry : activeWars.entrySet()) {
            TeamWar w = entry.getValue();
            if (w.team1Id == team.getId() || w.team2Id == team.getId()) {
                warKey = entry.getKey();
                war = w;
                break;
            }
        }
        if (war == null) { Msg.error(player, "You are not at war!"); return; }
        activeWars.remove(warKey);
        int otherTeamId = war.team1Id == team.getId() ? war.team2Id : war.team1Id;
        Team other = teamCache.get(otherTeamId);
        broadcastTeam(team, "Your team surrendered to " + (other != null ? other.getName() : "?") + "!");
        if (other != null) {
            broadcastTeam(other, team.getName() + " surrendered! You win!");
            addTeamXp(otherTeamId, 200);
        }
    }

    public boolean areAtWar(int team1Id, int team2Id) {
        String key = Math.min(team1Id, team2Id) + "-" + Math.max(team1Id, team2Id);
        TeamWar war = activeWars.get(key);
        return war != null && war.accepted;
    }

    public void recordWarKill(int killerTeamId, int victimTeamId) {
        String key = Math.min(killerTeamId, victimTeamId) + "-" + Math.max(killerTeamId, victimTeamId);
        TeamWar war = activeWars.get(key);
        if (war == null) return;
        if (war.team1Id == killerTeamId) war.team1Kills++;
        else war.team2Kills++;
        addTeamXp(killerTeamId, 10);
    }

    // ==================== HELPERS ====================

    private void removeFromTeam(Team team, String username) {
        team.getMembers().remove(username.toLowerCase());
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET team_id = NULL, team_rank = NULL WHERE username = ?")) {
                ps.setString(1, username.toLowerCase());
                ps.executeUpdate();
            }
        });
    }

    private void broadcastTeam(Team team, String message) {
        Component msg = Component.text("[SU] ", GOLD)
                .append(Component.text("[" + team.getName() + "] ", CYAN))
                .append(Component.text(message, GREEN));
        for (String member : team.getMembers().keySet()) {
            Player mp = Bukkit.getPlayerExact(member);
            if (mp != null) mp.sendMessage(msg);
        }
    }

    private void broadcastServer(String message) {
        Bukkit.getServer().sendMessage(Component.text("[SU] ", GOLD).append(Component.text(message, YELLOW)));
    }

    public Component buildGradientName(String name, List<String> colors) {
        if (colors.isEmpty()) return Component.text(name, WHITE);
        if (colors.size() == 1) return Component.text(name, TextColor.color(parseHex(colors.getFirst())));
        var builder = Component.text();
        for (int i = 0; i < name.length(); i++) {
            float ratio = (float) i / Math.max(1, name.length() - 1);
            int segmentCount = colors.size() - 1;
            float segPos = ratio * segmentCount;
            int seg = Math.min((int) segPos, segmentCount - 1);
            float segRatio = segPos - seg;
            int c1 = parseHex(colors.get(seg));
            int c2 = parseHex(colors.get(seg + 1));
            int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * segRatio);
            int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * segRatio);
            int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * segRatio);
            builder.append(Component.text(String.valueOf(name.charAt(i)), TextColor.color(r, g, b)));
        }
        return builder.build();
    }

    private static int parseHex(String hex) {
        if (hex.startsWith("#")) hex = hex.substring(1);
        return Integer.parseInt(hex, 16);
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        pendingInvites.values().forEach(list -> list.removeIf(i -> now - i.timestamp > INVITE_EXPIRE_MS));
        pendingInvites.values().removeIf(List::isEmpty);
        pvpRequests.values().removeIf(b -> now > b.endTime);
        for (var it = activeBattles.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (now > entry.getValue().endTime) {
                endPvPBattle(entry.getValue());
                break;
            }
        }
        dailyMissions.entrySet().removeIf(e -> {
            List<TeamMission> missions = e.getValue();
            return missions.isEmpty();
        });
    }

    private static String serializeItems(ItemStack[] items) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {
            oos.writeInt(items.length);
            for (ItemStack item : items) oos.writeObject(item);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    private static ItemStack[] deserializeItems(String data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bis)) {
            int len = ois.readInt();
            ItemStack[] items = new ItemStack[len];
            for (int i = 0; i < len; i++) items[i] = (ItemStack) ois.readObject();
            return items;
        } catch (Exception e) {
            return null;
        }
    }

    private static int indexOf(String[] arr, String val) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].equals(val)) return i;
        }
        return 0;
    }

    private static int tierForTarget(int typeIdx, int target) {
        if (typeIdx < 0 || typeIdx >= MISSION_TARGETS.length) return 0;
        int[] targets = MISSION_TARGETS[typeIdx];
        for (int i = targets.length - 1; i >= 0; i--) {
            if (target >= targets[i]) return i;
        }
        return 0;
    }

    // ==================== INNER CLASSES ====================

    public record TeamInvite(int teamId, String teamName, String inviter, long timestamp) {}

    public static class PvPBattle {
        final int team1Id;
        final int team2Id;
        final long endTime;
        private final Map<Integer, Integer> teamKills = new ConcurrentHashMap<>();

        PvPBattle(int t1, int t2, long end) { team1Id = t1; team2Id = t2; endTime = end; }

        void addKill(int teamId, UUID killer) { teamKills.merge(teamId, 1, Integer::sum); }
        int getTeamKills(int teamId) { return teamKills.getOrDefault(teamId, 0); }
    }

    public static class TeamMission {
        final String type;
        final int targetAmount;
        final int xpReward;
        int currentAmount;
        boolean completed;

        TeamMission(String t, int target, int xp) { type = t; targetAmount = target; xpReward = xp; }
    }

    public static class TeamWar {
        final int team1Id;
        final int team2Id;
        final long startTime;
        int team1Kills;
        int team2Kills;
        boolean accepted;

        TeamWar(int t1, int t2, long start) { team1Id = t1; team2Id = t2; startTime = start; }
    }
}
