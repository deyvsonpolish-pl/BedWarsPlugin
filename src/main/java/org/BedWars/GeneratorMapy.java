
package org.BedWars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class GeneratorMapy {
    private final BedWarsPlugin plugin;
    private final List<Location> diamondGenerators = new ArrayList();
    private final List<Location> emeraldGenerators = new ArrayList();
    private final Map<Location, ArmorStand> holograms = new HashMap();
    private final int diamondCooldown = 30;
    private final int emeraldCooldown = 60;
    private final Map<Location, Integer> diamondTimers = new HashMap();
    private final Map<Location, Integer> emeraldTimers = new HashMap();

    public GeneratorMapy(BedWarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void addDiamondGeneratorAtLocation(Location loc) {
        this.diamondGenerators.add(loc);
        this.addDiamondGeneratorPlaceholder(loc);
    }

    public void addEmeraldGeneratorAtLocation(Location loc) {
        this.emeraldGenerators.add(loc);
        this.addEmeraldGeneratorPlaceholder(loc);
    }

    public void addDiamondGeneratorPlaceholder(Location loc) {
        Location holoLoc = loc.clone().add((double)0.0F, (double)3.0F, (double)0.0F);
        if (!this.holograms.containsKey(holoLoc)) {
            this.createHologram(holoLoc, "§b\ud83d\udc8e Diament za 30s");
        }

        this.diamondTimers.put(loc, 30);
    }

    public void addEmeraldGeneratorPlaceholder(Location loc) {
        Location holoLoc = loc.clone().add((double)0.0F, (double)3.0F, (double)0.0F);
        if (!this.holograms.containsKey(holoLoc)) {
            this.createHologram(holoLoc, "§a\ud83d\udfe2 Emerald za 60s");
        }

        this.emeraldTimers.put(loc, 60);
    }

    public void resetTimers() {
        for(Location loc : this.diamondGenerators) {
            this.diamondTimers.put(loc, 30);
            ArmorStand holo = (ArmorStand)this.holograms.get(loc.clone().add((double)0.0F, (double)3.0F, (double)0.0F));
            if (holo != null) {
                holo.setCustomName("§b\ud83d\udc8e Diament za 30s");
            }
        }

        for(Location loc : this.emeraldGenerators) {
            this.emeraldTimers.put(loc, 60);
            ArmorStand holo = (ArmorStand)this.holograms.get(loc.clone().add((double)0.0F, (double)3.0F, (double)0.0F));
            if (holo != null) {
                holo.setCustomName("§a\ud83d\udfe2 Emerald za 60s");
            }
        }

    }

    public void createHologram(Location loc, String text) {
        ArmorStand stand = (ArmorStand)loc.getWorld().spawn(loc, ArmorStand.class, (as) -> {
            as.setVisible(false);
            as.setCustomNameVisible(true);
            as.setCustomName(text);
            as.setGravity(false);
            as.setMarker(true);
        });
        this.holograms.put(loc, stand);
    }

    public void start() {
        (new BukkitRunnable() {
            public void run() {
                GeneratorMapy.this.updateGenerators(GeneratorMapy.this.diamondGenerators, GeneratorMapy.this.diamondTimers, 30, Material.DIAMOND, "§b\ud83d\udc8e Diament za ");
                GeneratorMapy.this.updateGenerators(GeneratorMapy.this.emeraldGenerators, GeneratorMapy.this.emeraldTimers, 60, Material.EMERALD, "§a\ud83d\udfe2 Emerald za ");
            }
        }).runTaskTimer(this.plugin, 0L, 20L);
    }

    public void startWithPhase(final int phase) {
        (new BukkitRunnable() {
            public void run() {
                GeneratorMapy.this.updateGeneratorsWithPhase(phase);
            }
        }).runTaskTimer(this.plugin, 0L, 20L);
    }

    private void updateGenerators(List<Location> gens, Map<Location, Integer> timers, int cooldown, Material dropType, String prefix) {
        for(Location loc : gens) {
            int time = (Integer)timers.getOrDefault(loc, cooldown);
            ArmorStand holo = (ArmorStand)this.holograms.get(loc.clone().add((double)0.0F, (double)3.0F, (double)0.0F));
            if (time > 0) {
                --time;
                timers.put(loc, time);
                if (holo != null) {
                    holo.setCustomName(prefix + time + "s");
                }
            } else {
                Item item = loc.getWorld().dropItemNaturally(loc.clone().add((double)0.0F, (double)1.0F, (double)0.0F), new ItemStack(dropType, 1));
                item.setVelocity(item.getVelocity().multiply(0));
                timers.put(loc, cooldown);
                if (holo != null) {
                    holo.setCustomName(prefix + cooldown + "s");
                }
            }
        }

    }

    public void updateGeneratorsWithPhase(int phase) {
        this.updateGenerators(this.diamondGenerators, this.diamondTimers, Material.DIAMOND, "§b\ud83d\udc8e Diament za ", phase);
        this.updateGenerators(this.emeraldGenerators, this.emeraldTimers, Material.EMERALD, "§a\ud83d\udfe2 Emerald za ", phase);
    }

    private void updateGenerators(List<Location> gens, Map<Location, Integer> timers, Material dropType, String prefix, int phase) {
        int cooldown = this.getAdjustedCooldown(dropType, phase);

        for(Location loc : gens) {
            int time = (Integer)timers.getOrDefault(loc, cooldown);
            ArmorStand holo = (ArmorStand)this.holograms.get(loc.clone().add((double)0.0F, (double)3.0F, (double)0.0F));
            if (time > 0) {
                --time;
                timers.put(loc, time);
                if (holo != null) {
                    holo.setCustomName(prefix + "(Faza " + phase + ") " + time + "s");
                }
            } else {
                Item item = loc.getWorld().dropItemNaturally(loc.clone().add((double)0.0F, (double)1.0F, (double)0.0F), new ItemStack(dropType, 1));
                item.setVelocity(item.getVelocity().multiply(0));
                timers.put(loc, cooldown);
                if (holo != null) {
                    holo.setCustomName(prefix + "(Faza " + phase + ") " + cooldown + "s");
                }
            }
        }

    }

    private int getAdjustedCooldown(Material type, int phase) {
        int var10000;
        switch (phase) {
            case 1 -> var10000 = type == Material.DIAMOND ? 30 : 60;
            case 2 -> var10000 = type == Material.DIAMOND ? 20 : 40;
            case 3 -> var10000 = type == Material.DIAMOND ? 10 : 20;
            default -> var10000 = type == Material.DIAMOND ? 30 : 60;
        }

        return var10000;
    }

    public void stop() {
        for(ArmorStand as : this.holograms.values()) {
            as.remove();
        }

        this.holograms.clear();

        for(Location loc : this.diamondGenerators) {
            loc.getWorld().getNearbyEntities(loc, (double)2.0F, (double)2.0F, (double)2.0F).stream().filter((e) -> e instanceof Item).forEach((e) -> e.remove());
        }

        for(Location loc : this.emeraldGenerators) {
            loc.getWorld().getNearbyEntities(loc, (double)2.0F, (double)2.0F, (double)2.0F).stream().filter((e) -> e instanceof Item).forEach((e) -> e.remove());
        }

        this.diamondTimers.clear();
        this.emeraldTimers.clear();
    }

    public List<Location> getDiamondGenerators() {
        return this.diamondGenerators;
    }

    public List<Location> getEmeraldGenerators() {
        return this.emeraldGenerators;
    }

    public void clear() {
        this.diamondGenerators.clear();
        this.emeraldGenerators.clear();
        this.holograms.clear();
        this.diamondTimers.clear();
        this.emeraldTimers.clear();
    }
}
