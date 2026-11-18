package me.fix.xmas;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Makes the custom enchantments (see CEnchant) actually do something. */
public class EnchantListener implements Listener {

    private final Xmas plugin;
    /** Players currently breaking a vein, so our own extra BlockBreakEvents don't start another vein. */
    private final Set<UUID> busy = new HashSet<>();

    EnchantListener(Xmas plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ---------------- passive enchants (boots / helmet) ----------------
    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            int sw = CEnchant.level(p.getInventory().getBoots(), CEnchant.SWIFTNESS);
            if (sw > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, sw - 1, true, false));
            int cl = CEnchant.level(p.getInventory().getBoots(), CEnchant.CANDY_LEAP);
            if (cl > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, cl - 1, true, false));
            if (hearth(p)) {
                if (p.getFreezeTicks() > 0) p.setFreezeTicks(0);
                AttributeInstance mx = p.getAttribute(Attribute.MAX_HEALTH);
                if (mx != null && p.getHealth() < mx.getValue() * cfg().getDouble("enchants.hearth.low-health", 0.4)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false));
                    p.getWorld().spawnParticle(Particle.SMALL_FLAME, p.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.01);
                }
            }
            if (CEnchant.level(p.getInventory().getHelmet(), CEnchant.NIGHT_OWL) > 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false));
            }
        }
    }

    private static boolean hearth(Player p) {
        return CEnchant.level(p.getInventory().getChestplate(), CEnchant.HEARTH) > 0
                || CEnchant.level(p.getInventory().getLeggings(), CEnchant.HEARTH) > 0;
    }

    /** Things the area enchants should leave alone: yourself, armor stands, villagers, tamed pets, spectators and creative players. */
    private static boolean foe(Player self, Entity en) {
        if (!(en instanceof LivingEntity) || en.equals(self) || en instanceof ArmorStand || en instanceof Villager) return false;
        if (en instanceof Tameable t && t.isTamed()) return false;
        return !(en instanceof Player o && (o.getGameMode() == GameMode.SPECTATOR || o.getGameMode() == GameMode.CREATIVE));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFreeze(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FREEZE && e.getEntity() instanceof Player p && hearth(p)) e.setCancelled(true);
    }

    /** Frostguard: attackers get frozen and slowed. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDefend(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        int lvl = Math.max(CEnchant.level(victim.getInventory().getChestplate(), CEnchant.FROSTGUARD),
                CEnchant.level(victim.getInventory().getLeggings(), CEnchant.FROSTGUARD));
        if (lvl <= 0) return;
        Entity src = e.getDamager();
        if (src instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) src = shooter;
        if (!(src instanceof LivingEntity attacker) || attacker.equals(victim)) return;
        if (ThreadLocalRandom.current().nextDouble() >= cfg().getDouble("enchants.frostguard.chance-per-level", 0.15) * lvl) return;
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + 20 * lvl, 1));
        attacker.setFreezeTicks(attacker.getMaxFreezeTicks());
        attacker.getWorld().spawnParticle(Particle.SNOWFLAKE, attacker.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.03);
        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.6f);
        victim.sendActionBar(net.kyori.adventure.text.Component.text("Frostguard!", net.kyori.adventure.text.format.NamedTextColor.AQUA));
    }

    /** Holiday Bounty: sometimes mobs drop double. */
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        int lvl = CEnchant.level(killer.getInventory().getItemInMainHand(), CEnchant.HOLIDAY_BOUNTY);
        if (lvl <= 0 || e.getDrops().isEmpty()) return;
        if (ThreadLocalRandom.current().nextDouble() >= cfg().getDouble("enchants.holiday-bounty.chance-per-level", 0.1) * lvl) return;
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack d : e.getDrops()) extra.add(d.clone());
        e.getDrops().addAll(extra);
        e.getEntity().getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, e.getEntity().getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.2);
        killer.sendActionBar(net.kyori.adventure.text.Component.text("Holiday Bounty! Double loot!", net.kyori.adventure.text.format.NamedTextColor.GOLD));
    }

    /** Gift of Wisdom: more XP from orbs. */
    @EventHandler
    public void onXp(PlayerExpChangeEvent e) {
        int lvl = CEnchant.level(e.getPlayer().getInventory().getHelmet(), CEnchant.GIFT_OF_WISDOM);
        if (lvl <= 0 || e.getAmount() <= 0) return;
        e.setAmount((int) Math.ceil(e.getAmount() * (1 + cfg().getDouble("enchants.gift-of-wisdom.bonus-per-level", 0.25) * lvl)));
    }

    // ---------------- anvil: apply a custom enchant book ----------------
    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        AnvilInventory inv = e.getInventory();
        ItemStack first = inv.getItem(0);
        Map.Entry<CEnchant, Integer> book = CEnchant.bookContents(inv.getItem(1));
        if (first == null || book == null) return;
        CEnchant en = book.getKey();
        int lvl = book.getValue();
        if (!en.canApplyTo(first.getType())) {
            e.setResult(null);
            return;
        }
        int have = CEnchant.level(first, en);
        int next;
        if (have > lvl) {
            e.setResult(null);
            return;
        } else if (have == lvl) {
            if (lvl >= en.max) {
                e.setResult(null);
                return;
            }
            next = lvl + 1;
        } else {
            next = lvl;
        }
        e.setResult(CEnchant.apply(first, en, next));
        e.getView().setRepairCost(5 + 5 * next);
    }

    // ---------------- weapons ----------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity t)) return;
        ItemStack w = p.getInventory().getItemInMainHand();
        if (!w.hasItemMeta()) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        int ex = CEnchant.level(w, CEnchant.EXECUTIONER);
        if (ex > 0) {
            AttributeInstance max = t.getAttribute(Attribute.MAX_HEALTH);
            if (max != null && t.getHealth() <= max.getValue() * 0.3) {
                e.setDamage(e.getDamage() * (1 + cfg().getDouble("enchants.executioner.bonus-per-level", 0.15) * ex));
                t.getWorld().spawnParticle(Particle.CRIT, t.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.2);
            }
        }

        int fb = CEnchant.level(w, CEnchant.FROSTBITE);
        if (fb > 0 && rnd.nextDouble() < cfg().getDouble("enchants.frostbite.chance-per-level", 0.2) * fb) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + 20 * fb, 1));
            t.setFreezeTicks(t.getMaxFreezeTicks());
            t.getWorld().spawnParticle(Particle.SNOWFLAKE, t.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
        }

        int ls = CEnchant.level(w, CEnchant.LIFESTEAL);
        if (ls > 0) {
            double heal = Math.min(6.0, e.getFinalDamage() * cfg().getDouble("enchants.lifesteal.heal-per-level", 0.05) * ls);
            AttributeInstance mine = p.getAttribute(Attribute.MAX_HEALTH);
            if (heal > 0 && mine != null) {
                p.setHealth(Math.min(mine.getValue(), p.getHealth() + heal));
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 1, 0.2, 0.1, 0.2, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHitExtra(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity t)) return;
        ItemStack w = p.getInventory().getItemInMainHand();
        if (!w.hasItemMeta()) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location at = t.getLocation().add(0, 1, 0);

        int ss = CEnchant.level(w, CEnchant.SNOWSTORM);
        if (ss > 0 && rnd.nextDouble() < cfg().getDouble("enchants.snowstorm.chance-per-level", 0.15) * ss) {
            double r = cfg().getDouble("enchants.snowstorm.radius", 3);
            for (Entity en : t.getNearbyEntities(r, r, r)) {
                if (!foe(p, en)) continue;
                LivingEntity n = (LivingEntity) en;
                n.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60 + 20 * ss, 1));
                n.setFreezeTicks(n.getMaxFreezeTicks());
            }
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60 + 20 * ss, 1));
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24;
                t.getWorld().spawnParticle(Particle.SNOWFLAKE, at.getX() + Math.cos(a) * r, at.getY(), at.getZ() + Math.sin(a) * r, 2, 0.1, 0.5, 0.1, 0.02);
            }
            t.getWorld().spawnParticle(Particle.SNOWFLAKE, at, 40, 0.8, 0.8, 0.8, 0.05);
            t.getWorld().playSound(at, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.5f, 1f);
        }

        int gc = CEnchant.level(w, CEnchant.GRINCH_CURSE);
        if (gc > 0 && rnd.nextDouble() < cfg().getDouble("enchants.grinch-curse.chance-per-level", 0.2) * gc) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80 + 20 * gc, 0));
            t.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0));
            t.getWorld().spawnParticle(Particle.DUST, at, 25, 0.4, 0.6, 0.4, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(40, 190, 70), 1.5f));
            t.getWorld().playSound(at, Sound.ENTITY_WITCH_CELEBRATE, 1f, 0.7f);
        }

        int js = CEnchant.level(w, CEnchant.JINGLE_STUN);
        if (js > 0 && rnd.nextDouble() < cfg().getDouble("enchants.jingle-stun.chance-per-level", 0.08) * js) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 + 10 * js, 9));
            t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 + 10 * js, 1));
            t.getWorld().spawnParticle(Particle.NOTE, t.getLocation().add(0, 2.2, 0), 8, 0.4, 0.2, 0.4, 1);
            t.getWorld().playSound(at, Sound.BLOCK_BELL_USE, 1.5f, 1.3f);
            p.sendActionBar(net.kyori.adventure.text.Component.text("Jingle Stun!", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        }
    }

    // ---------------- mining: Timber (fell a whole tree) ----------------
    private List<Block> collectLogs(Block start, int max) {
        List<Block> out = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && out.size() < max) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (out.size() >= max) break;
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block n = b.getRelative(dx, dy, dz);
                        if (!seen.add(n) || !Tag.LOGS.isTagged(n.getType())) continue;
                        out.add(n);
                        queue.add(n);
                    }
                }
            }
        }
        return out;
    }

    private void timber(Player p, Block start, ItemStack tool) {
        List<Block> logs = collectLogs(start, cfg().getInt("enchants.timber.max-logs", 48));
        if (logs.isEmpty()) return;
        busy.add(p.getUniqueId());
        try {
            for (Block b : logs) {
                if (wornOut(tool)) break;
                BlockBreakEvent ev = new BlockBreakEvent(b, p);
                Bukkit.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) continue;
                breakExtra(p, b, tool, false, ev);
                wear(tool);
            }
            p.getInventory().setItemInMainHand(tool);
        } finally {
            busy.remove(p.getUniqueId());
        }
        p.getWorld().playSound(start.getLocation(), Sound.BLOCK_WOOD_BREAK, 2f, 0.6f);
    }

    // ---------------- mining: Vein Miner ----------------
    private int veinMax(ItemStack tool) {
        int lvl = CEnchant.level(tool, CEnchant.VEIN_MINER);
        int max = lvl > 0 ? cfg().getInt("enchants.vein-miner.per-level", 6) * lvl : 0;
        if ("candy_cane_pickaxe".equals(Items.idOf(tool))) {
            max = Math.max(max, cfg().getInt("items.candy-cane-pickaxe.vein-max", 12));
        }
        return max;
    }

    private static boolean isOre(Material m) {
        return m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
    }

    private static String family(Material m) {
        return m.name().replace("DEEPSLATE_", "");
    }

    private List<Block> collect(Block start, int max) {
        String fam = family(start.getType());
        List<Block> out = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && out.size() < max) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (out.size() >= max) break;
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block n = b.getRelative(dx, dy, dz);
                        if (!seen.add(n)) continue;
                        if (!family(n.getType()).equals(fam)) continue;
                        out.add(n);
                        queue.add(n);
                    }
                }
            }
        }
        return out;
    }

    private static boolean wornOut(ItemStack tool) {
        if (!(tool.getItemMeta() instanceof Damageable d)) return false;
        int maxDur = tool.getType().getMaxDurability();
        return maxDur > 0 && d.getDamage() >= maxDur - 1;
    }

    private static void wear(ItemStack tool) {
        if (!(tool.getItemMeta() instanceof Damageable d) || d.isUnbreakable()) return;
        int unb = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unb > 0 && ThreadLocalRandom.current().nextInt(unb + 1) != 0) return;
        d.setDamage(d.getDamage() + 1);
        tool.setItemMeta(d);
    }

    static Material smelted(Material m) {
        return switch (m) {
            case RAW_IRON, IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case RAW_GOLD, GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.GOLD_INGOT;
            case RAW_COPPER, COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            default -> null;
        };
    }

    /** Break an extra vein block the way a player would: protection plugins already approved it via the event. */
    private void breakExtra(Player p, Block b, ItemStack tool, boolean smelt, BlockBreakEvent ev) {
        World w = b.getWorld();
        Location at = b.getLocation().add(0.5, 0.5, 0.5);
        int xp = ev.getExpToDrop();
        List<ItemStack> drops = ev.isDropItems() ? new ArrayList<>(b.getDrops(tool, p)) : new ArrayList<>();
        b.setType(Material.AIR);
        for (ItemStack drop : drops) {
            ItemStack out = drop.clone();
            if (smelt) {
                Material r = smelted(out.getType());
                if (r != null) out.setType(r);
            }
            w.dropItemNaturally(at, out);
        }
        if (xp > 0) w.spawn(at, ExperienceOrb.class, o -> o.setExperience(xp));
        w.spawnParticle(Particle.CRIT, at, 6, 0.3, 0.3, 0.3, 0.05);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (busy.contains(p.getUniqueId())) return;
        if (!p.isSneaking() || p.getGameMode() == GameMode.CREATIVE) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (Tag.LOGS.isTagged(e.getBlock().getType()) && CEnchant.level(tool, CEnchant.TIMBER) > 0) {
            timber(p, e.getBlock(), tool);
            return;
        }
        int max = veinMax(tool);
        if (max <= 0) return;
        Block start = e.getBlock();
        if (!isOre(start.getType())) return;
        List<Block> vein = collect(start, max);
        if (vein.isEmpty()) return;
        boolean smelt = CEnchant.level(tool, CEnchant.AUTO_SMELT) > 0;

        busy.add(p.getUniqueId());
        try {
            for (Block b : vein) {
                if (wornOut(tool)) break;
                BlockBreakEvent ev = new BlockBreakEvent(b, p);
                Bukkit.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) continue;
                breakExtra(p, b, tool, smelt, ev);
                wear(tool);
            }
            p.getInventory().setItemInMainHand(tool);
        } finally {
            busy.remove(p.getUniqueId());
        }
    }

    // ---------------- mining: Auto Smelt on the block you actually broke ----------------
    @EventHandler(ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent e) {
        ItemStack tool = e.getPlayer().getInventory().getItemInMainHand();
        if (CEnchant.level(tool, CEnchant.AUTO_SMELT) <= 0) return;
        for (Item it : e.getItems()) {
            ItemStack st = it.getItemStack();
            Material r = smelted(st.getType());
            if (r == null) continue;
            st.setType(r);
            it.setItemStack(st);
        }
    }
}
