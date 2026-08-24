package com.starlightuniverse.enchant;

import static com.starlightuniverse.enchant.EnchantRarity.*;
import static com.starlightuniverse.enchant.ItemTarget.*;

public enum CustomEnchant {

    // --- HELMET (10) ---
    STARGAZER("Stargazer", HELMET, 1, COMMON, "Grants night vision"),
    NEBULA_SIGHT("Nebula Sight", HELMET, 5, RARE, "Makes mobs glow through walls"),
    COSMIC_WISDOM("Cosmic Wisdom", HELMET, 10, UNCOMMON, "Increases XP gained"),
    ASTRAL_BREATH("Astral Breath", HELMET, 10, COMMON, "Extends underwater breathing"),
    SOLAR_CLARITY("Solar Clarity", HELMET, 3, EPIC, "Immunity to Blindness, Nausea and Darkness"),
    CONSTELLATION("Constellation", HELMET, 1, COMMON, "Shows weather and time on action bar"),
    CELESTIAL_MIND("Celestial Mind", HELMET, 10, EPIC, "Chance for double XP on kills"),
    METEOR_GUARD("Meteor Guard", HELMET, 10, UNCOMMON, "Reduces critical hit damage"),
    STELLAR_NOURISH("Stellar Nourish", HELMET, 5, RARE, "Slowly regenerates hunger"),
    ORBITAL_SCAN("Orbital Scan", HELMET, 5, UNCOMMON, "Shows mob health"),

    // --- CHESTPLATE (10) ---
    STAR_HEART("Star Heart", CHESTPLATE, 10, LEGENDARY, "Grants extra hearts"),
    SOLAR_FLARE("Solar Flare", CHESTPLATE, 5, EPIC, "Sets attackers on fire"),
    VOID_DODGE("Void Dodge", CHESTPLATE, 10, LEGENDARY, "Chance to dodge attacks"),
    GRAVITY_WELL("Gravity Well", CHESTPLATE, 10, UNCOMMON, "Reduces knockback taken"),
    COMET_FALL("Comet Fall", CHESTPLATE, 3, RARE, "Grants slow falling"),
    SUPERNOVA_BURST("Supernova Burst", CHESTPLATE, 5, EPIC, "Gain Strength when low HP"),
    DARK_MATTER("Dark Matter", CHESTPLATE, 10, RARE, "Flat damage reduction"),
    PULSAR_REGEN("Pulsar Regen", CHESTPLATE, 10, LEGENDARY, "Regenerates health over time"),
    NOVA_SHIELD("Nova Shield", CHESTPLATE, 5, UNCOMMON, "Reduces fire and lava damage"),
    STARBORN_ARMOR("Starborn Armor", CHESTPLATE, 10, COMMON, "Reduces armor durability loss"),

    // --- LEGGINGS (10) ---
    LIGHT_SPEED("Light Speed", LEGGINGS, 10, RARE, "Increases movement speed"),
    LUNAR_LEAP("Lunar Leap", LEGGINGS, 5, UNCOMMON, "Increases jump height"),
    ZERO_GRAVITY("Zero Gravity", LEGGINGS, 5, RARE, "Negates fall damage for a distance"),
    COSMIC_STRIDE("Cosmic Stride", LEGGINGS, 3, EPIC, "Immunity to Slowness"),
    PLASMA_SHIELD("Plasma Shield", LEGGINGS, 10, UNCOMMON, "Reduces explosion damage"),
    STAR_FUEL("Star Fuel", LEGGINGS, 10, COMMON, "Reduces hunger drain while sprinting"),
    NEBULA_WEAVE("Nebula Weave", LEGGINGS, 10, COMMON, "Reduces leggings durability loss"),
    ANTIVENOM_ORBIT("Antivenom Orbit", LEGGINGS, 5, UNCOMMON, "Reduces Poison damage"),
    ANCHOR_POINT("Anchor Point", LEGGINGS, 3, RARE, "Immunity to Levitation"),
    LAST_LIGHT("Last Light", LEGGINGS, 3, LEGENDARY, "Gain Resistance when very low HP"),

