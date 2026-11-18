# DillyyXmas 1.7 - custom items and custom enchants

Every item has an ability on top of its vanilla enchants. Numbers are in `config.yml` under `items:` and `enchants:`.
Get items with `/xmas give <player> <id>`.

## Items

| ID | Item | What it does |
|---|---|---|
| `candy_cane_sword` | Candy Cane Blade | **Sticky Hit:** every hit slows the target. **Sugar Rush:** 20% chance per hit to heal you and give Speed II. |
| `candy_cane_pickaxe` | Candy Cane Pickaxe | **Sugar Haste:** Haste II while held. **Peppermint Vein:** sneak while mining an ore to break the whole vein (12 extra blocks). |
| `grinch_heart` | Grinch's Heart | **Grinch's Grip** (right-click, 45s): pulls in and weakens everything within 7 blocks, damages it, heals you 3 hearts and gives Absorption. |
| `santa_hat` | Santa Hat | **Christmas Spirit:** Regeneration while worn. **Santa's Sack:** 5% chance a monster you kill drops a gift. |
| `reindeer_boots` | Reindeer Boots | Speed + Jump Boost while worn, no fall damage. **Sleigh Dash:** sprint, then sneak to dash forward (6s cooldown). |
| `frost_wand` | Frost Wand | Right-click fires an ice bolt that damages and slows. *(unchanged)* |
| `gingerbread_chestplate` | Gingerbread Plate *(new)* | **Crunch:** 25% chance to reflect 30% of the damage you take back at the attacker. |
| `snow_globe` | Snow Globe *(new)* | **Blizzard** (right-click, 40s): freezes and slows everything within 7 blocks, gives you Resistance. |
| `jingle_bells` | Jingle Bells *(new)* | **Sleigh Ride** (right-click, 60s): Speed III, Jump Boost II and Haste II for 8 seconds. |
| `holly_bow` | Holly Bow *(new)* | Arrows make the target glow and slow it. Power V, Punch I, Infinity. |
| `sleigh_launcher` | Sleigh Launcher *(1.4)* | **Sleigh Blast** (right-click, 20s): rockets you forward in a firework trail, no fall damage while airborne. |
| `north_star` | North Star *(1.4)* | **Starfall** (right-click, 50s): golden stars crash down on up to 6 enemies within 10 blocks (8 damage, glowing). |
| `eggnog_flask` | Eggnog Flask *(1.4)* | **Holiday Cheer** (right-click, 90s): Strength, Regeneration II and Absorption II for 20s, plus a little food. |
| `yule_log_axe` | Yule Log Axe *(1.5)* | **Yule Fire:** 25% chance per hit to erupt in flame, igniting and damaging everything within 3 blocks. |
| `mistletoe_kiss` | Mistletoe Kiss *(1.5)* | **Season of Love** (right-click a player, 30s): heals you both 4 hearts and shares Regeneration II. |
| `ice_skates` | Ice Skates *(1.5)* | **Glide:** Speed III on any ice, Frost Walker II freezes water as you walk. |
| `snowball_cannon` | Snowball Cannon *(1.5)* | **Powder Burst** (right-click, 8s): fires 3 hard-hitting snowballs with knockback. |
| `frosty_coal` | Frosty's Magic Coal *(1.5)* | **Frosty Friends** (right-click, 60s): summons 3 snow golems that fight for you for 25s. |

**Santa Set bonus (1.5):** wear the Santa Hat, Gingerbread Plate and Reindeer Boots together for Resistance I and Strength I, plus a golden swirl around you.

The ability is tied to the item itself (not its name), so items players already own get the new abilities after the update.
Only the lore on newly created items shows the new ability text.

## Custom enchants

Stored on the item and shown in its lore, no datapack needed. `/xmas enchants` lists them in game.

