package com.starlightuniverse.admin;

import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public final class PunishCommands {

    private PunishCommands() {}

    public static List<Command> create(AdminManager am) {
        return List.of(
                new KickCmd(am), new FreezeCmd(am), new AdminKillCmd(am),
                new EffectCmd("setfire", "Set a player on fire", am, EffectType.FIRE),
                new EffectCmd("blind", "Blind a player", am, EffectType.BLIND),
                new EffectCmd("slow", "Slow a player", am, EffectType.SLOW),
                new EffectCmd("poison", "Poison a player", am, EffectType.POISON));
    }

    private static class KickCmd extends AdminCommand {
        KickCmd(AdminManager am) {
            super("kick", "Kick a player", "/kick <player> <reason>", am, AdminRank.MODERATOR.getLevel());
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length < 2) { Msg.error(player, "Usage: /kick <player> <reason>"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { Msg.error(player, "Player not online!"); return true; }
            String reason = joinArgs(args, 1);
            target.kick(Component.text("[SU] Kicked!\nReason: " + reason + "\nBy: " + player.getName()));
            Msg.success(player, "Kicked " + target.getName() + ". Reason: " + reason);
            return true;
        }
    }

    private static class FreezeCmd extends AdminCommand {
        FreezeCmd(AdminManager am) {
            super("freeze", "Freeze/unfreeze a player", "/freeze <player>", am, AdminRank.MODERATOR.getLevel());
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /freeze <player>"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { Msg.error(player, "Player not online!"); return true; }
            boolean frozen = adminManager.toggleFreeze(target.getUniqueId());
            if (frozen) {
                Msg.success(player, "Frozen " + target.getName() + ".");
                Msg.error(target, "You have been frozen by staff!");
            } else {
                Msg.success(player, "Unfrozen " + target.getName() + ".");
                Msg.success(target, "You have been unfrozen.");
            }
            return true;
        }
    }

    private static class AdminKillCmd extends AdminCommand {
        AdminKillCmd(AdminManager am) {
            super("kill", "Kill a player", "/kill <player>", am, AdminRank.MODERATOR.getLevel());
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length != 1) { Msg.error(player, "Usage: /kill <player>"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { Msg.error(player, "Player not online!"); return true; }
            target.setHealth(0);
            Msg.success(player, "Killed " + target.getName() + ".");
            return true;
        }
    }

    private enum EffectType { FIRE, BLIND, SLOW, POISON }

    private static class EffectCmd extends AdminCommand {
        private final EffectType type;
        EffectCmd(String name, String desc, AdminManager am, EffectType type) {
            super(name, desc, "/" + name + " <player> [seconds]", am, AdminRank.MODERATOR.getLevel());
            this.type = type;
        }
        @Override protected boolean onCommand(Player player, String[] args) {
            if (args.length < 1) { Msg.error(player, "Usage: /" + getName() + " <player> [seconds]"); return true; }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { Msg.error(player, "Player not online!"); return true; }
            int seconds = 10;
            if (args.length >= 2) {
                try { seconds = Integer.parseInt(args[1]); } catch (NumberFormatException e) { Msg.error(player, "Invalid seconds!"); return true; }
            }
            if (seconds <= 0 || seconds > 600) { Msg.error(player, "Seconds must be 1-600!"); return true; }
            switch (type) {
                case FIRE -> target.setFireTicks(seconds * 20);
                case BLIND -> target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, seconds * 20, 0));
                case SLOW -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 2));
                case POISON -> target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, seconds * 20, 1));
            }
            String effectName = type.name().toLowerCase();
            Msg.success(player, "Applied " + effectName + " to " + target.getName() + " for " + seconds + "s.");
            Msg.error(target, "A moderator applied " + effectName + " on you!");
            return true;
        }
    }
}
