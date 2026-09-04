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

            if (result == AuthManager.RegisterResult.SUCCESS) {
                // Save premium UUID so future logins can auto-authenticate via Mojang.
                AuthManager.MojangProfile mojang = authManager.checkMojangPremium(username);
                if (mojang != null) {
                    authManager.setPremiumUuid(username, mojang.id());
                }
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                switch (result) {
                    case SUCCESS -> {
                        authManager.setAuthenticated(player.getUniqueId());
                        player.setHealth(player.getMaxHealth());
                        player.setFoodLevel(20);
                        player.setSaturation(20f);
                        player.setFireTicks(0);
                        com.starlightuniverse.world.LobbyManager.ensureInLobby(player);
                        var headMgr = StarlightUniverse.getInstance().getPlayerHeadPackManager();
                        if (headMgr != null) headMgr.sendHeadOverlayAfterAuth(player);
                        var np = StarlightUniverse.getInstance().getNameplateManager();
                        if (np != null) {
                            Bukkit.getScheduler().runTaskLater(StarlightUniverse.getInstance(), () -> {
                                if (!player.isOnline()) return;
                                np.spawnFor(player);
                                for (Player other : player.getWorld().getPlayers()) {
                                    if (other.equals(player)) continue;
                                    np.remount(other);
                                }
                            }, 10L);
                        }
                        Msg.success(player, "Account registered successfully! You are now logged in.");
                        StarlightUniverse.getInstance().getLobbyManager().giveSurvivalItem(player);
                        com.starlightuniverse.announce.WelcomeMessage.send(player);
                    }
                    case PASSWORD_TOO_SHORT -> Msg.error(player, "Password must be at least 3 characters!");
                    case PASSWORD_MISMATCH -> Msg.error(player, "Passwords do not match!");
                    case ALREADY_REGISTERED -> Msg.error(player, "Account already exists! Use /login <password>");
                    case TOO_MANY_ACCOUNTS -> Msg.error(player, "Maximum 3 accounts allowed per IP address!");
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