| ID | Goes on | Max | Effect |
|---|---|---|---|
| `lifesteal` | swords, axes, mace, trident, spears | III | Heal 5% of damage dealt per level (max 3 hearts per hit). |
| `frostbite` | same | III | 20% per level to freeze and slow the target. |
| `executioner` | same | III | +15% damage per level against targets under 30% health. |
| `auto_smelt` | pickaxes | I | Ores drop as ingots (iron, gold, copper, netherite scrap). Also works on Vein Miner veins. |
| `vein_miner` | pickaxes | III | Sneak while mining an ore to break the connected vein (6 extra blocks per level). |
| `swiftness` | boots | III | Permanent Speed while worn. |
| `night_owl` | helmets | I | Permanent Night Vision while worn. |

**Giving them**
- `/xmas book <player> <enchant> [level]` gives a custom enchant book. The player combines it with the item in an anvil.
  Same level as the item already has raises it by one, a lower level is refused.
- `/xmas enchant <player> <enchant> [level]` applies it straight to the item they hold (admin).
- The Christmas crate in `extras/christmas-crate.yml` now has the new items and four enchant books.

Vein mining fires a normal block-break event for every extra block, so GriefPrevention and other protection plugins can still block it.

## Install

1. Push this folder to GitHub, the **Build** workflow makes `DillyyXmas-1.3.jar` (Actions tab > Build > Artifacts), or run `mvn package`.
2. Replace the old jar in `plugins/` and restart. Config changes: delete or merge `plugins/DillyyXmas/config.yml`, because
   an old config doesn't get the new `items:` and `enchants:` sections (the code falls back to the defaults above if they are missing).
3. Paste the new rewards from `extras/christmas-crate.yml` into your `crates.yml`, then `/crate reload`.

## 1.4: festive extras
- **Auras** (`auras.enabled`): every item now has a particle effect. Santa Hat gets a spinning red/white halo, Reindeer Boots leave a snowflake and gold trail when you run, Candy Cane tools carry a candy-stripe helix, the North Star has orbiting stars. The Frost Wand bolt now leaves a glittering trail.
- **Snow** (`snow.twinkle`): sparkles and red/green string-light flecks drift down with the snow.
- **Presents**: a red/green striped beam rises from every present so you can spot it from far away.
- **Join greeting** (`festive.join-title`): "Merry Christmas" title, days-until-Christmas countdown and a bell jingle.
- **Tips** (`festive.tips`): a rotating festive tip is broadcast every `festive.tip-interval-minutes`.
- **Firework shows** (`festive.fireworks`): a show over world spawn on Dec 24 and 25. Set `fireworks-always: true` to run it all month. `/xmas show` (admin) fires one where you stand.
- The crate file now has 18 rewards and is a 36-slot menu (new slots 25 to 27, back button on 35).

Update note: merge the new `auras:`, `festive:` and three `items:` sections into your existing `config.yml` (or delete it to regenerate). The code falls back to defaults if they are missing.

## 1.5: more festive
- **Santa's Sleigh** (`sleigh:` in config): every 30 minutes (needs 2 players online) a particle sleigh pulled by four rows of reindeer, Rudolph's red nose included, flies over the presents center with a bell soundtrack and drops presents along the way. `/xmas sleigh` (admin) launches it now.
- **Christmas Catalog:** `/xmas catalog` opens a menu of every item with its ability. Admins can click an item to take one.
- **Login streaks** (`streak:` in config): daily login rewards plus milestone gifts at 3, 7, 14 and 25 days, with a firework burst on milestones. `/xmas streak` shows your count. Rewards are console commands, edit them to fit your server.
- **Santa Set bonus** and five new items (above).
- Crate file: 23 rewards in the 36-slot menu (new slots 28 to 32).

Update note: merge the new `sleigh:`, `streak:` and five `items:` entries into your `config.yml`, or delete it to regenerate. Missing entries fall back to defaults. The default streak rewards use `addmoney`, `addshards` and `crate key` commands, change them if your server uses different ones.

