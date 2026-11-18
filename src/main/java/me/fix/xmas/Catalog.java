package me.fix.xmas;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

/** /xmas catalog: a browsable menu of every Christmas item and its ability. Admins can click to take one. */
public class Catalog implements Listener {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    void open(Player p) {
        Inventory inv = Bukkit.createInventory(new Holder(), 54, Xmas.c("&c&l❄ &a&lChristmas Catalog &c&l❄"));
        for (int i = 0; i < 54; i++) {
            Material m = (i % 2 == 0) ? Material.RED_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE;
            ItemStack pane = new ItemStack(m);
            ItemMeta meta = pane.getItemMeta();
            meta.displayName(Component.empty());
            pane.setItemMeta(meta);
            inv.setItem(i, pane);
        }
        boolean admin = p.hasPermission("xmas.admin");
        for (int i = 0; i < Items.IDS.size() && i < SLOTS.length; i++) {
            ItemStack it = Items.create(Items.IDS.get(i), 1);
            if (it == null) continue;
            if (admin) {
                ItemMeta meta = it.getItemMeta();
                List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
                lore.add(Component.empty());
                lore.add(Xmas.line("&eAdmin: click to take one"));
                meta.lore(lore);
                it.setItemMeta(meta);
            }
            inv.setItem(SLOTS[i], it);
        }
        p.openInventory(inv);
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !p.hasPermission("xmas.admin")) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        String id = Items.idOf(e.getCurrentItem());
        if (id == null) return;
        ItemStack give = Items.create(id, 1);
        if (give == null) return;
        for (ItemStack left : p.getInventory().addItem(give).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
