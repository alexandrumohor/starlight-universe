package com.starlightuniverse.vote;

import com.starlightuniverse.crate.CrateManager;
import com.starlightuniverse.crate.CrateType;
import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoteManager {

    private static final int VOTE_LINK_COUNT = 6;
    private static final long COOLDOWN_MS = 24 * 60 * 60 * 1000L;
    private static final double STAR_REWARD = 0.02;
    private static final int KEY_REWARD = 1;

    private static final String[] VOTE_URLS = {
            "https://starlightuniverse.com/vote1",
            "https://starlightuniverse.com/vote2",
            "https://starlightuniverse.com/vote3",
            "https://starlightuniverse.com/vote4",
            "https://starlightuniverse.com/vote5",
            "https://starlightuniverse.com/vote6"
    };

    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyManager economy;
    private final CrateManager crateManager;

    private final Map<UUID, long[]> cooldownCache = new ConcurrentHashMap<>();

    public VoteManager(JavaPlugin plugin, DatabaseManager db, EconomyManager economy, CrateManager crateManager) {
        this.plugin = plugin;
        this.db = db;
        this.economy = economy;
        this.crateManager = crateManager;
    }

    public void loadCooldowns(UUID uuid, String username) {
        db.queryAsync(conn -> {
            long[] cooldowns = new long[VOTE_LINK_COUNT];
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT link_id, last_vote FROM su_votes WHERE username = ?")) {
                ps.setString(1, username.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int linkId = rs.getInt("link_id");
                        if (linkId >= 0 && linkId < VOTE_LINK_COUNT) {
                            cooldowns[linkId] = rs.getTimestamp("last_vote").getTime();
                        }
                    }
                }
            }
            return cooldowns;
        }).thenAccept(cd -> {
            if (cd != null) cooldownCache.put(uuid, cd);
        });
    }

    public void openVoteGui(Player player) {
        VoteHolder holder = new VoteHolder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Vote for Starlight Universe", GOLD)
                        .decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bm = border.getItemMeta();
        bm.displayName(Component.text(" "));
        border.setItemMeta(bm);
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        long now = System.currentTimeMillis();
        long[] cooldowns = cooldownCache.getOrDefault(player.getUniqueId(), new long[VOTE_LINK_COUNT]);

        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < VOTE_LINK_COUNT; i++) {
            boolean available = (now - cooldowns[i]) >= COOLDOWN_MS;
            ItemStack icon = new ItemStack(available ? Material.LIME_DYE : Material.RED_DYE);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text("Vote Link #" + (i + 1), available ? GREEN : RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            if (available) {
                lore.add(Component.text("Click to vote!", YELLOW).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Reward: 0.02★ + 1 Star Key", GRAY).decoration(TextDecoration.ITALIC, false));
            } else {
                long remaining = COOLDOWN_MS - (now - cooldowns[i]);
                long hours = remaining / 3_600_000;
                long minutes = (remaining % 3_600_000) / 60_000;
                lore.add(Component.text("Cooldown: " + hours + "h " + minutes + "m", RED)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(slots[i], icon);
        }

        inv.setItem(22, closeButton());
        player.openInventory(inv);
    }

    public void handleClick(Player player, int slot) {
        int[] slots = {10, 11, 12, 14, 15, 16};
        int linkIndex = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) { linkIndex = i; break; }
        }
        if (slot == 22) { player.closeInventory(); return; }
        if (linkIndex < 0 || linkIndex >= VOTE_LINK_COUNT) return;

        long now = System.currentTimeMillis();
        long[] cooldowns = cooldownCache.getOrDefault(player.getUniqueId(), new long[VOTE_LINK_COUNT]);
        if ((now - cooldowns[linkIndex]) < COOLDOWN_MS) {
            Msg.error(player, "This vote link is still on cooldown!");
            return;
        }

        player.closeInventory();
        String url = VOTE_URLS[linkIndex];
        player.sendMessage(Component.empty());
        player.sendMessage(Msg.prefix()
                .append(Component.text("Click here to vote: ", WHITE))
                .append(Component.text("[Vote Link #" + (linkIndex + 1) + "]", CYAN)
                        .decoration(TextDecoration.BOLD, true)
                        .clickEvent(ClickEvent.openUrl(url))));
        player.sendMessage(Msg.prefix()
                .append(Component.text("After voting, your reward will be given automatically.", GRAY)));
        player.sendMessage(Component.empty());
    }

    public void awardVote(String username, int linkId) {
        if (linkId < 0 || linkId >= VOTE_LINK_COUNT) return;

        Player player = Bukkit.getPlayerExact(username);
        UUID uuid = player != null ? player.getUniqueId() : null;

        if (uuid != null) {
            economy.addStars(uuid, STAR_REWARD);

            ItemStack key = crateManager.createKey(CrateType.STAR, KEY_REWARD);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(key);
            overflow.values().forEach(o -> player.getWorld().dropItemNaturally(player.getLocation(), o));

            long now = System.currentTimeMillis();
            long[] cooldowns = cooldownCache.computeIfAbsent(uuid, k -> new long[VOTE_LINK_COUNT]);
            cooldowns[linkId] = now;

            player.sendMessage(Component.empty());
            player.sendMessage(Msg.prefix()
                    .append(Component.text("Thanks for voting! ", GREEN))
                    .append(Component.text("+0.02★ + 1 Star Key", GOLD)));
            player.sendMessage(Component.empty());
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        }

        String lower = username.toLowerCase();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_votes (username, link_id, last_vote) VALUES (?, ?, NOW()) " +
                            "ON DUPLICATE KEY UPDATE last_vote = NOW()")) {
                ps.setString(1, lower);
                ps.setInt(2, linkId);
                ps.executeUpdate();
            }
        });
    }

    public void onPlayerQuit(UUID uuid) {
        cooldownCache.remove(uuid);
    }

    private ItemStack closeButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Close", RED).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