## 1.6: nine new custom enchants + a much harder Grinch

### New custom enchants (`/xmas enchants` lists all 16)
| ID | Goes on | Max | Effect |
|---|---|---|---|
| `snowstorm` | swords, axes, mace, trident, spears | III | 15% per level: a mini blizzard slows and freezes everything within 3 blocks of the target. |
| `grinch_curse` | same | III | 20% per level: target gets Weakness and Nausea. |
| `jingle_stun` | same | III | 8% per level: ringing bell stuns the target for about a second. |
| `holiday_bounty` | same | III | 10% per level: the mob you killed drops double loot. |
| `frostguard` | chestplates, leggings | III | 15% per level: whoever hits you is frozen and slowed. |
| `hearth` | chestplates, leggings | I | Immune to freezing. A warm glow heals you when below 40% health. |
| `candy_leap` | boots | III | Permanent Jump Boost while worn. |
| `gift_of_wisdom` | helmets | III | +25% XP per level from orbs. |
| `timber` | axes | I | Sneak while chopping a log to fell the whole tree (up to 48 logs). |

Get them with `/xmas book <player> <enchant> [level]` or `/xmas enchant`. Numbers are under `enchants:` in `config.yml`.

### The Grinch now has three phases
- **Phase 1 (100% to 66%):** Stomp, Snowballs, Elves.
- **Phase 2 (66% to 33%):** adds **Coal Rain** (red rings warn you where bombs land) and **Cold Heart** (freezing shockwave). Lightning, a title, five seconds of invulnerability while elves rush in, and the boss bar turns yellow.
- **Phase 3 (below 33%):** enraged and faster, adds **Leap Slam** (he jumps onto a player and crashes down) and **Grinch's Grip** (pulls everyone in and heals off them). The boss bar turns red.
- A green, yellow or red aura swirls around him depending on the phase.
- Spawn has a lightning strike and a title. Defeat has fireworks, hearts and a title ("His heart grew three sizes!").
- New config under `boss:`: `coal-damage`, `cold-heart-damage`, `leap-damage`, `drain-heal-percent`.
- Top 1 damage dealer also gets the North Star and a Snowstorm III book, 2nd also gets the Eggnog Flask and a Frostguard III book.

Update note: merge the new `enchants:` entries and the four new `boss:` keys into your `config.yml`, or delete it to regenerate.

## 1.7: Christmas music

`/xmas music <song name>` gives you a music disc (needs `xmas.music`, op by default). Right-click a jukebox with it to play the song. Everyone within `music.range` blocks (default 100) hears it, with note particles floating above the jukebox. Right-click the jukebox again (empty hand) to take the disc out. Breaking or blowing up the jukebox drops the disc. `/xmas music list` shows what is installed, `/xmas music reload` rescans.

**The plugin cannot search the web or download songs.** A Minecraft client can only play sounds it already has, so music has to come from one of these:

1. **Built-in carols** (note block sounds): Jingle Bells, Silent Night, We Wish You a Merry Christmas, Carol of the Bells. Melodies were written from memory, so adjust them if a note is off (they live in `Music.java`).
2. **`.nbs` files:** drop Note Block Studio files into `plugins/DillyyXmas/songs/` and run `/xmas music reload`. The file name is the song name (`Last_Christmas.nbs` becomes `/xmas music last christmas`). There are many free community songs, but check each song's license before putting it on a public server.
3. **Resource pack sounds:** if your server already sends a resource pack, add the `.ogg` to it with a sound event in `sounds.json`, then add an entry under `music.custom-sounds:` in `config.yml` (title, sound key, length in seconds). Only use music you have the rights to.

Other notes: a sound reaches 16 blocks times its volume, so `range: 100` means volume 6.25. It fades with distance. The jukebox sound uses the "Jukebox/Note Blocks" volume slider, so players can turn it down. A song plays once and the disc stays in until taken out.
