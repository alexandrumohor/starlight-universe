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

            SkinManager.SkinData skin = null;
            if (result == AuthManager.LoginResult.SUCCESS) {
                authManager.saveSession(username, ip);
                // Re-fetch skin — profile textures reset each session.
                SkinManager sm = StarlightUniverse.getInstance().getSkinManager();
                if (sm != null) {
                    String uuid = authManager.getPremiumUuid(username);
                    if (uuid != null) skin = sm.fetchMojangSkin(uuid);
                    if (skin == null) {
                        AuthManager.MojangProfile p = authManager.checkMojangPremium(username);
                        if (p != null) skin = sm.fetchMojangSkin(p.id());
                    }
                    if (skin == null) skin = sm.getRandomSkin();
                }
            }
            final SkinManager.SkinData finalSkin = skin;

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
                        SkinManager sm = StarlightUniverse.getInstance().getSkinManager();
                        if (finalSkin != null && sm != null) sm.applySkin(player, finalSkin);
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
                        Msg.success(player, "Successfully logged in! Welcome back.");
                        StarlightUniverse.getInstance().getLobbyManager().giveSurvivalItem(player);
                        com.starlightuniverse.announce.WelcomeMessage.send(player);
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
