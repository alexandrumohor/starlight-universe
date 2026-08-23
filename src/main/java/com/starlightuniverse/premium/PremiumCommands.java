package com.starlightuniverse.premium;

import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.home.HomeManager;
import com.starlightuniverse.util.Msg;
import com.starlightuniverse.world.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public final class PremiumCommands {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);

    private PremiumCommands() {}

    public static List<Command> create(PremiumManager pm) {
        List<Command> cmds = new ArrayList<>();

        // ==================== METEOR (level >= 1) ====================

        cmds.add(perk("hat", "Put held item on your head", 1, pm, (p, args) -> {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) { Msg.error(p, "Hold an item to wear!"); return; }
            ItemStack helmet = p.getInventory().getHelmet();
            p.getInventory().setHelmet(hand.clone());
            p.getInventory().setItemInMainHand(helmet != null ? helmet : new ItemStack(Material.AIR));
            Msg.success(p, "Item placed on your head!");
        }));

        cmds.add(perk("feed", "Fill your hunger", 1, pm, (p, args) -> {
            p.setFoodLevel(20);
            p.setSaturation(5.0f);
            Msg.success(p, "You have been fed!");
        }));

        cmds.add(perk("craftingtable", "Open a portable crafting table", 1, pm, (p, args) -> {
            p.openWorkbench(null, true);
            Msg.success(p, "Crafting table opened!");
        }));

        cmds.add(perk("trash", "Open a trash disposal", 1, pm, (p, args) -> pm.openTrashGui(p)));

        cmds.add(perk("disposal", "Open a trash disposal", 1, pm, (p, args) -> pm.openTrashGui(p)));

        cmds.add(perk("recipe", "View crafting recipes", 1, pm, (p, args) -> {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                Msg.info(p, "Hold an item to see its recipe, or check the recipe book (green book icon).");
            } else {
                Msg.info(p, "Check the recipe book for " + formatMaterial(hand.getType().name()) + ".");
            }
        }));

        cmds.add(perk("sit", "Sit down", 1, pm, (p, args) -> pm.sitDown(p)));

        cmds.add(perk("repair", "Repair held item for $2,000", 1, pm, (p, args) -> {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) { Msg.error(p, "Hold an item to repair!"); return; }
            ItemMeta meta = hand.getItemMeta();
            if (!(meta instanceof Damageable dmg) || dmg.getDamage() == 0) { Msg.error(p, "This item doesn't need repair!"); return; }
            if (!pm.getEconomy().removeMoney(p.getUniqueId(), 2_000)) { Msg.error(p, "Not enough Money! Need $2,000"); return; }
            dmg.setDamage(0);
            hand.setItemMeta(meta);
            Msg.success(p, "Item repaired for $2,000!");
        }));

        cmds.add(perk("condense", "Condense items into blocks", 1, pm, (p, args) -> {
            int condensed = pm.condenseInventory(p);
            if (condensed > 0) Msg.success(p, "Condensed " + condensed + " blocks!");
            else Msg.info(p, "Nothing to condense.");
        }));

        cmds.add(perk("near", "Show nearby players", 1, pm, (p, args) -> {
            List<String> nearby = new ArrayList<>();
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other == p) continue;
                if (other.getWorld().equals(p.getWorld())) {
                    double dist = p.getLocation().distance(other.getLocation());
                    if (dist <= 100) nearby.add(other.getName() + " (" + (int) dist + "m)");
                }
            }
            if (nearby.isEmpty()) Msg.info(p, "No players nearby.");
            else Msg.info(p, "Nearby: " + String.join(", ", nearby));
        }));

        cmds.add(perkTab("ptime", "Set personal time", 1, pm, (p, args) -> {
            if (args.length < 1) { Msg.error(p, "Usage: /ptime <day|night|reset>"); return; }
            switch (args[0].toLowerCase()) {
                case "day" -> { p.setPlayerTime(6000, false); Msg.success(p, "Personal time set to day."); }
                case "night" -> { p.setPlayerTime(18000, false); Msg.success(p, "Personal time set to night."); }
                case "reset" -> { p.resetPlayerTime(); Msg.success(p, "Personal time reset."); }
                default -> Msg.error(p, "Usage: /ptime <day|night|reset>");
            }
        }, (s, args) -> args.length == 1 ? filter(List.of("day", "night", "reset"), args[0]) : List.of()));

        cmds.add(perk("colors", "Show hex color chart", 1, pm, (p, args) -> {
            p.sendMessage(Component.text("[SU] Hex Color Chart:", GOLD));
            String[][] colors = {
                    {"#FF0000", "Red"}, {"#FF5555", "Light Red"}, {"#FF8800", "Orange"}, {"#FFAA00", "Gold"},
                    {"#FFFF00", "Yellow"}, {"#FFFF55", "Light Yellow"}, {"#55FF55", "Green"}, {"#00CC00", "Dark Green"},
                    {"#55FFFF", "Cyan"}, {"#5555FF", "Blue"}, {"#AA00AA", "Purple"}, {"#FF55FF", "Pink"},
                    {"#FFFFFF", "White"}, {"#AAAAAA", "Gray"}, {"#555555", "Dark Gray"}
            };
            for (String[] c : colors) {
                p.sendMessage(Component.text("  " + c[0] + " - ", GRAY)
                        .append(Component.text(c[1], TextColor.fromHexString(c[0]))));
            }
        }));

        // ==================== COMET (level >= 2) ====================

        cmds.add(perk("anvil", "Open a portable anvil", 2, pm, (p, args) -> {
            p.openAnvil(p.getLocation(), true);
            Msg.success(p, "Anvil opened!");
        }));

        cmds.add(perk("heal", "Heal to full health", 2, pm, (p, args) -> {
            p.setHealth(p.getMaxHealth());
            p.setFireTicks(0);
            for (PotionEffect effect : p.getActivePotionEffects()) {
                if (effect.getType() == PotionEffectType.POISON || effect.getType() == PotionEffectType.WITHER) {
                    p.removePotionEffect(effect.getType());
                }
            }
            Msg.success(p, "You have been healed!");
        }));

        cmds.add(perk("trail", "Manage particle trails", 2, pm, (p, args) -> pm.openTrailGui(p)));

        cmds.add(perk("back", "Return to last death location", 2, pm, (p, args) -> {
            Location loc = pm.getLastDeath(p.getUniqueId());
            if (loc == null) { Msg.error(p, "No death location found!"); return; }
            p.teleport(loc);
            Msg.success(p, "Teleported to your last death location!");
        }));

        cmds.add(perkTab("seen", "Check when a player was last online", 2, pm, (p, args) -> {
            if (args.length < 1) { Msg.error(p, "Usage: /seen <player>"); return; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null && target.isOnline()) {
                Msg.info(p, args[0] + " is currently online!");
                return;
            }
            pm.getDb().queryAsync(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT last_active FROM su_players WHERE username = ?")) {
                    ps.setString(1, args[0].toLowerCase());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getString("last_active");
                    }
                }
                return null;
            }).thenAccept(lastActive -> Bukkit.getScheduler().runTask(pm.getPlugin(), () -> {
                if (!p.isOnline()) return;
                if (lastActive == null) Msg.error(p, "Player not found!");
                else Msg.info(p, args[0] + " was last seen: " + lastActive);
            }));
        }, (s, args) -> args.length == 1 ? playerNames(args[0]) : List.of()));

        cmds.add(perk("lay", "Lay down", 2, pm, (p, args) -> {
            if (p.isSwimming()) {
                p.setSwimming(false);
                Msg.success(p, "You stood up!");
            } else {
                p.setSwimming(true);
                Msg.success(p, "You are now laying down!");
            }
        }));

        cmds.add(perk("loom", "Open a portable loom", 2, pm, (p, args) -> {
            p.openLoom(p.getLocation(), true);
            Msg.success(p, "Loom opened!");
        }));

        cmds.add(perk("cartographytable", "Open a cartography table", 2, pm, (p, args) -> {
            p.openCartographyTable(p.getLocation(), true);
            Msg.success(p, "Cartography table opened!");
        }));

        cmds.add(perkTab("compass", "Point compass to a player", 2, pm, (p, args) -> {
            if (args.length < 1) { Msg.error(p, "Usage: /compass <player>"); return; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) { Msg.error(p, "Player not found!"); return; }
            if (!target.getWorld().equals(p.getWorld())) { Msg.error(p, "Player is in another world!"); return; }
            p.setCompassTarget(target.getLocation());
            Msg.success(p, "Compass pointing to " + target.getName() + "!");
        }, (s, args) -> args.length == 1 ? playerNames(args[0]) : List.of()));

        cmds.add(perk("firework", "Launch a firework", 2, pm, (p, args) -> {
            Location loc = p.getLocation();
            p.getWorld().spawn(loc, org.bukkit.entity.Firework.class, fw -> {
                org.bukkit.inventory.meta.FireworkMeta meta = fw.getFireworkMeta();
                meta.setPower(1);
                meta.addEffect(org.bukkit.FireworkEffect.builder()
                        .withColor(Color.YELLOW, Color.ORANGE)
                        .withFade(Color.RED)
                        .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                        .trail(true)
                        .build());
                fw.setFireworkMeta(meta);
            });
            Msg.success(p, "Firework launched!");
        }));

        cmds.add(perkTab("pweather", "Set personal weather", 2, pm, (p, args) -> {
            if (args.length < 1) { Msg.error(p, "Usage: /pweather <clear|rain|reset>"); return; }
            switch (args[0].toLowerCase()) {
                case "clear" -> { p.setPlayerWeather(WeatherType.CLEAR); Msg.success(p, "Personal weather set to clear."); }
                case "rain" -> { p.setPlayerWeather(WeatherType.DOWNFALL); Msg.success(p, "Personal weather set to rain."); }
                case "reset" -> { p.resetPlayerWeather(); Msg.success(p, "Personal weather reset."); }
                default -> Msg.error(p, "Usage: /pweather <clear|rain|reset>");
            }
        }, (s, args) -> args.length == 1 ? filter(List.of("clear", "rain", "reset"), args[0]) : List.of()));

        cmds.add(perk("autosort", "Sort your inventory", 2, pm, (p, args) -> {
            pm.sortInventory(p);
            Msg.success(p, "Inventory sorted!");
        }));

        cmds.add(perk("bottle", "Store XP levels as bottles", 2, pm, (p, args) -> {
            int levels = p.getLevel();
            if (levels < 1) { Msg.error(p, "You don't have any XP levels!"); return; }
            int bottles = Math.min(levels, 64);
            p.setLevel(levels - bottles);
            HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(
                    new ItemStack(Material.EXPERIENCE_BOTTLE, bottles));
            overflow.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            Msg.success(p, "Stored " + bottles + " XP levels as bottles!");
        }));

        // ==================== NEBULA (level >= 3) ====================

        cmds.add(perk("grindstone", "Open a portable grindstone", 3, pm, (p, args) -> {
            p.openGrindstone(p.getLocation(), true);
            Msg.success(p, "Grindstone opened!");
        }));

        cmds.add(perk("smithingtable", "Open a portable smithing table", 3, pm, (p, args) -> {
            p.openSmithingTable(p.getLocation(), true);
            Msg.success(p, "Smithing table opened!");
        }));

        cmds.add(perk("xpbottle", "Store XP levels as bottles", 3, pm, (p, args) -> {
            int levels = p.getLevel();
            if (levels < 1) { Msg.error(p, "You don't have any XP levels!"); return; }
            int bottles = Math.min(levels, 64);
            p.setLevel(levels - bottles);
            HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(
                    new ItemStack(Material.EXPERIENCE_BOTTLE, bottles));
            overflow.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            Msg.success(p, "Stored " + bottles + " XP levels as bottles!");
        }));

        cmds.add(perk("stonecutter", "Open a portable stonecutter", 3, pm, (p, args) -> {
            p.openStonecutter(p.getLocation(), true);
            Msg.success(p, "Stonecutter opened!");
        }));

        cmds.add(perk("uncondense", "Uncondense blocks into items", 3, pm, (p, args) -> {
            int uncondensed = pm.uncondenseInventory(p);
            if (uncondensed > 0) Msg.success(p, "Uncondensed " + uncondensed + " blocks!");
            else Msg.info(p, "Nothing to uncondense.");
        }));

        cmds.add(perk("iteminfo", "Show info about held item", 3, pm, (p, args) -> {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) { Msg.error(p, "Hold an item!"); return; }
            p.sendMessage(Component.text("[SU] Item Info:", GOLD));
            p.sendMessage(Component.text("  Type: " + hand.getType().name(), GRAY));
            p.sendMessage(Component.text("  Amount: " + hand.getAmount(), GRAY));
            p.sendMessage(Component.text("  Max Stack: " + hand.getMaxStackSize(), GRAY));
            if (hand.getItemMeta() instanceof Damageable dmg) {
                int maxDur = hand.getType().getMaxDurability();
                if (maxDur > 0)
                    p.sendMessage(Component.text("  Durability: " + (maxDur - dmg.getDamage()) + "/" + maxDur, GRAY));
            }
            if (!hand.getEnchantments().isEmpty()) {
                p.sendMessage(Component.text("  Enchantments: " + hand.getEnchantments().size(), GRAY));
            }
        }));

        cmds.add(perk("stack", "Merge similar item stacks", 3, pm, (p, args) -> {
            pm.stackItems(p);
            Msg.success(p, "Items stacked!");
        }));

        cmds.add(perk("craft", "Open a portable crafting table", 3, pm, (p, args) -> {
            p.openWorkbench(null, true);
            Msg.success(p, "Crafting table opened!");
        }));

        cmds.add(perk("fly", "Toggle flight", 3, pm, (p, args) -> {
            PremiumRank rank = pm.getPlayerRank(p.getUniqueId());
            WorldManager.WorldGroup group = WorldManager.getWorldGroup(p.getWorld());

            if (rank.canFlyProtections()) {
                if (group == WorldManager.WorldGroup.LOBBY || group == WorldManager.WorldGroup.SURVIVAL) {
                    pm.toggleFly(p);
                } else {
                    Msg.error(p, "You can't fly here!");
                }
            } else if (rank.canFlyLobby()) {
                if (group == WorldManager.WorldGroup.LOBBY) {
                    pm.toggleFly(p);
                } else {
                    Msg.error(p, "You can only fly in the lobby!");
                }
            } else {
                Msg.error(p, "You need Nebula rank or higher to fly!");
            }
        }));

        // ==================== SUPERNOVA (level >= 4) ====================

        cmds.add(perk("bodyglow", "Toggle glowing effect", 4, pm, (p, args) -> {
            p.setGlowing(!p.isGlowing());
            Msg.success(p, "Body glow " + (p.isGlowing() ? "enabled" : "disabled") + "!");
        }));

        cmds.add(perk("enderchest", "Open your ender chest", 4, pm, (p, args) -> {
            p.openInventory(p.getEnderChest());
            Msg.success(p, "Ender chest opened!");
        }));

        cmds.add(perk("smelt", "Smelt the held item", 4, pm, (p, args) -> {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) { Msg.error(p, "Hold an item to smelt!"); return; }
            Material result = pm.getSmeltResult(hand.getType());
            if (result == null) { Msg.error(p, "This item can't be smelted!"); return; }
            int amount = hand.getAmount();
            p.getInventory().setItemInMainHand(new ItemStack(result, amount));
            Msg.success(p, "Smelted " + amount + "x " + formatMaterial(hand.getType().name()) + "!");
        }));

        cmds.add(perkTab("skull", "Get a player's head", 4, pm, (p, args) -> {
            if (args.length < 1) { Msg.error(p, "Usage: /skull <player>"); return; }
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            meta.setOwningPlayer(target);
            meta.displayName(Component.text(args[0] + "'s Head", YELLOW).decoration(TextDecoration.ITALIC, false));
            skull.setItemMeta(meta);
            HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(skull);
            overflow.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            Msg.success(p, "Received " + args[0] + "'s skull!");
        }, (s, args) -> args.length == 1 ? playerNames(args[0]) : List.of()));

        cmds.add(perk("nightvision", "Toggle night vision", 4, pm, (p, args) -> {
            if (p.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                p.removePotionEffect(PotionEffectType.NIGHT_VISION);
                Msg.success(p, "Night vision disabled.");
            } else {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, true));
                Msg.success(p, "Night vision enabled!");
            }
        }));

        cmds.add(perkTab("speed", "Set movement speed", 4, pm, (p, args) -> {
            if (args.length < 1) { Msg.error(p, "Usage: /speed <1-5>"); return; }
            int level;
            try { level = Integer.parseInt(args[0]); } catch (NumberFormatException e) { Msg.error(p, "Invalid number!"); return; }
            if (level < 1 || level > 5) { Msg.error(p, "Speed must be 1-5!"); return; }
            float speed = 0.2f * level;
            p.setWalkSpeed(Math.min(speed, 1.0f));
            p.setFlySpeed(Math.min(speed / 2f, 1.0f));
            Msg.success(p, "Speed set to " + level + "!");
        }, (s, args) -> args.length == 1 ? List.of("1", "2", "3", "4", "5") : List.of()));

        cmds.add(perk("hdb", "Open head database", 4, pm, (p, args) -> {
            openHeadDatabase(pm, p);
        }));

        cmds.add(perk("fix", "Repair held item (30min cooldown)", 4, pm, (p, args) -> {
            if (pm.isOnCooldown(p.getUniqueId(), "fix")) {
                long remaining = pm.getCooldownRemaining(p.getUniqueId(), "fix") / 1000;
                Msg.error(p, "Cooldown: " + formatTime(remaining) + " remaining!");
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) { Msg.error(p, "Hold an item to repair!"); return; }
            ItemMeta meta = hand.getItemMeta();
            if (!(meta instanceof Damageable dmg) || dmg.getDamage() == 0) { Msg.error(p, "This item doesn't need repair!"); return; }
            dmg.setDamage(0);
            hand.setItemMeta(meta);
            pm.setCooldown(p.getUniqueId(), "fix", 30 * 60 * 1000L);
            Msg.success(p, "Item repaired!");
        }));

        cmds.add(perk("top", "Teleport to the highest block", 4, pm, (p, args) -> {
            Location loc = p.getLocation();
            int highestY = p.getWorld().getHighestBlockYAt(loc);
            p.teleport(new Location(p.getWorld(), loc.getX(), highestY + 1, loc.getZ(), loc.getYaw(), loc.getPitch()));
            Msg.success(p, "Teleported to the top!");
        }));

        cmds.add(perk("jump", "Teleport where you're looking", 4, pm, (p, args) -> {
            RayTraceResult result = p.rayTraceBlocks(100);
            if (result == null || result.getHitBlock() == null) { Msg.error(p, "No block in sight!"); return; }
            Location target = result.getHitBlock().getLocation().add(0.5, 1, 0.5);
            target.setYaw(p.getLocation().getYaw());
            target.setPitch(p.getLocation().getPitch());
            p.teleport(target);
            Msg.success(p, "Jumped!");
        }));

        // ==================== GALAXY (level >= 5) ====================

        cmds.add(perk("etable", "Open a portable enchanting table", 5, pm, (p, args) -> {
            p.openEnchanting(p.getLocation(), true);
            Msg.success(p, "Enchanting table opened!");
        }));

        cmds.add(perk("disenchant", "Remove enchantments from held item", 5, pm, (p, args) -> {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) { Msg.error(p, "Hold an item!"); return; }
            Map<Enchantment, Integer> enchants = hand.getEnchantments();
            if (enchants.isEmpty()) { Msg.error(p, "This item has no enchantments!"); return; }
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                hand.removeEnchantment(entry.getKey());
                ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
                EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
                meta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                book.setItemMeta(meta);
                HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(book);
                overflow.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
            }
            Msg.success(p, "Removed " + enchants.size() + " enchantment(s)!");
        }));

        cmds.add(perk("fixall", "Repair all items (1h cooldown)", 5, pm, (p, args) -> {
            if (pm.isOnCooldown(p.getUniqueId(), "fixall")) {
                long remaining = pm.getCooldownRemaining(p.getUniqueId(), "fixall") / 1000;
                Msg.error(p, "Cooldown: " + formatTime(remaining) + " remaining!");
                return;
            }
            int repaired = 0;
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && item.getItemMeta() instanceof Damageable dmg && dmg.getDamage() > 0) {
                    dmg.setDamage(0);
                    item.setItemMeta((ItemMeta) dmg);
                    repaired++;
                }
            }
            for (ItemStack item : p.getInventory().getArmorContents()) {
                if (item != null && item.getItemMeta() instanceof Damageable dmg && dmg.getDamage() > 0) {
                    dmg.setDamage(0);
                    item.setItemMeta((ItemMeta) dmg);
                    repaired++;
                }
            }
            if (repaired == 0) { Msg.info(p, "Nothing to repair."); return; }
            pm.setCooldown(p.getUniqueId(), "fixall", 60 * 60 * 1000L);
            Msg.success(p, "Repaired " + repaired + " items!");
        }));

        cmds.add(perkTab("rename", "Rename held item", 5, pm, (p, args) -> {
            if (args.length < 1) { Msg.error(p, "Usage: /rename <name>"); return; }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) { Msg.error(p, "Hold an item!"); return; }
            String name = String.join(" ", args);
            ItemMeta meta = hand.getItemMeta();
            meta.displayName(Component.text(name, GOLD).decoration(TextDecoration.ITALIC, false));
            hand.setItemMeta(meta);
            Msg.success(p, "Item renamed to \"" + name + "\"!");
        }, null));

        cmds.add(perkTab("lore", "Set item lore", 5, pm, (p, args) -> {
            if (args.length < 1) { Msg.error(p, "Usage: /lore <add|set|clear> [text]"); return; }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) { Msg.error(p, "Hold an item!"); return; }
            ItemMeta meta = hand.getItemMeta();
            switch (args[0].toLowerCase()) {
                case "clear" -> {
                    meta.lore(List.of());
                    hand.setItemMeta(meta);
                    Msg.success(p, "Lore cleared!");
                }
                case "add" -> {
                    if (args.length < 2) { Msg.error(p, "Usage: /lore add <text>"); return; }
                    String text = joinArgs(args, 1);
                    List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                    lore.add(Component.text(text, GRAY).decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                    hand.setItemMeta(meta);
                    Msg.success(p, "Lore line added!");
                }
                case "set" -> {
                    if (args.length < 2) { Msg.error(p, "Usage: /lore set <text>"); return; }
                    String text = joinArgs(args, 1);
                    meta.lore(List.of(Component.text(text, GRAY).decoration(TextDecoration.ITALIC, false)));
                    hand.setItemMeta(meta);
                    Msg.success(p, "Lore set!");
                }
                default -> Msg.error(p, "Usage: /lore <add|set|clear> [text]");
            }
        }, (s, args) -> args.length == 1 ? filter(List.of("add", "set", "clear"), args[0]) : List.of()));

        cmds.add(perk("unbreakable", "Toggle unbreakable on held item (24h cooldown)", 5, pm, (p, args) -> {
            if (pm.isOnCooldown(p.getUniqueId(), "unbreakable")) {
                long remaining = pm.getCooldownRemaining(p.getUniqueId(), "unbreakable") / 1000;
                Msg.error(p, "Cooldown: " + formatTime(remaining) + " remaining!");
                return;
            }
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) { Msg.error(p, "Hold an item!"); return; }
            ItemMeta meta = hand.getItemMeta();
            boolean unbreakable = !meta.isUnbreakable();
            meta.setUnbreakable(unbreakable);
            hand.setItemMeta(meta);
            pm.setCooldown(p.getUniqueId(), "unbreakable", 24 * 60 * 60 * 1000L);
            Msg.success(p, "Item is now " + (unbreakable ? "unbreakable" : "breakable") + "!");
        }));

        cmds.add(perk("autocraft", "Open portable crafting", 5, pm, (p, args) -> {
            p.openWorkbench(null, true);
            Msg.success(p, "Auto-craft station opened!");
        }));

        // ==================== GENERAL ====================

        cmds.add(new Command("premium") {
            { setDescription("View and purchase premium ranks"); setUsage("/premium"); }
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                if (!(sender instanceof Player p)) { sender.sendMessage(Msg.errorComponent("Players only!")); return true; }
                pm.openPremiumGui(p);
                return true;
            }
        });

        cmds.add(new Command("rankup") {
            { setDescription("Purchase the next premium rank"); setUsage("/rankup"); }
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                if (!(sender instanceof Player p)) { sender.sendMessage(Msg.errorComponent("Players only!")); return true; }
                int current = pm.getAdminManager().getPremiumLevel(p.getUniqueId());
                if (current >= 5) { Msg.error(p, "You already have the highest rank!"); return true; }
                PremiumRank next = PremiumRank.fromLevel(current + 1);
                pm.openBuyGui(p, next);
                return true;
            }
        });

        cmds.add(perkTab("referral", "Use a referral code", 0, pm, (p, args) -> {
            if (args.length < 1) { Msg.error(p, "Usage: /referral <player>"); return; }
            pm.handleReferral(p, args[0]);
        }, (s, args) -> args.length == 1 ? playerNames(args[0]) : List.of()));

        return cmds;
    }

    // ==================== HEAD DATABASE ====================

    private static void openHeadDatabase(PremiumManager pm, Player player) {
        PremiumHolder holder = new PremiumHolder(PremiumHolder.Type.HEAD_DATABASE);
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Head Database", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        String[] headNames = {
                "MHF_Pig", "MHF_Cow", "MHF_Sheep", "MHF_Chicken", "MHF_Villager",
                "MHF_Blaze", "MHF_Skeleton", "MHF_Zombie", "MHF_Creeper", "MHF_Spider",
                "MHF_Enderman", "MHF_Ghast", "MHF_Slime", "MHF_Golem", "MHF_Ocelot",
                "MHF_Wolf", "MHF_MushroomCow", "MHF_Squid", "MHF_Cactus", "MHF_Cake",
                "MHF_Chest", "MHF_Melon", "MHF_Pumpkin", "MHF_TNT", "MHF_Present1",
                "MHF_Present2", "MHF_ArrowUp", "MHF_ArrowDown", "MHF_ArrowLeft", "MHF_ArrowRight",
                "MHF_Exclamation", "MHF_Question", "MHF_OakLog", "MHF_CoconutB", "MHF_CoconutG"
        };

        for (int i = 0; i < headNames.length && i < 45; i++) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(headNames[i]));
            meta.displayName(Component.text(headNames[i].replace("MHF_", ""), YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Click to get this head", GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            skull.setItemMeta(meta);
            inv.setItem(i, skull);
        }

        player.openInventory(inv);
    }

    // ==================== HELPERS ====================

    private static Command perk(String name, String desc, int requiredLevel, PremiumManager pm,
                                BiConsumer<Player, String[]> action) {
        return perkTab(name, desc, requiredLevel, pm, action, null);
    }

    private static Command perkTab(String name, String desc, int requiredLevel, PremiumManager pm,
                                   BiConsumer<Player, String[]> action,
                                   java.util.function.BiFunction<CommandSender, String[], List<String>> tabCompleter) {
        return new Command(name) {
            {
                setDescription(desc);
                setUsage("/" + name);
            }

            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Msg.errorComponent("Players only!"));
                    return true;
                }
                if (requiredLevel > 0 && pm.getAdminManager().getPremiumLevel(p.getUniqueId()) < requiredLevel) {
                    Msg.error(p, "You need " + PremiumRank.fromLevel(requiredLevel).getDisplayName() +
                            " rank to use this! Type /premium to purchase.");
                    return true;
                }
                action.accept(p, args);
                return true;
            }

            @Override
            public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
                if (tabCompleter != null) return tabCompleter.apply(sender, args);
                return List.of();
            }
        };
    }

    private static List<String> playerNames(String prefix) {
        String lower = prefix.toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(lower))
                .toList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).toList();
    }

    private static String formatMaterial(String name) {
        return name.replace('_', ' ').toLowerCase();
    }

    private static String joinArgs(String[] args, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private static String formatTime(long seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
}
