package com.starlightuniverse.skill;

import com.starlightuniverse.auth.AuthManager;
import com.starlightuniverse.job.JobManager;
import com.starlightuniverse.world.WorldManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SkillListener implements Listener {

    private static final Set<Material> ORES = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS
    );

    private static final Set<Material> STONE_TYPES = Set.of(
            Material.STONE, Material.DEEPSLATE, Material.ANDESITE, Material.DIORITE,
            Material.GRANITE, Material.TUFF, Material.CALCITE, Material.BLACKSTONE,
            Material.BASALT, Material.NETHERRACK, Material.END_STONE
    );

    private static final Set<Material> DIG_BLOCKS = Set.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.ROOTED_DIRT, Material.COARSE_DIRT,
            Material.DIRT_PATH, Material.SAND, Material.RED_SAND, Material.GRAVEL,
            Material.CLAY, Material.SOUL_SAND, Material.SOUL_SOIL, Material.MYCELIUM,
            Material.PODZOL, Material.MUD
    );

    private static final Set<Material> LOG_BLOCKS = Set.of(
            Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.BAMBOO_BLOCK, Material.CRIMSON_STEM, Material.WARPED_STEM
    );

    private static final Set<Material> CROP_BLOCKS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH,
            Material.TORCHFLOWER_CROP, Material.PITCHER_CROP
    );

    private static final Set<Material> NON_AGEABLE_FARM = Set.of(
            Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE, Material.CACTUS
    );

    private static final Set<Material> FOOD_ITEMS = Set.of(
            Material.COOKED_BEEF, Material.COOKED_CHICKEN, Material.COOKED_COD,
            Material.COOKED_MUTTON, Material.COOKED_PORKCHOP, Material.COOKED_RABBIT,
            Material.COOKED_SALMON, Material.BAKED_POTATO, Material.BREAD,
            Material.DRIED_KELP
    );

    private static final Map<Material, Material> ORE_DROP_MAP = new HashMap<>();
    private static final Map<Material, Material> CROP_DROP_MAP = new HashMap<>();

    static {
        ORE_DROP_MAP.put(Material.COAL_ORE, Material.COAL);
        ORE_DROP_MAP.put(Material.DEEPSLATE_COAL_ORE, Material.COAL);
        ORE_DROP_MAP.put(Material.COPPER_ORE, Material.RAW_COPPER);
        ORE_DROP_MAP.put(Material.DEEPSLATE_COPPER_ORE, Material.RAW_COPPER);
        ORE_DROP_MAP.put(Material.IRON_ORE, Material.RAW_IRON);
        ORE_DROP_MAP.put(Material.DEEPSLATE_IRON_ORE, Material.RAW_IRON);
        ORE_DROP_MAP.put(Material.GOLD_ORE, Material.RAW_GOLD);
        ORE_DROP_MAP.put(Material.DEEPSLATE_GOLD_ORE, Material.RAW_GOLD);
        ORE_DROP_MAP.put(Material.LAPIS_ORE, Material.LAPIS_LAZULI);
        ORE_DROP_MAP.put(Material.DEEPSLATE_LAPIS_ORE, Material.LAPIS_LAZULI);
        ORE_DROP_MAP.put(Material.REDSTONE_ORE, Material.REDSTONE);
        ORE_DROP_MAP.put(Material.DEEPSLATE_REDSTONE_ORE, Material.REDSTONE);
        ORE_DROP_MAP.put(Material.DIAMOND_ORE, Material.DIAMOND);
        ORE_DROP_MAP.put(Material.DEEPSLATE_DIAMOND_ORE, Material.DIAMOND);
        ORE_DROP_MAP.put(Material.EMERALD_ORE, Material.EMERALD);
        ORE_DROP_MAP.put(Material.DEEPSLATE_EMERALD_ORE, Material.EMERALD);
        ORE_DROP_MAP.put(Material.NETHER_GOLD_ORE, Material.GOLD_NUGGET);
        ORE_DROP_MAP.put(Material.NETHER_QUARTZ_ORE, Material.QUARTZ);

        CROP_DROP_MAP.put(Material.WHEAT, Material.WHEAT);
        CROP_DROP_MAP.put(Material.CARROTS, Material.CARROT);
        CROP_DROP_MAP.put(Material.POTATOES, Material.POTATO);
        CROP_DROP_MAP.put(Material.BEETROOTS, Material.BEETROOT);
        CROP_DROP_MAP.put(Material.NETHER_WART, Material.NETHER_WART);
        CROP_DROP_MAP.put(Material.COCOA, Material.COCOA_BEANS);
        CROP_DROP_MAP.put(Material.SWEET_BERRY_BUSH, Material.SWEET_BERRIES);
        CROP_DROP_MAP.put(Material.TORCHFLOWER_CROP, Material.TORCHFLOWER);
        CROP_DROP_MAP.put(Material.PITCHER_CROP, Material.PITCHER_PLANT);
        CROP_DROP_MAP.put(Material.MELON, Material.MELON_SLICE);
        CROP_DROP_MAP.put(Material.PUMPKIN, Material.PUMPKIN);
        CROP_DROP_MAP.put(Material.SUGAR_CANE, Material.SUGAR_CANE);
        CROP_DROP_MAP.put(Material.CACTUS, Material.CACTUS);
    }

    private static final Map<Material, Integer> MINING_XP = new HashMap<>();
    private static final Map<Material, Integer> DIG_XP = new HashMap<>();
    private static final Map<EntityType, Integer> COMBAT_XP = new HashMap<>();

    static {
        for (Material m : List.of(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE)) MINING_XP.put(m, 5);
        for (Material m : List.of(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE)) MINING_XP.put(m, 7);
        for (Material m : List.of(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE)) MINING_XP.put(m, 10);
        for (Material m : List.of(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE)) MINING_XP.put(m, 12);
        for (Material m : List.of(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE)) MINING_XP.put(m, 8);
        for (Material m : List.of(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE)) MINING_XP.put(m, 15);
        for (Material m : List.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE)) MINING_XP.put(m, 50);
        for (Material m : List.of(Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE)) MINING_XP.put(m, 40);
        MINING_XP.put(Material.NETHER_GOLD_ORE, 10);
        MINING_XP.put(Material.NETHER_QUARTZ_ORE, 7);
        MINING_XP.put(Material.ANCIENT_DEBRIS, 100);
        for (Material m : STONE_TYPES) MINING_XP.put(m, 1);

        for (Material m : List.of(Material.DIRT, Material.GRASS_BLOCK, Material.ROOTED_DIRT,
                Material.COARSE_DIRT, Material.DIRT_PATH)) DIG_XP.put(m, 1);
        for (Material m : List.of(Material.SAND, Material.RED_SAND)) DIG_XP.put(m, 2);
        DIG_XP.put(Material.GRAVEL, 2);
        DIG_XP.put(Material.CLAY, 4);
        for (Material m : List.of(Material.SOUL_SAND, Material.SOUL_SOIL)) DIG_XP.put(m, 3);
        for (Material m : List.of(Material.MYCELIUM, Material.PODZOL)) DIG_XP.put(m, 3);
        DIG_XP.put(Material.MUD, 2);

        for (EntityType e : List.of(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER,
                EntityType.HUSK, EntityType.DROWNED)) COMBAT_XP.put(e, 5);
        for (EntityType e : List.of(EntityType.SKELETON, EntityType.STRAY)) COMBAT_XP.put(e, 5);
        COMBAT_XP.put(EntityType.WITHER_SKELETON, 25);
        for (EntityType e : List.of(EntityType.SPIDER, EntityType.CAVE_SPIDER)) COMBAT_XP.put(e, 5);
        COMBAT_XP.put(EntityType.CREEPER, 5);
        COMBAT_XP.put(EntityType.ENDERMAN, 20);
        COMBAT_XP.put(EntityType.BLAZE, 25);
        COMBAT_XP.put(EntityType.WITCH, 15);
        COMBAT_XP.put(EntityType.GUARDIAN, 20);
        COMBAT_XP.put(EntityType.ELDER_GUARDIAN, 80);
        COMBAT_XP.put(EntityType.WARDEN, 200);
        COMBAT_XP.put(EntityType.WITHER, 400);
        COMBAT_XP.put(EntityType.ENDER_DRAGON, 1000);
        COMBAT_XP.put(EntityType.PHANTOM, 10);
        COMBAT_XP.put(EntityType.PIGLIN_BRUTE, 20);
        for (EntityType e : List.of(EntityType.HOGLIN, EntityType.ZOGLIN)) COMBAT_XP.put(e, 15);
        COMBAT_XP.put(EntityType.GHAST, 15);
        for (EntityType e : List.of(EntityType.VINDICATOR, EntityType.PILLAGER)) COMBAT_XP.put(e, 15);
        COMBAT_XP.put(EntityType.EVOKER, 25);
        COMBAT_XP.put(EntityType.RAVAGER, 25);
        COMBAT_XP.put(EntityType.VEX, 5);
        COMBAT_XP.put(EntityType.SHULKER, 20);
        COMBAT_XP.put(EntityType.MAGMA_CUBE, 8);
        COMBAT_XP.put(EntityType.SLIME, 5);
        COMBAT_XP.put(EntityType.SILVERFISH, 3);
        COMBAT_XP.put(EntityType.ENDERMITE, 3);
        COMBAT_XP.put(EntityType.BREEZE, 20);
        COMBAT_XP.put(EntityType.BOGGED, 8);
    }

    private final JavaPlugin plugin;
    private final SkillManager skillManager;
    private final AuthManager authManager;
    private final JobManager jobManager;

    public SkillListener(JavaPlugin plugin, SkillManager skillManager,
                         AuthManager authManager, JobManager jobManager) {
        this.plugin = plugin;
        this.skillManager = skillManager;
        this.authManager = authManager;
        this.jobManager = jobManager;
    }

    private boolean canReward(Player player) {
        if (!authManager.isAuthenticated(player.getUniqueId())) return false;
        return WorldManager.getWorldGroup(player.getWorld()) == WorldManager.WorldGroup.SURVIVAL;
    }

    // ========== BLOCK BREAK — Mining, Excavation, Woodcutting, Farming XP + effects ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!canReward(player)) return;

        Block block = event.getBlock();
        Material type = block.getType();
        UUID uuid = player.getUniqueId();
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        boolean placed = jobManager.isPlacedBlock(block);

        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean hasSilkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH);

        // --- Mining ---
        Integer miningXp = MINING_XP.get(type);
        if (miningXp != null && !placed) {
            skillManager.addXp(player, SkillType.MINING, miningXp);

            int miningLevel = skillManager.getLevel(uuid, SkillType.MINING);

            // Haste effect
            int hasteLevel = SkillManager.getMiningHasteLevel(miningLevel);
            if (hasteLevel >= 0) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.HASTE, 100, hasteLevel, true, false, false));
            }

            // Double ore drops
            if (!hasSilkTouch && ORES.contains(type)) {
                double chance = SkillManager.getMiningDoubleDropChance(miningLevel);
                if (chance > 0 && ThreadLocalRandom.current().nextDouble() < chance) {
                    Material drop = ORE_DROP_MAP.get(type);
                    if (drop != null) {
                        block.getWorld().dropItemNaturally(loc, new ItemStack(drop));
                    }
                }
            }
            return;
        }

        // --- Excavation ---
        Integer digXp = DIG_XP.get(type);
        if (digXp != null && !placed) {
            skillManager.addXp(player, SkillType.EXCAVATION, digXp);

            int excLevel = skillManager.getLevel(uuid, SkillType.EXCAVATION);
            double chance = SkillManager.getExcavationDoubleDropChance(excLevel);
            if (chance > 0 && ThreadLocalRandom.current().nextDouble() < chance) {
                block.getWorld().dropItemNaturally(loc, new ItemStack(type));
            }
            return;
        }

        // --- Woodcutting ---
        if (LOG_BLOCKS.contains(type) && !placed) {
            int wcXp = (type == Material.CRIMSON_STEM || type == Material.WARPED_STEM) ? 7 : 5;
            skillManager.addXp(player, SkillType.WOODCUTTING, wcXp);

            int wcLevel = skillManager.getLevel(uuid, SkillType.WOODCUTTING);
            double chance = SkillManager.getWoodcuttingDoubleDropChance(wcLevel);
            if (chance > 0 && ThreadLocalRandom.current().nextDouble() < chance) {
                block.getWorld().dropItemNaturally(loc, new ItemStack(type));
            }
            return;
        }

        // --- Farming (ageable crops) ---
        if (CROP_BLOCKS.contains(type) && !placed) {
            if (block.getBlockData() instanceof Ageable ageable) {
                if (ageable.getAge() >= ageable.getMaximumAge()) {
                    int farmXp = (type == Material.NETHER_WART || type == Material.TORCHFLOWER_CROP
                            || type == Material.PITCHER_CROP) ? 5 :
                            (type == Material.BEETROOTS || type == Material.COCOA) ? 4 : 3;
                    skillManager.addXp(player, SkillType.FARMING, farmXp);

                    int farmLevel = skillManager.getLevel(uuid, SkillType.FARMING);

                    // Extra crop drops
                    double chance = SkillManager.getFarmingExtraDropChance(farmLevel);
                    if (chance > 0 && ThreadLocalRandom.current().nextDouble() < chance) {
                        Material drop = CROP_DROP_MAP.get(type);
                        if (drop != null) {
                            block.getWorld().dropItemNaturally(loc, new ItemStack(drop));
                        }
                    }

                    // Auto-replant
                    if (SkillManager.hasFarmingAutoReplant(farmLevel)) {
                        Material cropType = type;
                        org.bukkit.block.data.BlockData cropData = block.getBlockData().clone();
                        if (cropData instanceof Ageable replant) {
                            replant.setAge(0);
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                Block target = block.getWorld().getBlockAt(block.getLocation());
                                if (target.getType() == Material.AIR) {
                                    target.setType(cropType);
                                    target.setBlockData(replant);
                                }
                            });
                        }
                    }
                }
            }
            return;
        }

        // --- Farming (non-ageable) ---
        if (NON_AGEABLE_FARM.contains(type) && !placed) {
            skillManager.addXp(player, SkillType.FARMING, 2);

            int farmLevel = skillManager.getLevel(uuid, SkillType.FARMING);
            double chance = SkillManager.getFarmingExtraDropChance(farmLevel);
            if (chance > 0 && ThreadLocalRandom.current().nextDouble() < chance) {
                Material drop = CROP_DROP_MAP.get(type);
                if (drop != null) {
                    block.getWorld().dropItemNaturally(loc, new ItemStack(drop));
                }
            }
        }
    }

    // ========== ENTITY DEATH — Combat & Archery XP ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;
        if (!canReward(killer)) return;

        Integer xp = COMBAT_XP.get(entity.getType());
        int baseXp = xp != null ? xp : (isHostile(entity) ? 3 : 0);
        if (baseXp <= 0) return;

        EntityDamageEvent lastDamage = entity.getLastDamageCause();
        if (lastDamage instanceof EntityDamageByEntityEvent edbe) {
            Entity damager = edbe.getDamager();
            if (damager instanceof AbstractArrow || damager instanceof Trident) {
                skillManager.addXp(killer, SkillType.ARCHERY, baseXp);
                return;
            }
        }

        skillManager.addXp(killer, SkillType.COMBAT, baseXp);
    }

    // ========== DAMAGE — Combat/Archery damage bonus + lifesteal ==========

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();

        // --- Combat melee damage bonus + lifesteal ---
        if (damager instanceof Player player) {
            if (!canReward(player)) return;
            UUID uuid = player.getUniqueId();
            int combatLevel = skillManager.getLevel(uuid, SkillType.COMBAT);

            double dmgBonus = SkillManager.getCombatDamageBonus(combatLevel);
            if (dmgBonus > 0) {
                event.setDamage(event.getDamage() * (1.0 + dmgBonus));
            }

            double lifesteal = SkillManager.getCombatLifesteal(combatLevel);
            if (lifesteal > 0 && event.getEntity() instanceof LivingEntity) {
                double heal = event.getFinalDamage() * lifesteal;
                if (heal > 0) {
                    double newHealth = Math.min(player.getHealth() + heal,
                            player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
                    player.setHealth(newHealth);
                }
            }
            return;
        }

        // --- Archery damage bonus ---
        if (damager instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player player) {
            if (!canReward(player)) return;
            int archLevel = skillManager.getLevel(player.getUniqueId(), SkillType.ARCHERY);
            double bonus = SkillManager.getArcheryDamageBonus(archLevel);
            if (bonus > 0) {
                event.setDamage(event.getDamage() * (1.0 + bonus));
            }

            // Arrow recovery
            double recovery = SkillManager.getArcheryRecoveryChance(archLevel);
            if (recovery > 0 && ThreadLocalRandom.current().nextDouble() < recovery) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.getInventory().addItem(new ItemStack(Material.ARROW));
                });
            }
            return;
        }

        // --- Taming wolf damage bonus ---
        if (damager instanceof Wolf wolf && wolf.isTamed() && wolf.getOwner() instanceof Player owner) {
            if (!canReward(owner)) return;
            int tamingLevel = skillManager.getLevel(owner.getUniqueId(), SkillType.TAMING);
            double bonus = SkillManager.getTamingWolfDamageBonus(tamingLevel);
            if (bonus > 0) {
                event.setDamage(event.getDamage() * (1.0 + bonus));
            }
            return;
        }

        // --- Acrobatics dodge ---
        if (event.getEntity() instanceof Player victim) {
            if (!canReward(victim)) return;
            int acroLevel = skillManager.getLevel(victim.getUniqueId(), SkillType.ACROBATICS);
            double dodge = SkillManager.getAcrobaticsDodgeChance(acroLevel);
            if (dodge > 0 && ThreadLocalRandom.current().nextDouble() < dodge) {
                event.setCancelled(true);
                victim.sendActionBar(net.kyori.adventure.text.Component.text(
                        "★ Dodged!", TextColor.color(0x55FFFF)));
            }
        }
    }

    // ========== FALL DAMAGE — Acrobatics XP + reduction ==========

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!canReward(player)) return;

        int acroLevel = skillManager.getLevel(player.getUniqueId(), SkillType.ACROBATICS);

        // XP from surviving falls
        int xp = (int) Math.max(1, event.getDamage() * 2);
        skillManager.addXp(player, SkillType.ACROBATICS, xp);

        // Fall damage reduction
        double reduction = SkillManager.getAcrobaticsFallReduction(acroLevel);
        if (reduction > 0) {
            event.setDamage(event.getDamage() * (1.0 - reduction));
        }
    }

    // ========== FISHING — Fishing XP + double catch ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player player = event.getPlayer();
        if (!canReward(player)) return;

        Entity caught = event.getCaught();
        if (caught instanceof org.bukkit.entity.Item itemEntity) {
            ItemStack item = itemEntity.getItemStack();
            Material mat = item.getType();

            int xp = switch (mat) {
                case COD -> 5;
                case SALMON -> 8;
                case TROPICAL_FISH -> 15;
                case PUFFERFISH -> 20;
                case BOW, ENCHANTED_BOOK, FISHING_ROD, NAME_TAG, SADDLE, NAUTILUS_SHELL -> 30;
                default -> 2;
            };
            skillManager.addXp(player, SkillType.FISHING, xp);

            // Double catch
            int fishLevel = skillManager.getLevel(player.getUniqueId(), SkillType.FISHING);
            double chance = SkillManager.getFishingDoubleCatchChance(fishLevel);
            if (chance > 0 && ThreadLocalRandom.current().nextDouble() < chance) {
                player.getWorld().dropItemNaturally(
                        player.getLocation(), new ItemStack(mat, item.getAmount()));
            }
        }
    }

    // ========== BREWING — Alchemy XP + double brew ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewerClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory() instanceof BrewerInventory brew)) return;
        if (brew.getHolder() instanceof BrewingStand stand) {
            skillManager.trackBrewingStand(stand.getBlock(), player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        Block block = event.getBlock();
        UUID uuid = skillManager.getBrewingUser(block);
        if (uuid == null) return;

        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null || !player.isOnline()) return;
        if (!canReward(player)) return;

        int count = 0;
        for (ItemStack item : event.getResults()) {
            if (item != null && item.getType() != Material.AIR) count++;
        }
        if (count > 0) {
            skillManager.addXp(player, SkillType.ALCHEMY, 15 * count);
        }
    }

    // ========== TAMING — Taming XP ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        if (!canReward(player)) return;
        skillManager.addXp(player, SkillType.TAMING, 50);
    }

    // ========== FURNACE — Cooking XP + double food ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        if (!canReward(player)) return;

        Material type = event.getItemType();
        if (!FOOD_ITEMS.contains(type)) return;

        int amount = event.getItemAmount();
        skillManager.addXp(player, SkillType.COOKING, 3 * amount);

        // Double food
        int cookLevel = skillManager.getLevel(player.getUniqueId(), SkillType.COOKING);
        double chance = SkillManager.getCookingDoubleFoodChance(cookLevel);
        if (chance > 0) {
            int extra = 0;
            for (int i = 0; i < amount; i++) {
                if (ThreadLocalRandom.current().nextDouble() < chance) extra++;
            }
            if (extra > 0) {
                player.getWorld().dropItemNaturally(
                        player.getLocation(), new ItemStack(type, extra));
            }
        }
    }

    // ========== ANVIL — Repair XP ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        // We track repair XP when the player actually takes the result
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilUse(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != org.bukkit.event.inventory.InventoryType.ANVIL) return;
        if (event.getSlot() != 2) return; // result slot
        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;
        if (!canReward(player)) return;

        skillManager.addXp(player, SkillType.REPAIR, 20);
    }

    // --- Utility ---

    private boolean isHostile(Entity entity) {
        return entity instanceof org.bukkit.entity.Monster
                || entity instanceof org.bukkit.entity.Slime
                || entity instanceof org.bukkit.entity.Phantom
                || entity instanceof org.bukkit.entity.Ghast;
    }
}
