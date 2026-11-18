package me.fix.xmas;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class Xmas extends JavaPlugin implements CommandExecutor, TabCompleter {

    private File dataFile;
    private YamlConfiguration data;
    private Advent advent;
    private Presents presents;
    private Boss boss;
    private Mailbox mailbox;
    private Festive festive;
    private SantaSleigh sleigh;
    private Catalog catalog;
    private Music music;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        advent = new Advent(this);
        presents = new Presents(this);
        getServer().getPluginManager().registerEvents(advent, this);
        getServer().getPluginManager().registerEvents(presents, this);
        presents.start();
        new Snow(this).start();
        Items.init(this);
        CEnchant.init(this);
        ItemListener itemListener = new ItemListener(this);
        getServer().getPluginManager().registerEvents(itemListener, this);
        itemListener.start();
        EnchantListener enchantListener = new EnchantListener(this);
        getServer().getPluginManager().registerEvents(enchantListener, this);
        enchantListener.start();
        festive = new Festive(this);
        getServer().getPluginManager().registerEvents(festive, this);
        festive.start();
        new Auras(this).start();
        sleigh = new SantaSleigh(this, presents);
        sleigh.start();
        catalog = new Catalog();
        getServer().getPluginManager().registerEvents(catalog, this);
        music = new Music(this);
        music.load();
        getServer().getPluginManager().registerEvents(music, this);
        boss = new Boss(this);
        getServer().getPluginManager().registerEvents(boss, this);
        boss.start();
        mailbox = new Mailbox(this);
        mailbox.start();
        getCommand("xmas").setExecutor(this);
        getCommand("xmas").setTabCompleter(this);
        getCommand("advent").setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (presents != null) presents.clearAll();
        if (boss != null) boss.remove();
        if (mailbox != null) mailbox.shutdown();
        saveData();
    }

    // ---------- helpers ----------
    static Component c(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    static Component line(String s) {
        return c(s).decoration(TextDecoration.ITALIC, false);
    }

    void msg(CommandSender to, String s) {
        to.sendMessage(c(getConfig().getString("prefix", "&c[Xmas] ") + s));
    }

    ZoneId zone() {
        try {
            return ZoneId.of(getConfig().getString("timezone", "America/Chicago"));
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    LocalDate today() {
        return LocalDate.now(zone());
    }

    // per-player timezone (so advent doors unlock at each player's own midnight)
    boolean hasZone(UUID id) {
        return data.getString("timezone." + id) != null;
    }

    ZoneId zoneFor(UUID id) {
        String s = data.getString("timezone." + id);
        if (s != null) {
            try {
                return ZoneId.of(s);
            } catch (Exception ignored) {
            }
        }
        return zone();
    }

    LocalDate todayFor(UUID id) {
        return LocalDate.now(zoneFor(id));
    }

    void setZone(UUID id, String zoneId) {
        data.set("timezone." + id, zoneId);
        saveData();
    }

    void runCommands(Player p, List<String> cmds) {
        for (String cmd : cmds) {
            String run = cmd.replace("%player%", p.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run);
        }
    }

    // ---------- data ----------
    YamlConfiguration data() {
        return data;
    }

    List<Integer> claimed(UUID id) {
        return data.getIntegerList("advent." + id);
    }

    void addClaimed(UUID id, int day) {
        List<Integer> l = new ArrayList<>(claimed(id));
        if (!l.contains(day)) l.add(day);
        data.set("advent." + id, l);
        saveData();
    }

    void clearClaims(UUID id) {
        data.set("advent." + id, null);
        saveData();
    }

    boolean snowOff(UUID id) {
        return data.getStringList("snow-off").contains(id.toString());
    }

    /** Returns true if snow is now ON for this player. */
    boolean toggleSnow(UUID id) {
        List<String> l = new ArrayList<>(data.getStringList("snow-off"));
        boolean nowOn;
        if (l.remove(id.toString())) {
            nowOn = true;
        } else {
            l.add(id.toString());
            nowOn = false;
        }
        data.set("snow-off", l);
        saveData();
        return nowOn;
    }

    void saveData() {
        try {
            getDataFolder().mkdirs();
            data.save(dataFile);
        } catch (Exception e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    // ---------- commands ----------
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("advent")) {
            if (!(s instanceof Player p)) {
                s.sendMessage("Players only.");
                return true;
            }
            if (a.length > 0 && a[0].equalsIgnoreCase("timezone")) advent.timezoneCommand(p, a);
            else advent.open(p);
            return true;
        }
        if (a.length == 0) {
            info(s);
            return true;
        }
        switch (a[0].toLowerCase()) {
            case "snow" -> {
                if (!(s instanceof Player p)) return true;
                msg(p, toggleSnow(p.getUniqueId()) ? "&aSnow is now &fON&a." : "&cSnow is now &fOFF&c.");
            }
            case "present" -> {
                if (!s.hasPermission("xmas.admin")) { msg(s, "&cNo permission."); return true; }
                String mode = a.length > 1 ? a[1].toLowerCase() : "random";
                switch (mode) {
                    case "here" -> {
                        if (!(s instanceof Player p)) return true;
                        presents.create(p.getLocation().getBlock().getLocation(), true);
                    }
                    case "clear" -> {
                        presents.clearAll();
                        msg(s, "&aAll presents removed.");
                    }
                    default -> {
                        presents.spawnRandom(15, true);
                        msg(s, "&aTrying to spawn a present...");
                    }
                }
            }
            case "music" -> music.command(s, a);
            case "catalog" -> {
                if (!(s instanceof Player p)) return true;
                catalog.open(p);
            }
            case "streak" -> {
                if (!(s instanceof Player p)) return true;
                int n = festive.streakOf(p.getUniqueId());
                msg(p, n == 0 ? "&7No login streak yet. Come back tomorrow!" : "&6Login streak: &f" + n + " &7day(s). Keep it going!");
            }
            case "sleigh" -> {
                if (!s.hasPermission("xmas.admin")) { msg(s, "&cNo permission."); return true; }
                msg(s, sleigh.fly() ? "&aSanta's sleigh is on its way!" : "&cThe sleigh is already flying (or the world is missing).");
            }
            case "show" -> {
                if (!s.hasPermission("xmas.admin")) { msg(s, "&cNo permission."); return true; }
                if (!(s instanceof Player p)) return true;
                festive.show(p.getLocation().add(0, 10, 0), 10);
                festive.jingle(p);
                msg(s, "&aLet it snow, let it boom!");
            }
            case "reset" -> {
                if (!s.hasPermission("xmas.admin")) { msg(s, "&cNo permission."); return true; }
                if (a.length < 2) { msg(s, "&cUsage: /xmas reset <player>"); return true; }
                @SuppressWarnings("deprecation")
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(a[1]);
                clearClaims(op.getUniqueId());
                msg(s, "&aAdvent claims reset for &f" + a[1] + "&a.");
            }
            case "give" -> {
                if (!s.hasPermission("xmas.admin")) { msg(s, "&cNo permission."); return true; }
                if (a.length < 3) { msg(s, "&cUsage: /xmas give <player> <item> [amount]  &7Items: " + String.join(", ", Items.IDS)); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { msg(s, "&cThat player is offline."); return true; }
                int amount = 1;
                if (a.length > 3) {
                    try {
                        amount = Integer.parseInt(a[3]);
                    } catch (NumberFormatException ex) {
                        msg(s, "&cAmount must be a number.");
                        return true;
                    }
                }
                org.bukkit.inventory.ItemStack item = Items.create(a[2].toLowerCase(), amount);
                if (item == null) { msg(s, "&cUnknown item. Items: " + String.join(", ", Items.IDS)); return true; }
                for (org.bukkit.inventory.ItemStack left : t.getInventory().addItem(item).values()) {
                    t.getWorld().dropItemNaturally(t.getLocation(), left);
                }
                msg(s, "&aGave &f" + amount + "x " + a[2] + " &ato &f" + t.getName() + "&a.");
            }
            case "enchants" -> {
                msg(s, "&dCustom enchantments &7(get books from the Christmas crate, apply them in an anvil):");
                for (CEnchant ce : CEnchant.values()) {
                    s.sendMessage(c("&d  " + ce.display + " &7(max " + CEnchant.roman(ce.max) + ", " + ce.appliesText() + ") &8- &f" + ce.description));
                }
            }
            case "book" -> {
                if (!s.hasPermission("xmas.admin")) { msg(s, "&cNo permission."); return true; }
                if (a.length < 3) { msg(s, "&cUsage: /xmas book <player> <enchant> [level]"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { msg(s, "&cThat player is offline."); return true; }
                CEnchant ce = CEnchant.byId(a[2]);
                if (ce == null) { msg(s, "&cUnknown enchant. Try /xmas enchants"); return true; }
                int lvl = 1;
                if (a.length > 3) {
                    try {
                        lvl = Integer.parseInt(a[3]);
                    } catch (NumberFormatException ex) {
                        msg(s, "&cLevel must be a number.");
                        return true;
                    }
                }
                for (org.bukkit.inventory.ItemStack left : t.getInventory().addItem(CEnchant.book(ce, lvl)).values()) {
                    t.getWorld().dropItemNaturally(t.getLocation(), left);
                }
                msg(s, "&aGave &f" + ce.display + " " + CEnchant.roman(Math.max(1, Math.min(ce.max, lvl))) + " &abook to &f" + t.getName() + "&a.");
            }
            case "enchant" -> {
                if (!s.hasPermission("xmas.admin")) { msg(s, "&cNo permission."); return true; }
                if (a.length < 3) { msg(s, "&cUsage: /xmas enchant <player> <enchant> [level]  &7(applies to the item they hold)"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { msg(s, "&cThat player is offline."); return true; }
                CEnchant ce = CEnchant.byId(a[2]);
                if (ce == null) { msg(s, "&cUnknown enchant. Try /xmas enchants"); return true; }
                int lvl = ce.max;
                if (a.length > 3) {
                    try {
                        lvl = Integer.parseInt(a[3]);
                    } catch (NumberFormatException ex) {
                        msg(s, "&cLevel must be a number.");
                        return true;
                    }
                }
                org.bukkit.inventory.ItemStack held = t.getInventory().getItemInMainHand();
                if (held.getType().isAir() || !ce.canApplyTo(held.getType())) {
                    msg(s, "&c" + ce.display + " goes on: " + ce.appliesText() + ". They must be holding one.");
                    return true;
                }
                t.getInventory().setItemInMainHand(CEnchant.apply(held, ce, lvl));
                msg(s, "&aApplied &f" + ce.display + " " + CEnchant.roman(Math.max(1, Math.min(ce.max, lvl))) + " &ato &f" + t.getName() + "&a's held item.");
            }
            case "boss" -> {
                if (!s.hasPermission("xmas.admin")) { msg(s, "&cNo permission."); return true; }
                String mode = a.length > 1 ? a[1].toLowerCase() : "info";
                switch (mode) {
                    case "spawn" -> {
                        org.bukkit.Location where = s instanceof Player p ? p.getLocation() : boss.configLocation();
                        if (where == null) { msg(s, "&cSet boss.location in config.yml first."); return true; }
                        msg(s, boss.spawn(where) ? "&aThe Grinch has appeared!" : "&cA boss is already active.");
                    }
                    case "kill" -> {
                        boss.remove();
                        msg(s, "&aBoss removed (no rewards).");
                    }
                    default -> msg(s, boss.info());
                }
            }
            case "mailbox" -> {
                if (!s.hasPermission("xmas.admin")) { msg(s, "&cNo permission."); return true; }
                if (a.length < 3 || !a[1].equalsIgnoreCase("give")) { msg(s, "&cUsage: /xmas mailbox give <player> [amount]"); return true; }
                Player t = Bukkit.getPlayerExact(a[2]);
                if (t == null) { msg(s, "&cThat player is offline."); return true; }
                int amount = 1;
                if (a.length > 3) {
                    try {
                        amount = Integer.parseInt(a[3]);
                    } catch (NumberFormatException ex) {
                        msg(s, "&cAmount must be a number.");
                        return true;
                    }
                }
                for (org.bukkit.inventory.ItemStack left : t.getInventory().addItem(mailbox.item(amount)).values()) {
                    t.getWorld().dropItemNaturally(t.getLocation(), left);
                }
                msg(s, "&aGave &f" + Math.max(1, Math.min(64, amount)) + "x Christmas Mailbox &ato &f" + t.getName() + "&a.");
            }
            case "timezone" -> {
                if (!s.hasPermission("xmas.admin")) { msg(s, "&cNo permission."); return true; }
                if (a.length < 3) { msg(s, "&cUsage: /xmas timezone <player> <zone, e.g. America/New_York>"); return true; }
                try {
                    ZoneId.of(a[2]);
                } catch (Exception ex) {
                    msg(s, "&cThat isn't a valid timezone.");
                    return true;
                }
                @SuppressWarnings("deprecation")
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(a[1]);
                setZone(op.getUniqueId(), a[2]);
                msg(s, "&aTimezone for &f" + a[1] + " &aset to &f" + a[2] + "&a.");
            }
            case "reload" -> {
                if (!s.hasPermission("xmas.admin")) { msg(s, "&cNo permission."); return true; }
                reloadConfig();
                mailbox.reload();
                music.load();
                msg(s, "&aConfig reloaded.");
            }
            default -> info(s);
        }
        return true;
    }

    private void info(CommandSender s) {
        LocalDate t = today();
        LocalDate xmas = LocalDate.of(t.getYear(), 12, 25);
        if (t.isAfter(xmas)) xmas = xmas.plusYears(1);
        long days = ChronoUnit.DAYS.between(t, xmas);
        msg(s, days == 0 ? "&6&lMerry Christmas!" : "&f" + days + " &7day(s) until Christmas!");
        s.sendMessage(c("&7  /advent &8- &fopen the advent calendar"));
        s.sendMessage(c("&7  /xmas snow &8- &ftoggle snow effect"));
        s.sendMessage(c("&7  Hunt for &cpresents &7that appear around the world!"));
        s.sendMessage(c("&7  Craft a &cChristmas Mailbox &7to mail gifts to any player!"));
        s.sendMessage(c("&7  /xmas enchants &8- &flist the custom enchantments"));
        s.sendMessage(c("&7  /xmas catalog &8- &fbrowse every Christmas item and its ability"));
        s.sendMessage(c("&7  /xmas streak &8- &fyour daily login streak"));
        s.sendMessage(c("&7  /xmas music <song> &8- &fget a Christmas music disc for a jukebox"));
        s.sendMessage(c("&7  Watch the sky for &cSanta's Sleigh&7 - it drops presents!"));
        s.sendMessage(c("&7  Ten-plus &6custom Christmas items &7drop from the &cChristmas Crate&7 and the &aGrinch&7."));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("xmas")) {
            if (a.length == 1) {
                out.addAll(List.of("snow", "enchants", "catalog", "streak", "music"));
                if (s.hasPermission("xmas.admin")) out.addAll(List.of("sleigh", "show", "present", "reset", "timezone", "give", "boss", "mailbox", "book", "enchant", "reload"));
            } else if (a.length == 2 && a[0].equalsIgnoreCase("music")) {
                out.addAll(music.ids());
            } else if (a.length == 2 && a[0].equalsIgnoreCase("present")) {
                out.addAll(List.of("random", "here", "clear"));
            } else if (a.length == 2 && a[0].equalsIgnoreCase("boss")) {
                out.addAll(List.of("spawn", "kill", "info"));
            } else if (a.length == 2 && a[0].equalsIgnoreCase("mailbox")) {
                out.add("give");
            } else if (a.length == 3 && a[0].equalsIgnoreCase("give")) {
                out.addAll(Items.IDS);
            } else if (a.length == 3 && (a[0].equalsIgnoreCase("book") || a[0].equalsIgnoreCase("enchant"))) {
                for (CEnchant ce : CEnchant.values()) out.add(ce.id);
            }
        }
        String start = a.length == 0 ? "" : a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(start));
        return out;
    }
}
