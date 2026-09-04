package com.starlightuniverse.shop;

import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.util.*;

import static com.starlightuniverse.shop.ShopCategory.*;

public class ShopManager {

    static final int BULK_THRESHOLD = 320;
    static final double BULK_DISCOUNT = 0.10;
    static final int ITEMS_PER_PAGE = 45;
    static final int MAX_QUANTITY = 2304;

    // Buy screen slot layout (27-slot inventory)
    // Row 1: item icon at slot 4
    // Row 2: quantity controls  -1 -10 -64  [gap]  +1 +10 +64
    static final int BUY_ICON_SLOT = 4;
    static final int BUY_MINUS_1 = 10;
    static final int BUY_MINUS_10 = 11;
    static final int BUY_MINUS_64 = 12;
    static final int BUY_PLUS_1 = 14;
    static final int BUY_PLUS_10 = 15;
    static final int BUY_PLUS_64 = 16;
    static final int BUY_ACCEPT = 20;
    static final int BUY_CANCEL = 24;

    private static final int[] DEAL_SLOTS = {2, 4, 6};
    private static final int[] CAT_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 30, 32, 34};

    private static final List<ShopItem> ALL_ITEMS = new ArrayList<>();
    private static final Map<ShopCategory, List<ShopItem>> BY_CATEGORY = new EnumMap<>(ShopCategory.class);

    static {
        registerItems();
        for (ShopItem item : ALL_ITEMS) {
            BY_CATEGORY.computeIfAbsent(item.category(), k -> new ArrayList<>()).add(item);
        }
    }

    private static void registerItems() {
        // ==================== STONE BLOCKS ====================
        int stoneStair = 150, stoneSlab = 50, stoneWall = 100, stoneBase = 100;
        String[][] stoneFamilies = {
                {"STONE"}, {"COBBLESTONE"}, {"MOSSY_COBBLESTONE"},
                {"STONE_BRICKS"}, {"MOSSY_STONE_BRICKS"},
                {"GRANITE"}, {"POLISHED_GRANITE"}, {"DIORITE"}, {"POLISHED_DIORITE"},
                {"ANDESITE"}, {"POLISHED_ANDESITE"},
                {"COBBLED_DEEPSLATE"}, {"POLISHED_DEEPSLATE"}, {"DEEPSLATE_BRICKS"}, {"DEEPSLATE_TILES"},
                {"TUFF"}, {"POLISHED_TUFF"}, {"TUFF_BRICKS"},
                {"BRICK"}, {"MUD_BRICK"}, {"RESIN_BRICK"},
                {"SANDSTONE"}, {"RED_SANDSTONE"},
                {"PRISMARINE"}, {"PRISMARINE_BRICK"},
                {"NETHER_BRICK"}, {"RED_NETHER_BRICK"},
                {"BLACKSTONE"}, {"POLISHED_BLACKSTONE"}, {"POLISHED_BLACKSTONE_BRICK"},
                {"END_STONE_BRICK"}, {"PURPUR"},
                {"QUARTZ"}, {"SMOOTH_QUARTZ"}
        };

        addSafe("STONE", stoneBase, STONE_BLOCKS);
        addSafe("STONE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("STONE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("COBBLESTONE", stoneBase, STONE_BLOCKS);
        addSafe("COBBLESTONE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("COBBLESTONE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("COBBLESTONE_WALL", stoneWall, STONE_BLOCKS);
        addSafe("MOSSY_COBBLESTONE", stoneBase, STONE_BLOCKS);
        addSafe("MOSSY_COBBLESTONE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("MOSSY_COBBLESTONE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("MOSSY_COBBLESTONE_WALL", stoneWall, STONE_BLOCKS);
        addSafe("SMOOTH_STONE", stoneBase, STONE_BLOCKS);
        addSafe("SMOOTH_STONE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("STONE_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("CRACKED_STONE_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("STONE_BRICK_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("STONE_BRICK_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("STONE_BRICK_WALL", stoneWall, STONE_BLOCKS);
        addSafe("CHISELED_STONE_BRICKS", 200, STONE_BLOCKS);
        addSafe("MOSSY_STONE_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("MOSSY_STONE_BRICK_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("MOSSY_STONE_BRICK_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("MOSSY_STONE_BRICK_WALL", stoneWall, STONE_BLOCKS);
        addSafe("GRANITE", stoneBase, STONE_BLOCKS);
        addSafe("GRANITE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("GRANITE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("GRANITE_WALL", stoneWall, STONE_BLOCKS);
        addSafe("POLISHED_GRANITE", stoneBase, STONE_BLOCKS);
        addSafe("POLISHED_GRANITE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("POLISHED_GRANITE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("DIORITE", stoneBase, STONE_BLOCKS);
        addSafe("DIORITE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("DIORITE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("DIORITE_WALL", stoneWall, STONE_BLOCKS);
        addSafe("POLISHED_DIORITE", stoneBase, STONE_BLOCKS);
        addSafe("POLISHED_DIORITE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("POLISHED_DIORITE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("ANDESITE", stoneBase, STONE_BLOCKS);
        addSafe("ANDESITE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("ANDESITE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("ANDESITE_WALL", stoneWall, STONE_BLOCKS);
        addSafe("POLISHED_ANDESITE", stoneBase, STONE_BLOCKS);
        addSafe("POLISHED_ANDESITE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("POLISHED_ANDESITE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("DEEPSLATE", stoneBase, STONE_BLOCKS);
        addSafe("COBBLED_DEEPSLATE", stoneBase, STONE_BLOCKS);
        addSafe("COBBLED_DEEPSLATE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("COBBLED_DEEPSLATE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("COBBLED_DEEPSLATE_WALL", stoneWall, STONE_BLOCKS);
        addSafe("CHISELED_DEEPSLATE", stoneBase, STONE_BLOCKS);
        addSafe("POLISHED_DEEPSLATE", stoneBase, STONE_BLOCKS);
        addSafe("POLISHED_DEEPSLATE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("POLISHED_DEEPSLATE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("POLISHED_DEEPSLATE_WALL", stoneWall, STONE_BLOCKS);
        addSafe("DEEPSLATE_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("CRACKED_DEEPSLATE_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("DEEPSLATE_BRICK_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("DEEPSLATE_BRICK_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("DEEPSLATE_BRICK_WALL", stoneWall, STONE_BLOCKS);
        addSafe("DEEPSLATE_TILES", stoneBase, STONE_BLOCKS);
        addSafe("CRACKED_DEEPSLATE_TILES", stoneBase, STONE_BLOCKS);
        addSafe("DEEPSLATE_TILE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("DEEPSLATE_TILE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("DEEPSLATE_TILE_WALL", stoneWall, STONE_BLOCKS);
        addSafe("TUFF", stoneBase, STONE_BLOCKS);
        addSafe("TUFF_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("TUFF_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("TUFF_WALL", stoneWall, STONE_BLOCKS);
        addSafe("CHISELED_TUFF", stoneBase, STONE_BLOCKS);
        addSafe("POLISHED_TUFF", stoneBase, STONE_BLOCKS);
        addSafe("POLISHED_TUFF_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("POLISHED_TUFF_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("POLISHED_TUFF_WALL", stoneWall, STONE_BLOCKS);
        addSafe("TUFF_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("TUFF_BRICK_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("TUFF_BRICK_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("TUFF_BRICK_WALL", stoneWall, STONE_BLOCKS);
        addSafe("CHISELED_TUFF_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("BRICK_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("BRICK_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("BRICK_WALL", stoneWall, STONE_BLOCKS);
        addSafe("PACKED_MUD", stoneBase, STONE_BLOCKS);
        addSafe("MUD_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("MUD_BRICK_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("MUD_BRICK_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("MUD_BRICK_WALL", stoneWall, STONE_BLOCKS);
        addSafe("RESIN_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("RESIN_BRICK_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("RESIN_BRICK_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("RESIN_BRICK_WALL", stoneWall, STONE_BLOCKS);
        addSafe("CHISELED_RESIN_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("SANDSTONE", stoneBase, STONE_BLOCKS);
        addSafe("SANDSTONE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("SANDSTONE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("SANDSTONE_WALL", stoneWall, STONE_BLOCKS);
        addSafe("CHISELED_SANDSTONE", stoneBase, STONE_BLOCKS);
        addSafe("SMOOTH_SANDSTONE", stoneBase, STONE_BLOCKS);
        addSafe("SMOOTH_SANDSTONE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("SMOOTH_SANDSTONE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("CUT_SANDSTONE", stoneBase, STONE_BLOCKS);
        addSafe("CUT_SANDSTONE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("RED_SANDSTONE", stoneBase, STONE_BLOCKS);
        addSafe("RED_SANDSTONE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("RED_SANDSTONE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("RED_SANDSTONE_WALL", stoneWall, STONE_BLOCKS);
        addSafe("CHISELED_RED_SANDSTONE", stoneBase, STONE_BLOCKS);
        addSafe("SMOOTH_RED_SANDSTONE", stoneBase, STONE_BLOCKS);
        addSafe("SMOOTH_RED_SANDSTONE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("SMOOTH_RED_SANDSTONE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("CUT_RED_SANDSTONE", stoneBase, STONE_BLOCKS);
        addSafe("CUT_RED_SANDSTONE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("SEA_LANTERN", stoneBase, STONE_BLOCKS);
        addSafe("PRISMARINE", stoneBase, STONE_BLOCKS);
        addSafe("PRISMARINE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("PRISMARINE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("PRISMARINE_WALL", stoneWall, STONE_BLOCKS);
        addSafe("PRISMARINE_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("PRISMARINE_BRICK_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("PRISMARINE_BRICK_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("DARK_PRISMARINE", stoneBase, STONE_BLOCKS);
        addSafe("DARK_PRISMARINE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("DARK_PRISMARINE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("NETHERRACK", stoneBase, STONE_BLOCKS);
        addSafe("NETHER_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("CRACKED_NETHER_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("NETHER_BRICK_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("NETHER_BRICK_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("NETHER_BRICK_WALL", stoneWall, STONE_BLOCKS);
        addSafe("CHISELED_NETHER_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("RED_NETHER_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("RED_NETHER_BRICK_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("RED_NETHER_BRICK_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("RED_NETHER_BRICK_WALL", stoneWall, STONE_BLOCKS);
        addSafe("BASALT", stoneBase, STONE_BLOCKS);
        addSafe("SMOOTH_BASALT", stoneBase, STONE_BLOCKS);
        addSafe("POLISHED_BASALT", stoneBase, STONE_BLOCKS);
        addSafe("BLACKSTONE", stoneBase, STONE_BLOCKS);
        addSafe("BLACKSTONE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("BLACKSTONE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("BLACKSTONE_WALL", stoneWall, STONE_BLOCKS);
        addSafe("CHISELED_POLISHED_BLACKSTONE", stoneBase, STONE_BLOCKS);
        addSafe("POLISHED_BLACKSTONE", stoneBase, STONE_BLOCKS);
        addSafe("POLISHED_BLACKSTONE_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("POLISHED_BLACKSTONE_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("POLISHED_BLACKSTONE_WALL", stoneWall, STONE_BLOCKS);
        addSafe("POLISHED_BLACKSTONE_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("CRACKED_POLISHED_BLACKSTONE_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("POLISHED_BLACKSTONE_BRICK_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("POLISHED_BLACKSTONE_BRICK_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("POLISHED_BLACKSTONE_BRICK_WALL", stoneWall, STONE_BLOCKS);
        addSafe("END_STONE", stoneBase, STONE_BLOCKS);
        addSafe("END_STONE_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("END_STONE_BRICK_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("END_STONE_BRICK_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("END_STONE_BRICK_WALL", stoneWall, STONE_BLOCKS);
        addSafe("PURPUR_BLOCK", stoneBase, STONE_BLOCKS);
        addSafe("PURPUR_PILLAR", stoneBase, STONE_BLOCKS);
        addSafe("PURPUR_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("PURPUR_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("QUARTZ_BLOCK", stoneBase, STONE_BLOCKS);
        addSafe("QUARTZ_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("QUARTZ_SLAB", stoneSlab, STONE_BLOCKS);
        addSafe("CHISELED_QUARTZ_BLOCK", stoneBase, STONE_BLOCKS);
        addSafe("QUARTZ_BRICKS", stoneBase, STONE_BLOCKS);
        addSafe("QUARTZ_PILLAR", stoneBase, STONE_BLOCKS);
        addSafe("SMOOTH_QUARTZ", stoneBase, STONE_BLOCKS);
        addSafe("SMOOTH_QUARTZ_STAIRS", stoneStair, STONE_BLOCKS);
        addSafe("SMOOTH_QUARTZ_SLAB", stoneSlab, STONE_BLOCKS);

        // ==================== WOOD BLOCKS ====================
        String[] woods = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK",
                "MANGROVE", "CHERRY", "PALE_OAK", "CRIMSON", "WARPED"};
        for (String w : woods) {
            String logSuffix = (w.equals("CRIMSON") || w.equals("WARPED")) ? "_STEM" : "_LOG";
            String woodSuffix = (w.equals("CRIMSON") || w.equals("WARPED")) ? "_HYPHAE" : "_WOOD";
            addSafe(w + logSuffix, 100, WOOD_BLOCKS);
            addSafe(w + woodSuffix, 130, WOOD_BLOCKS);
            addSafe("STRIPPED_" + w + logSuffix, 100, WOOD_BLOCKS);
            addSafe("STRIPPED_" + w + woodSuffix, 130, WOOD_BLOCKS);
            addSafe(w + "_PLANKS", 25, WOOD_BLOCKS);
            addSafe(w + "_STAIRS", 35, WOOD_BLOCKS);
            addSafe(w + "_SLAB", 15, WOOD_BLOCKS);
        }
        addSafe("BAMBOO_BLOCK", 100, WOOD_BLOCKS);
        addSafe("STRIPPED_BAMBOO_BLOCK", 100, WOOD_BLOCKS);
        addSafe("BAMBOO_PLANKS", 25, WOOD_BLOCKS);
        addSafe("BAMBOO_MOSAIC", 25, WOOD_BLOCKS);
        addSafe("BAMBOO_STAIRS", 35, WOOD_BLOCKS);
        addSafe("BAMBOO_MOSAIC_STAIRS", 35, WOOD_BLOCKS);
        addSafe("BAMBOO_SLAB", 15, WOOD_BLOCKS);

        // ==================== NATURAL BLOCKS ====================
        addSafe("GRASS_BLOCK", 100, NATURAL_BLOCKS);
        addSafe("PODZOL", 100, NATURAL_BLOCKS);
        addSafe("MYCELIUM", 100, NATURAL_BLOCKS);
        addSafe("DIRT", 100, NATURAL_BLOCKS);
        addSafe("COARSE_DIRT", 100, NATURAL_BLOCKS);
        addSafe("ROOTED_DIRT", 100, NATURAL_BLOCKS);
        addSafe("MUD", 100, NATURAL_BLOCKS);
        addSafe("CLAY", 100, NATURAL_BLOCKS);
        addSafe("GRAVEL", 100, NATURAL_BLOCKS);
        addSafe("SAND", 100, NATURAL_BLOCKS);
        addSafe("RED_SAND", 100, NATURAL_BLOCKS);
        addSafe("ICE", 100, NATURAL_BLOCKS);
        addSafe("PACKED_ICE", 100, NATURAL_BLOCKS);
        addSafe("BLUE_ICE", 100, NATURAL_BLOCKS);
        addSafe("SNOW_BLOCK", 100, NATURAL_BLOCKS);
        addSafe("MOSS_BLOCK", 100, NATURAL_BLOCKS);
        addSafe("PALE_MOSS_BLOCK", 100, NATURAL_BLOCKS);
        addSafe("OBSIDIAN", 100, NATURAL_BLOCKS);
        addSafe("CRYING_OBSIDIAN", 100, NATURAL_BLOCKS);
        addSafe("SOUL_SAND", 100, NATURAL_BLOCKS);
        addSafe("SOUL_SOIL", 100, NATURAL_BLOCKS);
        String[] leaves = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK",
                "MANGROVE", "CHERRY", "PALE_OAK"};
        for (String l : leaves) addSafe(l + "_LEAVES", 100, NATURAL_BLOCKS);
        addSafe("AZALEA_LEAVES", 100, NATURAL_BLOCKS);
        addSafe("FLOWERING_AZALEA_LEAVES", 100, NATURAL_BLOCKS);
        addSafe("BROWN_MUSHROOM_BLOCK", 100, NATURAL_BLOCKS);
        addSafe("RED_MUSHROOM_BLOCK", 100, NATURAL_BLOCKS);
        addSafe("NETHER_WART_BLOCK", 100, NATURAL_BLOCKS);
        addSafe("WARPED_WART_BLOCK", 100, NATURAL_BLOCKS);
        addSafe("SHROOMLIGHT", 100, NATURAL_BLOCKS);
        addSafe("OCHRE_FROGLIGHT", 100, NATURAL_BLOCKS);
        addSafe("VERDANT_FROGLIGHT", 100, NATURAL_BLOCKS);
        addSafe("PEARLESCENT_FROGLIGHT", 100, NATURAL_BLOCKS);

        // ==================== MINERALS ====================
        addSafe("COAL", 200, MINERALS);
        addSafe("COAL_BLOCK", 1_800, MINERALS);
        addSafe("RAW_COPPER", 400, MINERALS);
        addSafe("RAW_COPPER_BLOCK", 3_600, MINERALS);
        addSafe("COPPER_INGOT", 400, MINERALS);
        addSafe("COPPER_BLOCK", 3_600, MINERALS);
        addSafe("RAW_IRON", 200, MINERALS);
        addSafe("RAW_IRON_BLOCK", 1_800, MINERALS);
        addSafe("IRON_INGOT", 200, MINERALS);
        addSafe("IRON_BLOCK", 1_800, MINERALS);
        addSafe("RAW_GOLD", 1_500, MINERALS);
        addSafe("RAW_GOLD_BLOCK", 13_500, MINERALS);
        addSafe("GOLD_INGOT", 1_500, MINERALS);
        addSafe("GOLD_BLOCK", 13_500, MINERALS);
        addSafe("LAPIS_LAZULI", 100, MINERALS);
        addSafe("LAPIS_BLOCK", 900, MINERALS);
        addSafe("REDSTONE", 100, MINERALS);
        addSafe("REDSTONE_BLOCK", 900, MINERALS);
        addSafe("DIAMOND", 2_000, MINERALS);
        addSafe("DIAMOND_BLOCK", 18_000, MINERALS);
        addSafe("EMERALD", 5_000, MINERALS);
        addSafe("EMERALD_BLOCK", 45_000, MINERALS);
        addSafe("NETHERITE_INGOT", 50_000, MINERALS);
        addSafe("NETHERITE_BLOCK", 450_000, MINERALS);
        addSafe("AMETHYST_SHARD", 100, MINERALS);
        addSafe("AMETHYST_CLUSTER", 175, MINERALS);
        addSafe("AMETHYST_BLOCK", 400, MINERALS);

        // ==================== FARMING ====================
        addSafe("BAMBOO", 60, FARMING);
        addSafe("SUGAR_CANE", 60, FARMING);
        addSafe("CACTUS", 75, FARMING);
        addSafe("MELON_SLICE", 20, FARMING);
        addSafe("SWEET_BERRIES", 125, FARMING);
        addSafe("NETHER_WART", 125, FARMING);
        addSafe("CARROT", 125, FARMING);
        addSafe("POTATO", 125, FARMING);
        addSafe("PUMPKIN", 180, FARMING);
        addSafe("WHEAT", 125, FARMING);
        addSafe("WHEAT_SEEDS", 50, FARMING);
        addSafe("COCOA_BEANS", 50, FARMING);
        addSafe("PUMPKIN_SEEDS", 50, FARMING);
        addSafe("MELON_SEEDS", 50, FARMING);
        addSafe("BEETROOT_SEEDS", 50, FARMING);
        addSafe("BEETROOT", 65, FARMING);
        addSafe("KELP", 75, FARMING);
        addSafe("GLOW_BERRIES", 75, FARMING);
        addSafe("RESIN_CLUMP", 100, FARMING);
        addSafe("RED_MUSHROOM", 30, FARMING);
        addSafe("BROWN_MUSHROOM", 30, FARMING);

        // ==================== SPAWNERS (spawn eggs; prices in hundreds of thousands) ====================
        // Passive
        addSafe("COW_SPAWN_EGG", 250_000, SPAWNERS);
        addSafe("PIG_SPAWN_EGG", 250_000, SPAWNERS);
        addSafe("SHEEP_SPAWN_EGG", 250_000, SPAWNERS);
        addSafe("CHICKEN_SPAWN_EGG", 250_000, SPAWNERS);
        addSafe("RABBIT_SPAWN_EGG", 400_000, SPAWNERS);
        addSafe("HORSE_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("DONKEY_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("MULE_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("LLAMA_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("CAMEL_SPAWN_EGG", 800_000, SPAWNERS);
        addSafe("WOLF_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("CAT_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("FOX_SPAWN_EGG", 600_000, SPAWNERS);
        addSafe("PANDA_SPAWN_EGG", 750_000, SPAWNERS);
        addSafe("POLAR_BEAR_SPAWN_EGG", 750_000, SPAWNERS);
        addSafe("OCELOT_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("PARROT_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("MOOSHROOM_SPAWN_EGG", 750_000, SPAWNERS);
        addSafe("SQUID_SPAWN_EGG", 300_000, SPAWNERS);
        addSafe("GLOW_SQUID_SPAWN_EGG", 600_000, SPAWNERS);
        addSafe("AXOLOTL_SPAWN_EGG", 800_000, SPAWNERS);
        addSafe("TURTLE_SPAWN_EGG", 750_000, SPAWNERS);
        addSafe("DOLPHIN_SPAWN_EGG", 750_000, SPAWNERS);
        addSafe("SALMON_SPAWN_EGG", 250_000, SPAWNERS);
        addSafe("COD_SPAWN_EGG", 250_000, SPAWNERS);
        addSafe("TROPICAL_FISH_SPAWN_EGG", 400_000, SPAWNERS);
        addSafe("PUFFERFISH_SPAWN_EGG", 400_000, SPAWNERS);
        addSafe("TADPOLE_SPAWN_EGG", 400_000, SPAWNERS);
        addSafe("FROG_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("SNIFFER_SPAWN_EGG", 1_500_000, SPAWNERS);
        addSafe("ALLAY_SPAWN_EGG", 1_500_000, SPAWNERS);
        addSafe("BEE_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("ARMADILLO_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("VILLAGER_SPAWN_EGG", 600_000, SPAWNERS);
        addSafe("STRIDER_SPAWN_EGG", 750_000, SPAWNERS);

        // Hostile
        addSafe("ZOMBIE_SPAWN_EGG", 300_000, SPAWNERS);
        addSafe("SKELETON_SPAWN_EGG", 400_000, SPAWNERS);
        addSafe("CREEPER_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("SPIDER_SPAWN_EGG", 400_000, SPAWNERS);
        addSafe("CAVE_SPIDER_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("WITCH_SPAWN_EGG", 750_000, SPAWNERS);
        addSafe("ENDERMAN_SPAWN_EGG", 800_000, SPAWNERS);
        addSafe("BLAZE_SPAWN_EGG", 1_000_000, SPAWNERS);
        addSafe("GHAST_SPAWN_EGG", 1_000_000, SPAWNERS);
        addSafe("MAGMA_CUBE_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("SLIME_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("PHANTOM_SPAWN_EGG", 800_000, SPAWNERS);
        addSafe("PILLAGER_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("RAVAGER_SPAWN_EGG", 1_500_000, SPAWNERS);
        addSafe("VINDICATOR_SPAWN_EGG", 600_000, SPAWNERS);
        addSafe("EVOKER_SPAWN_EGG", 1_500_000, SPAWNERS);
        addSafe("DROWNED_SPAWN_EGG", 400_000, SPAWNERS);
        addSafe("HUSK_SPAWN_EGG", 400_000, SPAWNERS);
        addSafe("STRAY_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("WITHER_SKELETON_SPAWN_EGG", 1_500_000, SPAWNERS);
        addSafe("ZOMBIFIED_PIGLIN_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("PIGLIN_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("PIGLIN_BRUTE_SPAWN_EGG", 1_200_000, SPAWNERS);
        addSafe("HOGLIN_SPAWN_EGG", 800_000, SPAWNERS);
        addSafe("ZOGLIN_SPAWN_EGG", 800_000, SPAWNERS);
        addSafe("GUARDIAN_SPAWN_EGG", 1_000_000, SPAWNERS);
        addSafe("ELDER_GUARDIAN_SPAWN_EGG", 2_500_000, SPAWNERS);
        addSafe("SHULKER_SPAWN_EGG", 2_500_000, SPAWNERS);
        addSafe("SILVERFISH_SPAWN_EGG", 300_000, SPAWNERS);
        addSafe("ENDERMITE_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("VEX_SPAWN_EGG", 800_000, SPAWNERS);
        addSafe("WARDEN_SPAWN_EGG", 5_000_000, SPAWNERS);
        addSafe("BREEZE_SPAWN_EGG", 1_500_000, SPAWNERS);
        addSafe("BOGGED_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("CREAKING_SPAWN_EGG", 1_500_000, SPAWNERS);
        addSafe("IRON_GOLEM_SPAWN_EGG", 2_000_000, SPAWNERS);
        addSafe("SNOW_GOLEM_SPAWN_EGG", 500_000, SPAWNERS);
        addSafe("WANDERING_TRADER_SPAWN_EGG", 1_000_000, SPAWNERS);

        // ==================== MOB DROPS ====================
        addSafe("FEATHER", 5, MOB_DROPS);
        addSafe("WHITE_WOOL", 100, MOB_DROPS);
        addSafe("LEATHER", 150, MOB_DROPS);
        addSafe("ROTTEN_FLESH", 150, MOB_DROPS);
        addSafe("ARROW", 100, MOB_DROPS);
        addSafe("BONE", 200, MOB_DROPS);
        addSafe("STRING", 200, MOB_DROPS);
        addSafe("SPIDER_EYE", 100, MOB_DROPS);
        addSafe("GUNPOWDER", 250, MOB_DROPS);
        addSafe("INK_SAC", 200, MOB_DROPS);
        addSafe("GLOW_INK_SAC", 250, MOB_DROPS);
        addSafe("SLIME_BALL", 200, MOB_DROPS);
        addSafe("MAGMA_CREAM", 200, MOB_DROPS);
        addSafe("BLAZE_ROD", 500, MOB_DROPS);
        addSafe("ENDER_PEARL", 250, MOB_DROPS);
        addSafe("SNOWBALL", 10, MOB_DROPS);
        addSafe("EGG", 2, MOB_DROPS);
        addSafe("RABBIT_HIDE", 20, MOB_DROPS);
        addSafe("HONEYCOMB", 20, MOB_DROPS);
        addSafe("SCUTE", 50, MOB_DROPS);
        addSafe("TURTLE_SCUTE", 50, MOB_DROPS);
        addSafe("ARMADILLO_SCUTE", 50, MOB_DROPS);
        addSafe("PRISMARINE_SHARD", 250, MOB_DROPS);
        addSafe("NAUTILUS_SHELL", 550, MOB_DROPS);
        addSafe("HEART_OF_THE_SEA", 1_500, MOB_DROPS);
        addSafe("BREEZE_ROD", 2_500, MOB_DROPS);
        addSafe("NETHER_STAR", 350_000, MOB_DROPS);
        addSafe("GHAST_TEAR", 1_000, MOB_DROPS);
        addSafe("PHANTOM_MEMBRANE", 50, MOB_DROPS);
        addSafe("SHULKER_SHELL", 3_000, MOB_DROPS);

        // ==================== FOOD ====================
        addSafe("CHICKEN", 100, FOOD);
        addSafe("COOKED_CHICKEN", 150, FOOD);
        addSafe("MUTTON", 150, FOOD);
        addSafe("COOKED_MUTTON", 200, FOOD);
        addSafe("PORKCHOP", 150, FOOD);
        addSafe("COOKED_PORKCHOP", 200, FOOD);
        addSafe("BEEF", 150, FOOD);
        addSafe("COOKED_BEEF", 200, FOOD);
        addSafe("APPLE", 200, FOOD);
        addSafe("GOLDEN_APPLE", 400, FOOD);
        addSafe("ENCHANTED_GOLDEN_APPLE", 700, FOOD);
        addSafe("GOLDEN_CARROT", 700, FOOD);
        addSafe("BAKED_POTATO", 300, FOOD);
        addSafe("DRIED_KELP", 200, FOOD);
        addSafe("RABBIT", 300, FOOD);
        addSafe("COOKED_RABBIT", 400, FOOD);
        addSafe("COD", 200, FOOD);
        addSafe("COOKED_COD", 400, FOOD);
        addSafe("SALMON", 200, FOOD);
        addSafe("COOKED_SALMON", 350, FOOD);
        addSafe("TROPICAL_FISH", 150, FOOD);
        addSafe("PUFFERFISH", 100, FOOD);
        addSafe("BREAD", 200, FOOD);
        addSafe("COOKIE", 200, FOOD);
        addSafe("CAKE", 1_500, FOOD);
        addSafe("PUMPKIN_PIE", 1_000, FOOD);

        // ==================== DECORATION ====================
        String[] colors = {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME",
                "PINK", "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE",
                "BROWN", "GREEN", "RED", "BLACK"};
        for (String c : colors) addSafe(c + "_WOOL", 150, DECORATION);
        for (String c : colors) addSafe(c + "_TERRACOTTA", 300, DECORATION);
        for (String c : colors) addSafe(c + "_GLAZED_TERRACOTTA", 300, DECORATION);
        for (String c : colors) addSafe(c + "_STAINED_GLASS", 200, DECORATION);
        for (String c : colors) addSafe(c + "_CONCRETE_POWDER", 50, DECORATION);
        for (String c : colors) addSafe(c + "_CONCRETE", 100, DECORATION);
        for (String c : colors) addSafe(c + "_CANDLE", 50, DECORATION);
        addSafe("CANDLE", 50, DECORATION);

        addSafe("VINE", 30, DECORATION);
        addSafe("LILY_PAD", 30, DECORATION);
        addSafe("FERN", 30, DECORATION);
        addSafe("LARGE_FERN", 35, DECORATION);
        addSafe("TALL_GRASS", 35, DECORATION);
        addSafe("SHORT_GRASS", 30, DECORATION);
        addSafe("WHITE_TULIP", 50, DECORATION);
        addSafe("PINK_TULIP", 50, DECORATION);
        addSafe("RED_TULIP", 50, DECORATION);
        addSafe("ORANGE_TULIP", 50, DECORATION);
        addSafe("POPPY", 150, DECORATION);
        addSafe("DANDELION", 100, DECORATION);
        addSafe("AZURE_BLUET", 50, DECORATION);
        addSafe("BLUE_ORCHID", 100, DECORATION);
        addSafe("ALLIUM", 100, DECORATION);
        addSafe("CORNFLOWER", 100, DECORATION);
        addSafe("LILY_OF_THE_VALLEY", 100, DECORATION);
        addSafe("OXEYE_DAISY", 100, DECORATION);
        addSafe("SUNFLOWER", 50, DECORATION);
        addSafe("LILAC", 100, DECORATION);
        addSafe("PEONY", 100, DECORATION);
        addSafe("ROSE_BUSH", 100, DECORATION);
        addSafe("WITHER_ROSE", 1_000, DECORATION);

        String[] coralTypes = {"FIRE", "HORN", "BRAIN", "BUBBLE", "TUBE"};
        for (String c : coralTypes) {
            addSafe(c + "_CORAL", 250, DECORATION);
            addSafe(c + "_CORAL_FAN", 150, DECORATION);
            addSafe(c + "_CORAL_BLOCK", 125, DECORATION);
        }
        addSafe("SMALL_AMETHYST_BUD", 100, DECORATION);
        addSafe("MEDIUM_AMETHYST_BUD", 125, DECORATION);
        addSafe("LARGE_AMETHYST_BUD", 150, DECORATION);
        addSafe("AMETHYST_CLUSTER", 200, DECORATION);
        addSafe("AZALEA", 50, DECORATION);
        addSafe("FLOWERING_AZALEA", 50, DECORATION);
        addSafe("SMALL_DRIPLEAF", 35, DECORATION);
        addSafe("BIG_DRIPLEAF", 50, DECORATION);
        addSafe("GLOW_ITEM_FRAME", 100, DECORATION);
        addSafe("ITEM_FRAME", 50, DECORATION);
        addSafe("GLOW_LICHEN", 35, DECORATION);
        addSafe("HANGING_ROOTS", 35, DECORATION);
        addSafe("TINTED_GLASS", 100, DECORATION);
        addSafe("MOSS_BLOCK", 100, DECORATION);
        addSafe("CHAIN", 100, DECORATION);
        addSafe("SOUL_CAMPFIRE", 75, DECORATION);
        addSafe("LANTERN", 20, DECORATION);
        addSafe("SOUL_LANTERN", 20, DECORATION);
        addSafe("SOUL_TORCH", 20, DECORATION);
        addSafe("TWISTING_VINES", 35, DECORATION);
        addSafe("WEEPING_VINES", 35, DECORATION);
        addSafe("POINTED_DRIPSTONE", 100, DECORATION);
        addSafe("CRIMSON_FUNGUS", 100, DECORATION);
        addSafe("WARPED_FUNGUS", 100, DECORATION);
        addSafe("CRIMSON_ROOTS", 35, DECORATION);
        addSafe("WARPED_ROOTS", 35, DECORATION);
        addSafe("SEA_PICKLE", 55, DECORATION);
        addSafe("END_ROD", 300, DECORATION);
        addSafe("COBWEB", 100, DECORATION);
        addSafe("SPORE_BLOSSOM", 500, DECORATION);
        addSafe("BELL", 15_000, DECORATION);
        addSafe("NETHERITE_UPGRADE_SMITHING_TEMPLATE", 50_000, DECORATION);

        String[] trims = {"SENTRY", "VEX", "WILD", "COAST", "DUNE", "WAYFINDER", "RAISER",
                "SHAPER", "HOST", "WARD", "SILENCE", "TIDE", "SNOUT", "RIB", "EYE",
                "SPIRE", "BOLT", "FLOW"};
        for (String t : trims) addSafe(t + "_ARMOR_TRIM_SMITHING_TEMPLATE", 150_000, DECORATION);

        String[] banners = {"SKULL", "CREEPER", "FLOWER", "MOJANG", "GLOBE", "PIGLIN"};
        for (String b : banners) addSafe(b + "_BANNER_PATTERN", 25_000, DECORATION);

        // ==================== MISCELLANEOUS ====================
        addSafe("TORCH", 5, MISCELLANEOUS);
        addSafe("REDSTONE_TORCH", 20, MISCELLANEOUS);
        addSafe("CAMPFIRE", 20, MISCELLANEOUS);
        addSafe("CRAFTING_TABLE", 5, MISCELLANEOUS);
        addSafe("STONECUTTER", 20, MISCELLANEOUS);
        addSafe("CARTOGRAPHY_TABLE", 20, MISCELLANEOUS);
        addSafe("FLETCHING_TABLE", 20, MISCELLANEOUS);
        addSafe("SMITHING_TABLE", 80, MISCELLANEOUS);
        addSafe("GRINDSTONE", 20, MISCELLANEOUS);
        addSafe("LOOM", 20, MISCELLANEOUS);
        addSafe("FURNACE", 5, MISCELLANEOUS);
        addSafe("SMOKER", 20, MISCELLANEOUS);
        addSafe("BLAST_FURNACE", 80, MISCELLANEOUS);
        addSafe("ANVIL", 150, MISCELLANEOUS);
        addSafe("COMPOSTER", 5, MISCELLANEOUS);
        addSafe("BREWING_STAND", 75, MISCELLANEOUS);
        addSafe("CHEST", 5, MISCELLANEOUS);
        addSafe("LEAD", 80, MISCELLANEOUS);
        addSafe("NAME_TAG", 150, MISCELLANEOUS);
        addSafe("SADDLE", 250, MISCELLANEOUS);
        addSafe("WATER_BUCKET", 20, MISCELLANEOUS);
        addSafe("LAVA_BUCKET", 80, MISCELLANEOUS);
        addSafe("TOTEM_OF_UNDYING", 150_000, MISCELLANEOUS);

        // ==================== SAPLINGS ====================
        addSafe("OAK_SAPLING", 100, SAPLINGS);
        addSafe("SPRUCE_SAPLING", 100, SAPLINGS);
        addSafe("BIRCH_SAPLING", 100, SAPLINGS);
        addSafe("JUNGLE_SAPLING", 100, SAPLINGS);
        addSafe("ACACIA_SAPLING", 100, SAPLINGS);
        addSafe("DARK_OAK_SAPLING", 100, SAPLINGS);
        addSafe("CHERRY_SAPLING", 100, SAPLINGS);
        addSafe("MANGROVE_PROPAGULE", 100, SAPLINGS);
        addSafe("WARPED_FUNGUS", 100, SAPLINGS);
        addSafe("CRIMSON_FUNGUS", 100, SAPLINGS);
        addSafe("PALE_OAK_SAPLING", 100, SAPLINGS);
    }

    /**
     * Try to register an item by material name; silently skip if the material
     * doesn't exist in this Bukkit version (e.g. 1.21+ items on older jars).
     */
    private static void addSafe(String materialName, int price, ShopCategory category) {
        Material mat;
        try {
            mat = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            return;
        }
        ALL_ITEMS.add(new ShopItem(mat, price, category));
    }

    public int getShopPrice(Material material) {
        for (ShopItem item : ALL_ITEMS) {
            if (item.material() == material) return item.price();
        }
        return -1;
    }

    public record DailyDeal(ShopItem item, int discountPercent) {}

    private final JavaPlugin plugin;
    private final EconomyManager economy;
    private final Map<UUID, ShopSession> sessions = new HashMap<>();
    private final List<DailyDeal> dailyDeals = new ArrayList<>();
    private long lastDealEpochDay;

    public ShopManager(JavaPlugin plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
        refreshDailyDeals();
    }

    private void refreshDailyDeals() {
        long today = LocalDate.now().toEpochDay();
        if (today == lastDealEpochDay && !dailyDeals.isEmpty()) return;
        lastDealEpochDay = today;
        dailyDeals.clear();
        if (ALL_ITEMS.isEmpty()) return;
        Random rng = new Random(today * 31337);
        List<ShopItem> pool = new ArrayList<>(ALL_ITEMS);
        for (int i = 0; i < 3 && !pool.isEmpty(); i++) {
            int idx = rng.nextInt(pool.size());
            ShopItem item = pool.remove(idx);
            int discount = 20 + rng.nextInt(21);
            dailyDeals.add(new DailyDeal(item, discount));
        }
    }

    public void openMainMenu(Player player) {
        refreshDailyDeals();
        ShopSession session = new ShopSession(ShopSession.State.MAIN_MENU);
        sessions.put(player.getUniqueId(), session);

        Inventory inv = createInventory(54, "Shop");
        fillBorder(inv);

        for (int i = 0; i < dailyDeals.size(); i++) {
            DailyDeal deal = dailyDeals.get(i);
            ShopItem si = deal.item();
            int discounted = (int) (si.price() * (1.0 - deal.discountPercent() / 100.0));
            ItemStack icon = new ItemStack(iconMaterial(si));
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(displayName(si), TextColor.color(0xFFD700))
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("DAILY DEAL!", TextColor.color(0xFF5555))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("-" + deal.discountPercent() + "% OFF", TextColor.color(0x55FF55))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Was: ", TextColor.color(0xFF5555))
                    .decoration(TextDecoration.ITALIC, false)
                    .append(EconomyManager.moneyText(si.price()).decoration(TextDecoration.ITALIC, false))
                    .decoration(TextDecoration.STRIKETHROUGH, true));
            lore.add(Component.text("Now: ", TextColor.color(0x55FF55))
                    .decoration(TextDecoration.ITALIC, false)
                    .append(EconomyManager.moneyText(discounted).decoration(TextDecoration.ITALIC, false)));
            lore.add(Component.empty());
            lore.add(Component.text("Click to buy!", TextColor.color(0xAAAAAA))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(DEAL_SLOTS[i], icon);
        }

        ShopCategory[] cats = ShopCategory.values();
        for (int i = 0; i < CAT_SLOTS.length && i < cats.length; i++) {
            inv.setItem(CAT_SLOTS[i], createCategoryIcon(cats[i]));
        }

        player.openInventory(inv);
    }

    public void openCategory(Player player, ShopCategory category, int page) {
        List<ShopItem> items = BY_CATEGORY.getOrDefault(category, List.of());
        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        ShopSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new ShopSession(ShopSession.State.CATEGORY));
        session.state = ShopSession.State.CATEGORY;
        session.category = category;
        session.page = page;

        Inventory inv = createInventory(54, "Shop - " + category.displayName()
                + " (Page " + (page + 1) + "/" + totalPages + ")");
        fillRow(inv, 5);

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());
        for (int i = start; i < end; i++) {
            ShopItem si = items.get(i);
            inv.setItem(i - start, createShopItemIcon(si));
        }

        inv.setItem(45, createGuiItem(Material.BARRIER, "Back", 0xFF5555));
        if (page > 0) {
            inv.setItem(48, createGuiItem(Material.ARROW, "Previous Page", 0xFFFF55));
        }
        inv.setItem(49, createGuiItem(Material.PAPER,
                "Page " + (page + 1) + "/" + totalPages, 0xAAAAAA));
        if (page < totalPages - 1) {
            inv.setItem(50, createGuiItem(Material.ARROW, "Next Page", 0xFFFF55));
        }

        player.openInventory(inv);
    }

    public void openSearchResults(Player player, String query) {
        String lower = query.toLowerCase();
        List<ShopItem> results = ALL_ITEMS.stream()
                .filter(si -> displayName(si).toLowerCase().contains(lower)
                        || formatMaterial(si.material()).toLowerCase().contains(lower))
                .toList();

        ShopSession session = new ShopSession(ShopSession.State.SEARCH_RESULTS);
        session.searchResults = results;
        session.page = 0;
        sessions.put(player.getUniqueId(), session);

        if (results.isEmpty()) {
            Msg.error(player, "No items found for '" + query + "'.");
            return;
        }
        openSearchPage(player, session);
    }

    private void openSearchPage(Player player, ShopSession session) {
        List<ShopItem> results = session.searchResults;
        int totalPages = Math.max(1, (int) Math.ceil(results.size() / (double) ITEMS_PER_PAGE));
        int page = Math.max(0, Math.min(session.page, totalPages - 1));
        session.page = page;

        Inventory inv = createInventory(54, "Shop - Search Results");
        fillRow(inv, 5);

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, results.size());
        for (int i = start; i < end; i++) {
            ShopItem si = results.get(i);
            inv.setItem(i - start, createShopItemIcon(si));
        }

        inv.setItem(45, createGuiItem(Material.BARRIER, "Back", 0xFF5555));
        if (page > 0) {
            inv.setItem(48, createGuiItem(Material.ARROW, "Previous Page", 0xFFFF55));
        }
        inv.setItem(49, createGuiItem(Material.PAPER,
                "Page " + (page + 1) + "/" + totalPages + " (" + results.size() + " results)", 0xAAAAAA));
        if (page < totalPages - 1) {
            inv.setItem(50, createGuiItem(Material.ARROW, "Next Page", 0xFFFF55));
        }
        player.openInventory(inv);
    }

    public void openBuyScreen(Player player, ShopItem item, int dealDiscount, ShopSession.State returnTo) {
        ShopSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new ShopSession(ShopSession.State.BUY));
        session.state = ShopSession.State.BUY;
        session.selectedItem = item;
        session.quantity = 1;
        session.dealDiscount = dealDiscount;
        session.returnTo = returnTo;

        Inventory inv = createInventory(27, "Shop - Buy " + displayName(item));
        fillAll(inv);

        // Quantity buttons: -1, -10, -64  [gap]  +1, +10, +64
        inv.setItem(BUY_MINUS_1, quantityButton(Material.RED_STAINED_GLASS_PANE, "-1", 0xFF5555, 1));
        inv.setItem(BUY_MINUS_10, quantityButton(Material.RED_STAINED_GLASS_PANE, "-10", 0xFF5555, 10));
        inv.setItem(BUY_MINUS_64, quantityButton(Material.RED_STAINED_GLASS_PANE, "-64", 0xFF5555, 64));
        inv.setItem(13, blankPane());
        inv.setItem(BUY_PLUS_1, quantityButton(Material.LIME_STAINED_GLASS_PANE, "+1", 0x55FF55, 1));
        inv.setItem(BUY_PLUS_10, quantityButton(Material.LIME_STAINED_GLASS_PANE, "+10", 0x55FF55, 10));
        inv.setItem(BUY_PLUS_64, quantityButton(Material.LIME_STAINED_GLASS_PANE, "+64", 0x55FF55, 64));

        inv.setItem(BUY_ACCEPT, createGuiItem(Material.LIME_CONCRETE, "Accept", 0x55FF55));
        inv.setItem(BUY_CANCEL, createGuiItem(Material.RED_CONCRETE, "Cancel", 0xFF5555));

        updateBuyDisplay(inv, session);
        player.openInventory(inv);
    }

    private void updateBuyDisplay(Inventory inv, ShopSession session) {
        ShopItem si = session.selectedItem;
        Material iconMat = iconMaterial(si);
        int displayCount = Math.min(session.quantity, iconMat.getMaxStackSize());
        ItemStack display = new ItemStack(iconMat, displayCount);
        ItemMeta dm = display.getItemMeta();
        dm.displayName(Component.text(displayName(si), TextColor.color(0xFFD700), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        double unitPrice = si.price();
        if (session.dealDiscount > 0) unitPrice *= (1.0 - session.dealDiscount / 100.0);
        double subtotal = unitPrice * session.quantity;
        double bulkDisc = 0;
        if (session.quantity >= BULK_THRESHOLD) {
            bulkDisc = subtotal * BULK_DISCOUNT;
            subtotal -= bulkDisc;
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Unit price: ", TextColor.color(0xAAAAAA))
                .decoration(TextDecoration.ITALIC, false)
                .append(EconomyManager.moneyText(unitPrice).decoration(TextDecoration.ITALIC, false)));
        if (session.dealDiscount > 0) {
            lore.add(Component.text("Daily Deal: -" + session.dealDiscount + "%", TextColor.color(0x55FF55))
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("Quantity: " + session.quantity, TextColor.color(0xFFFF55))
                .decoration(TextDecoration.ITALIC, false));
        if (bulkDisc > 0) {
            lore.add(Component.text("Bulk discount: -10% (-", TextColor.color(0x55FF55))
                    .decoration(TextDecoration.ITALIC, false)
                    .append(EconomyManager.moneyText(bulkDisc).decoration(TextDecoration.ITALIC, false))
                    .append(Component.text(")", TextColor.color(0x55FF55))));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Total: ", TextColor.color(0x55FF55), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false)
                .append(EconomyManager.moneyText(subtotal).decoration(TextDecoration.ITALIC, false)));
        dm.lore(lore);
        display.setItemMeta(dm);
        inv.setItem(BUY_ICON_SLOT, display);
    }

    double calculateTotal(ShopSession session) {
        double unitPrice = session.selectedItem.price();
        if (session.dealDiscount > 0) unitPrice *= (1.0 - session.dealDiscount / 100.0);
        double total = unitPrice * session.quantity;
        if (session.quantity >= BULK_THRESHOLD) total *= (1.0 - BULK_DISCOUNT);
        return total;
    }

    public void handleClick(Player player, int slot) {
        ShopSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        switch (session.state) {
            case MAIN_MENU -> handleMainMenuClick(player, session, slot);
            case CATEGORY -> handleCategoryClick(player, session, slot);
            case BUY -> handleBuyClick(player, session, slot);
            case SEARCH_RESULTS -> handleSearchClick(player, session, slot);
        }
    }

    private void handleMainMenuClick(Player player, ShopSession session, int slot) {
        for (int i = 0; i < DEAL_SLOTS.length; i++) {
            if (slot == DEAL_SLOTS[i] && i < dailyDeals.size()) {
                DailyDeal deal = dailyDeals.get(i);
                openBuyScreen(player, deal.item(), deal.discountPercent(), ShopSession.State.MAIN_MENU);
                return;
            }
        }
        ShopCategory[] cats = ShopCategory.values();
        for (int i = 0; i < CAT_SLOTS.length && i < cats.length; i++) {
            if (slot == CAT_SLOTS[i]) {
                openCategory(player, cats[i], 0);
                return;
            }
        }
    }

    private void handleCategoryClick(Player player, ShopSession session, int slot) {
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            List<ShopItem> items = BY_CATEGORY.getOrDefault(session.category, List.of());
            int idx = session.page * ITEMS_PER_PAGE + slot;
            if (idx < items.size()) {
                openBuyScreen(player, items.get(idx), 0, ShopSession.State.CATEGORY);
            }
        } else if (slot == 45) {
            openMainMenu(player);
        } else if (slot == 48 && session.page > 0) {
            openCategory(player, session.category, session.page - 1);
        } else if (slot == 50) {
            openCategory(player, session.category, session.page + 1);
        }
    }

    private void handleSearchClick(Player player, ShopSession session, int slot) {
        if (slot >= 0 && slot < ITEMS_PER_PAGE && session.searchResults != null) {
            int idx = session.page * ITEMS_PER_PAGE + slot;
            if (idx < session.searchResults.size()) {
                openBuyScreen(player, session.searchResults.get(idx), 0, ShopSession.State.SEARCH_RESULTS);
            }
        } else if (slot == 45) {
            openMainMenu(player);
        } else if (slot == 48 && session.page > 0) {
            session.page--;
            openSearchPage(player, session);
        } else if (slot == 50) {
            session.page++;
            openSearchPage(player, session);
        }
    }

    private void handleBuyClick(Player player, ShopSession session, int slot) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        switch (slot) {
            case BUY_MINUS_1 -> { session.quantity = Math.max(session.quantity - 1, 1); updateBuyDisplay(inv, session); }
            case BUY_MINUS_10 -> { session.quantity = Math.max(session.quantity - 10, 1); updateBuyDisplay(inv, session); }
            case BUY_MINUS_64 -> { session.quantity = Math.max(session.quantity - 64, 1); updateBuyDisplay(inv, session); }
            case BUY_PLUS_1 -> { session.quantity = Math.min(session.quantity + 1, MAX_QUANTITY); updateBuyDisplay(inv, session); }
            case BUY_PLUS_10 -> { session.quantity = Math.min(session.quantity + 10, MAX_QUANTITY); updateBuyDisplay(inv, session); }
            case BUY_PLUS_64 -> { session.quantity = Math.min(session.quantity + 64, MAX_QUANTITY); updateBuyDisplay(inv, session); }
            case BUY_ACCEPT -> performPurchase(player, session);
            case BUY_CANCEL -> handleCancel(player, session);
        }
    }

    private void handleCancel(Player player, ShopSession session) {
        switch (session.returnTo) {
            case MAIN_MENU -> openMainMenu(player);
            case CATEGORY -> openCategory(player, session.category, session.page);
            case SEARCH_RESULTS -> openSearchPage(player, session);
            default -> openMainMenu(player);
        }
    }

    private void performPurchase(Player player, ShopSession session) {
        double total = calculateTotal(session);
        UUID uuid = player.getUniqueId();

        if (!economy.hasMoney(uuid, total)) {
            double have = economy.getMoney(uuid);
            double need = Math.floor(total - have);
            player.sendMessage(Msg.prefix()
                    .append(Component.text("Insufficient funds! You need ", TextColor.color(0xFF5555)))
                    .append(EconomyManager.moneyText(need))
                    .append(Component.text(" more (have ", TextColor.color(0xFF5555)))
                    .append(EconomyManager.moneyText(have))
                    .append(Component.text(" / ", TextColor.color(0xFF5555)))
                    .append(EconomyManager.moneyText(total))
                    .append(Component.text(").", TextColor.color(0xFF5555))));
            return;
        }

        economy.removeMoney(uuid, total);

        Material mat = session.selectedItem.material();
        int remaining = session.quantity;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, mat.getMaxStackSize());
            ItemStack stack = new ItemStack(mat, stackSize);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= stackSize;
        }

        player.closeInventory();
        player.sendMessage(Msg.prefix()
                .append(Component.text("Purchased " + session.quantity + "x "
                        + displayName(session.selectedItem) + " for ", TextColor.color(0x55FF55)))
                .append(EconomyManager.moneyText(total))
                .append(Component.text("!", TextColor.color(0x55FF55))));
    }

    public void removeSession(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    private Inventory createInventory(int size, String title) {
        ShopHolder holder = new ShopHolder();
        Inventory inv = Bukkit.createInventory(holder, size,
                Component.text(title, TextColor.color(0xFFD700))
                        .decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);
        return inv;
    }

    private void fillAll(Inventory inv) {
        ItemStack filler = blankPane();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private void fillBorder(Inventory inv) {
        ItemStack filler = blankPane();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        for (int slot : DEAL_SLOTS) inv.setItem(slot, null);
        for (int slot : CAT_SLOTS) inv.setItem(slot, null);
    }

    private void fillRow(Inventory inv, int row) {
        ItemStack filler = blankPane();
        int start = row * 9;
        for (int i = start; i < start + 9 && i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private ItemStack blankPane() {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        return filler;
    }

    private ItemStack createCategoryIcon(ShopCategory cat) {
        List<ShopItem> items = BY_CATEGORY.getOrDefault(cat, List.of());
        return createGuiItem(cat.icon(), cat.displayName(), cat.color(),
                items.size() + " items", "Click to browse!");
    }

    private ItemStack createShopItemIcon(ShopItem si) {
        ItemStack icon = new ItemStack(iconMaterial(si));
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(displayName(si), TextColor.color(0xFFFFFF))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Price: ", TextColor.color(0x55FF55))
                .decoration(TextDecoration.ITALIC, false)
                .append(EconomyManager.moneyText(si.price()).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.text("Category: " + si.category().displayName(), TextColor.color(0xAAAAAA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to buy!", TextColor.color(0xFFFF55))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack quantityButton(Material material, String label, int color, int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, TextColor.color(color), TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createGuiItem(Material material, String name, int color, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, TextColor.color(color))
                .decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> loreList = new ArrayList<>();
            for (String line : lore) {
                if (line.isEmpty()) loreList.add(Component.empty());
                else loreList.add(Component.text(line, TextColor.color(0xAAAAAA))
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(loreList);
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * All items in the SPAWNERS category are displayed with a spawner block icon
     * for visual consistency. On purchase the player still receives the underlying
     * spawn egg (that's the actually placeable item).
     */
    private static Material iconMaterial(ShopItem si) {
        return si.category() == ShopCategory.SPAWNERS ? Material.SPAWNER : si.material();
    }

    private static String displayName(ShopItem si) {
        if (si.category() == ShopCategory.SPAWNERS) {
            String name = si.material().name();
            if (name.endsWith("_SPAWN_EGG")) {
                name = name.substring(0, name.length() - "_SPAWN_EGG".length());
            }
            return formatFromRaw(name) + " Spawner";
        }
        return formatMaterial(si.material());
    }

    private static String formatFromRaw(String rawName) {
        if (rawName.equals("TNT")) return "TNT";
        StringBuilder sb = new StringBuilder();
        for (String word : rawName.split("_")) {
            if (!sb.isEmpty()) sb.append(' ');
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    public static String formatMaterial(Material material) {
        String name = material.name();
        if (name.equals("TNT")) return "TNT";
        StringBuilder sb = new StringBuilder();
        for (String word : name.split("_")) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
