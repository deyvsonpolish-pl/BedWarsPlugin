package org.BedWars;

import java.io.File;
import java.io.IOException;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.util.Vector;

public class BedWarsPlugin extends JavaPlugin implements Listener, TabExecutor {
    private SklepDruzyn sklepDruzyn;
    private ArenaManager arenaManager;
    private static BedWarsPlugin instance;
    private MapResetManager mapResetManager;
    private MapRegionTool mapRegionTool;
    private SklepDruzyn shop;
    private GeneratorDruzyny generatorDruzyny;
    private GeneratorMapy generatorMapy;
    private NPCManager npcManager;

    public SklepDruzyn getSklepDruzyn() {
        return this.sklepDruzyn;
    }

    public SklepDruzyn getShop() {
        return this.sklepDruzyn;
    }

    public GeneratorDruzyny getGeneratorDruzyny() {
        return this.generatorDruzyny;
    }

    public GeneratorMapy getGeneratorMapy() {
        return this.generatorMapy;
    }

    public NPCManager getNpcManager() {
        return this.npcManager;
    }

    public void onEnable() {
        instance = this;
        this.saveDefaultConfig();
        // Sprawdzenie aktualizacji automatycznie
        AutoUpdater updater = new AutoUpdater(this, "deyvsonpolish-pl", "BedWars", "BedWarsPlugin.jar");
        updater.checkAndUpdate();
        this.removeOldNPCs();
        this.npcManager = new NPCManager(this);
        this.mapRegionTool = new MapRegionTool(this);
        this.arenaManager = new ArenaManager(this);
        this.mapResetManager = new MapResetManager(this);
        this.sklepDruzyn = new SklepDruzyn(this);
        this.generatorDruzyny = new GeneratorDruzyny(this);
        this.generatorMapy = new GeneratorMapy(this);
        this.getServer().getPluginManager().registerEvents(this.npcManager, this);
        this.getServer().getPluginManager().registerEvents(this.arenaManager, this);
        this.getServer().getPluginManager().registerEvents(this.sklepDruzyn, this);
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("bedwars").setExecutor(this);
        this.getCommand("bedwars").setTabCompleter(this);
        this.arenaManager.loadArenas();

        try {
            this.sklepDruzyn.removeAllShops();
        } catch (Exception e) {
            this.getLogger().warning("removeAllShops() error: " + e.getMessage());
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                this.sklepDruzyn.respawnShopsFromArenas();
            } catch (Exception e) {
                this.getLogger().warning("respawnShopsFromArenas() error: " + e.getMessage());
            }

        }, 20L);
        Bukkit.getScheduler().runTaskLater(this, () -> this.npcManager.loadNPC(), 20L);
        this.generatorDruzyny.start();
        this.getLogger().info("BedWarsPlugin enabled.");
    }

    public void onDisable() {
        if (this.arenaManager != null) {
            for(Arena arena : this.arenaManager.getArenas()) {
                if (arena.getGeneratorMapy() != null) {
                    arena.getGeneratorMapy().stop();
                }
            }

            try {
                this.arenaManager.saveArenas();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        if (this.sklepDruzyn != null) {
            try {
                this.sklepDruzyn.removeAllShops();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        this.removeOldNPCs();
        this.getLogger().info("BedWarsPlugin disabled.");
    }

    private void removeOldNPCs() {
        for(World w : Bukkit.getWorlds()) {
            for(Entity e : w.getEntities()) {
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

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            if (args.length == 0) {
                this.arenaManager.openArenaSelectGUI(p);
                return true;
            } else {
                switch (args[0].toLowerCase()) {
                    case "create":
                        if (args.length < 2) {
                            p.sendMessage(String.valueOf(ChatColor.RED) + "Usage: /bedwars create <name> [teamsCount]");
                            return true;
                        } else {
                            String name = args[1].toLowerCase();
                            if (this.arenaManager.getArena(name) != null) {
                                p.sendMessage(String.valueOf(ChatColor.RED) + "Arena already exists");
                                return true;
                            } else {
                                Arena a = new Arena(name);
                                a.setServerLobby(p.getLocation());
                                int teamsCount = 4;
                                if (args.length >= 3) {
                                    try {
                                        teamsCount = Math.max(1, Integer.parseInt(args[2]));
                                    } catch (Exception var17) {
                                    }
                                }

                                for(int i = 0; i < teamsCount; ++i) {
                                    String id = "TEAM" + (i + 1);
                                    ChatColor var10000;
                                    switch (i) {
                                        case 0 -> var10000 = ChatColor.RED;
                                        case 1 -> var10000 = ChatColor.BLUE;
                                        case 2 -> var10000 = ChatColor.GREEN;
                                        case 3 -> var10000 = ChatColor.YELLOW;
                                        case 4 -> var10000 = ChatColor.AQUA;
                                        case 5 -> var10000 = ChatColor.LIGHT_PURPLE;
                                        default -> var10000 = ChatColor.WHITE;
                                    }

                                    ChatColor color = var10000;
                                    Material var21;
                                    switch (i) {
                                        case 0 -> var21 = Material.RED_WOOL;
                                        case 1 -> var21 = Material.BLUE_WOOL;
                                        case 2 -> var21 = Material.GREEN_WOOL;
                                        case 3 -> var21 = Material.YELLOW_WOOL;
                                        case 4 -> var21 = Material.CYAN_WOOL;
                                        case 5 -> var21 = Material.PURPLE_WOOL;
                                        default -> var21 = Material.WHITE_WOOL;
                                    }

                                    Material wool = var21;
                                    Team t = new Team(id, color, wool);
                                    a.addTeam(t);
                                }

                                this.arenaManager.addArena(a);
                                String var10001 = String.valueOf(ChatColor.GREEN);
                                p.sendMessage(var10001 + "Arena " + name + " created. Use /bedwars edit " + name);
                                return true;
                            }
                        }
                    case "edit":
                        if (args.length < 2) {
                            p.sendMessage(String.valueOf(ChatColor.RED) + "Usage: /bedwars edit <name>");
                            return true;
                        } else {
                            Arena a = this.arenaManager.getArena(args[1].toLowerCase());
                            if (a == null) {
                                p.sendMessage(String.valueOf(ChatColor.RED) + "Arena not found");
                                return true;
                            }

                            this.arenaManager.openArenaSetupGUI(p, a);
                            return true;
                        }
                    case "setlobby":
                        this.arenaManager.setGlobalLobby(p.getLocation());
                        p.sendMessage(String.valueOf(ChatColor.GREEN) + "✅ Ustawiono globalne lobby i zapisano do config.yml!");
                        return true;
                    case "setnpc":
                        Location loc = p.getLocation();
                        getInstance().getNpcManager().spawnArenaNPC(p.getLocation());
                        p.sendMessage(String.valueOf(ChatColor.GREEN) + "✔ Ustawiono NPC aren!");
                        return true;
                    case "save":
                        this.arenaManager.saveArenas();
                        p.sendMessage(String.valueOf(ChatColor.GREEN) + "Arenas saved.");
                        return true;
                    case "join":
                        if (args.length < 2) {
                            p.sendMessage(String.valueOf(ChatColor.RED) + "Usage: /bedwars join <arena>");
                            return true;
                        } else {
                            Arena a = this.arenaManager.getArena(args[1].toLowerCase());
                            if (a == null) {
                                p.sendMessage(String.valueOf(ChatColor.RED) + "Arena not found");
                                return true;
                            }

                            this.arenaManager.joinArena(p, a);
                            return true;
                        }
                    default:
                        this.arenaManager.openArenaSelectGUI(p);
                        return true;
                }
            }
        } else {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? Arrays.asList("create", "edit", "setlobby", "setspawn", "setbed", "save", "join", "setnpc") : Collections.emptyList();
    }

    public static class ArenaManager implements Listener {
        private final BedWarsPlugin plugin;
        private final Map<String, Arena> arenas = new LinkedHashMap();
        private final Map<UUID, Arena> playerArena = new HashMap();
        private final Map<UUID, Team> playerTeam = new HashMap();
        private final Map<UUID, BukkitRunnable> observerTasks = new HashMap();
        private final String GUI_MAIN;
        private final String GUI_SETUP;
        private final String TEAM_GUI_TITLE;
        private final File arenasFile;
        private Location globalLobby;
        private final Set<Location> playerPlacedBlocks;

        public void removePlayerFromArena(Player player) {
            this.playerArena.remove(player.getUniqueId());
            this.playerTeam.remove(player.getUniqueId());
        }

        public void removeItemsFromArena(Arena arena) {
            World w = arena.getWorld();

            for(Entity e : w.getEntities()) {
                if (e instanceof Item) {
                    e.remove();
                }
            }

        }

        public void resetArenaState(Arena arena) {
            arena.getPlayersInArena().clear();
            arena.getEliminated().clear();
            arena.setInGame(false);
            arena.setCountingDown(false);
            arena.setCountdown(15);
            arena.setPhaseTimeLeft(arena.getPhaseDuration());
            arena.setCurrentPhase(1);

            for(Team t : arena.getTeams()) {
                t.setBedDestroyed(false);
            }

            Iterator<UUID> it = this.playerTeam.keySet().iterator();

            while(it.hasNext()) {
                UUID u = (UUID)it.next();
                Player p = Bukkit.getPlayer(u);
                if (p == null || !p.isOnline()) {
                    it.remove();
                    this.playerArena.remove(u);
                }
            }

        }

        public Arena getArenaForPlayer(Player player) {
            for(Arena arena : this.arenas.values()) {
                if (arena.getPlayersInArena().contains(player.getUniqueId())) {
                    return arena;
                }
            }

            return null;
        }

        public Team getPlayerTeam(Player player) {
            return (Team)this.playerTeam.get(player.getUniqueId());
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
            return (Arena)this.arenas.get(name.toLowerCase());
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

                for(Arena a : this.arenas.values()) {
                    String base = "arenas." + a.getName();
                    cfg.set(base + ".name", a.getName());
                    cfg.set(base + ".minPlayers", a.getMinPlayers());
                    cfg.set(base + ".maxPlayers", a.getMaxPlayers());
                    cfg.set(base + ".lobby", this.locToMap(a.getLobby()));
                    cfg.set(base + ".protectedBlocks", a.getProtectedBlocks().stream().map(Enum::name).collect(Collectors.toList()));
                    String teamsBase = base + ".teams";
                    cfg.set(teamsBase, (Object)null);

                    for(Team t : a.getTeams()) {
                        String tBase = teamsBase + "." + t.getId();
                        cfg.set(tBase + ".color", t.getColor() != null ? t.getColor().name() : ChatColor.WHITE.name());
                        cfg.set(tBase + ".wool", t.getWoolMaterial() != null ? t.getWoolMaterial().name() : Material.WHITE_WOOL.name());
                        cfg.set(tBase + ".bed", this.locToMap(t.getBedLocation()));
                        List<Map<String, Object>> spawns = (List)t.getSpawns().stream().map(this::locToMap).collect(Collectors.toList());
                        cfg.set(tBase + ".spawns", spawns);
                        if (t.getShopLocation() != null) {
                            cfg.set(tBase + ".shop", this.locToMap(t.getShopLocation()));
                        }

                        if (t.getGeneratorLocation() != null) {
                            cfg.set(tBase + ".generator", this.locToMap(t.getGeneratorLocation()));
                        }
                    }

                    if (a.getGeneratorMapy() != null) {
                        List<Map<String, Object>> diamondGens = a.getGeneratorMapy().getDiamondGenerators().stream().map(this::locToMap).toList();
                        List<Map<String, Object>> emeraldGens = a.getGeneratorMapy().getEmeraldGenerators().stream().map(this::locToMap).toList();
                        cfg.set(base + ".mapGenerators.diamond", diamondGens);
                        cfg.set(base + ".mapGenerators.emerald", emeraldGens);
                    }
                }

                cfg.save(this.arenasFile);
            } catch (IOException ex) {
                ex.printStackTrace();
            }

        }

        public void loadArenas() {
            if (this.arenasFile.exists()) {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.arenasFile);
                if (cfg.contains("arenas")) {
                    for(String key : cfg.getConfigurationSection("arenas").getKeys(false)) {
                        String base = "arenas." + key;
                        String name = cfg.getString(base + ".name", key);
                        Arena a = new Arena(name);
                        a.setMinPlayers(cfg.getInt(base + ".minPlayers", 2));
                        a.setMaxPlayers(cfg.getInt(base + ".maxPlayers", 16));
                        a.setLobby(this.mapToLoc(cfg.getConfigurationSection(base + ".lobby")));
                        if (cfg.contains(base + ".protectedBlocks")) {
                            List<String> list = cfg.getStringList(base + ".protectedBlocks");
                            List<Material> blocks = new ArrayList();

                            for(String s : list) {
                                try {
                                    blocks.add(Material.valueOf(s));
                                } catch (Exception var18) {
                                }
                            }

                            a.setProtectedBlocks(blocks);
                        }

                        ConfigurationSection teamsSec = cfg.getConfigurationSection(base + ".teams");
                        if (teamsSec == null) {
                            for(int i = 0; i < 4; ++i) {
                                String id = "TEAM" + (i + 1);
                                ChatColor var10000;
                                switch (i) {
                                    case 0 -> var10000 = ChatColor.RED;
                                    case 1 -> var10000 = ChatColor.BLUE;
                                    case 2 -> var10000 = ChatColor.GREEN;
                                    case 3 -> var10000 = ChatColor.YELLOW;
                                    default -> var10000 = ChatColor.WHITE;
                                }

                                ChatColor color = var10000;
                                Material var38;
                                switch (i) {
                                    case 0 -> var38 = Material.RED_WOOL;
                                    case 1 -> var38 = Material.BLUE_WOOL;
                                    case 2 -> var38 = Material.GREEN_WOOL;
                                    case 3 -> var38 = Material.YELLOW_WOOL;
                                    default -> var38 = Material.WHITE_WOOL;
                                }

                                Material wool = var38;
                                a.addTeam(new Team(id, color, wool));
                            }
                        } else {
                            for(String id : teamsSec.getKeys(false)) {
                                String colorName = teamsSec.getString(id + ".color", ChatColor.WHITE.name());
                                String woolName = teamsSec.getString(id + ".wool", Material.WHITE_WOOL.name());
                                ChatColor color = ChatColor.valueOf(colorName);

                                Material wool;
                                try {
                                    wool = Material.valueOf(woolName);
                                } catch (Exception var19) {
                                    wool = Material.WHITE_WOOL;
                                }

                                Team t = new Team(id, color, wool);
                                if (teamsSec.contains(id + ".spawns")) {
                                    for(Object o : teamsSec.getList(id + ".spawns", new ArrayList())) {
                                        if (o instanceof Map) {
                                            t.addSpawn(this.mapToLoc((Map)o));
                                        }
                                    }
                                }

                                if (teamsSec.contains(id + ".bed")) {
                                    t.setBedLocation(this.mapToLoc(teamsSec.getConfigurationSection(id + ".bed")));
                                }

                                if (teamsSec.contains(id + ".shop")) {
                                    t.setShopLocation(this.mapToLoc(teamsSec.getConfigurationSection(id + ".shop")));
                                }

                                if (teamsSec.contains(id + ".generator")) {
                                    Location genLoc = this.mapToLoc(teamsSec.getConfigurationSection(id + ".generator"));
                                    t.setGeneratorLocation(genLoc);
                                    this.plugin.getGeneratorDruzyny().setGenerator(t.getId(), genLoc);
                                }

                                a.addTeam(t);
                            }
                        }

                        a.setGeneratorMapy(new GeneratorMapy(this.plugin));
                        GeneratorMapy mapGen = a.getGeneratorMapy();
                        mapGen.stop();
                        if (cfg.contains(base + ".mapGenerators.diamond")) {
                            for(Object o : cfg.getList(base + ".mapGenerators.diamond", new ArrayList())) {
                                if (o instanceof Map) {
                                    Location loc = this.mapToLoc((Map)o);
                                    if (mapGen.getDiamondGenerators().stream().noneMatch((l) -> l.distanceSquared(loc) < 0.01)) {
                                        mapGen.getDiamondGenerators().add(loc);
                                        mapGen.addDiamondGeneratorPlaceholder(loc);
                                    }
                                }
                            }
                        }

                        if (cfg.contains(base + ".mapGenerators.emerald")) {
                            for(Object o : cfg.getList(base + ".mapGenerators.emerald", new ArrayList())) {
                                if (o instanceof Map) {
                                    Location loc = this.mapToLoc((Map)o);
                                    if (mapGen.getEmeraldGenerators().stream().noneMatch((l) -> l.distanceSquared(loc) < 0.01)) {
                                        mapGen.getEmeraldGenerators().add(loc);
                                        mapGen.addEmeraldGeneratorPlaceholder(loc);
                                    }
                                }
                            }
                        }

                        this.arenas.put(a.getName().toLowerCase(), a);
                    }

                }
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
            if (map == null) {
                return null;
            } else {
                String world = (String)map.get("world");
                World w = Bukkit.getWorld(world);
                if (w == null) {
                    return null;
                } else {
                    double x = ((Number)map.get("x")).doubleValue();
                    double y = ((Number)map.get("y")).doubleValue();
                    double z = ((Number)map.get("z")).doubleValue();
                    Number yawN = (Number)map.get("yaw");
                    Number pitchN = (Number)map.get("pitch");
                    float yaw = yawN != null ? yawN.floatValue() : 0.0F;
                    float pitch = pitchN != null ? pitchN.floatValue() : 0.0F;
                    return new Location(w, x, y, z, yaw, pitch);
                }
            }
        }

        public void openArenaSelectGUI(Player player) {
            Inventory inv = Bukkit.createInventory((InventoryHolder)null, 45, this.GUI_MAIN);
            ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta borderMeta = border.getItemMeta();
            borderMeta.setDisplayName(" ");
            border.setItemMeta(borderMeta);

            for(int i = 0; i < 45; ++i) {
                int row = i / 9;
                int col = i % 9;
                if (row == 0 || row == 4 || col == 0 || col == 8) {
                    inv.setItem(i, border);
                }
            }

            this.updateArenaGUI(inv);
            player.openInventory(inv);
            this.startArenaGuiAutoRefresh(player, inv);
        }

        private void updateArenaGUI(Inventory inv) {
            inv.clear();
            ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta borderMeta = border.getItemMeta();
            borderMeta.setDisplayName(" ");
            border.setItemMeta(borderMeta);

            for(int i = 0; i < 45; ++i) {
                int row = i / 9;
                int col = i % 9;
                if (row == 0 || row == 4 || col == 0 || col == 8) {
                    inv.setItem(i, border);
                }
            }

            int[] arenaSlots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            int i = 0;

            for(Arena a : this.arenas.values()) {
                if (i >= arenaSlots.length) {
                    break;
                }

                String status = this.plugin.getMapResetManager().getArenaStatus(a.getName());
                int progress = this.plugin.getMapResetManager().getArenaProgress(a.getName());
                Material block;
                if (status.equalsIgnoreCase("Restart")) {
                    block = Material.YELLOW_STAINED_GLASS;
                } else if (!a.isInGame() && status.equalsIgnoreCase("Badanie terenu")) {
                    block = Material.RED_STAINED_GLASS;
                } else if (a.isInGame()) {
                    block = Material.ORANGE_STAINED_GLASS;
                } else {
                    block = Material.LIME_STAINED_GLASS;
                }

                ItemStack item = new ItemStack(block);
                ItemMeta meta = item.getItemMeta();
                String var10001 = String.valueOf(ChatColor.AQUA);
                meta.setDisplayName(var10001 + a.getName());
                List<String> lore = new ArrayList();
                int activePlayers = (int)a.getPlayersInArena().stream().filter((uuid) -> !a.getEliminated().contains(uuid)).count();
                var10001 = String.valueOf(ChatColor.GRAY);
                lore.add(var10001 + "Gracze: " + activePlayers + "/" + a.getMaxPlayers());
                var10001 = String.valueOf(ChatColor.GOLD);
                lore.add(var10001 + "Autor: " + String.valueOf(ChatColor.AQUA) + (a.getMapAuthor() == null ? "Nie ustawiono" : a.getMapAuthor()));
                if (status.equalsIgnoreCase("Restart")) {
                    var10001 = String.valueOf(ChatColor.YELLOW);
                    lore.add(var10001 + "Status: " + String.valueOf(ChatColor.AQUA) + "Restart mapy");
                    var10001 = String.valueOf(ChatColor.GRAY);
                    lore.add(var10001 + "Postęp: " + String.valueOf(ChatColor.GREEN) + progress + "%");
                } else if (!a.isInGame() && status.equalsIgnoreCase("Badanie terenu")) {
                    var10001 = String.valueOf(ChatColor.YELLOW);
                    lore.add(var10001 + "Status: " + String.valueOf(ChatColor.AQUA) + "Badanie terenu");
                } else if (a.isInGame()) {
                    var10001 = String.valueOf(ChatColor.YELLOW);
                    lore.add(var10001 + "Status: " + String.valueOf(ChatColor.AQUA) + "Gra w toku");
                } else {
                    var10001 = String.valueOf(ChatColor.YELLOW);
                    lore.add(var10001 + "Status: " + String.valueOf(ChatColor.AQUA) + "Gotowa");
                }

                meta.setLore(lore);
                item.setItemMeta(meta);
                inv.setItem(arenaSlots[i], item);
                ++i;
            }

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
            Inventory inv = Bukkit.createInventory((InventoryHolder)null, 27, var10002 + " - " + arena.getName());
            inv.setItem(0, this.createGuiItem(Material.OAK_SIGN, String.valueOf(ChatColor.GREEN) + "Ustaw lobby"));
            inv.setItem(1, this.createGuiItem(Material.GRAY_WOOL, String.valueOf(ChatColor.AQUA) + "Dodaj drużynę"));
            inv.setItem(2, this.createGuiItem(Material.BLAZE_ROD, String.valueOf(ChatColor.GOLD) + "\ud83e\ude84 Ustaw teren regeneracji"));
            inv.setItem(16, this.createGuiItem(Material.EMERALD_BLOCK, String.valueOf(ChatColor.GREEN) + "\ud83d\udcbe Zapisz teren regeneracji"));
            int slot = 9;

            for(Team t : arena.getTeams()) {
                if (slot > 15) {
                    break;
                }

                Material var10003 = t.getWoolMaterial() != null ? t.getWoolMaterial() : Material.WHITE_WOOL;
                String var10004 = String.valueOf(t.getColor());
                inv.setItem(slot, this.createGuiItem(var10003, var10004 + t.getId()));
                ++slot;
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
                            Arena a = (Arena)this.arenas.get(arenaName.toLowerCase());
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
                                case 3:
                                case 4:
                                case 7:
                                case 9:
                                case 10:
                                case 11:
                                case 12:
                                case 13:
                                case 14:
                                case 15:
                                case 17:
                                case 20:
                                default:
                                    if (slot >= 9 && slot <= 15) {
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
                                    Inventory protectInv = Bukkit.createInventory((InventoryHolder)null, 27, "Bloki chronione - " + a.getName());

                                    for(Material m : a.getProtectedBlocks()) {
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
                                    }
                            }
                        } else if (title.equals(this.GUI_MAIN)) {
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
                        } else if (title.equals(this.TEAM_GUI_TITLE)) {
                            ev.setCancelled(true);
                            ItemStack it = ev.getCurrentItem();
                            if (it == null || it.getType().isAir()) {
                                return;
                            }

                            String name = ChatColor.stripColor(it.getItemMeta().getDisplayName());
                            Arena arena = (Arena)this.playerArena.get(p.getUniqueId());
                            if (arena == null) {
                                return;
                            }

                            Team selected = null;

                            for(Team t : arena.getTeams()) {
                                if (t.getId().equalsIgnoreCase(name) || ChatColor.stripColor(it.getItemMeta().getDisplayName()).equalsIgnoreCase(t.getId())) {
                                    selected = t;
                                    break;
                                }
                            }

                            if (selected != null) {
                                this.playerTeam.put(p.getUniqueId(), selected);
                                p.closeInventory();
                                String var56 = String.valueOf(ChatColor.GREEN);
                                p.sendMessage(var56 + "Wybrano drużynę: " + String.valueOf(selected.getColor()) + selected.getId());
                                List<Location> spawns = selected.getSpawns();
                                if (!spawns.isEmpty()) {
                                    p.teleport((Location)spawns.get((new Random()).nextInt(spawns.size())));
                                }

                                this.giveLeaveItem(p);
                            }
                        } else if (title.startsWith(String.valueOf(ChatColor.YELLOW) + "Edytuj drużynę ")) {
                            ev.setCancelled(true);
                            String teamId = title.substring((String.valueOf(ChatColor.YELLOW) + "Edytuj drużynę ").length());
                            String arenaName = p.hasMetadata("editingArena") ? ((MetadataValue)p.getMetadata("editingArena").get(0)).asString() : null;
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
                            String arenaName = p.hasMetadata("editingArena") ? ((MetadataValue)p.getMetadata("editingArena").get(0)).asString() : null;
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

                            team.setWoolMaterial(clicked.getType());
                            team.setColor(ChatColor.valueOf(ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).toUpperCase()));
                            String var61 = String.valueOf(ChatColor.GREEN);
                            p.sendMessage(var61 + "Zmieniono kolor drużyny na " + String.valueOf(team.getColor()) + team.getId());
                            this.openTeamEditGUI(p, arena, team);
                        }

                    }
                }
            }
        }

        public void openTeamColorGUI(Player p, Arena arena, Team team) {
            String var10002 = String.valueOf(ChatColor.LIGHT_PURPLE);
            Inventory inv = Bukkit.createInventory((InventoryHolder)null, 27, var10002 + "Kolor drużyny: " + team.getId());
            Material[] wools = new Material[]{Material.RED_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL, Material.YELLOW_WOOL, Material.ORANGE_WOOL, Material.PINK_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.WHITE_WOOL, Material.BLACK_WOOL};
            int i = 0;

            for(Material m : wools) {
                int var10001 = i++;
                String var10004 = String.valueOf(ChatColor.WHITE);
                inv.setItem(var10001, this.createGuiItem(m, var10004 + m.name().replace("_WOOL", "")));
            }

            inv.setItem(26, this.createGuiItem(Material.BARRIER, String.valueOf(ChatColor.RED) + "Powrót"));
            p.openInventory(inv);
            p.setMetadata("editingArena", new FixedMetadataValue(this.plugin, arena.getName()));
            p.setMetadata("editingTeam", new FixedMetadataValue(this.plugin, team.getId()));
        }

        public void openTeamEditGUI(Player player, Arena arena, Team team) {
            String var10002 = String.valueOf(ChatColor.YELLOW);
            Inventory inv = Bukkit.createInventory((InventoryHolder)null, 27, var10002 + "Edytuj drużynę " + team.getId());
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
            Player p = (Player)e.getPlayer();
            String title = e.getView().getTitle();
            if (title.startsWith("Bloki chronione - ")) {
                String arenaName = title.substring("Bloki chronione - ".length());
                Arena a = (Arena)this.arenas.get(arenaName.toLowerCase());
                if (a == null) {
                    return;
                }

                a.getProtectedBlocks().clear();

                for(ItemStack item : e.getInventory().getContents()) {
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
            this.playerTeam.remove(player.getUniqueId());
            this.playerArena.put(player.getUniqueId(), arena);
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
                this.playerArena.put(player.getUniqueId(), arena);
                arena.getPlayersInArena().add(player.getUniqueId());
                player.teleport(arena.getLobby() != null ? arena.getLobby() : player.getWorld().getSpawnLocation());
                player.getInventory().clear();
                player.setGameMode(GameMode.ADVENTURE);
                this.openTeamSelector(player, arena);
                this.updateLobbyScoreboard(player, arena, arena.getCountdown());
                this.startLobbyCountdownIfNeeded(arena);
                String var10001 = String.valueOf(ChatColor.GREEN);
                player.sendMessage(var10001 + "Dołączyłeś do areny: " + arena.getName());
                this.giveLeaveItem(player);
            }
        }

        public void openTeamSelector(Player p, Arena arena) {
            int teams = Math.max(1, arena.teamCount());
            int invSize = 9;
            Inventory inv = Bukkit.createInventory((InventoryHolder)null, invSize, this.TEAM_GUI_TITLE);
            List<Team> tlist = new ArrayList(arena.getTeams());

            for(int i = 0; i < tlist.size() && i < invSize; ++i) {
                Team t = (Team)tlist.get(i);
                Material mat = t.getWoolMaterial() != null ? t.getWoolMaterial() : Material.WHITE_WOOL;
                inv.setItem(i, this.createGuiItem(mat, t.getId()));
            }

            this.playerArena.put(p.getUniqueId(), arena);
            p.openInventory(inv);
        }

        public void giveLeaveItem(Player p) {
            ItemStack leave = new ItemStack(Material.BARRIER);
            ItemMeta meta = leave.getItemMeta();
            meta.setDisplayName(String.valueOf(ChatColor.RED) + "Wyjście z gry");
            leave.setItemMeta(meta);
            p.getInventory().setItem(8, leave);
        }

        @EventHandler
        public void onPlayerUseLeaveItem(PlayerInteractEvent ev) {
            Player p = ev.getPlayer();
            ItemStack item = ev.getItem();
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                if (item.getType() == Material.BARRIER && ChatColor.stripColor(item.getItemMeta().getDisplayName()).equalsIgnoreCase("Wyjście z gry") && (ev.getAction() == Action.RIGHT_CLICK_AIR || ev.getAction() == Action.RIGHT_CLICK_BLOCK)) {
                    Arena arena = (Arena)this.playerArena.get(p.getUniqueId());
                    if (arena == null) {
                        p.sendMessage(String.valueOf(ChatColor.RED) + "Nie jesteś w żadnej arenie!");
                        return;
                    }

                    arena.getPlayersInArena().remove(p.getUniqueId());
                    arena.getEliminated().add(p.getUniqueId());
                    this.playerArena.remove(p.getUniqueId());
                    this.playerTeam.remove(p.getUniqueId());
                    if (arena.getPlayersInArena().isEmpty()) {
                        arena.setInGame(false);
                        if (arena.getGameTask() != null) {
                            arena.getGameTask().cancel();
                        }
                    }

                    Location lobby = this.getGlobalLobby();
                    if (lobby == null && arena.getServerLobby() != null) {
                        lobby = arena.getServerLobby();
                    }

                    if (lobby != null) {
                        p.teleport(lobby);
                    }

                    p.setGameMode(GameMode.ADVENTURE);
                    p.getInventory().clear();
                    p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                    this.resetPlayerTeamColor(p);
                    p.sendMessage(String.valueOf(ChatColor.YELLOW) + "Wyszedłeś z gry.");
                    ev.setCancelled(true);
                }

            }
        }

        public void startLobbyCountdownIfNeeded(final Arena arena) {
            if (!arena.isCountingDown() && !arena.isInGame()) {
                if (arena.getPlayersInArena().size() >= arena.getMinPlayers()) {
                    arena.setCountingDown(true);
                    arena.setCountdown(15);
                    BukkitRunnable t = new BukkitRunnable() {
                        int count = arena.getCountdown();

                        public void run() {
                            if (arena.getPlayersInArena().size() < arena.getMinPlayers()) {
                                arena.setCountingDown(false);
                                this.cancel();
                            } else {
                                for (UUID uuid : new HashSet<UUID>(arena.getPlayersInArena())) {
                                    Player pl = Bukkit.getPlayer(uuid);
                                    if (pl != null && pl.isOnline()) {
                                        ArenaManager.this.updateLobbyScoreboard(pl, arena, this.count);
                                        if (this.count <= 5 && this.count > 0) {
                                            String var10001 = String.valueOf(ChatColor.AQUA);
                                            pl.sendMessage(var10001 + "Start za: " + this.count + "s");
                                        }
                                    }
                                }

                                if (this.count == 0) {
                                    ArenaManager.this.startGame(arena);
                                    this.cancel();
                                } else {
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

        public void startGame(final Arena arena) {
            arena.setCountingDown(false);
            arena.setInGame(true);

            for(Team t : arena.getTeams()) {
                if (t.getGeneratorLocation() != null) {
                    this.plugin.getGeneratorDruzyny().setGenerator(t.getId(), t.getGeneratorLocation());
                }
            }

            Random rnd = new Random();

            for(UUID uuid : new HashSet<>(arena.getPlayersInArena())) {
                Player pl = Bukkit.getPlayer(uuid);
                if (pl != null && pl.isOnline() && !this.playerTeam.containsKey(uuid)) {
                    this.assignRandomTeam(arena, pl);
                }
            }

            for(UUID uuid : new HashSet<>(arena.getPlayersInArena())) {
                Player pl = Bukkit.getPlayer(uuid);
                if (pl != null && pl.isOnline()) {
                    pl.setGameMode(GameMode.SURVIVAL);
                    Team t = (Team)this.playerTeam.get(uuid);
                    if (t != null) {
                        List<Location> spawns = t.getSpawns();
                        if (!spawns.isEmpty()) {
                            pl.teleport((Location)spawns.get(rnd.nextInt(spawns.size())));
                        }
                    } else if (!arena.getPlayerSpawns().isEmpty()) {
                        pl.teleport((Location)arena.getPlayerSpawns().get(rnd.nextInt(arena.getPlayerSpawns().size())));
                    }

                    pl.getInventory().addItem(new ItemStack[]{new ItemStack(Material.WOODEN_SWORD)});
                    pl.getInventory().addItem(new ItemStack[]{new ItemStack(Material.APPLE, 3)});
                    pl.sendMessage(String.valueOf(ChatColor.GOLD) + "Gra rozpoczęta! Chroń swoje łóżko!");
                }
            }

            BukkitRunnable gameTask = new BukkitRunnable() {
                int t = arena.getGameTime();

                public void run() {
                    if (!arena.isInGame()) {
                        this.cancel();
                    } else {
                        arena.setPhaseTimeLeft(arena.getPhaseTimeLeft() - 1);
                        if (arena.getPhaseTimeLeft() <= 0) {
                            int nextPhase = arena.getCurrentPhase() + 1;
                            if (nextPhase <= 3) {
                                arena.setCurrentPhase(nextPhase);
                                arena.setPhaseTimeLeft(arena.getPhaseDuration());
                                String var10000 = String.valueOf(ChatColor.GOLD);
                                Bukkit.broadcastMessage(var10000 + "Faza " + nextPhase + " rozpoczęta! Generatory przyspieszyły!");
                            }
                        }

                        arena.getGeneratorMapy().updateGeneratorsWithPhase(arena.getCurrentPhase());

                        for(UUID uuid : new HashSet<>(arena.getPlayersInArena())) {
                            Player pl = Bukkit.getPlayer(uuid);
                            if (pl != null && pl.isOnline()) {
                                ArenaManager.this.updateInGameScoreboard(pl, arena, this.t);
                            }
                        }

                        if (ArenaManager.this.isGameOver(arena)) {
                            ArenaManager.this.endGameWithTeleport(arena, false);
                            this.cancel();
                        } else {
                            --this.t;
                        }
                    }
                }
            };
            arena.setGameTask(gameTask);
            gameTask.runTaskTimer(this.plugin, 0L, 20L);
        }

        private void assignRandomTeam(Arena arena, Player p) {
            List<Team> teams = new ArrayList(arena.getTeams());
            teams.removeIf((t) -> arena.countTeam(t, this.playerTeam) > 0);
            if (teams.isEmpty()) {
                teams = new ArrayList(arena.getTeams());
            }

            Team assigned = (Team)teams.get((new Random()).nextInt(teams.size()));
            this.playerTeam.put(p.getUniqueId(), assigned);
            p.getInventory().clear();
            String var10001 = String.valueOf(ChatColor.GREEN);
            p.sendMessage(var10001 + "Przydzielono drużynę: " + String.valueOf(assigned.getColor()) + assigned.getId());
            List<Location> spawns = assigned.getSpawns();
            if (!spawns.isEmpty()) {
                p.teleport((Location)spawns.get((new Random()).nextInt(spawns.size())));
            }

            var10001 = String.valueOf(assigned.getColor());
            p.setDisplayName(var10001 + p.getName());
            String tab = p.getDisplayName();
            if (tab.length() > 16) {
                tab = tab.substring(0, 16);
            }

            p.setPlayerListName(tab);
            Color teamColor = assigned.getLeatherColor();
            ItemStack helmet = this.createLeatherArmor(Material.LEATHER_HELMET, teamColor);
            ItemStack chest = this.createLeatherArmor(Material.LEATHER_CHESTPLATE, teamColor);
            ItemStack leggings = this.createLeatherArmor(Material.LEATHER_LEGGINGS, teamColor);
            ItemStack boots = this.createLeatherArmor(Material.LEATHER_BOOTS, teamColor);
            p.getInventory().setHelmet(helmet);
            p.getInventory().setChestplate(chest);
            p.getInventory().setLeggings(leggings);
            p.getInventory().setBoots(boots);
            this.plugin.getShop().playerArmorMap.put(p.getUniqueId(), new SklepDruzyn.PlayerArmor(helmet, chest, leggings, boots));
        }

        private ChatColor getTeamColor(Team t) {
            return t != null && t.getColor() != null ? t.getColor() : ChatColor.WHITE;
        }

        private boolean isGameOver(Arena arena) {
            Set<Team> aliveTeams = new HashSet<>();

            for(UUID u : arena.getPlayersInArena()) {
                if (!arena.getEliminated().contains(u)) {
                    Team t = (Team)this.playerTeam.get(u);
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
            Arena arena = (Arena)this.playerArena.get(p.getUniqueId());
            if (arena != null && arena.isInGame()) {
                Block block = e.getBlock();
                BlockData var6 = block.getBlockData();
                if (var6 instanceof Bed) {
                    Bed bed = (Bed)var6;
                    Team target = arena.getTeamByBed(block.getLocation());
                    if (target != null) {
                        Team breakerTeam = (Team)this.playerTeam.get(p.getUniqueId());
                        if (breakerTeam == target) {
                            e.setCancelled(true);
                            p.sendMessage(String.valueOf(ChatColor.RED) + "Nie możesz zniszczyć własnego łóżka!");
                        } else if (target.isBedDestroyed()) {
                            e.setCancelled(true);
                        } else {
                            e.setDropItems(false);
                            Block otherPart = bed.getPart() == Part.HEAD ? block.getRelative(bed.getFacing(), -1) : block.getRelative(bed.getFacing(), 1);
                            block.getWorld().spawnParticle(Particle.BLOCK_CRACK, block.getLocation().add((double)0.5F, (double)0.5F, (double)0.5F), 10, block.getBlockData());
                            otherPart.getWorld().spawnParticle(Particle.BLOCK_CRACK, otherPart.getLocation().add((double)0.5F, (double)0.5F, (double)0.5F), 10, otherPart.getBlockData());
                            block.setType(Material.AIR, false);
                            otherPart.setType(Material.AIR, false);
                            target.setBedDestroyed(true);
                            String var10000 = String.valueOf(ChatColor.RED);
                            Bukkit.broadcastMessage(var10000 + "Łóżko drużyny " + String.valueOf(this.getTeamColor(target)) + target.getId() + String.valueOf(ChatColor.YELLOW) + " zostało zniszczone przez " + String.valueOf(ChatColor.GOLD) + p.getName());
                        }
                    }
                }
            }
        }

        @EventHandler
        public void onPlayerDeath(PlayerDeathEvent ev) {
            Player victim = ev.getEntity();
            UUID uuid = victim.getUniqueId();
            Arena arena = (Arena)this.playerArena.get(uuid);
            if (arena != null) {
                ev.getDrops().clear();
                ev.setKeepInventory(false);
                Team team = (Team)this.playerTeam.get(uuid);
                String teamName = team != null ? String.valueOf(this.getTeamColor(team)) + team.getId() : "Nieznana drużyna";

                for(UUID u : arena.getPlayersInArena()) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null && p.isOnline()) {
                        String var10001 = String.valueOf(ChatColor.RED);
                        p.sendMessage(var10001 + victim.getName() + String.valueOf(ChatColor.YELLOW) + " (" + teamName + ") został zabity!");
                    }
                }

                Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                    if (victim.isOnline()) {
                        if (team != null && !team.isBedDestroyed()) {
                            victim.spigot().respawn();
                            victim.setGameMode(GameMode.SURVIVAL);
                            List<Location> spawns = team.getSpawns();
                            if (!spawns.isEmpty()) {
                                victim.teleport((Location)spawns.get((new Random()).nextInt(spawns.size())));
                            }

                            victim.sendMessage(String.valueOf(ChatColor.GREEN) + "Odradzasz się - Twoje łóżko stoi!");
                        } else {
                            victim.spigot().respawn();
                            victim.setGameMode(GameMode.SPECTATOR);
                            arena.getEliminated().add(uuid);
                            victim.sendMessage(String.valueOf(ChatColor.RED) + "Twoje łóżko zostało zniszczone - jesteś wyeliminowany.");
                        }

                        if (this.isGameOver(arena)) {
                            this.endGameWithTeleport(arena, false);
                        }

                    }
                }, 1L);
            }
        }

        @EventHandler
        public void onPlayerRespawn(PlayerRespawnEvent ev) {
            Player player = ev.getPlayer();
            Arena arena = (Arena)this.playerArena.get(player.getUniqueId());
            if (arena != null) {
                Team team = (Team)this.playerTeam.get(player.getUniqueId());
                if (team != null) {
                    List<Location> spawns = team.getSpawns();
                    if (!spawns.isEmpty()) {
                        ev.setRespawnLocation((Location)spawns.get((new Random()).nextInt(spawns.size())));
                    }

                    Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                        if (player.isOnline()) {
                            player.getInventory().clear();
                            SklepDruzyn.PlayerArmor armor = (SklepDruzyn.PlayerArmor)this.plugin.getShop().playerArmorMap.get(player.getUniqueId());
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
            LeatherArmorMeta meta = (LeatherArmorMeta)item.getItemMeta();
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

                Arena arena = (Arena)this.playerArena.get(victim.getUniqueId());
                if (arena != null) {
                    if (!arena.isInGame()) {
                        ev.setCancelled(true);
                    } else {
                        if (var8 != null) {
                            Team vTeam = (Team)this.playerTeam.get(victim.getUniqueId());
                            Team dTeam = (Team)this.playerTeam.get(var8.getUniqueId());
                            if (vTeam != null && dTeam != null && vTeam == dTeam) {
                                ev.setCancelled(true);
                                var8.sendMessage(String.valueOf(ChatColor.RED) + "Nie możesz atakować członka swojej drużyny!");
                            }
                        }

                    }
                }
            }
        }

        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent ev) {
            Player player = ev.getPlayer();
            UUID uuid = player.getUniqueId();
            Arena arena = (Arena)this.playerArena.get(uuid);
            if (arena != null) {
                arena.getPlayersInArena().remove(uuid);
                arena.getEliminated().add(uuid);
                this.playerArena.remove(uuid);
                this.playerTeam.remove(uuid);
                BukkitRunnable t = (BukkitRunnable)this.observerTasks.remove(uuid);
                if (t != null) {
                    t.cancel();
                }

                if (arena.getPlayersInArena().isEmpty()) {
                    arena.setInGame(false);
                    if (arena.getGameTask() != null) {
                        arena.getGameTask().cancel();
                    }

                    if (arena.getLobbyTask() != null) {
                        arena.getLobbyTask().cancel();
                    }
                }

            }
        }

        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent ev) {
            ev.getPlayer().setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }

        private void updateLobbyScoreboard(Player p, Arena arena, int secondsLeft) {
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = sb.registerNewObjective("lobbySB", "dummy", String.valueOf(ChatColor.GOLD) + "BedWars - Lobby");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            String var10001 = String.valueOf(ChatColor.YELLOW);
            obj.getScore(var10001 + "Mapa: " + String.valueOf(ChatColor.WHITE) + arena.getName()).setScore(4);
            var10001 = String.valueOf(ChatColor.AQUA);
            obj.getScore(var10001 + "Graczy: " + String.valueOf(ChatColor.WHITE) + arena.getPlayersInArena().size() + "/" + arena.getMaxPlayers()).setScore(3);
            var10001 = String.valueOf(ChatColor.GREEN);
            obj.getScore(var10001 + "Start za: " + String.valueOf(ChatColor.WHITE) + secondsLeft + "s").setScore(2);
            obj.getScore(" ").setScore(1);
            p.setScoreboard(sb);
        }

        private void updateInGameScoreboard(Player p, Arena arena, int secondsLeft) {
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = sb.registerNewObjective("gameSB", "dummy", String.valueOf(ChatColor.RED) + "BedWars - Gra");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            String var10001 = String.valueOf(ChatColor.YELLOW);
            obj.getScore(var10001 + "Mapa: " + String.valueOf(ChatColor.WHITE) + arena.getName()).setScore(10);
            var10001 = String.valueOf(ChatColor.AQUA);
            obj.getScore(var10001 + "Czas: " + String.valueOf(ChatColor.WHITE) + this.formatTime(secondsLeft)).setScore(9);
            long alive = arena.getPlayersInArena().stream().filter((u) -> !arena.getEliminated().contains(u)).count();
            var10001 = String.valueOf(ChatColor.GOLD);
            obj.getScore(var10001 + "Zywych: " + String.valueOf(ChatColor.WHITE) + alive).setScore(8);
            obj.getScore(" ").setScore(7);
            int line = 6;

            for(Team t : arena.getTeams()) {
                var10001 = String.valueOf(t.getColor());
                obj.getScore(var10001 + t.getId() + ": " + String.valueOf(ChatColor.WHITE) + arena.countTeam(t, this.playerTeam)).setScore(line);
                --line;
                if (line < 0) {
                    break;
                }
            }

            p.setScoreboard(sb);
        }

        private String formatTime(int seconds) {
            int m = seconds / 60;
            int s = seconds % 60;
            return String.format("%02d:%02d", m, s);
        }

        private void startObserverTask(final Player victim, final Arena arena) {
            BukkitRunnable task = new BukkitRunnable() {
                public void run() {
                    if (!victim.isOnline()) {
                        this.cancel();
                    } else {
                        for(UUID u : arena.getPlayersInArena()) {
                            if (!u.equals(victim.getUniqueId())) {
                                Player p = Bukkit.getPlayer(u);
                                if (p != null && p.isOnline()) {
                                    double dist = p.getLocation().distance(victim.getLocation());
                                    if (dist < (double)2.0F) {
                                        Vector diff = victim.getLocation().toVector().subtract(p.getLocation().toVector());
                                        if (diff.length() > (double)0.0F && Double.isFinite(diff.getX())) {
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
            Arena arena = (Arena)this.playerArena.get(p.getUniqueId());
            if (arena != null && arena.isInGame()) {
                this.playerPlacedBlocks.add(e.getBlock().getLocation());
            }
        }

        @EventHandler
        public void onBlockBreak(BlockBreakEvent e) {
            Player p = e.getPlayer();
            Arena arena = (Arena)this.playerArena.get(p.getUniqueId());
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

        public void endGameWithTeleport(Arena arena, boolean timeUp) {
            arena.setInGame(false);
            if (arena.getGameTask() != null) {
                arena.getGameTask().cancel();
            }

            if (arena.getLobbyTask() != null) {
                arena.getLobbyTask().cancel();
            }

            arena.getGeneratorMapy().resetTimers();
            Set<Team> aliveTeams = (Set)arena.getPlayersInArena().stream().filter((ux) -> !arena.getEliminated().contains(ux)).map((ux) -> (Team)this.playerTeam.get(ux)).filter(Objects::nonNull).collect(Collectors.toSet());
            Team winningTeam = aliveTeams.size() == 1 ? (Team)aliveTeams.iterator().next() : null;
            String winnerName = winningTeam != null ? String.valueOf(this.getTeamColor(winningTeam)) + winningTeam.getId() : "Brak zwycięzcy";
            Set<UUID> all = new HashSet<>();
            all.addAll(arena.getPlayersInArena());
            all.addAll(arena.getEliminated());
            List<Player> toTeleport = new ArrayList();
            this.playerPlacedBlocks.clear();

            for(UUID u : all) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && p.isOnline()) {
                    toTeleport.add(p);
                    p.setGameMode(GameMode.ADVENTURE);
                    p.getInventory().clear();
                    p.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
                    String var10001 = String.valueOf(ChatColor.GOLD);
                    p.sendMessage(var10001 + "Koniec gry! Zwycięzca: " + String.valueOf(ChatColor.GREEN) + winnerName);
                    this.resetPlayerTeamColor(p);
                }
            }

            this.removeItemsFromArena(arena);
            BedWarsPlugin plugin = BedWarsPlugin.getInstance();
            BedWarsPlugin.getInstance().getArenaManager().getGlobalLobby();

            for(Team t : arena.getTeams()) {
                Location loc = t.getGeneratorLocation();
                if (loc != null) {
                    loc.getWorld().getNearbyEntities(loc, (double)2.0F, (double)2.0F, (double)2.0F).forEach((e) -> {
                        if (e instanceof Item) {
                            e.remove();
                        }

                    });
                    plugin.getGeneratorDruzyny().removeGenerator(t.getId());
                }
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for(Player p : toTeleport) {
                    if (this.globalLobby != null) {
                        p.teleport(this.globalLobby);
                        p.sendMessage(String.valueOf(ChatColor.GREEN) + "Teleportowano do globalnego lobby!");
                        Bukkit.getLogger().info("[BedWars] Teleportowano " + p.getName() + " do globalnego lobby po zakończeniu gry.");
                    } else {
                        p.sendMessage(String.valueOf(ChatColor.RED) + "⚠️ Globalne lobby nie jest ustawione!");
                        Bukkit.getLogger().warning("[BedWars] ⚠️ Globalne lobby nie jest ustawione dla " + p.getName());
                    }

                    this.playerTeam.remove(p.getUniqueId());
                    this.playerArena.remove(p.getUniqueId());
                }

                for(UUID u : new HashSet<>(this.observerTasks.keySet())) {
                    BukkitRunnable t = (BukkitRunnable)this.observerTasks.remove(u);
                    if (t != null) {
                        t.cancel();
                    }
                }

                arena.getPlayersInArena().clear();
                arena.getEliminated().clear();
                arena.setMapNeedsRestart(true);
                MapResetManager mapManager = plugin.getMapResetManager();
                mapManager.setArenaStatus(arena.getName(), "Badanie terenu");
                mapManager.setArenaProgress(arena.getName(), 0);
                String var10000 = String.valueOf(ChatColor.YELLOW);
                Bukkit.broadcastMessage(var10000 + "\ud83d\udd0d Arena " + arena.getName() + " jest badana i przygotowywana do odbudowy...");
                this.resetArenaState(arena);
                World world = arena.getWorld();
                if (world != null) {
                    mapManager.restoreChangedBlocks(arena.getName());
                } else {
                    Bukkit.getLogger().warning("[BedWars] Nie udało się znaleźć świata dla areny: " + arena.getName());
                }

            }, 1L);
        }

        public void resetPlayerTeamColor(Player player) {
            player.setDisplayName(player.getName());
            String tab = player.getName();
            if (tab.length() > 16) {
                tab = tab.substring(0, 16);
            }

            player.setPlayerListName(tab);
        }

        public void openArenaMainGUI(Player p) {
        }
    }

    public static class Arena {
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
            return (Team)this.teams.get(id.toUpperCase());
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
                for(Team t : this.teams.values()) {
                    Location l = t.getBedLocation();
                    if (l != null && l.getWorld().equals(loc.getWorld())) {
                        Block lBlock = l.getBlock();
                        Bed bed = null;
                        BlockData var8 = lBlock.getBlockData();
                        if (var8 instanceof Bed) {
                            Bed b = (Bed)var8;
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
            return t == null ? 0 : (int)this.playersInArena.stream().filter((u) -> playerTeam.get(u) == t && !this.eliminated.contains(u)).count();
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
                return ((Location)this.playerSpawns.get(0)).getWorld();
            } else {
                for(Team t : this.teams.values()) {
                    if (!t.getSpawns().isEmpty() && t.getSpawns().get(0) != null) {
                        return ((Location)t.getSpawns().get(0)).getWorld();
                    }
                }

                for(Team t : this.teams.values()) {
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
            if (this.color == null) {
                return Color.WHITE;
            } else {
                Color var10000;
                switch (this.color) {
                    case RED -> var10000 = Color.RED;
                    case BLUE -> var10000 = Color.BLUE;
                    case GREEN -> var10000 = Color.GREEN;
                    case YELLOW -> var10000 = Color.YELLOW;
                    case AQUA -> var10000 = Color.AQUA;
                    case GOLD -> var10000 = Color.ORANGE;
                    case WHITE -> var10000 = Color.WHITE;
                    default -> var10000 = Color.WHITE;
                }

                return var10000;
            }
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
