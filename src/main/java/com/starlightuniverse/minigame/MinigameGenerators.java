package com.starlightuniverse.minigame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class MinigameGenerators {

    private static final Random RNG = new Random();

    private MinigameGenerators() {}

    // ==================== WORD POOLS ====================

    private static final String[] SCRAMBLE_WORDS = {
            "diamond", "emerald", "netherite", "obsidian", "cobblestone", "redstone",
            "creeper", "skeleton", "enderman", "villager", "pillager", "wolf",
            "fortress", "stronghold", "portal", "dragon", "wither", "beacon",
            "furnace", "anvil", "hopper", "dispenser", "brewing", "cauldron",
            "trident", "elytra", "totem", "shulker", "warden", "allay",
            "compass", "clock", "lantern", "campfire", "bookshelf", "chest",
            "iron", "gold", "copper", "amethyst", "lapis", "quartz",
            "pumpkin", "melon", "carrot", "potato", "beetroot", "sugarcane",
            "spruce", "birch", "oak", "jungle", "acacia", "cherry",
            "mangrove", "bamboo", "kelp", "coral", "seagrass", "prismarine",
            "starlight", "cosmic", "galaxy", "nebula", "meteor", "comet",
            "supernova", "orbit", "planet", "asteroid", "quasar", "pulsar"
    };

    private static final String[] TYPE_RACE_PHRASES = {
            "starlight universe is the best server",
            "the quick brown fox jumps over the lazy dog",
            "pack my box with five dozen liquor jugs",
            "the five boxing wizards jump quickly",
            "sphinx of black quartz judge my vow",
            "how vexingly quick daft zebras jump",
            "watch out for the creeper behind you",
            "diamonds are found deep underground",
            "always carry a bucket of water in the nether",
            "the ender dragon guards the end island",
            "trading with villagers is very profitable",
            "never dig straight down in minecraft",
            "gold armor scares off piglins in the nether",
            "the warden is blind but can hear everything",
            "shulker boxes let you carry extra inventory"
    };

    // ==================== FILL THE BLANK ====================

    private static final String[][] FILL_BLANKS = {
            {"You mine ___ with an iron pickaxe or better", "diamond"},
            {"The ___ is the final boss in Minecraft", "dragon"},
            {"A ___ explodes when it gets close to you", "creeper"},
            {"You need blaze rods to make an ___ eye", "ender"},
            {"The ___ dimension is red and full of lava", "nether"},
            {"You throw an eye of ender to find a ___", "stronghold"},
            {"Wither ___ are dropped by wither skeletons", "skulls"},
            {"You need three wither skulls and four soul ___ to spawn the wither", "sand"},
            {"An ___ dropper trades emeralds for enchanted items", "villager"},
            {"You can shear a ___ to get wool without killing it", "sheep"},
            {"Enchanted golden ___ heal a lot of hearts", "apples"},
            {"You need a ___ pickaxe to mine obsidian", "diamond"},
            {"The ___ arena in Starlight lets you fight bosses", "boss"},
            {"You can donate ___ to your team's bank", "money"},
            {"The premium rank ___ is the highest", "galaxy"}
    };

    // ==================== TRIVIA ====================

    private static final String[][] TRIVIA = {
            {"What planet is closest to the sun?", "mercury"},
            {"How many hearts does a player have by default?", "10"},
            {"What color is a creeper?", "green"},
            {"How many eyes of ender are needed to activate an end portal?", "12"},
            {"What is the maximum enchant level for Sharpness?", "5"},
            {"What mob drops ender pearls?", "enderman"},
            {"How many players can be on Ender dragon at once? (max fight participants)", "1"},
            {"What is 2 to the power of 10?", "1024"},
            {"What continent is Egypt on?", "africa"},
            {"How many sides does a hexagon have?", "6"},
            {"How many players does one team need to be created (leader only)?", "1"},
            {"Which ore glows in the dark and is used for lamps?", "glowstone"},
            {"How many blocks tall is a player?", "2"},
            {"What is the currency icon for Money in Starlight?", "$"},
            {"How many premium ranks exist in Starlight?", "5"},
            {"What is the color of the sky in the End?", "purple"},
            {"Which mob is bigger, a wolf or a horse?", "horse"},
            {"How many bookshelves surround a max-level enchanting table?", "15"},
            {"What year did Minecraft first release (full release)?", "2011"},
            {"What is the highest number possible with a single Minecraft signed byte?", "127"}
    };

    // ==================== COLOR CODE ====================

    private static final String[][] COLORS = {
            {"red", "#FF0000"},
            {"green", "#00FF00"},
            {"blue", "#0000FF"},
            {"yellow", "#FFFF00"},
            {"cyan", "#00FFFF"},
            {"magenta", "#FF00FF"},
            {"white", "#FFFFFF"},
            {"black", "#000000"},
            {"orange", "#FFA500"},
            {"purple", "#800080"},
            {"pink", "#FFC0CB"},
            {"gold", "#FFD700"},
            {"gray", "#808080"},
            {"lime", "#00FF7F"},
            {"navy", "#000080"}
    };

    // ==================== CAPITALS ====================

    private static final String[][] CAPITALS = {
            {"Romania", "bucharest"},
            {"France", "paris"},
            {"Germany", "berlin"},
            {"Italy", "rome"},
            {"Spain", "madrid"},
            {"United Kingdom", "london"},
            {"Portugal", "lisbon"},
            {"Netherlands", "amsterdam"},
            {"Belgium", "brussels"},
            {"Austria", "vienna"},
            {"Poland", "warsaw"},
            {"Greece", "athens"},
            {"Hungary", "budapest"},
            {"Czech Republic", "prague"},
            {"Sweden", "stockholm"},
            {"Norway", "oslo"},
            {"Finland", "helsinki"},
            {"Denmark", "copenhagen"},
            {"Russia", "moscow"},
            {"Ukraine", "kyiv"},
            {"Turkey", "ankara"},
            {"Japan", "tokyo"},
            {"China", "beijing"},
            {"India", "delhi"},
            {"Brazil", "brasilia"},
            {"Argentina", "buenos aires"},
            {"Canada", "ottawa"},
            {"United States", "washington"},
            {"Mexico", "mexico city"},
            {"Australia", "canberra"},
            {"Egypt", "cairo"},
            {"South Africa", "pretoria"}
    };

    // ==================== ITEM GUESS ====================

    private static final String[][] ITEM_CLUES = {
            {"A red-and-white striped mushroom that grows on grass", "mushroom"},
            {"A block that emits redstone signal when a player steps on it", "pressure plate"},
            {"A tool used to shear sheep", "shears"},
            {"A mob-repelling flower that also decorates gardens", "flower"},
            {"A ranged weapon that fires arrows", "bow"},
            {"A wooden item you sit on and rides on rails", "minecart"},
            {"An underwater sea mob that shoots laser beams", "guardian"},
            {"A hostile mob made entirely of skeletons riding spiders", "spider jockey"},
            {"An item that lets you fly by gliding through the air", "elytra"},
            {"A block you use to sleep and set your spawn", "bed"},
            {"An orange gem that pigs like — used to breed them", "carrot"},
            {"A tool required to till dirt into farmland", "hoe"},
            {"A block that makes music when powered by redstone", "note block"},
            {"The strongest armor material in vanilla Minecraft", "netherite"},
            {"A hostile mob that drops string when killed", "spider"},
            {"A very fast projectile used to teleport short distances", "ender pearl"},
            {"An orb-shaped drop that lets you enchant items", "experience"},
            {"A tool used to catch fish", "fishing rod"},
            {"A container that has 27 slots and can be locked to one player", "chest"},
            {"The bright yellow star item that drops from a boss with three heads", "nether star"}
    };

    // ==================== BUILDERS ====================

    public static ActiveMinigame scrambledWord() {
        String word = pick(SCRAMBLE_WORDS);
        String scrambled;
        int guard = 0;
        do {
            scrambled = scramble(word);
            guard++;
        } while (scrambled.equalsIgnoreCase(word) && guard < 5);
        String prompt = "Unscramble: §" + scrambled;
        return new ActiveMinigame(MinigameType.SCRAMBLED_WORD, prompt, word, Collections.singletonList(word));
    }

    public static ActiveMinigame math() {
        int op = RNG.nextInt(4);
        int a, b, answer;
        String expr;
        switch (op) {
            case 0 -> {
                a = RNG.nextInt(90) + 10;
                b = RNG.nextInt(90) + 10;
                expr = a + " + " + b;
                answer = a + b;
            }
            case 1 -> {
                a = RNG.nextInt(90) + 10;
                b = RNG.nextInt(a) + 1;
                expr = a + " - " + b;
                answer = a - b;
            }
            case 2 -> {
                a = RNG.nextInt(11) + 2;
                b = RNG.nextInt(11) + 2;
                expr = a + " × " + b;
                answer = a * b;
            }
            default -> {
                b = RNG.nextInt(9) + 2;
                int q = RNG.nextInt(11) + 2;
                a = b * q;
                expr = a + " ÷ " + b;
                answer = q;
            }
        }
        String prompt = "Solve: §" + expr + " §= ?";
        String ans = String.valueOf(answer);
        return new ActiveMinigame(MinigameType.MATH, prompt, ans, Collections.singletonList(ans));
    }

    public static ActiveMinigame fillTheBlank() {
        String[] pair = pick(FILL_BLANKS);
        String prompt = "Fill the blank: §" + pair[0];
        return new ActiveMinigame(MinigameType.FILL_THE_BLANK, prompt, pair[1], Collections.singletonList(pair[1]));
    }

    public static ActiveMinigame typeRace() {
        String phrase = pick(TYPE_RACE_PHRASES);
        String prompt = "Type this exactly: §" + phrase;
        return new ActiveMinigame(MinigameType.TYPE_RACE, prompt, phrase, Collections.singletonList(phrase));
    }

    public static ActiveMinigame trivia() {
        String[] pair = pick(TRIVIA);
        String prompt = "Trivia: §" + pair[0];
        return new ActiveMinigame(MinigameType.TRIVIA, prompt, pair[1], Collections.singletonList(pair[1]));
    }

    public static ActiveMinigame reverseWord() {
        String word = pick(SCRAMBLE_WORDS);
        String reversed = new StringBuilder(word).reverse().toString();
        String prompt = "Reverse this word (type it forwards): §" + reversed;
        return new ActiveMinigame(MinigameType.REVERSE_WORD, prompt, word, Collections.singletonList(word));
    }

    public static ActiveMinigame colorCode() {
        String[] pair = pick(COLORS);
        boolean askForHex = RNG.nextBoolean();
        List<String> answers = new ArrayList<>();
        String prompt;
        String primary;
        if (askForHex) {
            prompt = "What is the hex code for §" + pair[0] + "§ ? (format: #RRGGBB)";
            primary = pair[1];
            answers.add(pair[1]);
            answers.add(pair[1].substring(1));
            answers.add(pair[1].toLowerCase());
            answers.add(pair[1].substring(1).toLowerCase());
        } else {
            prompt = "What color is this hex? §" + pair[1];
            primary = pair[0];
            answers.add(pair[0]);
        }
        return new ActiveMinigame(MinigameType.COLOR_CODE, prompt, primary, answers);
    }

    public static ActiveMinigame count() {
        String word = pick(SCRAMBLE_WORDS);
        int mode = RNG.nextInt(2);
        String prompt;
        String primary;
        if (mode == 0) {
            prompt = "How many letters are in the word: §" + word + "§ ?";
            primary = String.valueOf(word.length());
        } else {
            char pickChar = word.charAt(RNG.nextInt(word.length()));
            int cnt = 0;
            for (char c : word.toCharArray()) if (c == pickChar) cnt++;
            prompt = "How many times does '§" + pickChar + "§' appear in §" + word + "§ ?";
            primary = String.valueOf(cnt);
        }
        return new ActiveMinigame(MinigameType.COUNT, prompt, primary, Collections.singletonList(primary));
    }

    public static ActiveMinigame capital() {
        String[] pair = pick(CAPITALS);
        String prompt = "What is the capital of §" + pair[0] + "§ ?";
        return new ActiveMinigame(MinigameType.CAPITAL, prompt, pair[1], Collections.singletonList(pair[1]));
    }

    public static ActiveMinigame itemGuess() {
        String[] pair = pick(ITEM_CLUES);
        String prompt = "Guess the Minecraft item: §" + pair[0];
        return new ActiveMinigame(MinigameType.ITEM_GUESS, prompt, pair[1], Collections.singletonList(pair[1]));
    }

    public static ActiveMinigame build(MinigameType type) {
        return switch (type) {
            case SCRAMBLED_WORD -> scrambledWord();
            case MATH -> math();
            case FILL_THE_BLANK -> fillTheBlank();
            case TYPE_RACE -> typeRace();
            case TRIVIA -> trivia();
            case REVERSE_WORD -> reverseWord();
            case COLOR_CODE -> colorCode();
            case COUNT -> count();
            case CAPITAL -> capital();
            case ITEM_GUESS -> itemGuess();
        };
    }

    // ==================== HELPERS ====================

    private static <T> T pick(T[] arr) {
        return arr[RNG.nextInt(arr.length)];
    }

    private static String scramble(String word) {
        List<Character> chars = new ArrayList<>(word.length());
        for (char c : word.toCharArray()) chars.add(c);
        Collections.shuffle(chars, RNG);
        StringBuilder sb = new StringBuilder(chars.size());
        for (char c : chars) sb.append(c);
        return sb.toString();
    }
}
