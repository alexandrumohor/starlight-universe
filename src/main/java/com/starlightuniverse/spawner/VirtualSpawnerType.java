package com.starlightuniverse.spawner;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

public enum VirtualSpawnerType {

    // NOTE: All spawner prices are currently ★1000 (placeholder). Adjust per mob later
    // once we see how fast each is farmed.

    // ─── Passive / Farm animals ───
    COW(EntityType.COW, "Cow", Material.LEATHER,
            Currency.STARS, 1000, 3,
            List.of(
                    new Drop(Material.BEEF, 1, 3, 100),
                    new Drop(Material.LEATHER, 0, 2, 80)
            )),
    PIG(EntityType.PIG, "Pig", Material.PORKCHOP,
            Currency.STARS, 1000, 3,
            List.of(
                    new Drop(Material.PORKCHOP, 1, 3, 100)
            )),
    CHICKEN(EntityType.CHICKEN, "Chicken", Material.FEATHER,
            Currency.STARS, 1000, 3,
            List.of(
                    new Drop(Material.CHICKEN, 1, 1, 100),
                    new Drop(Material.FEATHER, 0, 2, 60),
                    new Drop(Material.EGG, 1, 1, 5)
            )),
    SHEEP(EntityType.SHEEP, "Sheep", Material.WHITE_WOOL,
            Currency.STARS, 1000, 3,
            List.of(
                    new Drop(Material.MUTTON, 1, 2, 100),
                    new Drop(Material.WHITE_WOOL, 1, 1, 100)
            )),
    RABBIT(EntityType.RABBIT, "Rabbit", Material.RABBIT_HIDE,
            Currency.STARS, 1000, 3,
            List.of(
                    new Drop(Material.RABBIT, 1, 1, 100),
                    new Drop(Material.RABBIT_HIDE, 0, 1, 80),
                    new Drop(Material.RABBIT_FOOT, 1, 1, 5)
            )),
    COD(EntityType.COD, "Cod", Material.COD,
            Currency.STARS, 1000, 2,
            List.of(
                    new Drop(Material.COD, 1, 1, 100)
            )),
    SALMON(EntityType.SALMON, "Salmon", Material.SALMON,
            Currency.STARS, 1000, 2,
            List.of(
                    new Drop(Material.SALMON, 1, 1, 100)
            )),
    SQUID(EntityType.SQUID, "Squid", Material.INK_SAC,
            Currency.STARS, 1000, 3,
            List.of(
                    new Drop(Material.INK_SAC, 1, 3, 100)
            )),
    TURTLE(EntityType.TURTLE, "Turtle", Material.TURTLE_SCUTE,
            Currency.STARS, 1000, 5,
            List.of(
                    new Drop(Material.TURTLE_SCUTE, 0, 1, 25),
                    new Drop(Material.SEAGRASS, 1, 2, 40)
            )),

    // ─── Common hostile ───
    ZOMBIE(EntityType.ZOMBIE, "Zombie", Material.ROTTEN_FLESH,
            Currency.STARS, 1000, 5,
            List.of(
                    new Drop(Material.ROTTEN_FLESH, 1, 3, 100),
                    new Drop(Material.IRON_INGOT, 1, 1, 5),
                    new Drop(Material.CARROT, 1, 1, 3),
                    new Drop(Material.POTATO, 1, 1, 3)
            )),
    SKELETON(EntityType.SKELETON, "Skeleton", Material.BONE,
            Currency.STARS, 1000, 5,
            List.of(
                    new Drop(Material.BONE, 1, 2, 100),
                    new Drop(Material.ARROW, 0, 2, 60)
            )),
    SPIDER(EntityType.SPIDER, "Spider", Material.STRING,
            Currency.STARS, 1000, 5,
            List.of(
                    new Drop(Material.STRING, 1, 2, 100),
                    new Drop(Material.SPIDER_EYE, 0, 1, 30)
            )),
    CAVE_SPIDER(EntityType.CAVE_SPIDER, "Cave Spider", Material.SPIDER_EYE,
            Currency.STARS, 1000, 5,
            List.of(
                    new Drop(Material.STRING, 1, 2, 100),
                    new Drop(Material.SPIDER_EYE, 1, 1, 40)
            )),
    HUSK(EntityType.HUSK, "Husk", Material.SAND,
            Currency.STARS, 1000, 5,
            List.of(
                    new Drop(Material.ROTTEN_FLESH, 1, 3, 100)
            )),
    STRAY(EntityType.STRAY, "Stray", Material.SNOWBALL,
            Currency.STARS, 1000, 5,
            List.of(
                    new Drop(Material.BONE, 1, 2, 100),
                    new Drop(Material.ARROW, 0, 2, 60)
            )),
    DROWNED(EntityType.DROWNED, "Drowned", Material.TRIDENT,
            Currency.STARS, 1000, 5,
            List.of(
                    new Drop(Material.ROTTEN_FLESH, 1, 2, 100),
                    new Drop(Material.COPPER_INGOT, 0, 1, 10),
                    new Drop(Material.NAUTILUS_SHELL, 1, 1, 3)
            )),
    ZOMBIFIED_PIGLIN(EntityType.ZOMBIFIED_PIGLIN, "Zombified Piglin", Material.GOLD_INGOT,
            Currency.STARS, 1000, 5,
            List.of(
                    new Drop(Material.ROTTEN_FLESH, 1, 2, 100),
                    new Drop(Material.GOLD_NUGGET, 1, 2, 100),
                    new Drop(Material.GOLD_INGOT, 1, 1, 5)
            )),

