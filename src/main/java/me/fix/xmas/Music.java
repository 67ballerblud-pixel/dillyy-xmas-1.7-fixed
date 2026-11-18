package me.fix.xmas;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Christmas music. /xmas music &lt;song&gt; gives a music disc; put it in a jukebox and the song plays, audible up to
 * music.range blocks away (default 100).
 *
 * <p>Songs come from three places, because a vanilla client can only play sounds it already has:
 * <ol>
 *   <li>built-in public-domain carols, played on note block sounds</li>
 *   <li>.nbs files (Note Block Studio) dropped into plugins/DillyyXmas/songs/</li>
 *   <li>music.custom-sounds in config.yml: sound events from a resource pack that you host yourself</li>
 * </ol>
 * The plugin does not download or rip songs from the internet.
 */
public class Music implements Listener {

    // ---------------------------------------------------------------- data
    private static final class Note {
        final double time;
        final Sound sound;
        final int idx;       // 0..24 note block index
        final float volume;

        Note(double time, Sound sound, int idx, float volume) {
            this.time = time;
            this.sound = sound;
            this.idx = idx;
            this.volume = volume;
        }
    }

    private static final class Song {
        final String id;
        final String title;
        final List<Note> notes;
        final double length;
        final boolean builtin;
        final String customSound; // resource-pack sound key, or null

        Song(String id, String title, List<Note> notes, double length, boolean builtin, String customSound) {
            this.id = id;
            this.title = title;
            this.notes = notes;
            this.length = length;
            this.builtin = builtin;
            this.customSound = customSound;
        }
    }

    private static final Sound[] NBS_INSTRUMENTS = {
            Sound.BLOCK_NOTE_BLOCK_HARP, Sound.BLOCK_NOTE_BLOCK_BASS, Sound.BLOCK_NOTE_BLOCK_BASEDRUM,
            Sound.BLOCK_NOTE_BLOCK_SNARE, Sound.BLOCK_NOTE_BLOCK_HAT, Sound.BLOCK_NOTE_BLOCK_GUITAR,
            Sound.BLOCK_NOTE_BLOCK_FLUTE, Sound.BLOCK_NOTE_BLOCK_BELL, Sound.BLOCK_NOTE_BLOCK_CHIME,
            Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, Sound.BLOCK_NOTE_BLOCK_COW_BELL,
            Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, Sound.BLOCK_NOTE_BLOCK_BIT, Sound.BLOCK_NOTE_BLOCK_BANJO,
            Sound.BLOCK_NOTE_BLOCK_PLING};

    private static final Material[] DISCS = {
            Material.MUSIC_DISC_CAT, Material.MUSIC_DISC_BLOCKS, Material.MUSIC_DISC_CHIRP, Material.MUSIC_DISC_FAR,
            Material.MUSIC_DISC_MALL, Material.MUSIC_DISC_MELLOHI, Material.MUSIC_DISC_STAL, Material.MUSIC_DISC_STRAD,
            Material.MUSIC_DISC_WARD, Material.MUSIC_DISC_WAIT, Material.MUSIC_DISC_13, Material.MUSIC_DISC_11,
            Material.MUSIC_DISC_OTHERSIDE, Material.MUSIC_DISC_PIGSTEP};

