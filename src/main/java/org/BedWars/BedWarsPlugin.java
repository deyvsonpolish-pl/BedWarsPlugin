package org.BedWars;

import org.BedWars.arena.ArenaMode;
import java.io.File;
import java.io.IOException;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Bed.Part;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import net.milkbowl.vault.chat.Chat;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.bukkit.Sound;
import org.BedWars.party.PartyHubItem;
import org.BedWars.party.PartySystem;
import java.util.Comparator;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
public class BedWarsPlugin extends JavaPlugin implements Listener, TabExecutor {
    private PartySystem partySystem;
    private PartyHubItem partyHubItem;
    private AutoUpdater updater;
    public PartySystem getPartySystem() { return partySystem; }
    public PartyHubItem getHubPartyItem() { return partyHubItem; }
    private SklepDruzyn sklepDruzyn;
    private ArenaManager arenaManager;
    private static BedWarsPlugin instance;
    private MapResetManager mapResetManager;
    private MapRegionTool mapRegionTool;
    private SklepDruzyn shop;
    private GeneratorDruzyny generatorDruzyny;
    private GeneratorMapy generatorMapy;
    private NPCManager npcManager;
    private ArenaMode arenaMode = ArenaMode.SOLO;
    public AutoUpdater getUpdater() {
        return updater;
    }
    public SklepDruzyn getSklepDruzyn() {
        return this.sklepDruzyn;
    }

    public SklepDruzyn getShop() {
        return this.sklepDruzyn;
    }

    private Chat chat;

    public void recalcLimits(Arena arena) {
        int teamsCount = arena.getTeams().size();
        int perTeam = arena.getArenaMode().getPlayersPerTeam();

        arena.setMaxPlayers(teamsCount * perTeam);
        arena.setMinPlayers(Math.max(2, teamsCount));
    }

    private Scoreboard arenaScoreboard;

    public GeneratorDruzyny getGeneratorDruzyny() {
        return this.generatorDruzyny;
    }

    public GeneratorMapy getGeneratorMapy() {
        return this.generatorMapy;
    }
    private final Map<UUID, Arena> playerArena = new HashMap<>();
    private final Map<UUID, Team> playerTeam = new HashMap<>();
    private SklepDruzyn sklep;
    // Mapy graczy
    public Map<UUID, BedWarsPlugin.Arena> getPlayerArena() {
        return this.playerArena;
    }

    public Map<UUID, Team> getPlayerTeam() {
        return this.playerTeam;
    }
    // Obiekt Vault Chat
    public net.milkbowl.vault.chat.Chat getChat() {
        return this.chat;
    }

    private TabListaBW tabListaBW;


    public TabListaBW getTabListaBW() {
        return tabListaBW;
    }
    public NPCManager getNpcManager() {
        return this.npcManager;
    }
    private ChatAndScoreboard chatAndScoreboard;
    private RankedSystem rankedSystem;
    public RankedSystem getRankedSystem() {
        return rankedSystem;
    }
    @Override
    public void onEnable() {

        instance = this;

        PluginManager pm = getServer().getPluginManager();

        // =========================
        // 0) Vault Chat (opcjonalnie)
        // =========================
        RegisteredServiceProvider<Chat> rsp =
                getServer().getServicesManager().getRegistration(Chat.class);
        this.chat = (rsp != null) ? rsp.getProvider() : null;

        // =========================
        // 1) LobbyMenu - UTWÓRZ RAZ I WŁĄCZ RAZ
        // =========================
        lobbyMenu = new SystemLobbyBWMenu(this);
        lobbyMenu.enable(); // wewnątrz robi registerEvents + load pliku
        //sutoupdate
        this.updater = new AutoUpdater(
                this,
                "deyvsonpolish-pl",
                "BedWarsPlugin"
        );

        updater.startPeriodicCheck();

        getCommand("bwupdate").setExecutor(new UpdateCommand(this));
        // =========================
        // 2) Sklep - UTWÓRZ RAZ, PODEPNIJ LOBBYMENU, ZAREJESTRUJ RAZ
        // =========================
        sklepDruzyn = new SklepDruzyn(this);
        sklepDruzyn.setLobbyMenu(lobbyMenu); // 🔥 KLUCZ (teraz lobbyMenu już istnieje)
        pm.registerEvents(sklepDruzyn, this);

        // =========================
        // 3) Reszta systemów (po tym)
        // =========================
        this.chatAndScoreboard = new ChatAndScoreboard(this);
        this.chatAndScoreboard.start();

        this.tabListaBW = new TabListaBW(this);
        arenaScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

        // =========================
        // 4) Managerowie
        // =========================
        this.npcManager = new NPCManager(this);
        this.mapRegionTool = new MapRegionTool(this);
        this.arenaManager = new ArenaManager(this);
        this.mapResetManager = new MapResetManager(this);
        this.generatorDruzyny = new GeneratorDruzyny(this);
        this.generatorMapy = new GeneratorMapy(this);

        pm.registerEvents(this.npcManager, this);
        pm.registerEvents(this.arenaManager, this);
        pm.registerEvents(this, this);

        // =========================
        // 5) Komendy
        // =========================
        Objects.requireNonNull(getCommand("bedwars")).setExecutor(this);
        Objects.requireNonNull(getCommand("bedwars")).setTabCompleter(this);

        // =========================
        // 6) Load aren (TYLKO RAZ)
        // =========================
        this.arenaManager.loadArenas();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Arena a : arenaManager.getArenas()) {
                if (a.getGeneratorMapy() != null) {
                    a.getGeneratorMapy().rebuildHolograms(1);
                }
            }
        }, 40L);

        new ArenaEnterListener(this);

        // =========================
        // 7) Ranked / Party
        // =========================
        rankedSystem = new RankedSystem(this);
        rankedSystem.enable();

        this.partySystem = new PartySystem(this);
        this.partyHubItem = new PartyHubItem(this);

        pm.registerEvents(partySystem, this);
        pm.registerEvents(partyHubItem, this);

        // =========================
        // 8) Pliki / hologramy / NPC
        // =========================
        File ranks = new File(getDataFolder(), "hologram.yml");
        if (!ranks.exists()) {
            saveResource("hologram.yml", false);
        }

        rankedHolo = new RankedHologramManager(this, rankedSystem);

        PluginCommand holoCmd = getCommand("bwholo");
        if (holoCmd != null) {
            holoCmd.setExecutor(rankedHolo);
            holoCmd.setTabCompleter(rankedHolo);
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            getLogger().info("[RankedHolo] Próba startu hologramu...");
            rankedHolo.start();
        }, 40L);

        // =========================
        // 9) Sklepy z aren
        // =========================
        Bukkit.getScheduler().runTaskLater(this, () -> {
            this.sklepDruzyn.respawnShopsFromArenas();
            getLogger().info("🧑‍🌾 Sklepy villagerów zostały odtworzone.");
        }, 40L);

        // =========================
        // 10) NPC load
        // =========================
        Bukkit.getScheduler().runTaskLater(this, () -> {
            this.removeOldNPCs();
            this.npcManager.loadNPC();
        }, 20L);

        // =========================


        this.getLogger().info("BedWarsPlugin enabled.");
    }
    private SystemLobbyBWMenu lobbyMenu;
    public SystemLobbyBWMenu getLobbyMenu() { return lobbyMenu; }
    private RankedHologramManager rankedHolo;
    @Override
    public void onDisable() {
        getLogger().info("Disabling BedWarsPlugin...");
        if (tabListaBW != null) {
            tabListaBW.stop();
        }
        if (lobbyMenu != null) lobbyMenu.disable();
        // 1. Cancel all plugin tasks
        try {
            Bukkit.getScheduler().cancelTasks(this);
            getLogger().info("All tasks cancelled.");
        } catch (Exception e) {
            getLogger().warning("Error while cancelling tasks: " + e.getMessage());
        }

        // 2. Unregister all event listeners
        try {
            HandlerList.unregisterAll((Plugin) this);
            getLogger().info("All listeners unregistered.");
        } catch (Exception e) {
            getLogger().warning("Error while unregistering listeners: " + e.getMessage());
        }
        if (this.chatAndScoreboard != null) {
            this.chatAndScoreboard.stop();
        }
        // 3. Stop all game-related logic
        if (this.arenaManager != null) {
            for (Arena arena : this.arenaManager.getArenas()) {
                // End games gracefully
                if (arena.isInGame()) {
                    this.arenaManager.endGameWithTeleport(arena, true); // End game, teleport players
                }
                // Stop map generators for each arena
                if (arena.getGeneratorMapy() != null) {
                    arena.getGeneratorMapy().stopForEndGameReset(); // ✅ NIE usuwa hologramów, tylko resetuje fazę 1 + 30/60
                }
            }
            if (rankedHolo != null) rankedHolo.stop();
            if (rankedSystem != null) rankedSystem.disable();
            // Save arenas state
            try {
                this.arenaManager.saveArenas();
                getLogger().info("Arenas saved.");
            } catch (Throwable t) {
                getLogger().severe("Could not save arenas: " + t.getMessage());
                t.printStackTrace();
            }
        }

        // 4. Stop other managers
        if (this.generatorDruzyny != null) {
            this.generatorDruzyny.stop();
            getLogger().info("Team generators stopped.");
        }

        // 5. Remove NPCs and Shops
        if (this.npcManager != null) {
            try {
                this.npcManager.removeAllNPCs();
                getLogger().info("All NPCs removed.");
            } catch (Throwable t) {
                getLogger().severe("Could not remove NPCs: " + t.getMessage());
                t.printStackTrace();
            }
        }

        if (this.sklepDruzyn != null) {
            try {
                this.sklepDruzyn.removeAllShops();
                getLogger().info("All shops removed.");
            } catch (Throwable t) {
                getLogger().severe("Could not remove shops: " + t.getMessage());
                t.printStackTrace();
            }
        }

        // Final cleanup of entities just in case
        this.removeOldNPCs();

        instance = null;
        this.getLogger().info("BedWarsPlugin has been disabled.");
    }

