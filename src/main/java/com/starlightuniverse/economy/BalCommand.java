package com.starlightuniverse.economy;

import com.starlightuniverse.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BalCommand extends Command {

    private static final TextColor GOLD = TextColor.color(0xFFD700);
    private static final TextColor GREEN = TextColor.color(0x55FF55);
    private static final TextColor CYAN = TextColor.color(0x55FFFF);
    private static final TextColor VIOLET = TextColor.color(0xAA00AA);
    private static final TextColor GRAY = TextColor.color(0xAAAAAA);

    private final EconomyManager economyManager;

    public BalCommand(EconomyManager economyManager) {
        super("bal");
        setDescription("View your balance");
        setUsage("/bal");
        setAliases(List.of("balance", "money"));
        this.economyManager = economyManager;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;

        double money = economyManager.getMoney(player.getUniqueId());
        double gems = economyManager.getGems(player.getUniqueId());
        double stars = economyManager.getStars(player.getUniqueId());

        player.sendMessage(Msg.prefix().append(Component.text("Your balance:", GRAY)));

        player.sendMessage(Component.text("  ")
                .append(Component.text(EconomyManager.MONEY_ICON + " ", GOLD))
                .append(Component.text("$" + EconomyManager.format(money), GREEN)));

        player.sendMessage(Component.text("  ")
                .append(Component.text(EconomyManager.GEMS_ICON + " ", CYAN))
                .append(Component.text("◆" + EconomyManager.format(gems), CYAN)));

        player.sendMessage(Component.text("  ")
                .append(Component.text(EconomyManager.STARS_ICON + " ", VIOLET))
                .append(Component.text("★" + EconomyManager.format(stars), VIOLET)));

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
