package com.starlightuniverse.vote;

import com.starlightuniverse.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class VoteCommand {

    private VoteCommand() {}

    public static List<Command> create(VoteManager manager) {
        return List.of(
                new Command("vote") {
                    { setDescription("Open the vote GUI"); setUsage("/vote"); }
                    @Override
                    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                        if (!(sender instanceof Player player)) return true;
                        manager.openVoteGui(player);
                        return true;
                    }
                },
                new Command("awardvote") {
                    { setDescription("Award a vote (op-only)"); setUsage("/awardvote <username> <linkId>"); }
                    @Override
                    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                        if (!sender.isOp()) {
                            sender.sendMessage(Msg.errorComponent("No permission."));
                            return true;
                        }
                        if (args.length < 2) {
                            sender.sendMessage(Msg.errorComponent("Usage: /awardvote <username> <linkId>"));
                            return true;
                        }
                        int linkId;
                        try { linkId = Integer.parseInt(args[1]); } catch (NumberFormatException e) {
                            sender.sendMessage(Msg.errorComponent("Invalid link ID."));
                            return true;
                        }
                        manager.awardVote(args[0], linkId);
                        sender.sendMessage(Msg.prefix().append(
                                net.kyori.adventure.text.Component.text("Vote awarded to " + args[0] + " (link " + linkId + ")",
                                        net.kyori.adventure.text.format.TextColor.color(0x55FF55))));
                        return true;
                    }
                }
        );
    }
}
