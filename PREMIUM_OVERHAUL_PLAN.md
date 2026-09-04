# Premium Overhaul Plan — Starlight Universe

Everything left to do from the Ranks.xlsx discussion + user decisions.  
Each section is a self-contained unit of work. Do them in order.

---

## 1. Rename Premium Ranks

**Old** → **New**:
| Level | Old Name   | New Name  |
|-------|-----------|-----------|
| 1     | Meteor    | Luminant  |
| 2     | Comet     | Astral    |
| 3     | Nebula    | Ethereal  |
| 4     | Supernova | Celestial |
| 5     | Galaxy    | Divine    |
| 6     | Universe  | Immortal  |

**Files to change:**
- `PremiumRank.java` — enum values, displayName, prefix strings
- `AdminRank.java` — PREMIUM_NAMES array
- `RankCommands.java` — usage message on line 86 (names in parentheses)
- `PremiumManager.java` — rank GUI display (line ~474), rank icons array
- `PremiumCommands.java` — fly error message (currently says "Nebula rank")
- `StarShopManager.java` — PURCHASABLE_RANKS array, display names
- `HomeManager.java` — getPremiumMaxHomes switch cases (just comments)
- `CrateManager.java` — crate tier names if they reference rank names

---

## 2. New Rank Colors (Dark/Elegant Theme)

All ranks **bold**. Immortal uses a **red → black gradient**.  
Choose dark, elegant hex colors for each rank (not bright/saturated).

**Changes:**
- `PremiumRank.java` — hex color strings, gradient methods
  - Remove `galaxyGradient()` and `UNIVERSE_COLORS`
  - Replace with Immortal red-to-black gradient
  - Make `getColoredPrefix()` and `getColoredDisplayName()` apply bold

---

## 3. Restructure PremiumRank Benefits

### Remove these methods:
- `getKeepXpPercent()` — keep inventory ON globally (no XP)
- `getKeepArmorPercent()` — same reason
- `getKeepInventoryPercent()` — same reason
- `getCooldownSeconds()` — all premium = 0 cooldown
- `getMobKillMoneyBonus()` — replaced by job multipliers
- `getXpBoost()` — replaced by vanilla XP multiplier
- `getDailyBonus()` — deemed useless

### Change existing:
- `getMaxHomes()`: 3 / 5 / 10 / 20 / 40 / -1 (already correct)
- `getExtraWarps()`: 2 / 5 / 10 / 15 / 20 / 50

### Add new methods:
| Method | L1 | L2 | L3 | L4 | L5 | L6 |
|--------|----|----|----|----|----|----|
| `getAuctionSlots()` | 2 | 5 | 10 | 15 | 20 | 50 |
| `getBlocksPerHour()` | 125 | 150 | 175 | 200 | 225 | 250 |
| `getJobXpMultiplier()` | 2 | 2 | 2 | 2 | 2 | 2 |
| `getJobMoneyMultiplier()` | 1 | 1 | 2 | 2 | 2 | 2 |
| `getVanillaXpMultiplier()` | 1 | 1 | 1 | 1 | 2 | 2 |
| `getPrivateVaults()` | 2 | 4 | 10 | 20 | 40 | 50 |
| `getChestShops()` | 25 | 50 | 100 | 250 | 500 | 1000 |

### Also update:
- `PremiumListener.java` — remove death keep logic (keepXP, keepArmor, keepInventory), remove mob kill money bonus, remove XP boost handler
- `PremiumManager.java` — remove `checkDailyBonus()` method and its call from PremiumListener.onJoin

---

## 4. Keep Inventory ON Globally

- Set `KEEP_INVENTORY` game rule to `true` on survival worlds
- **But** players still lose XP on death (vanilla behavior)
- Remove all premium keep-inventory/keep-armor/keep-XP logic

**Files:**
- `WorldManager.java` — set gamerule on world load/create
- `PremiumListener.java` — gut the death/respawn handlers

---

## 5. All TP Cooldown = 0 for Premium

All premium ranks (1-6) have 0 teleport cooldown.  
Non-premium keeps the existing cooldown.

**Files:**
- `PremiumRank.java` — `getCooldownSeconds()` returns 0 for all levels 1-6 (or remove and just check isPremium)
- `TpaManager.java` — check premium level for cooldown
- `RtpManager.java` — RTP cooldown: 1s for non-premium, 0 for premium + admins

---

## 6. Blocks Per Hour Earning System

Premium players passively earn protection blocks every hour based on rank.

**Implementation:**
- Periodic task in `HomeManager` that runs every 60 minutes
- For each online authenticated premium player, add `rank.getBlocksPerHour()` blocks
- Update `su_players.protection_blocks` in DB

