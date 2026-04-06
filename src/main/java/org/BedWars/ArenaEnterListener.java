package org.BedWars;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class ArenaEnterListener implements Listener {

    private final BedWarsPlugin plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public ArenaEnterListener(BedWarsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private final Map<UUID, Boolean> wasInside = new HashMap<>();

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;

        Player p = e.getPlayer();

        BedWarsPlugin.Arena arena = plugin.getPlayerArena().get(p.getUniqueId());
        if (arena == null) return;

        if (!arena.hasEnterRegion()) return;

        boolean insideNow = isInside(arena.getEnterP1(), arena.getEnterP2(), e.getTo());
        boolean insideBefore = wasInside.getOrDefault(p.getUniqueId(), false);

        // zapisz stan na przyszłość
        wasInside.put(p.getUniqueId(), insideNow);

        // interesuje nas TYLKO moment wejścia
        if (insideBefore || !insideNow) return;

        // anty-spam
        long now = System.currentTimeMillis();
        long last = cooldown.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < 1000) return;
        cooldown.put(p.getUniqueId(), now);

        // ================= LOGIKA =================
        if (!arena.isInGame()) {
            if (arena.getLobby() != null) p.teleport(arena.getLobby());
            return;
        }

        // GRA W TOKU -> kill
        p.setHealth(0.0);
    }


    private boolean isInside(Location a, Location b, Location x) {
        if (a == null || b == null || x == null) return false;
        if (!a.getWorld().equals(x.getWorld())) return false;

        int minX = Math.min(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        int maxX = Math.max(a.getBlockX(), b.getBlockX());
        int maxY = Math.max(a.getBlockY(), b.getBlockY());
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ());

        int X = x.getBlockX();
        int Y = x.getBlockY();
        int Z = x.getBlockZ();

        return X >= minX && X <= maxX
                && Y >= minY && Y <= maxY
                && Z >= minZ && Z <= maxZ;
    }
}
