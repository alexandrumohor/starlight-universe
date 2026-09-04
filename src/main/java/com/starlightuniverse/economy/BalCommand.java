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

        Component sep = Component.text(" | ", GRAY);
        Component line = Msg.prefix()
                .append(Component.text("Your balance: ", GRAY))
                .append(EconomyManager.moneyText(money))
                .append(sep)
                .append(EconomyManager.gemsText(gems))
                .append(sep)
                .append(EconomyManager.starsText(stars));

        player.sendMessage(line);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
