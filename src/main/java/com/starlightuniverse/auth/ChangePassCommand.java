package com.starlightuniverse.auth;

import com.starlightuniverse.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ChangePassCommand extends Command {

    private final JavaPlugin plugin;
    private final AuthManager authManager;

    public ChangePassCommand(JavaPlugin plugin, AuthManager authManager) {
        super("changepass");
        setDescription("Change your password");
        setUsage("/changepass <old> <new>");
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (!authManager.isAuthenticated(player.getUniqueId())) {
            Msg.error(player, "You must log in first!");
            return true;
        }

        if (args.length != 2) {
            Msg.error(player, "Usage: /changepass <old password> <new password>");
            return true;
        }

        String oldPass = args[0];
        String newPass = args[1];
        String username = player.getName();

        if (newPass.length() < 3) {
            Msg.error(player, "New password must be at least 3 characters!");
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = authManager.changePassword(username, oldPass, newPass);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (success) {
                    Msg.success(player, "Password changed successfully!");
                } else {
                    Msg.error(player, "Wrong old password or change failed!");
                }
            });
        });
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
