package org.BedWars;

import net.milkbowl.vault.chat.Chat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =========================
 *  RankedSystem (BedWars)
 * =========================
 *
 * ✅ Punkty +/- (ranked)
 * ✅ Rangi + podrangi (V-IV-III-II-I)
 * ✅ ranks.yml (rangi, gracze, ustawienia, wiadomości)
 * ✅ Autosave + debounce
 * ✅ Broadcast awansu/spadku na wybrane światy (lobby)
 * ✅ Global multiplier + event czasowy + per-player multiplier
 * ✅ Wiadomości jako lista (puste linie)
 * ✅ Prefix z PEX/LuckPerms przez Vault ({pexPrefix})
 *
 * DODANE:
 * ✅ public boolean hasAtLeastRank(UUID uuid, String required)  (np. "gold:I")
 * ✅ public int getMinPointsFor(String required)
 */
public class RankedSystem {

    // =========================
    // KONFIG / PLIKI
    // =========================
    private final JavaPlugin plugin;

    private File file;
    private YamlConfiguration yml;

    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private final List<Rank> ranks = new ArrayList<>();

    // Auto-save debounce
    private boolean dirty = false;
    private int saveTaskId = -1;

    // Podrangi
    private static final String[] SUBS = {"V", "IV", "III", "II", "I"};
    private static final int SUB_COUNT = 5;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // =========================
    // ADMIN UI TEXT
    // =========================
    private String adminTitle;
    private String adminSubtitle;
    private String adminNote;

    // =========================
    // SETTINGS (z ranks.yml)
    // =========================
    private boolean announcementsEnabled = true;
    private List<String> announcementWorlds = new ArrayList<>();

    // Messages (multi-line)
    private List<String> promotionLines = new ArrayList<>();
    private List<String> demotionLines = new ArrayList<>();
    private List<String> multiplierOnLines = new ArrayList<>();
    private List<String> multiplierOffLines = new ArrayList<>();

    // Multipliers
    private double globalMultiplier = 1.0;
    private long globalMultiplierExpiresAtMs = 0L; // 0 = bez limitu

    // Vault Chat (prefix)
    private Chat vaultChat;

