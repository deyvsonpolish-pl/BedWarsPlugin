package org.BedWars;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
public class ChatAndScoreboard implements Listener {

    private final BedWarsPlugin plugin;

    // ===== KONFIG =====
    // UJEDNOLIĆ z TabListaBW -> u Ciebie było "BWHUB"
    private static final String BW_HUB_WORLD = "BWHUB";
    private List<String> bannedWords = new ArrayList<>();
    private static final String HUB_OBJ = "BWHubSB";
    private static final int PHASE_TIME = 10;

    private static final String[] TITLE_FRAMES = { "\uE112" };
    private static final String[] TITLE_UPDATES = { "\uE112" };
    private static final String[] TITLE_TOP = { "\uE113" };

    // ===== ANIMACJA / FAZY =====
    private int phase = 0;       // 0=staty, 1=aktualizacje, 2=topka
    private int phaseTick = 0;
    private BukkitRunnable phaseTask;
    private BukkitRunnable hubTask;

    // ✅ cache ostatnich linii (żeby czyścić TYLKO swoje wpisy)
    private final Map<UUID, List<String>> lastHubLines = new ConcurrentHashMap<>();

    public ChatAndScoreboard(BedWarsPlugin plugin) {
        this.plugin = plugin;
    }

    // =========================
    // START / STOP
    // =========================
    public void start() {
        loadBannedWords(); // 🔥 TO MUSI BYĆ
        Bukkit.getPluginManager().registerEvents(this, plugin);

        startPhaseRotation();

        hubTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (isInBWHub(p) && !isInArena(p)) {
                        applyHubScoreboard(p);
                    }
                }
            }
        };
        hubTask.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        if (hubTask != null) hubTask.cancel();
        if (phaseTask != null) phaseTask.cancel();

        HandlerList.unregisterAll(this);

        lastHubLines.clear();
    }

    // =========================
    // FAZY
    // =========================
    private void startPhaseRotation() {
        phaseTask = new BukkitRunnable() {
            @Override
            public void run() {
                phaseTick++;
                if (phaseTick >= PHASE_TIME) {
                    phaseTick = 0;
                    phase = (phase + 1) % 3;
                }
            }
        };
        phaseTask.runTaskTimer(plugin, 20L, 20L);
    }

    private String progressBar(int tick, int total) {
        int t = Math.max(0, Math.min(total, tick));
        StringBuilder sb = new StringBuilder(ChatColor.GREEN + "[");
        for (int i = 0; i < total; i++) sb.append(i < t ? "■" : "□");
        sb.append(ChatColor.GREEN).append("]");
        return sb.toString();
    }

    private String progressBarFixed(int page, int pages) {
        StringBuilder sb = new StringBuilder(ChatColor.GREEN + "[");
        for (int i = 1; i <= pages; i++) {
            sb.append(i == page ? "■" : ChatColor.DARK_GRAY + "□" + ChatColor.GREEN);
        }
        sb.append("]");
        return sb.toString();
    }

    private String progressBarFull() {
        return ChatColor.GREEN + "[■■■■■■■■■■]";
    }

    private String spacer(int idx) {
        ChatColor[] c = {
                ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_PURPLE, ChatColor.DARK_AQUA,
                ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.GOLD, ChatColor.GRAY,
                ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA,
                ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE
        };
        return c[idx % c.length] + "" + ChatColor.RESET + " ";
    }

    private String formatTimeLeft(long expiresAtMs) {
        if (expiresAtMs <= 0) return "";

        long left = expiresAtMs - System.currentTimeMillis();
        if (left <= 0) return " (0:00)";

        long totalSec = left / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;

        return " (" + min + ":" + String.format("%02d", sec) + ")";
    }

    private boolean isCapsSpam(String msg) {
        int upper = 0;
        int letters = 0;

        for (char c : msg.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }

        if (letters < 5) return false;

        return (double) upper / letters > 0.7;
    }
    // =========================
    // SCOREBOARD (WSPÓLNY Z TAB!)
    // =========================
    private Objective getObjective(Player p) {
        // ✅ ważne: TEN SAM scoreboard co TabListaBW i ArenaManager
        Scoreboard sb = plugin.getArenaManager().getOrCreatePlayerBoard(p);
        if (sb == null) return null;

        Objective obj = sb.getObjective(HUB_OBJ);
        if (obj == null) {
            obj = sb.registerNewObjective(
                    HUB_OBJ,
                    "dummy",
                    ChatColor.translateAlternateColorCodes('&', TITLE_FRAMES[0])
            );
        }

        // ✅ zawsze sidebar
        if (obj.getDisplaySlot() != DisplaySlot.SIDEBAR) {
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        return obj;
    }

    private void applyHubScoreboard(Player p) {
        Objective obj = getObjective(p);
        if (obj == null) return;

        Scoreboard sb = obj.getScoreboard();

        // ✅ NIE resetuj wszystkich entries (bo TAB używa teamów na tym scoreboardzie)
        // Czyść tylko to, co TY wcześniej wstawiłeś jako linie sidebaru
        List<String> old = lastHubLines.getOrDefault(p.getUniqueId(), List.of());
        for (String sOld : old) sb.resetScores(sOld);

        SystemLobbyBWMenu.LobbySettings s = plugin.getLobbyMenu() != null
                ? plugin.getLobbyMenu().getSettings(p.getUniqueId())
                : new SystemLobbyBWMenu.LobbySettings();

        int viewPage;
        if (s.scoreboardMode == SystemLobbyBWMenu.ScoreboardMode.FIXED) {
            viewPage = Math.max(1, Math.min(3, s.fixedPage));
        } else {
            viewPage = phase + 1;
        }

        // tytuł per gracz
        String title;
        if (viewPage == 1) title = TITLE_FRAMES[0];
        else if (viewPage == 2) title = TITLE_UPDATES[0];
        else title = TITLE_TOP[0];

        obj.setDisplayName(ChatColor.translateAlternateColorCodes('&', title));

        // ✅ budujemy listę linii -> potem ustawiamy score
        List<String> lines = new ArrayList<>();

        switch (viewPage) {
            case 1 -> {
                int coins = 100;
                int wins = 0, deaths = 0, games = 0, kills = 0, beds = 0, bestStreak = 0;

                RankedSystem rr = plugin.getRankedSystem();
                int bwPoints = rr != null ? rr.getPoints(p) : 0;
                String bwRankPrefix = rr != null ? rr.getRankPrefix(p) : "";
                int toNext = rr != null ? rr.pointsToNext(p) : 0;
                double subProg = rr != null ? rr.subProgress(p) : 0.0;

                String nameLine = ChatColor.AQUA + "👤 " + getVaultPrefix(p) + ChatColor.WHITE + p.getName();

                double kd = deaths <= 0 ? kills : (double) kills / deaths;
                double wr = games <= 0 ? 0.0 : ((double) wins / games) * 100.0;

                int bars = (int) Math.round(Math.min(1.0, Math.max(0.0, subProg)) * 10.0);
                String rankBar = ChatColor.AQUA + "[" +
                        "■".repeat(bars) +
                        ChatColor.DARK_GRAY + "□".repeat(10 - bars) +
                        ChatColor.AQUA + "]";

                lines.add(nameLine);
                lines.add(spacer(lines.size()));

                lines.add(ChatColor.AQUA + "Ranga: " + bwRankPrefix);

                String nextInfo = toNext <= 0 ? ChatColor.GREEN + "MAX" : ChatColor.GREEN + "+" + toNext;
                lines.add(ChatColor.YELLOW + "Punkty: " + ChatColor.WHITE + bwPoints +
                        ChatColor.DARK_GRAY + " (" + nextInfo + ChatColor.DARK_GRAY + ")");

                lines.add(rankBar);
                lines.add(spacer(lines.size()));

                lines.add(ChatColor.GOLD + "Monety: " + ChatColor.WHITE + coins);
                lines.add(spacer(lines.size()));

                lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + "Statystyki");

                lines.add(ChatColor.GREEN + "W:" + wins +
                        ChatColor.RED + " D:" + deaths +
                        ChatColor.GRAY + " G:" + games);

                lines.add(ChatColor.AQUA + "K:" + kills +
                        ChatColor.YELLOW + " Ł:" + beds);

                lines.add(ChatColor.YELLOW + "K/D: " + String.format(Locale.US, "%.2f", kd) +
                        ChatColor.GREEN + " WR: " + String.format(Locale.US, "%.0f%%", wr));

                lines.add(ChatColor.LIGHT_PURPLE + "Best: " + ChatColor.WHITE + bestStreak);

                lines.add(spacer(lines.size()));

                lines.add(bottomBarLine(s, viewPage));
            }

            case 2 -> {
                String nameLine = ChatColor.AQUA + "👤 " + getVaultPrefix(p) + ChatColor.WHITE + p.getName();

                lines.add(nameLine);
                lines.add(spacer(lines.size()));

                lines.add(ChatColor.AQUA + "" + ChatColor.BOLD + "Aktualizacje");
                lines.add(ChatColor.GRAY + "Nowa mapa: " + ChatColor.AQUA + "Dżungla");

                RankedSystem rr = plugin.getRankedSystem();
                if (rr != null && rr.isEventActive()) {
                    double mult = rr.getGlobalMultiplier();
                    String timeLeft = formatTimeLeft(rr.getGlobalMultiplierExpiresAtMs());
                    lines.add(ChatColor.GRAY + "Event: " + ChatColor.GOLD + "x" + mult
                            + ChatColor.YELLOW + timeLeft);
                } else {
                    lines.add(ChatColor.GRAY + "Event: " + ChatColor.GREEN + "Brak");
                }

                while (lines.size() < 14) lines.add(spacer(lines.size()));
                lines.add(bottomBarLine(s, viewPage));
            }

            case 3 -> {
                lines.add(ChatColor.GOLD + " ");
                lines.add(spacer(lines.size()));
                lines.add(ChatColor.GOLD + "" + ChatColor.BOLD + " ");

                RankedSystem rr = plugin.getRankedSystem();
                List<RankedSystem.TopEntry> top = (rr != null) ? rr.getTopPlayers(5) : Collections.emptyList();

                if (top.isEmpty()) {
                    lines.add(ChatColor.GRAY + "Brak danych...");
                } else {
                    int pos = 1;
                    for (RankedSystem.TopEntry te : top) {
                        String pex = te.pexPrefix == null ? "" : te.pexPrefix;
                        if (!pex.isEmpty() && !pex.endsWith(" ")) pex += " ";

                        String lineNick = ChatColor.YELLOW + "#" + pos + " " + ChatColor.RESET + pex + ChatColor.WHITE + te.name;
                        if (lineNick.length() > 40) lineNick = lineNick.substring(0, 40);
                        lines.add(lineNick);

                        String lineInfo = ChatColor.DARK_GRAY + "  " + ChatColor.AQUA + te.bwRankName
                                + ChatColor.DARK_GRAY + " • " + ChatColor.GREEN + te.points + "pkt";
                        if (lineInfo.length() > 40) lineInfo = lineInfo.substring(0, 40);

                        // + spacer aby unikalne
                        lines.add(lineInfo + spacer(lines.size()));

                        pos++;
                        if (lines.size() >= 14) break;
                    }
                }

                while (lines.size() < 14) lines.add(spacer(lines.size()));
                lines.add(bottomBarLine(s, viewPage));
            }
        }

        // ✅ ustaw scores
        int score = lines.size();
        for (String sLine : lines) {
            obj.getScore(sLine).setScore(score--);
        }
        lastHubLines.put(p.getUniqueId(), new ArrayList<>(lines));

        // ✅ ustaw scoreboard TYLKO jeśli nie ma (nie myga)
        if (p.getScoreboard() != sb) p.setScoreboard(sb);
    }

    private String bottomBarLine(SystemLobbyBWMenu.LobbySettings s, int viewPage) {
        if (s.scoreboardMode == SystemLobbyBWMenu.ScoreboardMode.FIXED) {
            return progressBarFixed(viewPage, 3);
        }
        if (!s.animations) {
            return progressBarFull();
        }
        return progressBar(phaseTick, PHASE_TIME);
    }

    // =========================
    // EVENTY
    // =========================
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = e.getPlayer();
            if (isInBWHub(p) && !isInArena(p)) {
                applyHubScoreboard(p);
            }
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        lastHubLines.remove(id);
        // ✅ NIE unregister objective na scoreboardzie wspólnym (bo to board gracza używany też przez TAB)
        // Gdy gracz wyjdzie, i tak jego board znika z cache w ArenaManager (jak zrobisz cleanup), albo zostanie w mapie bez szkody.
    }

    // =========================
    // HELPERY
    // =========================
    private boolean isInArena(Player p) {
        return plugin.getPlayerArena().containsKey(p.getUniqueId());
    }

    private boolean isInBWHub(Player p) {
        return p.getWorld() != null && p.getWorld().getName().equalsIgnoreCase(BW_HUB_WORLD);
    }

    private String getVaultPrefix(Player p) {
        Chat chat = plugin.getChat();
        if (chat == null) return "";

        try {
            String prefix = chat.getPlayerPrefix(p);
            if (prefix == null) return "";
            return ChatColor.translateAlternateColorCodes('&', prefix) + ChatColor.RESET;
        } catch (Throwable t) {
            return "";
        }
    }
    private String removeDuplicates(String text) {
        return text.replaceAll("(.)\\1+", "$1");
    }
    private boolean containsBannedWord(String message) {
        String clean = message.toLowerCase()
                .replace("0", "o")
                .replace("1", "i")
                .replace("@", "a")
                .replace("3", "e")
                .replaceAll("[^a-ząćęłńóśźż]", "");

        clean = removeDuplicates(clean); // 🔥 KLUCZ

        for (String word : bannedWords) {
            String root = word.toLowerCase();
            if (clean.contains(root)) {
                return true;
            }
        }
        return false;
    }
    private void loadBannedWords() {
        try {
            File file = new File(Bukkit.getPluginManager()
                    .getPlugin("Hub") // 👈 NAZWA TWOJEGO HUBA
                    .getDataFolder(), "wiadomosci.yml");

            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            bannedWords = cfg.getStringList("chat.banned-words");

            Bukkit.getLogger().info("[BW] Załadowano bannedWords: " + bannedWords.size());

        } catch (Exception e) {
            Bukkit.getLogger().warning("Nie udało się wczytać banned-words!");
            e.printStackTrace();
        }
    }
    // =========================
    // CHAT – PAPER 1.20+
    // =========================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent e) {
        if (e.isCancelled()) return;
        Player sender = e.getPlayer();

        String msg = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText()
                .serialize(e.message());

        // 🔥 PRZEKLEŃSTWA
        if (containsBannedWord(msg)) {
            e.setCancelled(true);
            sender.sendMessage("§cNie używaj przekleństw!");
            return;
        }

        // 🔥 CAPS LOCK
        if (isCapsSpam(msg)) {
            e.setCancelled(true);
            sender.sendMessage("§cNie pisz CAPS LOCKIEM!");
            return;
        }

        boolean arena = plugin.getPlayerArena().containsKey(sender.getUniqueId());
        boolean hub = sender.getWorld() != null
                && sender.getWorld().getName().equalsIgnoreCase(BW_HUB_WORLD);

        if (!arena && !hub) return;

        e.setCancelled(true);

        if (arena) sendArenaChat(sender, msg);
        else sendHubChat(sender, msg);
    }

    private String getBWRankPrefix(Player p) {
        RankedSystem rr = plugin.getRankedSystem();
        if (rr == null) return "";
        return rr.getRankPrefix(p);
    }

    private void sendHubChat(Player sender, String msg) {
        String vault = getVaultPrefix(sender);
        String bw = getBWRankPrefix(sender);

        String line = ChatColor.DARK_AQUA + "[BW] "
                + ChatColor.LIGHT_PURPLE + bw
                + vault + ChatColor.WHITE + sender.getName()
                + ChatColor.DARK_GRAY + " » "
                + ChatColor.GRAY + msg;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isInBWHub(p) && !isInArena(p)) {
                p.sendMessage(line);
            }
        }
    }

    private void sendArenaChat(Player sender, String msg) {
        BedWarsPlugin.Arena arena = plugin.getPlayerArena().get(sender.getUniqueId());
        if (arena == null) return;

        String vault = getVaultPrefix(sender);
        String bw = getBWRankPrefix(sender);

        BedWarsPlugin.Team team = plugin.getPlayerTeam().get(sender.getUniqueId());
        ChatColor tc = team != null ? team.getColor() : ChatColor.GRAY;

        String line = ChatColor.DARK_AQUA + "[BW] "
                + ChatColor.LIGHT_PURPLE + bw
                + vault + tc + sender.getName()
                + ChatColor.DARK_GRAY + " » "
                + ChatColor.GRAY + msg;

        for (UUID u : arena.getPlayersInArena()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) {
                p.sendMessage(line);
            }
        }
    }
}