---

## 7. Job & XP Multipliers

Apply multipliers from the rank table when processing job/XP rewards.

**Files:**
- `JobListener.java` — multiply job XP by `getJobXpMultiplier()`, money by `getJobMoneyMultiplier()`
- `PremiumListener.java` (or a new listener) — multiply vanilla XP by `getVanillaXpMultiplier()`

---

## 8. Spawner Mining Without Silk Touch

Premium players can mine spawners without needing silk touch enchantment.

**Files:**
- `SpawnerListener.java` — check if player has premium rank, allow spawner drop

---

## 9. Auction Slot Limits

Limit auction house listings per player based on premium rank.

**Files:**
- `AuctionManager.java` — check `getAuctionSlots()` when creating a listing
- Non-premium default: 1 slot

---

## 10. New Commands

### Simple commands (one file each, in premium/commands or a new package):
| Command | What it does | Min rank |
|---------|-------------|----------|
| `/nickname <text>` | Set display name (color codes allowed for premium) | Premium 1+ |
| `/sell hand` | Sell held item at shop price | Premium 1+ |
| `/sell all` | Sell all sellable items in inventory | Premium 2+ |
| `/kittycannon` | Launch an exploding cat | Premium 3+ |
| `/beezooka` | Launch bees at target | Premium 3+ |
| `/clearinventory` | Clear your own inventory | Premium 1+ |
| `/ext` | Extinguish yourself | Premium 1+ |
| `/bellyflop` | Fun animation — launch into the air, slam down | Premium 4+ |
| `/crawl` | Toggle crawl mode | Premium 2+ |
| `/spin` | Spin in place (visual) | Premium 3+ |
| `/god` | Toggle god mode (invincibility) | Premium 5+ |

---

## 11. Private Vaults System

Virtual storage chests that premium players can open anywhere.

**Implementation:**
- New package `vault/`
- `VaultManager.java` — manages virtual inventories stored in DB
- DB table `su_vaults` (id, username, vault_number, contents MEDIUMTEXT)
- Command: `/pv <number>` — opens vault N (max from `getPrivateVaults()`)
- Serialize inventory to Base64 like existing inventory system

---

## 12. Chest Shops System

Player-owned shops using signs on chests.

**Implementation:**
- New package `chestshop/`
- `ChestShopManager.java` — manage player shops
- DB table `su_chest_shops` (id, owner, world, x, y, z, item, buy_price, sell_price, quantity)
- Max shops per player from `getChestShops()`
- Sign-based creation: place sign on chest with format:
  - Line 1: [Shop]
  - Line 2: quantity
  - Line 3: buy:sell price
  - Line 4: item name

---

## 13. [ITEM] / [INV] Chat Display

Premium players can show their held item or full inventory in chat.

**Implementation:**
- In `ChatManager.java` — detect `[ITEM]` or `[INV]` in messages
- Replace with hoverable component showing item tooltip / inventory contents
- Requires premium rank check

---

## 14. Color Signs

Premium players can use hex colors on signs.

**Implementation:**
- In a sign listener, parse `#RRGGBB` codes in sign text for premium players
- Apply TextColor to sign lines

---

## 15. Sit on Players

Premium perk to sit on other players (ride them).

**Implementation:**
- Command `/sit <player>` (with permission)
- Uses passenger API

---

## 16. Rainbow Premium Tag Color

Instead of "rainbow tag" benefit, apply rainbow color cycling to the premium rank prefix.

**Implementation:**
- In `PremiumRank.getColoredPrefix()` for ranks with `hasRainbowTag()`:
  - Animate or cycle colors on the prefix text
- Or implement as a separate system in `NameplateManager`

**Decision needed:** User said "rainbow tag is useless because you can't set a tag with premium, but you could put rainbow color on the premium rank name." This needs further discussion.

---

## NOT Implementing (User Decision)

- Fly protections (separate from border shovel — not wanted)
- Rainbow tag as a separate tag system
- Disposal signs / heal signs
- Grief prevention flags (already have protection system)

---

## Build Order Suggestion

1. Rename ranks + colors (§2, §3 visual changes only)
2. Restructure benefits (§3) + remove dead code from PremiumListener
3. Keep Inventory ON (§4)
4. TP cooldown fix (§5)
5. Job/XP multipliers (§7)
6. Blocks per hour (§6)
7. Auction slots (§9)
8. Spawner mining (§8)
9. Simple commands batch (§10)
10. Private Vaults (§11)
11. Chest Shops (§12)
12. Chat [ITEM]/[INV] (§13)
13. Color Signs (§14)
14. Sit on Players (§15)
15. Rainbow prefix color (§16)
