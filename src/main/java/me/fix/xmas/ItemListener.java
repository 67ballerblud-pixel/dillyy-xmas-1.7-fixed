package me.fix.xmas;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Behaviour for the Christmas items:
 * <ul>
 * <li>Frost Wand - right-click fires an ice bolt that damages and slows</li>
 * <li>Candy Cane Blade - hits make targets sticky, with a chance of a Sugar Rush</li>
 * <li>Candy Cane Pickaxe - Haste while held, and sneak-mining an ore breaks the whole vein (see EnchantListener)</li>
 * <li>Grinch's Heart - right-click: pull in and weaken everything nearby while you heal</li>
 * <li>Santa Hat - Regeneration while worn, and monsters you kill can drop gifts</li>
 * <li>Reindeer Boots - Speed and Jump Boost, no fall damage, sprint + sneak to dash</li>
 * <li>Gingerbread Plate - reflects part of the damage you take</li>
 * <li>Snow Globe - right-click: blizzard that freezes everything nearby</li>
 * <li>Jingle Bells - right-click: burst of speed, jump and haste</li>
 * <li>Holly Bow - arrows make targets glow and slow them</li>
 * <li>Sleigh Launcher - rocket forward in a firework trail</li>
 * <li>North Star - stars fall on every enemy nearby</li>
 * <li>Eggnog Flask - Strength, Regeneration and Absorption</li>
 * </ul>
 */
public class ItemListener implements Listener {

    private static final List<ItemStack> GIFTS = List.of(
            new ItemStack(Material.GOLDEN_CARROT, 8), new ItemStack(Material.COOKED_BEEF, 16),
            new ItemStack(Material.EMERALD, 4), new ItemStack(Material.DIAMOND, 2),
            new ItemStack(Material.EXPERIENCE_BOTTLE, 8), new ItemStack(Material.GOLDEN_APPLE, 1),
            new ItemStack(Material.COOKIE, 16), new ItemStack(Material.SNOWBALL, 16));

    private final Xmas plugin;
    private final NamespacedKey boltKey;
    private final NamespacedKey hollyKey;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<java.util.UUID, Long> noFallUntil = new HashMap<>();
    private final Set<java.util.UUID> summoned = new HashSet<>();
    private final NamespacedKey cannonKey;
    private boolean reflecting;
    private boolean burning;

    ItemListener(Xmas plugin) {
        this.plugin = plugin;
        this.boltKey = new NamespacedKey(plugin, "frost_bolt");
        this.hollyKey = new NamespacedKey(plugin, "holly_arrow");
        this.cannonKey = new NamespacedKey(plugin, "cannon_ball");
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ---------------- helpers ----------------
    /** Per-player cooldown for an item ability. Tells the player how long is left if it isn't ready. */
    private boolean ready(Player p, String id, String cfgKey, int defSeconds, String label) {
        int secs = cfg().getInt("items." + cfgKey + ".cooldown-seconds", defSeconds);
        String k = p.getUniqueId() + ":" + id;
        long now = System.currentTimeMillis();
        Long until = cooldowns.get(k);
        if (until != null && until > now) {
            long left = (until - now + 999) / 1000;
            p.sendActionBar(Xmas.c("&c" + label + " ready in &f" + left + "s"));
            return false;
        }
        cooldowns.put(k, now + secs * 1000L);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String prefix = e.getPlayer().getUniqueId() + ":";
        cooldowns.keySet().removeIf(k -> k.startsWith(prefix));
        noFallUntil.remove(e.getPlayer().getUniqueId());
    }

    private static void heal(Player p, double amount) {
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        if (max == null || amount <= 0) return;
        p.setHealth(Math.min(max.getValue(), p.getHealth() + amount));
    }

    /** Things the area abilities should leave alone: yourself, armor stands, villagers, tamed pets, spectators. */
    private static boolean skip(Player self, Entity en) {
        if (!(en instanceof LivingEntity) || en.equals(self) || en instanceof ArmorStand || en instanceof Villager) return true;
        if (en instanceof Tameable t && t.isTamed()) return true;
        return en instanceof Player other && (other.getGameMode() == GameMode.SPECTATOR || other.getGameMode() == GameMode.CREATIVE);
    }

    private static PotionEffect effect(PotionEffectType type, int ticks, int amp) {
        return new PotionEffect(type, ticks, amp);
    }

    // ---------------- right-click abilities ----------------
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        String id = Items.idOf(it);
        if (id == null) return;
        Player p = e.getPlayer();
        switch (id) {
            case "frost_wand" -> {
                e.setCancelled(true);
                frostWand(p, it);
            }
            case "grinch_heart" -> {
                e.setCancelled(true);
                grinchHeart(p);
            }
            case "snow_globe" -> {
                e.setCancelled(true);
                snowGlobe(p);
            }
            case "jingle_bells" -> {
                e.setCancelled(true);
                jingleBells(p);
            }
            case "sleigh_launcher" -> {
                e.setCancelled(true);
                sleighLauncher(p);
            }
            case "north_star" -> {
                e.setCancelled(true);
                northStar(p);
            }
            case "eggnog_flask" -> {
                e.setCancelled(true);
                eggnog(p);
            }
            case "snowball_cannon" -> {
                e.setCancelled(true);
                snowballCannon(p);
            }
            case "frosty_coal" -> {
                e.setCancelled(true);
                frostyCoal(p);
            }
            case "mistletoe_kiss" -> e.setCancelled(true);
            default -> {
            }
        }
    }

