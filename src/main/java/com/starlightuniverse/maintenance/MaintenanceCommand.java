package com.starlightuniverse.maintenance;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MaintenanceCommand extends Command {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor RED = TextColor.color(0xFF5555);

    private final MaintenanceManager manager;
    private final AdminManager adminManager;

    public MaintenanceCommand(MaintenanceManager manager, AdminManager adminManager) {
        super("maintenance");
        this.manager = manager;
        this.adminManager = adminManager;
        setDescription("Schedule a maintenance restart or lift the barrier");
        setUsage("/maintenance <start <seconds> | stop | status>");
        setAliases(List.of("mtn"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p) || adminManager.getAdminLevel(p.getUniqueId()) < MaintenanceManager.COMMAND_ADMIN_LEVEL) {
            sender.sendMessage(Component.text("[SU] Owner-only command.", RED));
            return true;
        }
        if (args.length == 0) { sendHelp(p); return true; }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "start" -> {
                if (args.length < 2) {
                    Msg.error(p, "Usage: /maintenance start <seconds>  (1-" + MaintenanceManager.MAX_COUNTDOWN_SECONDS + ")");
                    return true;
                }
                int seconds;
                try { seconds = Integer.parseInt(args[1]); }
                catch (NumberFormatException e) {
                    Msg.error(p, "Countdown must be a whole number of seconds.");
                    return true;
                }
                if (seconds < MaintenanceManager.MIN_COUNTDOWN_SECONDS
                        || seconds > MaintenanceManager.MAX_COUNTDOWN_SECONDS) {
                    Msg.error(p, "Seconds must be between " + MaintenanceManager.MIN_COUNTDOWN_SECONDS
                            + " and " + MaintenanceManager.MAX_COUNTDOWN_SECONDS + ".");
                    return true;
                }
                manager.startWithCountdown(p, seconds);
                Msg.success(p, "Restart countdown started (" + seconds + "s). All players will be kicked, then the server will restart.");
            }
            case "stop", "end", "off" -> {
                manager.stop(p);
                Msg.success(p, "Maintenance stopped.");
            }
            case "status" -> {
                if (manager.isActive()) {
                    p.sendMessage(Component.text("[SU] ", GOLD)
                            .append(Component.text("Status: ", GRAY))
                            .append(Component.text("BARRIER ACTIVE", RED)));
                } else if (manager.isCountdownRunning()) {
                    p.sendMessage(Component.text("[SU] ", GOLD)
                            .append(Component.text("Status: ", GRAY))
                            .append(Component.text("restart countdown ", YELLOW))
                            .append(Component.text(MaintenanceManager.formatDuration(manager.getCountdownRemainingSec()) + " remaining", YELLOW)));
                } else {
                    p.sendMessage(Component.text("[SU] ", GOLD)
                            .append(Component.text("Status: ", GRAY))
                            .append(Component.text("inactive", GREEN)));
                }
            }
            default -> sendHelp(p);
        }
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage(Component.text("[SU] Maintenance:", GOLD));
        p.sendMessage(Component.text("  /maintenance start <seconds> — countdown, kick, restart", GRAY));
        p.sendMessage(Component.text("  /maintenance stop — cancel a countdown or lift the barrier", GRAY));
        p.sendMessage(Component.text("  /maintenance status — show current state", GRAY));
    }
}
