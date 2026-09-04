package com.starlightuniverse.chat;

import com.starlightuniverse.admin.AdminRank;
import com.starlightuniverse.premium.PremiumRank;
import com.starlightuniverse.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ChatCommands {

    private ChatCommands() {}

    public static List<Command> create(ChatManager chatManager, ChatListener chatListener) {
        List<Command> commands = new ArrayList<>();

        commands.add(new Command("g") {
            { setDescription("Switch to global chat"); }

            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player player)) return false;
                chatManager.switchToGlobal(player.getUniqueId());
                Msg.success(player, "Switched to Global chat.");
                return true;
            }
        });

        commands.add(new Command("tc") {
            { setDescription("Switch to team chat"); }

            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player player)) return false;
                UUID uuid = player.getUniqueId();

                if (chatManager.getTeamManager().getPlayerTeam(uuid) == null) {
                    Msg.error(player, "You are not in a team!");
                    return true;
                }

                if (chatManager.getEffectiveChannel(uuid) == ChatManager.ChatChannel.TEAM) {
                    chatManager.switchToGlobal(uuid);
                    Msg.success(player, "Switched to Global chat.");
                } else {
                    chatManager.switchToTeam(uuid);
                    Msg.success(player, "Switched to Team chat. Messages go to your team only.");
                }
                return true;
            }
        });

        commands.add(new Command("sc") {
            { setDescription("Switch to staff chat"); }

            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player player)) return false;
                UUID uuid = player.getUniqueId();

                if (chatManager.getAdminManager().getAdminLevel(uuid) == 0) {
                    Msg.error(player, "Staff only!");
                    return true;
                }

                if (chatManager.getEffectiveChannel(uuid) == ChatManager.ChatChannel.STAFF) {
                    chatManager.switchToGlobal(uuid);
                    Msg.success(player, "Switched to Global chat.");
                } else {
                    chatManager.switchToStaff(uuid);
                    Msg.success(player, "Switched to Staff chat.");
                }
                return true;
            }
        });

        commands.add(new Command("l") {
            { setDescription("Switch to local chat (100 blocks)"); }

            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player player)) return false;
                UUID uuid = player.getUniqueId();

                if (chatManager.getEffectiveChannel(uuid) == ChatManager.ChatChannel.LOCAL) {
                    chatManager.switchToGlobal(uuid);
                    Msg.success(player, "Switched to Global chat.");
                } else {
                    chatManager.switchToLocal(uuid);
                    Msg.success(player, "Switched to Local chat (100 blocks).");
                }
                return true;
            }
        });

        commands.add(new Command("settag") {
            { setDescription("Set your custom chat tag");
              setUsage("/settag <text> or /settag clear"); }

            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player player)) return false;

                PremiumRank rank = chatManager.getPremiumManager().getPlayerRank(player.getUniqueId());
                if (rank == PremiumRank.NONE) {
                    Msg.error(player, "You need a premium rank to set a custom tag!");
                    return true;
                }

                if (args.length == 0) {
                    Msg.info(player, "Usage: /settag <text> or /settag clear");
                    String current = chatManager.getNameTag(player.getUniqueId());
                    if (current != null) {
                        Msg.gray(player, "Current tag: [" + current + "]");
                    }
                    return true;
                }

                String tag = String.join(" ", args);

                if (tag.equalsIgnoreCase("clear") || tag.equalsIgnoreCase("remove")) {
                    chatManager.setNameTag(player, null);
                    Msg.success(player, "Tag removed. Your premium rank will show instead.");
                    return true;
                }

                if (tag.length() > 16) {
                    Msg.error(player, "Tag must be 16 characters or less!");
                    return true;
                }

                if (!tag.matches("[a-zA-Z0-9_ ]+")) {
                    Msg.error(player, "Tag can only contain letters, numbers, spaces, and underscores!");
                    return true;
                }

                chatManager.setNameTag(player, tag);
                Msg.success(player, "Tag set to [" + tag + "]!");
                return true;
            }
            @Override
            public List<String> tabComplete(CommandSender s, String a, String[] args) {
                if (args.length == 1) {
                    return List.of("clear").stream()
                            .filter(x -> x.startsWith(args[0].toLowerCase())).toList();
                }
                return List.of();
            }
        });

        Command replyCmd = new Command("reply", "Reply to a player in public chat",
                "/reply <player> <message> or /r <message>", List.of("r")) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player player)) return false;

                if (args.length == 0) {
                    Msg.info(player, "Usage: /reply <player> <message> or /r <message>");
                    return true;
                }

                String targetName;
                String message;

                Player firstArgPlayer = Bukkit.getPlayerExact(args[0]);
                if (firstArgPlayer != null && args.length >= 2) {
                    targetName = firstArgPlayer.getName();
                    message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                } else {
                    UUID replyTarget = chatManager.getReplyTarget(player.getUniqueId());
                    if (replyTarget == null) {
                        Msg.error(player, "No one to reply to! Use /reply <player> <message>");
                        return true;
                    }
                    Player target = Bukkit.getPlayer(replyTarget);
                    if (target == null || !target.isOnline()) {
                        Msg.error(player, "That player is no longer online!");
                        return true;
                    }
                    targetName = target.getName();
                    message = String.join(" ", args);
                }

                if (message.isEmpty()) {
                    Msg.error(player, "Message cannot be empty!");
                    return true;
                }

                UUID uuid = player.getUniqueId();

                if (!chatManager.canChat(uuid)) {
                    long remaining = chatManager.getCooldownRemaining(uuid);
                    Msg.error(player, "Chat cooldown! Wait " +
                            String.format("%.1f", remaining / 1000.0) + "s.");
                    return true;
                }

                if (!chatManager.isStaffExempt(uuid) && chatManager.containsLink(message)) {
                    Msg.error(player, "Links are not allowed in chat!");
                    return true;
                }

                chatManager.recordChatCooldown(uuid);

                chatListener.sendReplyMessage(player, targetName, message);

                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null) {
                    chatManager.setReplyTarget(target.getUniqueId(), player.getUniqueId());
                }

                return true;
            }
            @Override
            public List<String> tabComplete(CommandSender s, String a, String[] args) {
                if (args.length == 1) {
                    String pfx = args[0].toLowerCase();
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(pfx)).toList();
                }
                return List.of();
            }
        };
        commands.add(replyCmd);

        return commands;
    }
}
