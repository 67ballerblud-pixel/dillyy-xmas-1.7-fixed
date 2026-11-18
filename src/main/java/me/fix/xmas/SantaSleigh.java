package me.fix.xmas;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/** Santa's Sleigh: a particle sleigh pulled by reindeer flies over spawn and drops presents on the way. */
public class SantaSleigh {

    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(220, 30, 40), 2.0f);
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 205, 50), 1.6f);
    private static final Particle.DustOptions BROWN = new Particle.DustOptions(Color.fromRGB(130, 80, 40), 1.6f);
    private static final Particle.DustOptions NOSE = new Particle.DustOptions(Color.fromRGB(255, 20, 20), 2.4f);

    private final Xmas plugin;
    private final Presents presents;
    private boolean flying;

    /** Particles are forced so players can see the sleigh from far away (normal range is only 32 blocks). */
    private void fx(World w, Particle part, Location l, int n, double ox, double oy, double oz, double extra, Object data) {
        w.spawnParticle(part, l, n, ox, oy, oz, extra, data, true);
    }

    SantaSleigh(Xmas plugin, Presents presents) {
        this.plugin = plugin;
        this.presents = presents;
    }

    void start() {
        long ticks = Math.max(1, plugin.getConfig().getLong("sleigh.interval-minutes", 30)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getConfig().getBoolean("sleigh.enabled", true)) return;
            if (Bukkit.getOnlinePlayers().size() < plugin.getConfig().getInt("sleigh.min-online", 2)) return;
            fly();
        }, ticks, ticks);
    }

    boolean fly() {
        if (flying) return false;
        World w = Bukkit.getWorld(plugin.getConfig().getString("presents.world", "world"));
        if (w == null) return false;
        flying = true;
        double cx = plugin.getConfig().getDouble("presents.center-x", 0);
        double cz = plugin.getConfig().getDouble("presents.center-z", 0);
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        final double dx = Math.cos(angle), dz = Math.sin(angle);
        final double speed = 0.7;
        final int total = 400;
        final int drops = Math.max(0, plugin.getConfig().getInt("sleigh.drops", 3));
        final double y = w.getHighestBlockYAt((int) cx, (int) cz) + plugin.getConfig().getInt("sleigh.height", 40);
        final Location pos = new Location(w, cx - dx * speed * total / 2, y, cz - dz * speed * total / 2);

        Bukkit.broadcast(Xmas.c(plugin.getConfig().getString("sleigh.message",
                "&c&lSanta's Sleigh &fis flying over spawn! &7Look up - gifts are falling!")));
        for (org.bukkit.entity.Player p : w.getPlayers()) p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.5f, 1.4f);

        new BukkitRunnable() {
            int t;

            @Override
            public void run() {
                try {
                    if (t++ >= total) {
                        flying = false;
                        cancel();
                        return;
                    }
                    pos.add(dx * speed, Math.sin(t * 0.05) * 0.15, dz * speed);
                    render(w, pos, dx, dz, t);
                    if (t % 15 == 0) w.playSound(pos, Sound.BLOCK_NOTE_BLOCK_BELL, 4f, 1.0f + (t % 45) / 45f);
                    if (t % 40 == 0) w.playSound(pos, Sound.ENTITY_HORSE_GALLOP, 3f, 1.2f);
                    int every = Math.max(1, total / (drops + 1));
                    if (drops > 0 && t % every == 0 && t < total) drop(w, pos);
                } catch (Exception ex) {
                    flying = false;
                    cancel();
                    plugin.getLogger().warning("Santa's Sleigh stopped: " + ex);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private void render(World w, Location pos, double dx, double dz, int t) {
        double px = -dz, pz = dx; // sideways
        // sleigh body
        for (int i = -2; i <= 2; i++) {
            Location l = pos.clone().add(-dx * i * 0.4, -0.2, -dz * i * 0.4);
            fx(w, Particle.DUST, l, 1, 0.25, 0.1, 0.25, 0, RED);
        }
        fx(w, Particle.DUST, pos.clone().add(0, 0.5, 0), 4, 0.3, 0.2, 0.3, 0, GOLD);
        // Santa
        fx(w, Particle.DUST, pos.clone().add(-dx * 0.4, 0.9, -dz * 0.4), 2, 0.1, 0.15, 0.1, 0, RED);
        // runners
        for (int s = -1; s <= 1; s += 2) {
            fx(w, Particle.DUST, pos.clone().add(px * 0.5 * s, -0.6, pz * 0.5 * s).add(-dx * 0.3, 0, -dz * 0.3), 3, 0.5 * Math.abs(dx) + 0.1, 0, 0.5 * Math.abs(dz) + 0.1, 0, GOLD);
        }
        // four rows of reindeer, trotting
        for (int row = 1; row <= 4; row++) {
            for (int s = -1; s <= 1; s += 2) {
                double bob = Math.sin(t * 0.4 + row) * 0.2;
                Location l = pos.clone().add(dx * (1.5 + row * 1.3) + px * 0.6 * s, bob, dz * (1.5 + row * 1.3) + pz * 0.6 * s);
                fx(w, Particle.DUST, l, 3, 0.15, 0.2, 0.15, 0, BROWN);
                if (row == 4 && s == 1) fx(w, Particle.DUST, l.clone().add(dx * 0.4, 0.3, dz * 0.4), 1, 0, 0, 0, 0, NOSE);
            }
        }
        // magic trail
        Location back = pos.clone().add(-dx * 2.5, -0.2, -dz * 2.5);
        fx(w, Particle.FIREWORK, back, 4, 0.3, 0.2, 0.3, 0.03, null);
        fx(w, Particle.END_ROD, back, 2, 0.4, 0.2, 0.4, 0.02, null);
        fx(w, Particle.SNOWFLAKE, back, 5, 0.6, 0.3, 0.6, 0.02, null);
    }

    private void drop(World w, Location pos) {
        int x = pos.getBlockX(), z = pos.getBlockZ();
        // falling gift streak so players can see where it lands
        double groundY = w.getHighestBlockYAt(x, z) + 1;
        for (double yy = pos.getY(); yy > groundY; yy -= 1.0) {
            fx(w, Particle.DUST, new Location(w, x + 0.5, yy, z + 0.5), 1, 0.05, 0, 0.05, 0, RED);
        }
        w.playSound(pos, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 3f, 1.4f);
        Location ground = new Location(w, x, groundY, z);
        if (w.isChunkLoaded(x >> 4, z >> 4)) {
            presents.dropAt(ground);
        } else {
            // chunk wasn't loaded: load it, then drop (before this it silently did nothing)
            w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(c ->
                    Bukkit.getScheduler().runTask(plugin, () -> presents.dropAt(ground)));
        }
    }
}
