package me.fix.xmas;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Custom Christmas items. Get them with /xmas give <player> <id> [amount] (the crate does this too). */
public final class Items {

    public static final List<String> IDS = List.of(
            "santa_hat", "reindeer_boots", "candy_cane_sword", "candy_cane_pickaxe", "frost_wand", "grinch_heart",
            "gingerbread_chestplate", "snow_globe", "jingle_bells", "holly_bow",
            "sleigh_launcher", "north_star", "eggnog_flask",
            "yule_log_axe", "mistletoe_kiss", "ice_skates", "snowball_cannon", "frosty_coal");

    private static NamespacedKey key;

    private Items() {
    }

    static void init(Xmas plugin) {
        key = new NamespacedKey(plugin, "xmas_item");
    }

    static String idOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private static ItemStack build(String id, Material m, String name, Color color, List<String> lore,
                                   Map<Enchantment, Integer> ench) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Xmas.line(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Xmas.line(s));
        meta.lore(l);
        for (Map.Entry<Enchantment, Integer> e : ench.entrySet()) meta.addEnchant(e.getKey(), e.getValue(), true);
        if (color != null && meta instanceof LeatherArmorMeta lm) lm.setColor(color);
        meta.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    private static Map<Enchantment, Integer> ench(Object... pairs) {
        Map<Enchantment, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((Enchantment) pairs[i], (Integer) pairs[i + 1]);
        return m;
    }

    /** True when the player wears the Santa Hat, Gingerbread Plate and Reindeer Boots together. */
    static boolean santaSet(org.bukkit.inventory.PlayerInventory inv) {
        return "santa_hat".equals(idOf(inv.getHelmet()))
                && "gingerbread_chestplate".equals(idOf(inv.getChestplate()))
                && "reindeer_boots".equals(idOf(inv.getBoots()));
    }

