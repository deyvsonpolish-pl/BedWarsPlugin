//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.BedWars;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class GeneratorDruzyny {
    private final BedWarsPlugin plugin;
    private final Map<String, Location> teamGeneratorLocations = new HashMap();
    private final int ironCooldown = 1001;
    private final int goldCooldown = 200;
    private final Map<String, Integer> ironCounters = new HashMap();
    private final Map<String, Integer> goldCounters = new HashMap();

    public GeneratorDruzyny(BedWarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void setGenerator(String teamId, Location loc) {
        this.teamGeneratorLocations.put(teamId, loc);
        this.ironCounters.put(teamId, 0);
        this.goldCounters.put(teamId, 0);
    }

    public void removeGenerator(String teamId) {
        this.teamGeneratorLocations.remove(teamId);
        this.ironCounters.remove(teamId);
        this.goldCounters.remove(teamId);
    }

    public void start() {
        (new BukkitRunnable() {
            public void run() {
                for(Map.Entry<String, Location> entry : GeneratorDruzyny.this.teamGeneratorLocations.entrySet()) {
                    String teamId = (String)entry.getKey();
                    Location loc = (Location)entry.getValue();
                    int iron = (Integer)GeneratorDruzyny.this.ironCounters.getOrDefault(teamId, 0) + 1;
                    if (iron >= 100) {
                        loc.getWorld().dropItemNaturally(loc.clone().add((double)0.5F, (double)1.0F, (double)0.5F), new ItemStack(Material.IRON_INGOT, 1));
                        iron = 0;
                    }

                    GeneratorDruzyny.this.ironCounters.put(teamId, iron);
                    int gold = (Integer)GeneratorDruzyny.this.goldCounters.getOrDefault(teamId, 0) + 1;
                    if (gold >= 200) {
                        loc.getWorld().dropItemNaturally(loc.clone().add((double)0.5F, (double)1.0F, (double)0.5F), new ItemStack(Material.GOLD_INGOT, 1));
                        gold = 0;
                    }

                    GeneratorDruzyny.this.goldCounters.put(teamId, gold);
                }

            }
        }).runTaskTimer(this.plugin, 0L, 1L);
    }
}
