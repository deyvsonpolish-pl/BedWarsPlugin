package org.BedWars;

import java.util.*;
import java.util.function.IntSupplier;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class GeneratorMapy {

    private final BedWarsPlugin plugin;

    private final List<Location> diamondGenerators = new ArrayList<>();
    private final List<Location> emeraldGenerators = new ArrayList<>();

    // ✅ STABILNY KLUCZ, NIE Location (bo yaw/pitch/double robią bugi)
    private final Map<String, ArmorStand> holograms = new HashMap<>();

    private final Map<String, Integer> diamondTimers = new HashMap<>();
    private final Map<String, Integer> emeraldTimers = new HashMap<>();

    private BukkitTask task;
    private IntSupplier phaseSupplier;

    private final NamespacedKey HOLO_KEY;

    public GeneratorMapy(BedWarsPlugin plugin) {
        this.plugin = plugin;
        this.HOLO_KEY = new NamespacedKey(plugin, "bw_mapgen_holo");
    }

    // =========================================================
    // PUBLIC API
    // =========================================================

    // EDYCJA / GUI (ma od razu pokazać fazę 1)
    public void addDiamondGeneratorAtLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        if (!containsLoc(diamondGenerators, loc)) diamondGenerators.add(loc.clone());

        String k = key(loc);
        ensureHologram(loc);
        diamondTimers.put(k, 30);
        setHoloText(loc, "§b💎 Diament za (Faza 1) 30s");
    }

    public void addEmeraldGeneratorAtLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        if (!containsLoc(emeraldGenerators, loc)) emeraldGenerators.add(loc.clone());

        String k = key(loc);
        ensureHologram(loc);
        emeraldTimers.put(k, 60);
        setHoloText(loc, "§a🟢 Emerald za (Faza 1) 60s");
    }

    // START gry (odliczanie działa tylko w trakcie gry)
    public void startWithPhase(IntSupplier phaseSupplier) {
        stopTaskOnly();
        this.phaseSupplier = phaseSupplier;

        this.task = new BukkitRunnable() {
            @Override public void run() {
                int phase = (GeneratorMapy.this.phaseSupplier != null)
                        ? GeneratorMapy.this.phaseSupplier.getAsInt()
                        : 1;
                updateGeneratorsWithPhase(phase);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // STOP tylko task
    public void stopTaskOnly() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ENDGAME: NIE USUWAJ HOLOGRAMÓW “logicznie” (ale reset mapy może je skasować)
    // więc: stop task + usuń itemy + ustaw teksty fazy 1 jeśli jeszcze stoją
    public void stopForEndGameReset() {
        stopTaskOnly();
        removeNearbyDroppedItems();
        resetToPhase(1); // jeśli hologramy jeszcze są, to dostaną fazę 1
    }

    // TYLKO gdy klikasz "Usuń wszystkie generatory"
    public void clear() {
        stopAndRemoveEverything();
        diamondGenerators.clear();
        emeraldGenerators.clear();
        diamondTimers.clear();
        emeraldTimers.clear();
    }

    // użyj gdy naprawdę chcesz usunąć armorstandy
    public void stopAndRemoveEverything() {
        stopTaskOnly();
        removeAllHolograms();
        removeNearbyDroppedItems();
    }

    // ✅ NAJWAŻNIEJSZE: wywołuj po resecie mapy / po przebudowie
    public void rebuildHolograms(int phase) {
        // wyczyść cache (bo armorstandy mogły zniknąć)
        holograms.clear();

        int dCd = getAdjustedCooldown(Material.DIAMOND, phase);
        int eCd = getAdjustedCooldown(Material.EMERALD, phase);

        for (Location loc : diamondGenerators) {
            if (loc == null || loc.getWorld() == null) continue;
            ensureHologram(loc);
            diamondTimers.put(key(loc), dCd);
            setHoloText(loc, "§b💎 Diament za (Faza " + phase + ") " + dCd + "s");
        }
        for (Location loc : emeraldGenerators) {
            if (loc == null || loc.getWorld() == null) continue;
            ensureHologram(loc);
            emeraldTimers.put(key(loc), eCd);
            setHoloText(loc, "§a🟢 Emerald za (Faza " + phase + ") " + eCd + "s");
        }
    }

    public void resetToPhase(int phase) {
        int dCd = getAdjustedCooldown(Material.DIAMOND, phase);
        int eCd = getAdjustedCooldown(Material.EMERALD, phase);

        for (Location loc : diamondGenerators) {
            if (loc == null || loc.getWorld() == null) continue;
            ensureHologram(loc);
            diamondTimers.put(key(loc), dCd);
            setHoloText(loc, "§b💎 Diament za (Faza " + phase + ") " + dCd + "s");
        }
        for (Location loc : emeraldGenerators) {
            if (loc == null || loc.getWorld() == null) continue;
            ensureHologram(loc);
            emeraldTimers.put(key(loc), eCd);
            setHoloText(loc, "§a🟢 Emerald za (Faza " + phase + ") " + eCd + "s");
        }
    }

    public List<Location> getDiamondGenerators() { return diamondGenerators; }
    public int getNextDiamondInSeconds(int phase) {
        return getMinTimer(diamondGenerators, diamondTimers, Material.DIAMOND, phase);
    }

    public int getNextEmeraldInSeconds(int phase) {
        return getMinTimer(emeraldGenerators, emeraldTimers, Material.EMERALD, phase);
    }

    private int getMinTimer(List<Location> gens, Map<String, Integer> timers, Material type, int phase) {
        int cooldown = getAdjustedCooldown(type, phase);

        if (gens == null || gens.isEmpty()) return cooldown; // brak generatorów -> pokaż cooldown fazy

        int min = Integer.MAX_VALUE;

        for (Location loc : gens) {
            if (loc == null || loc.getWorld() == null) continue;
            String k = key(loc);
            int t = timers.getOrDefault(k, cooldown);
            if (t < min) min = t;
        }

        // jeśli wszystko było null/niepoprawne
        if (min == Integer.MAX_VALUE) min = cooldown;

        return Math.max(0, min);
    }
    public List<Location> getEmeraldGenerators() { return emeraldGenerators; }

    // BACKWARD COMPAT (loadArenas) - TYLKO dodaje lokację, nic nie spawnuje
    public void addDiamondGeneratorPlaceholder(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        if (!containsLoc(diamondGenerators, loc)) diamondGenerators.add(loc.clone());
        diamondTimers.put(key(loc), 30);
    }

    public void addEmeraldGeneratorPlaceholder(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        if (!containsLoc(emeraldGenerators, loc)) emeraldGenerators.add(loc.clone());
        emeraldTimers.put(key(loc), 60);
    }

    // =========================================================
    // UPDATE LOOP
    // =========================================================

    private void updateGeneratorsWithPhase(int phase) {
        updateOneType(diamondGenerators, diamondTimers, Material.DIAMOND, "§b💎 Diament za ", phase);
        updateOneType(emeraldGenerators, emeraldTimers, Material.EMERALD, "§a🟢 Emerald za ", phase);
    }

    private void updateOneType(List<Location> gens,
                               Map<String, Integer> timers,
                               Material dropType,
                               String prefix,
                               int phase) {

        int cooldown = getAdjustedCooldown(dropType, phase);

        for (Location loc : gens) {
            if (loc == null || loc.getWorld() == null) continue;

            ensureHologram(loc);

            String k = key(loc);
            int time = timers.getOrDefault(k, cooldown);

            if (time > 0) {
                time--;
                timers.put(k, time);
                setHoloText(loc, prefix + "(Faza " + phase + ") " + time + "s");
            } else {
                Item item = loc.getWorld().dropItemNaturally(loc.clone().add(0, 1, 0), new ItemStack(dropType, 1));
                item.setVelocity(item.getVelocity().multiply(0));
                timers.put(k, cooldown);
                setHoloText(loc, prefix + "(Faza " + phase + ") " + cooldown + "s");
            }
        }
    }

    private int getAdjustedCooldown(Material type, int phase) {
        return switch (phase) {
            case 1 -> (type == Material.DIAMOND) ? 30 : 60;
            case 2 -> (type == Material.DIAMOND) ? 20 : 40;
            case 3 -> (type == Material.DIAMOND) ? 10 : 20;
            default -> (type == Material.DIAMOND) ? 30 : 60;
        };
    }

    // =========================================================
    // HOLOGRAMS
    // =========================================================

    // holo = gen + 0,3,0 (zawsze yaw/pitch = 0)
    private Location holoLoc(Location genLoc) {
        return new Location(
                genLoc.getWorld(),
                round2(genLoc.getX()),
                round2(genLoc.getY() + 3.0),
                round2(genLoc.getZ()),
                0f,
                0f
        );
    }

    private void ensureHologram(Location genLoc) {
        String k = key(genLoc);

        ArmorStand cached = holograms.get(k);
        if (cached != null && cached.isValid() && !cached.isDead()) return;

        Location hLoc = holoLoc(genLoc);

        // ✅ Zbierz wszystkie podejrzane hologramy w pobliżu
        List<ArmorStand> found = new ArrayList<>();
        for (Entity e : hLoc.getWorld().getNearbyEntities(hLoc, 0.8, 0.8, 0.8)) {
            if (e instanceof ArmorStand as) {
                // 1) nasz nowy system: PDC
                Byte mark = as.getPersistentDataContainer().get(HOLO_KEY, PersistentDataType.BYTE);

                // 2) fallback na stare wersje: po nazwie (żeby też posprzątać stare hologramy)
                String name = as.getCustomName();
                boolean legacy = name != null && (name.contains("Diament za") || name.contains("Emerald za") || name.contains("Faza"));

                if ((mark != null && mark == (byte) 1) || legacy) {
                    found.add(as);
                }
            }
        }

        if (!found.isEmpty()) {
            // ✅ Zostaw jeden (najbliższy), resztę usuń
            found.sort(Comparator.comparingDouble(a -> a.getLocation().distanceSquared(hLoc)));
            ArmorStand keep = found.get(0);

            for (int i = 1; i < found.size(); i++) {
                ArmorStand extra = found.get(i);
                if (extra.isValid() && !extra.isDead()) extra.remove();
            }

            // upewnij się, że "keep" jest poprawnie skonfigurowany i oznaczony
            keep.setVisible(false);
            keep.setCustomNameVisible(true);
            keep.setGravity(false);
            keep.setMarker(true);
            keep.setSmall(true);
            keep.setBasePlate(false);
            keep.setCollidable(false);
            keep.setInvulnerable(true);
            keep.setSilent(true);
            keep.getPersistentDataContainer().set(HOLO_KEY, PersistentDataType.BYTE, (byte) 1);

            holograms.put(k, keep);
            return;
        }

        // ✅ Jak nic nie znaleziono — spawnuj nowy
        ArmorStand stand = hLoc.getWorld().spawn(hLoc, ArmorStand.class, a -> {
            a.setVisible(false);
            a.setCustomNameVisible(true);
            a.setGravity(false);
            a.setMarker(true);
            a.setSmall(true);
            a.setBasePlate(false);
            a.setCollidable(false);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.getPersistentDataContainer().set(HOLO_KEY, PersistentDataType.BYTE, (byte) 1);
            a.setCustomName("§7...");
        });

        holograms.put(k, stand);
    }
    private void setHoloText(Location genLoc, String text) {
        String k = key(genLoc);
        ArmorStand as = holograms.get(k);
        if (as == null || as.isDead() || !as.isValid()) {
            // self-heal
            ensureHologram(genLoc);
            as = holograms.get(k);
        }
        if (as != null && as.isValid() && !as.isDead()) {
            as.setCustomName(text);
            as.setCustomNameVisible(true);
        }
    }

    private void removeAllHolograms() {
        for (ArmorStand as : holograms.values()) {
            if (as != null && as.isValid() && !as.isDead()) as.remove();
        }
        holograms.clear();
    }

    private void removeNearbyDroppedItems() {
        for (Location loc : diamondGenerators) {
            if (loc == null || loc.getWorld() == null) continue;
            loc.getWorld().getNearbyEntities(loc, 2, 2, 2).stream()
                    .filter(e -> e instanceof Item)
                    .forEach(Entity::remove);
        }
        for (Location loc : emeraldGenerators) {
            if (loc == null || loc.getWorld() == null) continue;
            loc.getWorld().getNearbyEntities(loc, 2, 2, 2).stream()
                    .filter(e -> e instanceof Item)
                    .forEach(Entity::remove);
        }
    }

    // =========================================================
    // UTILS
    // =========================================================

    private String key(Location loc) {
        return loc.getWorld().getName() + "|"
                + round2(loc.getX()) + "|"
                + round2(loc.getY()) + "|"
                + round2(loc.getZ());
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private boolean containsLoc(List<Location> list, Location loc) {
        for (Location l : list) {
            if (l == null || l.getWorld() == null || loc.getWorld() == null) continue;
            if (!l.getWorld().equals(loc.getWorld())) continue;
            if (l.distanceSquared(loc) < 0.0001) return true;
        }
        return false;
    }
}