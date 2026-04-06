package org.BedWars;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SystemLobbyBWMenu implements Listener {

    private final JavaPlugin plugin;

    // ====== KONFIG ======
    private static final String HUB_WORLD = "BWHub"; // <- dopasuj
    private static final int MENU_SLOT = 4;

    private static final String MAIN_TITLE = "§e🧩 Interfejs";
    private static final int MAIN_SIZE = 27;

    private static final String SB_TITLE = "§b📊 Scoreboard";
    private static final int SB_SIZE = 27;

    private static final String NOTIF_TITLE = "§e🔔 Powiadomienia";
    private static final int NOTIF_SIZE = 27;

    // ====== PDC ======
    private final NamespacedKey KEY_LOBBY_ITEM;
    private final NamespacedKey KEY_BUTTON;
    private final NamespacedKey KEY_GUI_TYPE;

    // ====== PLIK ======
    private File file;
    private YamlConfiguration yml;

    private final Map<UUID, LobbySettings> cache = new ConcurrentHashMap<>();

    public SystemLobbyBWMenu(JavaPlugin plugin) {
        this.plugin = plugin;
        this.KEY_LOBBY_ITEM = new NamespacedKey(plugin, "bw_lobby_item");
        this.KEY_BUTTON = new NamespacedKey(plugin, "bw_menu_button");
        this.KEY_GUI_TYPE = new NamespacedKey(plugin, "bw_gui_type");
    }

    // =========================
    // ENABLE / DISABLE
    // =========================
    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadFile();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isInHub(p) && !isInArena(p)) giveMenuItem(p);
        }
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        saveAll();
        cache.clear();
    }

    public LobbySettings getSettings(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadPlayerSettings);
    }

    // =========================
    // JOIN / WORLD / RESPAWN
    // =========================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = e.getPlayer();
            if (isInHub(p) && !isInArena(p)) giveMenuItem(p);
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = e.getPlayer();
            if (isInHub(p) && !isInArena(p)) giveMenuItem(p);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = e.getPlayer();
            if (isInHub(p) && !isInArena(p)) giveMenuItem(p);
        }, 5L);
    }

    // =========================
    // BLOKADY ITEMU
    // =========================
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!isInHub(p) || isInArena(p)) return;
        if (isLobbyMenuItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!isInHub(p) || isInArena(p)) return;
        if (isLobbyMenuItem(e.getMainHandItem()) || isLobbyMenuItem(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!isInHub(p) || isInArena(p)) return;

        if (isLobbyMenuItem(e.getOldCursor())) {
            e.setCancelled(true);
            return;
        }

        if (isOurGui(e.getView())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        // blokada przenoszenia itemu w hubie
        if (isInHub(p) && !isInArena(p)) {
            ItemStack current = e.getCurrentItem();
            ItemStack cursor = e.getCursor();

            if (isLobbyMenuItem(current) || isLobbyMenuItem(cursor)) {
                ClickType ct = e.getClick();
                InventoryAction act = e.getAction();

                if (ct.isShiftClick()
                        || ct == ClickType.NUMBER_KEY
                        || ct == ClickType.SWAP_OFFHAND
                        || act == InventoryAction.MOVE_TO_OTHER_INVENTORY
                        || act == InventoryAction.HOTBAR_SWAP
                        || act == InventoryAction.HOTBAR_MOVE_AND_READD
                        || act == InventoryAction.COLLECT_TO_CURSOR
                        || act == InventoryAction.SWAP_WITH_CURSOR
                        || act == InventoryAction.PICKUP_ALL
                        || act == InventoryAction.PICKUP_HALF
                        || act == InventoryAction.PICKUP_ONE
                        || act == InventoryAction.PICKUP_SOME
                        || act == InventoryAction.PLACE_ALL
                        || act == InventoryAction.PLACE_ONE
                        || act == InventoryAction.PLACE_SOME
                ) {
                    e.setCancelled(true);
                }
            }
        }

        // klik w naszych GUI
        if (isOurGui(e.getView())) {
            e.setCancelled(true);

            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType() == Material.AIR) return;

            String action = getButtonAction(it);
            if (action == null || action.equalsIgnoreCase("none")) return;

            String guiType = getGuiType(e.getView().getTopInventory());
            if (guiType == null) return;

            if ("MAIN".equalsIgnoreCase(guiType)) {
                handleMainClick(p, action);

            } else if ("SB".equalsIgnoreCase(guiType)) {
                handleScoreboardClick(p, action, e.getClick());
            } else if ("NOTIF".equalsIgnoreCase(guiType)) {
                handleNotificationsClick(p, action);
            } else if ("SHOP".equalsIgnoreCase(guiType)) {
                handleShopSettingsClick(p, action);
            }
        }
    }
    private void handleShopSettingsClick(Player p, String action) {
        LobbySettings s = getSettings(p.getUniqueId());

        switch (action) {
            case "toggle_shop_close" -> {
                s.shopCloseOnBuy = !s.shopCloseOnBuy;
                savePlayer(p.getUniqueId(), p.getName(), s);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openBedWarsShopSettings(p);
            }
            case "toggle_shop_open" -> {
                s.shopOpenLastPage = !s.shopOpenLastPage;
                savePlayer(p.getUniqueId(), p.getName(), s);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openBedWarsShopSettings(p);
            }
            case "back_main" -> {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.1f);
                openMainMenu(p);
            }
            case "toggle_quickbuy_persist" -> {
                s.quickBuyPersist = !s.quickBuyPersist;
                savePlayer(p.getUniqueId(), p.getName(), s);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openBedWarsShopSettings(p);
            }
            case "close" -> p.closeInventory();
        }
    }
    // =========================
    // OTWIERANIE MENU: klik item (air/block)
    // =========================
    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isInHub(p) || isInArena(p)) return;

        if (e.getHand() != EquipmentSlot.HAND) return;

        ItemStack it = e.getItem();
        if (!isLobbyMenuItem(it)) return;

        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK
                || a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);
            openMainMenu(p);
        }
    }

    // klik w entity (npc) też otwiera menu
    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (!isInHub(p) || isInArena(p)) return;

        ItemStack it = p.getInventory().getItemInMainHand();
        if (!isLobbyMenuItem(it)) return;

        e.setCancelled(true);
        openMainMenu(p);
    }
    private static final String SHOP_TITLE = "§a🛒 BedWars: Sklep";
    private static final int SHOP_SIZE = 27;
    // =========================
    // GUI: MAIN
    // =========================
    private void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, MAIN_SIZE, MAIN_TITLE);

        // tło
        for (int i = 0; i < MAIN_SIZE; i++) {
            inv.setItem(i, glass(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }
        stampGuiType(inv, "MAIN");

        inv.setItem(13, icon(Material.NETHER_STAR,
                "§e🧩 Interfejs",
                Arrays.asList("§7Dostosuj HUD i menu", "§8(na razie: scoreboard + powiadomienia)"),
                "none"));

        inv.setItem(11, icon(Material.PAPER,
                "§b📊 Scoreboard",
                Arrays.asList("§7Kliknij aby otworzyć ustawienia", "§8Tryb • Strona • Animacje"),
                "open_scoreboard"));
        inv.setItem(14, icon(Material.EMERALD,
                "§a🛒 BedWars: Sklep",
                Arrays.asList("§7Ustaw zachowanie sklepu", "§8Zamykanie • Ostatnia strona"),
                "open_bedwars_shop_settings"));
        // NOWE: Powiadomienia (obok)
        inv.setItem(15, icon(Material.BELL,
                "§e🔔 Powiadomienia",
                Arrays.asList("§7Ustaw powiadomienia lobby", "§8Rangi • Ogólne"),
                "open_notifications"));

        inv.setItem(22, icon(Material.BARRIER,
                "§cZamknij",
                Collections.singletonList("§7Kliknij aby zamknąć"),
                "close"));

        p.openInventory(inv);
    }

    private void handleMainClick(Player p, String action) {
        switch (action) {
            case "open_scoreboard" -> {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openScoreboardMenu(p);
            }
            case "open_notifications" -> {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openNotificationsMenu(p);
            }
            case "open_bedwars_shop_settings" -> {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openBedWarsShopSettings(p);
            }
            case "close" -> p.closeInventory();
        }
    }

    // =========================
    // GUI: SCOREBOARD
    // =========================
    private void openScoreboardMenu(Player p) {
        LobbySettings s = getSettings(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, SB_SIZE, SB_TITLE);

        for (int i = 0; i < SB_SIZE; i++) {
            inv.setItem(i, glass(Material.BLACK_STAINED_GLASS_PANE, " ", null));
        }
        stampGuiType(inv, "SB");

        inv.setItem(13, icon(Material.PAPER,
                "§b📊 Ustawienia Scoreboardu",
                Arrays.asList("§7Zmiany zapisują się automatycznie", "§8Plik: lobby-settings.yml"),
                "none"));

        String modeName = (s.scoreboardMode == ScoreboardMode.DYNAMIC) ? "§aDynamiczny" : "§eStały";
        inv.setItem(11, icon(Material.COMPARATOR,
                "§fTryb: " + modeName,
                Arrays.asList("§7Kliknij aby przełączyć", "§8Dynamiczny = fazy", "§8Stały = jedna strona"),
                "toggle_mode"));

        String pageInfo = (s.scoreboardMode == ScoreboardMode.FIXED)
                ? "§fStrona: §b" + s.fixedPage
                : "§8Dostępne tylko w trybie Stały";

        inv.setItem(12, icon(Material.MAP,
                "§bWybór strony",
                Arrays.asList(pageInfo, "§7LPM: następna", "§7PPM: poprzednia"),
                "change_page"));

        String anim = s.animations ? "§aON" : "§cOFF";
        inv.setItem(15, icon(Material.FIREWORK_STAR,
                "§fAnimacje: " + anim,
                Arrays.asList("§7Animacja tytułu/paska", "§7Kliknij aby przełączyć"),
                "toggle_anim"));

        inv.setItem(21, icon(Material.ARROW,
                "§ePowrót",
                Collections.singletonList("§7Wróć do Interfejsu"),
                "back_main"));

        inv.setItem(22, icon(Material.BARRIER,
                "§cZamknij",
                Collections.singletonList("§7Kliknij aby zamknąć"),
                "close"));

        p.openInventory(inv);
    }

    private void handleScoreboardClick(Player p, String action, ClickType clickType) {
        LobbySettings s = getSettings(p.getUniqueId());

        switch (action) {
            case "toggle_mode" -> {
                s.scoreboardMode = (s.scoreboardMode == ScoreboardMode.DYNAMIC) ? ScoreboardMode.FIXED : ScoreboardMode.DYNAMIC;
                savePlayer(p.getUniqueId(), p.getName(), s);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openScoreboardMenu(p);
            }
            case "change_page" -> {
                if (s.scoreboardMode != ScoreboardMode.FIXED) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                    return;
                }

                if (clickType == ClickType.RIGHT) s.fixedPage--;
                else s.fixedPage++;

                if (s.fixedPage > 3) s.fixedPage = 1;
                if (s.fixedPage < 1) s.fixedPage = 3;

                savePlayer(p.getUniqueId(), p.getName(), s);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.1f);
                openScoreboardMenu(p);
            }
            case "toggle_anim" -> {
                s.animations = !s.animations;
                savePlayer(p.getUniqueId(), p.getName(), s);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.3f);
                openScoreboardMenu(p);
            }
            case "back_main" -> {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.1f);
                openMainMenu(p);
            }
            case "close" -> p.closeInventory();
        }
    }

    // =========================
    // GUI: POWIADOMIENIA
    // =========================
    private void openNotificationsMenu(Player p) {
        LobbySettings s = getSettings(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, NOTIF_SIZE, NOTIF_TITLE);

        for (int i = 0; i < NOTIF_SIZE; i++) {
            inv.setItem(i, glass(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", null));
        }
        stampGuiType(inv, "NOTIF");

        inv.setItem(13, icon(Material.BELL,
                "§e🔔 Powiadomienia",
                Arrays.asList(
                        "§7Ustaw co ma Ci się wyświetlać",
                        "§8Zmiany zapisują się automatycznie"
                ),
                "none"));

        // 1) Rangi
        String ranksState = s.notifyRanks ? "§aWŁĄCZONE" : "§cWYŁĄCZONE";
        inv.setItem(11, icon(Material.NAME_TAG,
                "§fRangi: " + ranksState,
                Arrays.asList(
                        "§7Włącz aby otrzymywać informacje",
                        "§7o nowej randze / awansie",
                        "§8Kliknij aby przełączyć"
                ),
                "toggle_notify_ranks"));

        // 2) Ogólne
        String globalState = s.notifyGlobal ? "§aWŁĄCZONE" : "§cWYŁĄCZONE";
        inv.setItem(15, icon(Material.PAPER,
                "§fOgólne: " + globalState,
                Arrays.asList(
                        "§7Włącz/wyłącz wszystkie powiadomienia",
                        "§7lobby dla Ciebie (globalnie)",
                        "§8Kliknij aby przełączyć"
                ),
                "toggle_notify_global"));

        inv.setItem(21, icon(Material.ARROW,
                "§ePowrót",
                Collections.singletonList("§7Wróć do Interfejsu"),
                "back_main"));

        inv.setItem(22, icon(Material.BARRIER,
                "§cZamknij",
                Collections.singletonList("§7Kliknij aby zamknąć"),
                "close"));

        p.openInventory(inv);
    }

    private void handleNotificationsClick(Player p, String action) {
        LobbySettings s = getSettings(p.getUniqueId());

        switch (action) {
            case "toggle_notify_ranks" -> {
                s.notifyRanks = !s.notifyRanks;
                savePlayer(p.getUniqueId(), p.getName(), s);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openNotificationsMenu(p);
            }
            case "toggle_notify_global" -> {
                s.notifyGlobal = !s.notifyGlobal;
                savePlayer(p.getUniqueId(), p.getName(), s);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
                openNotificationsMenu(p);
            }
            case "back_main" -> {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.1f);
                openMainMenu(p);
            }
            case "close" -> p.closeInventory();
        }
    }

    // =========================
    // NOTIFICATION HELPERS (opcjonalnie do użycia w pluginie)
    // =========================
    public void sendRankNotification(Player p, String message) {
        LobbySettings s = getSettings(p.getUniqueId());
        if (!s.notifyGlobal) return;
        if (!s.notifyRanks) return;
        p.sendMessage(message);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
    }

    public void sendGlobalNotification(Player p, String message) {
        LobbySettings s = getSettings(p.getUniqueId());
        if (!s.notifyGlobal) return;
        p.sendMessage(message);
    }

    // =========================
    // ITEM: DOSTARCZANIE
    // =========================
    private void giveMenuItem(Player p) {
        p.getInventory().setItem(MENU_SLOT, createLobbyMenuItem());
    }

    private ItemStack createLobbyMenuItem() {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e🧩 Interfejs");
            meta.setLore(Arrays.asList("§7Dostosuj HUD i menu", "§8Kliknij aby otworzyć"));
            meta.getPersistentDataContainer().set(KEY_LOBBY_ITEM, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(meta);
        }
        return it;
    }

    private boolean isLobbyMenuItem(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return false;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return false;
        Byte b = meta.getPersistentDataContainer().get(KEY_LOBBY_ITEM, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }

    // =========================
    // GUI HELPERS
    // =========================
    private ItemStack glass(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack icon(Material mat, String name, List<String> lore, String action) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            if (action != null) meta.getPersistentDataContainer().set(KEY_BUTTON, PersistentDataType.STRING, action);
            it.setItemMeta(meta);
        }
        return it;
    }

    private String getButtonAction(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(KEY_BUTTON, PersistentDataType.STRING);
    }

    private boolean isOurGui(InventoryView view) {
        String t = ChatColor.stripColor(view.getTitle());
        return t.equals(ChatColor.stripColor(MAIN_TITLE))
                || t.equals(ChatColor.stripColor(SB_TITLE))
                || t.equals(ChatColor.stripColor(NOTIF_TITLE))
                || t.equals(ChatColor.stripColor(SHOP_TITLE));
    }
    private void openBedWarsShopSettings(Player p) {
        LobbySettings s = getSettings(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, SHOP_SIZE, SHOP_TITLE);

        for (int i = 0; i < SHOP_SIZE; i++) {
            inv.setItem(i, glass(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }
        stampGuiType(inv, "SHOP");

        String closeState = s.shopCloseOnBuy ? "§aTAK" : "§cNIE";
        inv.setItem(11, icon(Material.BARRIER,
                "§fZamykać sklep po zakupie? " + closeState,
                Arrays.asList("§7Kliknij aby przełączyć", "§8TAK = jak Hypixel", "§8NIE = szybkie zakupy"),
                "toggle_shop_close"));
        String qbState = s.quickBuyPersist ? "§aZAPAMIĘTUJ ZAWSZE" : "§eRESETUJ CO GRĘ";
        inv.setItem(13, icon(Material.NETHER_STAR,
                "§fQuick Buy: " + qbState,
                Arrays.asList(
                        "§7Kliknij aby przełączyć",
                        "§8ZAPAMIĘTUJ = zostaje na zawsze",
                        "§8RESETUJ = czyści się po grze"
                ),
                "toggle_quickbuy_persist"));
        String openState = s.shopOpenLastPage ? "§aOSTATNIA" : "§eGŁÓWNA";
        inv.setItem(15, icon(Material.BOOK,
                "§fOtwieranie po NPC: " + openState,
                Arrays.asList("§7Kliknij aby przełączyć", "§8OSTATNIA = wracasz tam gdzie byłeś"),
                "toggle_shop_open"));

        inv.setItem(21, icon(Material.ARROW,
                "§ePowrót",
                Collections.singletonList("§7Wróć do Interfejsu"),
                "back_main"));

        inv.setItem(22, icon(Material.BARRIER,
                "§cZamknij",
                Collections.singletonList("§7Kliknij aby zamknąć"),
                "close"));

        p.openInventory(inv);
    }
    private void stampGuiType(Inventory inv, String type) {
        ItemStack marker = inv.getItem(0);
        if (marker == null || marker.getType() == Material.AIR) marker = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);

        ItemMeta meta = marker.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.getPersistentDataContainer().set(KEY_GUI_TYPE, PersistentDataType.STRING, type);
            marker.setItemMeta(meta);
        }
        inv.setItem(0, marker);
    }

    private String getGuiType(Inventory topInv) {
        ItemStack it = topInv.getItem(0);
        if (it == null) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(KEY_GUI_TYPE, PersistentDataType.STRING);
    }

    // =========================
    // YAML: LOAD/SAVE
    // =========================
    private void loadFile() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

        file = new File(plugin.getDataFolder(), "lobby-settings.yml");
        if (!file.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nie mogę utworzyć lobby-settings.yml: " + e.getMessage());
            }
        }

        yml = YamlConfiguration.loadConfiguration(file);
        if (!yml.isConfigurationSection("players")) {
            yml.createSection("players");
            saveFile();
        }
    }

    private LobbySettings loadPlayerSettings(UUID uuid) {
        LobbySettings s = new LobbySettings();
        if (yml == null) loadFile();

        String path = "players." + uuid;
        String mode = yml.getString(path + ".scoreboardMode", "DYNAMIC");

        try {
            s.scoreboardMode = ScoreboardMode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            s.scoreboardMode = ScoreboardMode.DYNAMIC;
        }

        s.fixedPage = yml.getInt(path + ".fixedPage", 1);
        if (s.fixedPage < 1) s.fixedPage = 1;
        if (s.fixedPage > 3) s.fixedPage = 3;

        s.animations = yml.getBoolean(path + ".animations", true);

        // NOWE: powiadomienia
        s.notifyRanks = yml.getBoolean(path + ".notifyRanks", true);
        s.notifyGlobal = yml.getBoolean(path + ".notifyGlobal", true);
        s.shopCloseOnBuy = yml.getBoolean(path + ".shopCloseOnBuy", true);
        s.shopOpenLastPage = yml.getBoolean(path + ".shopOpenLastPage", true);
        s.quickBuyPersist = yml.getBoolean(path + ".quickBuyPersist", true);
        return s;
    }

    private void savePlayer(UUID uuid, String lastName, LobbySettings s) {
        if (yml == null) loadFile();

        String path = "players." + uuid;
        yml.set(path + ".lastName", lastName == null ? "Unknown" : lastName);
        yml.set(path + ".scoreboardMode", s.scoreboardMode.name());
        yml.set(path + ".fixedPage", s.fixedPage);
        yml.set(path + ".animations", s.animations);

        // NOWE: powiadomienia
        yml.set(path + ".notifyRanks", s.notifyRanks);
        yml.set(path + ".notifyGlobal", s.notifyGlobal);
        yml.set(path + ".shopCloseOnBuy", s.shopCloseOnBuy);
        yml.set(path + ".shopOpenLastPage", s.shopOpenLastPage);
        yml.set(path + ".quickBuyPersist", s.quickBuyPersist);
        saveFile();
    }

    private void saveAll() {
        for (UUID u : cache.keySet()) {
            Player p = Bukkit.getPlayer(u);
            String name = (p != null) ? p.getName() : "Unknown";
            savePlayer(u, name, cache.get(u));
        }
    }

    private void saveFile() {
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie mogę zapisać lobby-settings.yml: " + e.getMessage());
        }
    }

    // =========================
    // HUB / ARENA
    // =========================
    private boolean isInHub(Player p) {
        return p.getWorld() != null && p.getWorld().getName().equalsIgnoreCase(HUB_WORLD);
    }

    // Podmień na swoją logikę
    private boolean isInArena(Player p) {
        if (plugin instanceof BedWarsPlugin bw) {
            return bw.getPlayerArena().containsKey(p.getUniqueId());
        }
        return false;
    }

    // =========================
    // MODELE
    // =========================
    public enum ScoreboardMode { DYNAMIC, FIXED }

    public static class LobbySettings {
        public ScoreboardMode scoreboardMode = ScoreboardMode.DYNAMIC;
        public int fixedPage = 1;
        public boolean animations = true;
        // QUICK BUY
        public boolean quickBuyPersist = true; // true = zapamiętaj zawsze, false = resetuj co grę
        // NOWE: powiadomienia
        public boolean notifyRanks = true;   // info o randze
        public boolean notifyGlobal = true;  // ogólne powiadomienia (master switch)
        // BEDWARS SHOP
        public boolean shopCloseOnBuy = true;      // czy GUI zamyka się po zakupie
        public boolean shopOpenLastPage = true;    // czy NPC otwiera ostatnią stronę (true) czy główną (false)
    }
}
