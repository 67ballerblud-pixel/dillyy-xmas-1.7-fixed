package me.fix.xmas;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Custom enchantments. They are stored on the item (persistent data) and shown in its lore, so they work on any
 * server without a datapack. Players get them as custom enchant books (/xmas book) and apply them in an anvil.
 */
enum CEnchant {

    LIFESTEAL("lifesteal", "Lifesteal", 3, "Heal a share of the damage you deal.",
            "_SWORD", "_AXE", "MACE", "TRIDENT", "_SPEAR"),
    FROSTBITE("frostbite", "Frostbite", 3, "Hits can freeze and slow your target.",
            "_SWORD", "_AXE", "MACE", "TRIDENT", "_SPEAR"),
    EXECUTIONER("executioner", "Executioner", 3, "Bonus damage to targets under 30% health.",
            "_SWORD", "_AXE", "MACE", "TRIDENT", "_SPEAR"),
    AUTO_SMELT("auto_smelt", "Auto Smelt", 1, "Ores drop as smelted ingots.",
            "_PICKAXE"),
    VEIN_MINER("vein_miner", "Vein Miner", 3, "Sneak while mining an ore to break the whole vein.",
            "_PICKAXE"),
    SWIFTNESS("swiftness", "Swiftness", 3, "Permanent Speed while worn.",
            "_BOOTS"),
    NIGHT_OWL("night_owl", "Night Owl", 1, "Permanent Night Vision while worn.",
            "_HELMET"),
    SNOWSTORM("snowstorm", "Snowstorm", 3, "Hits can whip up a mini blizzard that slows everything near the target.",
            "_SWORD", "_AXE", "MACE", "TRIDENT", "_SPEAR"),
    GRINCH_CURSE("grinch_curse", "Grinch's Curse", 3, "Hits can weaken and sicken your target.",
            "_SWORD", "_AXE", "MACE", "TRIDENT", "_SPEAR"),
    JINGLE_STUN("jingle_stun", "Jingle Stun", 3, "Hits can briefly stun your target with a ringing bell.",
            "_SWORD", "_AXE", "MACE", "TRIDENT", "_SPEAR"),
    HOLIDAY_BOUNTY("holiday_bounty", "Holiday Bounty", 3, "Mobs you kill can drop double loot.",
            "_SWORD", "_AXE", "MACE", "TRIDENT", "_SPEAR"),
    FROSTGUARD("frostguard", "Frostguard", 3, "Attackers can be frozen and slowed when they hit you.",
            "_CHESTPLATE", "_LEGGINGS"),
    HEARTH("hearth", "Hearth", 1, "Immune to freezing, and a warm glow heals you when you are low.",
            "_CHESTPLATE", "_LEGGINGS"),
    CANDY_LEAP("candy_leap", "Candy Leap", 3, "Permanent Jump Boost while worn.",
            "_BOOTS"),
    GIFT_OF_WISDOM("gift_of_wisdom", "Gift of Wisdom", 3, "Gain more experience from orbs.",
            "_HELMET"),
    TIMBER("timber", "Timber", 1, "Sneak while chopping a log to fell the whole tree.",
            "_AXE");

    final String id;
    final String display;
    final int max;
    final String description;
    private final String[] applies;

    CEnchant(String id, String display, int max, String description, String... applies) {
        this.id = id;
        this.display = display;
        this.max = max;
        this.description = description;
        this.applies = applies;
    }

    /** True if this enchant can go on the given item type. */
    boolean canApplyTo(Material m) {
        if (m == null) return false;
        String n = m.name();
        for (String a : applies) {
            if (a.startsWith("_") ? n.endsWith(a) : n.equals(a)) return true;
        }
        return false;
    }

    /** Short human text of what it goes on, e.g. "sword, axe, mace". */
    String appliesText() {
        List<String> out = new ArrayList<>();
        for (String a : applies) out.add(a.replace("_", "").toLowerCase(Locale.ROOT));
        return String.join(", ", out);
    }

