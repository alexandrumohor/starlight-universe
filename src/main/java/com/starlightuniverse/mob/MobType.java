package com.starlightuniverse.mob;

import org.bukkit.entity.EntityType;

public enum MobType {
    ZOMBIE(EntityType.ZOMBIE, "Zombie", 20, 3),
    SKELETON(EntityType.SKELETON, "Skeleton", 20, 2),
    SPIDER(EntityType.SPIDER, "Spider", 16, 2),
    CREEPER(EntityType.CREEPER, "Creeper", 20, 4),
    ENDERMAN(EntityType.ENDERMAN, "Enderman", 40, 7),
    WITCH(EntityType.WITCH, "Witch", 26, 3),
    BLAZE(EntityType.BLAZE, "Blaze", 20, 5),
    WITHER_SKELETON(EntityType.WITHER_SKELETON, "Wither Skeleton", 20, 5),
    PILLAGER(EntityType.PILLAGER, "Pillager", 24, 3),
    VINDICATOR(EntityType.VINDICATOR, "Vindicator", 24, 6),
    HUSK(EntityType.HUSK, "Husk", 20, 3),
    DROWNED(EntityType.DROWNED, "Drowned", 20, 3),
    STRAY(EntityType.STRAY, "Stray", 20, 2);

    public final EntityType entityType;
    public final String displayName;
    public final double baseHp;
    public final double baseDamage;

    MobType(EntityType entityType, String displayName, double baseHp, double baseDamage) {
        this.entityType = entityType;
        this.displayName = displayName;
        this.baseHp = baseHp;
        this.baseDamage = baseDamage;
    }

    public static final MobType[] ALL = values();
}
