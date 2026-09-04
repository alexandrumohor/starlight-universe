package com.starlightuniverse.hottime;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class HotTimeCommand extends Command {

    public static final int REQUIRED_ADMIN_LEVEL = 3; // Moderator+

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor RED = TextColor.color(0xFF5555);

    private final HotTimeManager manager;
    private final AdminManager adminManager;

    public HotTimeCommand(HotTimeManager manager, AdminManager adminManager) {
        super("hottime");
        this.manager = manager;
        this.adminManager = adminManager;
        setDescription("Start or stop a Hot Time buff");
        setUsage("/hottime <minutes> <multiplier>");
        setAliases(java.util.List.of("ht"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p) || adminManager.getAdminLevel(p.getUniqueId()) < REQUIRED_ADMIN_LEVEL) {
            sender.sendMessage(Component.text("[SU] Moderator+ required.", RED));
            return true;
        }
        if (args.length == 1 && (args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("off"))) {
            if (!manager.stop()) Msg.error(p, "Hot Time is not active.");
            else Msg.success(p, "Hot Time stopped.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            if (manager.isActive()) {
                Msg.info(p, String.format("Hot Time is ACTIVE (x%.2f, %ds left).",
                        manager.getRawMultiplier(), manager.getRemainingSeconds()));
            } else {
                Msg.info(p, "Hot Time is inactive.");
            }
            return true;
        }
        if (args.length < 2) {
            sendHelp(p);
            return true;
        }
        int minutes;
        double mult;
        try { minutes = Integer.parseInt(args[0]); }
        catch (NumberFormatException e) { Msg.error(p, "Minutes must be a number."); return true; }
        try { mult = Double.parseDouble(args[1]); }
        catch (NumberFormatException e) { Msg.error(p, "Multiplier must be a number (e.g. 2, 2.5)."); return true; }

        if (minutes < 1 || minutes > HotTimeManager.MAX_DURATION_MINUTES) {
            Msg.error(p, "Minutes must be between 1 and " + HotTimeManager.MAX_DURATION_MINUTES + ".");
            return true;
        }
        if (mult < HotTimeManager.MIN_MULTIPLIER || mult > HotTimeManager.MAX_MULTIPLIER) {
            Msg.error(p, String.format("Multiplier must be between %.2f and %.2f.",
                    HotTimeManager.MIN_MULTIPLIER, HotTimeManager.MAX_MULTIPLIER));
            return true;
        }

        if (!manager.start(minutes, mult)) {
            Msg.error(p, "Hot Time is already active. Stop it first with /hottime stop.");
            return true;
        }
        Msg.success(p, "Hot Time started.");
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage(Component.text("[SU] Hot Time:", GOLD));
        p.sendMessage(Component.text("  /hottime <minutes> <multiplier> — start (e.g. /hottime 30 2)", GRAY));
        p.sendMessage(Component.text("  /hottime stop — end early", GRAY));
        p.sendMessage(Component.text("  /hottime status — show current state", GRAY));
    }

    @Override
    public java.util.List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return java.util.List.of("stop", "status", "5", "15", "30", "60").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2) {
            return java.util.List.of("1.5", "2", "2.5", "3").stream()
                    .filter(s -> s.startsWith(args[1])).toList();
        }
        return java.util.List.of();
    }
}
