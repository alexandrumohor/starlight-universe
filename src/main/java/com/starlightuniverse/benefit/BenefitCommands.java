package com.starlightuniverse.benefit;

import com.starlightuniverse.economy.EconomyManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class BenefitCommands {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);

    private static final Pattern HEX = Pattern.compile("^#?[0-9A-Fa-f]{6}$");

    private BenefitCommands() {}

    public static List<Command> create(BenefitManager mgr) {
        List<Command> cmds = new ArrayList<>();

        cmds.add(new Command("setprefix") {
            { setDescription("Set your custom prefix"); setUsage("/setprefix <text|clear>"); }
            @Override public boolean execute(CommandSender s, String l, String[] args) {
                if (!(s instanceof Player p)) return false;
                if (args.length == 0) { Msg.info(p, "Usage: /setprefix <text|clear>"); return true; }
                String text = String.join(" ", args);
                if (text.equalsIgnoreCase("clear") || text.equalsIgnoreCase("remove")) {
                    mgr.setCustomPrefix(p, null);
                    Msg.success(p, "Prefix cleared.");
                    return true;
                }
                if (text.length() > 12) { Msg.error(p, "Prefix must be 12 chars or less."); return true; }
                if (!text.matches("[a-zA-Z0-9_]+")) {
                    Msg.error(p, "Prefix: letters, digits, underscore only.");
                    return true;
                }
                if (!mgr.hasUnlock(p.getUniqueId(), BenefitManager.CAT_PREFIX, "all")) {
                    if (!mgr.purchasePrefixUnlock(p)) return true;
                }
                mgr.setCustomPrefix(p, text);
                Msg.success(p, "Prefix set to [" + text + "]!");
                return true;
            }
            @Override public List<String> tabComplete(CommandSender s, String a, String[] args) {
                if (args.length == 1) return List.of("clear").stream()
                        .filter(x -> x.startsWith(args[0].toLowerCase())).toList();
                return List.of();
            }
        });

        cmds.add(new Command("namecolor") {
            { setDescription("Set your name color"); setUsage("/namecolor <#RRGGBB|clear>"); }
            @Override public boolean execute(CommandSender s, String l, String[] args) {
                if (!(s instanceof Player p)) return false;
                if (args.length == 0) { Msg.info(p, "Usage: /namecolor <#RRGGBB|clear>"); return true; }
                String hex = args[0];
                if (hex.equalsIgnoreCase("clear") || hex.equalsIgnoreCase("remove")) {
                    mgr.purchaseNameColor(p, null);
                    return true;
                }
                if (!HEX.matcher(hex).matches()) {
                    Msg.error(p, "Invalid color. Use #RRGGBB");
                    return true;
                }
                if (!hex.startsWith("#")) hex = "#" + hex;
                mgr.purchaseNameColor(p, hex.toUpperCase());
                return true;
            }
            @Override public List<String> tabComplete(CommandSender s, String a, String[] args) {
                if (args.length == 1) return List.of("#FF0000", "#FFAA00", "#FFFF00",
                        "#55FF55", "#55FFFF", "#5555FF", "#AA00AA", "#FF55FF", "clear").stream()
                        .filter(x -> x.toLowerCase().startsWith(args[0].toLowerCase())).toList();
                return List.of();
            }
        });

        cmds.add(new Command("chatcolor") {
            { setDescription("Set your chat message color"); setUsage("/chatcolor <#RRGGBB|clear>"); }
            @Override public boolean execute(CommandSender s, String l, String[] args) {
                if (!(s instanceof Player p)) return false;
                if (args.length == 0) { Msg.info(p, "Usage: /chatcolor <#RRGGBB|clear>"); return true; }
                String hex = args[0];
                if (hex.equalsIgnoreCase("clear") || hex.equalsIgnoreCase("remove")) {
                    mgr.purchaseChatColor(p, null);
                    return true;
                }
                if (!HEX.matcher(hex).matches()) {
                    Msg.error(p, "Invalid color. Use #RRGGBB");
                    return true;
                }
                if (!hex.startsWith("#")) hex = "#" + hex;
                mgr.purchaseChatColor(p, hex.toUpperCase());
                return true;
            }
            @Override public List<String> tabComplete(CommandSender s, String a, String[] args) {
                if (args.length == 1) return List.of("#FF0000", "#FFAA00", "#FFFF00",
                        "#55FF55", "#55FFFF", "#5555FF", "#AA00AA", "#FF55FF", "clear").stream()
                        .filter(x -> x.toLowerCase().startsWith(args[0].toLowerCase())).toList();
                return List.of();
            }
        });

        cmds.add(new Command("joinmsg") {
            { setDescription("Set custom join message"); setUsage("/joinmsg <msg|clear>"); }
            @Override public boolean execute(CommandSender s, String l, String[] args) {
                if (!(s instanceof Player p)) return false;
                if (args.length == 0) { Msg.info(p, "Usage: /joinmsg <msg|clear>. Placeholder: {name}"); return true; }
                String msg = String.join(" ", args);
                if (msg.equalsIgnoreCase("clear")) {
                    mgr.setJoinMsg(p, null);
                    Msg.success(p, "Join message cleared.");
                    return true;
                }
                if (msg.length() > 80) { Msg.error(p, "Max 80 chars."); return true; }
                mgr.setJoinMsg(p, msg);
                return true;
            }
            @Override public List<String> tabComplete(CommandSender s, String a, String[] args) {
                if (args.length == 1) return List.of("clear").stream()
                        .filter(x -> x.startsWith(args[0].toLowerCase())).toList();
                return List.of();
            }
        });

        cmds.add(new Command("quitmsg") {
            { setDescription("Set custom quit message"); setUsage("/quitmsg <msg|clear>"); }
            @Override public boolean execute(CommandSender s, String l, String[] args) {
                if (!(s instanceof Player p)) return false;
                if (args.length == 0) { Msg.info(p, "Usage: /quitmsg <msg|clear>. Placeholder: {name}"); return true; }
                String msg = String.join(" ", args);
                if (msg.equalsIgnoreCase("clear")) {
                    mgr.setQuitMsg(p, null);
                    Msg.success(p, "Quit message cleared.");
                    return true;
                }
                if (msg.length() > 80) { Msg.error(p, "Max 80 chars."); return true; }
                mgr.setQuitMsg(p, msg);
                return true;
            }
            @Override public List<String> tabComplete(CommandSender s, String a, String[] args) {
                if (args.length == 1) return List.of("clear").stream()
                        .filter(x -> x.startsWith(args[0].toLowerCase())).toList();
                return List.of();
            }
        });

        cmds.add(new Command("glow") {
            { setDescription("Activate/deactivate your body glow"); setUsage("/glow <color|off>"); }
            @Override public boolean execute(CommandSender s, String l, String[] args) {
                if (!(s instanceof Player p)) return false;
                if (args.length == 0) { openGlowShop(mgr, p); return true; }
                if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("clear")) {
                    mgr.activateGlow(p, null);
                    Msg.success(p, "Glow disabled.");
                    return true;
                }
                BodyGlow g = BodyGlow.byKey(args[0]);
                if (g == null) { Msg.error(p, "Unknown glow color."); return true; }
                mgr.activateGlow(p, g);
                Msg.success(p, "Glow set to " + g.getDisplayName() + ".");
                return true;
            }
            @Override public List<String> tabComplete(CommandSender s, String a, String[] args) {
                if (args.length == 1) {
                    List<String> out = new ArrayList<>();
                    for (BodyGlow g : BodyGlow.values()) out.add(g.getKey());
                    out.add("off");
                    String pfx = args[0].toLowerCase();
                    return out.stream().filter(x -> x.startsWith(pfx)).toList();
                }
                return List.of();
            }
        });

        cmds.add(new Command("glowshop") {
            { setDescription("Open the body glow shop"); }
            @Override public boolean execute(CommandSender s, String l, String[] args) {
                if (!(s instanceof Player p)) return false;
                openGlowShop(mgr, p);
                return true;
            }
        });

        cmds.add(new Command("killeffect") {
            { setDescription("Activate/deactivate your kill effect"); setUsage("/killeffect <name|off>"); }
            @Override public boolean execute(CommandSender s, String l, String[] args) {
                if (!(s instanceof Player p)) return false;
                if (args.length == 0) { openKillShop(mgr, p); return true; }
                if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("clear")) {
                    mgr.activateKillEffect(p, null);
                    Msg.success(p, "Kill effect disabled.");
                    return true;
                }
                KillEffect fx = KillEffect.byKey(args[0]);
                if (fx == null) { Msg.error(p, "Unknown kill effect."); return true; }
                mgr.activateKillEffect(p, fx);
                Msg.success(p, "Kill effect set to " + fx.getDisplayName() + ".");
                return true;
            }
            @Override public List<String> tabComplete(CommandSender s, String a, String[] args) {
                if (args.length == 1) {
                    List<String> out = new ArrayList<>();
                    for (KillEffect fx : KillEffect.values()) out.add(fx.getKey());
                    out.add("off");
                    String pfx = args[0].toLowerCase();
                    return out.stream().filter(x -> x.startsWith(pfx)).toList();
                }
                return List.of();
            }
        });

        cmds.add(new Command("killshop") {
            { setDescription("Open the kill effect shop"); }
            @Override public boolean execute(CommandSender s, String l, String[] args) {
                if (!(s instanceof Player p)) return false;
                openKillShop(mgr, p);
                return true;
            }
        });

        return cmds;
    }

    public static void openGlowShop(BenefitManager mgr, Player player) {
        BenefitHolder holder = new BenefitHolder(BenefitHolder.Type.GLOW);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Body Glow Shop", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);
        UUID uuid = player.getUniqueId();

        BodyGlow[] all = BodyGlow.values();
        Material[] icons = {
                Material.BONE, Material.REDSTONE, Material.ORANGE_DYE, Material.YELLOW_DYE,
                Material.LIME_DYE, Material.CYAN_DYE, Material.LAPIS_LAZULI, Material.PURPLE_DYE
        };
        for (int i = 0; i < all.length; i++) {
            BodyGlow g = all[i];
            boolean owned = mgr.hasUnlock(uuid, BenefitManager.CAT_GLOW, g.getKey());
            boolean active = g.getKey().equals(mgr.getActiveGlow(uuid));
            ItemStack item = new ItemStack(icons[i]);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(g.getDisplayName() + " Glow", TextColor.fromHexString(g.getHex()))
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            if (active) {
                lore.add(Component.text("ACTIVE - Click to disable", GREEN).decoration(TextDecoration.ITALIC, false));
            } else if (owned) {
                lore.add(Component.text("OWNED - Click to activate", YELLOW).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Cost: " + EconomyManager.GEMS_ICON + BodyGlow.UNLOCK_GEM_COST, YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Click to buy", GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(i + 9, item);
        }

        ItemStack off = new ItemStack(Material.BARRIER);
        ItemMeta offMeta = off.getItemMeta();
        offMeta.displayName(Component.text("Turn Glow OFF", RED).decoration(TextDecoration.ITALIC, false));
        off.setItemMeta(offMeta);
        inv.setItem(22, off);

        player.openInventory(inv);
    }

    public static void openKillShop(BenefitManager mgr, Player player) {
        BenefitHolder holder = new BenefitHolder(BenefitHolder.Type.KILL);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Kill Effect Shop", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);
        UUID uuid = player.getUniqueId();

        KillEffect[] all = KillEffect.values();
        Material[] icons = {
                Material.LIGHTNING_ROD, Material.FIREWORK_ROCKET, Material.TNT,
                Material.BLAZE_POWDER, Material.SOUL_LANTERN, Material.ENCHANTED_BOOK
        };
        for (int i = 0; i < all.length; i++) {
            KillEffect fx = all[i];
            boolean owned = mgr.hasUnlock(uuid, BenefitManager.CAT_KILL, fx.getKey());
            boolean active = fx.getKey().equals(mgr.getActiveKillEffect(uuid));
            ItemStack item = new ItemStack(icons[i]);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(fx.getDisplayName(), CYAN).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(fx.getDescription(), GRAY).decoration(TextDecoration.ITALIC, false));
            if (active) {
                lore.add(Component.text("ACTIVE - Click to disable", GREEN).decoration(TextDecoration.ITALIC, false));
            } else if (owned) {
                lore.add(Component.text("OWNED - Click to activate", YELLOW).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Cost: " + EconomyManager.GEMS_ICON + fx.getGemCost(), YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Click to buy", GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(i + 9, item);
        }

        ItemStack off = new ItemStack(Material.BARRIER);
        ItemMeta offMeta = off.getItemMeta();
        offMeta.displayName(Component.text("Turn OFF", RED).decoration(TextDecoration.ITALIC, false));
        off.setItemMeta(offMeta);
        inv.setItem(22, off);

        player.openInventory(inv);
    }

    public static class BenefitHolder implements InventoryHolder {
        public enum Type { GLOW, KILL }
        private final Type type;
        private Inventory inv;
        public BenefitHolder(Type type) { this.type = type; }
        public Type getType() { return type; }
        public void setInventory(Inventory inv) { this.inv = inv; }
        @Override public Inventory getInventory() { return inv; }
    }
}
