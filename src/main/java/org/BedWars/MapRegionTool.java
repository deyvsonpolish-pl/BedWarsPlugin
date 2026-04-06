package org.BedWars;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
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

    // który gracz edytuje jaką arenę + jaki typ regionu
    private final Map<UUID, EditSession> sessions = new HashMap<>();

    // punkty dla ENTER (nie mieszamy z MapResetManager)
    private final Map<UUID, Location> enterP1 = new HashMap<>();
    private final Map<UUID, Location> enterP2 = new HashMap<>();

    private enum RegionType { REGEN, ENTER }

    private static class EditSession {
        final String arenaName;
        final RegionType type;
        EditSession(String arenaName, RegionType type) {
            this.arenaName = arenaName;
            this.type = type;
        }
    }

    public MapRegionTool(BedWarsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ====== REGEN (zostaje jak masz, Blaze Rod) ======
    public void giveSelectionTool(Player player, String arenaName) {
        ItemStack tool = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = tool.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "🦄 Narzędzie REGEN (" + arenaName + ")");
        tool.setItemMeta(meta);

        player.getInventory().addItem(tool);

        sessions.put(player.getUniqueId(), new EditSession(arenaName, RegionType.REGEN));

        player.sendMessage(ChatColor.YELLOW + "➡ Tryb edycji REGEN dla areny: " + ChatColor.AQUA + arenaName);
        player.sendMessage(ChatColor.GRAY + "Lewy klik = Punkt 1 | Prawy klik = Punkt 2");
    }

    public void saveSelection(Player player) {
        EditSession s = sessions.get(player.getUniqueId());
        if (s == null || s.type != RegionType.REGEN) {
            player.sendMessage(ChatColor.RED + "❌ Nie edytujesz terenu REGEN!");
            return;
        }
        plugin.getMapResetManager().saveRegion(player, s.arenaName);
        sessions.remove(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "💾 Zapisano teren REGEN dla areny " + ChatColor.AQUA + s.arenaName);
    }

    // ====== ENTER (NOWE – IRON_AXE) ======
    public void giveMapEnterSelectionTool(Player player, String arenaName) {
        ItemStack tool = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = tool.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "🪓 Narzędzie ENTER (" + arenaName + ")");
        tool.setItemMeta(meta);

        player.getInventory().addItem(tool);

        sessions.put(player.getUniqueId(), new EditSession(arenaName, RegionType.ENTER));

        // wyczyść stare punkty ENTER
        enterP1.remove(player.getUniqueId());
        enterP2.remove(player.getUniqueId());

        player.sendMessage(ChatColor.YELLOW + "➡ Tryb edycji ENTER dla areny: " + ChatColor.AQUA + arenaName);
        player.sendMessage(ChatColor.GRAY + "Lewy klik = Punkt 1 | Prawy klik = Punkt 2");
    }

    public void saveMapEnterSelection(Player player) {
        EditSession s = sessions.get(player.getUniqueId());
        if (s == null || s.type != RegionType.ENTER) {
            player.sendMessage(ChatColor.RED + "❌ Nie edytujesz terenu ENTER!");
            return;
        }

        Location p1 = enterP1.get(player.getUniqueId());
        Location p2 = enterP2.get(player.getUniqueId());

        if (p1 == null || p2 == null) {
            player.sendMessage(ChatColor.RED + "❌ Musisz ustawić oba punkty ENTER (LPM i PPM).");
            return;
        }

        BedWarsPlugin.Arena arena = plugin.getArenaManager().getArena(s.arenaName);
        if (arena == null) {
            player.sendMessage(ChatColor.RED + "❌ Nie ma areny: " + s.arenaName);
            return;
        }

        arena.setEnterP1(p1);
        arena.setEnterP2(p2);
        plugin.getArenaManager().saveArenas();

        sessions.remove(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + "💾 Zapisano teren ENTER dla areny " + ChatColor.AQUA + arena.getName());
    }

    // ====== CLICK HANDLER ======
    @EventHandler
    public void onSelect(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        EditSession s = sessions.get(p.getUniqueId());
        if (s == null) return;

        ItemStack item = e.getItem();
        if (item == null) return;

        // sprawdź czy trzyma właściwe narzędzie
        if (s.type == RegionType.REGEN && item.getType() != Material.BLAZE_ROD) return;
        if (s.type == RegionType.ENTER && item.getType() != Material.IRON_AXE) return;

        if (e.getClickedBlock() == null) return;

        Action a = e.getAction();
        if (a != Action.LEFT_CLICK_BLOCK && a != Action.RIGHT_CLICK_BLOCK) return;

        e.setCancelled(true);

        if (s.type == RegionType.REGEN) {
            if (a == Action.LEFT_CLICK_BLOCK) {
                plugin.getMapResetManager().setPos1(p);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            } else {
                plugin.getMapResetManager().setPos2(p);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            }
            return;
        }

        // ENTER zapisujemy lokalnie
        Location loc = e.getClickedBlock().getLocation();
        if (a == Action.LEFT_CLICK_BLOCK) {
            enterP1.put(p.getUniqueId(), loc);
            p.sendMessage(ChatColor.GREEN + "Punkt 1 (ENTER): " + ChatColor.WHITE +
                    loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        } else {
            enterP2.put(p.getUniqueId(), loc);
            p.sendMessage(ChatColor.GREEN + "Punkt 2 (ENTER): " + ChatColor.WHITE +
                    loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        }
    }
}
