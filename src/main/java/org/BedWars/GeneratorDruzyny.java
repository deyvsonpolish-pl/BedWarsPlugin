package org.BedWars;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class GeneratorDruzyny {

    private final BedWarsPlugin plugin;

    // teamId -> location
    private final Map<String, Location> teamGeneratorLocations = new HashMap<>();

    // cooldowny w tickach (task chodzi co 1 tick)
    private final int ironCooldown = 50; // 100 ticków = 5s
    private final int goldCooldown = 100; // 200 ticków = 10s

    private final Map<String, Integer> ironCounters = new HashMap<>();
    private final Map<String, Integer> goldCounters = new HashMap<>();

    private BukkitTask task;

    public GeneratorDruzyny(BedWarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void setGenerator(String teamId, Location loc) {
        if (teamId == null || loc == null || loc.getWorld() == null) return;

        // trzymamy kopię, żeby ktoś nie zmodyfikował referencji
        teamGeneratorLocations.put(teamId, loc.clone());
        ironCounters.put(teamId, 0);
        goldCounters.put(teamId, 0);
    }

    public void removeGenerator(String teamId) {
        teamGeneratorLocations.remove(teamId);
        ironCounters.remove(teamId);
        goldCounters.remove(teamId);
    }

    public void start() {
        stop(); // nie odpalaj 2 tasków naraz

        this.task = new BukkitRunnable() {
            @Override
            public void run() {

                for (Map.Entry<String, Location> entry : teamGeneratorLocations.entrySet()) {
                    String teamId = entry.getKey();
                    Location loc = entry.getValue();
                    if (loc == null) continue;

                    World w = loc.getWorld();
                    if (w == null) continue;

                    // IRON
                    int iron = ironCounters.getOrDefault(teamId, 0) + 1;
                    if (iron >= ironCooldown) {
                        spawnDrop(loc, new ItemStack(Material.IRON_INGOT, 1));
                        iron = 0;
                    }
                    ironCounters.put(teamId, iron);

                    // GOLD
                    int gold = goldCounters.getOrDefault(teamId, 0) + 1;
                    if (gold >= goldCooldown) {
                        spawnDrop(loc, new ItemStack(Material.GOLD_INGOT, 1));
                        gold = 0;
                    }
                    goldCounters.put(teamId, gold);
                }
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    /**
     * Spawn w stałym miejscu bez losowego wyrzutu (fix na "obok" i wchodzenie w bloki).
     */
    private void spawnDrop(Location base, ItemStack stack) {
        if (base == null || stack == null) return;
        if (base.getWorld() == null) return;

        // środek bloku generatora + 1 blok w górę (możesz zmienić na 1.2 jeśli masz niski sufit/slaby)
        Location dropLoc = base.getBlock().getLocation().add(0.5, 1.0, 0.5);

        Item item = base.getWorld().dropItem(dropLoc, stack);
        item.setVelocity(item.getVelocity().multiply(0)); // brak losowej prędkości (najważniejsze)
        item.setPickupDelay(10); // opcjonalnie
    }
}