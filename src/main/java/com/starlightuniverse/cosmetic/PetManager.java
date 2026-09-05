package com.starlightuniverse.cosmetic;

import com.starlightuniverse.database.DatabaseManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PetManager {

    private static final long PET_DURATION_MS = 14L * 24 * 60 * 60 * 1000;
    private static final double FOLLOW_DISTANCE = 3.0;
    private static final double TELEPORT_DISTANCE = 12.0;
    private static final double FOLLOW_SPEED = 0.25;

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);

    private static final NamespacedKey PET_TAG_KEY = NamespacedKey.fromString("starlightuniverse:cosmetic_pet");
    private static final NamespacedKey PET_SCROLL_TAG = NamespacedKey.fromString("starlightuniverse:pet_scroll");

    private final JavaPlugin plugin;
    private final DatabaseManager db;

    private final Map<UUID, PetData> activePets = new ConcurrentHashMap<>();
    private final Map<UUID, Map<PetType, Long>> ownedPets = new ConcurrentHashMap<>();

    private BukkitTask followTask;
    private BukkitTask expiryTask;

    public PetManager(JavaPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void start() {
        followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickFollow, 5L, 5L);
        expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickExpiry, 20L * 60, 20L * 60);
    }

    public void shutdown() {
        if (followTask != null) followTask.cancel();
        if (expiryTask != null) expiryTask.cancel();
        for (PetData data : activePets.values()) {
            if (data.entity != null && data.entity.isValid()) {
                data.entity.remove();
            }
        }
        activePets.clear();
    }

    // ── Scroll item ──

    public ItemStack createPetScroll() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Pet Scroll", CYAN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Right-click to open the pet menu", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Choose a pet companion that", YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("follows you for 14 days!", YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.setItemModel(NamespacedKey.fromString("starlight:pet_scroll"));
        meta.getPersistentDataContainer().set(PET_SCROLL_TAG, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPetScroll(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(PET_SCROLL_TAG, PersistentDataType.BYTE, (byte) 0) == 1;
    }

    // ── Pet selection GUI ──

    public void openPetMenu(Player player) {
        PetType[] types = PetType.values();
        int rows = Math.min(6, (int) Math.ceil((double) types.length / 7) + 1);
        int size = rows * 9;

        PetHolder holder = new PetHolder();
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text("Choose Your Pet", CYAN));
        holder.setInventory(inv);

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);
        for (int i = 0; i < size; i++) inv.setItem(i, border);

        Map<PetType, Long> owned = ownedPets.getOrDefault(player.getUniqueId(), Map.of());
        PetData currentPet = activePets.get(player.getUniqueId());
        long now = System.currentTimeMillis();

        int slot = 10;
        for (PetType type : types) {
            if (slot >= size) break;
            int col = slot % 9;
            if (col == 0) { slot++; continue; }
            if (col == 8) { slot += 2; continue; }

            ItemStack icon = new ItemStack(type.getIcon());
            ItemMeta meta = icon.getItemMeta();

            Long expiry = owned.get(type);
            boolean isOwned = expiry != null && expiry > now;
            boolean isActive = currentPet != null && currentPet.type == type;

            if (isActive) {
                meta.displayName(Component.text(type.getDisplayName(), type.getColor())
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));
                long remaining = expiry - now;
                meta.lore(List.of(
                        Component.text("ACTIVE", GREEN).decoration(TextDecoration.ITALIC, false)
                                .decoration(TextDecoration.BOLD, true),
                        Component.text("Time left: " + formatDuration(remaining), GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Click to deactivate", RED).decoration(TextDecoration.ITALIC, false)
                ));
                meta.setEnchantmentGlintOverride(true);
            } else if (isOwned) {
                meta.displayName(Component.text(type.getDisplayName(), type.getColor())
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));
                long remaining = expiry - now;
                meta.lore(List.of(
                        Component.text("OWNED", GREEN).decoration(TextDecoration.ITALIC, false),
                        Component.text("Time left: " + formatDuration(remaining), GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Click to activate", YELLOW).decoration(TextDecoration.ITALIC, false)
                ));
            } else {
                meta.displayName(Component.text(type.getDisplayName(), GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true));
                meta.lore(List.of(
                        Component.text("NOT OWNED", RED).decoration(TextDecoration.ITALIC, false),
                        Component.empty(),
                        Component.text("Use a Pet Scroll to unlock!", GRAY).decoration(TextDecoration.ITALIC, false)
                ));
            }

            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slot++;
        }

        player.openInventory(inv);
    }

    public void openScrollPetMenu(Player player) {
        PetType[] types = PetType.values();
        int rows = Math.min(6, (int) Math.ceil((double) types.length / 7) + 1);
        int size = rows * 9;

        PetHolder holder = new PetHolder();
        holder.setScrollMode(true);
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text("Choose a Pet (Scroll)", GOLD));
        holder.setInventory(inv);

        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);
        for (int i = 0; i < size; i++) inv.setItem(i, border);

        int slot = 10;
        for (PetType type : types) {
            if (slot >= size) break;
            int col = slot % 9;
            if (col == 0) { slot++; continue; }
            if (col == 8) { slot += 2; continue; }

            ItemStack icon = new ItemStack(type.getIcon());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(type.getDisplayName(), type.getColor())
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(
                    Component.text("Click to unlock for 14 days!", YELLOW).decoration(TextDecoration.ITALIC, false)
            ));
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            slot++;
        }

        player.openInventory(inv);
    }

    // ── Pet selection handling ──

    public void handleMenuClick(Player player, int slot, boolean scrollMode) {
        PetType type = getTypeFromSlot(slot);
        if (type == null) return;

        if (scrollMode) {
            handleScrollRedeem(player, type);
        } else {
            handlePetToggle(player, type);
        }
    }

    private void handleScrollRedeem(Player player, PetType type) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!isPetScroll(mainHand)) {
            Msg.error(player, "You need a Pet Scroll in your hand!");
            return;
        }

        UUID uuid = player.getUniqueId();
        Map<PetType, Long> owned = ownedPets.get(uuid);
        long now = System.currentTimeMillis();
        if (owned != null) {
            Long existing = owned.get(type);
            if (existing != null && existing > now) {
                Msg.error(player, "You already own this pet! Time left: " + formatDuration(existing - now));
                return;
            }
        }

        if (mainHand.getAmount() > 1) {
            mainHand.setAmount(mainHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        long expiry = now + PET_DURATION_MS;
        ownedPets.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(type, expiry);

        String lower = player.getName().toLowerCase();
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO su_cosmetic_pets (username, pet_type, expires_at) VALUES (?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at)")) {
                ps.setString(1, lower);
                ps.setString(2, type.name());
                ps.setTimestamp(3, new Timestamp(expiry));
                ps.executeUpdate();
            }
        });

        player.closeInventory();
        Msg.success(player, "You unlocked " + type.getDisplayName() + " for 14 days!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

        despawnPet(uuid);
        spawnPet(player, type);
    }

    private void handlePetToggle(Player player, PetType type) {
        UUID uuid = player.getUniqueId();
        PetData current = activePets.get(uuid);

        if (current != null && current.type == type) {
            despawnPet(uuid);
            player.closeInventory();
            Msg.info(player, type.getDisplayName() + " pet deactivated.");
            savePetState(player.getName().toLowerCase(), null);
            return;
        }

        Map<PetType, Long> owned = ownedPets.get(uuid);
        if (owned == null) {
            Msg.error(player, "You don't own this pet! Use a Pet Scroll to unlock it.");
            return;
        }
        Long expiry = owned.get(type);
        if (expiry == null || expiry < System.currentTimeMillis()) {
            Msg.error(player, "You don't own this pet! Use a Pet Scroll to unlock it.");
            return;
        }

        despawnPet(uuid);
        spawnPet(player, type);
        player.closeInventory();
        Msg.success(player, type.getDisplayName() + " pet activated!");
    }

    private PetType getTypeFromSlot(int slot) {
        PetType[] types = PetType.values();
        int index = 0;
        int s = 10;
        for (PetType type : types) {
            int col = s % 9;
            if (col == 0) { s++; col = s % 9; }
            if (col == 8) { s += 2; col = s % 9; }
            if (s == slot) return type;
            s++;
            index++;
        }
        return null;
    }

    // ── Pet spawn/despawn ──

    public void spawnPet(Player player, PetType type) {
        UUID uuid = player.getUniqueId();
        despawnPet(uuid);

        Location spawnLoc = player.getLocation().add(1, 0, 1);

        Bukkit.getScheduler().runTask(plugin, () -> {
            Mob entity = (Mob) player.getWorld().spawnEntity(spawnLoc, type.getEntityType());
            entity.setInvulnerable(true);
            entity.setAI(false);
            entity.setSilent(true);
            entity.setCollidable(false);
            entity.setPersistent(false);
            entity.setRemoveWhenFarAway(false);

            if (entity instanceof Ageable ageable) {
                ageable.setBaby();
                ageable.setAgeLock(true);
            }
            if (entity instanceof Tameable tameable) {
                tameable.setTamed(true);
                tameable.setOwner(player);
            }

            entity.customName(Component.text(player.getName() + "'s " + type.getDisplayName(), type.getColor()));
            entity.setCustomNameVisible(true);
            entity.addScoreboardTag("su_pet");
            entity.addScoreboardTag("su_pet_" + uuid);

            activePets.put(uuid, new PetData(type, entity));
            savePetState(player.getName().toLowerCase(), type);
        });
    }

    public void despawnPet(UUID uuid) {
        PetData data = activePets.remove(uuid);
        if (data != null && data.entity != null && data.entity.isValid()) {
            data.entity.remove();
        }
    }

    // ── Follow tick ──

    private void tickFollow() {
        for (Map.Entry<UUID, PetData> entry : activePets.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            PetData data = entry.getValue();
            if (player == null || !player.isOnline() || data.entity == null || !data.entity.isValid()) {
                continue;
            }

            Mob pet = data.entity;
            Location playerLoc = player.getLocation();
            Location petLoc = pet.getLocation();

            if (!playerLoc.getWorld().equals(petLoc.getWorld())) {
                pet.teleport(playerLoc.add(1, 0, 1));
                continue;
            }

            double distance = playerLoc.distance(petLoc);

            if (distance > TELEPORT_DISTANCE) {
                pet.teleport(playerLoc.add(1, 0, 1));
            } else if (distance > FOLLOW_DISTANCE) {
                double dx = playerLoc.getX() - petLoc.getX();
                double dz = playerLoc.getZ() - petLoc.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len > 0) {
                    dx /= len;
                    dz /= len;
                }

                Location target = petLoc.clone().add(dx * FOLLOW_SPEED, 0, dz * FOLLOW_SPEED);
                target.setY(playerLoc.getY());

                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                target.setYaw(yaw);
                target.setPitch(0);

                pet.teleport(target);
            }
        }
    }

    // ── Expiry tick ──

    private void tickExpiry() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<PetType, Long>> entry : ownedPets.entrySet()) {
            entry.getValue().entrySet().removeIf(e -> e.getValue() < now);
            if (entry.getValue().isEmpty()) {
                ownedPets.remove(entry.getKey());
            }
        }

        Iterator<Map.Entry<UUID, PetData>> it = activePets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PetData> entry = it.next();
            UUID uuid = entry.getKey();
            PetData data = entry.getValue();

            Map<PetType, Long> owned = ownedPets.get(uuid);
            if (owned == null || !owned.containsKey(data.type)) {
                if (data.entity != null && data.entity.isValid()) {
                    data.entity.remove();
                }
                it.remove();

                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    Msg.info(player, "Your " + data.type.getDisplayName() + " pet has expired.");
                }
            }
        }
    }

    // ── DB load/save ──

    public void loadPets(UUID uuid, String username) {
        db.queryAsync(conn -> {
            Map<PetType, Long> pets = new EnumMap<>(PetType.class);
            String activePetType = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT pet_type, expires_at, active FROM su_cosmetic_pets WHERE username = ? AND expires_at > NOW()")) {
                ps.setString(1, username.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            PetType type = PetType.valueOf(rs.getString("pet_type"));
                            pets.put(type, rs.getTimestamp("expires_at").getTime());
                            if (rs.getBoolean("active")) {
                                activePetType = type.name();
                            }
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            return new Object[]{pets, activePetType};
        }).thenAccept(result -> {
            if (result == null) return;
            @SuppressWarnings("unchecked")
            Map<PetType, Long> pets = (Map<PetType, Long>) result[0];
            String activeType = (String) result[1];

            if (!pets.isEmpty()) {
                ownedPets.put(uuid, new ConcurrentHashMap<>(pets));
            }

            if (activeType != null) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    PetType type = PetType.valueOf(activeType);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            spawnPet(player, type);
                        }
                    }, 40L);
                }
            }
        });
    }

    public void onPlayerQuit(UUID uuid) {
        despawnPet(uuid);
        ownedPets.remove(uuid);
    }

    private void savePetState(String username, PetType activeType) {
        db.executeAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE su_cosmetic_pets SET active = 0 WHERE username = ?")) {
                ps.setString(1, username);
                ps.executeUpdate();
            }
            if (activeType != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE su_cosmetic_pets SET active = 1 WHERE username = ? AND pet_type = ?")) {
                    ps.setString(1, username);
                    ps.setString(2, activeType.name());
                    ps.executeUpdate();
                }
            }
        });
    }

    // ── Command: /pet ──

    public void handlePetCommand(Player player) {
        openPetMenu(player);
    }

    // ── Utility ──

    public boolean hasPet(UUID uuid) {
        return activePets.containsKey(uuid);
    }

    public PetData getActivePet(UUID uuid) {
        return activePets.get(uuid);
    }

    private String formatDuration(long ms) {
        long totalMinutes = ms / 60_000;
        long days = totalMinutes / 1440;
        long hours = (totalMinutes % 1440) / 60;
        long minutes = totalMinutes % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    // ── Pet entity check ──

    public boolean isPetEntity(Entity entity) {
        return entity.getScoreboardTags().contains("su_pet");
    }

    // ── Inner data class ──

    public static class PetData {
        public final PetType type;
        public Mob entity;

        public PetData(PetType type, Mob entity) {
            this.type = type;
            this.entity = entity;
        }
    }
}
