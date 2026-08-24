package com.starlightuniverse.announce;

import com.starlightuniverse.admin.AdminManager;
import com.starlightuniverse.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AnnounceCommand extends Command {

    public static final int REQUIRED_ADMIN_LEVEL = 3; // Moderator+

    private final AnnouncementManager manager;
    private final AdminManager adminManager;

    public AnnounceCommand(AnnouncementManager manager, AdminManager adminManager) {
        super("announce");
        this.manager = manager;
        this.adminManager = adminManager;
        setDescription("Open the announcements manager GUI");
        setUsage("/announce");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (adminManager.getAdminLevel(p.getUniqueId()) < REQUIRED_ADMIN_LEVEL) {
            Msg.error(p, "Moderator+ required.");
            return true;
        }
        manager.openListGui(p);
        return true;
    }
}