    // ─── Uncommon ───
    CREEPER(EntityType.CREEPER, "Creeper", Material.GUNPOWDER,
            Currency.STARS, 1000, 5,
            List.of(
                    new Drop(Material.GUNPOWDER, 1, 2, 100)
            )),
    SLIME(EntityType.SLIME, "Slime", Material.SLIME_BALL,
            Currency.STARS, 1000, 5,
            List.of(
                    new Drop(Material.SLIME_BALL, 1, 2, 100)
            )),
    MAGMA_CUBE(EntityType.MAGMA_CUBE, "Magma Cube", Material.MAGMA_CREAM,
            Currency.STARS, 1000, 5,
            List.of(
                    new Drop(Material.MAGMA_CREAM, 1, 1, 100)
            )),
    SNOW_GOLEM(EntityType.SNOW_GOLEM, "Snow Golem", Material.CARVED_PUMPKIN,
            Currency.STARS, 1000, 3,
            List.of(
                    new Drop(Material.SNOWBALL, 1, 3, 100)
            )),
    IRON_GOLEM(EntityType.IRON_GOLEM, "Iron Golem", Material.IRON_BLOCK,
            Currency.STARS, 1000, 10,
            List.of(
                    new Drop(Material.IRON_INGOT, 1, 2, 100),
                    new Drop(Material.POPPY, 0, 1, 40)
            )),
    GLOW_SQUID(EntityType.GLOW_SQUID, "Glow Squid", Material.GLOW_INK_SAC,
            Currency.STARS, 1000, 5,
            List.of(
                    new Drop(Material.GLOW_INK_SAC, 1, 3, 100)
            )),

    // ─── Rare ───
    ENDERMAN(EntityType.ENDERMAN, "Enderman", Material.ENDER_PEARL,
            Currency.STARS, 1000, 8,
            List.of(
                    new Drop(Material.ENDER_PEARL, 1, 1, 80)
            )),
    PIGLIN(EntityType.PIGLIN, "Piglin", Material.GOLD_NUGGET,
            Currency.STARS, 1000, 8,
            List.of(
                    new Drop(Material.GOLD_NUGGET, 1, 2, 100),
                    new Drop(Material.GOLD_INGOT, 0, 1, 10)
            )),
    HOGLIN(EntityType.HOGLIN, "Hoglin", Material.PORKCHOP,
            Currency.STARS, 1000, 8,
            List.of(
                    new Drop(Material.PORKCHOP, 1, 3, 100),
                    new Drop(Material.LEATHER, 0, 1, 30)
            )),
    WITCH(EntityType.WITCH, "Witch", Material.GLASS_BOTTLE,
            Currency.STARS, 1000, 10,
            List.of(
                    new Drop(Material.REDSTONE, 0, 2, 50),
                    new Drop(Material.GLOWSTONE_DUST, 0, 2, 50),
                    new Drop(Material.SUGAR, 0, 2, 50),
                    new Drop(Material.GLASS_BOTTLE, 0, 2, 40),
                    new Drop(Material.GUNPOWDER, 0, 1, 30),
                    new Drop(Material.SPIDER_EYE, 0, 1, 20)
            )),
    PHANTOM(EntityType.PHANTOM, "Phantom", Material.PHANTOM_MEMBRANE,
            Currency.STARS, 1000, 8,
            List.of(
                    new Drop(Material.PHANTOM_MEMBRANE, 0, 1, 60)
            )),
    PILLAGER(EntityType.PILLAGER, "Pillager", Material.CROSSBOW,
            Currency.STARS, 1000, 8,
            List.of(
                    new Drop(Material.ARROW, 0, 2, 60),
                    new Drop(Material.EMERALD, 0, 1, 8)
            )),
    VINDICATOR(EntityType.VINDICATOR, "Vindicator", Material.IRON_AXE,
            Currency.STARS, 1000, 10,
            List.of(
                    new Drop(Material.EMERALD, 0, 1, 20)
            )),
    GUARDIAN(EntityType.GUARDIAN, "Guardian", Material.PRISMARINE_SHARD,
            Currency.STARS, 1000, 8,
            List.of(
                    new Drop(Material.PRISMARINE_SHARD, 0, 2, 60),
                    new Drop(Material.PRISMARINE_CRYSTALS, 0, 1, 40),
                    new Drop(Material.COD, 0, 1, 20)
            )),
    GHAST(EntityType.GHAST, "Ghast", Material.GHAST_TEAR,
            Currency.STARS, 1000, 10,
            List.of(
                    new Drop(Material.GUNPOWDER, 0, 2, 60),
                    new Drop(Material.GHAST_TEAR, 0, 1, 30)
            )),