    static ItemStack create(String id, int amount) {
        ItemStack it;
        switch (id) {
            case "santa_hat" -> it = build(id, Material.LEATHER_HELMET, "&c&lSanta Hat", Color.RED,
                    List.of("&7Ho ho ho!", "",
                            "&c&lChristmas Spirit &7- Regeneration while worn.",
                            "&c&lSanta's Sack &7- monsters you kill can", "&7drop a gift.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.RESPIRATION, 3));
            case "reindeer_boots" -> it = build(id, Material.LEATHER_BOOTS, "&6&lReindeer Boots", Color.fromRGB(139, 69, 19),
                    List.of("&7Fast as Rudolph.", "",
                            "&6&lRudolph's Stride &7- Speed and Jump Boost, no fall damage.",
                            "&6&lSleigh Dash &7- sprint, then sneak to dash forward.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, Enchantment.DEPTH_STRIDER, 3,
                            Enchantment.UNBREAKING, 3));
            case "candy_cane_sword" -> it = build(id, Material.DIAMOND_SWORD, "&c&lCandy &f&lCane &c&lBlade", null,
                    List.of("&7Sweet, sharp, and a little sticky.", "",
                            "&c&lSticky Hit &7- every hit slows the target.",
                            "&c&lSugar Rush &7- hits can heal you and give Speed.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.SHARPNESS, 4, Enchantment.LOOTING, 3, Enchantment.UNBREAKING, 3));
            case "candy_cane_pickaxe" -> it = build(id, Material.DIAMOND_PICKAXE, "&c&lCandy &f&lCane &c&lPickaxe", null,
                    List.of("&7Digs through coal faster than Santa's list.", "",
                            "&c&lSugar Haste &7- Haste II while held.",
                            "&c&lPeppermint Vein &7- sneak while mining an ore", "&7to break the whole vein.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3, Enchantment.UNBREAKING, 3));
            case "frost_wand" -> it = build(id, Material.BLAZE_ROD, "&b&lFrost Wand", null,
                    List.of("&7Right-click to fire an ice bolt.", "&7Slows whatever it hits.", "", "&8Christmas Exclusive"),
                    ench(Enchantment.UNBREAKING, 1));
            case "grinch_heart" -> it = build(id, Material.HEART_OF_THE_SEA, "&2&lGrinch's Heart", null,
                    List.of("&7Two sizes too small... until now.", "&7A trophy from defeating &aThe Grinch&7.", "",
                            "&2&lGrinch's Grip &7- right-click to pull in and weaken", "&7everything nearby while you heal.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.UNBREAKING, 1));
            case "gingerbread_chestplate" -> it = build(id, Material.LEATHER_CHESTPLATE, "&6&lGingerbread Plate",
                    Color.fromRGB(160, 82, 45),
                    List.of("&7Baked fresh, hard as a rock.", "",
                            "&6&lCrunch &7- sometimes reflects part of the", "&7damage you take back at the attacker.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3));
            case "snow_globe" -> it = build(id, Material.PRISMARINE_CRYSTALS, "&b&lSnow Globe", null,
                    List.of("&7Shake it and the blizzard follows.", "",
                            "&b&lBlizzard &7- right-click to freeze and slow", "&7everything around you.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.UNBREAKING, 1));
            case "jingle_bells" -> it = build(id, Material.BELL, "&6&lJingle Bells", null,
                    List.of("&7Jingle all the way.", "",
                            "&6&lSleigh Ride &7- right-click for a burst of", "&7Speed, Jump Boost and Haste.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.UNBREAKING, 1));
            case "holly_bow" -> it = build(id, Material.BOW, "&2&lHolly Bow", null,
                    List.of("&7Deck the halls with arrows.", "",
                            "&2&lHolly Arrows &7- targets you hit glow", "&7and are slowed.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.POWER, 5, Enchantment.PUNCH, 1, Enchantment.UNBREAKING, 3, Enchantment.INFINITY, 1));
            case "sleigh_launcher" -> it = build(id, Material.FIREWORK_ROCKET, "&c&lSleigh &6&lLauncher", null,
                    List.of("&7Santa's ride, pocket sized.", "",
                            "&c&lSleigh Blast &7- right-click to rocket forward",
                            "&7in a trail of fireworks. No fall damage", "&7while airborne.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.UNBREAKING, 1));
            case "north_star" -> it = build(id, Material.NETHER_STAR, "&e&lNorth Star", null,
                    List.of("&7The star on top of the tree,", "&7and it does not like being left out.", "",
                            "&e&lStarfall &7- right-click to rain stars on every", "&7enemy around you.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.UNBREAKING, 1));
            case "eggnog_flask" -> it = build(id, Material.HONEY_BOTTLE, "&f&lEggnog &6&lFlask", null,
                    List.of("&7Grandma's recipe. Do not ask what is in it.", "",
                            "&6&lHoliday Cheer &7- right-click for Strength,", "&7Regeneration and Absorption.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.UNBREAKING, 1));
            case "yule_log_axe" -> it = build(id, Material.NETHERITE_AXE, "&6&lYule Log &c&lAxe", null,
                    List.of("&7Cut from the biggest log in the fireplace.", "",
                            "&6&lYule Fire &7- hits can erupt in a burst of", "&7flame that ignites everything nearby.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.SHARPNESS, 5, Enchantment.FIRE_ASPECT, 2, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3));
            case "mistletoe_kiss" -> it = build(id, Material.SPORE_BLOSSOM, "&a&lMistletoe &c&lKiss", null,
                    List.of("&7Hold it up. Someone is getting hugged.", "",
                            "&a&lSeason of Love &7- right-click a player to heal", "&7both of you and share Regeneration.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.UNBREAKING, 1));
            case "ice_skates" -> it = build(id, Material.LEATHER_BOOTS, "&b&lIce Skates", Color.fromRGB(150, 220, 255),
                    List.of("&7Glide like you were born on a rink.", "",
                            "&b&lGlide &7- fast on ice, freezes water as you walk.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.FROST_WALKER, 2, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3));
            case "snowball_cannon" -> it = build(id, Material.DISPENSER, "&f&lSnowball &b&lCannon", null,
                    List.of("&7Pillow fights are for the weak.", "",
                            "&f&lPowder Burst &7- right-click to fire a rapid", "&7burst of hard-hitting snowballs.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.UNBREAKING, 1));
            case "frosty_coal" -> it = build(id, Material.COAL, "&f&lFrosty's &8&lMagic Coal", null,
                    List.of("&7Pop it on a snowman's face and watch.", "",
                            "&f&lFrosty Friends &7- right-click to summon snow", "&7golems that fight for you for a while.", "",
                            "&8Christmas Exclusive"),
                    ench(Enchantment.UNBREAKING, 1));
            default -> {
                return null;
            }
        }
        it.setAmount(Math.max(1, Math.min(64, amount)));
        return it;
    }
}
