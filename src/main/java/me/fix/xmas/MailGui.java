package me.fix.xmas;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All the mailbox menus. Menus never hold real items: they show copies, every click is cancelled, and
 * the real inventory is re-checked at the moment an item is actually taken, so nothing can be duplicated
 * or lost by closing a menu, swapping hotbar slots, shift-clicking, etc.
 */
final class MailGui implements Listener {

    private static final int PAGE = 45;

    static final class Menu implements InventoryHolder {
        enum Type { MAIN, PICK_PLAYER, PICK_ITEM, CONFIRM, INBOX }

        final Type type;
        Inventory inv;
        String filter = "";
        int page;
        UUID target;
        int srcSlot;
        int amount;
        ItemStack snapshot;
        final List<UUID> ids = new ArrayList<>();

        Menu(Type type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final Xmas plugin;
    private final MailStore store;
    private final Map<UUID, Long> searching = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSend = new HashMap<>();

    MailGui(Xmas plugin, MailStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ================================================================== item helpers

    private static ItemStack icon(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Xmas.line(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Xmas.line(s));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    private static void glow(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
    }

    private static void appendLore(ItemStack it, String... extra) {
        ItemMeta m = it.getItemMeta();
        List<Component> l = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
        for (String s : extra) l.add(Xmas.line(s));
        m.lore(l);
        it.setItemMeta(m);
    }

    private static void fill(Inventory inv, boolean festive) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) != null) continue;
            Material pane = festive
                    ? (i % 2 == 0 ? Material.RED_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE)
                    : Material.GRAY_STAINED_GLASS_PANE;
            inv.setItem(i, icon(pane, " "));
        }
    }