    // ─── Very rare ───
    BLAZE(EntityType.BLAZE, "Blaze", Material.BLAZE_ROD,
            Currency.STARS, 1000, 10,
            List.of(
                    new Drop(Material.BLAZE_ROD, 1, 1, 100)
            )),
    WITHER_SKELETON(EntityType.WITHER_SKELETON, "Wither Skeleton", Material.WITHER_SKELETON_SKULL,
            Currency.STARS, 1000, 20,
            List.of(
                    new Drop(Material.COAL, 1, 1, 100),
                    new Drop(Material.BONE, 1, 1, 50),
                    new Drop(Material.WITHER_SKELETON_SKULL, 1, 1, 3)
            )),
    SHULKER(EntityType.SHULKER, "Shulker", Material.SHULKER_SHELL,
            Currency.STARS, 1000, 20,
            List.of(
                    new Drop(Material.SHULKER_SHELL, 0, 1, 50)
            ));

    public enum Currency { MONEY, GEMS, STARS }

    public record Drop(Material material, int minAmount, int maxAmount, int chancePercent) {}

    // Tier -> seconds between spawns (per stack unit)
    public static final int[] TIER_SECONDS = {40, 25, 15};
    // Tier -> money cost to upgrade FROM this tier to the next (index 0 = 1->2, index 1 = 2->3)
    public static final double[] TIER_UPGRADE_MONEY = {5_000, 25_000};
    // Max stack per placed spawner block
    public static final int MAX_STACK = 64;
    // Max storage per material (safety cap)
    public static final int MAX_STORAGE_PER_MATERIAL = 4096;
    // Max XP a spawner can hold
    public static final int MAX_STORED_XP = 100_000;

    private final EntityType entityType;
    private final String displayName;
    private final Material icon;
    private final Currency currency;
    private final double shopPrice;
    private final int xpPerSpawn;
    private final List<Drop> drops;

    VirtualSpawnerType(EntityType entityType, String displayName, Material icon,
                       Currency currency, double shopPrice, int xpPerSpawn, List<Drop> drops) {
        this.entityType = entityType;
        this.displayName = displayName;
        this.icon = icon;
        this.currency = currency;
        this.shopPrice = shopPrice;
        this.xpPerSpawn = xpPerSpawn;
        this.drops = drops;
    }

    public EntityType getEntityType() { return entityType; }
    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public Currency getCurrency() { return currency; }
    public double getShopPrice() { return shopPrice; }
    public int getXpPerSpawn() { return xpPerSpawn; }
    public List<Drop> getDrops() { return drops; }

    public static VirtualSpawnerType fromName(String name) {
        if (name == null) return null;
        try { return VirtualSpawnerType.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    public static VirtualSpawnerType fromEntityType(EntityType entityType) {
        for (VirtualSpawnerType t : values()) {
            if (t.entityType == entityType) return t;
        }
        return null;
    }

    // Sell price per unit (server sell price when clicking "Sell All")
    public static double sellPrice(Material material) {
        return switch (material) {
            // Farm animal drops
            case BEEF, PORKCHOP, CHICKEN, MUTTON, SALMON -> 3;
            case COD -> 2;
            case LEATHER -> 5;
            case FEATHER -> 2;
            case EGG -> 3;
            case WHITE_WOOL -> 2;
            case RABBIT, RABBIT_HIDE -> 5;
            case RABBIT_FOOT -> 100;
            case INK_SAC -> 3;
            case GLOW_INK_SAC -> 15;
            case TURTLE_SCUTE -> 200;
            case SEAGRASS -> 1;
            // Zombie / skeleton drops
            case ROTTEN_FLESH -> 1;
            case IRON_INGOT -> 20;
            case CARROT, POTATO -> 2;
            case BONE -> 3;
            case ARROW -> 2;
            case STRING -> 2;
            case SPIDER_EYE -> 5;
            case SAND, SNOWBALL -> 1;
            // Water / nether extras
            case TRIDENT -> 500;
            case COPPER_INGOT -> 5;
            case NAUTILUS_SHELL -> 200;
            case GOLD_NUGGET -> 3;
            case GOLD_INGOT -> 30;
            case SLIME_BALL -> 5;
            case MAGMA_CREAM -> 10;
            case IRON_BLOCK -> 200;
            case POPPY -> 1;
            case CARVED_PUMPKIN -> 10;
            // Gems tier
            case GUNPOWDER -> 5;
            case ENDER_PEARL -> 30;
            case REDSTONE -> 3;
            case GLOWSTONE_DUST -> 5;
            case SUGAR -> 2;
            case GLASS_BOTTLE -> 2;
            case PHANTOM_MEMBRANE -> 100;
            case CROSSBOW -> 30;
            case IRON_AXE -> 20;
            case EMERALD -> 15;
            case PRISMARINE_SHARD -> 5;
            case PRISMARINE_CRYSTALS -> 10;
            case GHAST_TEAR -> 100;
            // Stars tier
            case BLAZE_ROD -> 50;
            case COAL -> 3;
            case WITHER_SKELETON_SKULL -> 500;
            case SHULKER_SHELL -> 300;
            default -> 0;
        };
    }
}