    // ---------------- storage ----------------
    private static NamespacedKey ENCH;
    private static NamespacedKey BOOK;

    static void init(Xmas plugin) {
        ENCH = new NamespacedKey(plugin, "xmas_ench");
        BOOK = new NamespacedKey(plugin, "xmas_book");
    }

    static CEnchant byId(String s) {
        if (s == null) return null;
        String k = s.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (CEnchant e : values()) if (e.id.equals(k)) return e;
        return null;
    }

    static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private static Map<CEnchant, Integer> parse(String raw) {
        Map<CEnchant, Integer> m = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return m;
        for (String part : raw.split(";")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            CEnchant e = byId(kv[0]);
            if (e == null) continue;
            try {
                m.put(e, Math.max(1, Math.min(e.max, Integer.parseInt(kv[1]))));
            } catch (NumberFormatException ignored) {
            }
        }
        return m;
    }

    static Map<CEnchant, Integer> read(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return new LinkedHashMap<>();
        return parse(it.getItemMeta().getPersistentDataContainer().get(ENCH, PersistentDataType.STRING));
    }

    /** Level of one custom enchant on an item, 0 if it doesn't have it. */
    static int level(ItemStack it, CEnchant e) {
        if (it == null || !it.hasItemMeta()) return 0;
        return parse(it.getItemMeta().getPersistentDataContainer().get(ENCH, PersistentDataType.STRING))
                .getOrDefault(e, 0);
    }

    private static boolean isEnchantLine(String plain) {
        for (CEnchant e : values()) {
            if (plain.startsWith(e.display + " ")) {
                String rest = plain.substring(e.display.length() + 1);
                if (rest.matches("[IVX0-9]+")) return true;
            }
        }
        return false;
    }

    private static void write(ItemMeta meta, Map<CEnchant, Integer> map) {
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        List<Component> old = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        old.removeIf(c -> isEnchantLine(plain.serialize(c)));
        List<Component> lore = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<CEnchant, Integer> en : map.entrySet()) {
            lore.add(Xmas.line("&d" + en.getKey().display + " " + roman(en.getValue())));
            if (sb.length() > 0) sb.append(';');
            sb.append(en.getKey().id).append(':').append(en.getValue());
        }
        lore.addAll(old);
        meta.lore(lore);
        meta.getPersistentDataContainer().set(ENCH, PersistentDataType.STRING, sb.toString());
    }

    /** Returns a copy of the item with the custom enchant added (or raised). */
    static ItemStack apply(ItemStack base, CEnchant e, int lvl) {
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        Map<CEnchant, Integer> cur = parse(meta.getPersistentDataContainer().get(ENCH, PersistentDataType.STRING));
        cur.put(e, Math.max(1, Math.min(e.max, lvl)));
        write(meta, cur);
        it.setItemMeta(meta);
        return it;
    }

    // ---------------- books ----------------
    static ItemStack book(CEnchant e, int lvl) {
        int level = Math.max(1, Math.min(e.max, lvl));
        ItemStack it = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Xmas.line("&d&lCustom Enchant: &f" + e.display + " " + roman(level)));
        List<Component> lore = new ArrayList<>();
        lore.add(Xmas.line("&7" + e.description));
        lore.add(Xmas.line("&7Goes on: &f" + e.appliesText()));
        lore.add(Xmas.line(""));
        lore.add(Xmas.line("&8Combine with the item in an anvil."));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(BOOK, PersistentDataType.STRING, e.id + ":" + level);
        it.setItemMeta(meta);
        return it;
    }

    /** The enchant stored in a custom enchant book, or null if this isn't one. */
    static Map.Entry<CEnchant, Integer> bookContents(ItemStack it) {
        if (it == null || it.getType() != Material.ENCHANTED_BOOK || !it.hasItemMeta()) return null;
        Map<CEnchant, Integer> m = parse(it.getItemMeta().getPersistentDataContainer().get(BOOK, PersistentDataType.STRING));
        return m.isEmpty() ? null : m.entrySet().iterator().next();
    }
}
