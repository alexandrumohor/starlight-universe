package com.starlightuniverse.minigame;

import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MinigameCommand extends Command {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);

    private final MinigameManager manager;

    public MinigameCommand(MinigameManager manager) {
        super("minigame");
        setDescription("Chat minigame controls");
        setUsage("/minigame <start|skip|pause|resume|streak|list>");
        setAliases(List.of("mg"));
        this.manager = manager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("streak")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("[SU] Players only.", RED));
                return true;
            }
            int s = manager.getStreak(p.getUniqueId());
            Msg.info(p, "Your current minigame win streak: " + s);
            return true;
        }

        if (sub.equals("list")) {
            sender.sendMessage(Component.text("[SU] ", GOLD)
                    .append(Component.text("Available minigames:", YELLOW)));
            for (MinigameType t : MinigameType.values()) {
                sender.sendMessage(Component.text("  " + t.name().toLowerCase(), CYAN)
                        .append(Component.text(" - " + t.getDisplayName(), WHITE))
                        .append(Component.text(" — " + t.getDescription(), GRAY)));
            }
            return true;
        }

        if (!sender.isOp()) {
            sender.sendMessage(Component.text("[SU] No permission!", RED));
            return true;
        }

        switch (sub) {
            case "start" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("[SU] Usage: /minigame start <type>", RED));
                    return true;
                }
                MinigameType type;
                try {
                    type = MinigameType.valueOf(args[1].toUpperCase());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(Component.text("[SU] Unknown type. Use /minigame list", RED));
                    return true;
                }
                if (manager.forceStart(type)) {
                    sender.sendMessage(Component.text("[SU] Started " + type.getDisplayName() + " minigame!", GREEN));
                } else {
                    sender.sendMessage(Component.text("[SU] A minigame is already running.", RED));
                }
            }
            case "skip" -> {
                if (manager.skipCurrent()) {
                    sender.sendMessage(Component.text("[SU] Skipped current minigame.", GREEN));
                } else {
                    sender.sendMessage(Component.text("[SU] No minigame is currently running.", RED));
                }
            }
            case "pause" -> {
                manager.setPaused(true);
                sender.sendMessage(Component.text("[SU] Minigames paused. No new games will auto-start.", YELLOW));
            }
            case "resume" -> {
                manager.setPaused(false);
                sender.sendMessage(Component.text("[SU] Minigames resumed.", GREEN));
            }
            case "status" -> {
                ActiveMinigame current = manager.getCurrentGame();
                if (current == null) {
                    sender.sendMessage(Component.text("[SU] No minigame is active. Paused: " + manager.isPaused(), GRAY));
                } else {
                    long elapsed = System.currentTimeMillis() - current.getStartTime();
                    sender.sendMessage(Component.text("[SU] Active: " + current.getType().getDisplayName()
                            + " — answer: " + current.getPrimaryAnswer()
                            + " — elapsed: " + (elapsed / 1000) + "s", YELLOW));
                }
                List<UUID> holders = manager.getStreakHolders();
                if (holders.isEmpty()) {
                    sender.sendMessage(Component.text("  No active win streaks.", GRAY));
                } else {
                    for (UUID u : holders) {
                        Player p = Bukkit.getPlayer(u);
                        String name = p != null ? p.getName() : u.toString();
                        sender.sendMessage(Component.text("  Streak: " + name + " = " + manager.getStreak(u), CYAN));
                    }
                }
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("[SU] ", GOLD)
                .append(Component.text("Minigame commands:", YELLOW)));
        sender.sendMessage(Component.text("  /minigame streak", CYAN)
                .append(Component.text(" - see your current win streak", GRAY)));
        sender.sendMessage(Component.text("  /minigame list", CYAN)
                .append(Component.text(" - list available minigame types", GRAY)));
        if (sender.isOp()) {
            sender.sendMessage(Component.text("  /minigame start <type>", CYAN)
                    .append(Component.text(" - force start (admin)", GRAY)));
            sender.sendMessage(Component.text("  /minigame skip", CYAN)
                    .append(Component.text(" - skip current game (admin)", GRAY)));
            sender.sendMessage(Component.text("  /minigame pause|resume", CYAN)
                    .append(Component.text(" - pause/resume auto-scheduler (admin)", GRAY)));
            sender.sendMessage(Component.text("  /minigame status", CYAN)
                    .append(Component.text(" - show current state (admin)", GRAY)));
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("streak", "list"));
            if (sender.isOp()) subs.addAll(List.of("start", "skip", "pause", "resume", "status"));
            String prefix = args[0].toLowerCase();
            return subs.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start") && sender.isOp()) {
            String prefix = args[1].toLowerCase();
            List<String> types = new ArrayList<>();
            for (MinigameType t : MinigameType.values()) {
                String n = t.name().toLowerCase();
                if (n.startsWith(prefix)) types.add(n);
            }
            return types;
        }
        return List.of();
    }
}
