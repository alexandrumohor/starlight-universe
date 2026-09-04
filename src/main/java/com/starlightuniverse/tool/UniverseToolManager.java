package com.starlightuniverse.tool;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class UniverseToolManager implements Listener {

    private static final NamespacedKey TOOL_TAG = NamespacedKey.fromString("starlightuniverse:universe_tool");
    private static final TextColor LEGENDARY_COLOR = TextColor.color(0xFFD700);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor PURPLE = TextColor.color(0xAA00FF);

    private static final Set<Material> UNBREAKABLE = Set.of(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
            Material.BEDROCK, Material.BARRIER, Material.END_PORTAL,
            Material.END_PORTAL_FRAME, Material.NETHER_PORTAL,
            Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK, Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID, Material.JIGSAW,
            Material.WATER, Material.LAVA
    );

    private final JavaPlugin plugin;
    private final Set<UUID> processing = new HashSet<>();

    public UniverseToolManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack createTool(UniverseToolType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(type.getDisplayName(), LEGENDARY_COLOR)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Universe Tool", PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Breaks blocks in a 3x3x3 area", GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Legendary", LEGENDARY_COLOR)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(lore);

        meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);

        meta.setItemModel(NamespacedKey.fromString("starlight:universe_" + type.name().toLowerCase()));
        meta.getPersistentDataContainer().set(TOOL_TAG, PersistentDataType.STRING, type.name());
        meta.setUnbreakable(true);

        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createRandomTool() {
        UniverseToolType[] types = UniverseToolType.values();
        return createTool(types[ThreadLocalRandom.current().nextInt(types.length)]);
    }

    public boolean isUniverseTool(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(TOOL_TAG);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (processing.contains(uuid)) return;
        if (player.isSneaking()) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isUniverseTool(tool)) return;

        Block origin = event.getBlock();
        processing.add(uuid);
        try {
            breakArea(origin, player, tool);
        } finally {
            processing.remove(uuid);
        }
    }

    private void breakArea(Block origin, Player player, ItemStack tool) {
        Location dropLoc = origin.getLocation().add(0.5, 0.5, 0.5);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    Block target = origin.getRelative(dx, dy, dz);
                    if (UNBREAKABLE.contains(target.getType())) continue;
                    if (target.getType().getHardness() < 0) continue;

                    BlockBreakEvent breakEvent = new BlockBreakEvent(target, player);
                    Bukkit.getPluginManager().callEvent(breakEvent);
                    if (breakEvent.isCancelled()) continue;

                    if (breakEvent.isDropItems()) {
                        for (ItemStack drop : target.getDrops(tool, player)) {
                            origin.getWorld().dropItemNaturally(dropLoc, drop);
                        }
                        target.getWorld().spawnParticle(Particle.BLOCK,
                                target.getLocation().add(0.5, 0.5, 0.5),
                                10, 0.3, 0.3, 0.3, 0.05, target.getBlockData());
                    }
                    target.setType(Material.AIR);
                }
            }
        }
    }

    public enum UniverseToolType {
        PICKAXE("Universe Pickaxe", Material.NETHERITE_PICKAXE),
        AXE("Universe Axe", Material.NETHERITE_AXE),
        SHOVEL("Universe Shovel", Material.NETHERITE_SHOVEL),
        HOE("Universe Hoe", Material.NETHERITE_HOE);

        private final String displayName;
        private final Material material;

        UniverseToolType(String displayName, Material material) {
            this.displayName = displayName;
            this.material = material;
        }

        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
    }
}
