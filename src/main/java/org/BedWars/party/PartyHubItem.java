package org.BedWars.party;

import org.BedWars.BedWarsPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class PartyHubItem implements Listener {

    private final JavaPlugin plugin;

    // ====== KONFIG ======
    private static final String HUB_WORLD = "BWHub"; // dopasuj
    private static final int PARTY_SLOT = 3;

    // ====== GUI ======
    private static final String PARTY_MAIN_TITLE = "§b👥 Party";
    private static final int PARTY_MAIN_SIZE = 27;

    // ====== PDC ======
    private final NamespacedKey KEY_PARTY_ITEM;
    private final NamespacedKey KEY_PARTY_GUI;
    private final NamespacedKey KEY_PARTY_BTN;

    public PartyHubItem(JavaPlugin plugin) {
        this.plugin = plugin;
        this.KEY_PARTY_ITEM = new NamespacedKey(plugin, "bw_party_item");
        this.KEY_PARTY_GUI = new NamespacedKey(plugin, "bw_party_gui");
        this.KEY_PARTY_BTN = new NamespacedKey(plugin, "bw_party_btn");
    }

    // =========================
    // ENABLE / DISABLE
    // =========================
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isInHub(p) && !isInArena(p)) givePartyItem(p);
        }
    }

    public void disable() {
        HandlerList.unregisterAll(this);
    }

    // =========================
    // GIVE ITEM (JOIN/WORLD/RESPAWN)
    // =========================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = e.getPlayer();
            if (isInHub(p) && !isInArena(p)) givePartyItem(p);
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = e.getPlayer();
            if (isInHub(p) && !isInArena(p)) givePartyItem(p);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = e.getPlayer();
            if (isInHub(p) && !isInArena(p)) givePartyItem(p);
        }, 5L);
    }

    private void givePartyItem(Player p) {
        p.getInventory().setItem(PARTY_SLOT, createPartyItem());
    }

    private ItemStack createPartyItem() {
        ItemStack it = new ItemStack(Material.FIREWORK_STAR);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b👥 Party");
            meta.setLore(Arrays.asList(
                    "§7Zarządzaj drużyną",
                    "§8Kliknij aby otworzyć"
            ));
            meta.getPersistentDataContainer().set(KEY_PARTY_ITEM, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(meta);
        }
        return it;
    }

    private boolean isPartyItem(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        Byte b = meta.getPersistentDataContainer().get(KEY_PARTY_ITEM, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    // =========================
    // BLOKADY PRZENOSZENIA / DROP
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!isInHub(p) || isInArena(p)) return;

        if (isPartyItem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!isInHub(p) || isInArena(p)) return;

        if (isPartyItem(e.getMainHandItem()) || isPartyItem(e.getOffHandItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isInHub(p) || isInArena(p)) return;

        if (isPartyItem(e.getOldCursor())) {
            e.setCancelled(true);
            return;
        }

        if (isPartyGui(e.getView())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        // blok przenoszenia party itemu w hubie
        if (isInHub(p) && !isInArena(p)) {
            ItemStack current = e.getCurrentItem();
            ItemStack cursor = e.getCursor();

            if (isPartyItem(current) || isPartyItem(cursor)) {
                e.setCancelled(true);
                return;
            }
        }

        // klik w GUI party
        if (isPartyGui(e.getView())) {
            e.setCancelled(true);

            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType() == Material.AIR) return;

            String btn = getBtn(it);
            if (btn == null) return;

            switch (btn) {
                case "close" -> p.closeInventory();
                case "create" -> {
                    p.sendMessage("§a[Party] §7(TODO) Tworzenie party w GUI.");
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                }
                case "invite" -> {
                    p.sendMessage("§a[Party] §7(TODO) Wybór gracza do zaproszenia (GUI).");
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                }
                case "leave" -> {
                    p.sendMessage("§c[Party] §7(TODO) Wyjście z party.");
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.0f);
                }
            }
        }
    }

    // =========================
    // OTWIERANIE GUI: klik item
    // =========================
    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isInHub(p) || isInArena(p)) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        ItemStack it = e.getItem();
        if (!isPartyItem(it)) return;

        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK
                || a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);
            if (plugin instanceof BedWarsPlugin bw) {
                bw.getPartySystem().openPartyMain(p);
            }
        }
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (!isInHub(p) || isInArena(p)) return;

        ItemStack it = p.getInventory().getItemInMainHand();
        if (!isPartyItem(it)) return;

        e.setCancelled(true);
    }

    // =========================
    // GUI: PARTY MAIN (placeholder)
    // =========================
    // =========================
    // GUI helpers
    // =========================
    private ItemStack glass(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack icon(Material mat, String name, List<String> lore, String btn) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            meta.getPersistentDataContainer().set(KEY_PARTY_BTN, PersistentDataType.STRING, btn);
            it.setItemMeta(meta);
        }
        return it;
    }

    private String getBtn(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(KEY_PARTY_BTN, PersistentDataType.STRING);
    }

    private void stampPartyGui(Inventory inv) {
        ItemStack marker = inv.getItem(0);
        if (marker == null || marker.getType() == Material.AIR) marker = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);

        ItemMeta meta = marker.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.getPersistentDataContainer().set(KEY_PARTY_GUI, PersistentDataType.BYTE, (byte) 1);
            marker.setItemMeta(meta);
        }
        inv.setItem(0, marker);
    }

    private boolean isPartyGui(InventoryView view) {
        // prosto: po tytule
        String t = ChatColor.stripColor(view.getTitle());
        return t.equals(ChatColor.stripColor(PARTY_MAIN_TITLE));
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> syncPartyItem(e.getPlayer()), 1L);
    }
    private void syncPartyItem(Player p) {
        if (isInHub(p) && !isInArena(p)) {
            p.getInventory().setItem(PARTY_SLOT, createPartyItem());
        } else {
            removePartyItem(p);
        }
    }
    private void removePartyItem(Player p) {
        PlayerInventory inv = p.getInventory();

        // usuń ze slota
        ItemStack slot = inv.getItem(PARTY_SLOT);
        if (isPartyItem(slot)) inv.setItem(PARTY_SLOT, null);

        // na wszelki wypadek usuń z całego eq
        for (int i = 0; i < inv.getSize(); i++) {
            if (isPartyItem(inv.getItem(i))) inv.setItem(i, null);
        }
    }

    // =========================
    // HUB / ARENA
    // =========================
    private boolean isInHub(Player p) {
        return p.getWorld() != null && p.getWorld().getName().equalsIgnoreCase(HUB_WORLD);
    }

    private boolean isInArena(Player p) {
        if (plugin instanceof BedWarsPlugin bw) {
            return bw.getPlayerArena().containsKey(p.getUniqueId());
        }
        return false;
    }
}