    // --- BOOTS (10) ---
    WARP_DRIVE("Warp Drive", BOOTS, 10, RARE, "Increases movement speed"),
    MAGMA_ORBIT("Magma Orbit", BOOTS, 3, LEGENDARY, "Walk on lava"),
    SOFT_LANDING("Soft Landing", BOOTS, 10, COMMON, "Reduces fall damage"),
    STAR_PATH("Star Path", BOOTS, 10, COMMON, "Bonus speed on paths"),
    LUNAR_SPRINGS("Lunar Springs", BOOTS, 5, UNCOMMON, "Enhanced auto-jump"),
    TIDE_LOCK("Tide Lock", BOOTS, 3, UNCOMMON, "Immunity to water currents"),
    METEOR_IMPACT("Meteor Impact", BOOTS, 10, EPIC, "AOE damage on landing from height"),
    DEEP_SPACE("Deep Space", BOOTS, 5, RARE, "Increases swim speed"),
    SOLAR_STEP("Solar Step", BOOTS, 3, COMMON, "Prevents farmland trampling"),
    STAR_MEND_BOOTS("Star Mend", BOOTS, 5, EPIC, "Boots self-repair over time"),

    // --- SWORD (10) ---
    STAR_DRAIN("Star Drain", SWORD, 10, LEGENDARY, "Steals health on hit"),
    COSMIC_BLEED("Cosmic Bleed", SWORD, 10, RARE, "Inflicts damage over time"),
    ECLIPSE_STRIKE("Eclipse Strike", SWORD, 5, EPIC, "Bonus damage when target is low HP"),
    FROST_NOVA("Frost Nova", SWORD, 5, UNCOMMON, "Applies Slowness on hit"),
    VENOM_STAR("Venom Star", SWORD, 10, UNCOMMON, "Applies Poison on hit"),
    THUNDERSTORM("Thunderstorm", SWORD, 3, EPIC, "Chance to strike lightning"),
    CONSTELLATION_CUT("Constellation Cut", SWORD, 5, RARE, "Chance to drop mob heads"),
    SOUL_HARVEST("Soul Harvest", SWORD, 10, COMMON, "Bonus XP on kills"),
    GRAVITY_DISARM("Gravity Disarm", SWORD, 5, LEGENDARY, "Chance to disarm target"),
    WITHER_STAR("Wither Star", SWORD, 5, RARE, "Applies Wither on hit"),

    // --- PICKAXE (10) ---
    SOLAR_SMELT("Solar Smelt", PICKAXE, 1, UNCOMMON, "Auto-smelts mined ores"),
    STAR_VEIN("Star Vein", PICKAXE, 5, EPIC, "Mines connected same blocks"),
    COSMIC_FORTUNE("Cosmic Fortune", PICKAXE, 10, COMMON, "Bonus XP from ores"),
    BLACK_HOLE("Black Hole", PICKAXE, 1, RARE, "Drops go directly to inventory"),
    ASTEROID_HASTE("Asteroid Haste", PICKAXE, 5, RARE, "Grants Haste while mining"),
    SUPERNOVA_MINE("Supernova Mine", PICKAXE, 3, LEGENDARY, "Mines in a 3x3 or 5x5 area"),
    MAGNETAR("Magnetar", PICKAXE, 5, UNCOMMON, "Attracts nearby drops"),
    PHOTON_BEAM("Photon Beam", PICKAXE, 3, EPIC, "Mines blocks in a line"),
    STARDUST_FINDER("Stardust Finder", PICKAXE, 10, RARE, "Chance for bonus gems"),
    ENERGY_CORE("Energy Core", PICKAXE, 1, LEGENDARY, "Uses XP instead of durability"),

    // --- AXE (10) ---
    METEOR_TIMBER("Meteor Timber", AXE, 5, RARE, "Fells multiple logs at once"),
    SOLAR_CHOP("Solar Chop", AXE, 5, COMMON, "Increases chopping speed"),
    STAR_SEED("Star Seed", AXE, 1, UNCOMMON, "Auto-plants saplings on tree fell"),
    COSMIC_RAGE("Cosmic Rage", AXE, 10, EPIC, "Consecutive hits deal more damage"),
    ORBIT_SHRED("Orbit Shred", AXE, 10, RARE, "Reduces enemy armor effectiveness"),
    NOVA_CLEAVE("Nova Cleave", AXE, 5, EPIC, "Hits nearby enemies"),
    MAGNETAR_PULL("Magnetar Pull", AXE, 5, UNCOMMON, "Attracts nearby drops"),
    STELLAR_EDGE("Stellar Edge", AXE, 5, RARE, "Chance for mob head drops"),
    PLASMA_DRAIN("Plasma Drain", AXE, 5, LEGENDARY, "Heals on combat hits"),
    GALAXY_HARVEST("Galaxy Harvest", AXE, 10, COMMON, "Bonus drops from leaves"),

