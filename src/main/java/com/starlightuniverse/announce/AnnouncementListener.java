package com.starlightuniverse.announce;

import com.starlightuniverse.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AnnouncementListener implements Listener {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);

    private final JavaPlugin plugin;
    private final AnnouncementManager manager;

    private static class CreateStash {
        String message;
        int frequencyMinutes;
        int durationSeconds;
        CreateStash(String message) { this.message = message; }
    }

    private final Map<UUID, CreateStash> createStash = new ConcurrentHashMap<>();

    public AnnouncementListener(JavaPlugin plugin, AnnouncementManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AnnouncementHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        int size = event.getView().getTopInventory().getSize();
        if (slot < 0 || slot >= size) return;

        switch (holder.getType()) {
            case LIST -> handleListClick(player, event, slot);
            case EDIT -> handleEditClick(player, event, holder.getAnnouncementId(), slot);
            case TYPE_PICKER -> handleTypePickerClick(player, holder.getAnnouncementId(), slot);
        }
    }

    private void handleListClick(Player player, InventoryClickEvent event, int slot) {
        if (slot == 49) {
            player.closeInventory();
            manager.setPending(player.getUniqueId(),
                    new AnnouncementManager.PendingInput(AnnouncementManager.InputStage.MESSAGE_CREATE, -1));
            promptMessage(player);
            return;
        }
        if (slot == 53) {
            player.closeInventory();
            return;
        }
        if (slot >= 45) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR
                || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        var all = new java.util.ArrayList<>(manager.getAll());
        if (slot >= all.size()) return;
        Announcement a = all.get(slot);
        int id = a.getId();

        ClickType type = event.getClick();
        if (type == ClickType.LEFT) {
            manager.toggle(player, id);
            manager.openListGui(player);
        } else if (type == ClickType.RIGHT) {
            manager.openEditGui(player, id);
        } else if (type == ClickType.SHIFT_RIGHT) {
            manager.delete(player, id);
            manager.openListGui(player);
        }
    }

    private void handleEditClick(Player player, InventoryClickEvent event, int id, int slot) {
        Announcement a = manager.get(id);
        if (a == null) { manager.openListGui(player); return; }
        ClickType c = event.getClick();
        switch (slot) {
            case 19 -> {
                player.closeInventory();
                manager.setPending(player.getUniqueId(),
                        new AnnouncementManager.PendingInput(AnnouncementManager.InputStage.MESSAGE_EDIT, id));
                promptMessage(player);
            }
            case 21 -> manager.openTypePickerGui(player, id);
            case 23 -> {
                int delta = switch (c) {
                    case LEFT -> -1;
                    case RIGHT -> +1;
                    case SHIFT_LEFT -> -10;
                    case SHIFT_RIGHT -> +10;
                    default -> 0;
                };
                if (delta != 0) {
                    manager.setFrequency(id, a.getFrequencyMinutes() + delta);
                    manager.openEditGui(player, id);
                }
            }
            case 25 -> {
                int delta = switch (c) {
                    case LEFT, SHIFT_LEFT -> -1;
                    case RIGHT, SHIFT_RIGHT -> +1;
                    default -> 0;
                };
                if (delta != 0) {
                    manager.setDuration(id, a.getDurationSeconds() + delta);
                    manager.openEditGui(player, id);
                }
            }
            case 31 -> {
                manager.toggle(player, id);
                manager.openEditGui(player, id);
            }
            case 39 -> {
                manager.broadcast(a);
                Msg.success(player, "Broadcasted announcement #" + id + " now.");
            }
            case 41 -> {
                if (c == ClickType.SHIFT_LEFT) {
                    manager.delete(player, id);
                    manager.openListGui(player);
                } else {
                    Msg.info(player, "Shift + Left-click to confirm delete.");
                }
            }
            case 36 -> manager.openListGui(player);
            default -> {}
        }
    }

    private void handleTypePickerClick(Player player, int id, int slot) {
        int[] slots = {10, 12, 13, 14, 16};
        AnnouncementType[] types = AnnouncementType.values();
        for (int i = 0; i < slots.length && i < types.length; i++) {
            if (slots[i] == slot) {
                AnnouncementType chosen = types[i];
                if (id >= 0) {
                    // Editing existing
                    manager.setType(id, chosen);
                    Msg.success(player, "Type set to " + chosen.getDisplayName() + ".");
                    manager.openEditGui(player, id);
                } else {
                    // Finishing create wizard
                    CreateStash st = createStash.remove(player.getUniqueId());
                    if (st == null) { player.closeInventory(); return; }
                    manager.create(player, st.message, chosen, st.frequencyMinutes, st.durationSeconds);
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        AnnouncementManager.PendingInput pending = manager.getPending(uuid);
        if (pending == null) return;
        event.setCancelled(true);
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleInput(event.getPlayer(), pending, raw));
    }

    private void handleInput(Player player, AnnouncementManager.PendingInput pending, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            manager.clearPending(player.getUniqueId());
            createStash.remove(player.getUniqueId());
            Msg.info(player, "Cancelled.");
            manager.openListGui(player);
            return;
        }
        switch (pending.stage) {
            case MESSAGE_CREATE -> {
                if (input.length() < 3) { Msg.error(player, "Message too short (min 3 chars). Try again or 'cancel'."); return; }
                String msg = input.length() > AnnouncementManager.MAX_MESSAGE_LENGTH
                        ? input.substring(0, AnnouncementManager.MAX_MESSAGE_LENGTH) : input;
                createStash.put(player.getUniqueId(), new CreateStash(msg));
                manager.setPending(player.getUniqueId(),
                        new AnnouncementManager.PendingInput(AnnouncementManager.InputStage.FREQUENCY, -1));
                promptFrequency(player);
            }
            case MESSAGE_EDIT -> {
                if (input.length() < 3) { Msg.error(player, "Message too short (min 3 chars). Try again or 'cancel'."); return; }
                manager.setMessage(pending.targetId, input);
                manager.clearPending(player.getUniqueId());
                Msg.success(player, "Message updated.");
                manager.openEditGui(player, pending.targetId);
            }
            case FREQUENCY -> {
                int freq;
                try { freq = Integer.parseInt(input.trim()); }
                catch (NumberFormatException e) {
                    Msg.error(player, "Enter a number in minutes (1-" + AnnouncementManager.MAX_FREQUENCY_MINUTES + ").");
                    return;
                }
                if (freq < 1 || freq > AnnouncementManager.MAX_FREQUENCY_MINUTES) {
                    Msg.error(player, "Frequency must be 1 to " + AnnouncementManager.MAX_FREQUENCY_MINUTES + " minutes.");
                    return;
                }
                if (pending.targetId >= 0) {
                    manager.setFrequency(pending.targetId, freq);
                    manager.clearPending(player.getUniqueId());
                    Msg.success(player, "Frequency updated to " + freq + " min.");
                    manager.openEditGui(player, pending.targetId);
                } else {
                    CreateStash st = createStash.get(player.getUniqueId());
                    if (st == null) { manager.clearPending(player.getUniqueId()); return; }
                    st.frequencyMinutes = freq;
                    manager.setPending(player.getUniqueId(),
                            new AnnouncementManager.PendingInput(AnnouncementManager.InputStage.DURATION, -1));
                    promptDuration(player);
                }
            }
            case DURATION -> {
                int dur;
                try { dur = Integer.parseInt(input.trim()); }
                catch (NumberFormatException e) {
                    Msg.error(player, "Enter a number in seconds (1-" + AnnouncementManager.MAX_DURATION_SECONDS + ").");
                    return;
                }
                if (dur < 1 || dur > AnnouncementManager.MAX_DURATION_SECONDS) {
                    Msg.error(player, "Duration must be 1 to " + AnnouncementManager.MAX_DURATION_SECONDS + " seconds.");
                    return;
                }
                if (pending.targetId >= 0) {
                    manager.setDuration(pending.targetId, dur);
                    manager.clearPending(player.getUniqueId());
                    Msg.success(player, "Duration updated to " + dur + "s.");
                    manager.openEditGui(player, pending.targetId);
                } else {
                    CreateStash st = createStash.get(player.getUniqueId());
                    if (st == null) { manager.clearPending(player.getUniqueId()); return; }
                    st.durationSeconds = dur;
                    manager.clearPending(player.getUniqueId());
                    // Open type picker with id -1 to signal "create mode"
                    manager.openTypePickerGui(player, -1);
                    Msg.info(player, "Pick a type to finish creating the announcement.");
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        manager.clearPending(uuid);
        createStash.remove(uuid);
    }

    private void promptMessage(Player player) {
        player.sendMessage(Component.text("[SU] ", GOLD)
                .append(Component.text("Type the announcement message in chat (3-"
                        + AnnouncementManager.MAX_MESSAGE_LENGTH + " chars). Type ", YELLOW))
                .append(Component.text("cancel", RED))
                .append(Component.text(" to abort.", YELLOW))
                .decoration(TextDecoration.ITALIC, false));
    }

    private void promptFrequency(Player player) {
        player.sendMessage(Component.text("[SU] ", GOLD)
                .append(Component.text("Type the broadcast frequency in minutes (1-"
                        + AnnouncementManager.MAX_FREQUENCY_MINUTES + "). Example: 15", YELLOW)));
    }

    private void promptDuration(Player player) {
        player.sendMessage(Component.text("[SU] ", GOLD)
                .append(Component.text("Type the actionbar duration in seconds (1-"
                        + AnnouncementManager.MAX_DURATION_SECONDS + "). Example: 5", YELLOW)));
    }
}
