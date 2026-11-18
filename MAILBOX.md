# Christmas Mailbox (DillyyXmas 1.2)

A craftable mailbox block. Place it down, right-click it, pick **any player who has ever joined** (online or
offline), pick an item from your inventory, confirm, and it is mailed to them. They collect it from any
mailbox whenever they like.

## How it works
- **Craft** (default, editable in `config.yml` -> `mailbox.recipe`):
  ```
  Iron   Red Dye  Iron
  Iron   Barrel   Iron
  Iron   Paper    Iron
  ```
- **Place** it like any block. Sneak + hold an item to build against it instead of opening it.
- **Right-click** -> menu:
  - **Send a Gift** -> choose a player (online players first, paginated, Search button: click it and type a
    name in chat) -> choose an item from your inventory (left-click = whole stack, right-click = 1) ->
    confirm screen -> sent.
  - **Your Mail** -> click a gift to claim it, or **Claim All**. Gifts that don't fit stay in the mailbox.
- The recipient gets a chat message + sound if online, and a reminder when they join if gifts are waiting.
- Mail belongs to the **player**, not the block: every mailbox is the same network.
- Breaking a mailbox drops the Christmas Mailbox item (nothing is stored in the block, so nothing can be
  lost). Mailboxes are protected from pistons, explosions and hoppers.

## Safety / anti-dupe
- Menus only show copies; every click in a menu is cancelled.
- The item is checked in your real inventory at the moment of sending, removed first, then stored.
- Claiming removes the gift from storage first, then gives it to you (and only if it fits).
- Storage is one file per recipient in `plugins/DillyyXmas/mail/`, written atomically. A file that can't be
  read is copied to `*.corrupt-<time>` and never overwritten.
- Every send and claim is written to the console log (`[Mailbox] A -> B: 5x DIAMOND`).
- Limits in config: max waiting gifts per player, send cooldown, blocked items list, self-mailing off.

## Commands / permissions
- `/xmas mailbox give <player> [amount]` - give mailbox items (`xmas.admin`)
- `/xmas reload` - reloads config and the recipe
- `xmas.mailbox.use` (default: everyone) - use placed mailboxes

Use it as a reward: `xmas mailbox give %player% 1` works in the advent / present / boss command lists, and
`xmas mailbox give {player} 1` in a crate COMMAND reward.

## Build
`mvn -B package` (Java 21) -> `target/DillyyXmas-1.2.jar`, or push to GitHub and download the artifact from
the "Build" workflow. Requires Paper 1.21+.

## First start
The first time it runs, it indexes everyone who has already joined the server (one-time, can take a moment on
very large servers). After that, new players are added when they join.
