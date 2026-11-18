package me.fix.xmas;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Advent implements Listener {

    private final Xmas plugin;

    Advent(Xmas plugin) {
        this.plugin = plugin;
    }

    static class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    /** Day 1..25 laid out as a 5x5 block in the middle of a 5-row inventory. */
    static int slot(int day) {
        return ((day - 1) / 5) * 9 + ((day - 1) % 5) + 2;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    static class TzHolder implements InventoryHolder {
        Inventory inv;
        final Map<Integer, String> zones = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    /** A door opens at midnight in the PLAYER'S timezone. */
    boolean available(Player p, int day) {
        if (cfg().getBoolean("advent.test-mode", false)) return true;
        LocalDate t = plugin.todayFor(p.getUniqueId());
        if (t.getMonthValue() != 12) return false;
        if (day > t.getDayOfMonth()) return false;
        return cfg().getBoolean("advent.late-claim", true) || day == t.getDayOfMonth();
    }

    private ItemStack item(Material m, int amount, String name, boolean glint, String... lore) {
        ItemStack it = new ItemStack(m, amount);
        ItemMeta im = it.getItemMeta();
        im.displayName(Xmas.line(name));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Xmas.line(s));
        im.lore(l);
        if (glint) im.setEnchantmentGlintOverride(true);
        it.setItemMeta(im);
        return it;
    }

    void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 45, Xmas.c(cfg().getString("advent.title", "&c&lAdvent Calendar")));
        h.inv = inv;
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, item(i % 2 == 0 ? Material.RED_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE, 1, " ", false));
        }
        List<Integer> done = plugin.claimed(p.getUniqueId());
        for (int d = 1; d <= 25; d++) {
            String display = cfg().getString("advent.days." + d + ".display", "&7Mystery gift");
            ItemStack it;
            if (done.contains(d)) {
                it = item(Material.LIME_STAINED_GLASS_PANE, d, "&a&lDay " + d + " &7- Claimed", false,
                        "&7You got: &f" + display);
            } else if (available(p, d)) {
                it = item(Material.CHEST, d, "&6&lDay " + d + " &e- Click to claim!", true,
                        "&7Reward: &f" + display);
            } else {
                it = item(Material.BARRIER, d, "&c&lDay " + d + " &7- Locked", false,
                        "&7Opens on December " + d + " &8(" + plugin.zoneFor(p.getUniqueId()).getId() + ")");
            }
            inv.setItem(slot(d), it);
        }
        inv.setItem(44, item(Material.CLOCK, 1, "&b&lYour timezone", false,
                "&7" + plugin.zoneFor(p.getUniqueId()).getId(),
                plugin.hasZone(p.getUniqueId()) ? "&7Doors open at midnight in your timezone." : "&eNot set yet! Click to pick yours.",
                "", "&eClick to change"));
        p.openInventory(inv);
    }

    // ---------- timezone picker ----------
    void timezoneCommand(Player p, String[] a) {
        if (a.length < 2) {
            openTz(p);
            return;
        }
        applyZone(p, a[1]);
    }

    private void applyZone(Player p, String zoneId) {
        try {
            ZoneId.of(zoneId);
        } catch (Exception e) {
            plugin.msg(p, "&cThat isn't a valid timezone. Use the menu or a name like &fAmerica/New_York&c.");
            return;
        }
        boolean lock = cfg().getBoolean("advent.timezone-lock", true);
        if (lock && plugin.hasZone(p.getUniqueId()) && !p.hasPermission("xmas.admin")) {
            plugin.msg(p, "&cYour timezone is already set. Ask a staff member to change it.");
            return;
        }
        plugin.setZone(p.getUniqueId(), zoneId);
        plugin.msg(p, "&aTimezone set to &f" + zoneId + "&a. Your doors now open at midnight there.");
    }

    void openTz(Player p) {
        TzHolder h = new TzHolder();
        Inventory inv = Bukkit.createInventory(h, 36, Xmas.c("&8Pick your timezone"));
        h.inv = inv;
        for (int i = 0; i < 36; i++) inv.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, 1, " ", false));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        List<Map<?, ?>> opts = cfg().getMapList("timezone-options");
        String current = plugin.zoneFor(p.getUniqueId()).getId();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US);
        for (int i = 0; i < Math.min(opts.size(), slots.length); i++) {
            Map<?, ?> o = opts.get(i);
            String name = String.valueOf(o.get("name"));
            String zone = String.valueOf(o.get("zone"));
            String time;
            try {
                time = LocalTime.now(ZoneId.of(zone)).format(fmt);
            } catch (Exception e) {
                continue;
            }
            boolean sel = zone.equals(current) && plugin.hasZone(p.getUniqueId());
            inv.setItem(slots[i], item(Material.CLOCK, 1, (sel ? "&a&l" : "&e") + name, sel,
                    "&7" + zone, "&7Time there now: &f" + time, "", sel ? "&aSelected" : "&eClick to select"));
            h.zones.put(slots[i], zone);
        }
        inv.setItem(31, item(Material.BARRIER, 1, "&cBack", false, "&7Return to the calendar."));
        p.openInventory(inv);
    }

    @EventHandler
    public void onTzClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof TzHolder h)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 31) {
            open(p);
            return;
        }
        String zone = h.zones.get(slot);
        if (zone == null) return;
        applyZone(p, zone);
        open(p);
    }

    private void claim(Player p, int day) {
        if (plugin.claimed(p.getUniqueId()).contains(day)) {
            plugin.msg(p, "&cYou already opened day " + day + ".");
            return;
        }
        if (!available(p, day)) {
            plugin.msg(p, "&cDay " + day + " isn't available yet.");
            return;
        }
        plugin.addClaimed(p.getUniqueId(), day);
        plugin.runCommands(p, cfg().getStringList("advent.days." + day + ".commands"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        plugin.msg(p, "&aYou opened day &f" + day + "&a and got &f"
                + cfg().getString("advent.days." + day + ".display", "a gift") + "&a!");
        open(p);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 44) {
            openTz(p);
            return;
        }
        for (int d = 1; d <= 25; d++) {
            if (slot(d) == slot) {
                claim(p, d);
                return;
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder || e.getInventory().getHolder() instanceof TzHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!cfg().getBoolean("advent.join-reminder", true)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            int n = 0;
            List<Integer> done = plugin.claimed(p.getUniqueId());
            for (int d = 1; d <= 25; d++) if (available(p, d) && !done.contains(d)) n++;
            if (n > 0) plugin.msg(p, "&7You have &a" + n + " &7unclaimed advent gift(s)! Use &e/advent");
            if (!plugin.hasZone(p.getUniqueId()) && plugin.today().getMonthValue() >= 11) {
                plugin.msg(p, "&7Set your timezone with &e/advent timezone &7so doors open at &fyour &7midnight.");
            }
        }, 60L);
    }
}
