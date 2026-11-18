package me.fix.xmas;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;

import java.util.concurrent.ThreadLocalRandom;

/** Particle auras for the Christmas items: halos, trails, spirals and sparkles around whoever carries them. */
public class Auras {

    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(230, 30, 40), 1.1f);
    private static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.WHITE, 1.1f);
    private static final Particle.DustOptions GREEN = new Particle.DustOptions(Color.fromRGB(30, 190, 70), 1.1f);
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.0f);
    private static final Particle.DustOptions BROWN = new Particle.DustOptions(Color.fromRGB(150, 85, 40), 1.0f);

    private final Xmas plugin;
    private int step;

    Auras(Xmas plugin) {
        this.plugin = plugin;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 4L);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("auras.enabled", true)) return;
        step++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            PlayerInventory inv = p.getInventory();
            World w = p.getWorld();
            Location base = p.getLocation();

            if ("santa_hat".equals(Items.idOf(inv.getHelmet()))) {
                // rotating red/white halo over the head
                for (int i = 0; i < 3; i++) {
                    double a = (step * 0.45) + i * (Math.PI * 2 / 3);
                    Location l = base.clone().add(Math.cos(a) * 0.45, p.isSneaking() ? 1.85 : 2.1, Math.sin(a) * 0.45);
                    w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, i % 2 == 0 ? RED : WHITE);
                }
                if (step % 6 == 0) w.spawnParticle(Particle.SNOWFLAKE, base.clone().add(0, 2.2, 0), 2, 0.3, 0.1, 0.3, 0.01);
            }

            if (Items.santaSet(inv)) {
                // full Santa set: golden swirl around the whole body
                for (int i = 0; i < 4; i++) {
                    double a = step * 0.5 + i * Math.PI / 2;
                    double y = 0.2 + ((step * 0.1 + i * 0.5) % 2.0);
                    w.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 0.8, y, Math.sin(a) * 0.8), 1, 0, 0, 0, 0, i % 2 == 0 ? GOLD : RED);
                }
                if (step % 10 == 0) w.spawnParticle(Particle.TOTEM_OF_UNDYING, base.clone().add(0, 1, 0), 3, 0.4, 0.6, 0.4, 0.05);
            }

            if ("ice_skates".equals(Items.idOf(inv.getBoots())) && (p.isSprinting() || p.getVelocity().lengthSquared() > 0.04)) {
                w.spawnParticle(Particle.SNOWFLAKE, base.clone().add(0, 0.1, 0), 5, 0.3, 0.05, 0.3, 0.03);
                w.spawnParticle(Particle.END_ROD, base.clone().add(0, 0.1, 0), 1, 0.2, 0.05, 0.2, 0.01);
            }

            if ("reindeer_boots".equals(Items.idOf(inv.getBoots()))) {
                if (p.isSprinting() || p.getVelocity().lengthSquared() > 0.04) {
                    w.spawnParticle(Particle.SNOWFLAKE, base.clone().add(0, 0.1, 0), 4, 0.25, 0.05, 0.25, 0.02);
                    w.spawnParticle(Particle.DUST, base.clone().add(0, 0.15, 0), 2, 0.2, 0.05, 0.2, 0, GOLD);
                    if (step % 5 == 0) w.spawnParticle(Particle.NOTE, base.clone().add(0, 0.3, 0), 1, 0.2, 0.1, 0.2, 1);
                }
            }

            if ("gingerbread_chestplate".equals(Items.idOf(inv.getChestplate())) && step % 5 == 0) {
                w.spawnParticle(Particle.DUST, base.clone().add(0, 1.2, 0), 3, 0.35, 0.4, 0.35, 0, BROWN);
                w.spawnParticle(Particle.DUST, base.clone().add(0, 1.2, 0), 2, 0.35, 0.4, 0.35, 0, WHITE);
            }

            String held = Items.idOf(inv.getItemInMainHand());
            if (held == null) continue;
            Location hand = base.clone().add(0, 1.0, 0);
            switch (held) {
                case "candy_cane_sword", "candy_cane_pickaxe" -> {
                    // candy-stripe double helix
                    for (int i = 0; i < 2; i++) {
                        double a = step * 0.6 + i * Math.PI;
                        double y = ((step * 0.08) + i * 0.5) % 1.8;
                        w.spawnParticle(Particle.DUST, hand.clone().add(Math.cos(a) * 0.7, y - 0.6, Math.sin(a) * 0.7), 1, 0, 0, 0, 0, i == 0 ? RED : WHITE);
                    }
                }
                case "frost_wand", "snow_globe" -> {
                    if (step % 2 == 0) w.spawnParticle(Particle.SNOWFLAKE, hand.clone().add(0, 0.4, 0), 2, 0.4, 0.5, 0.4, 0.01);
                    if (step % 8 == 0) w.spawnParticle(Particle.END_ROD, hand.clone().add(0, 0.6, 0), 1, 0.4, 0.4, 0.4, 0.01);
                }
                case "grinch_heart" -> {
                    if (step % 2 == 0) w.spawnParticle(Particle.DUST, hand.clone().add(0, 0.2, 0), 2, 0.5, 0.6, 0.5, 0, GREEN);
                    if (step % 12 == 0) w.spawnParticle(Particle.HEART, hand.clone().add(0, 0.8, 0), 1, 0.3, 0.2, 0.3, 0);
                }
                case "jingle_bells" -> {
                    if (step % 8 == 0) w.spawnParticle(Particle.NOTE, hand.clone().add(0, 0.9, 0), 1, 0.5, 0.3, 0.5, ThreadLocalRandom.current().nextDouble());
                }
                case "north_star" -> {
                    // orbiting gold stars
                    for (int i = 0; i < 3; i++) {
                        double a = step * 0.35 + i * (Math.PI * 2 / 3);
                        w.spawnParticle(Particle.END_ROD, hand.clone().add(Math.cos(a) * 0.8, 0.5 + Math.sin(a * 2) * 0.3, Math.sin(a) * 0.8), 1, 0, 0, 0, 0);
                    }
                    if (step % 3 == 0) w.spawnParticle(Particle.DUST, hand.clone().add(0, 0.5, 0), 1, 0.6, 0.6, 0.6, 0, GOLD);
                }
                case "yule_log_axe" -> {
                    if (step % 2 == 0) w.spawnParticle(Particle.SMALL_FLAME, hand.clone().add(0, 0.2, 0), 2, 0.5, 0.6, 0.5, 0.01);
                    if (step % 10 == 0) w.spawnParticle(Particle.LAVA, hand.clone().add(0, 0.5, 0), 1, 0.4, 0.3, 0.4, 0);
                }
                case "mistletoe_kiss" -> {
                    if (step % 3 == 0) w.spawnParticle(Particle.DUST, hand.clone().add(0, 0.6, 0), 1, 0.5, 0.5, 0.5, 0, step % 6 == 0 ? RED : GREEN);
                    if (step % 14 == 0) w.spawnParticle(Particle.HEART, hand.clone().add(0, 1.0, 0), 1, 0.3, 0.2, 0.3, 0);
                }
                case "snowball_cannon" -> {
                    if (step % 3 == 0) w.spawnParticle(Particle.SNOWFLAKE, hand.clone().add(0, 0.5, 0), 2, 0.4, 0.4, 0.4, 0.02);
                }
                case "frosty_coal" -> {
                    if (step % 2 == 0) w.spawnParticle(Particle.SNOWFLAKE, hand.clone().add(0, 0.6, 0), 2, 0.4, 0.5, 0.4, 0.01);
                    if (step % 6 == 0) w.spawnParticle(Particle.ASH, hand.clone().add(0, 0.6, 0), 3, 0.4, 0.4, 0.4, 0);
                }
                case "sleigh_launcher" -> {
                    if (step % 3 == 0) w.spawnParticle(Particle.FIREWORK, hand.clone().add(0, 0.2, 0), 1, 0.4, 0.3, 0.4, 0.02);
                }
                case "eggnog_flask" -> {
                    if (step % 4 == 0) w.spawnParticle(Particle.DUST, hand.clone().add(0, 0.5, 0), 2, 0.3, 0.3, 0.3, 0, GOLD);
                }
                case "holly_bow" -> {
                    if (step % 3 == 0) w.spawnParticle(Particle.DUST, hand.clone().add(0, 0.5, 0), 1, 0.4, 0.4, 0.4, 0, step % 6 == 0 ? RED : GREEN);
                }
                default -> {
                }
            }
        }
    }
}