    // --- SHOVEL (10) ---
    CRATER_DIG("Crater Dig", SHOVEL, 5, EPIC, "Digs in a radius"),
    SOLAR_TOUCH("Solar Touch", SHOVEL, 1, UNCOMMON, "Transforms blocks on dig"),
    PHOTON_TUNNEL("Photon Tunnel", SHOVEL, 3, RARE, "Digs blocks in a line"),
    STAR_HUNTER("Star Hunter", SHOVEL, 10, RARE, "Chance for rare loot"),
    NEBULA_MEND_SHOVEL("Nebula Mend", SHOVEL, 5, EPIC, "Shovel self-repairs over time"),
    VOID_POCKET("Void Pocket", SHOVEL, 1, UNCOMMON, "Drops go to inventory"),
    ORBIT_PATH("Orbit Path", SHOVEL, 3, COMMON, "Creates paths in a radius"),
    WARP_DIG("Warp Dig", SHOVEL, 10, COMMON, "Increases digging speed"),
    DUST_STORM("Dust Storm", SHOVEL, 3, RARE, "Chance to create water source"),
    GRAVITY_FLATTEN("Gravity Flatten", SHOVEL, 5, UNCOMMON, "Levels terrain in a radius"),

    // --- HOE (10) ---
    STAR_HARVEST("Star Harvest", HOE, 5, EPIC, "Auto-harvest and replant crops"),
    SOLAR_GROWTH("Solar Growth", HOE, 10, RARE, "Crops grow faster nearby"),
    COSMIC_SEEDS("Cosmic Seeds", HOE, 5, COMMON, "Bonus seed drops"),
    NEBULA_RAIN("Nebula Rain", HOE, 3, LEGENDARY, "Hydrates farmland without water"),
    STELLAR_YIELD("Stellar Yield", HOE, 5, RARE, "Bonus crop yield"),
    SUNLIGHT_TOUCH("Sunlight Touch", HOE, 3, EPIC, "Bonemeal effect on right-click"),
    CONSTELLATION_GUARD("Constellation Guard", HOE, 5, UNCOMMON, "Prevents mob spawns nearby"),
    PULSAR_MEND("Pulsar Mend", HOE, 5, UNCOMMON, "Hoe self-repairs over time"),
    GALAXY_BLOOM("Galaxy Bloom", HOE, 10, COMMON, "Bonus flower drops"),
    STAR_FENCE("Star Fence", HOE, 3, RARE, "Crops immune to mob trampling"),

    // --- BOW (10) ---
    HOMING_STAR("Homing Star", BOW, 3, LEGENDARY, "Arrows track targets"),
    METEOR_ARROW("Meteor Arrow", BOW, 5, EPIC, "Arrows explode on impact"),
    VENOM_COMET("Venom Comet", BOW, 5, UNCOMMON, "Arrows apply Poison"),
    SOLAR_DRAW("Solar Draw", BOW, 5, COMMON, "Reduces bow draw time"),
    SNIPER_NOVA("Sniper Nova", BOW, 10, RARE, "Bonus damage at long range"),
    STAR_BURST("Star Burst", BOW, 3, EPIC, "Shoots multiple arrows"),
    PIERCING_LIGHT("Piercing Light", BOW, 5, RARE, "Arrows pierce through entities"),
    THUNDER_BOLT("Thunder Bolt", BOW, 3, LEGENDARY, "Chance to strike lightning"),
    ORBIT_RETURN_BOW("Orbit Return", BOW, 10, COMMON, "Chance to recover arrows"),
    GRAVITY_TRAP("Gravity Trap", BOW, 5, UNCOMMON, "Applies Slowness and Fatigue"),