    private void frostWand(Player p, ItemStack it) {
        Material mat = it.getType();
        if (p.hasCooldown(mat)) return;
        p.setCooldown(mat, cfg().getInt("items.frost-wand.cooldown-ticks", 60));
        Snowball sb = p.launchProjectile(Snowball.class, p.getEyeLocation().getDirection().multiply(1.8));
        sb.getPersistentDataContainer().set(boltKey, PersistentDataType.BYTE, (byte) 1);
        new org.bukkit.scheduler.BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!sb.isValid() || ++t > 60) {
                    cancel();
                    return;
                }
                sb.getWorld().spawnParticle(Particle.SNOWFLAKE, sb.getLocation(), 4, 0.1, 0.1, 0.1, 0.01);
                sb.getWorld().spawnParticle(Particle.END_ROD, sb.getLocation(), 1, 0, 0, 0, 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOW_GOLEM_SHOOT, 1f, 1.4f);
    }

    /** Grinch's Heart: drag everything nearby to you, weaken it, and heal yourself. */
    private void grinchHeart(Player p) {
        if (!ready(p, "grinch_heart", "grinch-heart", 45, "Grinch's Grip")) return;
        double radius = cfg().getDouble("items.grinch-heart.radius", 7);
        double dmg = cfg().getDouble("items.grinch-heart.damage", 6);
        int weak = cfg().getInt("items.grinch-heart.weakness-seconds", 6) * 20;
        Location c = p.getLocation();
        for (Entity en : p.getNearbyEntities(radius, radius, radius)) {
            if (skip(p, en)) continue;
            LivingEntity t = (LivingEntity) en;
            Vector pull = c.toVector().subtract(t.getLocation().toVector());
            if (pull.lengthSquared() > 0.01) t.setVelocity(pull.normalize().multiply(0.9).setY(0.35));
            t.damage(dmg, p);
            t.addPotionEffect(effect(PotionEffectType.WEAKNESS, weak, 1));
            t.addPotionEffect(effect(PotionEffectType.NAUSEA, 100, 0));
        }
        heal(p, cfg().getDouble("items.grinch-heart.heal", 6));
        p.addPotionEffect(effect(PotionEffectType.ABSORPTION, 160, 1));
        Location mid = c.clone().add(0, 1, 0);
        p.getWorld().spawnParticle(Particle.DUST, mid, 70, 2, 1, 2, 0, new Particle.DustOptions(Color.fromRGB(60, 200, 60), 1.6f));
        p.getWorld().spawnParticle(Particle.HEART, mid, 10, 1, 0.8, 1, 0);
        p.getWorld().playSound(c, Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.7f);
        p.getWorld().playSound(c, Sound.ENTITY_RAVAGER_ROAR, 1f, 1.2f);
        p.sendActionBar(Xmas.c("&2&lYour heart grew three sizes!"));
    }

    /** Snow Globe: a blizzard that freezes and slows everything around you. */
    private void snowGlobe(Player p) {
        if (!ready(p, "snow_globe", "snow-globe", 40, "Blizzard")) return;
        double radius = cfg().getDouble("items.snow-globe.radius", 7);
        double dmg = cfg().getDouble("items.snow-globe.damage", 2);
        int slow = cfg().getInt("items.snow-globe.slow-seconds", 4) * 20;
        for (Entity en : p.getNearbyEntities(radius, radius, radius)) {
            if (skip(p, en)) continue;
            LivingEntity t = (LivingEntity) en;
            t.damage(dmg, p);
            t.addPotionEffect(effect(PotionEffectType.SLOWNESS, slow, 2));
            t.setFreezeTicks(t.getMaxFreezeTicks());
        }
        p.addPotionEffect(effect(PotionEffectType.RESISTANCE, 100, 0));
        Location c = p.getLocation();
        for (int ring = 1; ring <= 3; ring++) {
            double r = radius * ring / 3.0;
            for (int i = 0; i < 40; i++) {
                double a = Math.PI * 2 * i / 40;
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, c.getX() + Math.cos(a) * r, c.getY() + 0.5,
                        c.getZ() + Math.sin(a) * r, 1, 0, 0.4, 0, 0.02);
            }
        }
        p.getWorld().playSound(c, Sound.ENTITY_PLAYER_HURT_FREEZE, 2f, 0.8f);
        p.getWorld().playSound(c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2f, 0.6f);
        p.sendActionBar(Xmas.c("&b&lBlizzard!"));
    }

    /** Jingle Bells: a burst of speed, jump boost and haste for you. */
    private void jingleBells(Player p) {
        if (!ready(p, "jingle_bells", "jingle-bells", 60, "Jingle Bells")) return;
        int ticks = cfg().getInt("items.jingle-bells.seconds", 8) * 20;
        p.addPotionEffect(effect(PotionEffectType.SPEED, ticks, 2));
        p.addPotionEffect(effect(PotionEffectType.JUMP_BOOST, ticks, 1));
        p.addPotionEffect(effect(PotionEffectType.HASTE, ticks, 1));
        p.getWorld().spawnParticle(Particle.NOTE, p.getLocation().add(0, 1.5, 0), 14, 0.6, 0.5, 0.6, 1);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 2f, 1.2f);
        p.sendActionBar(Xmas.c("&6&lJingle all the way!"));
    }

    /** Sleigh Launcher: rocket forward in a trail of fireworks, no fall damage while airborne. */
    private void sleighLauncher(Player p) {
        if (!ready(p, "sleigh_launcher", "sleigh-launcher", 20, "Sleigh Blast")) return;
        double power = cfg().getDouble("items.sleigh-launcher.power", 2.2);
        p.setVelocity(p.getEyeLocation().getDirection().normalize().multiply(power).add(new Vector(0, 0.35, 0)));
        noFallUntil.put(p.getUniqueId(), System.currentTimeMillis() + 12_000L);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2f, 0.8f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 2f, 1.5f);
        p.sendActionBar(Xmas.c("&c&lSLEIGH BLAST! &6Hold on tight!"));
        new org.bukkit.scheduler.BukkitRunnable() {
            int t;

            @Override
            public void run() {
                if (!p.isOnline() || ++t > 30) {
                    cancel();
                    return;
                }
                Location l = p.getLocation().add(0, 0.5, 0);
                p.getWorld().spawnParticle(Particle.FIREWORK, l, 6, 0.2, 0.2, 0.2, 0.05);
                p.getWorld().spawnParticle(Particle.DUST, l, 6, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(t % 2 == 0 ? Color.RED : Color.fromRGB(30, 190, 70), 1.5f));
                if (t % 6 == 0) p.getWorld().playSound(l, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.0f + (t % 12) / 24f);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** North Star: golden stars crash down on every enemy nearby, one after another. */
    private void northStar(Player p) {
        if (!ready(p, "north_star", "north-star", 50, "Starfall")) return;
        double radius = cfg().getDouble("items.north-star.radius", 10);
        double dmg = cfg().getDouble("items.north-star.damage", 8);
        int max = cfg().getInt("items.north-star.max-targets", 6);
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity en : p.getNearbyEntities(radius, radius, radius)) {
            if (skip(p, en)) continue;
            targets.add((LivingEntity) en);
            if (targets.size() >= max) break;
        }
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 1.8f);
        p.sendActionBar(Xmas.c(targets.isEmpty() ? "&e&lStarfall &7(nothing to hit... pretty though)" : "&e&lSTARFALL!"));
        if (targets.isEmpty()) {
            for (int i = 0; i < 5; i++) {
                double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                double r = 2 + ThreadLocalRandom.current().nextDouble(radius - 2);
                starDrop(p.getLocation().add(Math.cos(a) * r, 0, Math.sin(a) * r), null, p, 0, i * 4L);
            }
            return;
        }
        for (int i = 0; i < targets.size(); i++) starDrop(targets.get(i).getLocation(), targets.get(i), p, dmg, i * 5L);
    }

    private void starDrop(Location at, LivingEntity target, Player caster, double dmg, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location ground = target != null && target.isValid() ? target.getLocation() : at;
            for (double y = 9; y >= 0; y -= 0.5) {
                ground.getWorld().spawnParticle(Particle.END_ROD, ground.clone().add(0, y, 0), 1, 0.05, 0.05, 0.05, 0);
            }
            ground.getWorld().spawnParticle(Particle.FIREWORK, ground.clone().add(0, 0.5, 0), 40, 0.6, 0.6, 0.6, 0.12);
            ground.getWorld().spawnParticle(Particle.DUST, ground.clone().add(0, 0.5, 0), 25, 0.7, 0.5, 0.7, 0, new Particle.DustOptions(Color.fromRGB(255, 200, 40), 1.6f));
            ground.getWorld().playSound(ground, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.5f, 1.1f);
            ground.getWorld().playSound(ground, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2f, 1.6f);
            if (target != null && target.isValid() && !target.isDead()) {
                target.damage(dmg, caster);
                target.addPotionEffect(effect(PotionEffectType.GLOWING, 100, 0));
            }
        }, delay);
    }

    /** Eggnog Flask: a short burst of holiday cheer. */
    private void eggnog(Player p) {
        if (!ready(p, "eggnog_flask", "eggnog-flask", 90, "Holiday Cheer")) return;
        int secs = cfg().getInt("items.eggnog-flask.seconds", 20) * 20;
        p.addPotionEffect(effect(PotionEffectType.STRENGTH, secs, 0));
        p.addPotionEffect(effect(PotionEffectType.REGENERATION, 160, 1));
        p.addPotionEffect(effect(PotionEffectType.ABSORPTION, secs, 1));
        p.setFoodLevel(Math.min(20, p.getFoodLevel() + 6));
        Location l = p.getLocation().add(0, 1, 0);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, l, 20, 0.5, 0.7, 0.5, 0);
        p.getWorld().spawnParticle(Particle.DUST, l, 30, 0.6, 0.8, 0.6, 0, new Particle.DustOptions(Color.fromRGB(255, 235, 190), 1.4f));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
        p.sendActionBar(Xmas.c("&6&lHo ho ho! &fFeeling merry."));
    }

    /** Mistletoe Kiss: right-click another player to heal you both. */
    @EventHandler
    public void onMistletoe(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Player other)) return;
        Player p = e.getPlayer();
        if (!"mistletoe_kiss".equals(Items.idOf(p.getInventory().getItemInMainHand()))) return;
        e.setCancelled(true);
        if (!ready(p, "mistletoe_kiss", "mistletoe-kiss", 30, "Season of Love")) return;
        double amount = cfg().getDouble("items.mistletoe-kiss.heal", 8);
        for (Player t : new Player[]{p, other}) {
            heal(t, amount);
            t.addPotionEffect(effect(PotionEffectType.REGENERATION, 100, 1));
            t.getWorld().spawnParticle(Particle.HEART, t.getLocation().add(0, 2, 0), 8, 0.4, 0.3, 0.4, 0);
            t.getWorld().spawnParticle(Particle.DUST, t.getLocation().add(0, 1, 0), 15, 0.5, 0.7, 0.5, 0, new Particle.DustOptions(Color.fromRGB(40, 190, 70), 1.3f));
            t.playSound(t.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.7f);
        }
        p.sendActionBar(Xmas.c("&a&lMerry Christmas, &f" + other.getName() + "&a&l!"));
        other.sendActionBar(Xmas.c("&a&l" + p.getName() + " &fshared some holiday love!"));
    }

    /** Snowball Cannon: a rapid burst of snowballs that hit harder than they should. */
    private void snowballCannon(Player p) {
        if (!ready(p, "snowball_cannon", "snowball-cannon", 8, "Powder Burst")) return;
        int shots = Math.max(1, cfg().getInt("items.snowball-cannon.shots", 3));
        for (int i = 0; i < shots; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                Vector dir = p.getEyeLocation().getDirection().normalize().multiply(2.0)
                        .add(new Vector(ThreadLocalRandom.current().nextDouble(-0.06, 0.06), ThreadLocalRandom.current().nextDouble(-0.04, 0.04), ThreadLocalRandom.current().nextDouble(-0.06, 0.06)));
                Snowball sb = p.launchProjectile(Snowball.class, dir);
                sb.getPersistentDataContainer().set(cannonKey, PersistentDataType.BYTE, (byte) 1);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOW_GOLEM_SHOOT, 1f, 0.8f + ThreadLocalRandom.current().nextFloat() * 0.5f);
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getEyeLocation().add(p.getLocation().getDirection()), 8, 0.1, 0.1, 0.1, 0.1);
            }, i * 3L);
        }
        p.sendActionBar(Xmas.c("&f&lPOWDER BURST!"));
    }

    /** Frosty's Magic Coal: summon snow golems that fight for you, then melt. */
    private void frostyCoal(Player p) {
        if (!ready(p, "frosty_coal", "frosty-coal", 60, "Frosty Friends")) return;
        int count = Math.max(1, cfg().getInt("items.frosty-coal.count", 3));
        int secs = cfg().getInt("items.frosty-coal.seconds", 25);
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            Location l = p.getLocation().add(Math.cos(a) * 2, 0, Math.sin(a) * 2);
            Snowman sn = p.getWorld().spawn(l, Snowman.class);
            sn.customName(Xmas.c("&f&l" + p.getName() + "'s Frosty"));
            sn.setCustomNameVisible(true);
            sn.setDerp(false);
            summoned.add(sn.getUniqueId());
            p.getWorld().spawnParticle(Particle.SNOWFLAKE, l.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (sn.isValid()) {
                    sn.getWorld().spawnParticle(Particle.CLOUD, sn.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.03);
                    sn.getWorld().playSound(sn.getLocation(), Sound.BLOCK_SNOW_BREAK, 1f, 0.8f);
                    sn.remove();
                }
                summoned.remove(sn.getUniqueId());
            }, secs * 20L);
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOW_GOLEM_AMBIENT, 2f, 0.7f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2f, 1.4f);
        p.sendActionBar(Xmas.c("&f&lFrosty and friends have arrived!"));
    }

    @EventHandler
    public void onSummonedDeath(EntityDeathEvent e) {
        if (summoned.remove(e.getEntity().getUniqueId())) e.getDrops().clear();
    }

    // ---------------- bows ----------------
    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (!"holly_bow".equals(Items.idOf(e.getBow()))) return;
        if (e.getProjectile() instanceof AbstractArrow arrow) {
            arrow.getPersistentDataContainer().set(hollyKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (pr.getPersistentDataContainer().has(hollyKey, PersistentDataType.BYTE)) {
            pr.getWorld().spawnParticle(Particle.DUST, pr.getLocation(), 25, 0.3, 0.3, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(30, 160, 60), 1.4f));
            if (e.getHitEntity() instanceof LivingEntity target) {
                target.addPotionEffect(effect(PotionEffectType.GLOWING, cfg().getInt("items.holly-bow.glow-seconds", 8) * 20, 0));
                target.addPotionEffect(effect(PotionEffectType.SLOWNESS, cfg().getInt("items.holly-bow.slow-seconds", 3) * 20, 0));
            }
            return;
        }
        if (pr.getPersistentDataContainer().has(cannonKey, PersistentDataType.BYTE)) {
            pr.getWorld().spawnParticle(Particle.SNOWFLAKE, pr.getLocation(), 15, 0.2, 0.2, 0.2, 0.05);
            if (e.getHitEntity() instanceof LivingEntity t && pr.getShooter() instanceof Player shooter && !t.equals(shooter)) {
                t.damage(cfg().getDouble("items.snowball-cannon.damage", 3), shooter);
                t.setVelocity(t.getVelocity().add(pr.getVelocity().normalize().multiply(0.4).setY(0.15)));
            }
            return;
        }
        if (!pr.getPersistentDataContainer().has(boltKey, PersistentDataType.BYTE)) return;
        pr.getWorld().spawnParticle(Particle.SNOWFLAKE, pr.getLocation(), 25, 0.3, 0.3, 0.3, 0.05);
        if (!(e.getHitEntity() instanceof LivingEntity target)) return;
        if (!(pr.getShooter() instanceof Player shooter) || target.equals(shooter)) return;
        double dmg = cfg().getDouble("items.frost-wand.damage", 3);
        int slow = cfg().getInt("items.frost-wand.slow-seconds", 3) * 20;
        target.damage(dmg, shooter);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slow, 1));
    }

    // ---------------- combat: Candy Cane Blade and Gingerbread Plate ----------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        // Yule Log Axe: sometimes the hit erupts in flame and ignites everything nearby
        if (!burning && e.getDamager() instanceof Player yp && e.getEntity() instanceof LivingEntity yt
                && "yule_log_axe".equals(Items.idOf(yp.getInventory().getItemInMainHand()))
                && ThreadLocalRandom.current().nextDouble() < cfg().getDouble("items.yule-log-axe.chance", 0.25)) {
            double r = cfg().getDouble("items.yule-log-axe.radius", 3);
            double dmg = cfg().getDouble("items.yule-log-axe.damage", 4);
            int burn = cfg().getInt("items.yule-log-axe.burn-seconds", 4) * 20;
            burning = true;
            try {
                for (Entity en : yt.getNearbyEntities(r, r, r)) {
                    if (skip(yp, en)) continue;
                    LivingEntity n = (LivingEntity) en;
                    n.setFireTicks(burn);
                    n.damage(dmg, yp);
                }
            } finally {
                burning = false;
            }
            yt.setFireTicks(burn);
            Location at = yt.getLocation().add(0, 1, 0);
            yt.getWorld().spawnParticle(Particle.FLAME, at, 40, 0.8, 0.6, 0.8, 0.08);
            yt.getWorld().spawnParticle(Particle.LAVA, at, 8, 0.6, 0.4, 0.6, 0);
            yt.getWorld().playSound(at, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.7f);
            yp.sendActionBar(Xmas.c("&6&lYULE FIRE!"));
        }

        // Candy Cane Blade: sticky hits, sometimes a Sugar Rush
        if (e.getDamager() instanceof Player p && e.getEntity() instanceof LivingEntity t
                && "candy_cane_sword".equals(Items.idOf(p.getInventory().getItemInMainHand()))) {
            t.addPotionEffect(effect(PotionEffectType.SLOWNESS, cfg().getInt("items.candy-cane-sword.sticky-seconds", 2) * 20, 0));
            Location at = t.getLocation().add(0, 1, 0);
            t.getWorld().spawnParticle(Particle.DUST, at, 8, 0.3, 0.4, 0.3, 0, new Particle.DustOptions(Color.RED, 1.2f));
            t.getWorld().spawnParticle(Particle.DUST, at, 8, 0.3, 0.4, 0.3, 0, new Particle.DustOptions(Color.WHITE, 1.2f));
            if (ThreadLocalRandom.current().nextDouble() < cfg().getDouble("items.candy-cane-sword.sugar-rush-chance", 0.2)) {
                p.addPotionEffect(effect(PotionEffectType.SPEED, 80, 1));
                heal(p, cfg().getDouble("items.candy-cane-sword.sugar-rush-heal", 2));
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.8f);
                p.sendActionBar(Xmas.c("&c&lSUGAR RUSH!"));
            }
        }

        // Gingerbread Plate: reflect part of the damage back at the attacker
        if (reflecting || !(e.getEntity() instanceof Player victim)) return;
        if (!"gingerbread_chestplate".equals(Items.idOf(victim.getInventory().getChestplate()))) return;
        Entity src = e.getDamager();
        if (src instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) src = shooter;
        if (!(src instanceof LivingEntity attacker) || attacker.equals(victim)) return;
        if (ThreadLocalRandom.current().nextDouble() >= cfg().getDouble("items.gingerbread-chestplate.reflect-chance", 0.25)) return;
        double back = e.getFinalDamage() * cfg().getDouble("items.gingerbread-chestplate.reflect-percent", 0.3);
        if (back <= 0) return;
        reflecting = true;
        try {
            attacker.damage(back, victim);
        } finally {
            reflecting = false;
        }
        attacker.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.2);
        victim.sendActionBar(Xmas.c("&6&lCrunch! &7Reflected some damage."));
    }

    // ---------------- Reindeer Boots: no fall damage, sprint + sneak to dash ----------------
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        if ("reindeer_boots".equals(Items.idOf(p.getInventory().getBoots()))) e.setCancelled(true);
        Long until = noFallUntil.get(p.getUniqueId());
        if (until != null) {
            if (until > System.currentTimeMillis()) e.setCancelled(true);
            noFallUntil.remove(p.getUniqueId());
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!e.isSneaking() || !p.isSprinting() || p.isGliding() || !p.isOnGround()) return;
        if (!"reindeer_boots".equals(Items.idOf(p.getInventory().getBoots()))) return;
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) return;
        if (!ready(p, "reindeer_boots", "reindeer-boots", 6, "Sleigh Dash")) return;
        p.setVelocity(dir.normalize().multiply(cfg().getDouble("items.reindeer-boots.dash-power", 1.6)).setY(0.3));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 20, 0.4, 0.1, 0.4, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_HORSE_JUMP, 1f, 1.3f);
    }

    // ---------------- Santa Hat: gifts from monsters ----------------
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Monster)) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null || !"santa_hat".equals(Items.idOf(killer.getInventory().getHelmet()))) return;
        if (ThreadLocalRandom.current().nextDouble() >= cfg().getDouble("items.santa-hat.gift-chance", 0.05)) return;
        e.getDrops().add(GIFTS.get(ThreadLocalRandom.current().nextInt(GIFTS.size())).clone());
        killer.sendActionBar(Xmas.c("&c&lA gift fell out of Santa's sack!"));
        killer.getWorld().spawnParticle(Particle.FIREWORK, e.getEntity().getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.05);
    }

    // ---------------- passive effects, once a second ----------------
    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = p.getInventory();
            if ("santa_hat".equals(Items.idOf(inv.getHelmet()))) {
                int amp = cfg().getInt("items.santa-hat.regen-amplifier", 0);
                if (amp >= 0) p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, amp, true, false));
            }
            if ("reindeer_boots".equals(Items.idOf(inv.getBoots()))) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, cfg().getInt("items.reindeer-boots.speed-amplifier", 0), true, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, cfg().getInt("items.reindeer-boots.jump-amplifier", 0), true, false));
            }
            if ("ice_skates".equals(Items.idOf(inv.getBoots()))) {
                Material below = p.getLocation().subtract(0, 0.2, 0).getBlock().getType();
                if (below == Material.ICE || below == Material.PACKED_ICE || below == Material.BLUE_ICE || below == Material.FROSTED_ICE) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, cfg().getInt("items.ice-skates.speed-amplifier", 2), true, false));
                }
            }
            if (Items.santaSet(inv)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, true, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0, true, false));
            }
            if ("candy_cane_pickaxe".equals(Items.idOf(inv.getItemInMainHand()))) {
                int amp = cfg().getInt("items.candy-cane-pickaxe.haste-amplifier", 1);
                if (amp >= 0) p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, amp, true, false));
            }
        }
    }
}
