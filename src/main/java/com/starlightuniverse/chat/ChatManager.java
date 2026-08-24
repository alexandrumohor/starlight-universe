package com.starlightuniverse.chat;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.admin.AdminRank;
import com.starlightuniverse.benefit.BenefitManager;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.emoji.EmojiManager;
import com.starlightuniverse.nameplate.NameplateManager;
import com.starlightuniverse.premium.PremiumManager;
import com.starlightuniverse.premium.PremiumRank;
import com.starlightuniverse.team.Team;
import com.starlightuniverse.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatManager {

    public enum ChatChannel { GLOBAL, TEAM, STAFF, LOCAL }

    private static final long COOLDOWN_MS = 1500;

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor MENTION_COLOR = TextColor.color(0xFFD700);

    private static final Pattern LINK_PATTERN = Pattern.compile(
            "(https?://|www\\.)[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE
    );

    static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w{1,16})");

    private static final Set<String> SWEAR_WORDS = Set.of(
            "fuck", "fucking", "fucker", "shit", "shitty", "bitch", "dick",
            "pussy", "cunt", "nigger", "nigga", "faggot", "fag", "retard",
            "retarded", "whore", "slut", "bastard", "cock", "wanker", "twat",
            "pula", "pizda", "muie", "futut", "futu", "fute", "cacat",
            "curva", "coaie", "plm", "fmm", "sugi", "handicapat",
            "bagami", "mortii", "mata", "matii", "mamata"
    );

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final AdminManager adminManager;
    private final PremiumManager premiumManager;
    private final TeamManager teamManager;
    private EmojiManager emojiManager;
    private BenefitManager benefitManager;
    private NameplateManager nameplateManager;

    private final Map<UUID, ChatChannel> channels = new ConcurrentHashMap<>();
    private final Map<UUID, Long> chatCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameColorCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> chatColorCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> nameTagCache = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> replyTargets = new ConcurrentHashMap<>();

    public ChatManager(JavaPlugin plugin, DatabaseManager db, AdminManager adminManager,
                       PremiumManager premiumManager, TeamManager teamManager) {
        this.plugin = plugin;
        this.db = db;
        this.adminManager = adminManager;
        this.premiumManager = premiumManager;
        this.teamManager = teamManager;
    }

    public void setEmojiManager(EmojiManager emojiManager) { this.emojiManager = emojiManager; }
    public void setBenefitManager(BenefitManager benefitManager) { this.benefitManager = benefitManager; }
    public void setNameplateManager(NameplateManager nameplateManager) { this.nameplateManager = nameplateManager; }
    public EmojiManager getEmojiManager() { return emojiManager; }
    public BenefitManager getBenefitManager() { return benefitManager; }
    public NameplateManager getNameplateManager() { return nameplateManager; }

    public String applyEmoji(Player player, String message) {
        if (emojiManager == null) return message;
        return emojiManager.replaceTokens(message, emojiManager.isUnlocked(player.getUniqueId()));
    }

    public void loadPlayer(Player player) {
        String username = player.getName().toLowerCase();
        UUID uuid = player.getUniqueId();
        db.queryAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name_color, chat_color, name_tag FROM su_players WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new String[]{
                                rs.getString("name_color"),
                                rs.getString("chat_color"),
                                rs.getString("name_tag")
                        };
                    }
                }
            }
            return new String[]{null, null, null};
        }).thenAccept(data -> {
            if (data != null) {
                if (data[0] != null) nameColorCache.put(uuid, data[0]);
                if (data[1] != null) chatColorCache.put(uuid, data[1]);
                if (data[2] != null) nameTagCache.put(uuid, data[2]);
            }
        });
    }

    public void unloadPlayer(UUID uuid) {
        channels.remove(uuid);
        chatCooldowns.remove(uuid);
        nameColorCache.remove(uuid);
        chatColorCache.remove(uuid);
        nameTagCache.remove(uuid);
        replyTargets.remove(uuid);
    }

    // ==================== CHANNELS ====================

    public ChatChannel getChannel(UUID uuid) {
        return channels.getOrDefault(uuid, ChatChannel.GLOBAL);
    }

    public void setChannel(UUID uuid, ChatChannel channel) {
        if (channel == ChatChannel.GLOBAL) {
            channels.remove(uuid);
        } else {
            channels.put(uuid, channel);
        }
    }

    public ChatChannel getEffectiveChannel(UUID uuid) {
        ChatChannel explicit = channels.getOrDefault(uuid, ChatChannel.GLOBAL);
        if (explicit != ChatChannel.GLOBAL) return explicit;
        if (adminManager.isInStaffChat(uuid)) return ChatChannel.STAFF;
        if (teamManager.isInTeamChat(uuid)) return ChatChannel.TEAM;
        return ChatChannel.GLOBAL;
    }

    public void switchToGlobal(UUID uuid) {
        channels.remove(uuid);
        if (teamManager.isInTeamChat(uuid)) {
            teamManager.disableTeamChat(uuid);
        }
        if (adminManager.isInStaffChat(uuid)) {
            adminManager.toggleStaffChat(uuid);
        }
    }

    public void switchToTeam(UUID uuid) {
        channels.put(uuid, ChatChannel.TEAM);
        if (adminManager.isInStaffChat(uuid)) {
            adminManager.toggleStaffChat(uuid);
        }
        teamManager.disableTeamChat(uuid);
    }

    public void switchToStaff(UUID uuid) {
        channels.put(uuid, ChatChannel.STAFF);
        teamManager.disableTeamChat(uuid);
    }

    public void switchToLocal(UUID uuid) {
        channels.put(uuid, ChatChannel.LOCAL);
        if (adminManager.isInStaffChat(uuid)) {
            adminManager.toggleStaffChat(uuid);
        }
        teamManager.disableTeamChat(uuid);
    }

    // ==================== COOLDOWN ====================

    public boolean canChat(UUID uuid) {
        if (isStaffExempt(uuid)) return true;
        Long last = chatCooldowns.get(uuid);
        return last == null || System.currentTimeMillis() - last >= COOLDOWN_MS;
    }

    public long getCooldownRemaining(UUID uuid) {
        Long last = chatCooldowns.get(uuid);
        if (last == null) return 0;
        long remaining = COOLDOWN_MS - (System.currentTimeMillis() - last);
        return remaining > 0 ? remaining : 0;
    }

    public void recordChatCooldown(UUID uuid) {
        chatCooldowns.put(uuid, System.currentTimeMillis());
    }

    public boolean isStaffExempt(UUID uuid) {
        return adminManager.getAdminLevel(uuid) > 0;
    }

    // ==================== FILTERS ====================

    public boolean containsLink(String message) {
        return LINK_PATTERN.matcher(message).find();
    }

    public String filterSwears(String message) {
        String[] words = message.split("(?<=\\s)|(?=\\s)");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            String clean = word.toLowerCase().replaceAll("[^a-zA-Z]", "");
            if (!clean.isEmpty() && SWEAR_WORDS.contains(clean)) {
                result.append("*".repeat(word.length()));
            } else {
                result.append(word);
            }
        }
        return result.toString();
    }

    // ==================== TAGS ====================

    public String getNameTag(UUID uuid) { return nameTagCache.get(uuid); }

    public void setNameTag(Player player, String tag) {
        UUID uuid = player.getUniqueId();
        String username = player.getName().toLowerCase();
        if (tag == null) {
            nameTagCache.remove(uuid);
        } else {
            nameTagCache.put(uuid, tag);
        }
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_players SET name_tag = ? WHERE username = ?")) {
                ps.setString(1, tag);
                ps.setString(2, username);
                ps.executeUpdate();
            }
        });
    }

    public String getNameColor(UUID uuid) { return nameColorCache.get(uuid); }
    public String getChatColor(UUID uuid) { return chatColorCache.get(uuid); }

    // ==================== REPLY ====================

    public void setReplyTarget(UUID player, UUID target) {
        replyTargets.put(player, target);
    }

    public UUID getReplyTarget(UUID player) {
        return replyTargets.get(player);
    }

    // ==================== FORMAT BUILDING ====================

    public Component buildChatMessage(Player player, String message, String replyToName) {
        UUID uuid = player.getUniqueId();
        Component result = Component.empty();

        Team team = teamManager.getPlayerTeam(uuid);
        if (team != null) {
            result = result.append(
                    teamManager.buildGradientName("[" + team.getName() + "]", team.getColors())
            ).append(Component.text(" "));
        }

        AdminRank adminRank = adminManager.getAdminRank(uuid);
        if (adminRank != AdminRank.NONE) {
            result = result.append(
                    Component.text(adminRank.getPrefix(), adminRank.getColor())
            ).append(Component.text(" "));
        }

        String customPrefix = benefitManager != null ? benefitManager.getCustomPrefix(uuid) : null;
        String tag = nameTagCache.get(uuid);
        PremiumRank premRank = premiumManager.getPlayerRank(uuid);
        if (customPrefix != null && !customPrefix.isEmpty()) {
            TextColor c = premRank != PremiumRank.NONE ? premRank.getColor() : GOLD;
            result = result.append(Component.text("[" + customPrefix + "]", c))
                    .append(Component.text(" "));
        } else if (tag != null && !tag.isEmpty()) {
            TextColor tagColor = premRank != PremiumRank.NONE ? premRank.getColor() : GRAY;
            result = result.append(
                    Component.text("[" + tag + "]", tagColor)
            ).append(Component.text(" "));
        } else {
            if (premRank != PremiumRank.NONE) {
                result = result.append(premRank.getColoredPrefix())
                        .append(Component.text(" "));
            }
        }

        String nameColorHex = nameColorCache.get(uuid);
        TextColor nameColor = WHITE;
        if (nameColorHex != null) {
            TextColor parsed = TextColor.fromHexString(nameColorHex);
            if (parsed != null) nameColor = parsed;
        }

        if (replyToName != null) {
            result = result.append(Component.text(player.getName(), nameColor))
                    .append(Component.text(" replies to ", GRAY))
                    .append(Component.text(replyToName, YELLOW))
                    .append(Component.text(" >> ", GRAY));
        } else {
            result = result.append(Component.text(player.getName(), nameColor))
                    .append(Component.text(" >> ", GRAY));
        }

        String chatColorHex = chatColorCache.get(uuid);
        TextColor msgColor = WHITE;
        if (premRank.hasColoredChat() && chatColorHex != null) {
            TextColor parsed = TextColor.fromHexString(chatColorHex);
            if (parsed != null) msgColor = parsed;
        }

        result = result.append(buildMessageWithMentions(message, msgColor));
        return result;
    }

    private Component buildMessageWithMentions(String message, TextColor defaultColor) {
        Matcher matcher = MENTION_PATTERN.matcher(message);
        if (!matcher.find()) {
            return Component.text(message, defaultColor);
        }

        matcher.reset();
        Component result = Component.empty();
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                result = result.append(Component.text(
                        message.substring(lastEnd, matcher.start()), defaultColor));
            }
            String mentionedName = matcher.group(1);
            Player mentioned = Bukkit.getPlayerExact(mentionedName);
            if (mentioned != null && mentioned.isOnline()) {
                result = result.append(Component.text("@" + mentioned.getName(), MENTION_COLOR)
                        .decoration(TextDecoration.BOLD, true));
            } else {
                result = result.append(Component.text(matcher.group(), defaultColor));
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < message.length()) {
            result = result.append(Component.text(message.substring(lastEnd), defaultColor));
        }

        return result;
    }

    public void playMentionSound(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(),
                        Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        });
    }

    // ==================== GETTERS ====================

    public JavaPlugin getPlugin() { return plugin; }
    public AdminManager getAdminManager() { return adminManager; }
    public PremiumManager getPremiumManager() { return premiumManager; }
    public TeamManager getTeamManager() { return teamManager; }
}