// wymagane importy (dodaj je jeśli ich jeszcze nie masz)
// --------------------------------------------------
// Bezpieczny reload / apply update
// --------------------------------------------------

    /**
     * Wywołaj to przy komendzie /bedwars reload
     */
    public void reloadPluginSafe(CommandSender sender) {

        sender.sendMessage(ChatColor.YELLOW + "⟳ Trwa bezpieczny reload BedWars...");

        Bukkit.getScheduler().runTask(this, () -> {
            try {

                // -----------------------------------------------
                // 1) Spróbuj zatrzymać wszystko, co plugin używa
                // -----------------------------------------------
                try {
                    Bukkit.getScheduler().cancelTasks(this);
                } catch (Exception ignored) {
                }

                try {
                    BedWarsPlugin.getInstance().getNpcManager().removeAllNPCs();
                } catch (Exception ignored) {
                }

                try {
                    BedWarsPlugin.getInstance().getMapResetManager().stopAll();
                } catch (Exception ignored) {
                }

                try {
                    BedWarsPlugin.getInstance().getNpcManager().removeAllNPCs();
                } catch (Exception ignored) {
                }
                try {
                    BedWarsPlugin.getInstance().getMapResetManager().stopAll();
                } catch (Exception ignored) {
                }
                try {
                    BedWarsPlugin.getInstance().getGeneratorDruzyny().stop();
                } catch (Exception ignored) {
                }
// GeneratorMapy i SklepDruzyn analogicznie


                try {
                    BedWarsPlugin.getInstance().getSklepDruzyn().clear();
                } catch (Exception ignored) {
                }


                try {
                    HandlerList.unregisterAll((Listener) this);
                } catch (Exception ignored) {
                }

                // -----------------------------------------------
                // 2) Wyłącz plugin
                // -----------------------------------------------
                PluginManager pm = Bukkit.getServer().getPluginManager();
                pm.disablePlugin(this);


                // -----------------------------------------------
                // 3) Włącz plugin ponownie
                // -----------------------------------------------
                pm.enablePlugin(this);

                sender.sendMessage(ChatColor.GREEN + "✔ BedWars przeładowany pomyślnie!");

            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "❌ Błąd podczas reloadu – sprawdź konsolę.");
                e.printStackTrace();
            }
        });

    }


    private void removeOldNPCs() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.hasMetadata("arenaNPC")) {
                    e.remove();
                }
            }
        }

    }

    public static BedWarsPlugin getInstance() {
        return instance;
    }

    public ArenaManager getArenaManager() {
        return this.arenaManager;
    }

    public MapResetManager getMapResetManager() {
        return this.mapResetManager;
    }

    public MapRegionTool getMapRegionTool() {
        return this.mapRegionTool;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            this.arenaManager.openArenaSelectGUI(p);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // =====================================================
            //             🔁 BEZPIECZNE REŁADOWANIE PLUGINU
            // =====================================================
            case "update":
                // Tylko gracze mogą klikać
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can use this command.");
                    return true;
                }

                p.sendMessage(ChatColor.YELLOW + "🔄 Plugin zostanie przeładowany...");
                // Wykonanie reload w głównym wątku, żeby uniknąć AsyncCatcher
                Bukkit.getScheduler().runTask(BedWarsPlugin.getInstance(), () -> {
                    BedWarsPlugin.getInstance().getServer().dispatchCommand(
                            BedWarsPlugin.getInstance().getServer().getConsoleSender(),
                            "plugman reload " + BedWarsPlugin.getInstance().getDescription().getName()
                    );
                });
                return true;

            // =====================================================
            //                🏗 TWORZENIE ARENY
            // =====================================================
            case "create":
                if (args.length < 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /bedwars create <name> [teamsCount]");
                    return true;
                }

                String name = args[1].toLowerCase();
                if (this.arenaManager.getArena(name) != null) {
                    p.sendMessage(ChatColor.RED + "Arena already exists");
                    return true;
                }

                Arena a = new Arena(name);
                a.setServerLobby(p.getLocation());

                int teamsCount = 4;
                if (args.length >= 3) {
                    try {
                        teamsCount = Math.max(1, Integer.parseInt(args[2]));
                    } catch (Exception ignored) {
                    }
                }

                for (int i = 0; i < teamsCount; ++i) {
                    String id = "TEAM" + (i + 1);

                    ChatColor color = switch (i) {
                        case 0 -> ChatColor.RED;
                        case 1 -> ChatColor.BLUE;
                        case 2 -> ChatColor.GREEN;
                        case 3 -> ChatColor.YELLOW;
                        case 4 -> ChatColor.DARK_AQUA; // CYAN wool
                        case 6 -> ChatColor.AQUA;
                        case 5 -> ChatColor.LIGHT_PURPLE;
                        case 7 -> ChatColor.GRAY;       // ➕ SZARY

                        default -> ChatColor.WHITE;
                    };

                    Material wool = switch (i) {
                        case 0 -> Material.RED_WOOL;
                        case 1 -> Material.BLUE_WOOL;
                        case 2 -> Material.GREEN_WOOL;
                        case 3 -> Material.YELLOW_WOOL;
                        case 4 -> Material.CYAN_WOOL;
                        case 5 -> Material.PURPLE_WOOL;
                        case 6 -> Material.LIGHT_BLUE_WOOL; // ➕
                        case 7 -> Material.GRAY_WOOL;       // ➕
                        default -> Material.WHITE_WOOL;
                    };

                    Team t = new Team(id, color, wool);
                    a.addTeam(t);
                }

                this.arenaManager.addArena(a);
                p.sendMessage(ChatColor.GREEN + "Arena " + name + " created. Use /bedwars edit " + name);
                return true;

            // =====================================================
            //                  🎛 EDYCJA ARENY
            // =====================================================
            case "edit":
                if (args.length < 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /bedwars edit <name>");
                    return true;
                }

                Arena arenaEdit = this.arenaManager.getArena(args[1].toLowerCase());
                if (arenaEdit == null) {
                    p.sendMessage(ChatColor.RED + "Arena not found");
                    return true;
                }

                this.arenaManager.openArenaSetupGUI(p, arenaEdit);
                return true;

            // =====================================================
            //                 🏠 GLOBALNE LOBBY
            // =====================================================
            case "setlobby":
                this.arenaManager.setGlobalLobby(p.getLocation());
                p.sendMessage(ChatColor.GREEN + "✅ Ustawiono globalne lobby i zapisano do config.yml!");
                return true;

            // =====================================================
            //                     🧍 NPC
            // =====================================================
            case "setnpc":
                getInstance().getNpcManager().spawnArenaNPC(p.getLocation());
                p.sendMessage(ChatColor.GREEN + "✔ Ustawiono NPC aren!");
                return true;

            // =====================================================
            //                     💾 ZAPIS
            // =====================================================
            case "save":
                this.arenaManager.saveArenas();
                p.sendMessage(ChatColor.GREEN + "Arenas saved.");
                return true;

            // =====================================================
            //                       🎮 DOŁĄCZ
            // =====================================================
            case "join":
                if (args.length < 2) {
                    p.sendMessage(ChatColor.RED + "Usage: /bedwars join <arena>");
                    return true;
                }

                Arena joinArena = this.arenaManager.getArena(args[1].toLowerCase());
                if (joinArena == null) {
                    p.sendMessage(ChatColor.RED + "Arena not found");
                    return true;
                }

                this.arenaManager.joinArena(p, joinArena);
                return true;

            // =====================================================
            //                   DOMYŚLNE — GU
            // =====================================================
            default:
                this.arenaManager.openArenaSelectGUI(p);
                return true;
        }
    }


    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? Arrays.asList("create", "edit", "setlobby", "setspawn", "setbed", "save", "join", "setnpc", "update") : Collections.emptyList();
    }

    public class ArenaManager implements Listener {
        private final Map<UUID, UUID> lastDamager = new HashMap<>();
        private final Map<UUID, Long> regionVoidMark = new HashMap<>();
        private static final long REGION_VOID_MARK_MS = 2000; // okienko na wykrycie śmierci
        private final Map<UUID, Long> lastDamagerTime = new HashMap<>();
        private static final long PUSH_WINDOW_MS = 8000; // 8s okno na "zepchnięcie"
        private final Map<UUID, Scoreboard> gameBoards = new HashMap<>();
        private static final String P_VOID = "§8[§5⬇§8] ";
        private static final String P_KILL = "§8[§c☠§8] ";
        private static final String P_BED  = "§8[§4🛏§8] ";
        private static final String P_INFO = "§8[§bBW§8] ";
        private final Map<String, org.bukkit.boss.BossBar> arenaBars = new HashMap<>();
        private final Map<String, Long> arenaBarFlashUntil = new HashMap<>();
        private final Map<String, String> arenaBarFlashText = new HashMap<>();
        private BukkitRunnable bossbarTask;
        private boolean isInsideEnterRegion(Arena arena, Location loc) {
            if (arena == null || loc == null) return false;
            if (!arena.hasEnterRegion()) return false;

            Location p1 = arena.getEnterP1();
            Location p2 = arena.getEnterP2();
            if (p1 == null || p2 == null) return false;
            if (p1.getWorld() == null || p2.getWorld() == null) return false;
            if (loc.getWorld() == null) return false;
            if (!loc.getWorld().equals(p1.getWorld())) return false;

            int minX = Math.min(p1.getBlockX(), p2.getBlockX());
            int minY = Math.min(p1.getBlockY(), p2.getBlockY());
            int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
            int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
            int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
            int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();

            return x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
        @EventHandler
        public void onMoveIntoEnterKill(org.bukkit.event.player.PlayerMoveEvent ev) {
            Player p = ev.getPlayer();
            if (ev.getTo() == null) return;

            Arena arena = plugin.getPlayerArena().get(p.getUniqueId());
            if (arena == null) return;
            if (!arena.isInGame()) return;

            // tylko gdy faktycznie wszedł do regionu (żeby nie spamowało co tick)
            boolean fromIn = isInsideEnterRegion(arena, ev.getFrom());
            boolean toIn = isInsideEnterRegion(arena, ev.getTo());
            if (fromIn || !toIn) return;

            // oznacz, że to śmierć z regionu (pod void)
            regionVoidMark.put(p.getUniqueId(), System.currentTimeMillis());

            // “zabij jak void”: zepchnij w dół / teleport pod mapę
            Location to = ev.getTo().clone();
            to.setY(Math.min(to.getY(), -10)); // bezpiecznie pod 0
            p.teleport(to);

            // opcjonalnie: efekt
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        }
        private void clearChat(Player p) {
            for (int i = 0; i < 120; i++) {
                p.sendMessage(" ");
            }
        }
        private void arenaBroadcastInGame(Arena arena, String msg) {
            if (arena == null || !arena.isInGame()) return;
            for (UUID u : arena.getPlayersInArena()) {
                Player pl = Bukkit.getPlayer(u);
                if (pl != null && pl.isOnline()) pl.sendMessage(msg);
            }
        }
        private void startBossbarTickerIfNeeded() {
            if (bossbarTask != null) return;

            bossbarTask = new BukkitRunnable() {
                @Override public void run() {
                    for (Arena a : arenas.values()) {
                        if (a == null || !a.isInGame()) continue;

                        var bar = arenaBars.get(a.getName().toLowerCase());
                        if (bar == null) continue;

                        long now = System.currentTimeMillis();

                        // jeśli flash (event) aktywny -> pokaż event
                        Long until = arenaBarFlashUntil.get(a.getName().toLowerCase());
                        if (until != null && now < until) {
                            String txt = arenaBarFlashText.getOrDefault(a.getName().toLowerCase(), "§c...");
                            bar.setTitle(txt);
                            continue;
                        }
                        a.setPhaseTimeLeft(a.getPhaseTimeLeft() - 1);

                        if (a.getPhaseTimeLeft() <= 0) {
                            a.setCurrentPhase(a.getCurrentPhase() + 1);
                            a.setPhaseTimeLeft(a.getPhaseDuration()); // albo per-faza, jeśli masz różne czasy
                        }
                        // HUD faz (tu wstawimy timery)
                        String hud = buildPhaseHud(a); // funkcja niżej
                        bar.setTitle(hud);
                    }
                }
            };
            bossbarTask.runTaskTimer(plugin, 0L, 20L);
        }
        private String buildPhaseHud(Arena a) {
            int ph = a.getCurrentPhase();          // aktualna faza
            int left = a.getPhaseTimeLeft();       // ile zostało do końca tej fazy (sekundy)

            int nextPhase = ph + 1;

            // jeśli masz np. max 4 fazy, możesz to ograniczyć:
            // if (ph >= 4) return "§7Faza §f" + ph + " §8| §aKoniec";

            return "§7Faza §f" + nextPhase + " §7za: §f" + formatMmSs(left)
                    + " §8| §7Aktualnie: §f" + ph;
        }

        private String formatMmSs(int seconds) {
            if (seconds < 0) seconds = 0;
            int m = seconds / 60;
            int s = seconds % 60;
            return String.format("%02d:%02d", m, s);
        }

        public Scoreboard getOrCreatePlayerBoard(Player p) {
            ScoreboardManager m = Bukkit.getScoreboardManager();
            if (m == null) return Bukkit.getScoreboardManager().getMainScoreboard();

            return gameBoards.computeIfAbsent(p.getUniqueId(), id -> m.getNewScoreboard());
        }

        public Scoreboard getPlayerBoard(Player p) {
            return gameBoards.get(p.getUniqueId());
        }

        // ===== GUI SORT/FILTER STATE =====
        private enum SortMode {PLAYERS_DESC, PLAYERS_ASC, STATUS, NAME_ASC}

        private SortMode sortMode = SortMode.PLAYERS_DESC;
        private int maxPage = 0;

        private enum ModeFilter {ALL, SOLO, DUO, TRIO, V4}

        private ModeFilter modeFilter = ModeFilter.ALL;

        private enum TypeFilter {
            ALL,
            ONLY_RANKED,
            ONLY_NORMAL,
            ONLY_ENABLED,
            ONLY_DISABLED,
            ONLY_INGAME
        }

        private TypeFilter typeFilter = TypeFilter.ALL;

        //
        private Team tryAssignPartyTeamOnJoin(Player p, Arena arena) {
            PartySystem ps = plugin.getPartySystem();
            if (ps == null) return null;

            // DOSTOSUJ NAZWY metod do swojego PartySystem!
            // Założenie: ps.getParty(p) zwraca Party lub null
            var party = ps.getParty(p);
            if (party == null) return null;

            UUID leader = party.getLeader(); // <- dostosuj
            if (leader == null) return null;

            // lider musi być w tej arenie
            if (!arena.getPlayersInArena().contains(leader)) return null;

            Team leaderTeam = plugin.getPlayerTeam().get(leader);
            if (leaderTeam == null) return null;

            // czy jest miejsce w teamie?
            int limit = arena.getArenaMode().getPlayersPerTeam();
            int inTeam = arena.countTeam(leaderTeam, plugin.getPlayerTeam());
            if (inTeam >= limit) return null;

            plugin.getPlayerTeam().put(p.getUniqueId(), leaderTeam);
            leaderTeam.setEverHadPlayer(true);
            return leaderTeam;
        }

        private ItemStack createPagerItem() {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName("§eZmiana strony");

            List<String> lore = new ArrayList<>();
            lore.add("§7Strona: §f" + (page + 1) + "§7/§f" + (maxPage + 1));
            lore.add(" ");
            lore.add("§aLPP §7- wstecz");
            lore.add("§aPPM §7- dalej");
            meta.setLore(lore);

            it.setItemMeta(meta);
            return it;
        }

        private static final int SLOT_PAGE_INFO = 17;
        private int page = 0;

        private int pageSize() {
            return RANKED_SLOTS.length + NORMAL_SLOTS.length;
        }


        private static final int[] RANKED_SLOTS = {
                10, 11, 12, 13, 14, 15, 16
        };

        private static final int[] NORMAL_SLOTS = {
                28, 29, 30, 31, 32, 33, 34
        };
        private static final int ARENAS_PER_PAGE =
                RANKED_SLOTS.length + NORMAL_SLOTS.length;
        // Panel (rząd 3)
        private static final int SLOT_SORT_PREV = 19;
        private static final int SLOT_SORT_NEXT = 20;
        private static final int SLOT_MODE_FILTER = 21;
        private static final int SLOT_TYPE_FILTER = 22;
        private static final int SLOT_REFRESH = 23;
        private static final int SLOT_SEARCH = 24; // opcjonalnie (na razie tylko informacja)
        private static final int SLOT_CLOSE = 25;

        private int statusPriority(Arena a) {
            // mniejsze = wyżej
            String status = plugin.getMapResetManager().getArenaStatus(a.getName());
            if (status != null && status.equalsIgnoreCase("Restart")) return 4;
            if (a.isInGame()) return 3;
            if (a.isCountingDown()) return 2;
            // "Badanie terenu" blokujesz join i jest jak "niegotowa"
            if (status != null && status.equalsIgnoreCase("Badanie terenu")) return 1;
            return 0; // Gotowa
        }

        private boolean passesModeFilter(Arena a) {
            ArenaMode m = a.getArenaMode();

            return switch (modeFilter) {
                case ALL -> true;
                case SOLO -> m == ArenaMode.SOLO;
                case DUO -> m == ArenaMode.DUO;
                case TRIO -> m == ArenaMode.TRIO;
                case V4 -> m.getPlayersPerTeam() == 4;
            };
        }


        private boolean passesTypeFilter(Arena a) {
            return switch (typeFilter) {
                case ALL -> true;
                case ONLY_RANKED -> a.isRanked();
                case ONLY_NORMAL -> !a.isRanked();
                case ONLY_ENABLED -> a.isEnabled();
                case ONLY_DISABLED -> !a.isEnabled();
                case ONLY_INGAME -> a.isInGame();
            };
        }

        private List<Arena> collectFilteredArenas(boolean ranked) {
            return arenas.values().stream()
                    .filter(a -> a.isRanked() == ranked)
                    .filter(this::passesModeFilter)
                    .filter(this::passesTypeFilter)
                    .toList();
        }

        private void sortArenas(List<Arena> list) {
            Comparator<Arena> byPlayers = Comparator.comparingInt(a -> {
                int alive = (int) a.getPlayersInArena().stream()
                        .filter(uuid -> !a.getEliminated().contains(uuid))
                        .count();
                return alive;
            });

            Comparator<Arena> byStatus = Comparator.comparingInt(this::statusPriority);
            Comparator<Arena> byName = Comparator.comparing(Arena::getName, String.CASE_INSENSITIVE_ORDER);

            switch (sortMode) {
                case PLAYERS_DESC -> list.sort(byPlayers.reversed().thenComparing(byStatus).thenComparing(byName));
                case PLAYERS_ASC -> list.sort(byPlayers.thenComparing(byStatus).thenComparing(byName));
                case STATUS -> list.sort(byStatus.thenComparing(byPlayers.reversed()).thenComparing(byName));
                case NAME_ASC -> list.sort(byName.thenComparing(byPlayers.reversed()));
            }
        }

        private ItemStack createArenaItem(Arena a) {

            String status = plugin.getMapResetManager().getArenaStatus(a.getName());
            int progress = plugin.getMapResetManager().getArenaProgress(a.getName());

            int alivePlayers = (int) a.getPlayersInArena().stream()
                    .filter(uuid -> !a.getEliminated().contains(uuid))
                    .count();

            // ✅ 1. NAJPIERW WYBIERZ MATERIAŁ
            Material mat;

            if (!a.isEnabled()) {
                mat = Material.BARRIER; // ⛔ mapa wyłączona MA ZAWSZE PRIORYTET
            } else if (status != null && status.equalsIgnoreCase("Restart")) {
                mat = Material.YELLOW_STAINED_GLASS;
            } else if (!a.isInGame() && status != null && status.equalsIgnoreCase("Badanie terenu")) {
                mat = Material.RED_STAINED_GLASS;
            } else if (a.isInGame()) {
                mat = Material.ORANGE_STAINED_GLASS;
            } else if (a.isRanked()) {
                mat = Material.NETHER_STAR;
            } else {
                mat = Material.LIME_STAINED_GLASS;
            }

            // ✅ 2. DOPIERO TERAZ TWORZYSZ ITEM
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();

            meta.setDisplayName(ChatColor.AQUA + a.getName());

            List<String> lore = new ArrayList<>();
            lore.add(a.isRanked() ? "§d§lRANKED" : "§a§lNORMAL");
            lore.add("§7Gracze: §f" + alivePlayers + "§7/§f" + a.getMaxPlayers());
            lore.add("§7Tryb: §e" + a.getArenaMode().getDisplay());

            if (!a.isEnabled()) {
                lore.add("§cMapa wyłączona");
            }

            if (a.isRanked()) {
                lore.add("§7Min ranga: §b" + prettyReq(a.getRankedMin()));
                RankedSystem rs = plugin.getRankedSystem();
                if (rs != null) {
                    lore.add("§7Wymagane pkt: §e" + rs.getMinPointsFor(a.getRankedMin()));
                }
            }

            if (status != null && status.equalsIgnoreCase("Restart")) {
                lore.add("§7Status: §eRestart mapy");
                lore.add("§7Postęp: §a" + progress + "%");
            } else if (!a.isInGame() && status != null && status.equalsIgnoreCase("Badanie terenu")) {
                lore.add("§7Status: §cBadanie terenu");
            } else if (a.isInGame()) {
                lore.add("§7Status: §6Gra w toku");
            } else if (a.isCountingDown()) {
                lore.add("§7Status: §bOdliczanie");
            } else {
                lore.add("§7Status: §aGotowa");
            }

            lore.add(" ");
            lore.add(a.isEnabled()
                    ? "§eKliknij aby dołączyć"
                    : "§7Nie można dołączyć");

            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;
        }

        private void placeControlPanel(Inventory inv) {
            inv.setItem(SLOT_SORT_PREV, createGuiItem(Material.HOPPER, "§bSort: §f" + sortModeName() + " §7(◀)"));
            inv.setItem(SLOT_SORT_NEXT, createGuiItem(Material.HOPPER, "§bSort: §f" + sortModeName() + " §7(▶)"));

            inv.setItem(SLOT_MODE_FILTER, createGuiItem(Material.COMPARATOR, "§eTryb: §f" + modeFilterName() + " §7(klik)"));
            inv.setItem(SLOT_TYPE_FILTER, createGuiItem(Material.PAPER, "§dFiltr: §f" + typeFilterName() + " §7(klik)"));

            inv.setItem(SLOT_REFRESH, createGuiItem(Material.SLIME_BALL, "§aOdśwież"));
            inv.setItem(SLOT_SEARCH, createGuiItem(Material.NAME_TAG, "§7Szukaj: §f(wkrótce)"));
            inv.setItem(SLOT_CLOSE, createGuiItem(Material.BARRIER, "§cZamknij"));
            inv.setItem(SLOT_PAGE_INFO, createGuiItem(
                    Material.PAPER,
                    "§7Strona: §f" + (page + 1) + "§7/§f" + (maxPage + 1)
            ));
        }

        private String sortModeName() {
            return switch (sortMode) {
                case PLAYERS_DESC -> "Gracze ↓";
                case PLAYERS_ASC -> "Gracze ↑";
                case STATUS -> "Status";
                case NAME_ASC -> "Nazwa A→Z";
            };
        }

        private String modeFilterName() {
            return switch (modeFilter) {
                case ALL -> "Wszystkie";
                case SOLO -> "SOLO";
                case DUO -> "DUO";
                case TRIO -> "TRIO";
                case V4 -> "4v4";
            };
        }

        private String typeFilterName() {
            return switch (typeFilter) {
                case ALL -> "Wszystkie";
                case ONLY_RANKED -> "Tylko Ranked";
                case ONLY_NORMAL -> "Tylko Normal";
                case ONLY_ENABLED -> "Tylko Włączone";
                case ONLY_DISABLED -> "Tylko Wyłączone";
                case ONLY_INGAME -> "Tylko w grze";
            };
        }


        private final BedWarsPlugin plugin;
        private final Map<String, Arena> arenas = new LinkedHashMap();
        private final Map<UUID, BukkitRunnable> observerTasks = new HashMap();
        private final String GUI_MAIN;

        private final String GUI_SETUP;
        private final String TEAM_GUI_TITLE;
        private final File arenasFile;
        private Location globalLobby;
        private final Set<Location> playerPlacedBlocks;

        public void removePlayerFromArena(Player player) {
            plugin.getPlayerArena().remove(player.getUniqueId());
            plugin.getPlayerTeam().remove(player.getUniqueId());
        }

        public void removeItemsFromArena(Arena arena) {
            World w = arena.getWorld();

            for (Entity e : w.getEntities()) {
                if (e instanceof Item) {
                    e.remove();
                }
            }

        }

        public boolean canJoinTeam(Arena arena, Team team) {
            int limit = arena.getArenaMode().getPlayersPerTeam();
            return arena.countTeam(team, plugin.getPlayerTeam()) < limit;
        }

        public void resetArenaState(Arena arena) {
            arena.getPlayersInArena().clear();
            arena.getEliminated().clear();
            arena.setInGame(false);
            arena.setCountingDown(false);
            arena.setCountdown(30);
            arena.setPhaseTimeLeft(arena.getPhaseDuration());
            arena.setCurrentPhase(1);

            for (Team t : arena.getTeams()) {
                t.setBedDestroyed(false);
                t.setEverHadPlayer(false); // ✅ reset flagi
            }

            Iterator<UUID> it = plugin.getPlayerTeam().keySet().iterator();

            while (it.hasNext()) {
                UUID u = (UUID) it.next();
                Player p = Bukkit.getPlayer(u);
                if (p == null || !p.isOnline()) {
                    it.remove();
                    plugin.getPlayerArena().remove(u);
                }
            }

        }

        public Arena getArenaForPlayer(Player player) {
            for (Arena arena : this.arenas.values()) {
                if (arena.getPlayersInArena().contains(player.getUniqueId())) {
                    return arena;
                }
            }

            return null;
        }

        public Team getPlayerTeam(Player player) {
            return plugin.getPlayerTeam().get(player.getUniqueId());
        }

        public ArenaManager(BedWarsPlugin plugin) {
            this.GUI_MAIN = String.valueOf(ChatColor.GREEN) + "BedWars - Areny";
            this.GUI_SETUP = String.valueOf(ChatColor.BLUE) + "BedWars  Areny";
            this.TEAM_GUI_TITLE = String.valueOf(ChatColor.YELLOW) + "BedWars Wybierz drużynę";
            this.globalLobby = null;
            this.playerPlacedBlocks = new HashSet();
            this.plugin = plugin;
            this.arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

        }

        public Arena getArena(String name) {
            return (Arena) this.arenas.get(name.toLowerCase());
        }

        public Collection<Arena> getArenas() {
            return this.arenas.values();
        }

        public void addArena(Arena a) {
            this.arenas.put(a.getName().toLowerCase(), a);
        }

        public void setGlobalLobby(Location loc) {
            this.globalLobby = loc;
            this.plugin.getConfig().set("globalLobby", this.locToMap(loc));
            this.plugin.saveConfig();
        }

        public Location getGlobalLobby() {
            if (this.globalLobby != null) {
                return this.globalLobby;
            } else {
                if (this.plugin.getConfig().contains("globalLobby")) {
                    this.globalLobby = this.mapToLoc(this.plugin.getConfig().getConfigurationSection("globalLobby"));
                }

                return this.globalLobby;
            }
        }

        public void saveArenas() {
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.arenasFile);

                for (Arena a : this.arenas.values()) {

                    String base = "arenas." + a.getName();
                    cfg.set(base + ".enabled", a.isEnabled());
                    cfg.set(base + ".name", a.getName());
                    cfg.set(base + ".minPlayers", a.getMinPlayers());
                    cfg.set(base + ".maxPlayers", a.getMaxPlayers());
                    cfg.set(base + ".lobby", this.locToMap(a.getLobby()));

                    if (a.getEnterP1() != null && a.getEnterP2() != null) {
                        cfg.set(base + ".enter.p1", locToMap(a.getEnterP1()));
                        cfg.set(base + ".enter.p2", locToMap(a.getEnterP2()));
                    } else {
                        cfg.set(base + ".enter", null);
                    }

                    cfg.set(base + ".arenaMode", a.getArenaMode().name());
                    cfg.set(base + ".protectedBlocks",
                            a.getProtectedBlocks().stream().map(Enum::name).collect(Collectors.toList()));
                    cfg.set(base + ".ranked", a.isRanked());
                    cfg.set(base + ".rankedMin", a.getRankedMin());

                    String teamsBase = base + ".teams";
                    cfg.set(teamsBase, null);

                    for (Team t : a.getTeams()) {
                        String tBase = teamsBase + "." + t.getId();
                        cfg.set(tBase + ".color", t.getColor() != null ? t.getColor().name() : ChatColor.WHITE.name());
                        cfg.set(tBase + ".wool", t.getWoolMaterial() != null ? t.getWoolMaterial().name() : Material.WHITE_WOOL.name());
                        cfg.set(tBase + ".bed", this.locToMap(t.getBedLocation()));

                        List<Map<String, Object>> spawns = t.getSpawns().stream()
                                .filter(Objects::nonNull)
                                .map(this::locToMap)
                                .collect(Collectors.toList());
                        cfg.set(tBase + ".spawns", spawns);

                        if (t.getShopLocation() != null) {
                            cfg.set(tBase + ".shop", this.locToMap(t.getShopLocation()));
                        }

                        if (t.getGeneratorLocation() != null) {
                            cfg.set(tBase + ".generator", this.locToMap(t.getGeneratorLocation()));
                        }
                    }

                    // ✅ map generators (bezpiecznie)
                    if (a.getGeneratorMapy() != null) {
                        List<Map<String, Object>> diamondGens = a.getGeneratorMapy().getDiamondGenerators().stream()
                                .filter(Objects::nonNull)
                                .map(this::locToMap)
                                .collect(Collectors.toList());

                        List<Map<String, Object>> emeraldGens = a.getGeneratorMapy().getEmeraldGenerators().stream()
                                .filter(Objects::nonNull)
                                .map(this::locToMap)
                                .collect(Collectors.toList());

                        cfg.set(base + ".mapGenerators.diamond", diamondGens);
                        cfg.set(base + ".mapGenerators.emerald", emeraldGens);
                    } else {
                        cfg.set(base + ".mapGenerators", null);
                    }
                }

                cfg.save(this.arenasFile);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        public void loadArenas() {
            if (!this.arenasFile.exists()) return;

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.arenasFile);
            if (!cfg.contains("arenas")) return;

            for (String key : cfg.getConfigurationSection("arenas").getKeys(false)) {
                String base = "arenas." + key;
                String name = cfg.getString(base + ".name", key);
                Arena a = new Arena(name);

                // Min gracze do startu
                a.setMinPlayers(cfg.getInt(base + ".minPlayers", 2));
                a.setEnabled(cfg.getBoolean(base + ".enabled", true));

                // Tryb areny
                if (cfg.contains(base + ".arenaMode")) {
                    try {
                        a.setArenaMode(ArenaMode.valueOf(cfg.getString(base + ".arenaMode")));
                    } catch (IllegalArgumentException e) {
                        a.setArenaMode(ArenaMode.SOLO);
                    }
                } else {
                    a.setArenaMode(ArenaMode.SOLO);
                }

                // Lobby
                a.setLobby(this.mapToLoc(cfg.getConfigurationSection(base + ".lobby")));
                a.setRanked(cfg.getBoolean(base + ".ranked", false));
                a.setRankedMin(cfg.getString(base + ".rankedMin", "gold:I"));

                // Bloki chronione
                if (cfg.contains(base + ".protectedBlocks")) {
                    List<String> list = cfg.getStringList(base + ".protectedBlocks");
                    List<Material> blocks = new ArrayList<>();
                    for (String s : list) {
                        try {
                            blocks.add(Material.valueOf(s));
                        } catch (Exception ignored) {}
                    }
                    a.setProtectedBlocks(blocks);
                }

                // Drużyny
                ConfigurationSection teamsSec = cfg.getConfigurationSection(base + ".teams");
                if (teamsSec == null) {
                    ChatColor[] colors = {ChatColor.RED, ChatColor.BLUE, ChatColor.GREEN, ChatColor.YELLOW};
                    Material[] wools = {Material.RED_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL, Material.YELLOW_WOOL};
                    for (int i = 0; i < 4; i++) {
                        String id = "TEAM" + (i + 1);
                        a.addTeam(new Team(id, colors[i], wools[i]));
                    }
                } else {
                    for (String id : teamsSec.getKeys(false)) {
                        String colorName = teamsSec.getString(id + ".color", ChatColor.WHITE.name());
                        String woolName = teamsSec.getString(id + ".wool", Material.WHITE_WOOL.name());

                        ChatColor color = ChatColor.valueOf(colorName);
                        Material wool;
                        try {
                            wool = Material.valueOf(woolName);
                        } catch (Exception e) {
                            wool = Material.WHITE_WOOL;
                        }

                        Team t = new Team(id, color, wool);

                        // Spawny
                        if (teamsSec.contains(id + ".spawns")) {
                            for (Object o : teamsSec.getList(id + ".spawns", new ArrayList<>())) {
                                if (o instanceof Map) {
                                    Location l = this.mapToLoc((Map<?, ?>) o);
                                    if (l != null) t.addSpawn(l);
                                }
                            }
                        }

                        // Bed
                        if (teamsSec.contains(id + ".bed")) {
                            t.setBedLocation(this.mapToLoc(teamsSec.getConfigurationSection(id + ".bed")));
                        }

                        // Shop
                        if (teamsSec.contains(id + ".shop")) {
                            t.setShopLocation(this.mapToLoc(teamsSec.getConfigurationSection(id + ".shop")));
                        }

                        // Generator drużyny
                        if (teamsSec.contains(id + ".generator")) {
                            Location genLoc = this.mapToLoc(teamsSec.getConfigurationSection(id + ".generator"));
                            t.setGeneratorLocation(genLoc);
                            if (genLoc != null) this.plugin.getGeneratorDruzyny().setGenerator(t.getId(), genLoc);
                        }

                        a.addTeam(t);
                    }
                }

                // Map generators
                a.setGeneratorMapy(new GeneratorMapy(this.plugin));
                GeneratorMapy mapGen = a.getGeneratorMapy();
                mapGen.stopTaskOnly(); // na wszelki wypadek

                if (cfg.contains(base + ".mapGenerators.diamond")) {
                    for (Object o : cfg.getList(base + ".mapGenerators.diamond", new ArrayList<>())) {
                        if (o instanceof Map) {
                            Location loc = this.mapToLoc((Map<?, ?>) o);

                            if (loc == null) {
                                plugin.getLogger().warning("[Arenas] Pomijam DIAMOND generator (loc==null) arena=" + name + " wpis=" + o);
                                continue;
                            }

                            boolean exists = mapGen.getDiamondGenerators().stream()
                                    .filter(Objects::nonNull)
                                    .anyMatch(l -> l.getWorld() != null
                                            && loc.getWorld() != null
                                            && l.getWorld().equals(loc.getWorld())
                                            && l.distanceSquared(loc) < 0.01);

                            // ✅ ważne: NIE dodawaj loc ręcznie, placeholder już to robi + hologram
                            if (!exists) {
                                mapGen.addDiamondGeneratorPlaceholder(loc);
                            }
                        }
                    }
                }

                if (cfg.contains(base + ".mapGenerators.emerald")) {
                    for (Object o : cfg.getList(base + ".mapGenerators.emerald", new ArrayList<>())) {
                        if (o instanceof Map) {
                            Location loc = this.mapToLoc((Map<?, ?>) o);

                            if (loc == null) {
                                plugin.getLogger().warning("[Arenas] Pomijam EMERALD generator (loc==null) arena=" + name + " wpis=" + o);
                                continue;
                            }

                            boolean exists = mapGen.getEmeraldGenerators().stream()
                                    .filter(Objects::nonNull)
                                    .anyMatch(l -> l.getWorld() != null
                                            && loc.getWorld() != null
                                            && l.getWorld().equals(loc.getWorld())
                                            && l.distanceSquared(loc) < 0.01);

                            if (!exists) {
                                mapGen.addEmeraldGeneratorPlaceholder(loc);
                            }
                        }
                    }
                }

                // Region wejścia
                if (cfg.contains(base + ".enter.p1") && cfg.contains(base + ".enter.p2")) {
                    Location p1 = mapToLoc(cfg.getConfigurationSection(base + ".enter.p1"));
                    Location p2 = mapToLoc(cfg.getConfigurationSection(base + ".enter.p2"));
                    a.setEnterP1(p1);
                    a.setEnterP2(p2);
                }

                // Najważniejsze: przelicz limity po dodaniu drużyn
                a.recalcLimits();

                this.arenas.put(a.getName().toLowerCase(), a);
            }
        }
        private Map<String, Object> locToMap(Location loc) {
            if (loc == null) {
                return null;
            } else {
                Map<String, Object> map = new LinkedHashMap();
                map.put("world", loc.getWorld().getName());
                map.put("x", loc.getX());
                map.put("y", loc.getY());
                map.put("z", loc.getZ());
                map.put("yaw", loc.getYaw());
                map.put("pitch", loc.getPitch());
                return map;
            }
        }


        private Location mapToLoc(ConfigurationSection sec) {
            return sec == null ? null : this.mapToLoc(sec.getValues(false));
        }

        private Location mapToLoc(Map<?, ?> map) {
            if (map == null) return null;

            try {
                Object worldObj = map.get("world");
                if (!(worldObj instanceof String worldName)) return null;

                World w = Bukkit.getWorld(worldName);
                if (w == null) {
                    plugin.getLogger().warning("[Arenas] World nie jest wczytany, próbuję załadować: " + worldName);

                    try {
                        w = Bukkit.createWorld(new org.bukkit.WorldCreator(worldName));
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[Arenas] Nie udało się załadować świata: " + worldName);
                        t.printStackTrace();
                    }

                    if (w == null) {
                        plugin.getLogger().warning("[Arenas] Nadal nie ma świata: " + worldName + " -> loc=null");
                        return null;
                    }
                }

                Object xO = map.get("x");
                Object yO = map.get("y");
                Object zO = map.get("z");
                if (!(xO instanceof Number) || !(yO instanceof Number) || !(zO instanceof Number)) return null;

                double x = ((Number) xO).doubleValue();
                double y = ((Number) yO).doubleValue();
                double z = ((Number) zO).doubleValue();

                float yaw = 0f;
                float pitch = 0f;

                Object yawO = map.get("yaw");
                Object pitchO = map.get("pitch");
                if (yawO instanceof Number) yaw = ((Number) yawO).floatValue();
                if (pitchO instanceof Number) pitch = ((Number) pitchO).floatValue();

                return new Location(w, x, y, z, yaw, pitch);

            } catch (Throwable t) {
                plugin.getLogger().warning("[Arenas] Nie udało się wczytać lokacji: " + map);
                t.printStackTrace();
                return null;
            }
        }


        public void openArenaSelectGUI(Player player) {

            Inventory inv = Bukkit.createInventory((InventoryHolder) null, 45, this.GUI_MAIN);
            ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta borderMeta = border.getItemMeta();
            borderMeta.setDisplayName(" ");
            border.setItemMeta(borderMeta);

            for (int i = 0; i < 45; i++) {
                int r = i / 9;
                int c = i % 9;

                boolean isBorder = (r == 0 || r == 4 || c == 0 || c == 8);
                boolean isPager =
                        i == SLOT_PAGE_INFO;

                if (isBorder && !isPager) {
                    inv.setItem(i, border);
                }
            }
            page = 0;
            this.updateArenaGUI(inv);
            player.openInventory(inv);
            this.startArenaGuiAutoRefresh(player, inv);
        }

        private void updateArenaGUI(@NotNull Inventory inv) {
            for (int slot : RANKED_SLOTS) inv.setItem(slot, null);
            for (int slot : NORMAL_SLOTS) inv.setItem(slot, null);

            // ramka + panel
            ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta bm = border.getItemMeta();
            bm.setDisplayName(" ");
            border.setItemMeta(bm);

            for (int i = 0; i < 45; i++) {
                int r = i / 9;
                int c = i % 9;

                boolean isBorder = (r == 0 || r == 4 || c == 0 || c == 8);
                boolean isPager =
                        i == SLOT_PAGE_INFO;

                if (isBorder && !isPager) inv.setItem(i, border);
            }

            placeControlPanel(inv);

            // ✅ LISTY OSOBNO
            List<Arena> ranked = new ArrayList<>(collectFilteredArenas(true));
            List<Arena> normal = new ArrayList<>(collectFilteredArenas(false));

            sortArenas(ranked);
            sortArenas(normal);

            int perSection = RANKED_SLOTS.length; // 7 (tyle samo co NORMAL_SLOTS)

            int rankedMaxPage = (ranked.isEmpty()) ? 0 : (ranked.size() - 1) / perSection;
            int normalMaxPage = (normal.isEmpty()) ? 0 : (normal.size() - 1) / perSection;

            // ✅ maxPage = max z obu sekcji
            this.maxPage = Math.max(rankedMaxPage, normalMaxPage);
            page = Math.max(0, Math.min(page, maxPage));

            // ✅ SUBLISTY DLA BIEŻĄCEJ STRONY (RANKED)
            int rStart = page * perSection;
            int rEnd = Math.min(rStart + perSection, ranked.size());
            List<Arena> rankedPage = (rStart >= rEnd) ? Collections.emptyList() : ranked.subList(rStart, rEnd);

            // ✅ SUBLISTY DLA BIEŻĄCEJ STRONY (NORMAL)
            int nStart = page * perSection;
            int nEnd = Math.min(nStart + perSection, normal.size());
            List<Arena> normalPage = (nStart >= nEnd) ? Collections.emptyList() : normal.subList(nStart, nEnd);

            // ✅ WYPEŁNIANIE SLOTÓW
            for (int i = 0; i < rankedPage.size() && i < RANKED_SLOTS.length; i++) {
                inv.setItem(RANKED_SLOTS[i], createArenaItem(rankedPage.get(i)));
            }
            for (int i = 0; i < normalPage.size() && i < NORMAL_SLOTS.length; i++) {
                inv.setItem(NORMAL_SLOTS[i], createArenaItem(normalPage.get(i)));
            }

            // Nagłówki sekcji
            inv.setItem(9, createGuiItem(Material.PURPLE_STAINED_GLASS_PANE, "§d§lRANKED"));
            inv.setItem(27, createGuiItem(Material.LIME_STAINED_GLASS_PANE, "§a§lNORMAL"));
            inv.setItem(SLOT_PAGE_INFO, createPagerItem());
        }


        private void startArenaGuiAutoRefresh(final Player player, final Inventory inv) {
            (new BukkitRunnable() {
                public void run() {
                    if (player.isOnline() && player.getOpenInventory() != null && player.getOpenInventory().getTitle().equals(ArenaManager.this.GUI_MAIN)) {
                        ArenaManager.this.updateArenaGUI(inv);
                    } else {
                        this.cancel();
                    }
                }
            }).runTaskTimer(this.plugin, 0L, 40L);
        }

        public void openArenaSetupGUI(Player player, Arena arena) {
            String var10002 = this.GUI_SETUP;
            Inventory inv = Bukkit.createInventory((InventoryHolder) null, 36, var10002 + " - " + arena.getName());
            inv.setItem(0, this.createGuiItem(Material.OAK_SIGN, String.valueOf(ChatColor.GREEN) + "Ustaw lobby"));
            inv.setItem(1, this.createGuiItem(Material.GRAY_WOOL, String.valueOf(ChatColor.AQUA) + "Dodaj drużynę"));
            inv.setItem(2, this.createGuiItem(Material.BLAZE_ROD, String.valueOf(ChatColor.GOLD) + "\ud83e\ude84 Ustaw teren regeneracji"));
            inv.setItem(4, createGuiItem(
                    Material.COMPARATOR,
                    ChatColor.AQUA + "Tryb areny: " + arena.getArenaMode().getDisplay()
            ));
            inv.setItem(16, this.createGuiItem(Material.EMERALD_BLOCK, String.valueOf(ChatColor.GREEN) + "\ud83d\udcbe Zapisz teren regeneracji"));
            int slot = 9;

            int[] teamSlots = {9, 10, 11, 12, 13, 14, 15, 17}; // 8 miejsc (16 pomijamy bo zajęty)

            int i = 0;
            for (Team t : arena.getTeams()) {
                if (i >= teamSlots.length) break;

                Material wool = t.getWoolMaterial() != null ? t.getWoolMaterial() : Material.WHITE_WOOL;
                inv.setItem(teamSlots[i], createGuiItem(wool, String.valueOf(t.getColor()) + t.getId()));
                i++;
            }

            inv.setItem(5, this.createGuiItem(Material.CHEST, String.valueOf(ChatColor.GOLD) + "Bloki chronione"));
            inv.setItem(6, this.createGuiItem(Material.REDSTONE_BLOCK, String.valueOf(ChatColor.RED) + "Zapisz ustawienia"));
            inv.setItem(8, this.createGuiItem(Material.BARRIER, String.valueOf(ChatColor.DARK_RED) + "Zamknij"));
            Material var7 = Material.PLAYER_HEAD;
            String var9 = String.valueOf(ChatColor.AQUA);
            inv.setItem(18, this.createGuiItem(var7, var9 + "Min graczy: " + arena.getMinPlayers()));
            var7 = Material.DIAMOND_HELMET;
            var9 = String.valueOf(ChatColor.AQUA);
            inv.setItem(19, this.createGuiItem(var7, var9 + "Max graczy: " + arena.getMaxPlayers()));
            inv.setItem(21, this.createGuiItem(Material.DIAMOND_BLOCK, String.valueOf(ChatColor.AQUA) + "Ustaw generator diamentów"));
            inv.setItem(22, this.createGuiItem(Material.EMERALD_BLOCK, String.valueOf(ChatColor.GREEN) + "Ustaw generator emeraldów"));
            inv.setItem(23, this.createGuiItem(Material.RED_BANNER, String.valueOf(ChatColor.RED) + "Usuń wszystkie generatory"));
            inv.setItem(24, createGuiItem(
                    arena.isRanked() ? Material.EMERALD : Material.REDSTONE,
                    ChatColor.GOLD + "Ranked: " + (arena.isRanked() ? ChatColor.GREEN + "TAK" : ChatColor.RED + "NIE")
            ));

            inv.setItem(25, createGuiItem(
                    Material.GOLD_INGOT,
                    ChatColor.YELLOW + "Min ranga: " + ChatColor.AQUA + prettyReq(arena.getRankedMin())
            ));
            inv.setItem(26, createGuiItem(
                    arena.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                    ChatColor.YELLOW + "Mapa: " +
                            (arena.isEnabled() ? ChatColor.GREEN + "WŁĄCZONA" : ChatColor.RED + "WYŁĄCZONA")
            ));
            inv.setItem(27, createGuiItem(Material.IRON_AXE, "§6🪓 Ustaw teren mapy (wejście)"));
            inv.setItem(28, createGuiItem(Material.EMERALD_BLOCK, "§a💾 Zapisz teren mapy (wejście)"));
            player.openInventory(inv);
            player.setMetadata("editingArena", new FixedMetadataValue(this.plugin, arena.getName()));
            String var10001 = String.valueOf(ChatColor.YELLOW);
            player.sendMessage(var10001 + "Edytujesz arenę: " + arena.getName());
        }

        private ItemStack createGuiItem(Material mat, String name) {
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(name);
            item.setItemMeta(meta);
            return item;
        }

        private ChatColor materialToChatColor(Material m) {
            return switch (m) {
                case WHITE_WOOL -> ChatColor.WHITE;

                case ORANGE_WOOL -> ChatColor.GOLD;
                case MAGENTA_WOOL -> ChatColor.DARK_PURPLE;
                case LIGHT_BLUE_WOOL -> ChatColor.AQUA;

                case YELLOW_WOOL -> ChatColor.YELLOW;
                case LIME_WOOL -> ChatColor.GREEN;
                case PINK_WOOL -> ChatColor.LIGHT_PURPLE;

                case GRAY_WOOL -> ChatColor.DARK_GRAY;
                case LIGHT_GRAY_WOOL -> ChatColor.GRAY;

                case CYAN_WOOL -> ChatColor.DARK_AQUA;
                case PURPLE_WOOL -> ChatColor.DARK_PURPLE;
                case BLUE_WOOL -> ChatColor.BLUE;

                case BROWN_WOOL -> ChatColor.GOLD;
                case GREEN_WOOL -> ChatColor.DARK_GREEN;
                case RED_WOOL -> ChatColor.RED;

                case BLACK_WOOL -> ChatColor.BLACK;

                default -> ChatColor.WHITE;
            };
        }

        private String prettyReq(String req) {
            if (req == null) return "brak";
            String r = req;
            String s = "";
            if (req.contains(":")) {
                String[] p = req.split(":", 2);
                r = p[0];
                s = p[1];
            }
            if (r.isEmpty()) return "brak";
            String rankName = r.substring(0, 1).toUpperCase() + r.substring(1).toLowerCase();
            return rankName + (s.isEmpty() ? "" : " " + s.toUpperCase());
        }

        private void teleportAndEquip(Player p, Team team) {
            List<Location> spawns = team.getSpawns();
            if (!spawns.isEmpty()) {
                p.teleport(spawns.get(new Random().nextInt(spawns.size())));
            }
            Color teamColor = team.getLeatherColor();
            p.getInventory().setHelmet(createLeatherArmor(Material.LEATHER_HELMET, teamColor));
            p.getInventory().setChestplate(createLeatherArmor(Material.LEATHER_CHESTPLATE, teamColor));
            p.getInventory().setLeggings(createLeatherArmor(Material.LEATHER_LEGGINGS, teamColor));
            p.getInventory().setBoots(createLeatherArmor(Material.LEATHER_BOOTS, teamColor));
        }

        @EventHandler
        public void onInventoryClick(InventoryClickEvent ev) {

            HumanEntity var3 = ev.getWhoClicked();


            if (var3 instanceof Player p) {

                if (ev.getView() != null) {

                    String title = ev.getView().getTitle();
                    if (title != null) {
                        if (title.startsWith(this.GUI_SETUP)) {
                            ev.setCancelled(true);
                            String arenaName = title.substring(this.GUI_SETUP.length() + 3);
                            Arena a = (Arena) this.arenas.get(arenaName.toLowerCase());
                            if (a == null) {
                                return;
                            }

                            int slot = ev.getRawSlot();
                            switch (slot) {
                                case 0:
                                    a.setLobby(p.getLocation());
                                    p.sendMessage(String.valueOf(ChatColor.GREEN) + "Lobby ustawione");
                                    break;
                                case 1:
                                    int idNum = a.teamCount() + 1;
                                    String id = "TEAM" + idNum;
                                    Team t = new Team(id, ChatColor.WHITE, Material.WHITE_WOOL);
                                    a.addTeam(t);
                                    String var54 = String.valueOf(ChatColor.GREEN);
                                    p.sendMessage(var54 + "Dodano drużynę: " + id);
                                    this.openArenaSetupGUI(p, a);
                                    break;
                                case 2:
                                    this.plugin.getMapRegionTool().giveSelectionTool(p, a.getName());
                                    p.closeInventory();
                                    p.sendMessage(String.valueOf(ChatColor.YELLOW) + "Tryb edycji: ustaw punkt 1 i punkt 2 różdżką.");
                                    break;
                                case 4:
                                    // Pobierz arenę z tytułu GUI, a nie z playerArena
                                    Arena arenaToEdit = this.arenas.get(arenaName.toLowerCase());
                                    if (arenaToEdit == null) return;

                                    arenaToEdit.setArenaMode(arenaToEdit.getArenaMode().next());
                                    saveArenas();
                                    openArenaSetupGUI(p, arenaToEdit);
                                    p.sendMessage(ChatColor.GREEN + "Tryb ustawiony na: " + arenaToEdit.getArenaMode().getDisplay());
                                    break;


                                case 20:
                                case 27:
                                    plugin.getMapRegionTool().giveMapEnterSelectionTool(p, a.getName());
                                    p.closeInventory();
                                    p.sendMessage("§eUstaw punkt 1 i punkt 2 siekierką (teren mapy - wejście).");
                                    break;

                                case 28:
                                    plugin.getMapRegionTool().saveMapEnterSelection(p);
                                    p.sendMessage("§aZapisano teren mapy (wejście) dla areny " + a.getName());
                                    break;

                                default:
                                    if ((slot >= 9 && slot <= 15) || slot == 17) {
                                        ItemStack it = ev.getCurrentItem();
                                        if (it == null || it.getType().isAir()) {
                                            return;
                                        }

                                        String name = ChatColor.stripColor(it.getItemMeta().getDisplayName());
                                        Team target = null;

                                        for (Team teamLoop : a.getTeams()) {
                                            if (teamLoop.getId().equalsIgnoreCase(name) || teamLoop.getId().equalsIgnoreCase(ChatColor.stripColor(name))) {
                                                target = teamLoop;
                                                break;
                                            }

                                            if (ChatColor.stripColor(it.getItemMeta().getDisplayName()).equalsIgnoreCase(teamLoop.getId())) {
                                                target = teamLoop;
                                                break;
                                            }
                                        }


                                        if (target != null) {
                                            String var55 = String.valueOf(ChatColor.AQUA);
                                            p.sendMessage(var55 + "Edytuj drużynę: " + String.valueOf(target.getColor()) + target.getId());
                                            this.openTeamEditGUI(p, a, target);
                                        }
                                    }
                                    break;
                                case 5:
                                    p.closeInventory();
                                    Inventory protectInv = Bukkit.createInventory((InventoryHolder) null, 27, "Bloki chronione - " + a.getName());

                                    for (Material m : a.getProtectedBlocks()) {
                                        protectInv.addItem(new ItemStack[]{new ItemStack(m)});
                                    }

                                    p.openInventory(protectInv);
                                    p.sendMessage(String.valueOf(ChatColor.AQUA) + "Wrzuć tutaj bloki, których nie będzie można niszczyć.");
                                    break;
                                case 6:
                                    this.saveArenas();
                                    p.sendMessage(String.valueOf(ChatColor.GREEN) + "Arena zapisana.");
                                    break;
                                case 8:
                                    p.closeInventory();
                                    break;
                                case 16:
                                    this.plugin.getMapRegionTool().saveSelection(p);
                                    String var53 = String.valueOf(ChatColor.GREEN);
                                    p.sendMessage(var53 + "Zapisano teren regeneracji dla areny " + a.getName());
                                    break;
                                case 18:
                                    int minPlayers = a.getMinPlayers();
                                    if (ev.isLeftClick()) {
                                        ++minPlayers;
                                    } else if (ev.isRightClick()) {
                                        minPlayers = Math.max(2, minPlayers - 1);
                                    }

                                    a.setMinPlayers(minPlayers);
                                    Inventory inv18 = ev.getInventory();
                                    inv18.setItem(18, this.createGuiItem(Material.PLAYER_HEAD, ChatColor.AQUA + "Min graczy: " + minPlayers));
                                    p.sendMessage(ChatColor.GREEN + "Min players: " + minPlayers);
                                    break;

                                case 19:
                                    int maxPlayers = a.getMaxPlayers();
                                    if (ev.isLeftClick()) {
                                        ++maxPlayers;
                                    } else if (ev.isRightClick()) {
                                        maxPlayers = Math.max(2, maxPlayers - 1);
                                    }

                                    a.setMaxPlayers(maxPlayers);
                                    Inventory inv19 = ev.getInventory();
                                    inv19.setItem(19, this.createGuiItem(Material.DIAMOND_HELMET, ChatColor.AQUA + "Max graczy: " + maxPlayers));
                                    p.sendMessage(ChatColor.GREEN + "Max players: " + maxPlayers);
                                    break;

                                case 21:
                                    Block diamondBlock = p.getLocation().subtract(0, 1, 0).getBlock();
                                    if (diamondBlock.getType() != Material.DIAMOND_BLOCK) {
                                        p.sendMessage(ChatColor.RED + "Musisz stać na bloku diamentu, aby ustawić generator!");
                                        return;
                                    }

                                    Arena arena21 = this.arenas.get(arenaName.toLowerCase());
                                    if (arena21 == null) return;

                                    if (arena21.getGeneratorMapy() == null) {
                                        arena21.setGeneratorMapy(new GeneratorMapy(this.plugin));
                                    }

                                    GeneratorMapy mapGen21 = arena21.getGeneratorMapy();
                                    Location loc21 = diamondBlock.getLocation().add(0.5, 0.5, 0.5);
                                    mapGen21.addDiamondGeneratorAtLocation(loc21);
                                    this.saveArenas();
                                    p.sendMessage(ChatColor.GREEN + "Ustawiono generator diamentów!");
                                    break;

                                case 22:
                                    Block emeraldBlock = p.getLocation().subtract(0, 1, 0).getBlock();
                                    if (emeraldBlock.getType() != Material.EMERALD_BLOCK) {
                                        p.sendMessage(ChatColor.RED + "Musisz stać na bloku emeraldu, aby ustawić generator!");
                                        return;
                                    }

                                    Arena arena22 = this.arenas.get(arenaName.toLowerCase());
                                    if (arena22 == null) return;

                                    if (arena22.getGeneratorMapy() == null) {
                                        arena22.setGeneratorMapy(new GeneratorMapy(this.plugin));
                                    }

                                    GeneratorMapy mapGen22 = arena22.getGeneratorMapy();
                                    Location loc22 = emeraldBlock.getLocation().add(0.5, 0.5, 0.5);
                                    mapGen22.addEmeraldGeneratorAtLocation(loc22);
                                    this.saveArenas();
                                    p.sendMessage(ChatColor.GREEN + "Ustawiono generator emeraldów!");
                                    break;
                                case 23:
                                    if (a != null) {
                                        GeneratorMapy mapGen = a.getGeneratorMapy();
                                        if (mapGen != null) {
                                            mapGen.clear();
                                        }

                                        this.saveArenas();
                                        p.sendMessage(String.valueOf(ChatColor.RED) + "Usunięto wszystkie generatory diamentów i emeraldów!");
                                        break; // ✅ DODAJ TO
                                    }
                                case 24:
                                    a.setRanked(!a.isRanked());
                                    saveArenas();
                                    openArenaSetupGUI(p, a);
                                    p.sendMessage(ChatColor.GREEN + "Ranked: " + (a.isRanked() ? "WŁĄCZONE" : "WYŁĄCZONE"));
                                    break;

                                case 25:
                                    // cyklicznie zmieniaj próg (domyślnie od GOLD:I)
                                    List<String> reqs = Arrays.asList(
                                            "iron:V", "iron:IV", "iron:III", "iron:II", "iron:I",
                                            "bronze:V", "bronze:IV", "bronze:III", "bronze:II", "bronze:I",
                                            "silver:V", "silver:IV", "silver:III", "silver:II", "silver:I",
                                            "gold:V", "gold:IV", "gold:III", "gold:II", "gold:I",
                                            "platinum:V", "platinum:IV", "platinum:III", "platinum:II", "platinum:I",
                                            "diamond:V", "diamond:IV", "diamond:III", "diamond:II", "diamond:I",
                                            "master:V", "master:IV", "master:III", "master:II", "master:I",
                                            "legend:V", "legend:IV", "legend:III", "legend:II", "legend:I"
                                    );

                                    String cur = a.getRankedMin();
                                    int idx = reqs.indexOf(cur);
                                    if (idx < 0) idx = reqs.indexOf("gold:I");
                                    if (idx < 0) idx = 0;

                                    if (ev.isLeftClick()) idx = (idx + 1) % reqs.size();
                                    else if (ev.isRightClick()) idx = (idx - 1 + reqs.size()) % reqs.size();

                                    a.setRankedMin(reqs.get(idx));
                                    saveArenas();
                                    openArenaSetupGUI(p, a);

                                    RankedSystem rs = plugin.getRankedSystem();
                                    int need = (rs != null) ? rs.getMinPointsFor(a.getRankedMin()) : 0;

                                    p.sendMessage(ChatColor.GREEN + "Min ranga ustawiona na: " + ChatColor.AQUA + prettyReq(a.getRankedMin())
                                            + ChatColor.GRAY + " (" + ChatColor.YELLOW + need + ChatColor.GRAY + " pkt)");
                                    break;

                                case 26:
                                    a.setEnabled(!a.isEnabled());
                                    saveArenas();
                                    openArenaSetupGUI(p, a);
                                    p.sendMessage(ChatColor.YELLOW + "Mapa " +
                                            (a.isEnabled() ? "włączona" : "wyłączona"));
                                    break;
                            }

                        } else if (title.equals(this.GUI_MAIN)) {

                            ev.setCancelled(true); // 🔥 TO MUSI BYĆ PIERWSZE

                            int raw = ev.getRawSlot();

                            if (raw == SLOT_PAGE_INFO) {
                                if (ev.isLeftClick()) {          // LPP
                                    if (page > 0) page--;
                                } else if (ev.isRightClick()) {  // PPM
                                    if (page < maxPage) page++;
                                }
                                updateArenaGUI(ev.getInventory());
                                return;
                            }
// panel
                            if (raw == SLOT_CLOSE) {
                                p.closeInventory();
                                return;
                            }
                            if (raw == SLOT_REFRESH) {
                                updateArenaGUI(ev.getInventory());
                                p.sendMessage(ChatColor.GREEN + "Odświeżono listę aren.");
                                return;
                            }
                            if (raw == SLOT_SORT_PREV || raw == SLOT_SORT_NEXT) {
                                page = 0;
                                SortMode[] vals = SortMode.values();
                                int pos = Arrays.asList(vals).indexOf(sortMode);
                                if (raw == SLOT_SORT_NEXT) pos = (pos + 1) % vals.length;
                                else pos = (pos - 1 + vals.length) % vals.length;
                                sortMode = vals[pos];
                                updateArenaGUI(ev.getInventory());
                                p.sendMessage(ChatColor.AQUA + "Sortowanie: " + ChatColor.WHITE + sortModeName());
                                return;
                            }
                            if (raw == SLOT_MODE_FILTER) {
                                page = 0;
                                ModeFilter[] vals = ModeFilter.values();
                                int pos = Arrays.asList(vals).indexOf(modeFilter);
                                pos = (pos + 1) % vals.length;
                                modeFilter = vals[pos];
                                updateArenaGUI(ev.getInventory());
                                p.sendMessage(ChatColor.YELLOW + "Tryb: " + ChatColor.WHITE + modeFilterName());
                                return;
                            }
                            if (raw == SLOT_TYPE_FILTER) {
                                page = 0;
                                TypeFilter[] vals = TypeFilter.values();
                                int pos = Arrays.asList(vals).indexOf(typeFilter);
                                pos = (pos + 1) % vals.length;
                                typeFilter = vals[pos];
                                updateArenaGUI(ev.getInventory());
                                p.sendMessage(ChatColor.LIGHT_PURPLE + "Filtr: " + ChatColor.WHITE + typeFilterName());
                                return;
                            }

                            ev.setCancelled(true);
                            ItemStack clicked = ev.getCurrentItem();
                            if (clicked == null || clicked.getType() == Material.AIR) {
                                return;
                            }

                            String arenaName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                            Arena a = this.getArena(arenaName);
                            if (a == null) {
                                p.sendMessage(String.valueOf(ChatColor.RED) + "Arena nie istnieje");
                                return;
                            }

                            this.joinArena(p, a);
                            p.closeInventory();


                        } else if (title.startsWith(String.valueOf(ChatColor.YELLOW) + "Edytuj drużynę ")) {
                            ev.setCancelled(true);
                            String teamId = title.substring((String.valueOf(ChatColor.YELLOW) + "Edytuj drużynę ").length());
                            String arenaName = p.hasMetadata("editingArena") ? ((MetadataValue) p.getMetadata("editingArena").get(0)).asString() : null;
                            if (arenaName == null) {
                                return;
                            }

                            Arena arena = this.getArena(arenaName);
                            if (arena == null) {
                                return;
                            }

                            Team team = arena.getTeam(teamId);
                            if (team == null) {
                                return;
                            }

                            switch (ev.getRawSlot()) {
                                case 10:
                                    Block target = p.getTargetBlockExact(5);
                                    if (target == null || !target.getType().name().endsWith("_BED")) {
                                        p.sendMessage(String.valueOf(ChatColor.RED) + "Musisz patrzeć na łóżko, aby je ustawić!");
                                        return;
                                    }

                                    team.setBedLocation(target.getLocation());
                                    String var60 = String.valueOf(ChatColor.GREEN);
                                    p.sendMessage(var60 + "Ustawiono łóżko dla " + team.getId() + " w lokalizacji: " + target.getX() + ", " + target.getY() + ", " + target.getZ());
                                    break;
                                case 11:
                                    team.addSpawn(p.getLocation());
                                    String var59 = String.valueOf(ChatColor.GREEN);
                                    p.sendMessage(var59 + "Dodano spawn dla " + team.getId());
                                    break;
                                case 12:
                                    this.openTeamColorGUI(p, arena, team);
                                    break;
                                case 13:
                                    this.plugin.getSklepDruzyn().spawnTeamShop(arena, team, p.getLocation());
                                    team.setShopLocation(p.getLocation());
                                    this.saveArenas();
                                    String var58 = String.valueOf(ChatColor.GREEN);
                                    p.sendMessage(var58 + "Ustawiono sklep dla drużyny " + String.valueOf(team.getColor()) + team.getId());
                                    break;
                                case 14:
                                    Location loc = p.getLocation();
                                    team.setGeneratorLocation(loc);
                                    this.plugin.getGeneratorDruzyny().setGenerator(team.getId(), loc);
                                    String var57 = String.valueOf(ChatColor.GREEN);
                                    p.sendMessage(var57 + "Ustawiono generator dla drużyny " + team.getId() + " w twojej lokalizacji!");
                                case 15:
                                case 16:
                                case 17:
                                case 18:
                                case 19:
                                case 20:
                                case 21:
                                case 22:
                                case 23:
                                case 24:
                                case 25:
                                default:
                                    break;
                                case 26:
                                    this.openArenaSetupGUI(p, arena);
                            }

                        } else if (title.startsWith(String.valueOf(ChatColor.LIGHT_PURPLE) + "Kolor drużyny: ")) {
                            ev.setCancelled(true);
                            String teamId = title.substring((String.valueOf(ChatColor.LIGHT_PURPLE) + "Kolor drużyny: ").length());
                            String arenaName = p.hasMetadata("editingArena") ? ((MetadataValue) p.getMetadata("editingArena").get(0)).asString() : null;
                            if (arenaName == null) {
                                return;
                            }

                            Arena arena = this.getArena(arenaName);
                            if (arena == null) {
                                return;
                            }

                            Team team = arena.getTeam(teamId);
                            if (team == null) {
                                return;
                            }

                            ItemStack clicked = ev.getCurrentItem();
                            if (clicked == null || clicked.getType() == Material.AIR) {
                                return;
                            }

                            if (clicked.getType() == Material.BARRIER) {
                                this.openTeamEditGUI(p, arena, team);
                                return;
                            }

                            String picked = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).toUpperCase();

                            ChatColor newColor;
                            try {
                                newColor = ChatColor.valueOf(picked);
                            } catch (IllegalArgumentException ex) {
                                p.sendMessage(ChatColor.RED + "Nieobsługiwany kolor: " + picked);
                                return;
                            }

                            Material wool = clicked.getType();

                            team.setWoolMaterial(wool);
                            team.setColor(materialToChatColor(wool));   // ✅ najważniejsze

                            saveArenas(); // żeby się zapisało do arenas.yml

                            p.sendMessage(ChatColor.GREEN + "Zmieniono kolor drużyny na " + team.getColor() + team.getId());
                            this.openTeamEditGUI(p, arena, team);

                        }

                    }
                    if (ChatColor.stripColor(title).equalsIgnoreCase("Wybierz drużynę")) {
                        ev.setCancelled(true);
                        ItemStack clicked = ev.getCurrentItem();
                        if (clicked == null || clicked.getType() == Material.AIR) return;

                        ItemMeta meta = clicked.getItemMeta();
                        if (meta == null || !meta.hasDisplayName()) return;

                        String teamName = ChatColor.stripColor(meta.getDisplayName());
                        Arena arena = plugin.getPlayerArena().get(p.getUniqueId());
                        if (arena == null) return;

                        // Pobranie wybranej drużyny
                        Team selected = arena.getTeams().stream()
                                .filter(tm -> tm.getId().equalsIgnoreCase(teamName))
                                .findFirst()
                                .orElse(null);

                        if (selected == null) return;

                        // Liczenie graczy w drużynie
                        int playersInTeam = (int) arena.getPlayersInArena().stream()
                                .filter(uuid -> plugin.getPlayerTeam().get(uuid) == selected)
                                .count();

                        int totalPlayers = arena.getPlayersInArena().size();
                        int teamCount = arena.getTeams().size();

                        // Blokada jeśli drużyna zajęta
                        if (playersInTeam > 0 && totalPlayers < teamCount) {
                            p.sendMessage(ChatColor.RED + "Ta drużyna jest już zajęta! Wybierz inną.");
                            return;
                        }

// Przypisanie gracza do drużyny (GLOBALNA MAPA)
                        if (!assignTeamForPlayerOrParty(p, arena, selected)) {
                            // jeśli false -> brak miejsca
                            return;
                        }
                        plugin.getTabListaBW().updateArenaTab(arena);
                        giveLeaveItem(p);
// Upewnij się, że gracz jest w globalnej playerArena (jeśli GUI dodało do lokalnej mapy wcześniej)
                        plugin.getPlayerArena().putIfAbsent(p.getUniqueId(), arena);

// AKTUALIZACJA TAB (TYLKO RAZ)
                        plugin.getTabListaBW().updateArenaTab(arena);

                    }
                }
            }
        }


        private final Map<UUID, List<String>> lastGameLines = new HashMap<>();
        private final String OBJ_NAME = "gameSB";

        private Objective getOrCreateObj(Scoreboard sb, String title) {
            Objective obj = sb.getObjective(OBJ_NAME);
            if (obj == null) {
                obj = sb.registerNewObjective(OBJ_NAME, "dummy", title);
            } else {
                obj.setDisplayName(title);
            }

            // ✅ KLUCZ: ZAWSZE ustawiaj SIDEBAR, nawet jak objective już istnieje
            if (obj.getDisplaySlot() != DisplaySlot.SIDEBAR) {
                obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            }

            return obj;
        }

        private void setSidebar(Player p, String title, List<String> lines) {
            Scoreboard sb = getOrCreatePlayerBoard(p); // ✅ TO SAMO co TAB
            if (sb == null) return;

            Objective obj = getOrCreateObj(sb, title);

            // usuń stare linie
            List<String> old = lastGameLines.getOrDefault(p.getUniqueId(), List.of());
            for (String s : old) sb.resetScores(s);

            // dodaj nowe
            int score = lines.size();
            for (String s : lines) {
                obj.getScore(s).setScore(score--);
            }

            lastGameLines.put(p.getUniqueId(), new ArrayList<>(lines));

            // ✅ ustaw scoreboard TYLKO jeśli trzeba
            if (p.getScoreboard() != sb) p.setScoreboard(sb);
        }


        private boolean assignTeamForPlayerOrParty(Player p, Arena arena, Team selected) {
            PartySystem ps = plugin.getPartySystem();
            int limit = arena.getArenaMode().getPlayersPerTeam();

            var party = (ps != null) ? ps.getParty(p) : null;

            // brak party -> normalne nadpisanie (lobby)
            if (party == null) {
                if (!arena.isInGame()) plugin.getPlayerTeam().remove(p.getUniqueId());

                if (arena.countTeam(selected, plugin.getPlayerTeam()) >= limit) {
                    p.sendMessage(ChatColor.RED + "Ta drużyna jest pełna!");
                    return false;
                }
                plugin.getPlayerTeam().put(p.getUniqueId(), selected);
                selected.setEverHadPlayer(true);
                return true;
            }

            UUID leader = party.getLeader();
            if (leader == null) leader = p.getUniqueId();

            // ✅ TYLKO LIDER MOŻE ZMIENIAĆ TEAM PARTY
            if (!p.getUniqueId().equals(leader)) {
                p.sendMessage(ChatColor.RED + "Tylko lider party może zmieniać drużynę.");
                return false;
            }

            // ✅ usuń poprzednie teamy party w lobby (żeby nadpisało)
            if (!arena.isInGame()) {
                for (UUID u : party.getMembers()) {
                    if (!arena.getPlayersInArena().contains(u)) continue;
                    plugin.getPlayerTeam().remove(u);
                }
            }

            // zbierz członków party w arenie (online)
            List<Player> membersInArena = new ArrayList<>();
            for (UUID u : party.getMembers()) {
                if (!arena.getPlayersInArena().contains(u)) continue;
                Player pl = Bukkit.getPlayer(u);
                if (pl != null && pl.isOnline()) membersInArena.add(pl);
            }

            int free = limit - arena.countTeam(selected, plugin.getPlayerTeam());
            if (membersInArena.size() > free) {
                p.sendMessage(ChatColor.RED + "Brak miejsca w tej drużynie dla całej party (" + membersInArena.size() + ").");
                return false;
            }

            // przypisz wszystkim
            for (Player pl : membersInArena) {
                plugin.getPlayerTeam().put(pl.getUniqueId(), selected);
                giveLeaveItem(pl);

                // ✅ wiadomość dla członków (bez lidera)
                if (!pl.getUniqueId().equals(leader)) {
                    pl.sendMessage(ChatColor.GREEN + "Lider party (" + p.getName() + ") przeniósł party do: "
                            + selected.getColor() + selected.getId());
                }
            }

            selected.setEverHadPlayer(true);

// ✅ wiadomość tylko dla lidera
            p.sendMessage(ChatColor.YELLOW + "Wybrałeś drużynę: " + selected.getColor() + selected.getId());

            return true;
        }


        public void removePlayerFromAllTeams(Player p) {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            if (board == null) return;

            for (org.bukkit.scoreboard.Team t : board.getTeams()) {
                if (t.hasEntry(p.getName())) {
                    t.removeEntry(p.getName());
                }
            }
        }


        public void openTeamColorGUI(Player p, Arena arena, Team team) {
            Inventory inv = Bukkit.createInventory(null, 27,
                    ChatColor.LIGHT_PURPLE + "Kolor drużyny: " + team.getId()
            );

            Object[][] colors = new Object[][]{
                    {Material.WHITE_WOOL, "WHITE"},
                    {Material.ORANGE_WOOL, "GOLD"},
                    {Material.MAGENTA_WOOL, "DARK_PURPLE"},
                    {Material.LIGHT_BLUE_WOOL, "AQUA"},
                    {Material.YELLOW_WOOL, "YELLOW"},
                    {Material.LIME_WOOL, "GREEN"},
                    {Material.PINK_WOOL, "LIGHT_PURPLE"},
                    {Material.GRAY_WOOL, "DARK_GRAY"},
                    {Material.LIGHT_GRAY_WOOL, "GRAY"},
                    {Material.CYAN_WOOL, "DARK_AQUA"},
                    {Material.PURPLE_WOOL, "DARK_PURPLE"},
                    {Material.BLUE_WOOL, "BLUE"},
                    {Material.BROWN_WOOL, "GOLD"},
                    {Material.GREEN_WOOL, "DARK_GREEN"},
                    {Material.RED_WOOL, "RED"},
                    {Material.BLACK_WOOL, "BLACK"},
            };

            int[] slots = {
                    0, 1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16
            };

            for (int i = 0; i < colors.length; i++) {
                Material mat = (Material) colors[i][0];
                String chatColorName = (String) colors[i][1];
                inv.setItem(slots[i], createGuiItem(mat, ChatColor.WHITE + chatColorName));
            }

            inv.setItem(26, createGuiItem(Material.BARRIER, ChatColor.RED + "Powrót"));
            p.openInventory(inv);

            p.setMetadata("editingArena", new FixedMetadataValue(this.plugin, arena.getName()));
            p.setMetadata("editingTeam", new FixedMetadataValue(this.plugin, team.getId()));
        }


        public void openTeamEditGUI(Player player, Arena arena, Team team) {
            String var10002 = String.valueOf(ChatColor.YELLOW);
            Inventory inv = Bukkit.createInventory((InventoryHolder) null, 27, var10002 + "Edytuj drużynę " + team.getId());
            inv.setItem(10, this.createGuiItem(Material.RED_BED, String.valueOf(ChatColor.RED) + "Ustaw łóżko"));
            inv.setItem(11, this.createGuiItem(Material.COMPASS, String.valueOf(ChatColor.AQUA) + "Dodaj spawn"));
            inv.setItem(12, this.createGuiItem(Material.WHITE_WOOL, String.valueOf(ChatColor.GREEN) + "Zmień kolor"));
            ItemStack shop = new ItemStack(Material.EMERALD);
            ItemMeta shopMeta = shop.getItemMeta();
            shopMeta.setDisplayName(String.valueOf(ChatColor.GREEN) + "Ustaw sklep drużyny");
            shopMeta.setLore(Arrays.asList(String.valueOf(ChatColor.GRAY) + "Kliknij, aby ustawić sklep dla drużyny", String.valueOf(ChatColor.GRAY) + "Villager zostanie zespawnowany w twojej lokalizacji"));
            shop.setItemMeta(shopMeta);
            inv.setItem(13, shop);
            inv.setItem(14, this.createGuiItem(Material.EMERALD_BLOCK, String.valueOf(ChatColor.GOLD) + "Ustaw generator drużyny"));
            inv.setItem(26, this.createGuiItem(Material.BARRIER, String.valueOf(ChatColor.RED) + "Powrót"));
            player.openInventory(inv);
            player.setMetadata("editingArena", new FixedMetadataValue(this.plugin, arena.getName()));
            player.setMetadata("editingTeam", new FixedMetadataValue(this.plugin, team.getId()));
        }

        @EventHandler
        public void onInventoryClose(InventoryCloseEvent e) {
            Player p = (Player) e.getPlayer();
            String title = e.getView().getTitle();
            if (title.startsWith("Bloki chronione - ")) {
                String arenaName = title.substring("Bloki chronione - ".length());
                Arena a = (Arena) this.arenas.get(arenaName.toLowerCase());
                if (a == null) {
                    return;
                }

                a.getProtectedBlocks().clear();

                for (ItemStack item : e.getInventory().getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        a.addProtectedBlock(item.getType());
                    }
                }

                String var10001 = String.valueOf(ChatColor.GREEN);
                p.sendMessage(var10001 + "Zapisano bloki chronione dla " + a.getName());
                this.saveArenas();
            }

        }

        public void joinArena(Player player, Arena arena) {
            if (!arena.isEnabled()) {
                player.sendMessage(ChatColor.RED + "Ta mapa jest wyłączona.");
                return;
            }
            if (arena.getScoreboard() == null) {
                Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
                arena.setScoreboard(sb);
            }

// ✅ zawsze czyść starą drużynę / arenę (GLOBALNE MAPY)
            plugin.getPlayerTeam().remove(player.getUniqueId());
            plugin.getPlayerArena().remove(player.getUniqueId());
            String mapStatus = this.plugin.getMapResetManager().getArenaStatus(arena.getName());
            if (arena.isInGame()) {
                player.sendMessage(String.valueOf(ChatColor.RED) + "Ta arena jest w trakcie gry");
            } else if (mapStatus.equalsIgnoreCase("Badanie terenu")) {
                player.sendMessage(String.valueOf(ChatColor.RED) + "Nie możesz dołączyć – arena bada teren");
            } else if (mapStatus.equalsIgnoreCase("Restart")) {
                player.sendMessage(String.valueOf(ChatColor.RED) + "Nie możesz dołączyć – arena jest w trakcie odbudowy mapy");
            } else if (arena.getPlayersInArena().size() >= arena.getMaxPlayers()) {
                player.sendMessage(String.valueOf(ChatColor.RED) + "Arena jest pełna");
            } else {
                // ✅ RANKED BLOKADA (od Gold I)
                if (arena.isRanked()) {
                    RankedSystem rs = plugin.getRankedSystem();
                    if (rs == null) {
                        player.sendMessage(ChatColor.RED + "Ranked jest wyłączony.");
                        return;
                    }

                    String req = arena.getRankedMin(); // np. "gold:I"
                    if (!rs.hasAtLeastRank(player.getUniqueId(), req)) {
                        int need = rs.getMinPointsFor(req);
                        int your = rs.getPoints(player.getUniqueId());

                        player.sendMessage(ChatColor.RED + "Ta arena jest tylko dla Ranked!");
                        player.sendMessage(ChatColor.YELLOW + "Wymagane: " + ChatColor.AQUA + prettyReq(req)
                                + ChatColor.GRAY + " (" + ChatColor.YELLOW + need + ChatColor.GRAY + " pkt)");
                        player.sendMessage(ChatColor.YELLOW + "Twoje punkty: " + ChatColor.GREEN + your);
                        return;
                    }
                }
                plugin.getPlayerArena().put(player.getUniqueId(), arena);
                arena.getPlayersInArena().add(player.getUniqueId());
                arena.getEliminated().remove(player.getUniqueId()); // ✅ ważne
                player.teleport(arena.getLobby() != null ? arena.getLobby() : player.getWorld().getSpawnLocation());
                player.getInventory().clear();
                player.setGameMode(GameMode.ADVENTURE);
                plugin.getTabListaBW().startAutoUpdater(arena);
                plugin.getTabListaBW().updateArenaTab(arena);
                this.updateLobbyScoreboard(player, arena, arena.getCountdown());
                this.startLobbyCountdownIfNeeded(arena);
                String var10001 = String.valueOf(ChatColor.GREEN);
                clearChat(player);
                sendBedWarsBanner(player);
                ensureTeamsAssignedForLobby(arena);
                broadcastArenaJoin(arena, player);
                giveTeamCompass(player);
                // Przypisanie gracza do drużyny
                giveLeaveItem(player);
                plugin.getTabListaBW().updateArenaTab(arena);

// Aktualizacja TAB
            }
        }
        private void sendBedWarsBanner(Player p) {
            p.sendMessage("§c§m--------------------------------");
            p.sendMessage("§c§l||  B E D W A R S  ||");
            p.sendMessage("§c§m--------------------------------");
        }
        private void sendBedWarsStart(Arena arena, int seconds) {

            // animacja kolorów zależna od sekundy
            String main;
            String bar;
            float pitch;

            switch (seconds) {
                case 5 -> { main = "§a§lBEDWARS"; bar = "§a▶ Start za §e5§as"; pitch = 1.0f; }
                case 4 -> { main = "§e§lBEDWARS"; bar = "§e▶ Start za §b4§es"; pitch = 1.1f; }
                case 3 -> { main = "§6§lBEDWARS"; bar = "§6▶ Start za §e3§6s"; pitch = 1.2f; }
                case 2 -> { main = "§c§lBEDWARS"; bar = "§c▶ Start za §e2§cs"; pitch = 1.3f; }
                case 1 -> { main = "§4§lBEDWARS"; bar = "§4▶ Start za §e1§4s"; pitch = 1.4f; }
                default -> { main = "§c§lBEDWARS"; bar = "§a▶ Start"; pitch = 1.0f; }
            }

            for (UUID u : arena.getPlayersInArena()) {
                Player p = Bukkit.getPlayer(u);
                if (p == null || !p.isOnline()) continue;

                p.sendTitle(
                        main,
                        "§7Przygotuj się...",
                        0, 18, 4
                );

                p.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(bar)
                );

                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, pitch);
            }
        }
        private void sendBedWarsFight(Arena arena) {
            for (UUID u : arena.getPlayersInArena()) {
                Player p = Bukkit.getPlayer(u);
                if (p == null || !p.isOnline()) continue;

                p.sendTitle(
                        "§c§lSTART!",
                        "§e§lDO WALKI!",
                        0, 30, 10
                );

                p.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText("§c⚔ §eDO WALKI! §c⚔")
                );

                // mocniejszy dźwięk na start
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.8f);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

                // opcjonalnie: mały “flash”
                p.spawnParticle(Particle.FIREWORKS_SPARK, p.getLocation().add(0, 1, 0), 35, 0.6, 0.6, 0.6, 0.01);
            }
        }
        private void broadcastArenaJoin(Arena arena, Player joined) {

            String rank = getRankPrefix(joined);
            String nick = getTeamColoredName(joined);

            for (UUID u : arena.getPlayersInArena()) {
                Player pl = Bukkit.getPlayer(u);
                if (pl == null) continue;

                pl.sendMessage("§7[§a+§7] " + rank + nick + " §7dołączył do areny §e" + arena.getName()
                        + " §7(§b" + arena.getPlayersInArena().size() + "§7/§b" + arena.getMaxPlayers() + "§7)");
            }

            // tylko joinujący: Title + Actionbar
            joined.sendTitle("§a§lDOŁĄCZONO!", "§7Arena: §e" + arena.getName(), 10, 40, 10);
            joined.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                            "§a✅ Dołączyłeś do §e" + arena.getName() + " §7| §b" +
                                    arena.getPlayersInArena().size() + "/" + arena.getMaxPlayers()
                    )
            );
        }
        private String getRankPrefix(Player p) {
            // Vault Chat (masz getChat() w pluginie)
            if (plugin.getChat() != null) {
                String prefix = plugin.getChat().getPlayerPrefix(p);
                if (prefix != null && !prefix.isEmpty()) return ChatColor.translateAlternateColorCodes('&', prefix);
            }
            return "§7"; // fallback
        }

        private String getTeamColoredName(Player p) {
            Team t = plugin.getPlayerTeam().get(p.getUniqueId());
            ChatColor c = (t != null && t.getColor() != null) ? t.getColor() : ChatColor.WHITE;
            return c + p.getName();
        }
        @EventHandler
        public void onDrop(PlayerDropItemEvent e) {
            if (e.getItemDrop().getItemStack().getType() == Material.COMPASS) {
                e.setCancelled(true);
            }
        }

        // --- W EventHandlerze, gdy gracz klika compass ---
        @EventHandler
        public void onPlayerUseTeamCompass(PlayerInteractEvent ev) {
            Player p = ev.getPlayer();
            ItemStack item = ev.getItem();

            if (item == null || item.getType() != Material.COMPASS) return;
            if (!item.hasItemMeta()) return;

            ItemMeta meta = item.getItemMeta();
            NamespacedKey key = new NamespacedKey(BedWarsPlugin.getInstance(), "bedwars_item");

            // ❌ to nie jest kompas BedWars
            if (!meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;
            if (!"TEAM_COMPASS".equals(meta.getPersistentDataContainer().get(key, PersistentDataType.STRING))) return;

            ev.setCancelled(true);

            Arena arena = BedWarsPlugin.getInstance().getPlayerArena().get(p.getUniqueId());
            if (arena == null) {
                // 🧹 self-heal – usuń TYLKO nasz kompas
                p.getInventory().remove(item);
                p.updateInventory();
                return;
            }

            openTeamSelector(p, arena);
        }


        // --- Otwiera GUI wyboru drużyny ---
        public void openTeamSelector(Player p, Arena arena) {
            int size = 9; // GUI na 9 slotów
            Inventory inv = Bukkit.createInventory(null, size, ChatColor.YELLOW + "Wybierz drużynę");

            for (Team team : arena.getTeams()) {
                Material mat = team.getWoolMaterial() != null ? team.getWoolMaterial() : Material.WHITE_WOOL;

                int maxPlayers = arena.getArenaMode().getPlayersPerTeam();
                int currentPlayers = (int) arena.getPlayersInArena().stream()
                        .filter(uuid -> plugin.getPlayerTeam().get(uuid) == team)
                        .count();

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(team.getColor() + team.getId());

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Graczy: " + currentPlayers + "/" + maxPlayers);
                if (currentPlayers >= maxPlayers) {
                    lore.add(ChatColor.RED + "Pełna drużyna!");
                } else {
                    lore.add(ChatColor.GREEN + "Kliknij, aby dołączyć!");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);

                inv.addItem(item);
            }

            plugin.getPlayerArena().put(p.getUniqueId(), arena);
            p.openInventory(inv);
        }

        public void giveLeaveItem(Player p) {
            ItemStack leave = new ItemStack(Material.BARRIER);
            ItemMeta meta = leave.getItemMeta();
            meta.setDisplayName(ChatColor.RED + "Wyjście z gry");

            NamespacedKey key = new NamespacedKey(BedWarsPlugin.getInstance(), "bedwars_item");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "LEAVE_ITEM");

            leave.setItemMeta(meta);
            p.getInventory().setItem(8, leave);
        }


        @EventHandler(priority = EventPriority.HIGHEST)
        public void onPlayerUseLeaveItem(PlayerInteractEvent ev) {
            if (ev.getHand() != EquipmentSlot.HAND) return;

            Player p = ev.getPlayer();
            ItemStack item = ev.getItem();
            if (item == null || item.getType() != Material.BARRIER) return;
            if (!item.hasItemMeta()) return;

            ItemMeta meta = item.getItemMeta();
            NamespacedKey key = new NamespacedKey(BedWarsPlugin.getInstance(), "bedwars_item");

            // ✅ to musi być NASZ barrier
            if (!meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;
            if (!"LEAVE_ITEM".equals(meta.getPersistentDataContainer().get(key, PersistentDataType.STRING))) return;

            ev.setCancelled(true);

            Arena arena = plugin.getPlayerArena().get(p.getUniqueId());
            if (arena == null) {
                // ✅ bez spamu – drugi event / po wyjściu
                return;
            }

            // usuń z list areny
            arena.getPlayersInArena().remove(p.getUniqueId());
            arena.getEliminated().add(p.getUniqueId());

            // usuń z lokalnych
            plugin.getPlayerArena().remove(p.getUniqueId());
            plugin.getPlayerTeam().remove(p.getUniqueId());

            // usuń z globalnych
            plugin.getPlayerArena().remove(p.getUniqueId());
            plugin.getPlayerTeam().remove(p.getUniqueId());

            // czyść header/footer + scoreboard
            p.setPlayerListHeaderFooter("", "");
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

            // wyczyść EQ od razu
            p.getInventory().clear();
            p.updateInventory();

            // policz lobby teraz, ale teleport zrób tick później
            Location lobby = this.getGlobalLobby();
            if (lobby == null && arena.getServerLobby() != null) lobby = arena.getServerLobby();
            Location finalLobby = lobby;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;

                if (finalLobby != null) {
                    p.teleport(finalLobby);
                }

                // dopiero po dłuższej chwili hub/tab/itemy
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (p.isOnline()) plugin.getTabListaBW().applyHubNow(p);
                }, 20L);

            }, 2L); // 2 ticki
            plugin.getTabListaBW().applyHubNow(p);

            p.sendMessage(ChatColor.YELLOW + "Wyszedłeś z gry.");

            // odśwież TAB dla pozostałych
            plugin.getTabListaBW().updateArenaTab(arena);
        }

        private boolean canStartFair(Arena arena) {
            // 1) minPlayers musi się zgadzać
            if (arena.getPlayersInArena().size() < arena.getMinPlayers()) return false;

            // 2) muszą być przynajmniej 2 różne drużyny z graczami
            Set<String> nonEmptyTeams = new HashSet<>();

            for (UUID u : arena.getPlayersInArena()) {
                Team t = plugin.getPlayerTeam().get(u);
                if (t != null) {
                    nonEmptyTeams.add(t.getId()); // np. "RED", "BLUE"
                }
            }

            return nonEmptyTeams.size() >= 2;
        }

        public void startLobbyCountdownIfNeeded(final Arena arena) {
            if (!arena.isCountingDown() && !arena.isInGame()) {
                ensureTeamsAssignedForLobby(arena);

                if (canStartFair(arena)) {
                    arena.setCountingDown(true);
                    arena.setCountdown(30);
                    BukkitRunnable t = new BukkitRunnable() {
                        int count = arena.getCountdown();

                        public void run() {
                            ensureTeamsAssignedForLobby(arena);

                            if (!canStartFair(arena)) {
                                arena.setCountingDown(false);
                                for (UUID uuid : arena.getPlayersInArena()) {
                                    Player pl = Bukkit.getPlayer(uuid);
                                    if (pl != null)
                                        pl.sendMessage(ChatColor.YELLOW + "Czekamy na przeciwników... (min. 2 drużyny muszą mieć graczy)");
                                }
                                this.cancel();
                                return;
                            } else {
                                for (UUID uuid : new HashSet<UUID>(arena.getPlayersInArena())) {
                                    Player pl = Bukkit.getPlayer(uuid);
                                    if (pl != null && pl.isOnline()) {
                                        ArenaManager.this.updateLobbyScoreboard(pl, arena, this.count);
                                        if (this.count <= 5 && this.count > 0) {
                                            sendBedWarsStart(arena, this.count);
                                        }
                                    }
                                }

                                if (this.count == 0) {
                                    sendBedWarsFight(arena);          // ✅ START / DO WALKI
                                    ArenaManager.this.startGame(arena);
                                    this.cancel();
                                } else {
                                    if (this.count <= 5) {
                                        sendBedWarsStart(arena, this.count); // ✅ animacja 5..1
                                    }
                                    --this.count;
                                    arena.setCountdown(this.count);
                                }
                            }
                        }
                    };
                    arena.setLobbyTask(t);
                    t.runTaskTimer(this.plugin, 0L, 20L);
                }
            }
        }

        private void ensureTeamsAssignedForLobby(Arena arena) {
            PartySystem ps = plugin.getPartySystem();
            int limit = arena.getArenaMode().getPlayersPerTeam();

            // 1) Najpierw ogarnij PARTY jako grupy (żeby nie rozdzielało)
            if (ps != null) {
                Set<UUID> handled = new HashSet<>();

                for (UUID u : new HashSet<>(arena.getPlayersInArena())) {
                    if (handled.contains(u)) continue;

                    var party = ps.getParty(Bukkit.getPlayer(u));
                    if (party == null) continue;

                    UUID leader = party.getLeader();
                    if (leader == null) leader = u;

                    // zbierz członków party, którzy są w tej arenie i nie mają teamu
                    List<UUID> members = new ArrayList<>();
                    for (UUID m : party.getMembers()) {
                        if (!arena.getPlayersInArena().contains(m)) continue;
                        handled.add(m);
                        members.add(m);
                    }
                    if (members.isEmpty()) continue;

                    // jeśli lider już ma team -> przypisz wszystkim to samo
                    Team leaderTeam = plugin.getPlayerTeam().get(leader);
                    if (leaderTeam != null) {
                        // sprawdź czy jest miejsce dla tych, którzy jeszcze nie mają teamu
                        long need = members.stream().filter(id -> !plugin.getPlayerTeam().containsKey(id)).count();
                        int free = limit - arena.countTeam(leaderTeam, plugin.getPlayerTeam());

                        if (need <= free) {
                            for (UUID id : members) {
                                if (plugin.getPlayerTeam().containsKey(id)) continue;
                                plugin.getPlayerTeam().put(id, leaderTeam);
                                leaderTeam.setEverHadPlayer(true);

                                Player pl = Bukkit.getPlayer(id);
                                if (pl != null && pl.isOnline()) giveLeaveItem(pl);
                            }
                        }
                        continue;
                    }

                    // lider nie ma teamu -> wybierz team z miejscem dla CAŁEJ party (tych bez teamu)
                    List<UUID> needAssign = members.stream()
                            .filter(id -> !plugin.getPlayerTeam().containsKey(id))
                            .toList();

                    if (needAssign.isEmpty()) continue;

                    Team teamForParty = chooseTeamForParty(arena, limit, needAssign.size());
                    if (teamForParty == null) {
                        Player leadPl = Bukkit.getPlayer(leader);
                        if (leadPl != null) {
                            leadPl.sendMessage(ChatColor.RED + "Brak miejsca, żeby cała party była w jednej drużynie.");
                        }
                        continue;
                    }

                    for (UUID id : needAssign) {
                        plugin.getPlayerTeam().put(id, teamForParty);
                        teamForParty.setEverHadPlayer(true);

                        Player pl = Bukkit.getPlayer(id);
                        if (pl != null && pl.isOnline()) giveLeaveItem(pl);
                    }
                }
            }

            // 2) Dopiero potem SOLO (bez teamu)
            for (UUID u : new HashSet<>(arena.getPlayersInArena())) {
                if (plugin.getPlayerTeam().containsKey(u)) continue;

                Team chosen = chooseTeamForAutoJoin(arena, limit);
                if (chosen == null) continue;

                plugin.getPlayerTeam().put(u, chosen);
                chosen.setEverHadPlayer(true);

                Player pl = Bukkit.getPlayer(u);
                if (pl != null && pl.isOnline()) {
                    giveLeaveItem(pl);
                }
            }
        }


        /**
         * Auto-join: preferuj puste teamy (żeby start mógł ruszyć),
         * potem najmniej liczny team.
         */
        private Team chooseTeamForParty(Arena arena, int limit, int partySize) {

            List<Team> candidates = arena.getTeams().stream()
                    .filter(t -> (limit - arena.countTeam(t, plugin.getPlayerTeam())) >= partySize)
                    .toList();

            if (candidates.isEmpty()) return null;

            // preferuj najmniej zaludniony team
            int min = candidates.stream()
                    .mapToInt(t -> arena.countTeam(t, plugin.getPlayerTeam()))
                    .min()
                    .orElse(0);

            List<Team> best = candidates.stream()
                    .filter(t -> arena.countTeam(t, plugin.getPlayerTeam()) == min)
                    .toList();

            return best.get(new Random().nextInt(best.size()));
        }


        private Team chooseTeamForAutoJoin(Arena arena, int limit) {

            // teamy które mają jeszcze miejsce
            List<Team> available = arena.getTeams().stream()
                    .filter(t -> arena.countTeam(t, plugin.getPlayerTeam()) < limit)
                    .toList();

            if (available.isEmpty()) return null;

            // preferuj PUSTE teamy (żeby nie pakowało zawsze do RED)
            List<Team> empty = available.stream()
                    .filter(t -> arena.countTeam(t, plugin.getPlayerTeam()) == 0)
                    .toList();

            List<Team> pool = !empty.isEmpty() ? empty : available;

            // weź najmniej liczny (a przy remisie losowo)
            int min = pool.stream()
                    .mapToInt(t -> arena.countTeam(t, plugin.getPlayerTeam()))
                    .min()
                    .orElse(0);

            List<Team> best = pool.stream()
                    .filter(t -> arena.countTeam(t, plugin.getPlayerTeam()) == min)
                    .toList();

            return best.get(new Random().nextInt(best.size()));
        }

        private void createArenaBossBar(Arena arena) {
            String key = arena.getName().toLowerCase();

            org.bukkit.boss.BossBar bar = arenaBars.get(key);
            if (bar == null) {
                bar = Bukkit.createBossBar("§bBedWars", org.bukkit.boss.BarColor.BLUE, org.bukkit.boss.BarStyle.SEGMENTED_10);
                arenaBars.put(key, bar);
            }

            // wyczyść i dodaj graczy areny
            for (Player p : new ArrayList<>(bar.getPlayers())) bar.removePlayer(p);
            for (UUID u : arena.getPlayersInArena()) {
                Player pl = Bukkit.getPlayer(u);
                if (pl != null && pl.isOnline()) bar.addPlayer(pl);
            }
            bar.setVisible(true);
        }
        public void startGame(final Arena arena) {

            if (arena.getLobbyTask() != null) {
                arena.getLobbyTask().cancel();
                arena.setLobbyTask(null);
            }

            arena.setCountingDown(false);
            arena.setInGame(true);
            createArenaBossBar(arena);
            startBossbarTickerIfNeeded();
            if (arena.getGeneratorMapy() != null) {
                plugin.getGeneratorDruzyny().start();
                arena.getGeneratorMapy().stopTaskOnly();
                arena.getGeneratorMapy().resetToPhase(arena.getCurrentPhase()); // albo 1
                arena.getGeneratorMapy().startWithPhase(arena::getCurrentPhase);
            } else {
                plugin.getLogger().warning("[GEN] arena.getGeneratorMapy() == null dla areny " + arena.getName());
            }
            Random rnd = new Random();

            // przydział teamów
            for (UUID uuid : new HashSet<>(arena.getPlayersInArena())) {
                Player pl = Bukkit.getPlayer(uuid);
                if (pl != null && pl.isOnline() && !plugin.getPlayerTeam().containsKey(uuid)) {
                    assignRandomTeam(arena, pl);
                }
            }

            // ustaw GAME scoreboard RAZ
            for (UUID uuid : new HashSet<>(arena.getPlayersInArena())) {
                Player pl = Bukkit.getPlayer(uuid);
                if (pl == null || !pl.isOnline()) continue;

                // ✅ nie twórz nowego sb tutaj
                Scoreboard sb = getOrCreatePlayerBoard(pl);
                if (pl.getScoreboard() != sb) pl.setScoreboard(sb);

                pl.setGameMode(GameMode.SURVIVAL);

                Team t = plugin.getPlayerTeam().get(uuid);
                if (t != null && !t.getSpawns().isEmpty()) {
                    pl.teleport(t.getSpawns().get(new Random().nextInt(t.getSpawns().size())));
                }

                pl.getInventory().clear();
                pl.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                pl.getInventory().addItem(new ItemStack(Material.APPLE, 3));
            }

            BukkitRunnable gameTask = new BukkitRunnable() {
                int time = arena.getGameTime();

                public void run() {
                    if (!arena.isInGame()) {
                        cancel();
                        return;
                    }

                    for (UUID uuid : arena.getPlayersInArena()) {
                        Player pl = Bukkit.getPlayer(uuid);
                        if (pl != null && pl.isOnline()) {
                            updateInGameScoreboard(pl, arena, time);
                        }
                    }

                    time--;
                }
            };

            arena.setGameTask(gameTask);
            gameTask.runTaskTimer(plugin, 1L, 20L);
        }


        private void assignRandomTeam(Arena arena, Player p) {
            int limit = arena.getArenaMode().getPlayersPerTeam();

            List<Team> candidates = arena.getTeams().stream()
                    .filter(t -> arena.countTeam(t, plugin.getPlayerTeam()) < limit)
                    .toList();

            if (candidates.isEmpty()) candidates = new ArrayList<>(arena.getTeams());

            Team assigned = candidates.get(new Random().nextInt(candidates.size()));
            assigned.setEverHadPlayer(true);

            plugin.getPlayerTeam().put(p.getUniqueId(), assigned);
            plugin.getPlayerArena().put(p.getUniqueId(), arena);
            p.getInventory().clear();

            p.sendMessage(ChatColor.GREEN + "Przydzielono drużynę: " + assigned.getColor() + assigned.getId());

            List<Location> spawns = assigned.getSpawns();
            if (!spawns.isEmpty()) {
                p.teleport(spawns.get(new Random().nextInt(spawns.size())));
            }


            Color teamColor = assigned.getLeatherColor();
            p.getInventory().setHelmet(createLeatherArmor(Material.LEATHER_HELMET, teamColor));
            p.getInventory().setChestplate(createLeatherArmor(Material.LEATHER_CHESTPLATE, teamColor));
            p.getInventory().setLeggings(createLeatherArmor(Material.LEATHER_LEGGINGS, teamColor));
            p.getInventory().setBoots(createLeatherArmor(Material.LEATHER_BOOTS, teamColor));

            this.plugin.getShop().playerArmorMap.put(
                    p.getUniqueId(),
                    new SklepDruzyn.PlayerArmor(
                            p.getInventory().getHelmet(),
                            p.getInventory().getChestplate(),
                            p.getInventory().getLeggings(),
                            p.getInventory().getBoots()
                    )
            );
        }

        public void giveTeamCompass(Player p) {
            ItemStack compass = new ItemStack(Material.COMPASS);
            ItemMeta meta = compass.getItemMeta();

            meta.setDisplayName(ChatColor.AQUA + "Wybór drużyny");

            NamespacedKey key = new NamespacedKey(BedWarsPlugin.getInstance(), "bedwars_item");
            meta.getPersistentDataContainer().set(
                    key,
                    PersistentDataType.STRING,
                    "TEAM_COMPASS"
            );

            compass.setItemMeta(meta);
            p.getInventory().setItem(0, compass);
        }


        private ChatColor getTeamColor(Team t) {
            return t != null && t.getColor() != null ? t.getColor() : ChatColor.WHITE;
        }

        private boolean isGameOver(Arena arena) {
            Set<Team> aliveTeams = new HashSet<>();

            for (UUID u : arena.getPlayersInArena()) {
                if (!arena.getEliminated().contains(u)) {
                    Team t = (Team) plugin.getPlayerTeam().get(u);
                    if (t != null) {
                        aliveTeams.add(t);
                    }
                }
            }

            return aliveTeams.size() <= 1;
        }

        @EventHandler(
                priority = EventPriority.HIGHEST
        )
        public void onBedBreak(BlockBreakEvent e) {
            Player p = e.getPlayer();
            Arena arena = (Arena) plugin.getPlayerArena().get(p.getUniqueId());
            if (arena != null && arena.isInGame()) {
                Block block = e.getBlock();
                BlockData var6 = block.getBlockData();
                if (var6 instanceof Bed) {
                    Bed bed = (Bed) var6;
                    Team target = arena.getTeamByBed(block.getLocation());
                    if (target != null && !target.hasEverHadPlayer()) {
                        e.setCancelled(true);
                        p.sendMessage("§c✖ Nie możesz zniszczyć tego łóżka!");
                        p.sendMessage("§7Od startu areny nikt nie dołączył do tej drużyny,");
                        p.sendMessage("§7dlatego rozwalenie tego łóżka jest niemożliwe.");
                        return;
                    }
                    if (target != null) {
                        Team breakerTeam = plugin.getPlayerTeam().get(p.getUniqueId());
                        if (breakerTeam == target) {
                            e.setCancelled(true);
                            p.sendMessage(String.valueOf(ChatColor.RED) + "Nie możesz zniszczyć własnego łóżka!");
                        } else if (target.isBedDestroyed()) {
                            e.setCancelled(true);
                        } else {
                            e.setDropItems(false);
                            Block otherPart = bed.getPart() == Part.HEAD ? block.getRelative(bed.getFacing(), -1) : block.getRelative(bed.getFacing(), 1);
                            block.getWorld().spawnParticle(Particle.BLOCK_CRACK, block.getLocation().add((double) 0.5F, (double) 0.5F, (double) 0.5F), 10, block.getBlockData());
                            otherPart.getWorld().spawnParticle(Particle.BLOCK_CRACK, otherPart.getLocation().add((double) 0.5F, (double) 0.5F, (double) 0.5F), 10, otherPart.getBlockData());
                            block.setType(Material.AIR, false);
                            otherPart.setType(Material.AIR, false);
                            target.setBedDestroyed(true);
                            String var10000 = String.valueOf(ChatColor.RED);
                            if (arena != null && arena.isInGame()) {
                                String msg = P_BED + "§cŁóżko drużyny " + getTeamColor(target) + target.getId() + " §czostało zniszczone przez §6" + p.getName();
                                arenaBroadcastInGame(arena, msg);
                                flashArenaBar(arena, "§4🛏 §cŁóżko " + getTeamColor(target) + target.getId() + " §czniszczone przez §6" + p.getName(), 4000);
                            }
                        }
                    }
                }
            }
        }
        private void refreshArenaBossBarPlayers(Arena arena) {
            if (arena == null || !arena.isInGame()) return;
            String key = arena.getName().toLowerCase();
            var bar = arenaBars.get(key);
            if (bar == null) return;

            for (Player p : new ArrayList<>(bar.getPlayers())) {
                if (!arena.getPlayersInArena().contains(p.getUniqueId())) bar.removePlayer(p);
            }
            for (UUID u : arena.getPlayersInArena()) {
                Player pl = Bukkit.getPlayer(u);
                if (pl != null && pl.isOnline() && !bar.getPlayers().contains(pl)) bar.addPlayer(pl);
            }
        }
        @EventHandler
        public void onPlayerDeath(PlayerDeathEvent ev) {
            Player victim = ev.getEntity();
            Bukkit.getScheduler().runTask(plugin, () -> {
                victim.spigot().respawn();
            });
            UUID uuid = victim.getUniqueId();
            Arena arena = plugin.getPlayerArena().get(uuid);
            if (arena == null) return;

            boolean inGame = arena.isInGame();

            boolean markedRegionVoid = false;
            long markT = regionVoidMark.getOrDefault(uuid, 0L);
            if (markT > 0 && (System.currentTimeMillis() - markT) <= REGION_VOID_MARK_MS) {
                markedRegionVoid = true;
                regionVoidMark.remove(uuid);
            }

            boolean voidDeath = markedRegionVoid || victim.getLocation().getY() <= 0;

            Player killer = victim.getKiller();
            boolean handledAsVoid = false;

            // ===== VOID MESSAGE =====
            if (inGame && voidDeath) {
                UUID killerId = lastDamager.get(uuid);
                long t = lastDamagerTime.getOrDefault(uuid, 0L);

                Player pusher = null;
                if (killerId != null && (System.currentTimeMillis() - t) <= PUSH_WINDOW_MS) {
                    pusher = Bukkit.getPlayer(killerId);
                }

                if (pusher != null) {
                    arenaBroadcastInGame(arena, P_VOID + "§c" + victim.getName() + " §7został zepchnięty do voida przez §6" + pusher.getName());
                    flashArenaBar(arena, "§5⬇ §c" + victim.getName() + " §7zepchnięty przez §6" + pusher.getName(), 3500);
                } else {
                    arenaBroadcastInGame(arena, P_VOID + "§c" + victim.getName() + " §7spadł do voida");
                    flashArenaBar(arena, "§5⬇ §c" + victim.getName() + " §7spadł do voida", 3000);
                }

                handledAsVoid = true;
            }

            // ===== CLEAR =====
            ev.getDrops().clear();
            ev.setKeepInventory(false);

            Team team = plugin.getPlayerTeam().get(uuid);
            String teamName = team != null ? String.valueOf(getTeamColor(team)) + team.getId() : "Nieznana drużyna";

            if (!voidDeath) {
                for (UUID u : arena.getPlayersInArena()) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null && p.isOnline()) {
                        p.sendMessage(ChatColor.RED + victim.getName() + ChatColor.YELLOW + " (" + teamName + ") został zabity!");
                    }
                }
            }

            // ===== RESPAWN SYSTEM =====
            if (team != null && !team.isBedDestroyed()) {

                int respawnTime = 5; // 🔥 tutaj zmieniasz czas

                new BukkitRunnable() {
                    int time = respawnTime;

                    @Override
                    public void run() {

                        if (!victim.isOnline()) {
                            cancel();
                            return;
                        }

                        if (time <= 0) {

                            victim.spigot().respawn();
                            victim.setGameMode(GameMode.SURVIVAL);

                            List<Location> spawns = team.getSpawns();
                            if (!spawns.isEmpty()) {
                                victim.teleport(spawns.get(new Random().nextInt(spawns.size())));
                            }

                            victim.sendTitle("§aODRODZENIE!", "", 0, 20, 10);

                            cancel();
                            return;
                        }

                        victim.setGameMode(GameMode.SPECTATOR);

                        victim.sendTitle(
                                "§cZginąłeś!",
                                "§7Respawn za §e" + time + "s",
                                0, 20, 0
                        );

                        time--;
                    }

                }.runTaskTimer(plugin, 0L, 20L);

            } else {

                // ===== FINAL KILL =====
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!victim.isOnline()) return;

                    victim.spigot().respawn();
                    victim.setGameMode(GameMode.SPECTATOR);
                    arena.getEliminated().add(uuid);

                    victim.sendMessage(ChatColor.RED + "Twoje łóżko zostało zniszczone - jesteś wyeliminowany.");

                    if (isGameOver(arena)) {
                        endGameWithTeleport(arena, false);
                    }

                }, 1L);
            }
        }

        @EventHandler
        public void onPlayerRespawn(PlayerRespawnEvent ev) {

            Player player = ev.getPlayer();
            Arena arena = (Arena) plugin.getPlayerArena().get(player.getUniqueId());
            if (player.getGameMode() == GameMode.SPECTATOR) return;
            if (arena != null) {
                Team team = (Team) plugin.getPlayerTeam().get(player.getUniqueId());
                if (team != null) {
                    List<Location> spawns = team.getSpawns();
                    if (!spawns.isEmpty()) {
                        ev.setRespawnLocation((Location) spawns.get((new Random()).nextInt(spawns.size())));
                    }

                    Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                        if (player.isOnline()) {
                            player.getInventory().clear();
                            SklepDruzyn.PlayerArmor armor = (SklepDruzyn.PlayerArmor) this.plugin.getShop().playerArmorMap.get(player.getUniqueId());
                            if (armor != null) {
                                player.getInventory().setHelmet(armor.getHelmet());
                                player.getInventory().setChestplate(armor.getChestplate());
                                player.getInventory().setLeggings(armor.getLeggings());
                                player.getInventory().setBoots(armor.getBoots());
                            } else {
                                Color teamColor = team.getLeatherColor();
                                player.getInventory().setHelmet(this.createLeatherArmor(Material.LEATHER_HELMET, teamColor));
                                player.getInventory().setChestplate(this.createLeatherArmor(Material.LEATHER_CHESTPLATE, teamColor));
                                player.getInventory().setLeggings(this.createLeatherArmor(Material.LEATHER_LEGGINGS, teamColor));
                                player.getInventory().setBoots(this.createLeatherArmor(Material.LEATHER_BOOTS, teamColor));
                            }

                            player.getInventory().addItem(new ItemStack[]{new ItemStack(Material.WOODEN_SWORD)});
                            player.getInventory().addItem(new ItemStack[]{new ItemStack(Material.APPLE, 3)});
                            player.updateInventory();
                        }
                    }, 1L);
                }
            }
        }

        private ItemStack createLeatherArmor(Material mat, Color color) {
            ItemStack item = new ItemStack(mat);
            LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
            if (meta != null) {
                meta.setColor(color);
                item.setItemMeta(meta);
            }

            return item;
        }

        @EventHandler
        public void onEntityDamageByEntity(EntityDamageByEntityEvent ev) {

            Entity var3 = ev.getEntity();
            if (var3 instanceof Player victim) {
                Player var8 = null;
                Entity var6 = ev.getDamager();
                if (var6 instanceof Player p) {
                    var8 = p;
                } else {
                    var6 = ev.getDamager();
                    if (var6 instanceof Projectile proj) {
                        ProjectileSource var7 = proj.getShooter();
                        if (var7 instanceof Player shooter) {
                            var8 = shooter;
                        }
                    }
                }

                Arena arena = (Arena) plugin.getPlayerArena().get(victim.getUniqueId());
                if (arena != null) {
                    if (!arena.isInGame()) {
                        ev.setCancelled(true);
                    } else {
                        if (var8 != null) {
                            Team vTeam = (Team) plugin.getPlayerTeam().get(victim.getUniqueId());
                            Team dTeam = (Team) plugin.getPlayerTeam().get(var8.getUniqueId());
                            if (vTeam != null && dTeam != null && vTeam == dTeam) {
                                ev.setCancelled(true);
                                var8.sendMessage(String.valueOf(ChatColor.RED) + "Nie możesz atakować członka swojej drużyny!");
                                return; // ✅ ważne, żeby nie zapisywać friendly-fire
                            }

                            // ✅ zapis "kto ostatnio uderzył" (do voida)
                            lastDamager.put(victim.getUniqueId(), var8.getUniqueId());
                            lastDamagerTime.put(victim.getUniqueId(), System.currentTimeMillis());
                        }

                    }
                }

            }
        }

        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent ev) {
            Player player = ev.getPlayer();
            UUID uuid = player.getUniqueId();
            Arena arena = plugin.getPlayerArena().get(uuid);

            if (arena != null) {
                arena.getPlayersInArena().remove(uuid);
                arena.getEliminated().add(uuid);

                // usuń lokalne
                plugin.getPlayerArena().remove(uuid);
                plugin.getPlayerTeam().remove(uuid);

                // ✅ usuń globalne (TAB/HUB na tym działa!)
                plugin.getPlayerArena().remove(uuid);
                plugin.getPlayerTeam().remove(uuid);

                plugin.getTabListaBW().updateArenaTab(arena);

                BukkitRunnable t = this.observerTasks.remove(uuid);
                if (t != null) t.cancel();

                if (arena.getPlayersInArena().isEmpty()) {
                    arena.setInGame(false);
                    if (arena.getGameTask() != null) arena.getGameTask().cancel();
                    if (arena.getLobbyTask() != null) arena.getLobbyTask().cancel();
                }
            }
            // ✅ jeśli gra trwa i po wyjściu został 1 team lub 0 graczy -> kończ
            if (arena.isInGame()) {
                if (arena.getPlayersInArena().isEmpty()) {
                    endGameWithTeleport(arena, false);
                    return;
                }

                if (isGameOver(arena)) {
                    endGameWithTeleport(arena, false);
                }
            }
        }


        // -----------------------------
