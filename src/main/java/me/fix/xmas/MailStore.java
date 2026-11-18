package me.fix.xmas;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Mail storage. One file per recipient (plugins/DillyyXmas/mail/&lt;uuid&gt;.yml) so a change only rewrites
 * that player's file, plus players.yml (every player who has ever joined, used by the player picker).
 * All reads/writes of the in-memory maps happen on the main thread; only file I/O is async, and it runs
 * on a single thread so writes land in order.
 */
final class MailStore {

    static final class Mail {
        final UUID id;
        final UUID from;
        final String fromName;
        final long time;
        private final String data;
        private ItemStack cache;

        Mail(UUID id, UUID from, String fromName, long time, String data) {
            this.id = id;
            this.from = from;
            this.fromName = fromName;
            this.time = time;
            this.data = data;
        }

        /** A fresh copy of the mailed item, or null if it cannot be read back. */
        ItemStack item() {
            try {
                if (cache == null) cache = ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
                return cache.clone();
            } catch (Exception e) {
                return null;
            }
        }
    }

    private final Xmas plugin;
    private final File mailDir;
    private final File playersFile;
    private final Map<UUID, LinkedHashMap<UUID, Mail>> mail = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private boolean seeded;
    private boolean namesDirty;
    private int taskId = -1;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> new Thread(r, "DillyyXmas-MailIO"));

    MailStore(Xmas plugin) {
        this.plugin = plugin;
        this.mailDir = new File(plugin.getDataFolder(), "mail");
        this.playersFile = new File(plugin.getDataFolder(), "players.yml");
    }

    // ------------------------------------------------------------------ load / save

    void load() {
        mailDir.mkdirs();

        YamlConfiguration pc = YamlConfiguration.loadConfiguration(playersFile);
        seeded = pc.getBoolean("seeded", false);
        ConfigurationSection ps = pc.getConfigurationSection("players");
        if (ps != null) {
            for (String k : ps.getKeys(false)) {
                try {
                    names.put(UUID.fromString(k), ps.getString(k, "?"));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        File[] files = mailDir.listFiles((d, n) -> n.endsWith(".yml"));
        int total = 0;
        if (files != null) {
            for (File f : files) {
                try {
                    UUID owner = UUID.fromString(f.getName().substring(0, f.getName().length() - 4));
                    YamlConfiguration y = new YamlConfiguration();
                    try {
                        y.load(f);
                    } catch (Exception bad) {
                        // never overwrite a file we could not read: keep a copy and warn loudly
                        File backup = new File(f.getPath() + ".corrupt-" + System.currentTimeMillis());
                        Files.copy(f.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().severe("Could not read " + f.getName() + " (copied to " + backup.getName() + "): " + bad.getMessage());
                        continue;
                    }
                    ConfigurationSection s = y.getConfigurationSection("mail");
                    if (s == null) continue;
                    LinkedHashMap<UUID, Mail> map = new LinkedHashMap<>();
                    for (String id : s.getKeys(false)) {
                        ConfigurationSection m = s.getConfigurationSection(id);
                        if (m == null) continue;
                        String data = m.getString("item");
                        if (data == null) continue;
                        UUID mid = UUID.fromString(id);
                        UUID from = null;
                        try {
                            String fs = m.getString("from");
                            if (fs != null) from = UUID.fromString(fs);
                        } catch (IllegalArgumentException ignored) {
                        }
                        map.put(mid, new Mail(mid, from, m.getString("fromName", "?"), m.getLong("time"), data));
                    }
                    if (!map.isEmpty()) {
                        mail.put(owner, map);
                        total += map.size();
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Skipping mail file " + f.getName() + ": " + e.getMessage());
                }
            }
        }
        plugin.getLogger().info("Mailbox: loaded " + total + " unclaimed gift(s) for " + mail.size() + " player(s).");

        if (!seeded) seed();
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (namesDirty) writeNames();
        }, 1200L, 1200L).getTaskId();
    }

    /** One-time import of everyone who has ever joined (so offline players can receive gifts). */
    private void seed() {
        int n = 0;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            String name = op.getName();
            if (name != null && op.hasPlayedBefore()) {
                names.put(op.getUniqueId(), name);
                n++;
            }
        }
        seeded = true;
        plugin.getLogger().info("Mailbox: indexed " + n + " existing player(s).");
        writeNames();
    }

    void shutdown() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        if (namesDirty) writeNames();
        io.shutdown();
        try {
            if (!io.awaitTermination(15, TimeUnit.SECONDS)) plugin.getLogger().warning("Mail writes did not finish in time.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeNames() {
        namesDirty = false;
        YamlConfiguration y = new YamlConfiguration();
        y.set("seeded", seeded);
        for (Map.Entry<UUID, String> e : names.entrySet()) y.set("players." + e.getKey(), e.getValue());
        String content = y.saveToString();
        io.submit(() -> writeAtomic(playersFile, content));
    }

    private void persist(UUID owner) {
        File f = new File(mailDir, owner + ".yml");
        Map<UUID, Mail> map = mail.get(owner);
        if (map == null || map.isEmpty()) {
            io.submit(() -> {
                try {
                    Files.deleteIfExists(f.toPath());
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not delete " + f.getName() + ": " + e.getMessage());
                }
            });
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        for (Mail m : map.values()) {
            String p = "mail." + m.id;
            y.set(p + ".from", m.from == null ? null : m.from.toString());
            y.set(p + ".fromName", m.fromName);
            y.set(p + ".time", m.time);
            y.set(p + ".item", m.data);
        }
        String content = y.saveToString();
        io.submit(() -> writeAtomic(f, content));
    }

    private void writeAtomic(File target, String content) {
        try {
            target.getParentFile().mkdirs();
            File tmp = new File(target.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFailed) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Could not write " + target.getName() + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ players index

    void remember(UUID id, String name) {
        String old = names.put(id, name);
        if (!name.equals(old)) namesDirty = true;
    }

    boolean isKnown(UUID id) {
        return names.containsKey(id);
    }

    String nameOf(UUID id) {
        return names.getOrDefault(id, "Unknown");
    }

    Map<UUID, String> known() {
        return Collections.unmodifiableMap(names);
    }

    // ------------------------------------------------------------------ mail

    int count(UUID owner) {
        Map<UUID, Mail> m = mail.get(owner);
        return m == null ? 0 : m.size();
    }

    /** Newest first. */
    List<Mail> inbox(UUID owner) {
        Map<UUID, Mail> m = mail.get(owner);
        if (m == null) return new ArrayList<>();
        List<Mail> list = new ArrayList<>(m.values());
        list.sort((a, b) -> Long.compare(b.time, a.time));
        return list;
    }

    Mail get(UUID owner, UUID mailId) {
        Map<UUID, Mail> m = mail.get(owner);
        return m == null ? null : m.get(mailId);
    }

    Mail add(UUID to, Player from, ItemStack item) {
        Mail m = new Mail(UUID.randomUUID(), from.getUniqueId(), from.getName(), System.currentTimeMillis(),
                Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        mail.computeIfAbsent(to, k -> new LinkedHashMap<>()).put(m.id, m);
        persist(to);
        return m;
    }

    Mail remove(UUID owner, UUID mailId) {
        Map<UUID, Mail> m = mail.get(owner);
        if (m == null) return null;
        Mail removed = m.remove(mailId);
        if (removed != null) {
            if (m.isEmpty()) mail.remove(owner);
            persist(owner);
        }
        return removed;
    }
}