    /** Built-in public-domain carols: title, bpm, melody as NOTE:BEATS (R = rest). Written from memory, tweak freely. */
    private static final String[][] BUILTIN = {
            {"Jingle Bells", "150",
                    "E4:1 E4:1 E4:2 E4:1 E4:1 E4:2 E4:1 G4:1 C4:1.5 D4:.5 E4:4 "
                            + "F4:1 F4:1 F4:1.5 F4:.5 F4:1 E4:1 E4:1 E4:.5 E4:.5 E4:1 D4:1 D4:1 E4:1 D4:2 G4:2 "
                            + "E4:1 E4:1 E4:2 E4:1 E4:1 E4:2 E4:1 G4:1 C4:1.5 D4:.5 E4:4 "
                            + "F4:1 F4:1 F4:1.5 F4:.5 F4:1 E4:1 E4:1 E4:.5 E4:.5 G4:1 G4:1 F4:1 D4:1 C4:4"},
            {"Silent Night", "84",
                    "G4:1.5 A4:.5 G4:1 E4:3 G4:1.5 A4:.5 G4:1 E4:3 D5:2 D5:1 B4:3 C5:2 C5:1 G4:3 "
                            + "A4:2 A4:1 C5:1.5 B4:.5 A4:1 G4:1.5 A4:.5 G4:1 E4:3 "
                            + "A4:2 A4:1 C5:1.5 B4:.5 A4:1 G4:1.5 A4:.5 G4:1 E4:3 "
                            + "D5:2 D5:1 F5:1.5 D5:.5 B4:1 C5:3 E5:3 C5:1 G4:1 E4:1 G4:1.5 F4:.5 D4:1 C4:3"},
            {"We Wish You a Merry Christmas", "132",
                    "D4:1 G4:1 G4:.5 A4:.5 G4:.5 F#4:.5 E4:1 E4:1 E4:1 A4:1 A4:.5 B4:.5 A4:.5 G4:.5 F#4:1 D4:1 D4:1 "
                            + "B4:1 B4:.5 C5:.5 B4:.5 A4:.5 G4:1 E4:1 D4:.5 D4:.5 E4:1 A4:1 F#4:1 G4:3"},
            {"Carol of the Bells", "176",
                    "G4:1 F#4:.5 G4:.5 D4:1 G4:1 F#4:.5 G4:.5 D4:1 G4:1 F#4:.5 G4:.5 D4:1 G4:1 F#4:.5 G4:.5 D4:1 "
                            + "A4:1 G#4:.5 A4:.5 E4:1 A4:1 G#4:.5 A4:.5 E4:1 A4:1 G#4:.5 A4:.5 E4:1 A4:1 G#4:.5 A4:.5 E4:1 "
                            + "B4:1 A#4:.5 B4:.5 F#4:1 B4:1 A#4:.5 B4:.5 F#4:1 B4:1 A#4:.5 B4:.5 F#4:1 B4:1 A#4:.5 B4:.5 F#4:1 "
                            + "D5:1 C#5:.5 D5:.5 A4:1 D5:1 C#5:.5 D5:.5 A4:1 D5:1 C#5:.5 D5:.5 A4:1 D5:1 C#5:.5 D5:.5 A4:1 "
                            + "G4:3 G4:3"}
    };

    // ---------------------------------------------------------------- state
    private final Xmas plugin;
    private final NamespacedKey discKey;
    private final Map<String, Song> songs = new LinkedHashMap<>();
    private final Map<String, Session> sessions = new HashMap<>();

    Music(Xmas plugin) {
        this.plugin = plugin;
        this.discKey = new NamespacedKey(plugin, "xmas_disc");
    }

    private double range() {
        return Math.max(16, plugin.getConfig().getDouble("music.range", 100));
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("music.enabled", true);
    }

