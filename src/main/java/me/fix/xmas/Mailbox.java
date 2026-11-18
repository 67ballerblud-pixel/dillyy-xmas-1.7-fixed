package me.fix.xmas;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Craftable Christmas Mailbox. A placed mailbox is a BARREL whose tile entity carries a marker in its
 * persistent data. It never holds items itself: right-clicking opens the mail menus (see MailGui), and
 * mail is stored per player, so every mailbox is simply an access point to the same network.
 */
public final class Mailbox implements Listener {

    private final Xmas plugin;
    final MailStore store;
    private final MailGui gui;
    private final NamespacedKey blockKey;
    private final NamespacedKey itemKey;
    private final NamespacedKey recipeKey;
    private boolean recipeActive;

    Mailbox(Xmas plugin) {
        this.plugin = plugin;
        this.store = new MailStore(plugin);
        this.gui = new MailGui(plugin, store);
        this.blockKey = new NamespacedKey(plugin, "mailbox_block");
        this.itemKey = new NamespacedKey(plugin, "mailbox_item");
        this.recipeKey = new NamespacedKey(plugin, "mailbox");
    }

    void start() {
        store.load();
        registerRecipe();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(gui, plugin);
    }

    void shutdown() {
        store.shutdown();
    }

    void reload() {
        registerRecipe();
        if (recipeActive) {
            for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipe(recipeKey);
        }
    }

    // ------------------------------------------------------------------ item + recipe

    ItemStack item(int amount) {
        ItemStack it = new ItemStack(Material.BARREL, Math.max(1, Math.min(64, amount)));
        ItemMeta m = it.getItemMeta();
        m.displayName(Xmas.line("&c&lChristmas &a&lMailbox"));
        m.lore(List.of(
                Xmas.line("&7Place it down, then right-click it to"),
                Xmas.line("&7mail a gift to anyone who has joined."),
                Xmas.line(""),
                Xmas.line("&8Christmas Exclusive")));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        recipeActive = false;
        if (!plugin.getConfig().getBoolean("mailbox.enabled", true)) return;

        List<String> shape = plugin.getConfig().getStringList("mailbox.recipe.shape");
        Map<Character, Material> ing = new HashMap<>();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("mailbox.recipe.ingredients");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                Material m = Material.matchMaterial(String.valueOf(s.getString(k)));
                if (k.length() == 1 && m != null && !m.isAir()) ing.put(k.charAt(0), m);
            }
        }
        if (!tryRecipe(shape, ing)) {
            plugin.getLogger().warning("Invalid mailbox recipe in config.yml - using the default recipe.");
            tryRecipe(List.of("IDI", "IBI", "IPI"), Map.of(
                    'I', Material.IRON_INGOT, 'D', Material.RED_DYE, 'B', Material.BARREL, 'P', Material.PAPER));
        }
    }

    private boolean tryRecipe(List<String> shape, Map<Character, Material> ing) {
        try {
            if (shape.isEmpty() || shape.size() > 3) return false;
            int w = shape.get(0).length();
            if (w < 1 || w > 3) return false;
            for (String row : shape) if (row.length() != w) return false;

            Set<Character> used = new HashSet<>();
            for (String row : shape) for (char ch : row.toCharArray()) if (ch != ' ') used.add(ch);
            if (used.isEmpty()) return false;

            ShapedRecipe r = new ShapedRecipe(recipeKey, item(1));
            r.shape(shape.toArray(new String[0]));
            for (char ch : used) {
                Material m = ing.get(ch);
                if (m == null) return false;
                r.setIngredient(ch, m);
            }
            recipeActive = Bukkit.addRecipe(r);
            return recipeActive;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ block identification

    boolean isMailbox(Block b) {
        if (b == null || b.getType() != Material.BARREL) return false;
        BlockState st = b.getState(false);
        return st instanceof TileState ts && ts.getPersistentDataContainer().has(blockKey, PersistentDataType.BYTE);
    }

    private boolean isMailboxInventory(Inventory inv) {
        if (inv == null || inv.getType() != InventoryType.BARREL) return false;
        Location l = inv.getLocation();
        return l != null && isMailbox(l.getBlock());
    }

    // ------------------------------------------------------------------ events

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        ItemStack hand = e.getItemInHand();
        if (!hand.hasItemMeta()) return;
        if (!hand.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) return;
        BlockState st = e.getBlockPlaced().getState();
        if (st instanceof TileState ts) {
            ts.getPersistentDataContainer().set(blockKey, PersistentDataType.BYTE, (byte) 1);
            ts.update(true, false);
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null || !isMailbox(b)) return;
        Player p = e.getPlayer();
        // sneak + holding something = the player is trying to build against it
        if (p.isSneaking() && p.getInventory().getItemInMainHand().getType() != Material.AIR) return;
        e.setCancelled(true);
        if (!p.hasPermission("xmas.mailbox.use")) {
            plugin.msg(p, "&cYou don't have permission to use mailboxes.");
            return;
        }
        gui.openMain(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!isMailbox(b)) return;
        e.setDropItems(false);
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item(1));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::isMailbox);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::isMailbox);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (isMailbox(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (isMailbox(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /** A mailbox must never become a real storage barrel (hoppers, other plugins, ...). */
    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        if (isMailboxInventory(e.getDestination()) || isMailboxInventory(e.getSource())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (isMailboxInventory(e.getInventory())) e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        store.remember(p.getUniqueId(), p.getName());
        if (recipeActive && plugin.getConfig().getBoolean("mailbox.discover-recipe-on-join", true)) {
            p.discoverRecipe(recipeKey);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            int n = store.count(p.getUniqueId());
            if (n > 0) {
                plugin.msg(p, "&aYou have &f" + n + " &agift" + (n == 1 ? "" : "s")
                        + " waiting! Right-click any &cChristmas Mailbox &ato collect.");
            }
        }, 60L);
    }
}
