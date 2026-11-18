package me.fix.xmas;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Santa's presents: shulker-box gifts that appear in the wild; right-click one to open it. */
public class Presents implements Listener {

    private static final List<Material> DEFAULT_MATS =
            List.of(Material.RED_SHULKER_BOX, Material.GREEN_SHULKER_BOX, Material.WHITE_SHULKER_BOX);

    private static class Present {
        final Location loc;
        final long expires;

        Present(Location loc, long expires) {
            this.loc = loc;
            this.expires = expires;
        }
    }

    private final Xmas plugin;
    private final Map<String, Present> active = new HashMap<>();
    private long nextSpawn;

    Presents(Xmas plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private static String key(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    private List<Material> materials() {
        List<Material> out = new ArrayList<>();
        for (String s : cfg().getStringList("presents.materials")) {
            Material m = Material.matchMaterial(s);
            if (m != null) out.add(m);
        }
        return out.isEmpty() ? DEFAULT_MATS : out;
    }

    private boolean isPresentMaterial(Material m) {
        return materials().contains(m) || DEFAULT_MATS.contains(m);
    }

    // ---------- lifecycle ----------
    void start() {
        cleanupOrphans();
        nextSpawn = System.currentTimeMillis() + intervalMs();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private long intervalMs() {
        return Math.max(1, cfg().getLong("presents.interval-minutes", 20)) * 60_000L;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Present> it = active.values().iterator();
        while (it.hasNext()) {
            Present p = it.next();
            if (now >= p.expires) {
                removeBlock(p.loc);
                it.remove();
                changed = true;
                continue;
            }
            p.loc.getWorld().spawnParticle(Particle.END_ROD,
                    p.loc.getX() + 0.5, p.loc.getY() + 1.3, p.loc.getZ() + 0.5, 3, 0.15, 0.4, 0.15, 0.01);
            // a candy-colored beam so presents can be spotted from far away
            for (int i = 0; i < 12; i++) {
                p.loc.getWorld().spawnParticle(Particle.DUST,
                        p.loc.getX() + 0.5, p.loc.getY() + 1.5 + i * 1.5, p.loc.getZ() + 0.5, 1, 0.05, 0, 0.05, 0,
                        new Particle.DustOptions(i % 2 == 0 ? Color.RED : Color.fromRGB(30, 190, 70), 1.8f));
            }
        }
        if (changed) saveList();

        if (cfg().getBoolean("presents.enabled", true) && now >= nextSpawn) {
            nextSpawn = now + intervalMs();
            if (active.size() < cfg().getInt("presents.max-active", 3)
                    && Bukkit.getOnlinePlayers().size() >= cfg().getInt("presents.min-online", 1)) {
                spawnRandom(15, true);
            }
        }
    }

    // ---------- spawning ----------
    void spawnRandom(int attempts, boolean announce) {
        World w = Bukkit.getWorld(cfg().getString("presents.world", "world"));
        if (w == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double min = cfg().getDouble("presents.min-radius", 50);
        double max = Math.max(min + 1, cfg().getDouble("presents.max-radius", 500));
        double angle = r.nextDouble(Math.PI * 2);
        double dist = r.nextDouble(min, max);
        int x = (int) (cfg().getDouble("presents.center-x", 0) + Math.cos(angle) * dist);
        int z = (int) (cfg().getDouble("presents.center-z", 0) + Math.sin(angle) * dist);
        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk ->
                Bukkit.getScheduler().runTask(plugin, () -> place(w, x, z, attempts, announce)));
    }

    private void place(World w, int x, int z, int attempts, boolean announce) {
        Block ground = w.getHighestBlockAt(x, z);
        Block above = ground.getRelative(BlockFace.UP);
        boolean bad = ground.isLiquid() || Tag.LEAVES.isTagged(ground.getType())
                || !above.isEmpty() || !w.getWorldBorder().isInside(above.getLocation());
        if (bad) {
            if (attempts > 0) spawnRandom(attempts - 1, announce);
            return;
        }
        create(above.getLocation(), announce);
    }

    /** Used by Santa's Sleigh: drop a present on the ground at this x/z if it is a safe spot. */
    void dropAt(Location air) {
        World w = air.getWorld();
        if (w == null) return;
        Block ground = w.getHighestBlockAt(air.getBlockX(), air.getBlockZ());
        Block above = ground.getRelative(BlockFace.UP);
        if (ground.isLiquid() || Tag.LEAVES.isTagged(ground.getType()) || !above.isEmpty()
                || !w.getWorldBorder().isInside(above.getLocation())) return;
        if (active.size() >= cfg().getInt("sleigh.max-presents", 12)) return;
        create(above.getLocation(), false);
    }

    void create(Location loc, boolean announce) {
        List<Material> mats = materials();
        Material m = mats.get(ThreadLocalRandom.current().nextInt(mats.size()));
        loc.getBlock().setType(m);
        long life = Math.max(1, cfg().getLong("presents.lifetime-minutes", 10)) * 60_000L;
        active.put(key(loc), new Present(loc.clone(), System.currentTimeMillis() + life));
        saveList();
        World w = loc.getWorld();
        w.spawnParticle(Particle.FIREWORK, loc.getX() + 0.5, loc.getY() + 1, loc.getZ() + 0.5, 40, 0.4, 0.6, 0.4, 0.05);
        w.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
        if (announce) announce(loc);
    }

    private void announce(Location loc) {
        int round = cfg().getInt("presents.hint-rounding", 100);
        String where;
        if (round <= 0) {
            where = "X " + loc.getBlockX() + ", Y " + loc.getBlockY() + ", Z " + loc.getBlockZ();
        } else {
            long x = Math.round(loc.getX() / (double) round) * round;
            long z = Math.round(loc.getZ() / (double) round) * round;
            where = "X " + x + ", Z " + z;
        }
        String m = cfg().getString("presents.announce-message", "&fA present appeared near &e{where}&f!");
        Bukkit.broadcast(Xmas.c(m.replace("{where}", where).replace("{world}", loc.getWorld().getName())));
    }

    // ---------- opening ----------
    private void claim(Player p, Present pr) {
        List<Map<?, ?>> rewards = cfg().getMapList("presents.rewards");
        if (rewards.isEmpty()) return;
        int total = 0;
        for (Map<?, ?> m : rewards) total += weight(m);
        if (total <= 0) return;
        int roll = ThreadLocalRandom.current().nextInt(total);
        Map<?, ?> chosen = rewards.get(0);
        for (Map<?, ?> m : rewards) {
            roll -= weight(m);
            if (roll < 0) {
                chosen = m;
                break;
            }
        }
        List<String> cmds = new ArrayList<>();
        Object c = chosen.get("commands");
        if (c instanceof List<?> l) for (Object o : l) cmds.add(String.valueOf(o));
        String name = chosen.get("name") == null ? "a gift" : String.valueOf(chosen.get("name"));

        active.remove(key(pr.loc));
        removeBlock(pr.loc);
        saveList();

        plugin.runCommands(p, cmds);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        firework(pr.loc.clone().add(0.5, 1, 0.5));
        String found = cfg().getString("presents.found-message", "&e{player} &ffound a present and got &a{reward}&f!");
        Bukkit.broadcast(Xmas.c(found.replace("{player}", p.getName()).replace("{reward}", name)));
    }

    private int weight(Map<?, ?> m) {
        Object w = m.get("weight");
        return w instanceof Number n ? Math.max(0, n.intValue()) : 1;
    }

    private void firework(Location l) {
        Firework fw = l.getWorld().spawn(l, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder().withColor(Color.RED, Color.GREEN).withFade(Color.WHITE)
                .with(FireworkEffect.Type.BALL_LARGE).build());
        fw.setFireworkMeta(meta);
        fw.detonate();
    }

    // ---------- cleanup / persistence ----------
    private void removeBlock(Location loc) {
        Block b = loc.getBlock();
        if (isPresentMaterial(b.getType())) b.setType(Material.AIR);
    }

    void clearAll() {
        for (Present p : active.values()) removeBlock(p.loc);
        active.clear();
        saveList();
    }

    private void saveList() {
        plugin.data().set("presents", new ArrayList<>(active.keySet()));
        plugin.saveData();
    }

    /** Presents left behind by a crash/stop: remove them on startup. */
    private void cleanupOrphans() {
        List<String> old = plugin.data().getStringList("presents");
        plugin.data().set("presents", new ArrayList<String>());
        plugin.saveData();
        for (String s : old) {
            String[] p = s.split(";");
            if (p.length != 4) continue;
            World w = Bukkit.getWorld(p[0]);
            if (w == null) continue;
            try {
                int x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]), z = Integer.parseInt(p[3]);
                Location loc = new Location(w, x, y, z);
                w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(ch ->
                        Bukkit.getScheduler().runTask(plugin, () -> removeBlock(loc)));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    // ---------- protection ----------
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Present pr = active.get(key(e.getClickedBlock().getLocation()));
        if (pr == null) return;
        e.setCancelled(true);
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getHand() == EquipmentSlot.HAND) {
            claim(e.getPlayer(), pr);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (active.containsKey(key(e.getBlock().getLocation()))) {
            e.setCancelled(true);
            plugin.msg(e.getPlayer(), "&7Right-click the present to open it!");
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> active.containsKey(key(b.getLocation())));
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> active.containsKey(key(b.getLocation())));
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (active.containsKey(key(b.getLocation()))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (active.containsKey(key(b.getLocation()))) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
