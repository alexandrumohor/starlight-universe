package com.starlightuniverse.admin;

import com.starlightuniverse.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BannerCommand extends Command {

    private static final Material BLOCK = Material.VERDANT_FROGLIGHT;
    private static final double SCALE = 2.7;
    private static final int LETTER_HEIGHT = 7;
    private static final int LETTER_GAP = 1;
    private static final int LINE_GAP = 3;

    private static final Map<Character, String[]> FONT = buildFont();
    private static final List<Location> lastPlacedBlocks = new ArrayList<>();

    private final JavaPlugin plugin;

    public BannerCommand(JavaPlugin plugin) {
        super("serverbanner");
        setDescription("Spawn the STARLIGHT UNIVERSE banner");
        setUsage("/serverbanner");
        this.plugin = plugin;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            if (sender instanceof Player p) Msg.error(p, "Only operators can use this!");
            return true;
        }
        if (!(sender instanceof Player player)) return true;

        Location loc = player.getLocation();
        World world = loc.getWorld();
        Direction dir = Direction.fromYaw(loc.getYaw());

        String line1 = "STARLIGHT";
        String line2 = "UNIVERSE";

        int pixWidth1 = pixelTextWidth(line1);
        int pixWidth2 = pixelTextWidth(line2);
        int maxPixWidth = Math.max(pixWidth1, pixWidth2);

        int pixOffset1 = (maxPixWidth - pixWidth1) / 2;
        int pixOffset2 = (maxPixWidth - pixWidth2) / 2;

        int eCharStart = pixelCharStart(line2, 4);
        int anchorPixCol = pixOffset2 + eCharStart + 2;
        int anchorScaledCol = s(anchorPixCol) + (s(anchorPixCol + 1) - s(anchorPixCol)) / 2;

        int originX = loc.getBlockX() - dir.dx * anchorScaledCol;
        int originZ = loc.getBlockZ() - dir.dz * anchorScaledCol;

        int baseY2 = loc.getBlockY() - s(1);
        int baseY1 = baseY2 + s(LETTER_HEIGHT) + s(LINE_GAP);

        lastPlacedBlocks.clear();

        int placed = 0;
        placed += placeText(world, originX, baseY1, originZ, dir, line1, pixOffset1);
        placed += placeText(world, originX, baseY2, originZ, dir, line2, pixOffset2);

        int totalW = s(maxPixWidth);
        int totalH = s(LETTER_HEIGHT * 2 + LINE_GAP);
        Msg.success(player, "Banner placed! " + placed + " blocks, " + totalW + "x" + totalH + ".");
        return true;
    }

    private int placeText(World world, int startX, int baseY, int startZ, Direction dir, String text, int pixOffset) {
        int placed = 0;
        int pixCursor = pixOffset;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String[] glyph = FONT.get(c);
            if (glyph == null) {
                pixCursor += 4 + LETTER_GAP;
                continue;
            }

            int glyphWidth = glyph[0].length();

            for (int row = 0; row < LETTER_HEIGHT; row++) {
                int yFrom = baseY + s(LETTER_HEIGHT - 1 - row);
                int yTo = baseY + s(LETTER_HEIGHT - row);
                for (int col = 0; col < glyphWidth; col++) {
                    if (glyph[row].charAt(col) == '#') {
                        int hFrom = s(pixCursor + col);
                        int hTo = s(pixCursor + col + 1);
                        for (int y = yFrom; y < yTo; y++) {
                            for (int h = hFrom; h < hTo; h++) {
                                int bx = startX + dir.dx * h;
                                int bz = startZ + dir.dz * h;
                                Block block = world.getBlockAt(bx, y, bz);
                                block.setType(BLOCK);
                                lastPlacedBlocks.add(block.getLocation());
                                placed++;
                            }
                        }
                    }
                }
            }

            pixCursor += glyphWidth + LETTER_GAP;
        }

        return placed;
    }

    private static int s(int pixel) {
        return (int) (pixel * SCALE);
    }

    private int pixelTextWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            String[] glyph = FONT.get(text.charAt(i));
            width += (glyph != null ? glyph[0].length() : 4);
            if (i < text.length() - 1) width += LETTER_GAP;
        }
        return width;
    }

    private int pixelCharStart(String text, int charIndex) {
        int pos = 0;
        for (int i = 0; i < charIndex; i++) {
            String[] glyph = FONT.get(text.charAt(i));
            pos += (glyph != null ? glyph[0].length() : 4) + LETTER_GAP;
        }
        return pos;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (!sender.isOp() || !(sender instanceof Player player)) return List.of();
        Location loc = player.getLocation();
        if (args.length == 1) return List.of(String.valueOf(loc.getBlockX()));
        if (args.length == 2) return List.of(String.valueOf(loc.getBlockY()));
        if (args.length == 3) return List.of(String.valueOf(loc.getBlockZ()));
        return List.of();
    }

    private enum Direction {
        SOUTH(-1, 0),
        WEST(0, -1),
        NORTH(1, 0),
        EAST(0, 1);

        final int dx, dz;

        Direction(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        static Direction fromYaw(float yaw) {
            yaw = ((yaw % 360) + 360) % 360;
            if (yaw >= 315 || yaw < 45) return SOUTH;
            if (yaw >= 45 && yaw < 135) return WEST;
            if (yaw >= 135 && yaw < 225) return NORTH;
            return EAST;
        }
    }

    public static Command createRemoveCommand() {
        return new Command("removebanner") {
            {
                setDescription("Remove the last placed banner");
                setUsage("/removebanner");
            }

            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
                if (!sender.isOp()) {
                    if (sender instanceof Player p) Msg.error(p, "Only operators can use this!");
                    return true;
                }
                if (lastPlacedBlocks.isEmpty()) {
                    if (sender instanceof Player p) Msg.error(p, "No banner to remove!");
                    return true;
                }
                int count = lastPlacedBlocks.size();
                for (Location loc : lastPlacedBlocks) {
                    loc.getBlock().setType(Material.AIR);
                }
                lastPlacedBlocks.clear();
                if (sender instanceof Player p) Msg.success(p, "Banner removed! " + count + " blocks cleared.");
                return true;
            }
        };
    }

    private static Map<Character, String[]> buildFont() {
        return Map.ofEntries(
            Map.entry('A', new String[]{
                ".###.",
                "#...#",
                "#...#",
                "#####",
                "#...#",
                "#...#",
                "#...#"
            }),
            Map.entry('B', new String[]{
                "####.",
                "#...#",
                "#...#",
                "####.",
                "#...#",
                "#...#",
                "####."
            }),
            Map.entry('C', new String[]{
                ".###.",
                "#...#",
                "#....",
                "#....",
                "#....",
                "#...#",
                ".###."
            }),
            Map.entry('D', new String[]{
                "####.",
                "#...#",
                "#...#",
                "#...#",
                "#...#",
                "#...#",
                "####."
            }),
            Map.entry('E', new String[]{
                "#####",
                "#....",
                "#....",
                "####.",
                "#....",
                "#....",
                "#####"
            }),
            Map.entry('F', new String[]{
                "#####",
                "#....",
                "#....",
                "####.",
                "#....",
                "#....",
                "#...."
            }),
            Map.entry('G', new String[]{
                ".###.",
                "#...#",
                "#....",
                "#.###",
                "#...#",
                "#...#",
                ".###."
            }),
            Map.entry('H', new String[]{
                "#...#",
                "#...#",
                "#...#",
                "#####",
                "#...#",
                "#...#",
                "#...#"
            }),
            Map.entry('I', new String[]{
                "###",
                ".#.",
                ".#.",
                ".#.",
                ".#.",
                ".#.",
                "###"
            }),
            Map.entry('J', new String[]{
                ".####",
                "...#.",
                "...#.",
                "...#.",
                "...#.",
                "#..#.",
                ".##.."
            }),
            Map.entry('K', new String[]{
                "#...#",
                "#..#.",
                "#.#..",
                "##...",
                "#.#..",
                "#..#.",
                "#...#"
            }),
            Map.entry('L', new String[]{
                "#....",
                "#....",
                "#....",
                "#....",
                "#....",
                "#....",
                "#####"
            }),
            Map.entry('M', new String[]{
                "#...#",
                "##.##",
                "#.#.#",
                "#...#",
                "#...#",
                "#...#",
                "#...#"
            }),
            Map.entry('N', new String[]{
                "#...#",
                "##..#",
                "#.#.#",
                "#..##",
                "#...#",
                "#...#",
                "#...#"
            }),
            Map.entry('O', new String[]{
                ".###.",
                "#...#",
                "#...#",
                "#...#",
                "#...#",
                "#...#",
                ".###."
            }),
            Map.entry('P', new String[]{
                "####.",
                "#...#",
                "#...#",
                "####.",
                "#....",
                "#....",
                "#...."
            }),
            Map.entry('Q', new String[]{
                ".###.",
                "#...#",
                "#...#",
                "#...#",
                "#.#.#",
                "#..#.",
                ".##.#"
            }),
            Map.entry('R', new String[]{
                "####.",
                "#...#",
                "#...#",
                "####.",
                "#.#..",
                "#..#.",
                "#...#"
            }),
            Map.entry('S', new String[]{
                ".####",
                "#....",
                "#....",
                ".###.",
                "....#",
                "....#",
                "####."
            }),
            Map.entry('T', new String[]{
                "#####",
                "..#..",
                "..#..",
                "..#..",
                "..#..",
                "..#..",
                "..#.."
            }),
            Map.entry('U', new String[]{
                "#...#",
                "#...#",
                "#...#",
                "#...#",
                "#...#",
                "#...#",
                ".###."
            }),
            Map.entry('V', new String[]{
                "#...#",
                "#...#",
                "#...#",
                "#...#",
                ".#.#.",
                ".#.#.",
                "..#.."
            }),
            Map.entry('W', new String[]{
                "#...#",
                "#...#",
                "#...#",
                "#.#.#",
                "#.#.#",
                "##.##",
                "#...#"
            }),
            Map.entry('X', new String[]{
                "#...#",
                "#...#",
                ".#.#.",
                "..#..",
                ".#.#.",
                "#...#",
                "#...#"
            }),
            Map.entry('Y', new String[]{
                "#...#",
                "#...#",
                ".#.#.",
                "..#..",
                "..#..",
                "..#..",
                "..#.."
            }),
            Map.entry('Z', new String[]{
                "#####",
                "....#",
                "...#.",
                "..#..",
                ".#...",
                "#....",
                "#####"
            }),
            Map.entry(' ', new String[]{
                "...",
                "...",
                "...",
                "...",
                "...",
                "...",
                "..."
            })
        );
    }
}
