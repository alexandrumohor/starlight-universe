package com.starlightuniverse.travel;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class RtpCommand extends Command {

    private final RtpManager rtpManager;
    private final AdminManager adminManager;

    public RtpCommand(RtpManager rtpManager, AdminManager adminManager) {
        super("rtp");
        setDescription("Random teleport to a world");
        setUsage("/rtp [lock|unlock <world>]");
        this.rtpManager = rtpManager;
        this.adminManager = adminManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            rtpManager.openGui(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("lock") || args[0].equalsIgnoreCase("unlock")) {
            if (adminManager.getAdminLevel(player.getUniqueId()) < 3) {
                Msg.error(player, "You don't have permission!");
                return true;
            }
            if (args.length < 2) {
                Msg.error(player, "Usage: /rtp " + args[0].toLowerCase() + " <world>");
                return true;
            }
            RtpManager.RtpWorld r = RtpManager.RtpWorld.byKey(args[1]);
            if (r == null) {
                Msg.error(player, "Unknown world! Valid: overworld, nether, end, resource_overworld, resource_nether, resource_end");
                return true;
            }
            boolean lock = args[0].equalsIgnoreCase("lock");
            rtpManager.setLocked(r, lock);
            Msg.success(player, r.display + " has been " + (lock ? "LOCKED." : "UNLOCKED."));
            return true;
        }

        RtpManager.RtpWorld r = RtpManager.RtpWorld.byKey(args[0]);
        if (r != null) {
            rtpManager.teleport(player, r);
            return true;
        }

        Msg.error(player, "Unknown option. Use /rtp to open the GUI.");
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.stream(new String[]{"lock", "unlock", "overworld", "nether", "end",
                            "resource_overworld", "resource_nether", "resource_end"})
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("lock") || args[0].equalsIgnoreCase("unlock"))) {
            return Arrays.stream(RtpManager.RtpWorld.values())
                    .map(r -> r.name().toLowerCase())
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
