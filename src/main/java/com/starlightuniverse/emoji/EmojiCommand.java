package com.starlightuniverse.emoji;

import com.starlightuniverse.economy.EconomyManager;
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

public class EmojiCommand extends Command {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);

    private final EmojiManager emojiManager;

    public EmojiCommand(EmojiManager emojiManager) {
        super("emoji");
        this.emojiManager = emojiManager;
        setDescription("Open the emoji menu");
        setAliases(List.of("emojis"));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;
        openGui(player);
        return true;
    }

    public void openGui(Player player) {
        EmojiHolder holder = new EmojiHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Emoji Menu", GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        boolean unlocked = emojiManager.isUnlocked(player.getUniqueId());

        Emoji[] all = Emoji.values();
        int slot = 0;
        for (int i = 0; i < all.length && slot < 45; i++, slot++) {
            Emoji e = all[i];
            ItemStack item = new ItemStack(iconFor(e));
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(e.getUnicodeString() + " " + e.getName(),
                    unlocked ? YELLOW : GRAY).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Category: " + e.getCategory(), GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Chat: " + e.getToken(), CYAN).decoration(TextDecoration.ITALIC, false));
            if (!unlocked) {
                lore.add(Component.empty());
                lore.add(Component.text("Locked", GRAY).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
        }

        ItemStack info = new ItemStack(Material.NAME_TAG);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("How to use", YELLOW).decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(List.of(
                Component.text("Type :name: in chat.", GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Example: :smile: -> " + Emoji.SMILE.getUnicodeString(),
                        GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Backspace deletes the whole emoji.", GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        ItemStack unlockItem = new ItemStack(unlocked ? Material.EMERALD : Material.EMERALD_BLOCK);
        ItemMeta um = unlockItem.getItemMeta();
        if (unlocked) {
            um.displayName(Component.text("Emoji Pack Unlocked", GREEN).decoration(TextDecoration.ITALIC, false));
            um.lore(List.of(Component.text("You own the pack.", GRAY).decoration(TextDecoration.ITALIC, false)));
        } else {
            um.displayName(Component.text("Unlock Emoji Pack", GOLD).decoration(TextDecoration.ITALIC, false));
            um.lore(List.of(
                    Component.text("Cost: " + EconomyManager.GEMS_ICON + Emoji.UNLOCK_GEM_COST + " Gems",
                            YELLOW).decoration(TextDecoration.ITALIC, false),
                    Component.text("Click to unlock all emoji!", GRAY).decoration(TextDecoration.ITALIC, false)
            ));
        }
        unlockItem.setItemMeta(um);
        inv.setItem(53, unlockItem);

        player.openInventory(inv);
    }

    private Material iconFor(Emoji e) {
        return switch (e.getCategory()) {
            case "Faces" -> Material.PLAYER_HEAD;
            case "Symbols" -> Material.NETHER_STAR;
            case "Hands" -> Material.SKELETON_SKULL;
            case "Game" -> Material.DIAMOND;
            case "Misc" -> Material.PAPER;
            default -> Material.PAPER;
        };
    }

    public static class EmojiHolder implements InventoryHolder {
        private Inventory inv;
        public void setInventory(Inventory inv) { this.inv = inv; }
        @Override public Inventory getInventory() { return inv; }
    }
}
