package org.BedWars;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MapRegionTool implements Listener {
    private final BedWarsPlugin plugin;
    private final Map<String, String> editingArena = new HashMap();

    public MapRegionTool(BedWarsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void giveSelectionTool(Player player, String arenaName) {
        ItemStack tool = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = tool.getItemMeta();
        String var10001 = String.valueOf(ChatColor.GOLD);
        meta.setDisplayName(var10001 + "\ud83e\ude84 Narzędzie do ustawiania terenu (" + arenaName + ")");
        tool.setItemMeta(meta);
        player.getInventory().addItem(new ItemStack[]{tool});
        this.editingArena.put(player.getName(), arenaName);
        var10001 = String.valueOf(ChatColor.YELLOW);
        player.sendMessage(var10001 + "➡ Tryb edycji terenu dla areny: " + String.valueOf(ChatColor.AQUA) + arenaName);
        player.sendMessage(String.valueOf(ChatColor.GRAY) + "Lewy klik = Punkt 1 | Prawy klik = Punkt 2");
    }

    @EventHandler
    public void onSelect(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (this.editingArena.containsKey(p.getName())) {
            if (e.getItem() != null && e.getItem().getType() == Material.BLAZE_ROD) {
                e.setCancelled(true);
                String var10000 = (String)this.editingArena.get(p.getName());
                if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null) {
                    this.plugin.getMapResetManager().setPos1(p);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.0F);
                }

                if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
                    this.plugin.getMapResetManager().setPos2(p);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.2F, 1.2F);
                }

            }
        }
    }

    public void saveSelection(Player player) {
        String name = player.getName();
        if (!this.editingArena.containsKey(name)) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "❌ Nie jesteś w trybie edycji!");
        } else {
            String arenaName = (String)this.editingArena.get(name);
            this.plugin.getMapResetManager().saveRegion(player, arenaName);
            this.editingArena.remove(name);
            String var10001 = String.valueOf(ChatColor.GREEN);
            player.sendMessage(var10001 + "\ud83d\udcbe Zapisano teren regeneracji dla areny " + String.valueOf(ChatColor.AQUA) + arenaName);
        }
    }
}
