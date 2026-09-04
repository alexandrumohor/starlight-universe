package com.starlightuniverse.travel;

import com.starlightuniverse.StarlightUniverse;
import com.starlightuniverse.util.Msg;
import com.starlightuniverse.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SpawnCommand extends Command {

    public SpawnCommand() {
        super("spawn");
        setDescription("Teleport back to the survival lobby");
        setUsage("/spawn");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        World world = WorldManager.findWorld(WorldManager.SURVIVAL_LOBBY);
        if (world == null) {
            Msg.error(player, "Survival lobby is not available!");
            return true;
        }

        for (Entity passenger : new ArrayList<>(player.getPassengers())) {
            if (passenger instanceof TextDisplay) {
                player.removePassenger(passenger);
                passenger.remove();
            }
        }
        if (player.isInsideVehicle()) player.leaveVehicle();

        player.setFallDistance(0);
        player.setVelocity(new Vector(0, 0, 0));
        player.setNoDamageTicks(40);
        player.setFireTicks(0);

        player.teleportAsync(world.getSpawnLocation(), PlayerTeleportEvent.TeleportCause.COMMAND)
                .thenAccept(success -> Bukkit.getScheduler().runTask(StarlightUniverse.getInstance(), () -> {
                    if (!player.isOnline()) return;
                    if (Boolean.TRUE.equals(success)) {
                        player.setFallDistance(0);
                        player.setVelocity(new Vector(0, 0, 0));
                        player.setNoDamageTicks(40);
                        player.setFireTicks(0);
                        Msg.success(player, "Welcome back to Survival Lobby!");
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    } else {
                        Msg.error(player, "Teleport was cancelled!");
                    }
                }));
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
