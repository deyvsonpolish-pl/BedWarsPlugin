package org.BedWars;

import net.milkbowl.vault.chat.Chat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TabListaBW {

    private BukkitRunnable tabTitleTask;
    private int tabTitleFrame = 0;

    private static final String[] TAB_TITLE_FRAMES = new String[]{
            "&bB&fᴇᴅWᴀʀs",
            "&fB&bᴇ&fᴅWᴀʀs",
            "&fBᴇ&bᴅ&fWᴀʀs",
            "&fBᴇᴅ&bW&fᴀʀs",
            "&fBᴇᴅW&bᴀ&fʀs",
            "&fBᴇᴅWᴀ&bʀ&fs",
            "&fBᴇᴅWᴀʀ&bs",
            "&fBᴇᴅWᴀʀs"
    };

    private final BedWarsPlugin plugin;

    private static final String BW_HUB_WORLD = "BWHUB";
    private static final String DISCORD_INVITE = "discord.gg/mckosmo";
    private static final String BW_COMMAND = "/bedwars";

    private final Map<String, BukkitRunnable> arenaTabTasks = new ConcurrentHashMap<>();
    private BukkitRunnable hubTask;

    public TabListaBW(BedWarsPlugin plugin) {
        this.plugin = plugin;
        startHubUpdater();
        startTabTitleAnimation();
    }

    public void stop() {
        if (hubTask != null) hubTask.cancel();
        if (tabTitleTask != null) tabTitleTask.cancel();
        arenaTabTasks.values().forEach(BukkitRunnable::cancel);
        arenaTabTasks.clear();
    }

    // zawsze bierzemy board gracza z ArenaManager
    private Scoreboard getBoard(Player p) {
        return plugin.getArenaManager().getOrCreatePlayerBoard(p);
    }

    // =================================================
    // TAB TITLE ANIM (tylko HUB) - ustawia TYLKO HEADER
    // =================================================
    private void startTabTitleAnimation() {
        if (tabTitleTask != null) return;

        tabTitleTask = new BukkitRunnable() {
            @Override
            public void run() {
                String frame = ChatColor.translateAlternateColorCodes('&', TAB_TITLE_FRAMES[tabTitleFrame]);

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().getName().equalsIgnoreCase(BW_HUB_WORLD)
                            || plugin.getPlayerArena().containsKey(p.getUniqueId())) {
                        p.setPlayerListHeader(frame);
                    }
                }

                tabTitleFrame++;
                if (tabTitleFrame >= TAB_TITLE_FRAMES.length) tabTitleFrame = 0;
            }
        };

        tabTitleTask.runTaskTimer(plugin, 0L, 8L);
    }

    // =================================================
    // HUB UPDATER - ustawia TYLKO FOOTER + hub_ prefix
    // (bez unregister co tick => brak migania)
    // =================================================
    private void startHubUpdater() {
        if (hubTask != null) return;

        hubTask = new BukkitRunnable() {
            @Override
            public void run() {
                World hub = Bukkit.getWorld(BW_HUB_WORLD);
                if (hub == null) return;

                int lobbyPlayers = (int) Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.getWorld().getName().equalsIgnoreCase(BW_HUB_WORLD))
                        .count();

                int inGamePlayers = plugin.getPlayerArena().size();

                List<Player> hubPlayers = Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p.getWorld().getName().equalsIgnoreCase(BW_HUB_WORLD))
                        .filter(p -> !plugin.getPlayerArena().containsKey(p.getUniqueId()))
                        .collect(Collectors.toList());

                for (Player p : hubPlayers) {
                    Scoreboard sb = getBoard(p);
                    if (sb == null) continue;
                    if (p.getScoreboard() != sb) p.setScoreboard(sb);

                    updateHubTabForViewer(p, hubPlayers, lobbyPlayers, inGamePlayers);
                }
            }
        };

        hubTask.runTaskTimer(plugin, 0L, 20L);
    }

    private void applyHubTab(Player p, int lobbyPlayers, int inGamePlayers) {
        Scoreboard sb = getBoard(p);
        if (sb == null) return;

        String rank = color(getVaultPrefix(p));

        String teamName = safeTeamName("hub_" + p.getName());
        Team t = sb.getTeam(teamName);
        if (t == null) t = sb.registerNewTeam(teamName);

        t.setPrefix(limit(rank.isEmpty() ? "" : rank + ChatColor.RESET + " ", 64));
        t.setColor(ChatColor.GRAY);
        t.setSuffix("");

        if (!t.hasEntry(p.getName())) t.addEntry(p.getName());

        String footer = color(
                "&8&m───────────────&8[ &bKosmoBW &8]&8&m───────────────\n" +
                        "&7Lobby: &a" + lobbyPlayers + "   &7W grach: &e" + inGamePlayers + "\n" +
                        "&bDiscord: &f" + DISCORD_INVITE + "\n" +
                        "&eDołącz do gry: &b" + BW_COMMAND + "\n" +
                        "&8&m────────────────────────────────────"
        );

        // ✅ tylko footer – header robi animacja
        p.setPlayerListFooter(footer);
    }

    // =================================================
    // ARENA TAB
    // =================================================
    public void updateArenaTab(BedWarsPlugin.Arena arena) {
        if (arena == null) return;

        List<Player> players = getPlayersInArena(arena);

        int alive = (int) players.stream()
                .filter(pl -> !arena.getEliminated().contains(pl.getUniqueId()))
                .count();

        int max = arena.getMaxPlayers();

        for (Player viewer : players) {
            Scoreboard sb = getBoard(viewer);
            if (sb == null) continue;

            if (viewer.getScoreboard() != sb) viewer.setScoreboard(sb);

            cleanupOldArenaTeams(players, sb);

            BedWarsPlugin.Team viewerTeam = plugin.getPlayerTeam().get(viewer.getUniqueId());
            String footer = (viewerTeam == null)
                    ? color("&7Gracze w grze: &a" + alive + "&7/&a" + max) + "\n" + color("&eWybierz drużynę: &bkompas")
                    : color("&7Gracze w grze: &a" + alive + "&7/&a" + max) + "\n" + color("&eTwoja drużyna: " + viewerTeam.getColor() + viewerTeam.getId());

            // header na arenie nie animuje – zostaw null
            viewer.setPlayerListFooter(footer);

            for (Player target : players) {
                String entry = target.getName();
                String teamName = safeTeamName("player_" + entry);

                Team tabTeam = sb.getTeam(teamName);
                if (tabTeam == null) {
                    tabTeam = sb.registerNewTeam(teamName);
                    tabTeam.setAllowFriendlyFire(false);
                    tabTeam.setCanSeeFriendlyInvisibles(true);
                }

                // usuń entry ze starych teamów player_*
                for (Team other : sb.getTeams()) {
                    if (!other.getName().startsWith("player_")) continue;
                    if (other != tabTeam && other.hasEntry(entry)) other.removeEntry(entry);
                }

                BedWarsPlugin.Team bwTeam = plugin.getPlayerTeam().get(target.getUniqueId());
                ChatColor nameColor = (bwTeam == null ? ChatColor.GRAY : bwTeam.getColor());

                String rank = color(getVaultPrefix(target));

                String prefix = rank.isEmpty()
                        ? ("" + nameColor)
                        : (rank + ChatColor.RESET + nameColor + " ");

                tabTeam.setPrefix(limit(prefix, 64));
                tabTeam.setColor(nameColor);
                tabTeam.setSuffix("");

                if (!tabTeam.hasEntry(entry)) tabTeam.addEntry(entry);
            }
        }
    }

    // =================================================
    // PUBLIC: natychmiast HUB (po wyjściu z areny / join)
    // =================================================
    public void applyHubNow(Player p) {
        if (p == null || !p.isOnline()) return;
        if (!p.getWorld().getName().equalsIgnoreCase(BW_HUB_WORLD)) return;
        if (plugin.getPlayerArena().containsKey(p.getUniqueId())) return;

        int lobbyPlayers = (int) Bukkit.getOnlinePlayers().stream()
                .filter(pl -> pl.getWorld().getName().equalsIgnoreCase(BW_HUB_WORLD))
                .count();
        int inGamePlayers = plugin.getPlayerArena().size();

        Scoreboard sb = getBoard(p);
        if (sb == null) return;

        if (p.getScoreboard() != sb) p.setScoreboard(sb);

        // ✅ TU czyścimy resztki po arenie (raz)
        cleanupArenaTeamsFromHub(sb);

        // hub ustaw
        cleanupOldHubTeams(Collections.singletonList(p), sb);
        applyHubTab(p, lobbyPlayers, inGamePlayers);
    }

    private void cleanupArenaTeamsFromHub(Scoreboard sb) {
        for (Team t : new HashSet<>(sb.getTeams())) {
            if (t.getName().startsWith("player_")) {
                t.unregister();
            }
        }
    }

    // =================================================
    // AUTO UPDATER (ARENA)
    // =================================================
    public void startAutoUpdater(BedWarsPlugin.Arena arena) {
        if (arena == null) return;

        String key = arena.getName().toLowerCase();
        if (arenaTabTasks.containsKey(key)) return;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                boolean anyone = plugin.getPlayerArena().values().stream().anyMatch(a -> a == arena);
                if (!anyone) {
                    cancel();
                    arenaTabTasks.remove(key);
                    return;
                }
                updateArenaTab(arena);
            }
        };

        task.runTaskTimer(plugin, 0L, 20L);
        arenaTabTasks.put(key, task);
    }

    public void stopAutoUpdater(BedWarsPlugin.Arena arena) {
        if (arena == null) return;
        BukkitRunnable t = arenaTabTasks.remove(arena.getName().toLowerCase());
        if (t != null) t.cancel();
    }

    // =================================================
    // HELPERY
    // =================================================
    private List<Player> getPlayersInArena(BedWarsPlugin.Arena arena) {
        List<Player> list = new ArrayList<>();
        for (Map.Entry<UUID, BedWarsPlugin.Arena> e : plugin.getPlayerArena().entrySet()) {
            if (e.getValue() != arena) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && p.isOnline()) list.add(p);
        }
        return list;
    }

    private String getVaultPrefix(Player p) {
        Chat chat = plugin.getChat();
        if (chat == null) return "";
        try {
            String prefix = chat.getPlayerPrefix(p);
            return prefix == null ? "" : prefix;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private String limit(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String safeTeamName(String s) {
        s = (s == null ? "unknown" : s).replace(" ", "_");
        return s.length() > 32 ? s.substring(0, 32) : s;
    }

    private void cleanupOldArenaTeams(List<Player> alive, Scoreboard sb) {
        Set<String> aliveNames = new HashSet<>();
        for (Player p : alive) aliveNames.add(p.getName());

        for (Team t : new HashSet<>(sb.getTeams())) {
            if (!t.getName().startsWith("player_")) continue;

            for (String e : new HashSet<>(t.getEntries())) {
                if (!aliveNames.contains(e)) t.removeEntry(e);
            }
            if (t.getEntries().isEmpty()) t.unregister();
        }
    }
    // zamiast: applyHubTab(p, lobbyPlayers, inGamePlayers);
// zrób:
    private void updateHubTabForViewer(Player viewer, List<Player> hubPlayers, int lobbyPlayers, int inGamePlayers) {
        Scoreboard sb = getBoard(viewer);
        if (sb == null) return;

        // 1) usuń resztki areny raz (opcjonalnie)
        cleanupArenaTeamsFromHub(sb);

        // 2) wyczyść entry nieobecnych z hub_* (bez unregister)
        cleanupOldHubTeams(hubPlayers, sb);

        // 3) ustaw footer (header robi animacja)
        String footer = color(
                "&8&m───────────────&8[ &bKosmoBW &8]&8&m───────────────\n" +
                        "&7Lobby: &a" + lobbyPlayers + "   &7W grach: &e" + inGamePlayers + "\n" +
                        "&bDiscord: &f" + DISCORD_INVITE + "\n" +
                        "&eDołącz do gry: &b" + BW_COMMAND + "\n" +
                        "&8&m────────────────────────────────────"
        );
        viewer.setPlayerListFooter(footer);

        // 4) NAJWAŻNIEJSZE: team dla KAŻDEGO targetu na scoreboardzie widza
        for (Player target : hubPlayers) {
            String entry = target.getName();

            // stabilna i unikalna nazwa teamu (bez kolizji po ucięciu)
            String teamName = safeTeamName("hub_" + target.getUniqueId().toString().substring(0, 8));
            Team t = sb.getTeam(teamName);
            if (t == null) t = sb.registerNewTeam(teamName);

            // posprzątaj entry z innych hub_ teamów (żeby nie było duplikatów)
            for (Team other : sb.getTeams()) {
                if (!other.getName().startsWith("hub_")) continue;
                if (other != t && other.hasEntry(entry)) other.removeEntry(entry);
            }

            String rank = color(getVaultPrefix(target));
            String prefix = rank.isEmpty() ? "" : (rank + ChatColor.RESET + " ");

            t.setPrefix(limit(prefix, 64));
            t.setColor(ChatColor.GRAY);
            t.setSuffix("");

            if (!t.hasEntry(entry)) t.addEntry(entry);
        }
    }

    // ✅ HUB: NIE unregister teamów, tylko czyścimy entry, żeby nie zostawały duchy
    private void cleanupOldHubTeams(List<Player> hubPlayers, Scoreboard sb) {
        Set<String> alive = new HashSet<>();
        for (Player p : hubPlayers) alive.add(p.getName());

        for (Team t : sb.getTeams()) {
            if (!t.getName().startsWith("hub_")) continue;

            for (String e : new HashSet<>(t.getEntries())) {
                if (!alive.contains(e)) t.removeEntry(e);
            }
            // ❌ NIE unregister -> brak flicker
        }
    }
}
