package org.BedWars;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import java.io.File;
import java.util.*;

public class RankedHologramManager implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final RankedSystem ranked;

    // hologram.yml
    private final File holoFile;
    private YamlConfiguration yml;

    private BukkitTask task;

    // spawned entities (IDs do usuwania awaryjnego)
    private final List<UUID> spawned = new ArrayList<>();
    private static final String HOLO_TAG = "bwholo_ranked";

    // references do entity (żeby update bez migania)
    private TextDisplay lineHeader1;
    private TextDisplay lineHeader2;
    private TextDisplay lineHeader3;
    private TextDisplay lineEvent;
    private TextDisplay lineSep;
    private TextDisplay lineTopTitle;

    private final ItemDisplay[] topHeads = new ItemDisplay[5];
    private final TextDisplay[] topName = new TextDisplay[5];
    private final TextDisplay[] topInfo = new TextDisplay[5];

    // sygnatura topki (żeby nie ruszać jeśli się nie zmieniło)
    private List<String> lastTopSig = new ArrayList<>();
    // sygnatura eventu (żeby update czasu działał)
    private String lastEventSig = "";

    // ====== WYGLĄD / ODSTĘPY ======
    private static final double HEADER_Y = 2.85;
    private static final double LINE_GAP = 0.32;
    private static final double BLOCK_GAP = 0.60;

    private static final double HEAD_Y = 0.65;
    private static final double HEAD_SCALE = 1.25;
    private static final double HEAD_GAP = 2.05;

    private static final double TOP_TEXT_1 = 0.72;
    private static final double TOP_TEXT_2 = 0.16;

    public RankedHologramManager(JavaPlugin plugin, RankedSystem ranked) {
        this.plugin = plugin;
        this.ranked = ranked;

        this.holoFile = new File(plugin.getDataFolder(), "hologram.yml");

        if (!holoFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                holoFile.createNewFile();

                YamlConfiguration def = new YamlConfiguration();
                def.set("hologram.enabled", true);
                def.set("hologram.updateSeconds", 1); // ✅ event czas = płynnie (1s)
                def.save(holoFile);

                plugin.getLogger().info("[RankedHolo] Utworzono nowy hologram.yml");
            } catch (Exception e) {
                plugin.getLogger().severe("[RankedHolo] Nie mogę utworzyć hologram.yml!");
                e.printStackTrace();
            }
        }

        this.yml = YamlConfiguration.loadConfiguration(holoFile);
    }

    // =========================
    // PUBLIC API
    // =========================
    public void start() {
        reloadYml();

        plugin.getLogger().info("[RankedHolo] Start hologramu...");

        // zawsze czyść stare hologramy po tagu + swoje
        removeOldHologramsGlobal();
        removeHologram();

        if (!isEnabled()) {
            plugin.getLogger().warning("[RankedHolo] hologram.enabled = false");
            return;
        }

        Location base = getBaseLocation();
        if (base == null) {
            plugin.getLogger().warning("[RankedHolo] Brak hologram.location w hologram.yml");
            return;
        }

        if (base.getWorld() == null) {
            plugin.getLogger().warning("[RankedHolo] Świat hologramu NIE jest załadowany!");
            return;
        }

        int sec = Math.max(1, getUpdateSeconds());

        task = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::tickUpdate,
                20L,
                20L * sec
        );

        Bukkit.getScheduler().runTask(plugin, () -> {
            spawnOrUpdateNow();
            plugin.getLogger().info("[RankedHolo] Hologram aktywny.");
        });
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        removeHologram();
    }

    public void reload() {
        reloadYml();
        stop();
        if (isEnabled()) start();
    }

    // =========================
    // INTERNAL
    // =========================
    private void tickUpdate() {
        if (!isEnabled()) return;
        spawnOrUpdateNow();
    }

    private void spawnOrUpdateNow() {
        Location base = getBaseLocation();
        if (base == null) return;
        if (base.getWorld() == null) return;

        // topka
        List<RankedSystem.TopEntry> top = ranked != null ? ranked.getTopPlayers(5) : Collections.emptyList();

        List<String> topSig = new ArrayList<>();
        for (RankedSystem.TopEntry e : top) topSig.add(e.uuid + ":" + e.points + ":" + e.bwRankName);

        // event sygnatura (żeby czas odświeżał się płynnie)
        String eventSig = buildEventLine(); // już kolorowana linia

        boolean needRespawn = spawned.isEmpty() || anyMissingEntities(base.getWorld()) || referencesMissing();
        boolean topChanged = !topSig.equals(lastTopSig);
        boolean eventChanged = !eventSig.equals(lastEventSig);

        if (needRespawn) {
            // respawn całości (np. po reload świata)
            removeHologram();
            spawnHologram(base, top);
            lastTopSig = topSig;
            lastEventSig = eventSig;
            return;
        }

        // ✅ bez migania: aktualizujemy tylko to co trzeba
        if (eventChanged) {
            if (lineEvent != null) lineEvent.setText(eventSig);
            lastEventSig = eventSig;
        }

        if (topChanged) {
            updateTopSection(top);
            lastTopSig = topSig;
        }
    }

    private boolean referencesMissing() {
        if (lineHeader1 == null || lineEvent == null || lineTopTitle == null) return true;
        for (int i = 0; i < 5; i++) {
            if (topHeads[i] == null || topName[i] == null || topInfo[i] == null) return true;
        }
        return false;
    }

    private boolean anyMissingEntities(World w) {
        for (UUID id : spawned) {
            if (w.getEntity(id) == null) return true;
        }
        return false;
    }

    private void reloadYml() {
        this.yml = YamlConfiguration.loadConfiguration(holoFile);
    }

    private boolean isEnabled() {
        return yml.getBoolean("hologram.enabled", false);
    }

    private int getUpdateSeconds() {
        return yml.getInt("hologram.updateSeconds", 1);
    }

    private Location getBaseLocation() {
        if (!yml.isConfigurationSection("hologram.location")) return null;

        String worldName = yml.getString("hologram.location.world", "");
        double x = yml.getDouble("hologram.location.x", 0);
        double y = yml.getDouble("hologram.location.y", 0);
        double z = yml.getDouble("hologram.location.z", 0);
        float yaw = (float) yml.getDouble("hologram.location.yaw", 0);

        World w = Bukkit.getWorld(worldName);
        if (w == null) w = resolveWorldIgnoreCase(worldName);

        if (w == null) {
            plugin.getLogger().warning("[RankedHolo] Nie znaleziono świata '" + worldName + "'. Dostępne światy: " +
                    Bukkit.getWorlds().stream().map(World::getName).toList());
            return null;
        }

        return new Location(w, x, y, z, yaw, 0f);
    }

    private World resolveWorldIgnoreCase(String name) {
        if (name == null || name.isEmpty()) return null;
        for (World ww : Bukkit.getWorlds()) {
            if (ww.getName().equalsIgnoreCase(name)) return ww;
        }
        return null;
    }

    /** przesunięcie w układzie LOKALNYM (prawo/lewo/przód/tył względem yaw). */
    private Location addLocal(Location base, double right, double up, double forward) {
        float yawDeg = base.getYaw();
        double yaw = Math.toRadians(yawDeg + 180);

        double fx = Math.sin(yaw);
        double fz = -Math.cos(yaw);

        double rx = fz;
        double rz = -fx;

        return base.clone().add(
                rx * right + fx * forward,
                up,
                rz * right + fz * forward
        );
    }

    // =========================
    // SPAWN / UPDATE / REMOVE
    // =========================
    private void spawnHologram(Location base, List<RankedSystem.TopEntry> top) {
        spawned.clear();

        double y = HEADER_Y;

        lineHeader1 = spawnText(addLocal(base, 0, y, 0), color("&d&lBEDWARS &8| &fRANKED"), 1.02f);
        spawned.add(lineHeader1.getUniqueId()); y -= LINE_GAP;

        lineHeader2 = spawnText(addLocal(base, 0, y, 0), color("&7Dołącz do trybu i &ezbieraj punkty&7!"), 0.95f);
        spawned.add(lineHeader2.getUniqueId()); y -= LINE_GAP;

        lineHeader3 = spawnText(addLocal(base, 0, y, 0), color("&7Walcz o &6TOP &7i lepszą pozycję."), 0.95f);
        spawned.add(lineHeader3.getUniqueId()); y -= BLOCK_GAP;

        lineEvent = spawnText(addLocal(base, 0, y, 0), buildEventLine(), 0.92f);
        spawned.add(lineEvent.getUniqueId()); y -= BLOCK_GAP;

        lineSep = spawnText(addLocal(base, 0, y, 0), color("&8----------------------"), 0.90f);
        spawned.add(lineSep.getUniqueId()); y -= LINE_GAP;

        lineTopTitle = spawnText(addLocal(base, 0, y + 0.35, 0), color("&6TOP 5 &fRanked"), 0.96f);
        spawned.add(lineTopTitle.getUniqueId());

        // TOP 5
        double startRight = -2 * HEAD_GAP;

        for (int i = 0; i < 5; i++) {
            double right = startRight + (i * HEAD_GAP);
            RankedSystem.TopEntry entry = (i < top.size()) ? top.get(i) : null;

            topHeads[i] = spawnHead(addLocal(base, right, HEAD_Y, 0), entry);
            spawned.add(topHeads[i].getUniqueId());

            String l1 = (entry == null)
                    ? color("&8#" + (i + 1) + " &7---")
                    : color(placeColor(i + 1) + "#" + (i + 1) + " &f" + safe(entry.name));

            String l2 = (entry == null)
                    ? color("&7Brak danych")
                    : color("&b" + safeRank(entry.bwRankName) + " &8• &a" + entry.points + "pkt");

            topName[i] = spawnText(addLocal(base, right, HEAD_Y + TOP_TEXT_1, 0), l1, 0.78f);
            topInfo[i] = spawnText(addLocal(base, right, HEAD_Y + TOP_TEXT_2, 0), l2, 0.72f);

            spawned.add(topName[i].getUniqueId());
            spawned.add(topInfo[i].getUniqueId());
        }
    }

    private void updateTopSection(List<RankedSystem.TopEntry> top) {
        for (int i = 0; i < 5; i++) {
            RankedSystem.TopEntry entry = (i < top.size()) ? top.get(i) : null;

            // update head
            if (topHeads[i] != null) {
                topHeads[i].setItemStack(makeSkull(entry));
            }

            // update text
            String l1 = (entry == null)
                    ? color("&8#" + (i + 1) + " &7---")
                    : color(placeColor(i + 1) + "#" + (i + 1) + " &f" + safe(entry.name));

            String l2 = (entry == null)
                    ? color("&7Brak danych")
                    : color("&b" + safeRank(entry.bwRankName) + " &8• &a" + entry.points + "pkt");

            if (topName[i] != null) topName[i].setText(l1);
            if (topInfo[i] != null) topInfo[i].setText(l2);
        }
    }

    private void removeHologram() {
        // usuń entity po UUID
        for (World w : Bukkit.getWorlds()) {
            for (UUID id : spawned) {
                var ent = w.getEntity(id);
                if (ent != null) ent.remove();
            }
        }

        spawned.clear();

        // wyczyść refy
        lineHeader1 = lineHeader2 = lineHeader3 = lineEvent = lineSep = lineTopTitle = null;
        for (int i = 0; i < 5; i++) {
            topHeads[i] = null;
            topName[i] = null;
            topInfo[i] = null;
        }

        lastTopSig = new ArrayList<>();
        lastEventSig = "";
    }

    private void removeOldHologramsGlobal() {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(HOLO_TAG)) {
                    e.remove();
                    removed++;
                }
            }
        }
        plugin.getLogger().info("[RankedHolo] Usunięto stare hologramy po tagu: " + HOLO_TAG + " (" + removed + ")");
    }

    // =========================
    // SPAWN HELPERS
    // =========================
    private TextDisplay spawnText(Location loc, String text, float scale) {
        TextDisplay td = loc.getWorld().spawn(loc, TextDisplay.class);
        td.setText(text);
        td.addScoreboardTag(HOLO_TAG);

        td.setBillboard(Display.Billboard.CENTER);
        td.setSeeThrough(true);
        td.setShadowed(true);
        td.setBackgroundColor(null);
        td.setViewRange(48f);

        var t = td.getTransformation();
        td.setTransformation(new Transformation(
                t.getTranslation(),
                t.getLeftRotation(),
                t.getScale().set(scale, scale, scale),
                t.getRightRotation()
        ));

        return td;
    }

    private ItemDisplay spawnHead(Location loc, RankedSystem.TopEntry entry) {
        loc.setYaw(0f);
        loc.setPitch(0f);

        ItemDisplay id = loc.getWorld().spawn(loc, ItemDisplay.class);
        id.setBillboard(Display.Billboard.CENTER);
        id.setViewRange(48f);
        id.setShadowRadius(0.1f);

        id.setItemStack(makeSkull(entry));

        var t = id.getTransformation();
        id.setTransformation(new Transformation(
                t.getTranslation(),
                new org.joml.Quaternionf().rotateY((float) Math.toRadians(180)),
                t.getScale().set((float) HEAD_SCALE, (float) HEAD_SCALE, (float) HEAD_SCALE),
                t.getRightRotation()
        ));

        id.addScoreboardTag(HOLO_TAG);
        return id;
    }

    private ItemStack makeSkull(RankedSystem.TopEntry entry) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (meta != null) {
            if (entry != null) {
                try {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(entry.uuid);
                    meta.setOwningPlayer(op);
                } catch (Throwable ignored) {}
                meta.setDisplayName(ChatColor.WHITE + safe(entry.name));
            } else {
                meta.setDisplayName(ChatColor.GRAY + "---");
            }
            skull.setItemMeta(meta);
        }

        return skull;
    }

    // =========================
    // EVENT LINE
    // =========================
    private String buildEventLine() {
        if (ranked != null && ranked.isEventActive()) {
            return color("&6EVENT: &ex" + fmtMult(ranked.getGlobalMultiplier()) + formatTimeLeft(ranked.getGlobalMultiplierExpiresAtMs()));
        }
        return color("&7EVENT: &aBrak");
    }

    private String formatTimeLeft(long expiresAtMs) {
        if (expiresAtMs <= 0) return "";
        long left = expiresAtMs - System.currentTimeMillis();
        if (left <= 0) return "";
        long sec = left / 1000;
        long mm = sec / 60;
        long ss = sec % 60;
        return color(" &8(&e" + String.format(Locale.US, "%02d:%02d", mm, ss) + "&8)");
    }

    // =========================
    // FORMAT / COLORS
    // =========================
    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private String safe(String s) {
        if (s == null || s.isEmpty()) return "Unknown";
        return s.length() > 16 ? s.substring(0, 16) : s;
    }

    private String safeRank(String s) {
        if (s == null || s.isEmpty()) return "Brak";
        return s.length() > 18 ? s.substring(0, 18) : s;
    }

    private String placeColor(int pos) {
        return switch (pos) {
            case 1 -> "&6";
            case 2 -> "&f";
            case 3 -> "&c";
            default -> "&e";
        };
    }

    private String fmtMult(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    // =========================
    // COMMANDS: /bwholo
    // =========================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("bedwars.admin")) {
            sender.sendMessage(ChatColor.RED + "Brak uprawnień (bedwars.admin).");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "set" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(ChatColor.RED + "Tylko gracz może ustawić pozycję.");
                    return true;
                }

                Location loc = p.getLocation().clone();
                setLocation(loc);
                sender.sendMessage(ChatColor.GREEN + "Ustawiono hologram. Kierunek TOP5 = kierunek w którym patrzyłeś.");
                reload();
                return true;
            }

            case "enable" -> {
                yml.set("hologram.enabled", true);
                save();
                sender.sendMessage(ChatColor.GREEN + "Włączono hologram.");
                reload();
                return true;
            }

            case "disable" -> {
                yml.set("hologram.enabled", false);
                save();
                sender.sendMessage(ChatColor.YELLOW + "Wyłączono hologram.");
                reload();
                return true;
            }

            case "remove" -> {
                yml.set("hologram.enabled", false);
                yml.set("hologram.location", null);
                save();
                sender.sendMessage(ChatColor.YELLOW + "Usunięto hologram (i lokację).");
                stop();
                return true;
            }

            case "update" -> {
                spawnOrUpdateNow();
                sender.sendMessage(ChatColor.GREEN + "Odświeżono hologram.");
                return true;
            }

            case "setinterval" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Użycie: /bwholo setinterval <sekundy>");
                    return true;
                }
                int sec;
                try { sec = Integer.parseInt(args[1]); } catch (Exception e) { sec = -1; }
                if (sec < 1 || sec > 60) {
                    sender.sendMessage(ChatColor.RED + "Podaj 1..60 sekund.");
                    return true;
                }
                yml.set("hologram.updateSeconds", sec);
                save();
                sender.sendMessage(ChatColor.GREEN + "Ustawiono update na " + sec + "s.");
                reload();
                return true;
            }

            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "========== " + ChatColor.WHITE + "BWHolo" + ChatColor.AQUA + " ==========");
        sender.sendMessage(ChatColor.YELLOW + "/bwholo set" + ChatColor.GRAY + " - ustaw pozycję + kierunek (tam gdzie stoisz i patrzysz)");
        sender.sendMessage(ChatColor.YELLOW + "/bwholo enable" + ChatColor.GRAY + " - włącz hologram");
        sender.sendMessage(ChatColor.YELLOW + "/bwholo disable" + ChatColor.GRAY + " - wyłącz hologram");
        sender.sendMessage(ChatColor.YELLOW + "/bwholo remove" + ChatColor.GRAY + " - usuń hologram i lokację");
        sender.sendMessage(ChatColor.YELLOW + "/bwholo update" + ChatColor.GRAY + " - ręczne odświeżenie");
        sender.sendMessage(ChatColor.YELLOW + "/bwholo setinterval <1..60>" + ChatColor.GRAY + " - co ile sekund update");
        sender.sendMessage(ChatColor.AQUA + "======================================");
    }

    private void setLocation(Location loc) {
        yml.set("hologram.location.world", loc.getWorld().getName());
        yml.set("hologram.location.x", loc.getX());
        yml.set("hologram.location.y", loc.getY());
        yml.set("hologram.location.z", loc.getZ());

        yml.set("hologram.location.yaw", loc.getYaw());
        yml.set("hologram.location.pitch", 0);

        if (!yml.isSet("hologram.enabled")) yml.set("hologram.enabled", true);
        if (!yml.isSet("hologram.updateSeconds")) yml.set("hologram.updateSeconds", 1);
        save();
    }

    private void save() {
        try {
            yml.save(holoFile);
        } catch (Exception e) {
            plugin.getLogger().severe("[RankedHolo] Nie mogę zapisać hologram.yml: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("set", "enable", "disable", "remove", "update", "setinterval");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setinterval")) {
            return Arrays.asList("1", "2", "3", "5", "10", "15", "30", "60");
        }
        return Collections.emptyList();
    }
}