    // ---------------------------------------------------------------- loading
    void load() {
        songs.clear();
        for (String[] b : BUILTIN) {
            Song s = parseBuiltin(b[0], Double.parseDouble(b[1]), b[2]);
            songs.put(s.id, s);
        }
        File dir = new File(plugin.getDataFolder(), "songs");
        if (!dir.exists()) {
            dir.mkdirs();
            try {
                Files.writeString(new File(dir, "README.txt").toPath(),
                        "Drop Note Block Studio (.nbs) files in this folder, then run /xmas music reload.\n"
                                + "The file name becomes the song name, e.g. Last_Christmas.nbs -> /xmas music last christmas\n");
            } catch (IOException ignored) {
            }
        }
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".nbs"));
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File f : files) {
                try {
                    String title = f.getName().substring(0, f.getName().length() - 4).replace('_', ' ');
                    Song s = loadNbs(f, title);
                    if (s != null && !s.notes.isEmpty()) songs.put(s.id, s);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Could not read " + f.getName() + ": " + ex);
                }
            }
        }
        ConfigurationSection custom = plugin.getConfig().getConfigurationSection("music.custom-sounds");
        if (custom != null) {
            for (String key : custom.getKeys(false)) {
                String sound = custom.getString(key + ".sound");
                if (sound == null || sound.isBlank()) continue;
                String title = custom.getString(key + ".title", key);
                double seconds = custom.getDouble(key + ".seconds", 180);
                Song s = new Song(slug(title), title, List.of(), seconds, false, sound);
                songs.put(s.id, s);
            }
        }
        plugin.getLogger().info("Music: " + songs.size() + " songs loaded.");
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String slug(String title) {
        return norm(title).replace(' ', '_');
    }

    /** NOTE like C4, F#4, Bb3 -> note block index 0..24 (F#3..F#5), shifted by octaves if it is outside that range. */
    private static int index(String name) {
        int i = 0;
        int semi = switch (Character.toUpperCase(name.charAt(i++))) {
            case 'C' -> 0;
            case 'D' -> 2;
            case 'E' -> 4;
            case 'F' -> 5;
            case 'G' -> 7;
            case 'A' -> 9;
            default -> 11;
        };
        if (i < name.length() && name.charAt(i) == '#') {
            semi++;
            i++;
        } else if (i < name.length() && name.charAt(i) == 'b') {
            semi--;
            i++;
        }
        int octave = Integer.parseInt(name.substring(i));
        return fit(12 * (octave + 1) + semi - 54);
    }

    private static int fit(int idx) {
        while (idx < 0) idx += 12;
        while (idx > 24) idx -= 12;
        return idx;
    }

    private Song parseBuiltin(String title, double bpm, String melody) {
        List<Note> notes = new ArrayList<>();
        double t = 0.2;
        double sec = 60.0 / bpm;
        for (String tok : melody.trim().split("\\s+")) {
            String[] p = tok.split(":");
            double beats = p.length > 1 ? Double.parseDouble(p[1]) : 1;
            if (!p[0].equalsIgnoreCase("R")) {
                int idx = index(p[0]);
                notes.add(new Note(t, Sound.BLOCK_NOTE_BLOCK_HARP, idx, 1.0f));
                notes.add(new Note(t, Sound.BLOCK_NOTE_BLOCK_BELL, idx, 0.55f));
            }
            t += beats * sec;
        }
        return new Song(slug(title), title, notes, t, true, null);
    }

    private static String readStr(ByteBuffer b) {
        int len = b.getInt();
        if (len < 0 || len > 100_000) throw new BufferUnderflowException();
        byte[] x = new byte[len];
        b.get(x);
        return new String(x, StandardCharsets.UTF_8);
    }

    /** Minimal Note Block Studio (.nbs) reader: classic format and versions 1 to 5. */
    private Song loadNbs(File f, String fallbackTitle) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(f.toPath())).order(ByteOrder.LITTLE_ENDIAN);
        List<Note> notes = new ArrayList<>();
        double last = 0;
        try {
            int version = 0;
            int first = b.getShort();
            if (first == 0) {
                version = b.get();
                b.get();                      // vanilla instrument count
                if (version >= 3) b.getShort(); // song length
            }
            b.getShort();                     // layer count
            String name = readStr(b);
            readStr(b);
            readStr(b);
            readStr(b);                       // author, original author, description
            int tempo = b.getShort() & 0xFFFF;
            b.get();
            b.get();
            b.get();                          // auto-save, duration, time signature
            for (int i = 0; i < 5; i++) b.getInt();
            readStr(b);                       // imported midi/schematic name
            if (version >= 4) {
                b.get();
                b.get();
                b.getShort();                 // loop on/off, max loops, loop start
            }
            double step = tempo <= 0 ? 0.05 : 100.0 / tempo;
            int tick = -1;
            while (true) {
                int jump = b.getShort() & 0xFFFF;
                if (jump == 0) break;
                tick += jump;
                while (true) {
                    int layerJump = b.getShort() & 0xFFFF;
                    if (layerJump == 0) break;
                    int inst = b.get() & 0xFF;
                    int key = b.get() & 0xFF;
                    double vol = 1.0;
                    if (version >= 4) {
                        vol = (b.get() & 0xFF) / 100.0;
                        b.get();              // panning
                        b.getShort();         // fine pitch
                    }
                    if (inst >= NBS_INSTRUMENTS.length) continue;
                    double time = 0.2 + tick * step;
                    last = Math.max(last, time);
                    notes.add(new Note(time, NBS_INSTRUMENTS[inst], fit(key - 33), (float) Math.max(0.1, Math.min(1.0, vol))));
                }
            }
            if (!name.isBlank() && name.length() < 60) fallbackTitle = name.trim();
        } catch (BufferUnderflowException ignored) {
            // truncated file: play what we got
        }
        return new Song(slug(fallbackTitle), fallbackTitle, notes, last, false, null);
    }

    // ---------------------------------------------------------------- disc item
    private ItemStack disc(Song s) {
        return disc(s.id, s.title, s.length);
    }

    private ItemStack disc(String id, String title, double length) {
        ItemStack it = new ItemStack(DISCS[Math.floorMod(id.hashCode(), DISCS.length)]);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Xmas.line("&c&l♫ &f&l" + title));
        int secs = (int) Math.round(length);
        List<Component> lore = new ArrayList<>();
        lore.add(Xmas.line("&7Christmas music disc &8(" + (secs / 60) + ":" + String.format("%02d", secs % 60) + ")"));
        lore.add(Xmas.line(""));
        lore.add(Xmas.line("&a&lRight-click a jukebox &7to play it."));
        lore.add(Xmas.line("&7Everyone within &f" + (int) range() + " &7blocks can hear it."));
        lore.add(Xmas.line("&7Right-click the jukebox again to take it out."));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(discKey, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    private String discId(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(discKey, PersistentDataType.STRING);
    }

    // ---------------------------------------------------------------- command
    List<String> ids() {
        List<String> out = new ArrayList<>(songs.keySet());
        out.add("list");
        out.add("reload");
        return out;
    }

    void command(CommandSender s, String[] a) {
        if (!enabled()) {
            plugin.msg(s, "&cMusic is turned off.");
            return;
        }
        if (a.length < 2 || a[1].equalsIgnoreCase("list")) {
            list(s);
            return;
        }
        if (a[1].equalsIgnoreCase("reload")) {
            if (!s.hasPermission("xmas.admin")) {
                plugin.msg(s, "&cNo permission.");
                return;
            }
            load();
            plugin.msg(s, "&aReloaded &f" + songs.size() + " &asongs.");
            return;
        }
        if (!(s instanceof Player p)) {
            plugin.msg(s, "&cPlayers only.");
            return;
        }
        if (!p.hasPermission("xmas.music")) {
            plugin.msg(s, "&cYou don't have permission to get music discs.");
            return;
        }
        String q = norm(String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)));
        List<Song> hits = new ArrayList<>();
        for (Song song : songs.values()) {
            String t = norm(song.title);
            if (t.equals(q)) {
                hits.clear();
                hits.add(song);
                break;
            }
            if (t.contains(q)) hits.add(song);
        }
        if (hits.isEmpty()) {
            plugin.msg(s, "&cNo song called &f" + String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)) + "&c.");
            plugin.msg(s, "&7I can't search the web for songs. &f/xmas music list &7shows what's installed.");
            plugin.msg(s, "&7Admins can add more by dropping &f.nbs &7files in &fplugins/DillyyXmas/songs&7.");
            return;
        }
        if (hits.size() > 1) {
            StringBuilder sb = new StringBuilder("&eMore than one match: ");
            for (Song h : hits) sb.append("&f").append(h.title).append("&7, ");
            plugin.msg(s, sb.substring(0, sb.length() - 2));
            return;
        }
        Song song = hits.get(0);
        for (ItemStack left : p.getInventory().addItem(disc(song)).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
        plugin.msg(s, "&aHere's &f" + song.title + "&a. Put it in a jukebox!");
    }

    private void list(CommandSender s) {
        plugin.msg(s, "&6&lChristmas music &7(&f/xmas music <song name>&7)");
        for (Song song : songs.values()) {
            String kind = song.customSound != null ? "&8resource pack" : song.builtin ? "&8built-in" : "&8.nbs file";
            s.sendMessage(Xmas.c("&7  &c♫ &f" + song.title + " " + kind));
        }
    }

    // ---------------------------------------------------------------- jukebox
    private static String key(Block b) {
        return b.getWorld().getName().replace('.', '_') + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private String stored(Block b) {
        return plugin.data().getString("music.discs." + key(b));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (!enabled() || e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.JUKEBOX) return;
        Player p = e.getPlayer();
        ItemStack hand = e.getItem();
        String held = discId(hand);
        String inside = stored(b);

        if (held != null) {
            e.setCancelled(true); // never let the vanilla jukebox play the disc item's real music
            if (inside != null || (b.getState() instanceof Jukebox jb && jb.hasRecord())) {
                p.sendActionBar(Xmas.c("&cThat jukebox already has a disc in it."));
                return;
            }
            Song song = songs.get(held);
            if (song == null) {
                p.sendActionBar(Xmas.c("&cThat song isn't installed any more."));
                return;
            }
            hand.setAmount(hand.getAmount() - 1);
            plugin.data().set("music.discs." + key(b), song.id);
            plugin.saveData();
            play(b, song);
            p.sendActionBar(Xmas.c("&a♫ Now playing: &f" + song.title));
            return;
        }
        if (inside != null) {
            if (p.isSneaking() && hand != null && !hand.getType().isAir()) return; // sneak-placing a block
            e.setCancelled(true);
            eject(b);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() == Material.JUKEBOX && stored(e.getBlock()) != null) eject(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        for (Block b : e.blockList()) if (b.getType() == Material.JUKEBOX && stored(b) != null) eject(b);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(BlockExplodeEvent e) {
        for (Block b : e.blockList()) if (b.getType() == Material.JUKEBOX && stored(b) != null) eject(b);
    }

    /** Take the disc out: stop the music and drop the disc item above the jukebox. */
    private void eject(Block b) {
        String k = key(b);
        String id = plugin.data().getString("music.discs." + k);
        Session ses = sessions.remove(k);
        if (ses != null) ses.stop();
        plugin.data().set("music.discs." + k, null);
        plugin.saveData();
        if (id == null) return;
        Song song = songs.get(id);
        ItemStack item = song != null ? disc(song) : disc(id, id.replace('_', ' '), 0);
        Location at = b.getLocation().add(0.5, 1.1, 0.5);
        b.getWorld().dropItem(at, item);
        b.getWorld().playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.RECORDS, 1f, 0.8f);
    }

    private void play(Block b, Song song) {
        String k = key(b);
        Session old = sessions.remove(k);
        if (old != null) old.stop();
        Session s = new Session(b, k, song);
        sessions.put(k, s);
        s.runTaskTimer(plugin, 1L, 1L);
    }

    private final class Session extends BukkitRunnable {
        private final Block block;
        private final String key;
        private final Song song;
        private final Location center;
        private int tick;
        private int next;

        Session(Block block, String key, Song song) {
            this.block = block;
            this.key = key;
            this.song = song;
            this.center = block.getLocation().add(0.5, 0.5, 0.5);
            if (song.customSound != null) {
                block.getWorld().playSound(center, song.customSound, SoundCategory.RECORDS, (float) (range() / 16.0), 1f);
            }
        }

        void stop() {
            cancel();
            if (song.customSound != null) {
                World w = block.getWorld();
                for (Player p : w.getPlayers()) p.stopSound(song.customSound, SoundCategory.RECORDS);
            }
        }

        @Override
        public void run() {
            World w = block.getWorld();
            if (!w.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                sessions.remove(key);
                cancel();
                return;
            }
            if (block.getType() != Material.JUKEBOX) { // removed by something that isn't a player or explosion
                sessions.remove(key);
                if (plugin.data().getString("music.discs." + key) != null) {
                    plugin.data().set("music.discs." + key, null);
                    plugin.saveData();
                    w.dropItem(center, disc(song));
                }
                stop();
                return;
            }
            double now = tick++ / 20.0;
            float base = (float) (range() / 16.0); // a sound's reach is 16 blocks x volume
            boolean particles = plugin.getConfig().getBoolean("music.note-particles", true);
            boolean bell = plugin.getConfig().getBoolean("music.bell-layer", true);
            Location up = center.clone().add(0, 0.9, 0);
            while (next < song.notes.size() && song.notes.get(next).time <= now) {
                Note n = song.notes.get(next++);
                if (n.sound == Sound.BLOCK_NOTE_BLOCK_BELL && song.builtin && !bell) continue;
                float pitch = (float) Math.pow(2.0, (n.idx - 12) / 12.0);
                w.playSound(center, n.sound, SoundCategory.RECORDS, base * n.volume, pitch);
                if (particles && n.sound != Sound.BLOCK_NOTE_BLOCK_BELL) {
                    w.spawnParticle(Particle.NOTE, up, 0, n.idx / 24.0, 0, 0, 1.0);
                }
            }
            if (song.customSound != null && particles && tick % 10 == 0) {
                w.spawnParticle(Particle.NOTE, up, 0, (tick % 24) / 24.0, 0, 0, 1.0);
            }
            if (tick % 40 == 0 && particles) w.spawnParticle(Particle.SNOWFLAKE, up, 6, 0.4, 0.3, 0.4, 0.01);
            if (now > song.length + 1.5) {
                sessions.remove(key);
                cancel(); // song finished: the disc stays in the jukebox until someone takes it out
            }
        }
    }
}
