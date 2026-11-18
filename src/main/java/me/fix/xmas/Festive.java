package me.fix.xmas;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.meta.FireworkMeta;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Server-wide festive stuff: join title + jingle, rotating tips, Christmas Eve/Day firework shows. */
public class Festive implements Listener {

    private static final Color[] PALETTE = {Color.RED, Color.GREEN, Color.WHITE, Color.fromRGB(255, 200, 40), Color.fromRGB(60, 200, 255)};

    private final Xmas plugin;
    private int tipIndex;

    Festive(Xmas plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    void start() {
        long tipTicks = Math.max(1, cfg().getLong("festive.tip-interval-minutes", 10)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tip, tipTicks, tipTicks);
        long showTicks = Math.max(10, cfg().getLong("festive.firework-interval-seconds", 45)) * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::autoShow, showTicks, showTicks);
    }

    long daysUntilChristmas() {
        LocalDate t = plugin.today();
        LocalDate x = LocalDate.of(t.getYear(), 12, 25);
        if (t.isAfter(x)) x = x.plusYears(1);
        return ChronoUnit.DAYS.between(t, x);
    }

    // ---------- join greeting ----------
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!cfg().getBoolean("festive.join-title", true)) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            long d = daysUntilChristmas();
            String sub = d == 0 ? "&e&lHo ho ho! &fIt's Christmas Day!" : "&f" + d + " &7day" + (d == 1 ? "" : "s") + " until &cChristmas";
            p.showTitle(Title.title(Xmas.c("&c&lMerry &a&lChristmas&f!"), Xmas.c(sub),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofMillis(2600), Duration.ofMillis(800))));
            jingle(p);
            streak(p);
        }, 40L);
    }

    // ---------- login streaks ----------
    private void streak(Player p) {
        if (!cfg().getBoolean("streak.enabled", true)) return;
        java.util.UUID id = p.getUniqueId();
        LocalDate today = plugin.todayFor(id);
        String last = plugin.data().getString("streak." + id + ".last");
        int count = plugin.data().getInt("streak." + id + ".count", 0);
        if (today.toString().equals(last)) return;
        count = (last != null && today.minusDays(1).toString().equals(last)) ? count + 1 : 1;
        plugin.data().set("streak." + id + ".last", today.toString());
        plugin.data().set("streak." + id + ".count", count);
        plugin.saveData();
        plugin.runCommands(p, cfg().getStringList("streak.daily-commands"));
        List<String> milestone = cfg().getStringList("streak.milestones." + count);
        p.sendMessage(Xmas.c(cfg().getString("prefix", "&c[Xmas] ") + "&6&lLogin streak: &f" + count + " &7day" + (count == 1 ? "" : "s") + " in a row!"));
        if (!milestone.isEmpty()) {
            plugin.runCommands(p, milestone);
            Bukkit.broadcast(Xmas.c(cfg().getString("prefix", "&c[Xmas] ") + "&e" + p.getName() + " &fhit a &6" + count + "-day &flogin streak and got a milestone gift!"));
            show(p.getLocation().add(0, 6, 0), 5);
        }
    }

    int streakOf(java.util.UUID id) {
        return plugin.data().getInt("streak." + id + ".count", 0);
    }

    /** A short bell melody for one player. */
    void jingle(Player p) {
        float[] pitches = {1.0f, 1.0f, 1.26f, 1.0f, 1.5f, 1.33f};
        for (int i = 0; i < pitches.length; i++) {
            final float pitch = pitches[i];
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, pitch);
            }, i * 4L);
        }
    }

    // ---------- tips ----------
    private void tip() {
        if (!cfg().getBoolean("festive.tips-enabled", true) || Bukkit.getOnlinePlayers().isEmpty()) return;
        List<String> tips = cfg().getStringList("festive.tips");
        if (tips.isEmpty()) return;
        String line = tips.get(tipIndex++ % tips.size());
        Bukkit.broadcast(Xmas.c(cfg().getString("prefix", "&c[Xmas] ") + line));
    }

    // ---------- firework shows ----------
    private void autoShow() {
        if (!cfg().getBoolean("festive.fireworks", true) || Bukkit.getOnlinePlayers().isEmpty()) return;
        boolean always = cfg().getBoolean("festive.fireworks-always", false);
        LocalDate t = plugin.today();
        boolean xmasDays = t.getMonthValue() == 12 && (t.getDayOfMonth() == 24 || t.getDayOfMonth() == 25);
        if (!always && !xmasDays) return;
        for (String name : cfg().getStringList("snow.worlds")) {
            World w = Bukkit.getWorld(name);
            if (w == null || w.getPlayers().isEmpty()) continue;
            show(w.getSpawnLocation().add(0, 12, 0), 6);
        }
    }

    /** Launches a ring of red/green/white/gold fireworks around a point. */
    void show(Location center, int count) {
        World w = center.getWorld();
        if (w == null) return;
        for (int i = 0; i < count; i++) {
            final int n = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                double r = 4 + ThreadLocalRandom.current().nextDouble(10);
                Location l = center.clone().add(Math.cos(a) * r, ThreadLocalRandom.current().nextDouble(8), Math.sin(a) * r);
                burst(l, n % 2 == 0 ? FireworkEffect.Type.BALL_LARGE : FireworkEffect.Type.STAR);
            }, i * 6L);
        }
    }

    static void burst(Location l, FireworkEffect.Type type) {
        Firework fw = l.getWorld().spawn(l, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        meta.addEffect(FireworkEffect.builder()
                .withColor(PALETTE[r.nextInt(PALETTE.length)], PALETTE[r.nextInt(PALETTE.length)])
                .withFade(Color.WHITE).with(type).trail(true).flicker(true).build());
        fw.setFireworkMeta(meta);
        fw.detonate();
    }
}
