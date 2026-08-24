package com.starlightuniverse.diag;

import com.starlightuniverse.admin.AdminCommand;
import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.admin.AdminRank;
import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class DiagCommand extends AdminCommand {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor YELLOW = TextColor.color(0xFFFF55);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);
    private static final TextColor GREEN = TextColor.color(0x55FF55);

    private final DiagnosticsService diag;

    public DiagCommand(AdminManager adminManager, DiagnosticsService diag) {
        super("sudiag", "Starlight Universe diagnostics", "/sudiag [status|counts|save]",
                adminManager, AdminRank.OWNER.getLevel());
        this.diag = diag;
    }

    @Override
    protected boolean onCommand(Player player, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (sub) {
            case "status" -> showStatus(player);
            case "counts" -> showCounts(player);
            case "save" -> {
                Msg.info(player, "Saving all persistent state...");
                int n = diag.forceSaveAll();
                Msg.success(player, "Saved " + n + " authenticated player(s), all worlds, all Bukkit player data.");
            }
            default -> Msg.error(player, "Usage: /sudiag [status|counts|save]");
        }
        return true;
    }

    private void showStatus(Player player) {
        player.sendMessage(Component.text("=== Starlight Universe Diagnostics ===", GOLD));
        Map<String, String> status = diag.buildStatus();
        for (Map.Entry<String, String> e : status.entrySet()) {
            player.sendMessage(
                    Component.text("  " + e.getKey() + ": ", CYAN)
                            .append(Component.text(e.getValue(), YELLOW)));
        }
        player.sendMessage(Component.text("Use /sudiag counts for DB row counts.", GRAY));
    }

    private void showCounts(Player player) {
        player.sendMessage(Component.text("=== Database Row Counts ===", GOLD));
        Map<String, Long> counts = diag.tableCounts();
        long missing = 0;
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            long v = e.getValue();
            if (v < 0) {
                missing++;
                player.sendMessage(
                        Component.text("  " + e.getKey() + ": ", CYAN)
                                .append(Component.text("MISSING", TextColor.color(0xFF5555))));
            } else {
                player.sendMessage(
                        Component.text("  " + e.getKey() + ": ", CYAN)
                                .append(Component.text(String.valueOf(v), GREEN)));
            }
        }
        if (missing > 0) {
            Msg.error(player, missing + " table(s) missing or unreachable. Check DB migrations.");
        }
    }

    @Override
    public List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            String p = args[0].toLowerCase();
            return List.of("status", "counts", "save").stream().filter(s -> s.startsWith(p)).toList();
        }
        return List.of();
    }
}
