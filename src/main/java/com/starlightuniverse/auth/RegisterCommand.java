package com.starlightuniverse.auth;

import com.starlightuniverse.StarlightUniverse;
import com.starlightuniverse.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RegisterCommand extends Command {

    private final JavaPlugin plugin;
    private final AuthManager authManager;

    public RegisterCommand(JavaPlugin plugin, AuthManager authManager) {
        super("register");
        setDescription("Register a new account");
        setUsage("/register <password> <confirm>");
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (authManager.isAuthenticated(player.getUniqueId())) {
            Msg.error(player, "You are already logged in!");
            return true;
        }

        if (args.length != 2) {
            Msg.error(player, "Usage: /register <password> <confirm>");
            return true;
        }

        String password = args[0];
        String confirm = args[1];
        String username = player.getName();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthManager.RegisterResult result = authManager.register(username, password, confirm, ip);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                switch (result) {
                    case SUCCESS -> {
                        authManager.setAuthenticated(player.getUniqueId());
                        Msg.success(player, "Account registered successfully! You are now logged in.");
                        StarlightUniverse.getInstance().getLobbyManager().giveSurvivalItem(player);
                        if (StarlightUniverse.getInstance().getPremiumManager() != null) {
                            StarlightUniverse.getInstance().getPremiumManager().grantTrial(username);
                            Msg.info(player, "You received a 3-day Meteor trial! Check /premium for perks.");
                        }
                        com.starlightuniverse.announce.WelcomeMessage.send(player);
                    }
                    case PASSWORD_TOO_SHORT -> Msg.error(player, "Password must be at least 3 characters!");
                    case PASSWORD_MISMATCH -> Msg.error(player, "Passwords do not match!");
                    case ALREADY_REGISTERED -> Msg.error(player, "Account already exists! Use /login <password>");
                    case TOO_MANY_ACCOUNTS -> Msg.error(player, "Maximum 2 accounts per IP address!");
                    case ERROR -> Msg.error(player, "Registration failed! Please try again.");
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
