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

public class LoginCommand extends Command {

    private final JavaPlugin plugin;
    private final AuthManager authManager;

    public LoginCommand(JavaPlugin plugin, AuthManager authManager) {
        super("login");
        setDescription("Log in to your account");
        setUsage("/login <password>");
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

        if (args.length != 1) {
            Msg.error(player, "Usage: /login <password>");
            return true;
        }

        String password = args[0];
        String username = player.getName();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthManager.LoginResult result = authManager.login(username, password);

            if (result == AuthManager.LoginResult.SUCCESS) {
                authManager.saveSession(username, ip);
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                switch (result) {
                    case SUCCESS -> {
                        authManager.setAuthenticated(player.getUniqueId());
                        Msg.success(player, "Successfully logged in! Welcome back.");
                        StarlightUniverse.getInstance().getLobbyManager().giveSurvivalItem(player);
                    }
                    case WRONG_PASSWORD -> Msg.error(player, "Wrong password!");
                    case NOT_REGISTERED -> Msg.error(player, "Account not found! Use /register <password> <confirm>");
                    case TOO_MANY_ATTEMPTS -> Msg.error(player, "Too many failed attempts! Wait 60 seconds.");
                    case ERROR -> Msg.error(player, "Login failed! Please try again.");
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