    private static String itemName(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m != null && m.hasDisplayName() && m.displayName() != null) {
            return PlainTextComponentSerializer.plainText().serialize(m.displayName());
        }
        StringBuilder sb = new StringBuilder();
        for (String part : it.getType().name().toLowerCase(Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private boolean isBlocked(ItemStack it) {
        for (String s : cfg().getStringList("mailbox.blacklist")) {
            if (s.equalsIgnoreCase(it.getType().name())) return true;
        }
        return false;
    }

    private static boolean canFit(PlayerInventory inv, ItemStack it) {
        int need = it.getAmount();
        for (ItemStack s : inv.getStorageContents()) {
            if (s == null || s.getType().isAir()) need -= it.getMaxStackSize();
            else if (s.isSimilar(it)) need -= (s.getMaxStackSize() - s.getAmount());
            if (need <= 0) return true;
        }
        return false;
    }

    private void sound(Player p, String path, String def) {
        String key = cfg().getString(path, def);
        if (key == null || key.isBlank() || key.equalsIgnoreCase("none")) return;
        p.playSound(p.getLocation(), key, 1f, 1f);
    }

    private void closeLater(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
    }

    private Menu create(Menu.Type type, int size, String title) {
        Menu m = new Menu(type);
        m.inv = Bukkit.createInventory(m, size, Xmas.c(title));
        return m;
    }

    // ================================================================== MAIN

    void openMain(Player p) {
        Menu m = create(Menu.Type.MAIN, 27, "&c&lChristmas &a&lMailbox");
        int n = store.count(p.getUniqueId());
        m.inv.setItem(11, icon(Material.CHEST, "&a&lSend a Gift",
                "&7Pick a player and an item from", "&7your inventory to mail them.", "", "&eClick to start"));
        ItemStack in = icon(Material.ENDER_CHEST, "&c&lYour Mail &7(" + n + ")",
                n == 0 ? "&7No gifts waiting." : "&7You have &f" + n + " &7gift" + (n == 1 ? "" : "s") + " to collect.",
                "", "&eClick to open");
        if (n > 0) glow(in);
        m.inv.setItem(15, in);
        m.inv.setItem(26, icon(Material.BARRIER, "&cClose"));
        fill(m.inv, true);
        p.openInventory(m.inv);
    }

    // ================================================================== PICK PLAYER

    void openPickPlayer(Player p, String filter, int page) {
        Menu m = create(Menu.Type.PICK_PLAYER, 54, "&8Send a gift - choose a player");
        m.filter = filter == null ? "" : filter;
        m.page = page;
        renderPlayers(m, p);
        p.openInventory(m.inv);
    }

    private void renderPlayers(Menu m, Player viewer) {
        Inventory inv = m.inv;
        inv.clear();
        m.ids.clear();

        boolean allowSelf = cfg().getBoolean("mailbox.allow-self", false);
        String f = m.filter.toLowerCase(Locale.ROOT);
        List<Map.Entry<UUID, String>> list = new ArrayList<>();
        for (Map.Entry<UUID, String> en : store.known().entrySet()) {
            if (!allowSelf && en.getKey().equals(viewer.getUniqueId())) continue;
            if (!f.isEmpty() && !en.getValue().toLowerCase(Locale.ROOT).contains(f)) continue;
            list.add(en);
        }
        list.sort((a, b) -> {
            boolean ao = Bukkit.getPlayer(a.getKey()) != null;
            boolean bo = Bukkit.getPlayer(b.getKey()) != null;
            if (ao != bo) return ao ? -1 : 1;
            return a.getValue().compareToIgnoreCase(b.getValue());
        });

        int pages = Math.max(1, (list.size() + PAGE - 1) / PAGE);
        m.page = Math.max(0, Math.min(m.page, pages - 1));
        int start = m.page * PAGE;

        for (int i = 0; i < PAGE && start + i < list.size(); i++) {
            Map.Entry<UUID, String> en = list.get(start + i);
            boolean online = Bukkit.getPlayer(en.getKey()) != null;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(Bukkit.getOfflinePlayer(en.getKey()));
            sm.displayName(Xmas.line((online ? "&a" : "&f") + en.getValue()));
            sm.lore(List.of(Xmas.line(online ? "&aOnline" : "&7Offline"), Xmas.line(""), Xmas.line("&eClick to choose")));
            head.setItemMeta(sm);
            inv.setItem(i, head);
            m.ids.add(en.getKey());
        }

        if (m.page > 0) inv.setItem(45, icon(Material.ARROW, "&ePrevious page"));
        inv.setItem(46, icon(Material.OAK_DOOR, "&cBack"));
        inv.setItem(48, icon(Material.NAME_TAG, "&b&lSearch",
                m.filter.isEmpty() ? "&7Click, then type a name in chat." : "&7Filter: &f" + m.filter, "&7Type &fcancel &7to abort."));
        if (!m.filter.isEmpty()) inv.setItem(49, icon(Material.RED_DYE, "&cClear search"));
        inv.setItem(50, icon(Material.PAPER, "&7Page " + (m.page + 1) + "/" + pages,
                "&7" + list.size() + " player" + (list.size() == 1 ? "" : "s")));
        if (m.page < pages - 1) inv.setItem(53, icon(Material.ARROW, "&eNext page"));
        fill(inv, false);
    }

    private void handlePickPlayer(Player p, Menu m, int slot) {
        if (slot >= 0 && slot < PAGE) {
            if (slot < m.ids.size()) openPickItem(p, m.ids.get(slot));
            return;
        }
        switch (slot) {
            case 45 -> { if (m.page > 0) { m.page--; renderPlayers(m, p); } }
            case 53 -> { m.page++; renderPlayers(m, p); }
            case 46 -> openMain(p);
            case 48 -> startSearch(p);
            case 49 -> { m.filter = ""; m.page = 0; renderPlayers(m, p); }
            default -> { }
        }
    }

    private void startSearch(Player p) {
        searching.put(p.getUniqueId(), System.currentTimeMillis() + 30_000L);
        closeLater(p);
        plugin.msg(p, "&aType a player's name in chat to search &7(or type &fcancel&7). You have 30 seconds.");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Long until = searching.remove(e.getPlayer().getUniqueId());
        if (until == null) return;
        if (System.currentTimeMillis() > until) return; // expired: let the message go through normally
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) openPickPlayer(p, text.equalsIgnoreCase("cancel") ? "" : text, 0);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        searching.remove(e.getPlayer().getUniqueId());
        lastSend.remove(e.getPlayer().getUniqueId());
    }

    // ================================================================== PICK ITEM

    void openPickItem(Player p, UUID target) {
        Menu m = create(Menu.Type.PICK_ITEM, 54, "&8Gift to " + store.nameOf(target));
        m.target = target;
        PlayerInventory pi = p.getInventory();
        for (int s = 0; s < 36; s++) {
            int invSlot = s < 27 ? s + 9 : s - 27; // top 3 rows of the menu = main inventory, 4th row = hotbar
            ItemStack it = pi.getItem(invSlot);
            if (it == null || it.getType().isAir()) continue;
            ItemStack shown = it.clone();
            if (isBlocked(it)) appendLore(shown, "", "&cThis item can't be mailed.");
            else appendLore(shown, "", "&eLeft-click: &fsend the whole stack", "&eRight-click: &fsend just 1");
            m.inv.setItem(s, shown);
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(Bukkit.getOfflinePlayer(target));
        sm.displayName(Xmas.line("&a" + store.nameOf(target)));
        sm.lore(List.of(Xmas.line("&7Choose an item above to send."), Xmas.line("&8Armor and offhand can't be mailed.")));
        head.setItemMeta(sm);
        m.inv.setItem(49, head);
        m.inv.setItem(45, icon(Material.OAK_DOOR, "&cBack", "&7Choose a different player."));
        fill(m.inv, false);
        p.openInventory(m.inv);
    }

    private void handlePickItem(Player p, Menu m, InventoryClickEvent e, int slot) {
        if (slot == 45) {
            openPickPlayer(p, "", 0);
            return;
        }
        if (slot < 0 || slot > 35) return;
        int invSlot = slot < 27 ? slot + 9 : slot - 27;
        ItemStack cur = p.getInventory().getItem(invSlot);
        if (cur == null || cur.getType().isAir()) return;
        if (isBlocked(cur)) {
            plugin.msg(p, "&cThat item can't be mailed.");
            return;
        }
        int amt = e.isRightClick() ? 1 : cur.getAmount();
        ItemStack snap = cur.clone();
        snap.setAmount(amt);
        if (cfg().getBoolean("mailbox.confirm-before-send", true)) openConfirm(p, m.target, invSlot, amt, snap);
        else send(p, m.target, invSlot, amt, snap);
    }

    // ================================================================== CONFIRM

    private void openConfirm(Player p, UUID target, int invSlot, int amt, ItemStack snap) {
        Menu m = create(Menu.Type.CONFIRM, 27, "&8Send this gift?");
        m.target = target;
        m.srcSlot = invSlot;
        m.amount = amt;
        m.snapshot = snap;
        ItemStack preview = snap.clone();
        appendLore(preview, "", "&7To: &f" + store.nameOf(target), "&7Amount: &f" + amt);
        m.inv.setItem(13, preview);
        m.inv.setItem(11, icon(Material.RED_STAINED_GLASS_PANE, "&c&lCancel", "&7Go back and choose again."));
        m.inv.setItem(15, icon(Material.LIME_STAINED_GLASS_PANE, "&a&lSend gift",
                "&7Send &f" + amt + "x " + itemName(snap), "&7to &f" + store.nameOf(target) + "&7."));
        fill(m.inv, false);
        p.openInventory(m.inv);
    }

    private void handleConfirm(Player p, Menu m, int slot) {
        if (slot == 11) openPickItem(p, m.target);
        else if (slot == 15) send(p, m.target, m.srcSlot, m.amount, m.snapshot);
    }

    // ================================================================== SEND

    private void send(Player p, UUID target, int invSlot, int amt, ItemStack snap) {
        String targetName = store.nameOf(target);
        if (!store.isKnown(target)) {
            plugin.msg(p, "&cThat player isn't known to the server.");
            closeLater(p);
            return;
        }
        if (target.equals(p.getUniqueId()) && !cfg().getBoolean("mailbox.allow-self", false)) {
            plugin.msg(p, "&cYou can't mail yourself.");
            return;
        }
        if (isBlocked(snap)) {
            plugin.msg(p, "&cThat item can't be mailed.");
            return;
        }
        long now = System.currentTimeMillis();
        long cd = cfg().getLong("mailbox.send-cooldown-seconds", 3) * 1000L;
        Long last = lastSend.get(p.getUniqueId());
        if (last != null && now - last < cd) {
            plugin.msg(p, "&cSlow down - wait a moment before sending another gift.");
            return;
        }
        if (store.count(target) >= cfg().getInt("mailbox.max-inbox", 54)) {
            plugin.msg(p, "&c" + targetName + "'s mailbox is full. Try again later.");
            closeLater(p);
            return;
        }

        PlayerInventory pi = p.getInventory();
        ItemStack cur = pi.getItem(invSlot);
        if (cur == null || !cur.isSimilar(snap) || cur.getAmount() < amt) {
            plugin.msg(p, "&cYour inventory changed - please pick the item again.");
            openPickItem(p, target);
            return;
        }

        // 1) take the item out of the inventory, 2) store it. Both happen on the main thread in one tick.
        if (cur.getAmount() == amt) {
            pi.setItem(invSlot, null);
        } else {
            ItemStack rest = cur.clone();
            rest.setAmount(cur.getAmount() - amt);
            pi.setItem(invSlot, rest);
        }
        ItemStack toStore = snap.clone();
        toStore.setAmount(amt);
        store.add(target, p, toStore);
        lastSend.put(p.getUniqueId(), now);

        closeLater(p);
        String what = amt + "x " + itemName(toStore);
        plugin.msg(p, "&aGift sent! &f" + what + " &ais on its way to &f" + targetName + "&a.");
        sound(p, "mailbox.send-sound", "entity.experience_orb.pickup");
        plugin.getLogger().info("[Mailbox] " + p.getName() + " -> " + targetName + ": " + amt + "x " + toStore.getType());

        Player online = Bukkit.getPlayer(target);
        if (online != null && !online.equals(p)) {
            plugin.msg(online, "&e" + p.getName() + " &asent you a gift: &f" + what
                    + "&a! Right-click any &cChristmas Mailbox &ato collect it.");
            sound(online, "mailbox.receive-sound", "entity.player.levelup");
        }
    }

    // ================================================================== INBOX

    void openInbox(Player p, int page) {
        Menu m = create(Menu.Type.INBOX, 54, "&8Your gifts");
        m.page = page;
        renderInbox(m, p);
        p.openInventory(m.inv);
    }

    private void renderInbox(Menu m, Player p) {
        Inventory inv = m.inv;
        inv.clear();
        m.ids.clear();
        List<MailStore.Mail> list = store.inbox(p.getUniqueId());
        int pages = Math.max(1, (list.size() + PAGE - 1) / PAGE);
        m.page = Math.max(0, Math.min(m.page, pages - 1));
        int start = m.page * PAGE;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(plugin.zone());

        for (int i = 0; i < PAGE && start + i < list.size(); i++) {
            MailStore.Mail mail = list.get(start + i);
            ItemStack it = mail.item();
            if (it == null) {
                it = icon(Material.BARRIER, "&cUnreadable gift", "&7From: &f" + mail.fromName, "&7Ask an admin for help.");
            } else {
                appendLore(it, "", "&7From: &f" + mail.fromName, "&7Sent: &f" + fmt.format(Instant.ofEpochMilli(mail.time)),
                        "", "&eClick to claim");
            }
            inv.setItem(i, it);
            m.ids.add(mail.id);
        }

        if (m.page > 0) inv.setItem(45, icon(Material.ARROW, "&ePrevious page"));
        inv.setItem(46, icon(Material.OAK_DOOR, "&cBack"));
        ItemStack all = icon(Material.HOPPER, "&a&lClaim All", "&7" + list.size() + " gift" + (list.size() == 1 ? "" : "s"),
                "&7Claims until your inventory is full.");
        if (!list.isEmpty()) glow(all);
        inv.setItem(49, all);
        inv.setItem(50, icon(Material.PAPER, "&7Page " + (m.page + 1) + "/" + pages));
        if (m.page < pages - 1) inv.setItem(53, icon(Material.ARROW, "&eNext page"));
        fill(inv, true);
    }

    private void handleInbox(Player p, Menu m, int slot) {
        if (slot >= 0 && slot < PAGE) {
            if (slot < m.ids.size() && claim(p, m.ids.get(slot))) {
                sound(p, "mailbox.claim-sound", "entity.item.pickup");
                renderInbox(m, p);
            }
            return;
        }
        switch (slot) {
            case 45 -> { if (m.page > 0) { m.page--; renderInbox(m, p); } }
            case 53 -> { m.page++; renderInbox(m, p); }
            case 46 -> openMain(p);
            case 49 -> {
                int n = 0;
                for (MailStore.Mail mail : store.inbox(p.getUniqueId())) {
                    if (!claim(p, mail.id)) break;
                    n++;
                }
                if (n > 0) {
                    plugin.msg(p, "&aClaimed &f" + n + " &agift" + (n == 1 ? "" : "s") + "!");
                    sound(p, "mailbox.claim-sound", "entity.item.pickup");
                }
                renderInbox(m, p);
            }
            default -> { }
        }
    }

    /** Moves one gift from the mail store into the player's inventory. Returns true if it was claimed. */
    private boolean claim(Player p, UUID mailId) {
        MailStore.Mail mail = store.get(p.getUniqueId(), mailId);
        if (mail == null) return false;
        ItemStack it = mail.item();
        if (it == null) {
            plugin.msg(p, "&cThat gift can't be read. Please ask an admin.");
            return false;
        }
        if (!canFit(p.getInventory(), it)) {
            plugin.msg(p, "&cNot enough inventory space for that gift.");
            return false;
        }
        // remove from storage FIRST so a gift can never be claimed twice
        if (store.remove(p.getUniqueId(), mailId) == null) return false;
        for (ItemStack left : p.getInventory().addItem(it).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        plugin.getLogger().info("[Mailbox] " + p.getName() + " claimed " + it.getAmount() + "x " + it.getType()
                + " from " + mail.fromName);
        return true;
    }

    // ================================================================== click routing

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true); // nothing can ever be moved in or out of a mailbox menu
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= m.inv.getSize()) return;
        ItemStack clicked = m.inv.getItem(raw);
        if (clicked == null || clicked.getType().isAir()) return;

        switch (m.type) {
            case MAIN -> {
                switch (raw) {
                    case 11 -> openPickPlayer(p, "", 0);
                    case 15 -> openInbox(p, 0);
                    case 26 -> closeLater(p);
                    default -> { }
                }
            }
            case PICK_PLAYER -> handlePickPlayer(p, m, raw);
            case PICK_ITEM -> handlePickItem(p, m, e, raw);
            case CONFIRM -> handleConfirm(p, m, raw);
            case INBOX -> handleInbox(p, m, raw);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