    // --- MACE (10) ---
    EARTHQUAKE_STAR("Earthquake Star", MACE, 5, EPIC, "AOE knockback around impact"),
    NEUTRON_CRUSH("Neutron Crush", MACE, 10, RARE, "Bonus damage vs armored targets"),
    PULSAR_STUN("Pulsar Stun", MACE, 5, RARE, "Chance to stun with Slowness and Blindness"),
    GRAVITON_PULL("Graviton Pull", MACE, 5, EPIC, "Pulls enemies toward you"),
    SOLAR_BREAKER("Solar Breaker", MACE, 5, LEGENDARY, "Chance to disable shields"),
    COSMIC_MOMENTUM("Cosmic Momentum", MACE, 10, RARE, "Consecutive hits deal more damage"),
    METEOR_FORCE("Meteor Force", MACE, 10, UNCOMMON, "Increases knockback"),
    STAR_CRUSH("Star Crush", MACE, 5, EPIC, "Bonus damage based on enemy armor"),
    SUPERNOVA_CRY("Supernova Cry", MACE, 3, LEGENDARY, "Gain Strength on kills"),
    DARK_MATTER_SKIN("Dark Matter Skin", MACE, 5, UNCOMMON, "Gain Resistance after being hit"),

    // --- TRIDENT (10) ---
    POSEIDON_STAR("Poseidon Star", TRIDENT, 10, UNCOMMON, "Bonus damage underwater"),
    TIDAL_NOVA("Tidal Nova", TRIDENT, 5, EPIC, "Wave knockback on hit"),
    CHAIN_LIGHTNING("Chain Lightning", TRIDENT, 5, LEGENDARY, "Lightning bounces to nearby enemies"),
    WHIRLPOOL_STAR("Whirlpool Star", TRIDENT, 3, EPIC, "Creates a vortex pulling enemies"),
    ORBIT_RETURN_TRIDENT("Orbit Return", TRIDENT, 5, COMMON, "Trident returns faster"),
    DEEP_STAR("Deep Star", TRIDENT, 3, RARE, "Grants Dolphin's Grace"),
    FROZEN_COMET("Frozen Comet", TRIDENT, 5, RARE, "Applies Slowness and freezing"),
    STELLAR_TIDE("Stellar Tide", TRIDENT, 5, UNCOMMON, "Heals in water on hit"),
    UNIVERSAL_IMPALE("Universal Impale", TRIDENT, 10, LEGENDARY, "Bonus damage vs ALL mobs"),
    PIERCING_STAR("Piercing Star", TRIDENT, 3, RARE, "Trident pierces through entities"),

    // --- SPEAR (Custom Item) (10) ---
    LONG_ORBIT("Long Orbit", SPEAR, 5, RARE, "Increases attack range"),
    JAVELIN_STAR("Javelin Star", SPEAR, 5, UNCOMMON, "Increases throw distance"),
    STARLIGHT_PIERCE("Starlight Pierce", SPEAR, 10, RARE, "Increases base damage"),
    COSMIC_THRUST("Cosmic Thrust", SPEAR, 5, UNCOMMON, "Reduces attack cooldown"),
    NEBULA_GUARD("Nebula Guard", SPEAR, 3, EPIC, "Grants Resistance while stationary"),
    BARBED_STAR("Barbed Star", SPEAR, 5, COMMON, "Inflicts bleed damage"),
    SOLAR_SWEEP("Solar Sweep", SPEAR, 5, EPIC, "Sweeps in a line"),
    VOID_SKEWER("Void Skewer", SPEAR, 3, LEGENDARY, "Ignores a portion of armor"),
    COMET_RETURN("Comet Return", SPEAR, 3, RARE, "Chance for thrown spear to return"),
    SENTINEL_STAR("Sentinel Star", SPEAR, 5, UNCOMMON, "Bonus damage vs approaching mobs");

    private final String displayName;
    private final ItemTarget target;
    private final int maxLevel;
    private final EnchantRarity rarity;
    private final String description;

    CustomEnchant(String displayName, ItemTarget target, int maxLevel, EnchantRarity rarity, String description) {
        this.displayName = displayName;
        this.target = target;
        this.maxLevel = maxLevel;
        this.rarity = rarity;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public ItemTarget getTarget() { return target; }
    public int getMaxLevel() { return maxLevel; }
    public EnchantRarity getRarity() { return rarity; }
    public String getDescription() { return description; }

    public static CustomEnchant byName(String name) {
        for (CustomEnchant e : values()) {
            if (e.name().equalsIgnoreCase(name) || e.displayName.replace(" ", "_").equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }
}