// Lobby scoreboard
// -----------------------------
// gdzieś w klasie (cache ostatnich linii lobby na gracza)
        private final Map<UUID, List<String>> lastLobbyLines = new HashMap<>();

        private Objective getOrCreateLobbyObj(Scoreboard sb) {
            Objective obj = sb.getObjective("lobbySB");
            if (obj == null) {
                obj = sb.registerNewObjective("lobbySB", "dummy", ChatColor.GOLD + "BedWars - Lobby");
                obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            } else {
                obj.setDisplayName(ChatColor.GOLD + "BedWars - Lobby");
                if (obj.getDisplaySlot() != DisplaySlot.SIDEBAR) obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            }
            return obj;
        }

        private void updateLobbyScoreboard(Player p, Arena arena, int secondsLeft) {
            Scoreboard sb = getOrCreatePlayerBoard(p); // ✅ wspólny z TAB
            if (sb == null) return;

            Objective obj = sb.getObjective("lobbySB");
            if (obj == null) {
                obj = sb.registerNewObjective("lobbySB", "dummy", ChatColor.GOLD + "BedWars - Lobby");
                obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            } else {
                obj.setDisplayName(ChatColor.GOLD + "BedWars - Lobby");
                if (obj.getDisplaySlot() != DisplaySlot.SIDEBAR) obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            }

            List<String> lines = new ArrayList<>();
            lines.add(ChatColor.YELLOW + "Mapa: " + ChatColor.WHITE + arena.getName());
            lines.add(ChatColor.AQUA + "Graczy: " + ChatColor.WHITE + arena.getPlayersInArena().size() + "/" + arena.getMaxPlayers());
            lines.add(ChatColor.GREEN + "Start za: " + ChatColor.WHITE + secondsLeft + "s");
            lines.add(" ");

            List<String> old = lastLobbyLines.getOrDefault(p.getUniqueId(), List.of());
            for (String s : old) sb.resetScores(s);

            int score = lines.size();
            for (String s : lines) obj.getScore(s).setScore(score--);

            lastLobbyLines.put(p.getUniqueId(), new ArrayList<>(lines));

            if (p.getScoreboard() != sb) p.setScoreboard(sb);
        }


        // -----------------------------
