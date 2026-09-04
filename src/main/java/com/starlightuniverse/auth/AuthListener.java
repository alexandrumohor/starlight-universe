package com.starlightuniverse.auth;

import com.starlightuniverse.StarlightUniverse;
import com.starlightuniverse.util.Msg;
import com.starlightuniverse.world.InventoryManager;
import com.starlightuniverse.world.WorldManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.plugin.java.JavaPlugin;

public class AuthListener implements Listener {

    private final JavaPlugin plugin;
    private final AuthManager authManager;
    private final SkinManager skinManager;

    public AuthListener(JavaPlugin plugin, AuthManager authManager, SkinManager skinManager) {
        this.plugin = plugin;
        this.authManager = authManager;
        this.skinManager = skinManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String ip = event.getAddress() != null ? event.getAddress().getHostAddress() : null;
        if (ip == null) return;
        int already = authManager.countOnlineWithIp(ip);
        if (already >= AuthManager.MAX_SIMULTANEOUS_ACCOUNTS_PER_IP) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("[SU] ", TextColor.color(0xFFD700))
                            .append(Component.text(
                                    "Maximum " + AuthManager.MAX_SIMULTANEOUS_ACCOUNTS_PER_IP
                                            + " accounts allowed per IP address!",
                                    TextColor.color(0xFF5555))));
            return;
        }

        // Pre-load the skin BEFORE the client fully joins. Cracked / TLauncher
        // clients lock the local player's skin to whatever was on the profile
        // at connection time and never re-render it mid-session, so patching
        // it later via setPlayerProfile only fixes what OTHER players see.
        // Applying it here means the client renders the correct skin from
        // frame one.
        try {
            String username = event.getName();
            String premiumUuid = authManager.getPremiumUuid(username);
            SkinManager.SkinData skin = null;
            if (premiumUuid != null) {
                skin = skinManager.fetchMojangSkin(premiumUuid);
            }
            if (skin == null) {
                AuthManager.MojangProfile mojang = authManager.checkMojangPremium(username);
                if (mojang != null) skin = skinManager.fetchMojangSkin(mojang.id());
            }
            if (skin != null) {
                var profile = event.getPlayerProfile();
                profile.removeProperty("textures");
                profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty(
                        "textures", skin.value(), skin.signature()));
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[SU] Pre-login skin injection failed for "
                    + event.getName() + ": " + t.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String username = player.getName();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";

        prepareForLobby(player);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getLogger().info("[SU][auth] async start for " + username + " ip=" + ip);
            boolean registered;
            try {
                registered = authManager.isRegistered(username);
                plugin.getLogger().info("[SU][auth] " + username + " registered=" + registered);
            } catch (Throwable t) {
                plugin.getLogger().warning("[SU][auth] isRegistered threw: " + t);
                return;
            }

            if (registered) {
                // Check launcher FIRST. Only the official Minecraft launcher sends the
                // real Mojang UUID during handshake — TLauncher/cracked send an offline
                // UUID derived from the name. If (and only if) the client UUID matches
                // Mojang's real UUID, we auto-login. Anything else requires /login,
                // regardless of any active session cache.
                String premiumUuid = authManager.getPremiumUuid(username);
                AuthManager.MojangProfile profile = premiumUuid != null
                        ? authManager.checkMojangPremium(username) : null;
                var verifier = StarlightUniverse.getInstance().getPremiumSessionVerifier();
                java.util.Optional<java.util.UUID> clientUuid = verifier != null
                        ? verifier.consumeClientUuid(username)
                        : java.util.Optional.empty();
                boolean launcherVerified = profile != null
                        && clientUuid.isPresent()
                        && sameUuid(clientUuid.get(), profile.id());
                plugin.getLogger().info("[SU][auth] " + username
                        + " premium-uuid=" + premiumUuid
                        + " mojang=" + (profile != null ? profile.id() : "null")
                        + " client=" + clientUuid.orElse(null)
                        + " launcherVerified=" + launcherVerified);

                if (launcherVerified) {
                    authManager.saveSession(username, ip);
                    SkinManager.SkinData skin = skinManager.fetchMojangSkin(premiumUuid);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            authManager.setAuthenticated(player.getUniqueId());
                            player.setHealth(player.getMaxHealth());
                            player.setFoodLevel(20);
                            player.setSaturation(20f);
                            player.setFireTicks(0);
                            com.starlightuniverse.world.LobbyManager.ensureInLobby(player);
                            Msg.success(player, "Premium account verified! Auto-login successful.");
                            if (skin != null) skinManager.applySkin(player, skin);
                            sendHeadOverlay(player);
                            StarlightUniverse.getInstance().getLobbyManager().giveSurvivalItem(player);
                            com.starlightuniverse.announce.WelcomeMessage.send(player);
                        }
                    });
                    return;
                }

                boolean hasPw = authManager.hasPassword(username);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        if (hasPw) {
                            Msg.info(player, "Welcome back! Type /login <password>");
                        } else {
                            Msg.info(player, "Set a password: /register <password> <confirm>");
                        }
                    }
                });
            } else {
                // First-time join: ALWAYS require /register, even for premium accounts.
                plugin.getLogger().info("[SU][auth] " + username + " → unregistered branch");
                AuthManager.MojangProfile mojang = null;
                try {
                    mojang = authManager.checkMojangPremium(username);
                    plugin.getLogger().info("[SU][auth] checkMojangPremium(" + username + ") returned "
                            + (mojang == null ? "null (not premium)" : "id=" + mojang.id()));
                } catch (Throwable t) {
                    plugin.getLogger().warning("[SU][auth] checkMojangPremium threw: " + t);
                }
                // Skin fetch is a blocking HTTP call — do it here on the async thread.
                SkinManager.SkinData skinToApply;
                if (mojang != null) {
                    skinToApply = skinManager.fetchMojangSkin(mojang.id());
                    plugin.getLogger().info("[SU][auth] Mojang skin path → skinData="
                            + (skinToApply == null ? "null" : "OK"));
                } else {
                    skinToApply = skinManager.getRandomSkin();
                    plugin.getLogger().info("[SU][auth] Random skin path → skinData="
                            + (skinToApply == null ? "null (cache empty?)" : "OK"));
                }
                final SkinManager.SkinData finalSkin = skinToApply;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    Msg.info(player, "Welcome! Type /register <password> <confirm>");
                    if (finalSkin != null) skinManager.applySkin(player, finalSkin);
                });
            }
        });
    }

    /** Compare a runtime UUID with Mojang's undashed 32-char hex id string. */
    private static boolean sameUuid(java.util.UUID uuid, String mojangHex) {
        if (uuid == null || mojangHex == null) return false;
        String clean = mojangHex.replace("-", "").toLowerCase();
        String from = uuid.toString().replace("-", "").toLowerCase();
        return clean.equals(from);
    }

    private void sendHeadOverlay(Player player) {
        var headMgr = StarlightUniverse.getInstance().getPlayerHeadPackManager();
        if (headMgr != null) headMgr.sendHeadOverlayAfterAuth(player);
        // Spawn nameplate now that the player is in their final world (lobby).
        // Delayed 1 tick so the teleport from ensureInLobby is fully applied first.
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
    }

    /**
     * Resolves a skin for the given username. Prefers stored premium_uuid → Mojang skin,
     * falls back to live checkMojangPremium, finally to a random cached skin.
     * Blocking — call from async thread only.
     */
    private SkinManager.SkinData fetchSkinFor(String username) {
        try {
            String uuid = authManager.getPremiumUuid(username);
            if (uuid != null) {
                SkinManager.SkinData s = skinManager.fetchMojangSkin(uuid);
                if (s != null) return s;
            }
            AuthManager.MojangProfile mojang = authManager.checkMojangPremium(username);
            if (mojang != null) {
                SkinManager.SkinData s = skinManager.fetchMojangSkin(mojang.id());
                if (s != null) return s;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[SU][auth] fetchSkinFor threw: " + t);
        }
        return skinManager.getRandomSkin();
    }

    private void prepareForLobby(Player player) {
        WorldManager.WorldGroup currentGroup = WorldManager.getWorldGroup(player.getWorld());
        if (currentGroup == WorldManager.WorldGroup.SURVIVAL) {
            InventoryManager invManager = StarlightUniverse.getInstance().getInventoryManager();
            if (invManager != null) {
                invManager.saveInventory(player, WorldManager.WorldGroup.SURVIVAL);
            }
        }

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setLevel(0);
        player.setExp(0);
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setHealth(player.getMaxHealth());
        player.setGameMode(GameMode.ADVENTURE);
        // Suppress vanilla "Flying is not enabled" kick during the auth window
        // and while the player sits idle in lobby waiting to /login or /register.
        player.setAllowFlight(true);
        player.setFlying(false);

        World lobby = WorldManager.findWorld(WorldManager.LOBBY);
        if (lobby != null && !player.getWorld().equals(lobby)) {
            player.teleport(lobby.getSpawnLocation());
        } else if (lobby == null) {
            plugin.getLogger().warning("[SU] Lobby world 'lobby' is not loaded — player "
                    + player.getName() + " will stay in world '" + player.getWorld().getName() + "'.");
        }
        // Force midnight client-side so the lobby looks the same before and after
        // login. Without this, cracked clients (TLauncher) render the world at
        // the client's default time until they receive the server's world state,
        // creating a jarring "day → midnight" flash the moment /login succeeds.
        player.setPlayerTime(18000L, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (authManager.isAuthenticated(player.getUniqueId())) {
            String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    authManager.saveSession(player.getName(), ip));
        }
        authManager.removeAuthenticated(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (authManager.isAuthenticated(event.getPlayer().getUniqueId())) return;
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            event.setTo(from.clone().setDirection(to.getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () ->
                    Msg.error(event.getPlayer(), "You must log in first!"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (authManager.isAuthenticated(event.getPlayer().getUniqueId())) return;
        String cmd = event.getMessage().toLowerCase().split(" ")[0];
        if (cmd.equals("/register") || cmd.equals("/login") || cmd.equals("/changepass")) return;
        event.setCancelled(true);
        Msg.error(event.getPlayer(), "You must log in first!");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !authManager.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventory(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && !authManager.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !authManager.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && !authManager.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && !authManager.isAuthenticated(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!authManager.isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
