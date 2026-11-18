package me.fix.xmas;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** The Grinch: a Christmas raid boss. Spawn with /xmas boss spawn (or enable auto-spawn in config). */
public class Boss implements Listener {

    private final Xmas plugin;
    private Zombie boss;
    private BossBar bar;
    private BukkitTask loop;
    private long spawnedAt;
    private int seconds;
    private boolean enraged;
    private int phase = 1;
    private int fxStep;
    private BukkitTask fx;
    private long nextAuto;
    private final Map<UUID, Double> damage = new HashMap<>();
    private final Set<UUID> minions = new HashSet<>();
    private final Set<UUID> shown = new HashSet<>();

    Boss(Xmas plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    void start() {
        nextAuto = System.currentTimeMillis() + autoMs();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!cfg().getBoolean("boss.auto-spawn.enabled", false) || active()) return;
            if (System.currentTimeMillis() < nextAuto) return;
            nextAuto = System.currentTimeMillis() + autoMs();
            if (Bukkit.getOnlinePlayers().size() < cfg().getInt("boss.auto-spawn.min-online", 3)) return;
            Location l = configLocation();
            if (l != null) spawn(l);
        }, 200L, 200L);
    }

    private long autoMs() {
        return Math.max(1, cfg().getLong("boss.auto-spawn.interval-minutes", 120)) * 60_000L;
    }

    Location configLocation() {
        World w = Bukkit.getWorld(cfg().getString("boss.location.world", "world"));
        if (w == null) return null;
        return new Location(w, cfg().getDouble("boss.location.x", 0), cfg().getDouble("boss.location.y", 100),
                cfg().getDouble("boss.location.z", 0));
    }

    boolean active() {
        return boss != null && boss.isValid() && !boss.isDead();
    }

    String info() {
        if (!active()) return "No boss is active.";
        Location l = boss.getLocation();
        return "The Grinch: " + (int) boss.getHealth() + " HP at " + l.getBlockX() + ", " + l.getBlockY() + ", "
                + l.getBlockZ() + " (" + l.getWorld().getName() + ")";
    }

    private void setAttr(LivingEntity e, Attribute a, double v) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst != null) inst.setBaseValue(v);
    }

    private ItemStack leather(Material m, Color c) {
        ItemStack it = new ItemStack(m);
        if (it.getItemMeta() instanceof LeatherArmorMeta lm) {
            lm.setColor(c);
            it.setItemMeta(lm);
        }
        return it;
    }

    // ---------- spawning ----------
    boolean spawn(Location loc) {
        if (active() || loc.getWorld() == null) return false;
        String name = cfg().getString("boss.name", "&a&lThe Grinch");
        double health = cfg().getDouble("boss.health", 1000);

        boss = loc.getWorld().spawn(loc, Zombie.class, z -> {
            z.setBaby(false);
            z.customName(Xmas.c(name));
            z.setCustomNameVisible(true);
            z.setPersistent(false);          // never saved to disk, so no orphan bosses after a restart
            z.setRemoveWhenFarAway(false);
            z.setShouldBurnInDay(false);
            z.setCanPickupItems(false);
            setAttr(z, Attribute.MAX_HEALTH, health);
            z.setHealth(health);
            setAttr(z, Attribute.ATTACK_DAMAGE, cfg().getDouble("boss.damage", 8));
            setAttr(z, Attribute.MOVEMENT_SPEED, cfg().getDouble("boss.speed", 0.28));
            setAttr(z, Attribute.KNOCKBACK_RESISTANCE, 0.8);
            setAttr(z, Attribute.SCALE, cfg().getDouble("boss.scale", 2.0));
            setAttr(z, Attribute.FOLLOW_RANGE, 48);
            EntityEquipment eq = z.getEquipment();
            eq.setHelmet(leather(Material.LEATHER_HELMET, Color.GREEN));
            eq.setChestplate(leather(Material.LEATHER_CHESTPLATE, Color.RED));
            eq.setLeggings(leather(Material.LEATHER_LEGGINGS, Color.GREEN));
            eq.setBoots(leather(Material.LEATHER_BOOTS, Color.RED));
            ItemStack sword = Items.create("candy_cane_sword", 1);
            if (sword != null) eq.setItemInMainHand(sword);
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        });

        damage.clear();
        minions.clear();
        shown.clear();
        enraged = false;
        phase = 1;
        seconds = 0;
        spawnedAt = System.currentTimeMillis();
        bar = BossBar.bossBar(Xmas.c(name), 1f, BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
        loop = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        fx = Bukkit.getScheduler().runTaskTimer(plugin, this::visual, 2L, 4L);
        loc.getWorld().strikeLightningEffect(loc);
        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4f, 0.7f);
        for (Player near : loc.getWorld().getPlayers()) {
            if (near.getLocation().distanceSquared(loc) < 100 * 100) {
                near.showTitle(Title.title(Xmas.c("&a&lTHE GRINCH"), Xmas.c("&7has come to steal Christmas"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500), Duration.ofMillis(700))));
            }
        }

        loc.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 3f, 0.6f);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 80, 2, 1, 2, 0.1);
        if (cfg().getBoolean("boss.broadcast-spawn", true)) {
            Bukkit.broadcast(Xmas.c(cfg().getString("prefix", "") + "&a&lThe Grinch &fis trying to ruin Christmas at &eX "
                    + loc.getBlockX() + ", Y " + loc.getBlockY() + ", Z " + loc.getBlockZ() + " &f(" + loc.getWorld().getName()
                    + ")! Stop him!"));
        }
        return true;
    }

    /** Remove the boss and everything it spawned without giving rewards. */
    void remove() {
        if (boss != null && boss.isValid()) boss.remove();
        cleanup();
    }

    private void cleanup() {
        if (loop != null) loop.cancel();
        loop = null;
        if (fx != null) fx.cancel();
        fx = null;
        phase = 1;
        if (bar != null) {
            for (UUID id : shown) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.hideBossBar(bar);
            }
        }
        for (UUID id : minions) {
            Entity m = Bukkit.getEntity(id);
            if (m != null) m.remove();
        }
        minions.clear();
        shown.clear();
        damage.clear();
        boss = null;
        bar = null;
        enraged = false;
        seconds = 0;
    }

    // ---------- main loop ----------
    private void tick() {
        if (!active()) {
            cleanup();
            return;
        }
        seconds++;
        double max = boss.getAttribute(Attribute.MAX_HEALTH).getValue();
        double hp = boss.getHealth();
        bar.progress((float) Math.max(0, Math.min(1, hp / max)));
        bar.name(Xmas.c(cfg().getString("boss.name", "&a&lThe Grinch") + " &7- &c" + (int) hp + "&7/&c" + (int) max));

        Location bl = boss.getLocation();
        for (Player p : boss.getWorld().getPlayers()) {
            boolean near = p.getLocation().distanceSquared(bl) < 80 * 80;
            if (near && shown.add(p.getUniqueId())) p.showBossBar(bar);
            else if (!near && shown.remove(p.getUniqueId())) p.hideBossBar(bar);
        }

        if (phase == 1 && hp < max * 0.66) enterPhase(2);
        else if (phase == 2 && hp < max * 0.33) enterPhase(3);

        long lifeMs = Math.max(1, cfg().getLong("boss.max-lifetime-minutes", 20)) * 60_000L;
        if (System.currentTimeMillis() - spawnedAt > lifeMs) {
            Bukkit.broadcast(Xmas.c(cfg().getString("prefix", "") + "&a&lThe Grinch &fgot bored and went back to his cave."));
            remove();
            return;
        }

        int interval = Math.max(2, cfg().getInt("boss.ability-interval-seconds", 7));
        if (enraged) interval = Math.max(2, interval / 2);
        if (seconds % interval == 0) ability();
    }

    private List<Player> nearbyPlayers(double radius) {
        List<Player> out = new ArrayList<>();
        for (Entity e : boss.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player p && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) {
                out.add(p);
            }
        }
        return out;
    }

    private void enrage() {
        enraged = true;
        setAttr(boss, Attribute.MOVEMENT_SPEED, cfg().getDouble("boss.speed", 0.28) + 0.07);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 3f, 0.5f);
        for (Player p : nearbyPlayers(80)) {
            p.sendMessage(Xmas.c("&c&lThe Grinch is ENRAGED! &7He moves faster and attacks more often!"));
        }
    }

    private void ability() {
        List<Player> targets = nearbyPlayers(30);
        if (targets.isEmpty()) return;
        List<Integer> pool = new ArrayList<>(List.of(0, 1, 2));
        if (phase >= 2) pool.addAll(List.of(3, 4));
        if (phase >= 3) pool.addAll(List.of(5, 6, 3));
        int pick = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        if (pick == 2 && minions.size() >= cfg().getInt("boss.max-minions", 6)) pick = 0;
        switch (pick) {
            case 0 -> stomp();
            case 1 -> snowballs(targets);
            case 2 -> elves(targets);
            case 3 -> coalRain(targets);
            case 4 -> coldHeart();
            case 5 -> leapSlam(targets);
            default -> drain();
        }
    }

    /** Knockback vector away from a point, safe when the player is standing exactly on it. */
    private static Vector away(Player p, Location from, double h, double y) {
        Vector v = p.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (v.lengthSquared() < 0.0001) v = new Vector(1, 0, 0);
        return v.normalize().multiply(h).setY(y);
    }

    // ---------- phases ----------
    private void enterPhase(int n) {
        phase = n;
        World w = boss.getWorld();
        Location l = boss.getLocation();
        String sub = n == 2 ? "&e&lPhase 2 &7- The Grinch hurls cursed coal and freezes hearts"
                : "&c&lPhase 3 &7- ENRAGED. He leaps, drains, and will not stop";
        bar.color(n == 2 ? BossBar.Color.YELLOW : BossBar.Color.RED);
        if (n == 3) enrage();
        w.playSound(l, Sound.ENTITY_WITHER_SPAWN, 2f, 0.8f);
        w.strikeLightningEffect(l);
        w.spawnParticle(Particle.EXPLOSION, l.clone().add(0, 2, 0), 6, 2, 1, 2, 0);
        for (Player p : nearbyPlayers(80)) {
            p.showTitle(Title.title(Xmas.c("&a&lThe Grinch Roars!"), Xmas.c(sub),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(600))));
        }
        // brief invulnerability while his elves rush in, so he cannot just be burst through the phase change
        boss.setInvulnerable(true);
        List<Player> targets = nearbyPlayers(30);
        if (!targets.isEmpty()) elves(targets);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (active()) boss.setInvulnerable(false);
        }, 100L);
    }

    /** Green swirl around the Grinch, hotter in later phases. */
    private void visual() {
        if (!active()) return;
        fxStep++;
        World w = boss.getWorld();
        Location base = boss.getLocation();
        Color c = phase == 1 ? Color.fromRGB(40, 190, 70) : phase == 2 ? Color.fromRGB(190, 220, 40) : Color.fromRGB(230, 40, 40);
        Particle.DustOptions opt = new Particle.DustOptions(c, 1.6f);
        for (int i = 0; i < 3; i++) {
            double a = fxStep * 0.4 + i * (Math.PI * 2 / 3);
            double y = (fxStep * 0.15 + i * 1.1) % 3.6;
            w.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * 1.4, y, Math.sin(a) * 1.4), 1, 0, 0, 0, 0, opt);
        }
        if (phase >= 3 && fxStep % 3 == 0) w.spawnParticle(Particle.SMALL_FLAME, base.clone().add(0, 1.5, 0), 3, 0.8, 1.2, 0.8, 0.01);
        if (phase >= 2 && fxStep % 5 == 0) w.spawnParticle(Particle.SNOWFLAKE, base.clone().add(0, 3.5, 0), 4, 1, 0.3, 1, 0.01);
    }

    // ---------- phase 2+ abilities ----------
    /** Telegraphed coal bombs: a red ring warns where they land. */
    private void coalRain(List<Player> targets) {
        double dmg = cfg().getDouble("boss.coal-damage", 7);
        List<Location> spots = new ArrayList<>();
        for (Player t : targets) {
            if (spots.size() >= 10) break;
            spots.add(t.getLocation().clone());
            for (int i = 0; i < 2; i++) {
                spots.add(t.getLocation().clone().add(ThreadLocalRandom.current().nextDouble(-5, 5), 0, ThreadLocalRandom.current().nextDouble(-5, 5)));
            }
            t.sendActionBar(Xmas.c("&8&lCOAL RAIN! &7Get out of the red!"));
        }
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 2f, 0.6f);
        for (Location spot : spots) {
            new BukkitRunnable() {
                int t;

                @Override
                public void run() {
                    World w = spot.getWorld();
                    if (w == null || !active()) {
                        cancel();
                        return;
                    }
                    if (t++ < 20) {
                        for (int i = 0; i < 20; i++) {
                            double a = Math.PI * 2 * i / 20;
                            w.spawnParticle(Particle.DUST, spot.getX() + Math.cos(a) * 2.5, spot.getY() + 0.15, spot.getZ() + Math.sin(a) * 2.5,
                                    1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(230, 30, 30), 1.2f));
                        }
                        w.spawnParticle(Particle.ASH, spot.clone().add(0, 8 - t * 0.35, 0), 3, 0.3, 0.3, 0.3, 0);
                        return;
                    }
                    w.spawnParticle(Particle.EXPLOSION, spot.clone().add(0, 0.5, 0), 3, 1, 0.3, 1, 0);
                    w.spawnParticle(Particle.LARGE_SMOKE, spot.clone().add(0, 0.5, 0), 25, 1.2, 0.3, 1.2, 0.03);
                    w.playSound(spot, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.1f);
                    for (Entity en : w.getNearbyEntities(spot, 2.5, 3, 2.5)) {
                        if (en instanceof Player p && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) {
                            p.damage(dmg, boss);
                            p.setVelocity(away(p, spot, 0.6, 0.6));
                        }
                    }
                    cancel();
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }
    }

    /** Cold Heart: a freezing shockwave. */
    private void coldHeart() {
        Location l = boss.getLocation();
        World w = l.getWorld();
        double dmg = cfg().getDouble("boss.cold-heart-damage", 4);
        w.playSound(l, Sound.ENTITY_PLAYER_HURT_FREEZE, 3f, 0.6f);
        w.playSound(l, Sound.BLOCK_GLASS_BREAK, 3f, 0.5f);
        for (int ring = 1; ring <= 5; ring++) {
            final int r = ring * 3;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!active()) return;
                for (int i = 0; i < 36; i++) {
                    double a = Math.PI * 2 * i / 36;
                    w.spawnParticle(Particle.SNOWFLAKE, l.getX() + Math.cos(a) * r, l.getY() + 0.4, l.getZ() + Math.sin(a) * r, 2, 0.1, 0.3, 0.1, 0.02);
                }
            }, ring * 2L);
        }
        for (Player p : nearbyPlayers(14)) {
            p.damage(dmg, boss);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
            p.setFreezeTicks(p.getMaxFreezeTicks());
            p.sendActionBar(Xmas.c("&b&lCOLD HEART! &7You are freezing."));
        }
    }

    /** Leaps onto a player and slams down. */
    private void leapSlam(List<Player> targets) {
        Player t = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        Vector to = t.getLocation().toVector().subtract(boss.getLocation().toVector());
        Vector flat = new Vector(to.getX(), 0, to.getZ());
        double dist = flat.length();
        if (dist < 0.01) flat = new Vector(1, 0, 0);
        boss.setVelocity(flat.normalize().multiply(Math.min(1.6, 0.4 + dist * 0.07)).setY(1.1));
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 3f, 1.2f);
        t.sendActionBar(Xmas.c("&c&lTHE GRINCH LEAPS!"));
        double dmg = cfg().getDouble("boss.leap-damage", 8);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active()) return;
            Location l = boss.getLocation();
            World w = l.getWorld();
            w.spawnParticle(Particle.EXPLOSION, l, 4, 1.5, 0.2, 1.5, 0);
            w.spawnParticle(Particle.CLOUD, l, 60, 3, 0.2, 3, 0.15);
            w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.6f);
            for (Player p : nearbyPlayers(6)) {
                p.damage(dmg, boss);
                p.setVelocity(away(p, l, 1.1, 0.7));
            }
        }, 24L);
    }

    /** Pulls players in and heals off them. */
    private void drain() {
        Location l = boss.getLocation().add(0, 2, 0);
        World w = l.getWorld();
        AttributeInstance mx = boss.getAttribute(Attribute.MAX_HEALTH);
        double per = cfg().getDouble("boss.drain-heal-percent", 0.02);
        w.playSound(l, Sound.ENTITY_WARDEN_HEARTBEAT, 3f, 0.6f);
        for (Player p : nearbyPlayers(14)) {
            Vector pull = l.toVector().subtract(p.getLocation().toVector());
            if (pull.lengthSquared() > 0.01) p.setVelocity(pull.normalize().multiply(0.8).setY(0.25));
            p.damage(3, boss);
            Vector line = l.toVector().subtract(p.getEyeLocation().toVector());
            double len = line.length();
            for (double d = 0; d < len; d += 0.6) {
                Vector pt = p.getEyeLocation().toVector().add(line.clone().normalize().multiply(d));
                w.spawnParticle(Particle.DUST, pt.getX(), pt.getY(), pt.getZ(), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(60, 220, 80), 1.2f));
            }
            if (mx != null) boss.setHealth(Math.min(mx.getValue(), boss.getHealth() + mx.getValue() * per));
            p.sendActionBar(Xmas.c("&2&lGRINCH'S GRIP! &7He is draining you."));
        }
    }

    private void stomp() {
        Location l = boss.getLocation();
        World w = l.getWorld();
        w.spawnParticle(Particle.CLOUD, l, 70, 3, 0.2, 3, 0.1);
        w.spawnParticle(Particle.SNOWFLAKE, l, 60, 3, 0.5, 3, 0.05);
        w.playSound(l, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.7f);
        double dmg = cfg().getDouble("boss.stomp-damage", 6);
        for (Player p : nearbyPlayers(7)) {
            p.damage(dmg, boss);
            Vector away = p.getLocation().toVector().subtract(l.toVector()).setY(0);
            if (away.lengthSquared() < 0.0001) away = new Vector(1, 0, 0);
            away.normalize().multiply(1.2).setY(0.7);
            p.setVelocity(away);
            p.sendActionBar(Xmas.c("&c&lSTOMP!"));
        }
    }

    private void snowballs(List<Player> targets) {
        targets.sort(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(boss.getLocation())));
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_SNOW_GOLEM_SHOOT, 2f, 0.6f);
        for (int i = 0; i < Math.min(3, targets.size()); i++) {
            Player t = targets.get(i);
            Vector dir = t.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector());
            if (dir.lengthSquared() < 0.0001) continue;
            boss.launchProjectile(Snowball.class, dir.normalize().multiply(1.6));
            t.sendActionBar(Xmas.c("&b&lSNOWBALLS incoming!"));
        }
    }

    private void elves(List<Player> targets) {
        Location l = boss.getLocation();
        for (int i = 0; i < 3 && minions.size() < cfg().getInt("boss.max-minions", 6); i++) {
            Location spawn = l.clone().add(ThreadLocalRandom.current().nextDouble(-3, 3), 0, ThreadLocalRandom.current().nextDouble(-3, 3));
            Zombie elf = l.getWorld().spawn(spawn, Zombie.class, z -> {
                z.setBaby(true);
                z.customName(Xmas.c("&2Grinch's Elf"));
                z.setCustomNameVisible(true);
                z.setPersistent(false);
                z.setRemoveWhenFarAway(true);
                z.setShouldBurnInDay(false);
                setAttr(z, Attribute.MAX_HEALTH, 20);
                z.setHealth(20);
                z.getEquipment().setHelmet(leather(Material.LEATHER_HELMET, Color.GREEN));
                z.getEquipment().setHelmetDropChance(0f);
            });
            elf.setTarget(targets.get(ThreadLocalRandom.current().nextInt(targets.size())));
            minions.add(elf.getUniqueId());
        }
        l.getWorld().playSound(l, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2f, 1f);
        for (Player p : targets) p.sendActionBar(Xmas.c("&2&lThe Grinch calls his elves!"));
    }

    // ---------- events ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!active() || !e.getEntity().getUniqueId().equals(boss.getUniqueId())) return;
        Player p = null;
        Entity d = e.getDamager();
        if (d instanceof Player direct) p = direct;
        else if (d instanceof Projectile pr && pr.getShooter() instanceof Player shooter) p = shooter;
        if (p != null) damage.merge(p.getUniqueId(), e.getFinalDamage(), Double::sum);
    }

    @EventHandler
    public void onSnowballHit(ProjectileHitEvent e) {
        if (!active() || !(e.getEntity() instanceof Snowball sb)) return;
        if (!(sb.getShooter() instanceof Entity shooter) || !shooter.getUniqueId().equals(boss.getUniqueId())) return;
        if (e.getHitEntity() instanceof Player p) {
            p.damage(cfg().getDouble("boss.snowball-damage", 3), boss);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
        }
    }

    @EventHandler
    public void onCombust(EntityCombustEvent e) {
        UUID id = e.getEntity().getUniqueId();
        if ((boss != null && id.equals(boss.getUniqueId())) || minions.contains(id)) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        if (boss != null && id.equals(boss.getUniqueId())) {
            e.getDrops().clear();
            e.setDroppedExp(cfg().getInt("boss.xp", 500));
            victory(e.getEntity().getLocation());
            finish();
        } else if (minions.remove(id)) {
            e.getDrops().clear();
            e.setDroppedExp(5);
        }
    }

    /** "His heart grew three sizes": hearts, fireworks and a title for everyone nearby. */
    private void victory(Location l) {
        World w = l.getWorld();
        if (w == null) return;
        w.spawnParticle(Particle.HEART, l.clone().add(0, 3, 0), 40, 3, 2, 3, 0);
        w.playSound(l, Sound.UI_TOAST_CHALLENGE_COMPLETE, 4f, 1f);
        for (int i = 0; i < 8; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location at = l.clone().add(ThreadLocalRandom.current().nextDouble(-6, 6), 4 + ThreadLocalRandom.current().nextDouble(6), ThreadLocalRandom.current().nextDouble(-6, 6));
                Festive.burst(at, ThreadLocalRandom.current().nextBoolean() ? org.bukkit.FireworkEffect.Type.BALL_LARGE : org.bukkit.FireworkEffect.Type.STAR);
            }, i * 5L);
        }
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(l) > 100 * 100) continue;
            p.showTitle(Title.title(Xmas.c("&a&lTHE GRINCH IS DEFEATED"), Xmas.c("&fHis heart grew three sizes!"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(3000), Duration.ofMillis(900))));
        }
    }

    private void finish() {
        List<Map.Entry<UUID, Double>> ranked = new ArrayList<>(damage.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        double min = cfg().getDouble("boss.min-damage-to-reward", 20);
        List<String> participation = cfg().getStringList("boss.participation-commands");
        ConfigurationSection top = cfg().getConfigurationSection("boss.top-commands");

        StringBuilder board = new StringBuilder();
        for (int i = 0; i < ranked.size(); i++) {
            Player p = Bukkit.getPlayer(ranked.get(i).getKey());
            if (p == null) continue;
            double dmg = ranked.get(i).getValue();
            if (dmg >= min) plugin.runCommands(p, participation);
            if (i < 3) {
                if (top != null) plugin.runCommands(p, top.getStringList(String.valueOf(i + 1)));
                board.append("&e#").append(i + 1).append(" &f").append(p.getName()).append(" &7(").append((int) dmg).append(") ");
            }
        }
        Bukkit.broadcast(Xmas.c(cfg().getString("prefix", "") + "&a&lThe Grinch &fwas defeated! " + board));
        cleanup();
    }
}