// In-game scoreboard
// -----------------------------
        private void updateInGameScoreboard(Player p, Arena arena, int secondsLeft) {
            if (!arena.isInGame()) return; // ✅ ODWROTNIE
            List<String> lines = new ArrayList<>();
            lines.add(ChatColor.GRAY + "mcKosmo.pl");
            lines.add(" ");

            lines.add(ChatColor.WHITE + "Mapa: " + ChatColor.YELLOW + arena.getName());
            lines.add(ChatColor.WHITE + "Czas: " + ChatColor.GREEN + formatTime(secondsLeft));
            lines.add(ChatColor.DARK_GRAY + "────────────");

            for (Team t : arena.getTeams()) {
                int alivePlayers = countAlivePlayers(arena, t);

                String icon;
                if (!t.hasEverHadPlayer()) icon = ChatColor.DARK_GRAY + "❌";
                else if (!t.isBedDestroyed()) icon = ChatColor.RED + "❤";
                else icon = ChatColor.GRAY + "☠";

                lines.add(t.getColor() + t.getId() + ChatColor.GRAY + "  " + icon + ChatColor.GRAY + " (" + alivePlayers + ")");
            }

            lines.add(ChatColor.DARK_GRAY + "──────────── ");

            Team yourTeam = plugin.getPlayerTeam().get(p.getUniqueId());
            if (yourTeam != null) {
                lines.add(ChatColor.WHITE + "Twoja drużyna:");
                lines.add(yourTeam.getColor() + yourTeam.getId());
            }

            setSidebar(p, ChatColor.RED + "" + ChatColor.BOLD + "BEDWARS", lines);
        }


        private int countAlivePlayers(Arena arena, Team team) {
            int alive = 0;
            for (UUID uuid : arena.getPlayersInArena()) {
                if (arena.getEliminated().contains(uuid)) continue;
                Team t = plugin.getPlayerTeam().get(uuid);
                if (t == team) alive++;
            }
            return alive;
        }


        private String formatTime(int seconds) {
            int m = seconds / 60;
            int s = seconds % 60;
            return String.format("%02d:%02d", m, s);
        }

        private void startObserverTask(final Player victim, final Arena arena) {
            BukkitRunnable task = new BukkitRunnable() {
                public void run() {
                    ensureTeamsAssignedForLobby(arena);

                    if (!victim.isOnline()) {
                        this.cancel();
                    } else {
                        for (UUID u : arena.getPlayersInArena()) {
                            if (!u.equals(victim.getUniqueId())) {
                                Player p = Bukkit.getPlayer(u);
                                if (p != null && p.isOnline()) {
                                    double dist = p.getLocation().distance(victim.getLocation());
                                    if (dist < (double) 2.0F) {
                                        Vector diff = victim.getLocation().toVector().subtract(p.getLocation().toVector());
                                        if (diff.length() > (double) 0.0F && Double.isFinite(diff.getX())) {
                                            Vector push = diff.normalize().multiply(0.3);
                                            victim.setVelocity(push);
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            };
            task.runTaskTimer(this.plugin, 0L, 5L);
            this.observerTasks.put(victim.getUniqueId(), task);
        }

        @EventHandler
        public void onBlockPlace(BlockPlaceEvent e) {
            Player p = e.getPlayer();
            Arena arena = (Arena) plugin.getPlayerArena().get(p.getUniqueId());
            if (arena != null && arena.isInGame()) {
                this.playerPlacedBlocks.add(e.getBlock().getLocation());
            }
        }

        @EventHandler
        public void onBlockBreak(BlockBreakEvent e) {
            Player p = e.getPlayer();
            Arena arena = (Arena) plugin.getPlayerArena().get(p.getUniqueId());
            if (arena != null && arena.isInGame()) {
                Block block = e.getBlock();
                Location loc = block.getLocation();
                if (!(block.getBlockData() instanceof Bed)) {
                    if (this.playerPlacedBlocks.contains(loc)) {
                        this.playerPlacedBlocks.remove(loc);
                    } else {
                        e.setCancelled(true);
                        p.sendMessage(String.valueOf(ChatColor.RED) + "Nie możesz niszczyć bloków mapy!");
                    }
                }
            }
        }
        private void removeArenaBossBar(Arena arena) {
            if (arena == null) return;
            String key = arena.getName().toLowerCase();

            org.bukkit.boss.BossBar bar = arenaBars.remove(key);
            if (bar != null) {
                bar.setVisible(false);
                for (Player p : new ArrayList<>(bar.getPlayers())) bar.removePlayer(p);
            }
            arenaBarFlashUntil.remove(key);
            arenaBarFlashText.remove(key);
        }
        private void flashArenaBar(Arena arena, String text, long ms) {
            if (arena == null || !arena.isInGame()) return;
            String key = arena.getName().toLowerCase();

            arenaBarFlashText.put(key, text);
            arenaBarFlashUntil.put(key, System.currentTimeMillis() + ms);
        }
        public void endGameWithTeleport(Arena arena, boolean timeUp) {

            if (arena == null) return;

            // STOP TAB updater od razu
            plugin.getTabListaBW().stopAutoUpdater(arena);

            // zatrzymaj flagi gry
            arena.setInGame(false);
            arena.setCountingDown(false);

            // cancel taski
            if (arena.getGameTask() != null) {
                arena.getGameTask().cancel();
                arena.setGameTask(null);
            }
            if (arena.getLobbyTask() != null) {
                arena.getLobbyTask().cancel();
                arena.setLobbyTask(null);
            }

            // STOP generatory mapy (bez usuwania hologramów "logicznie")
            if (arena.getGeneratorMapy() != null) {
                arena.getGeneratorMapy().stopForEndGameReset();
            }

            // reset faz
            arena.setCurrentPhase(1);
            arena.setPhaseTimeLeft(arena.getPhaseDuration());

            // STOP generatory drużyn
            plugin.getGeneratorDruzyny().stop();

            // ===== ZWYCIĘZCA =====
            Set<Team> aliveTeams = arena.getPlayersInArena().stream()
                    .filter(u -> !arena.getEliminated().contains(u))
                    .map(u -> plugin.getPlayerTeam().get(u))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Team winningTeam = (aliveTeams.size() == 1) ? aliveTeams.iterator().next() : null;
            String winnerName = (winningTeam != null)
                    ? (String.valueOf(this.getTeamColor(winningTeam)) + winningTeam.getId())
                    : "Brak zwycięzcy";

            // ===== LISTA GRACZY DO TELEPORTU =====
            Set<UUID> all = new HashSet<>();
            all.addAll(arena.getPlayersInArena());
            all.addAll(arena.getEliminated());

            List<Player> toTeleport = new ArrayList<>();
            this.playerPlacedBlocks.clear();

            // ===== RANKED: WYLICZ ZWYCIĘZCÓW / PRZEGRANYCH =====
            Set<UUID> winnersUuids = new HashSet<>();
            Set<UUID> losersUuids = new HashSet<>(all);

            if (winningTeam != null) {
                for (UUID u : all) {
                    Team t = plugin.getPlayerTeam().get(u);
                    if (t != null && t.equals(winningTeam)) winnersUuids.add(u);
                }
                losersUuids.removeAll(winnersUuids);
            }

            // ===== PRZYDZIEL PUNKTY (RAZ) =====
            RankedSystem rankedSystem = plugin.getRankedSystem();
            if (rankedSystem != null && winningTeam != null && arena.isRanked()) {
                for (UUID u : winnersUuids) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                    rankedSystem.applyPointsChange(u, op.getName(), +25, "WIN");
                }
                for (UUID u : losersUuids) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                    rankedSystem.applyPointsChange(u, op.getName(), -15, "LOSE");
                }
            }

            // ===== WIADOMOŚCI / CZYSZCZENIE GRACZY =====
            for (UUID u : all) {
                Player p = Bukkit.getPlayer(u);
                if (p == null || !p.isOnline()) continue;

                toTeleport.add(p);

                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();

                // czyść sidebar/tab
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                p.setPlayerListHeaderFooter("", "");
                plugin.getSklepDruzyn().clearQuickBuyIfNeeded(p);
                p.sendMessage(ChatColor.GOLD + "Koniec gry! Zwycięzca: " + ChatColor.GREEN + winnerName);
            }

            // usuń itemy z areny
            removeItemsFromArena(arena);

            // usuń itemy z generatorów drużyn
            for (Team t : arena.getTeams()) {
                Location loc = t.getGeneratorLocation();
                if (loc != null && loc.getWorld() != null) {
                    loc.getWorld().getNearbyEntities(loc, 2.0, 2.0, 2.0).forEach(e -> {
                        if (e instanceof Item) e.remove();
                    });
                }
            }
            removeArenaBossBar(arena);
            // ===== TELEPORT + RESET MAPY =====
            Bukkit.getScheduler().runTaskLater(plugin, () -> {

                // lobby globalne
                Location lobby = getGlobalLobby();
                if (lobby == null && arena.getServerLobby() != null) lobby = arena.getServerLobby();

                // teleport graczy
                for (Player p : toTeleport) {
                    if (p == null || !p.isOnline()) continue;

                    if (lobby != null) {
                        p.teleport(lobby);
                        p.sendMessage(ChatColor.GREEN + "Teleportowano do lobby!");
                    } else {
                        p.sendMessage(ChatColor.RED + "⚠️ Lobby nie jest ustawione! Ustaw: /bedwars setlobby");
                    }

                    // usuń z map
                    plugin.getPlayerTeam().remove(p.getUniqueId());
                    plugin.getPlayerArena().remove(p.getUniqueId());

                    // HUB tab dopiero po teleport/zmianie świata
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline()) plugin.getTabListaBW().applyHubNow(p);
                    }, 10L);
                }

                // cancel observer tasks
                for (UUID u : new HashSet<>(this.observerTasks.keySet())) {
                    BukkitRunnable t = this.observerTasks.remove(u);
                    if (t != null) t.cancel();
                }

                // reset areny w pamięci
                arena.getPlayersInArena().clear();
                arena.getEliminated().clear();
                arena.setMapNeedsRestart(true);

                MapResetManager mapManager = plugin.getMapResetManager();
                mapManager.setArenaStatus(arena.getName(), "Badanie terenu");
                mapManager.setArenaProgress(arena.getName(), 0);

                Bukkit.broadcastMessage(ChatColor.YELLOW + "🔍 Arena " + arena.getName()
                        + " jest badana i przygotowywana do odbudowy...");

                // reset flag / teamów / countdownów
                resetArenaState(arena);

                // restore bloków (może usuwać entity -> hologramy też)
                World world = arena.getWorld();
                if (world != null) {
                    mapManager.restoreChangedBlocks(arena.getName());

                    // ✅ NAJWAŻNIEJSZE: odbuduj hologramy po resecie mapy
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (arena.getGeneratorMapy() != null) {
                            arena.getGeneratorMapy().rebuildHolograms(1);
                        }
                    }, 5L);

                } else {
                    Bukkit.getLogger().warning("[BedWars] Nie udało się znaleźć świata dla areny: " + arena.getName());
                }

            }, 1L);
        }
    }

    public static class Arena {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        private final String name;
        private Location lobby;
        private Location serverLobby;
        private int minPlayers = 2;
        private int maxPlayers = 16;
        private int countdown = 15;
        private boolean countingDown = false;
        private final Map<String, Team> teams = new LinkedHashMap();
        private final List<Location> playerSpawns = new ArrayList();
        private final List<Material> protectedBlocks = new ArrayList();
        private int currentPhase = 1;
        private final int phaseDuration = 600;
        private int phaseTimeLeft = 600;
        private final Set<UUID> playersInArena = new HashSet();
        private final Set<UUID> eliminated = new HashSet();
        private boolean inGame = false;
        private int gameTime = 1800;
        private BukkitRunnable lobbyTask = null;
        private BukkitRunnable gameTask = null;
        private boolean mapNeedsRestart = false;
        private String mapAuthor = null;
        private GeneratorMapy generatorMapy;
        // ✅ RANKED (per arena)
        private boolean ranked = false;
        private String rankedMin = "gold:I";

        public boolean isRanked() {
            return ranked;
        }

        public void setRanked(boolean ranked) {
            this.ranked = ranked;
        }

        public String getRankedMin() {
            return rankedMin;
        }

        public void setRankedMin(String rankedMin) {
            if (rankedMin == null || rankedMin.isEmpty()) rankedMin = "gold:I";
            this.rankedMin = rankedMin;
        }

        public int getCurrentPhase() {
            return this.currentPhase;
        }

        public void setCurrentPhase(int p) {
            this.currentPhase = p;
        }

        public int getPhaseTimeLeft() {
            return this.phaseTimeLeft;
        }

        public void setPhaseTimeLeft(int t) {
            this.phaseTimeLeft = t;
        }

        public int getPhaseDuration() {
            return 600;
        }

        public Arena(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public void setLobby(Location l) {
            this.lobby = l;
        }

        public Location getLobby() {
            return this.lobby;
        }

        public void setServerLobby(Location l) {
            this.serverLobby = l;
        }

        public Location getServerLobby() {
            return this.serverLobby;
        }

        public void addTeam(Team t) {
            this.teams.put(t.getId().toUpperCase(), t);
        }

        public Team getTeam(String id) {
            return (Team) this.teams.get(id.toUpperCase());
        }

        public Collection<Team> getTeams() {
            return this.teams.values();
        }

        public Set<String> getTeamIds() {
            return this.teams.keySet();
        }

        public void removeTeam(String id) {
            this.teams.remove(id.toUpperCase());
        }

        public int teamCount() {
            return this.teams.size();
        }

        public void addTeamSpawn(String teamId, Location loc) {
            Team t = this.getTeam(teamId);
            if (t != null) {
                t.addSpawn(loc);
            }

        }

        public List<Location> getPlayerSpawns() {
            return this.playerSpawns;
        }

        public void setTeamBed(String teamId, Location loc) {
            Team t = this.getTeam(teamId);
            if (t != null) {
                t.setBedLocation(loc);
                t.setBedDestroyed(false);
            }

        }

        public Location getTeamBed(String teamId) {
            Team t = this.getTeam(teamId);
            return t != null ? t.getBedLocation() : null;
        }

        public boolean isBedDestroyed(Team t) {
            return t == null || t.isBedDestroyed();
        }

        public void setBedDestroyed(Team t, boolean v) {
            if (t != null) {
                t.setBedDestroyed(v);
            }

        }

        public Team getTeamByBed(Location loc) {
            if (loc == null) {
                return null;
            } else {
                for (Team t : this.teams.values()) {
                    Location l = t.getBedLocation();
                    if (l != null && l.getWorld().equals(loc.getWorld())) {
                        Block lBlock = l.getBlock();
                        Bed bed = null;
                        BlockData var8 = lBlock.getBlockData();
                        if (var8 instanceof Bed) {
                            Bed b = (Bed) var8;
                            Block headBlock = b.getPart() == Part.HEAD ? lBlock : lBlock.getRelative(b.getFacing());
                            Block footBlock = b.getPart() == Part.FOOT ? lBlock : lBlock.getRelative(b.getFacing().getOppositeFace());
                            if (loc.getBlockX() == headBlock.getX() && loc.getBlockY() == headBlock.getY() && loc.getBlockZ() == headBlock.getZ() || loc.getBlockX() == footBlock.getX() && loc.getBlockY() == footBlock.getY() && loc.getBlockZ() == footBlock.getZ()) {
                                return t;
                            }
                        }
                    }
                }

                return null;
            }
        }

        public GeneratorMapy getGeneratorMapy() {
            return this.generatorMapy;
        }

        public void setGeneratorMapy(GeneratorMapy generatorMapy) {
            this.generatorMapy = generatorMapy;
        }

        public void addProtectedBlock(Material m) {
            if (!this.protectedBlocks.contains(m)) {
                this.protectedBlocks.add(m);
            }

        }

        public List<Material> getProtectedBlocks() {
            return this.protectedBlocks;
        }

        public void setProtectedBlocks(List<Material> list) {
            this.protectedBlocks.clear();
            this.protectedBlocks.addAll(list);
        }

        public Set<UUID> getPlayersInArena() {
            return this.playersInArena;

        }

        private Location regenP1;
        private Location regenP2;

        public Location getRegenP1() { return regenP1; }
        public Location getRegenP2() { return regenP2; }
        public void setRegenP1(Location l) { regenP1 = l; }
        public void setRegenP2(Location l) { regenP2 = l; }
        private Location enterP1;
        private Location enterP2;

        public Location getEnterP1() { return enterP1; }
        public Location getEnterP2() { return enterP2; }
        public void setEnterP1(Location l) { enterP1 = l; }
        public void setEnterP2(Location l) { enterP2 = l; }

        public boolean hasEnterRegion() {
            return enterP1 != null && enterP2 != null && enterP1.getWorld() != null && enterP2.getWorld() != null;
        }

        public boolean hasRegenRegion() {
            return regenP1 != null && regenP2 != null && regenP1.getWorld() != null && regenP2.getWorld() != null;
        }
        public Set<UUID> getEliminated() {
            return this.eliminated;
        }

        public boolean isInGame() {
            return this.inGame;
        }

        public void setInGame(boolean v) {
            this.inGame = v;
        }

        public int getCountdown() {
            return this.countdown;
        }

        public void setCountdown(int c) {
            this.countdown = c;
        }

        public boolean isCountingDown() {
            return this.countingDown;
        }

        public void setCountingDown(boolean v) {
            this.countingDown = v;
        }

        public int getMinPlayers() {
            return this.minPlayers;
        }

        public void setMinPlayers(int m) {
            this.minPlayers = m;
        }

        public int getMaxPlayers() {
            return this.maxPlayers;
        }

        public void setMaxPlayers(int m) {
            this.maxPlayers = m;
        }


        public int countTeam(Team t, Map<UUID, Team> playerTeam) {
            return t == null ? 0 : (int) this.playersInArena.stream().filter((u) -> playerTeam.get(u) == t && !this.eliminated.contains(u)).count();
        }

        public int getGameTime() {
            return this.gameTime;
        }

        public void setGameTime(int t) {
            this.gameTime = t;
        }

        public BukkitRunnable getLobbyTask() {
            return this.lobbyTask;
        }

        public void setLobbyTask(BukkitRunnable t) {
            this.lobbyTask = t;
        }

        public BukkitRunnable getGameTask() {
            return this.gameTask;
        }

        public void setGameTask(BukkitRunnable t) {
            this.gameTask = t;
        }

        public boolean isMapNeedsRestart() {
            return this.mapNeedsRestart;
        }

        public void setMapNeedsRestart(boolean v) {
            this.mapNeedsRestart = v;
        }

        public String getMapAuthor() {
            return this.mapAuthor;
        }

        public void setMapAuthor(String s) {
            this.mapAuthor = s;
        }

        public World getWorld() {
            if (this.lobby != null) {
                return this.lobby.getWorld();
            } else if (!this.playerSpawns.isEmpty() && this.playerSpawns.get(0) != null) {
                return ((Location) this.playerSpawns.get(0)).getWorld();
            } else {
                for (Team t : this.teams.values()) {
                    if (!t.getSpawns().isEmpty() && t.getSpawns().get(0) != null) {
                        return ((Location) t.getSpawns().get(0)).getWorld();
                    }
                }

                for (Team t : this.teams.values()) {
                    if (t.getBedLocation() != null) {
                        return t.getBedLocation().getWorld();
                    }
                }

                return null;
            }
        }

        public void setTeamShop(Team team, Location loc) {
            if (team != null) {
                team.setShopLocation(loc);
            }

        }

        private ArenaMode arenaMode = ArenaMode.SOLO;

        public ArenaMode getArenaMode() {
            return arenaMode;
        }

        public void setArenaMode(ArenaMode mode) {
            this.arenaMode = mode;
            recalcLimits();
        }

        private void recalcLimits() {
            int teamsCount = this.teams.size();
            int perTeam = arenaMode.getPlayersPerTeam();

            this.maxPlayers = teamsCount * perTeam;
        }

        private Scoreboard scoreboard;

        public Scoreboard getScoreboard() {
            return this.scoreboard;
        }

        public void setScoreboard(Scoreboard sb) {
            this.scoreboard = sb;
        }

        public Location getTeamShop(Team team) {
            return team != null ? team.getShopLocation() : null;
        }

        public boolean hasPlayer(Player player) {
            return this.playersInArena.contains(player.getUniqueId());
        }
    }

    public static class Team implements Serializable {
        private Location generatorLocation;
        private final String id;
        private ChatColor color;
        private Material woolMaterial;
        private final List<Location> spawns = new ArrayList();
        private Location bedLocation = null;
        private boolean bedDestroyed = false;
        private Location shopLocation = null;
        private boolean everHadPlayer = false;

        public boolean hasEverHadPlayer() {
            return everHadPlayer;
        }

        public void setEverHadPlayer(boolean everHadPlayer) {
            this.everHadPlayer = everHadPlayer;
        }
        public void setGeneratorLocation(Location loc) {
            this.generatorLocation = loc;
        }

        public Location getGeneratorLocation() {
            return this.generatorLocation;
        }

        public Team(String id, ChatColor color, Material woolMaterial) {
            this.id = id;
            this.color = color;
            this.woolMaterial = woolMaterial;
        }

        public String getId() {
            return this.id;
        }

        public ChatColor getColor() {
            return this.color;
        }

        public void setColor(ChatColor color) {
            this.color = color;
        }

        public Material getWoolMaterial() {
            return this.woolMaterial;
        }

        public void setWoolMaterial(Material m) {
            this.woolMaterial = m;
        }

        public List<Location> getSpawns() {
            return this.spawns;
        }

        public void addSpawn(Location loc) {
            this.spawns.add(loc);
        }

        public void clearSpawns() {
            this.spawns.clear();
        }

        public Color getLeatherColor() {
            if (this.color == null) return Color.WHITE;

            return switch (this.color) {
                case RED -> Color.RED;
                case BLUE -> Color.BLUE;
                case GREEN -> Color.GREEN;
                case YELLOW -> Color.YELLOW;

                case DARK_AQUA -> Color.fromRGB(0x00, 0xAA, 0xAA);      // cyan
                case GRAY -> Color.fromRGB(0xAA, 0xAA, 0xAA);           // jasny szary
                case LIGHT_PURPLE -> Color.fromRGB(0xFF, 0x55, 0xFF);   // różowy
                case BLACK -> Color.BLACK;
                default -> Color.WHITE;
            };
        }





        public Location getBedLocation() {
            return this.bedLocation;
        }

        public void setBedLocation(Location loc) {
            this.bedLocation = loc;
        }

        public boolean isBedDestroyed() {
            return this.bedDestroyed;
        }

        public void setBedDestroyed(boolean v) {
            this.bedDestroyed = v;
        }

        public Location getShopLocation() {
            return this.shopLocation;
        }

        public void setShopLocation(Location loc) {
            this.shopLocation = loc;
        }
    }
}

