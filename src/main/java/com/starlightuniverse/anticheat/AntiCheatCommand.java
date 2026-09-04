package com.starlightuniverse.anticheat;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class AntiCheatCommand extends Command {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor RED = TextColor.color(0xFF5555);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);

    private final AntiCheatManager manager;
    private final AdminManager adminManager;

    public AntiCheatCommand(AntiCheatManager manager, AdminManager adminManager) {
        super("ac");
        this.manager = manager;
        this.adminManager = adminManager;
        setDescription("Anti-cheat management");
        setUsage("/ac <check|history|clear> <player>");
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player p) || adminManager.getAdminLevel(p.getUniqueId()) < 2) {
            sender.sendMessage(Component.text("[SU] Helper+ required.", RED));
            return true;
        }
        if (args.length < 2) {
            Msg.error(p, "Usage: /ac <check|history|clear> <player>");
            return true;
        }
        String sub = args[0].toLowerCase();
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) target = Bukkit.getOfflinePlayer(args[1]);

        switch (sub) {
            case "check" -> {
                Map<Violation, Integer> v = manager.getViolations(target.getUniqueId());
                p.sendMessage(Component.text("[SU] Violations for " + target.getName() + ":", GOLD));
                if (v.isEmpty()) {
                    p.sendMessage(Component.text("  None active.", GREEN));
                    return true;
                }
                int total = 0;
                for (Map.Entry<Violation, Integer> e : v.entrySet()) {
                    p.sendMessage(Component.text("  " + e.getKey().getLabel() + ": ", WHITE)
                            .append(Component.text(String.valueOf(e.getValue()), YELLOW)));
                    total += e.getValue();
                }
                p.sendMessage(Component.text("  Total: " + total + " / " + AntiCheatManager.KICK_THRESHOLD, GRAY));
            }
            case "history" -> {
                List<AntiCheatManager.ViolationEntry> h = manager.getHistory(target.getUniqueId());
                p.sendMessage(Component.text("[SU] History for " + target.getName() + ":", GOLD));
                if (h.isEmpty()) {
                    p.sendMessage(Component.text("  No history.", GREEN));
                    return true;
                }
                for (AntiCheatManager.ViolationEntry e : h) {
                    p.sendMessage(Component.text("  [" + e.formattedTime() + "] ", GRAY)
                            .append(Component.text(e.violation().getLabel(), YELLOW))
                            .append(Component.text(" — " + e.details(), GRAY)));
                }
            }
            case "clear" -> {
                manager.clear(target.getUniqueId());
                Msg.success(p, "Cleared violations for " + target.getName());
            }
            default -> Msg.error(p, "Usage: /ac <check|history|clear> <player>");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("check", "history", "clear").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2) {
            String pfx = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(pfx)).toList();
        }
        return List.of();
    }
}
