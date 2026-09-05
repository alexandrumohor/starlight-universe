package com.starlightuniverse.voucher;

import com.starlightuniverse.booster.BoosterManager;
import com.starlightuniverse.booster.BoosterType;
import com.starlightuniverse.cosmetic.DisguiseManager;
import com.starlightuniverse.cosmetic.PetManager;
import com.starlightuniverse.cosmetic.TrailManager;
import com.starlightuniverse.crate.CrateManager;
import com.starlightuniverse.crate.CrateType;
import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.home.HomeManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class VoucherManager {

    private static final NamespacedKey TAG_VOUCHER_TYPE = NamespacedKey.fromString("starlightuniverse:voucher_type");
    private static final NamespacedKey TAG_VOUCHER_VALUE = NamespacedKey.fromString("starlightuniverse:voucher_value");
    private static final NamespacedKey TAG_VOUCHER_TIER = NamespacedKey.fromString("starlightuniverse:voucher_tier");

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor PURPLE = TextColor.color(0xAA00FF);

    private final JavaPlugin plugin;
    private final EconomyManager economy;
    private final HomeManager homeManager;
    private final CrateManager crateManager;

    private final Map<UUID, Integer> flyBankMinutes = new ConcurrentHashMap<>();
    private final Set<UUID> flyActive = ConcurrentHashMap.newKeySet();

    private BoosterManager boosterManager;
    private EnchantRemoverListener enchantRemoverListener;
    private PetManager petManager;
    private TrailManager trailManager;
    private DisguiseManager disguiseManager;

    public VoucherManager(JavaPlugin plugin, EconomyManager economy, HomeManager homeManager, CrateManager crateManager) {
        this.plugin = plugin;
        this.economy = economy;
        this.homeManager = homeManager;
        this.crateManager = crateManager;
    }

    public void setBoosterManager(BoosterManager boosterManager) {
        this.boosterManager = boosterManager;
    }

    public void setEnchantRemoverListener(EnchantRemoverListener listener) {
        this.enchantRemoverListener = listener;
    }

    public void setPetManager(PetManager petManager) {
        this.petManager = petManager;
    }

    public void setTrailManager(TrailManager trailManager) {
        this.trailManager = trailManager;
    }

    public void setDisguiseManager(DisguiseManager disguiseManager) {
        this.disguiseManager = disguiseManager;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickFly, 20L, 20L);
    }

    // ── Fly Voucher ──

    public ItemStack createFlyVoucher(int minutes) {
        ItemStack item = new ItemStack(Material.FEATHER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Fly Time Voucher", CYAN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Right-click to redeem", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("+" + minutes + " minutes of flight", GREEN).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.setItemModel(NamespacedKey.fromString("starlight:shop_fly"));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(TAG_VOUCHER_TYPE, PersistentDataType.STRING, "FLY");
        pdc.set(TAG_VOUCHER_VALUE, PersistentDataType.INTEGER, minutes);
        item.setItemMeta(meta);
        return item;
    }

    public void redeemFlyVoucher(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        int minutes = meta.getPersistentDataContainer().getOrDefault(TAG_VOUCHER_VALUE, PersistentDataType.INTEGER, 0);
        if (minutes <= 0) return;

        flyBankMinutes.merge(player.getUniqueId(), minutes, Integer::sum);
        consumeOne(player, item);
        Msg.success(player, "Added " + minutes + " minutes to your fly bank! Total: " +
                flyBankMinutes.getOrDefault(player.getUniqueId(), 0) + " min");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
    }

    public void toggleFly(Player player) {
        UUID uuid = player.getUniqueId();
        if (flyActive.contains(uuid)) {
            flyActive.remove(uuid);
            player.setAllowFlight(false);
            player.setFlying(false);
            Msg.info(player, "Fly disabled. Remaining: " + flyBankMinutes.getOrDefault(uuid, 0) + " min");
        } else {
            int bank = flyBankMinutes.getOrDefault(uuid, 0);
            if (bank <= 0) {
                Msg.error(player, "No fly time remaining! Use a Fly Time Voucher.");
                return;
            }
            flyActive.add(uuid);
            player.setAllowFlight(true);
            Msg.success(player, "Fly enabled! Bank: " + bank + " min");
        }
    }

    public int getFlyBank(UUID uuid) { return flyBankMinutes.getOrDefault(uuid, 0); }
    public boolean isFlyActive(UUID uuid) { return flyActive.contains(uuid); }

    public void disableFly(Player player) {
        flyActive.remove(player.getUniqueId());
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    private void tickFly() {
        Iterator<UUID> it = flyActive.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) { it.remove(); continue; }
            if (!player.isFlying()) continue;

            int remaining = flyBankMinutes.getOrDefault(uuid, 0);
            if (remaining <= 0) {
                it.remove();
                player.setAllowFlight(false);
                player.setFlying(false);
                Msg.error(player, "Fly time expired!");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                continue;
            }
            flyBankMinutes.put(uuid, remaining - 1);
            if (remaining <= 5) {
                Msg.info(player, "Fly time: " + (remaining - 1) + " min remaining!");
            }
        }
    }

    // ── Booster Voucher (5 types, delegated to BoosterManager) ──

    private static final NamespacedKey TAG_BOOSTER_KIND = NamespacedKey.fromString("starlightuniverse:booster_kind");

    public ItemStack createBooster(BoosterType type, double multiplier, int durationMinutes) {
        ItemStack item = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = item.getItemMeta();
        String mult = String.format("%.1fx", multiplier);
        meta.displayName(Component.text(type.getDisplayName() + " " + mult, type.getColor())
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Right-click to activate", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(mult + " multiplier for " + durationMinutes + " min", YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text(type.getDescription(), GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.setItemModel(NamespacedKey.fromString("starlight:cr_booster"));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(TAG_VOUCHER_TYPE, PersistentDataType.STRING, "BOOSTER");
        pdc.set(TAG_VOUCHER_VALUE, PersistentDataType.INTEGER, durationMinutes);
        pdc.set(TAG_VOUCHER_TIER, PersistentDataType.STRING, String.valueOf(multiplier));
        pdc.set(TAG_BOOSTER_KIND, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public void redeemBooster(Player player, ItemStack item) {
        if (boosterManager == null) {
            Msg.error(player, "Booster system is not available.");
            return;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int durationMin = pdc.getOrDefault(TAG_VOUCHER_VALUE, PersistentDataType.INTEGER, 0);
        String multStr = pdc.getOrDefault(TAG_VOUCHER_TIER, PersistentDataType.STRING, "1.0");
        double multiplier = Double.parseDouble(multStr);
        String kindStr = pdc.getOrDefault(TAG_BOOSTER_KIND, PersistentDataType.STRING, "XP_VANILLA");

        BoosterType type;
        try {
            type = BoosterType.valueOf(kindStr);
        } catch (IllegalArgumentException e) {
            type = kindStr.equals("XP") ? BoosterType.XP_VANILLA
                    : kindStr.equals("MONEY") ? BoosterType.MONEY_JOB
                    : BoosterType.XP_VANILLA;
        }

        consumeOne(player, item);
        boosterManager.activate(player, type, multiplier, durationMin);
    }

    // ── Protection Expansion Token ──

    public ItemStack createProtectionToken(int blocks) {
        ItemStack item = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Protection Blocks Token", GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Right-click to redeem", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("+" + String.format("%,d", blocks) + " Protection Blocks", YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.setItemModel(NamespacedKey.fromString("starlight:cr_protection"));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(TAG_VOUCHER_TYPE, PersistentDataType.STRING, "PROTECTION");
        pdc.set(TAG_VOUCHER_VALUE, PersistentDataType.INTEGER, blocks);
        item.setItemMeta(meta);
        return item;
    }

    public void redeemProtectionToken(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        int blocks = meta.getPersistentDataContainer().getOrDefault(TAG_VOUCHER_VALUE, PersistentDataType.INTEGER, 0);
        if (blocks <= 0) return;

        String lower = player.getName().toLowerCase();
        homeManager.addProtectionBlocks(lower, blocks);
        consumeOne(player, item);
        int newBudget = homeManager.getPlayerBlockBudget(lower);
        Msg.success(player, "+" + String.format("%,d", blocks) + " protection blocks! Budget: " + String.format("%,d", newBudget));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
    }

    // ── Random Gear Ticket ──

    private static final Map<CrateType, NamespacedKey> GEAR_TICKET_MODELS = Map.of(
            CrateType.STAR, NamespacedKey.fromString("starlight:gear_ticket_star"),
            CrateType.COSMIC, NamespacedKey.fromString("starlight:gear_ticket_cosmic"),
            CrateType.GALAXY, NamespacedKey.fromString("starlight:gear_ticket_galaxy"),
            CrateType.CELESTIAL, NamespacedKey.fromString("starlight:gear_ticket_celestial"),
            CrateType.UNIVERSE, NamespacedKey.fromString("starlight:gear_ticket_universe")
    );

    private static final TextColor LORE_RED = TextColor.fromHexString("#FF0000");
    private static final TextColor LORE_WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor ENCHANT_BLUE = TextColor.fromHexString("#0099FF");

    public ItemStack createGearTicket(CrateType tier) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        String tierName = tier.name().charAt(0) + tier.name().substring(1).toLowerCase();
        meta.displayName(Component.text(tierName + " Gear Ticket", tier.getColor())
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(gearTicketLore(tier));
        meta.setEnchantmentGlintOverride(true);
        meta.setItemModel(GEAR_TICKET_MODELS.get(tier));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(TAG_VOUCHER_TYPE, PersistentDataType.STRING, "GEAR_TICKET");
        pdc.set(TAG_VOUCHER_TIER, PersistentDataType.STRING, tier.name());
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> gearTicketLore(CrateType tier) {
        String tn = tier.getDisplayName().replace(" Crate", "");
        TextColor tc = tier.getColor();
        return switch (tier) {
            case STAR, COSMIC -> simpleGearLore(tc, tn,
                    "Helmet", "Chestplate", "Leggings", "Boots",
                    "Sword", "Pickaxe", "Axe", "Shovel", "Hoe", "Spear");
            case GALAXY -> typedGearLore(tc, tn, "II", "II", "II", "II");
            case CELESTIAL -> typedGearLore(tc, tn, "IV", "V", "V", "III");
            case UNIVERSE -> universeLore(tc, tn);
        };
    }

    private List<Component> simpleGearLore(TextColor tc, String tn, String... pieces) {
        List<Component> lore = new ArrayList<>();
        lore.add(gearHeader(tc, tn));
        lore.add(Component.empty());
        for (String piece : pieces) {
            lore.add(pieceLine(tc, tn, piece));
            lore.add(enchLine(ench("Unbreaking", "I")));
        }
        return lore;
    }

    private List<Component> typedGearLore(TextColor tc, String tn, String protLvl, String sharpLvl, String effLvl, String unbLvl) {
        List<Component> lore = new ArrayList<>();
        lore.add(gearHeader(tc, tn));
        lore.add(Component.empty());
        for (String piece : new String[]{"Helmet", "Chestplate", "Leggings", "Boots"}) {
            lore.add(pieceLine(tc, tn, piece));
            lore.add(enchLine(ench("Protection", protLvl), ench("Unbreaking", unbLvl)));
        }
        lore.add(pieceLine(tc, tn, "Sword"));
        lore.add(enchLine(ench("Sharpness", sharpLvl), ench("Unbreaking", unbLvl)));
        lore.add(pieceLine(tc, tn, "Pickaxe"));
        lore.add(enchLine(ench("Efficiency", effLvl), ench("Unbreaking", unbLvl)));
        lore.add(pieceLine(tc, tn, "Axe"));
        lore.add(enchLine(ench("Sharpness", sharpLvl), ench("Unbreaking", unbLvl)));
        lore.add(pieceLine(tc, tn, "Shovel"));
        lore.add(enchLine(ench("Efficiency", effLvl), ench("Unbreaking", unbLvl)));
        lore.add(pieceLine(tc, tn, "Hoe"));
        lore.add(enchLine(ench("Efficiency", effLvl), ench("Unbreaking", unbLvl)));
        lore.add(pieceLine(tc, tn, "Spear"));
        lore.add(enchLine(ench("Sharpness", sharpLvl), ench("Unbreaking", unbLvl)));
        return lore;
    }

    private List<Component> universeLore(TextColor tc, String tn) {
        List<Component> lore = new ArrayList<>();
        lore.add(gearHeader(tc, tn));
        lore.add(Component.empty());

        lore.add(pieceLine(tc, tn, "Helmet"));
        lore.add(enchLine(ench("Protection", "IV"), ench("Blast Protection", "IV"), ench("Fire Protection", "IV")));
        lore.add(enchLine(ench("Projectile Protection", "IV"), ench("Respiration", "III"), ench("Aqua Affinity", null)));
        lore.add(enchLine(ench("Thorns", "III"), ench("Unbreaking", "III"), ench("Mending", null)));

        lore.add(pieceLine(tc, tn, "Chestplate"));
        lore.add(enchLine(ench("Protection", "IV"), ench("Blast Protection", "IV"), ench("Fire Protection", "IV")));
        lore.add(enchLine(ench("Projectile Protection", "IV"), ench("Thorns", "III"), ench("Unbreaking", "III")));
        lore.add(enchLine(ench("Mending", null)));

        lore.add(pieceLine(tc, tn, "Leggings"));
        lore.add(enchLine(ench("Protection", "IV"), ench("Blast Protection", "IV"), ench("Fire Protection", "IV")));
        lore.add(enchLine(ench("Projectile Protection", "IV"), ench("Swift Sneak", "III"), ench("Thorns", "III")));
        lore.add(enchLine(ench("Unbreaking", "III"), ench("Mending", null)));

        lore.add(pieceLine(tc, tn, "Boots"));
        lore.add(enchLine(ench("Frost Walker", "II"), ench("Protection", "IV"), ench("Blast Protection", "IV")));
        lore.add(enchLine(ench("Fire Protection", "IV"), ench("Projectile Protection", "IV"), ench("Feather Falling", "IV")));
        lore.add(enchLine(ench("Soul Speed", "III"), ench("Depth Strider", "III"), ench("Thorns", "III")));
        lore.add(enchLine(ench("Unbreaking", "III"), ench("Mending", null)));

        lore.add(pieceLine(tc, tn, "Sword"));
        lore.add(enchLine(ench("Sharpness", "V"), ench("Smite", "V"), ench("Bane of Arthropods", "V")));
        lore.add(enchLine(ench("Sweeping Edge", "III"), ench("Fire Aspect", "II"), ench("Knockback", "II")));
        lore.add(enchLine(ench("Looting", "III"), ench("Unbreaking", "III"), ench("Mending", null)));

        lore.add(pieceLine(tc, tn, "Pickaxe"));
        lore.add(enchLine(ench("Fortune", "III"), ench("Silk Touch", null), ench("Efficiency", "V")));
        lore.add(enchLine(ench("Unbreaking", "III"), ench("Mending", null)));

        lore.add(pieceLine(tc, tn, "Axe"));
        lore.add(enchLine(ench("Silk Touch", null), ench("Efficiency", "V"), ench("Unbreaking", "III")));
        lore.add(enchLine(ench("Mending", null)));

        lore.add(pieceLine(tc, tn, "Shovel"));
        lore.add(enchLine(ench("Fortune", "III"), ench("Silk Touch", null), ench("Efficiency", "V")));
        lore.add(enchLine(ench("Unbreaking", "III"), ench("Mending", null)));

        lore.add(pieceLine(tc, tn, "Hoe"));
        lore.add(enchLine(ench("Fortune", "III"), ench("Silk Touch", null), ench("Efficiency", "V")));
        lore.add(enchLine(ench("Unbreaking", "III"), ench("Mending", null)));

        lore.add(pieceLine(tc, tn, "Spear"));
        lore.add(enchLine(ench("Unbreaking", "III"), ench("Mending", null), ench("Sharpness", "V")));
        lore.add(enchLine(ench("Smite", "V"), ench("Bane of Arthropods", "V"), ench("Looting", "III")));
        lore.add(enchLine(ench("Lunge", "III"), ench("Fire Aspect", "II"), ench("Knockback", "II")));

        return lore;
    }

    private Component gearHeader(TextColor gearColor, String gearName) {
        return Component.text("Right-Click", LORE_RED)
                .append(Component.text(" to receive the ", LORE_WHITE))
                .append(Component.text(gearName, gearColor))
                .append(Component.text(" ", LORE_WHITE))
                .append(Component.text("Gear", gearColor))
                .append(Component.text(" set:", LORE_WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    private Component pieceLine(TextColor gc, String material, String piece) {
        return Component.text(material, gc)
                .append(Component.text(" ", LORE_WHITE))
                .append(Component.text(piece, gc))
                .append(Component.text(":", LORE_WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    private Component ench(String name, String level) {
        Component c = Component.text(name, ENCHANT_BLUE);
        if (level != null) c = c.append(Component.text(" " + level, LORE_WHITE));
        return c;
    }

    private Component enchLine(Component... entries) {
        Component line = Component.text("  ", LORE_WHITE);
        for (int i = 0; i < entries.length; i++) {
            if (i > 0) line = line.append(Component.text(", ", LORE_WHITE));
            line = line.append(entries[i]);
        }
        return line.decoration(TextDecoration.ITALIC, false);
    }

    public void redeemGearTicket(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String tierName = meta.getPersistentDataContainer().getOrDefault(TAG_VOUCHER_TIER, PersistentDataType.STRING, "STAR");
        CrateType tier = CrateType.fromName(tierName);
        if (tier == null) tier = CrateType.STAR;

        List<ItemStack> gear = buildGearSet(tier);
        consumeOne(player, item);

        for (ItemStack piece : gear) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(piece);
            overflow.values().forEach(o -> player.getWorld().dropItemNaturally(player.getLocation(), o));
        }

        Msg.success(player, "You received a " + tier.getDisplayName() + " gear set!");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
    }

    private List<ItemStack> buildGearSet(CrateType tier) {
        List<ItemStack> gear = switch (tier) {
            case STAR -> goldGearSet();
            case COSMIC -> ironGearSet();
            case GALAXY -> diamondIIGearSet();
            case CELESTIAL -> diamondVGearSet();
            case UNIVERSE -> netheriteGodGearSet();
        };
        String tn = tier.getDisplayName().replace(" Crate", "");
        TextColor tc = tier.getColor();
        for (ItemStack piece : gear) {
            String raw = piece.getType().name();
            String pName = raw.substring(raw.indexOf('_') + 1);
            pName = pName.charAt(0) + pName.substring(1).toLowerCase();
            ItemMeta meta = piece.getItemMeta();
            meta.displayName(Component.text(tn + " " + pName, tc)
                    .decoration(TextDecoration.ITALIC, false));
            piece.setItemMeta(meta);
        }
        return gear;
    }

    private List<ItemStack> goldGearSet() {
        List<ItemStack> gear = new ArrayList<>();
        for (Material m : new Material[]{Material.GOLDEN_SWORD, Material.GOLDEN_PICKAXE, Material.GOLDEN_AXE,
                Material.GOLDEN_SHOVEL, Material.GOLDEN_HOE, Material.GOLDEN_HELMET,
                Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS}) {
            gear.add(enchanted(m, Map.of(Enchantment.UNBREAKING, 1)));
        }
        gear.add(enchanted(Material.GOLDEN_SPEAR, Map.of(Enchantment.UNBREAKING, 1)));
        return gear;
    }

    private List<ItemStack> ironGearSet() {
        List<ItemStack> gear = new ArrayList<>();
        for (Material m : new Material[]{Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE,
                Material.IRON_SHOVEL, Material.IRON_HOE, Material.IRON_HELMET,
                Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS}) {
            gear.add(enchanted(m, Map.of(Enchantment.UNBREAKING, 1)));
        }
        gear.add(enchanted(Material.IRON_SPEAR, Map.of(Enchantment.UNBREAKING, 1)));
        return gear;
    }

    private List<ItemStack> diamondIIGearSet() {
        List<ItemStack> gear = new ArrayList<>();
        gear.add(enchanted(Material.DIAMOND_SWORD, Map.of(Enchantment.UNBREAKING, 2, Enchantment.SHARPNESS, 2)));
        gear.add(enchanted(Material.DIAMOND_PICKAXE, Map.of(Enchantment.UNBREAKING, 2, Enchantment.EFFICIENCY, 2)));
        gear.add(enchanted(Material.DIAMOND_AXE, Map.of(Enchantment.UNBREAKING, 2, Enchantment.SHARPNESS, 2)));
        gear.add(enchanted(Material.DIAMOND_SHOVEL, Map.of(Enchantment.UNBREAKING, 2, Enchantment.EFFICIENCY, 2)));
        gear.add(enchanted(Material.DIAMOND_HOE, Map.of(Enchantment.UNBREAKING, 2, Enchantment.EFFICIENCY, 2)));
        gear.add(enchanted(Material.DIAMOND_SPEAR, Map.of(Enchantment.UNBREAKING, 2, Enchantment.SHARPNESS, 2)));
        for (Material m : new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}) {
            gear.add(enchanted(m, Map.of(Enchantment.UNBREAKING, 2, Enchantment.PROTECTION, 2)));
        }
        return gear;
    }

    private List<ItemStack> diamondVGearSet() {
        List<ItemStack> gear = new ArrayList<>();
        gear.add(enchanted(Material.DIAMOND_SWORD, Map.of(Enchantment.UNBREAKING, 3, Enchantment.SHARPNESS, 5)));
        gear.add(enchanted(Material.DIAMOND_PICKAXE, Map.of(Enchantment.UNBREAKING, 3, Enchantment.EFFICIENCY, 5)));
        gear.add(enchanted(Material.DIAMOND_AXE, Map.of(Enchantment.UNBREAKING, 3, Enchantment.SHARPNESS, 5)));
        gear.add(enchanted(Material.DIAMOND_SHOVEL, Map.of(Enchantment.UNBREAKING, 3, Enchantment.EFFICIENCY, 5)));
        gear.add(enchanted(Material.DIAMOND_HOE, Map.of(Enchantment.UNBREAKING, 3, Enchantment.EFFICIENCY, 5)));
        gear.add(enchanted(Material.DIAMOND_SPEAR, Map.of(Enchantment.UNBREAKING, 3, Enchantment.SHARPNESS, 5)));
        for (Material m : new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}) {
            gear.add(enchanted(m, Map.of(Enchantment.UNBREAKING, 3, Enchantment.PROTECTION, 4)));
        }
        return gear;
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> netheriteGodGearSet() {
        List<ItemStack> gear = new ArrayList<>();
        gear.add(enchanted(Material.NETHERITE_SWORD, Map.of(
                Enchantment.MENDING, 1, Enchantment.UNBREAKING, 3, Enchantment.SHARPNESS, 5,
                Enchantment.SMITE, 5, Enchantment.BANE_OF_ARTHROPODS, 5,
                Enchantment.SWEEPING_EDGE, 3, Enchantment.LOOTING, 3,
                Enchantment.FIRE_ASPECT, 2, Enchantment.KNOCKBACK, 2)));
        gear.add(enchanted(Material.NETHERITE_PICKAXE, Map.of(
                Enchantment.MENDING, 1, Enchantment.UNBREAKING, 3,
                Enchantment.FORTUNE, 3, Enchantment.SILK_TOUCH, 1, Enchantment.EFFICIENCY, 5)));
        gear.add(enchanted(Material.NETHERITE_AXE, Map.of(
                Enchantment.MENDING, 1, Enchantment.UNBREAKING, 3,
                Enchantment.EFFICIENCY, 5, Enchantment.SILK_TOUCH, 1)));
        gear.add(enchanted(Material.NETHERITE_SHOVEL, Map.of(
                Enchantment.MENDING, 1, Enchantment.UNBREAKING, 3,
                Enchantment.EFFICIENCY, 5, Enchantment.SILK_TOUCH, 1, Enchantment.FORTUNE, 3)));
        gear.add(enchanted(Material.NETHERITE_HOE, Map.of(
                Enchantment.MENDING, 1, Enchantment.UNBREAKING, 3,
                Enchantment.EFFICIENCY, 5, Enchantment.SILK_TOUCH, 1, Enchantment.FORTUNE, 3)));
        gear.add(enchanted(Material.NETHERITE_HELMET, Map.of(
                Enchantment.MENDING, 1, Enchantment.PROTECTION, 4,
                Enchantment.PROJECTILE_PROTECTION, 4, Enchantment.BLAST_PROTECTION, 4,
                Enchantment.FIRE_PROTECTION, 4, Enchantment.UNBREAKING, 3,
                Enchantment.RESPIRATION, 3, Enchantment.AQUA_AFFINITY, 1, Enchantment.THORNS, 3)));
        gear.add(enchanted(Material.NETHERITE_CHESTPLATE, Map.of(
                Enchantment.MENDING, 1, Enchantment.PROTECTION, 4,
                Enchantment.PROJECTILE_PROTECTION, 4, Enchantment.BLAST_PROTECTION, 4,
                Enchantment.FIRE_PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.THORNS, 3)));
        gear.add(enchanted(Material.NETHERITE_LEGGINGS, Map.of(
                Enchantment.MENDING, 1, Enchantment.PROTECTION, 4,
                Enchantment.PROJECTILE_PROTECTION, 4, Enchantment.BLAST_PROTECTION, 4,
                Enchantment.FIRE_PROTECTION, 4, Enchantment.UNBREAKING, 3,
                Enchantment.THORNS, 3, Enchantment.SWIFT_SNEAK, 3)));
        gear.add(enchanted(Material.NETHERITE_BOOTS, Map.ofEntries(
                Map.entry(Enchantment.MENDING, 1), Map.entry(Enchantment.PROTECTION, 4),
                Map.entry(Enchantment.PROJECTILE_PROTECTION, 4), Map.entry(Enchantment.BLAST_PROTECTION, 4),
                Map.entry(Enchantment.FIRE_PROTECTION, 4), Map.entry(Enchantment.FEATHER_FALLING, 4),
                Map.entry(Enchantment.UNBREAKING, 3), Map.entry(Enchantment.DEPTH_STRIDER, 3),
                Map.entry(Enchantment.FROST_WALKER, 2), Map.entry(Enchantment.SOUL_SPEED, 3),
                Map.entry(Enchantment.THORNS, 3))));
        gear.add(enchanted(Material.NETHERITE_SPEAR, Map.of(
                Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.SHARPNESS, 5,
                Enchantment.SMITE, 5, Enchantment.BANE_OF_ARTHROPODS, 5,
                Enchantment.LOOTING, 3, Enchantment.LUNGE, 3,
                Enchantment.FIRE_ASPECT, 2, Enchantment.KNOCKBACK, 2)));
        return gear;
    }

    private ItemStack enchanted(Material material, Map<Enchantment, Integer> enchants) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        enchants.forEach((e, lvl) -> meta.addEnchant(e, lvl, true));
        item.setItemMeta(meta);
        return item;
    }

    // ── Enchant Protection Scroll ──

    private static final NamespacedKey ENCHANT_PROTECTED_KEY = NamespacedKey.fromString("starlightuniverse:enchant_protected");

    public ItemStack createEnchantProtectionScroll() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Enchant Protection Scroll", CYAN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Right-click while holding an item", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("in your off-hand to protect it.", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Protected items won't lose durability", YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("on enchantment failure. (One-time use)", YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.setItemModel(NamespacedKey.fromString("starlight:enchant_protection_scroll"));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(TAG_VOUCHER_TYPE, PersistentDataType.STRING, "ENCHANT_PROTECTION_SCROLL");
        item.setItemMeta(meta);
        return item;
    }

    public void redeemEnchantProtectionScroll(Player player, ItemStack scroll) {
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType() == Material.AIR || offHand.getType().getMaxDurability() <= 0) {
            Msg.error(player, "Hold the item you want to protect in your off-hand!");
            return;
        }

        if (offHand.hasItemMeta()) {
            byte val = offHand.getItemMeta().getPersistentDataContainer()
                    .getOrDefault(ENCHANT_PROTECTED_KEY, PersistentDataType.BYTE, (byte) 0);
            if (val == 1) {
                Msg.error(player, "That item is already protected!");
                return;
            }
        }

        ItemMeta meta = offHand.getItemMeta();
        meta.getPersistentDataContainer().set(ENCHANT_PROTECTED_KEY, PersistentDataType.BYTE, (byte) 1);

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();
        lore.add(Component.text("✦ Enchant Protected", CYAN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        offHand.setItemMeta(meta);

        consumeOne(player, scroll);
        Msg.success(player, "Your item is now protected from enchant failure damage!");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);
    }

    // ── Enchant Remover Scroll ──

    public ItemStack createEnchantRemoverScroll() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Enchant Remover Scroll", PURPLE)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Right-click to use", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Removes one enchantment of your", YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("choice from any item. (One-time use)", YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.setItemModel(NamespacedKey.fromString("starlight:enchant_remover_scroll"));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(TAG_VOUCHER_TYPE, PersistentDataType.STRING, "ENCHANT_REMOVER_SCROLL");
        item.setItemMeta(meta);
        return item;
    }

    // ── Voucher detection ──

    public String getVoucherType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(TAG_VOUCHER_TYPE, PersistentDataType.STRING, null);
    }

    public void handleRightClick(Player player, ItemStack item) {
        if (petManager != null && petManager.isPetScroll(item)) {
            petManager.openScrollPetMenu(player);
            return;
        }

        if (trailManager != null && trailManager.isTrailScroll(item)) {
            trailManager.openScrollTrailMenu(player);
            return;
        }

        if (disguiseManager != null && disguiseManager.isDisguiseScroll(item)) {
            disguiseManager.openScrollDisguiseMenu(player);
            return;
        }

        String type = getVoucherType(item);
        if (type == null) return;
        switch (type) {
            case "FLY" -> redeemFlyVoucher(player, item);
            case "BOOSTER" -> redeemBooster(player, item);
            case "PROTECTION" -> redeemProtectionToken(player, item);
            case "GEAR_TICKET" -> redeemGearTicket(player, item);
            case "ENCHANT_PROTECTION_SCROLL" -> redeemEnchantProtectionScroll(player, item);
            case "ENCHANT_REMOVER_SCROLL" -> {
                if (enchantRemoverListener != null) enchantRemoverListener.startSelection(player);
            }
        }
    }

    private void consumeOne(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    public void onPlayerQuit(UUID uuid) {
        flyActive.remove(uuid);
    }
}