    public RankedSystem(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================
    // ENABLE / DISABLE
    // =========================
    public void enable() {
        loadFile();
        setupVaultChat();
        loadRanks();
        loadPlayers();
        loadSettings();
        registerCommands();

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) saveNow();
            tickMultiplierExpiry();
        }, 20L * 30, 20L * 30);
    }

    public void disable() {
        if (saveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(saveTaskId);
            saveTaskId = -1;
        }
        saveNow();
        data.clear();
        ranks.clear();
    }

    // =========================
    // VAULT
    // =========================
    private void setupVaultChat() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            vaultChat = null;
            plugin.getLogger().warning("[Ranked] Vault nie wykryty – {pexPrefix} będzie puste.");
            return;
        }

        RegisteredServiceProvider<Chat> rsp = Bukkit.getServicesManager().getRegistration(Chat.class);
        if (rsp == null) {
            vaultChat = null;
            plugin.getLogger().warning("[Ranked] Brak provider Chat z Vault – sprawdź LuckPerms/PEX + Vault.");
            return;
        }

        vaultChat = rsp.getProvider();
    }

    private String getPexPrefix(UUID uuid, String lastKnownName) {
        if (vaultChat == null) return "";

        try {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                String pref = vaultChat.getPlayerPrefix(online);
                return pref == null ? "" : color(pref).trim();
            }

            // fallback dla offline (Vault różnie to wspiera)
            String worldName = "world";
            if (!Bukkit.getWorlds().isEmpty() && Bukkit.getWorlds().get(0) != null) {
                worldName = Bukkit.getWorlds().get(0).getName();
            }

            String pref = vaultChat.getPlayerPrefix(worldName, lastKnownName);
            return pref == null ? "" : color(pref).trim();

        } catch (Throwable t) {
            return "";
        }
    }

    // =========================
    // PUBLIC API
    // =========================
    public int getPoints(Player p) {
        return getPoints(p.getUniqueId());
    }

    public int getPoints(UUID uuid) {
        PlayerData pd = data.get(uuid);
        return pd == null ? 0 : pd.points;
    }

    public void setPoints(UUID uuid, String lastName, int points, String reason) {
        points = Math.max(0, points);
        PlayerData pd = data.computeIfAbsent(uuid, k -> new PlayerData(uuid, lastName, 0));

        RankState before = computeRank(pd.points);

        pd.lastName = (lastName == null ? pd.lastName : lastName);
        pd.points = points;
        pd.lastReason = (reason == null ? "" : reason);
        pd.lastChangeAt = LocalDateTime.now().format(DTF);

        RankState after = computeRank(pd.points);
        pd.rankId = after.rankId;
        pd.subIndex = after.subIndex;

        handleRankChange(uuid, pd.lastName, before, after);
        markDirty();
    }

    public int applyPointsChange(UUID uuid, String lastName, int delta, String reason) {
        PlayerData pd = data.computeIfAbsent(uuid, k -> new PlayerData(uuid, lastName, 0));

        RankState before = computeRank(pd.points);

        pd.lastName = (lastName == null ? pd.lastName : lastName);

        double mult = getEffectiveMultiplier(uuid);
        int applied = (int) Math.round(delta * mult);

        int newPoints = Math.max(0, pd.points + applied);
        pd.points = newPoints;

        RankState after = computeRank(pd.points);
        pd.rankId = after.rankId;
        pd.subIndex = after.subIndex;

        pd.lastReason = (reason == null ? "" : reason) + " (delta=" + delta + ", mult=" + mult + ", applied=" + applied + ")";
        pd.lastChangeAt = LocalDateTime.now().format(DTF);

        handleRankChange(uuid, pd.lastName, before, after);
        markDirty();

        return applied;
    }

    public int applyPointsChange(Player p, int delta, String reason) {
        return applyPointsChange(p.getUniqueId(), p.getName(), delta, reason);
    }

    public String getRankName(UUID uuid) {
        int pts = getPoints(uuid);
        RankState st = computeRank(pts);
        Rank r = ranks.get(st.rankId);
        return r.displayName + " " + SUBS[st.subIndex];
    }

    public String getRankName(Player p) {
        return getRankName(p.getUniqueId());
    }

    public String getRankPrefix(UUID uuid) {
        int pts = getPoints(uuid);
        RankState st = computeRank(pts);
        Rank r = ranks.get(st.rankId);
        return r.color + "[" + r.displayName + " " + SUBS[st.subIndex] + "]" + ChatColor.RESET + " ";
    }

    public String getRankPrefix(Player p) {
        return getRankPrefix(p.getUniqueId());
    }

    public int pointsToNext(UUID uuid) {
        int pts = getPoints(uuid);
        return computePointsToNext(pts);
    }

    public int pointsToNext(Player p) {
        return pointsToNext(p.getUniqueId());
    }

    public double subProgress(UUID uuid) {
        int pts = getPoints(uuid);
        return computeSubProgress(pts);
    }

    public double subProgress(Player p) {
        return subProgress(p.getUniqueId());
    }

    public double getEffectiveMultiplier(UUID uuid) {
        tickMultiplierExpiry();

        PlayerData pd = data.get(uuid);
        double playerMult = (pd == null ? 1.0 : pd.playerMultiplier);
        if (playerMult <= 0) playerMult = 1.0;

        return Math.max(0.0, globalMultiplier) * playerMult;
    }

    // =========================
    // ✅ DODANE: RANK CHECK (ARENY RANKED)
    // =========================

    /**
     * required format:
     *  - "gold:I"
     *  - "gold:II"
     *  - "gold:V"
     *  - albo samo "gold" (wtedy próg = start rangi)
     */
    public boolean hasAtLeastRank(UUID uuid, String required) {
        if (required == null || required.isEmpty()) return true;
        int pts = getPoints(uuid);
        int need = getMinPointsFor(required);
        return pts >= need;
    }

    /**
     * Minimalne punkty potrzebne dla progu (liczone z ranks.yml).
     * Subrangi: V..I (I najwyższe w obrębie rangi).
     */
    public int getMinPointsFor(String required) {
        if (required == null || required.isEmpty()) return 0;
        if (ranks.isEmpty()) return 0;

        String raw = required.trim().toLowerCase(Locale.ROOT);
        String rankId;
        String sub = null;

        if (raw.contains(":")) {
            String[] parts = raw.split(":", 2);
            rankId = parts[0].trim();
            sub = parts[1].trim().toUpperCase(Locale.ROOT);
        } else {
            rankId = raw.trim();
        }

        int idx = -1;
        for (int i = 0; i < ranks.size(); i++) {
            if (ranks.get(i).id.equalsIgnoreCase(rankId)) {
                idx = i;
                break;
            }
        }

        if (idx == -1) {
            String alt = raw.replace("_", ":").replace("-", ":");
            if (!alt.equals(raw)) return getMinPointsFor(alt);
            return 0;
        }

        int rankStart = ranks.get(idx).minPoints;

        if (sub == null || sub.isEmpty()) return rankStart;

        int nextRankStart = (idx == ranks.size() - 1) ? (rankStart + 1) : ranks.get(idx + 1).minPoints;
        int span = Math.max(1, nextRankStart - rankStart);

        int subIndex = -1;
        for (int i = 0; i < SUBS.length; i++) {
            if (SUBS[i].equalsIgnoreCase(sub)) {
                subIndex = i;
                break;
            }
        }
        if (subIndex == -1) return rankStart;

        // próg wejścia w subrangę: V=0%, IV=20%, III=40%, II=60%, I=80%
        int threshold = rankStart + (int) Math.floor((subIndex / (double) SUB_COUNT) * span);
        return Math.max(0, threshold);
    }

    // =========================
    // RANK LOGIC
    // =========================
    private RankState computeRank(int points) {
        if (ranks.isEmpty()) return new RankState(0, 0);

        int rankId = 0;
        for (int i = 0; i < ranks.size(); i++) {
            if (points >= ranks.get(i).minPoints) rankId = i;
        }

        if (rankId == ranks.size() - 1) {
            return new RankState(rankId, SUB_COUNT - 1);
        }

        Rank r = ranks.get(rankId);
        int rankStart = r.minPoints;
        int nextRankStart = ranks.get(rankId + 1).minPoints;
        int span = Math.max(1, nextRankStart - rankStart);

        double ratio = (points - rankStart) / (double) span; // 0.. <1
        int subIndex = (int) Math.floor(ratio * SUB_COUNT);
        subIndex = clamp(subIndex, 0, SUB_COUNT - 1);
        return new RankState(rankId, subIndex);
    }

    private int computePointsToNext(int points) {
        RankState st = computeRank(points);
        if (ranks.isEmpty()) return 0;

        if (st.rankId == ranks.size() - 1 && st.subIndex == SUB_COUNT - 1) return 0;

        Rank r = ranks.get(st.rankId);

        int rankStart = r.minPoints;
        int nextRankStart = (st.rankId == ranks.size() - 1) ? (rankStart + 1) : ranks.get(st.rankId + 1).minPoints;
        int span = Math.max(1, nextRankStart - rankStart);

        int nextSubIndex = st.subIndex + 1;
        if (nextSubIndex >= SUB_COUNT) {
            return Math.max(0, nextRankStart - points);
        }

        int threshold = rankStart + (int) Math.ceil((nextSubIndex / (double) SUB_COUNT) * span);
        return Math.max(0, threshold - points);
    }

    private double computeSubProgress(int points) {
        RankState st = computeRank(points);
        if (ranks.isEmpty()) return 1.0;
        if (st.rankId == ranks.size() - 1) return 1.0;

        Rank r = ranks.get(st.rankId);
        int rankStart = r.minPoints;
        int nextRankStart = ranks.get(st.rankId + 1).minPoints;
        int span = Math.max(1, nextRankStart - rankStart);

        int subStart = rankStart + (int) Math.floor((st.subIndex / (double) SUB_COUNT) * span);
        int subEnd = rankStart + (int) Math.floor(((st.subIndex + 1) / (double) SUB_COUNT) * span);

        if (subEnd <= subStart) return 1.0;
        return clamp01((points - subStart) / (double) (subEnd - subStart));
    }

    // =========================
    // ANNOUNCEMENTS
    // =========================
    private void handleRankChange(UUID uuid, String name, RankState before, RankState after) {
        if (!announcementsEnabled) return;

        int beforeScore = before.rankId * 100 + before.subIndex;
        int afterScore = after.rankId * 100 + after.subIndex;
        if (afterScore == beforeScore) return;

        String playerName = (name == null ? uuid.toString() : name);
        String newRank = ranks.get(after.rankId).displayName + " " + SUBS[after.subIndex];

        if (afterScore > beforeScore) {
            broadcastLinesToLobby(promotionLines, uuid, playerName, newRank);
        } else {
            broadcastLinesToLobby(demotionLines, uuid, playerName, newRank);
        }
    }

    private void broadcastLinesToLobby(List<String> lines, UUID uuid, String player, String rank) {
        if (lines == null || lines.isEmpty()) return;
        for (String line : lines) {
            String msg = formatLine(uuid, player, rank, line);
            broadcastToLobby(msg);
        }
    }

    private void broadcastToLobby(String msg) {
        if (msg == null) return;

        String out = color(msg);

        if (announcementWorlds == null || announcementWorlds.isEmpty()) {
            Bukkit.broadcastMessage(out);
            return;
        }

        Set<String> worldsLower = new HashSet<>();
        for (String w : announcementWorlds) if (w != null) worldsLower.add(w.toLowerCase(Locale.ROOT));

        for (Player p : Bukkit.getOnlinePlayers()) {
            World w = p.getWorld();
            if (w != null && worldsLower.contains(w.getName().toLowerCase(Locale.ROOT))) {
                p.sendMessage(out);
            }
        }
    }

    public double getGlobalMultiplier() {
        return globalMultiplier;
    }

    public boolean isEventActive() {
        return globalMultiplier > 1.0;
    }

    private String formatLine(UUID uuid, String player, String rank, String template) {
        if (template == null) return "";
        String pex = getPexPrefix(uuid, player);
        if (!pex.isEmpty()) pex = pex + " ";

        return template
                .replace("{player}", player)
                .replace("{rank}", rank)
                .replace("{multiplier}", String.valueOf(globalMultiplier))
                .replace("{pexPrefix}", pex);
    }

    // =========================
    // MULTIPLIER EXPIRY
    // =========================
    private void tickMultiplierExpiry() {
        if (globalMultiplierExpiresAtMs <= 0L) return;
        if (System.currentTimeMillis() >= globalMultiplierExpiresAtMs) {
            globalMultiplier = 1.0;
            globalMultiplierExpiresAtMs = 0L;
            broadcastLinesToLobby(multiplierOffLines, UUID.randomUUID(), "SYSTEM", "NONE");
            markDirty();
        }
    }

    // =========================
    // LOAD / SAVE
    // =========================
    private void loadFile() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

        file = new File(plugin.getDataFolder(), "ranks.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nie mogę utworzyć ranks.yml: " + e.getMessage());
            }
        }

        yml = YamlConfiguration.loadConfiguration(file);

        if (!yml.isConfigurationSection("admin")) {
            yml.set("admin.title", "&b&lBEDWARS &7| &fRangi");
            yml.set("admin.subtitle", "&7Panel administracji rang i punktów");
            yml.set("admin.note", "&8Edytuj progi rang poniżej. Reload: /bwrank reload");
            yml.set("admin.lastGenerated", LocalDateTime.now().format(DTF));
            saveSilently();
        }

        adminTitle = color(yml.getString("admin.title", "&b&lBEDWARS &7| &fRangi"));
        adminSubtitle = color(yml.getString("admin.subtitle", "&7Panel administracji rang i punktów"));
        adminNote = color(yml.getString("admin.note", "&8Edytuj progi rang poniżej. Reload: /bwrank reload"));

        if (!yml.isConfigurationSection("ranks")) {
            writeDefaultRanksToFile();
            saveSilently();
        }

        if (!yml.isConfigurationSection("players")) {
            yml.createSection("players");
            saveSilently();
        }

        if (!yml.isConfigurationSection("settings")) {
            yml.set("settings.announcements.enabled", true);
            yml.set("settings.announcements.worlds", Arrays.asList("BWHUB", "Dzungla"));
            yml.set("settings.multiplier.global", 1.0);
            yml.set("settings.multiplier.expiresAtMs", 0);

            yml.set("messages.promotion", Arrays.asList(
                    "",
                    "&a&lRANKED &8» &f{player} {pexPrefix}&7awansował na &e{rank}&7!",
                    ""
            ));
            yml.set("messages.demotion", Arrays.asList(
                    "",
                    "&c&lRANKED &8» &f{player} {pexPrefix}&7spadł na &e{rank}&7!",
                    ""
            ));
            yml.set("messages.multiplierOn", Arrays.asList(
                    "",
                    "&6&lEVENT &8» &fWłączono mnożnik punktów: &ex{multiplier}&f!",
                    ""
            ));
            yml.set("messages.multiplierOff", Arrays.asList(
                    "",
                    "&6&lEVENT &8» &fMnożnik punktów wrócił do &ex1.0&f.",
                    ""
            ));
            saveSilently();
        }
    }

    private void loadSettings() {
        announcementsEnabled = yml.getBoolean("settings.announcements.enabled", true);
        announcementWorlds = yml.getStringList("settings.announcements.worlds");

        globalMultiplier = yml.getDouble("settings.multiplier.global", 1.0);
        globalMultiplierExpiresAtMs = yml.getLong("settings.multiplier.expiresAtMs", 0L);

        promotionLines = readMessageLines("messages.promotion", "&a&lRANKED &8» &f{player} &7awansował na &e{rank}&7!");
        demotionLines  = readMessageLines("messages.demotion",  "&c&lRANKED &8» &f{player} &7spadł na &e{rank}&7!");
        multiplierOnLines  = readMessageLines("messages.multiplierOn",  "&6&lEVENT &8» &fWłączono mnożnik: &ex{multiplier}&f!");
        multiplierOffLines = readMessageLines("messages.multiplierOff", "&6&lEVENT &8» &fMnożnik wrócił do &ex1.0&f.");

        promotionLines.replaceAll(this::color);
        demotionLines.replaceAll(this::color);
        multiplierOnLines.replaceAll(this::color);
        multiplierOffLines.replaceAll(this::color);
    }

    private List<String> readMessageLines(String path, String defSingleLine) {
        if (yml.isList(path)) {
            List<String> list = yml.getStringList(path);
            if (list == null || list.isEmpty()) return new ArrayList<>(List.of(defSingleLine));
            return new ArrayList<>(list);
        }
        String single = yml.getString(path, defSingleLine);
        return new ArrayList<>(List.of(single));
    }

    private void writeDefaultRanksToFile() {
        ConfigurationSection sec = yml.createSection("ranks");
        createRankSec(sec, "iron",     "&7", "Iron",     0);
        createRankSec(sec, "bronze",   "&6", "Bronze",   500);
        createRankSec(sec, "silver",   "&f", "Silver",   1500);
        createRankSec(sec, "gold",     "&e", "Gold",     3500);
        createRankSec(sec, "platinum", "&b", "Platinum", 7000);
        createRankSec(sec, "diamond",  "&3", "Diamond",  12000);
        createRankSec(sec, "master",   "&c", "Master",   20000);
        createRankSec(sec, "legend",   "&d", "Legend",   30000);
    }

    private void createRankSec(ConfigurationSection parent, String id, String color, String display, int minPoints) {
        ConfigurationSection r = parent.createSection(id);
        r.set("color", color);
        r.set("displayName", display);
        r.set("minPoints", minPoints);
    }

    private void loadRanks() {
        ranks.clear();

        ConfigurationSection sec = yml.getConfigurationSection("ranks");
        if (sec == null) {
            writeDefaultRanksToFile();
            sec = yml.getConfigurationSection("ranks");
        }
        if (sec == null) return;

        List<Rank> tmp = new ArrayList<>();
        for (String id : sec.getKeys(false)) {
            ConfigurationSection r = sec.getConfigurationSection(id);
            if (r == null) continue;

            String col = r.getString("color", "&7");
            String name = r.getString("displayName", id);
            int min = r.getInt("minPoints", 0);

            tmp.add(new Rank(id.toLowerCase(Locale.ROOT), color(col), name, min));
        }

        tmp.sort(Comparator.comparingInt(a -> a.minPoints));

        int last = Integer.MIN_VALUE;
        for (Rank r : tmp) {
            if (r.minPoints < last) continue;
            ranks.add(r);
            last = r.minPoints;
        }

        if (ranks.isEmpty()) ranks.add(new Rank("iron", ChatColor.GRAY.toString(), "Iron", 0));
    }

    private void loadPlayers() {
        data.clear();

        ConfigurationSection players = yml.getConfigurationSection("players");
        if (players == null) return;

        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection p = players.getConfigurationSection(key);
                if (p == null) continue;

                String lastName = p.getString("lastName", "Unknown");
                int points = p.getInt("points", 0);

                PlayerData pd = new PlayerData(uuid, lastName, points);
                pd.playerMultiplier = p.getDouble("playerMultiplier", 1.0);
                pd.lastReason = p.getString("lastReason", "");
                pd.lastChangeAt = p.getString("lastChangeAt", "");

                RankState st = computeRank(points);
                pd.rankId = st.rankId;
                pd.subIndex = st.subIndex;

                data.put(uuid, pd);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void reload() {
        loadFile();
        setupVaultChat();
        loadRanks();
        loadPlayers();
        loadSettings();
    }

    private void markDirty() {
        dirty = true;
        if (saveTaskId != -1) Bukkit.getScheduler().cancelTask(saveTaskId);
        saveTaskId = Bukkit.getScheduler().runTaskLater(plugin, this::saveNow, 40L).getTaskId();
    }

    public void saveNow() {
        dirty = false;
        if (saveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(saveTaskId);
            saveTaskId = -1;
        }

        yml.set("settings.multiplier.global", globalMultiplier);
        yml.set("settings.multiplier.expiresAtMs", globalMultiplierExpiresAtMs);

        ConfigurationSection players = yml.getConfigurationSection("players");
        if (players == null) players = yml.createSection("players");

        for (UUID uuid : data.keySet()) {
            PlayerData pd = data.get(uuid);
            if (pd == null) continue;

            ConfigurationSection p = players.getConfigurationSection(uuid.toString());
            if (p == null) p = players.createSection(uuid.toString());

            p.set("lastName", pd.lastName);
            p.set("points", pd.points);
            p.set("rankId", pd.rankId);
            p.set("subIndex", pd.subIndex);

            p.set("playerMultiplier", pd.playerMultiplier);
            p.set("lastReason", pd.lastReason);
            p.set("lastChangeAt", pd.lastChangeAt);
        }

        yml.set("admin.lastSaved", LocalDateTime.now().format(DTF));
        saveSilently();
    }

    private void saveSilently() {
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie mogę zapisać ranks.yml: " + e.getMessage());
        }
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    // =========================
    // COMMANDS
    // =========================
    private void registerCommands() {
        PluginCommand cmd = plugin.getCommand("bwrank");
        if (cmd != null) {
            RankCommand ex = new RankCommand();
            cmd.setExecutor(ex);
            cmd.setTabCompleter(ex);
        } else {
            plugin.getLogger().warning("Brak komendy 'bwrank' w plugin.yml (dodaj ją).");
        }

        PluginCommand cmd2 = plugin.getCommand("bwpoints");
        if (cmd2 != null) {
            PointsCommand ex2 = new PointsCommand();
            cmd2.setExecutor(ex2);
            cmd2.setTabCompleter(ex2);
        } else {
            plugin.getLogger().warning("Brak komendy 'bwpoints' w plugin.yml (dodaj ją).");
        }
    }

    private class PointsCommand implements CommandExecutor, TabCompleter {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length == 0) {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(ChatColor.RED + "Użycie: /bwpoints <nick>");
                    return true;
                }
                sendPlayerInfo(sender, p.getUniqueId(), p.getName());
                return true;
            }

            OfflinePlayer op = Bukkit.getOfflinePlayer(args[0]);
            sendPlayerInfo(sender, op.getUniqueId(), op.getName());
            return true;
        }

        private void sendPlayerInfo(CommandSender sender, UUID uuid, String name) {
            PlayerData pd = data.get(uuid);
            int pts = getPoints(uuid);
            String rank = getRankName(uuid);
            int toNext = pointsToNext(uuid);
            double mult = getEffectiveMultiplier(uuid);

            sender.sendMessage(ChatColor.AQUA + "========== " + ChatColor.WHITE + "BedWars Ranked" + ChatColor.AQUA + " ==========");
            sender.sendMessage(ChatColor.GRAY + "Gracz: " + ChatColor.WHITE + (name == null ? uuid.toString() : name));
            sender.sendMessage(ChatColor.GRAY + "Ranga: " + ChatColor.WHITE + rank);
            sender.sendMessage(ChatColor.GRAY + "Punkty: " + ChatColor.YELLOW + pts);
            sender.sendMessage(ChatColor.GRAY + "Do awansu: " + ChatColor.GREEN + (toNext <= 0 ? "MAX" : toNext));
            sender.sendMessage(ChatColor.GRAY + "Mnożnik: " + ChatColor.GOLD + "x" + mult);

            if (pd != null && pd.lastChangeAt != null && !pd.lastChangeAt.isEmpty()) {
                sender.sendMessage(ChatColor.DARK_GRAY + "Ostatnia zmiana: " + pd.lastChangeAt);
                if (pd.lastReason != null && !pd.lastReason.isEmpty()) {
                    sender.sendMessage(ChatColor.DARK_GRAY + "Powód: " + pd.lastReason);
                }
            }

            sender.sendMessage(ChatColor.AQUA + "=======================================");
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (args.length == 1) {
                List<String> list = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) list.add(p.getName());
                return list;
            }
            return Collections.emptyList();
        }
    }

    private class RankCommand implements CommandExecutor, TabCompleter {

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length == 0) {
                sendHelp(sender);
                return true;
            }

            String sub = args[0].toLowerCase(Locale.ROOT);

            switch (sub) {
                case "reload" -> {
                    if (!sender.hasPermission("bedwars.admin")) {
                        sender.sendMessage(ChatColor.RED + "Brak uprawnień (bedwars.admin).");
                        return true;
                    }
                    reload();
                    sender.sendMessage(ChatColor.GREEN + "Przeładowano ranks.yml (rangi + gracze + ustawienia).");
                    return true;
                }

                case "info" -> {
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Użycie: /bwrank info <nick>");
                        return true;
                    }
                    OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                    new PointsCommand().sendPlayerInfo(sender, op.getUniqueId(), op.getName());
                    return true;
                }
                case "setpoints" -> {
                    if (!sender.hasPermission("bedwars.admin")) {
                        sender.sendMessage(ChatColor.RED + "Brak uprawnień (bedwars.admin).");
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Użycie: /bwrank setpoints <nick> <amount>");
                        return true;
                    }

                    OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                    int amount = parseInt(args[2], -1);
                    if (amount < 0) {
                        sender.sendMessage(ChatColor.RED + "Kwota musi być >= 0");
                        return true;
                    }

                    setPoints(op.getUniqueId(), op.getName(), amount, "ADMIN_SETPOINTS");
                    sender.sendMessage(ChatColor.GREEN + "Ustawiono punkty: " + op.getName() + " = " + amount);
                    return true;
                }

                case "addpoints" -> {
                    if (!sender.hasPermission("bedwars.admin")) {
                        sender.sendMessage(ChatColor.RED + "Brak uprawnień (bedwars.admin).");
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Użycie: /bwrank addpoints <nick> <amount>");
                        return true;
                    }

                    OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                    int amount = parseInt(args[2], 0);

                    applyPointsChange(op.getUniqueId(), op.getName(), amount, "ADMIN_ADDPOINTS");
                    sender.sendMessage(ChatColor.GREEN + "Dodano punkty: " + op.getName() + " +" + amount);
                    return true;
                }

                case "removepoints" -> {
                    if (!sender.hasPermission("bedwars.admin")) {
                        sender.sendMessage(ChatColor.RED + "Brak uprawnień (bedwars.admin).");
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Użycie: /bwrank removepoints <nick> <amount>");
                        return true;
                    }

                    OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                    int amount = parseInt(args[2], 0);

                    applyPointsChange(op.getUniqueId(), op.getName(), -Math.abs(amount), "ADMIN_REMOVEPOINTS");
                    sender.sendMessage(ChatColor.GREEN + "Odjęto punkty: " + op.getName() + " -" + Math.abs(amount));
                    return true;
                }

                case "resetpoints" -> {
                    if (!sender.hasPermission("bedwars.admin")) {
                        sender.sendMessage(ChatColor.RED + "Brak uprawnień (bedwars.admin).");
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Użycie: /bwrank resetpoints <nick>");
                        return true;
                    }

                    OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                    setPoints(op.getUniqueId(), op.getName(), 0, "ADMIN_RESETPOINTS");
                    sender.sendMessage(ChatColor.GREEN + "Zresetowano punkty gracza: " + op.getName());
                    return true;
                }

                case "setmultiplier" -> {
                    if (!sender.hasPermission("bedwars.admin")) {
                        sender.sendMessage(ChatColor.RED + "Brak uprawnień (bedwars.admin).");
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Użycie: /bwrank setmultiplier <value> [minutes]");
                        return true;
                    }

                    double val = parseDouble(args[1], -1);
                    if (val <= 0) {
                        sender.sendMessage(ChatColor.RED + "Mnożnik musi być > 0 (np. 2.0)");
                        return true;
                    }

                    globalMultiplier = val;
                    globalMultiplierExpiresAtMs = 0L;

                    if (args.length >= 3) {
                        int minutes = parseInt(args[2], 0);
                        if (minutes > 0) {
                            globalMultiplierExpiresAtMs = System.currentTimeMillis() + (minutes * 60L * 1000L);
                        }
                    }

                    broadcastLinesToLobby(multiplierOnLines, UUID.randomUUID(), "SYSTEM", "NONE");
                    sender.sendMessage(ChatColor.GREEN + "Ustawiono globalny mnożnik na x" + globalMultiplier +
                            (globalMultiplierExpiresAtMs > 0 ? " na czas." : "."));
                    markDirty();
                    return true;
                }

                case "setplayermultiplier" -> {
                    if (!sender.hasPermission("bedwars.admin")) {
                        sender.sendMessage(ChatColor.RED + "Brak uprawnień (bedwars.admin).");
                        return true;
                    }
                    if (args.length < 3) {
                        sender.sendMessage(ChatColor.RED + "Użycie: /bwrank setplayermultiplier <nick> <value>");
                        return true;
                    }

                    OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                    double val = parseDouble(args[2], -1);
                    if (val <= 0) {
                        sender.sendMessage(ChatColor.RED + "Mnożnik musi być > 0");
                        return true;
                    }

                    PlayerData pd = data.computeIfAbsent(op.getUniqueId(), k -> new PlayerData(op.getUniqueId(), op.getName(), 0));
                    pd.playerMultiplier = val;
                    pd.lastReason = "ADMIN_PLAYER_MULT";
                    pd.lastChangeAt = LocalDateTime.now().format(DTF);

                    sender.sendMessage(ChatColor.GREEN + "Ustawiono mnożnik gracza " + op.getName() + " na x" + val);
                    markDirty();
                    return true;
                }

                case "resetplayermultiplier" -> {
                    if (!sender.hasPermission("bedwars.admin")) {
                        sender.sendMessage(ChatColor.RED + "Brak uprawnień (bedwars.admin).");
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage(ChatColor.RED + "Użycie: /bwrank resetplayermultiplier <nick>");
                        return true;
                    }

                    OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                    PlayerData pd = data.computeIfAbsent(op.getUniqueId(), k -> new PlayerData(op.getUniqueId(), op.getName(), 0));
                    pd.playerMultiplier = 1.0;
                    pd.lastReason = "ADMIN_PLAYER_MULT_RESET";
                    pd.lastChangeAt = LocalDateTime.now().format(DTF);

                    sender.sendMessage(ChatColor.GREEN + "Zresetowano mnożnik gracza " + op.getName() + " do x1.0");
                    markDirty();
                    return true;
                }

                default -> {
                    sendHelp(sender);
                    return true;
                }
            }
        }

        private void sendHelp(CommandSender sender) {
            sender.sendMessage(ChatColor.AQUA + adminTitle);
            sender.sendMessage(ChatColor.GRAY + adminSubtitle);
            sender.sendMessage(ChatColor.DARK_GRAY + adminNote);
            sender.sendMessage(ChatColor.AQUA + " ");
            sender.sendMessage(ChatColor.YELLOW + "/bwpoints " + ChatColor.GRAY + "- twoja ranga i punkty");
            sender.sendMessage(ChatColor.YELLOW + "/bwpoints <nick> " + ChatColor.GRAY + "- info o graczu");
            sender.sendMessage(ChatColor.AQUA + " ");
            sender.sendMessage(ChatColor.GOLD + "Admin:");
            sender.sendMessage(ChatColor.YELLOW + "/bwrank info <nick>");
            sender.sendMessage(ChatColor.YELLOW + "/bwrank reload");
            sender.sendMessage(ChatColor.YELLOW + "/bwrank setpoints <nick> <amount>");
            sender.sendMessage(ChatColor.YELLOW + "/bwrank addpoints <nick> <amount>");
            sender.sendMessage(ChatColor.YELLOW + "/bwrank removepoints <nick> <amount>");
            sender.sendMessage(ChatColor.YELLOW + "/bwrank resetpoints <nick>");

            sender.sendMessage(ChatColor.YELLOW + "/bwrank setmultiplier <value> [minutes]");
            sender.sendMessage(ChatColor.YELLOW + "/bwrank setplayermultiplier <nick> <value>");
            sender.sendMessage(ChatColor.YELLOW + "/bwrank resetplayermultiplier <nick>");
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

            if (args.length == 1) {
                return Arrays.asList(
                        "info", "reload",
                        "setpoints", "addpoints", "removepoints", "resetpoints",
                        "setmultiplier", "setplayermultiplier", "resetplayermultiplier"
                );
            }

            if (args.length == 2) {
                String sub = args[0].toLowerCase(Locale.ROOT);

                if (sub.equals("info")
                        || sub.equals("setpoints")
                        || sub.equals("addpoints")
                        || sub.equals("removepoints")
                        || sub.equals("resetpoints")
                        || sub.equals("setplayermultiplier")
                        || sub.equals("resetplayermultiplier")) {

                    List<String> list = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) list.add(p.getName());
                    return list;
                }
            }

            if (args.length == 3) {
                String sub = args[0].toLowerCase(Locale.ROOT);

                if (sub.equals("setpoints") || sub.equals("addpoints") || sub.equals("removepoints")) {
                    return Arrays.asList("5", "10", "25", "50", "100", "250", "500", "1000");
                }

                if (sub.equals("setplayermultiplier")) {
                    return Arrays.asList("1.0", "1.5", "2.0", "3.0");
                }
            }

            if (args.length == 2) {
                String sub = args[0].toLowerCase(Locale.ROOT);
                if (sub.equals("setmultiplier")) {
                    return Arrays.asList("1.0", "1.5", "2.0", "3.0");
                }
            }

            if (args.length == 3) {
                String sub = args[0].toLowerCase(Locale.ROOT);
                if (sub.equals("setmultiplier")) {
                    return Arrays.asList("5", "10", "15", "30", "60", "120");
                }
            }

            return Collections.emptyList();
        }
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception ignored) { return def; }
    }

    private double parseDouble(String s, double def) {
        try { return Double.parseDouble(s); } catch (Exception ignored) { return def; }
    }

    // =========================
    // DATA STRUCTS
    // =========================
    private static class PlayerData {
        final UUID uuid;
        String lastName;
        int points;

        int rankId = 0;
        int subIndex = 0;

        double playerMultiplier = 1.0;

        String lastReason = "";
        String lastChangeAt = "";

        PlayerData(UUID uuid, String lastName, int points) {
            this.uuid = uuid;
            this.lastName = (lastName == null ? "Unknown" : lastName);
            this.points = Math.max(0, points);
        }
    }

    private static class Rank {
        final String id;
        final String color;
        final String displayName;
        final int minPoints;

        Rank(String id, String color, String displayName, int minPoints) {
            this.id = id;
            this.color = color;
            this.displayName = displayName;
            this.minPoints = minPoints;
        }
    }

    private static class RankState {
        final int rankId;
        final int subIndex;

        RankState(int rankId, int subIndex) {
            this.rankId = rankId;
            this.subIndex = subIndex;
        }
    }

    // =========================
    // UTILS
    // =========================
    private static int clamp(int v, int a, int b) {
        return Math.max(a, Math.min(b, v));
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    public long getGlobalMultiplierExpiresAtMs() {
        return globalMultiplierExpiresAtMs;
    }

    // =========================
    // TOPKA / PUBLIC HELPERS
    // =========================
    public static class TopEntry {
        public final UUID uuid;
        public final String name;
        public final int points;
        public final String bwRankName;
        public final String pexPrefix;

        public TopEntry(UUID uuid, String name, int points, String bwRankName, String pexPrefix) {
            this.uuid = uuid;
            this.name = name;
            this.points = points;
            this.bwRankName = bwRankName;
            this.pexPrefix = pexPrefix;
        }
    }

    public String getPexPrefixPublic(UUID uuid, String lastKnownName) {
        String p = getPexPrefix(uuid, lastKnownName);
        if (p == null) return "";
        return p.trim();
    }

    public List<TopEntry> getTopPlayers(int limit) {
        int lim = Math.max(1, Math.min(10, limit));

        List<PlayerData> list = new ArrayList<>(data.values());
        list.sort((a, b) -> Integer.compare(b.points, a.points));

        List<TopEntry> out = new ArrayList<>();
        for (PlayerData pd : list) {
            if (pd == null) continue;

            String name = (pd.lastName == null || pd.lastName.isEmpty()) ? "Unknown" : pd.lastName;
            int pts = pd.points;

            String bwRank = getRankName(pd.uuid);
            String pex = getPexPrefixPublic(pd.uuid, name);

            out.add(new TopEntry(pd.uuid, name, pts, bwRank, pex));
            if (out.size() >= lim) break;
        }

        return out;
    }
}
