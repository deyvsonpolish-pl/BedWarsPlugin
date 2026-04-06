package org.BedWars;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Ulepszona wersja sklepu:
 * - czytelny układ (ramka + kategorie + sekcja itemów + Quick Buy na dole)
 * - spójne tytuły GUI (ważne do obsługi klików)
 * - więcej itemów jak w prawdziwym BedWars
 * - refactor: mniej duplikacji, jedna logika kliknięć, mapy z definicjami itemów
 *
 * Wymaga: Spigot/Paper 1.16+ (używane stained glass pane kolory, NamespacedKey/PDC)
 */
public class SklepDruzyn implements Listener {

    private final BedWarsPlugin plugin;
    private final NamespacedKey shopKey;

    // anty-spam klików
    private final Map<UUID, Long> recentClicks = new HashMap<>();

    // villager -> arena/team
    private final Map<UUID, VillagerShopInfo> villagerTeams = new HashMap<>();

    // Quick Buy (slot -> key)
    private final Map<UUID, Map<Integer, String>> quickBuySlots = new HashMap<>();
    private final Map<UUID, String> pendingQuickBuy = new HashMap<>();
    private final Map<UUID, String> lastShopPage = new HashMap<>(); // np. "MAIN" albo "cat_blocks" itd.
    private SystemLobbyBWMenu lobbyMenu; // referencja do ustawień
    // upgrade’y (prosto)
    private final Map<UUID, Integer> playerSwordLevel = new HashMap<>();
    private final Map<UUID, Integer> playerPickaxeLevel = new HashMap<>();
    private final Map<UUID, Integer> playerAxeLevel = new HashMap<>();

    public final Map<UUID, PlayerArmor> playerArmorMap = new HashMap<>();

    // Quick Buy sloty (dolny pasek)
    private static final int[] QUICK_BUY_SLOTS = {45,46,47,48,49,50,51,52,53};

    // Layout
    private static final String TITLE_MAIN = ChatColor.DARK_GREEN + "Sklep";
    private static final String TITLE_CAT_PREFIX = ChatColor.DARK_GREEN + "Sklep: ";
    private static final int[] CATEGORY_SLOTS = {0,1,2,3,4,5,6,7,8};
    public void setLobbyMenu(SystemLobbyBWMenu lobbyMenu) {
        this.lobbyMenu = lobbyMenu;
    }
    // Kategorie (key + nazwa + ikonka)
    private enum Category {
        QUICK("cat", "Szybki zakup", Material.NETHER_STAR),
        BLOCKS("cat_blocks", "Bloki", Material.BRICKS),
        WEAPONS("cat_weapons", "Bronie", Material.IRON_SWORD),
        ARMOR("cat_armor", "Zbroje", Material.IRON_CHESTPLATE),
        TOOLS("cat_tools", "Narzędzia", Material.IRON_PICKAXE),
        RANGED("cat_bows", "Łuki", Material.BOW),
        UTILITY("cat_utility", "Użytkowe", Material.SLIME_BALL),
        EXPLOSIVES("cat_explosives", "Eksplozje", Material.TNT),
        POTIONS("cat_potions", "Mikstury", Material.BREWING_STAND);

        final String key;
        final String name;
        final Material icon;
        Category(String key, String name, Material icon) {
            this.key = key; this.name = name; this.icon = icon;
        }
        static Category fromKey(String k) {
            for (Category c : values()) if (c.key.equals(k)) return c;
            return QUICK;
        }
    }

    /**
     * Definicja itemu sklepu (proste pozycje, bez upgrade logiki)
     */
    private static class ShopEntry {
        final String key;
        final Material displayMat;
        final int amountToGive;
        final String name;
        final String desc;
        final Material currency;
        final int cost;

        ShopEntry(String key, Material displayMat, int amountToGive, String name, String desc, Material currency, int cost) {
            this.key = key;
            this.displayMat = displayMat;
            this.amountToGive = amountToGive;
            this.name = name;
            this.desc = desc;
            this.currency = currency;
            this.cost = cost;
        }
    }

    // Baza itemów (większość jak Hypixel-ish)
    private final Map<String, ShopEntry> entries = new HashMap<>();

    public SklepDruzyn(BedWarsPlugin plugin) {
        this.plugin = plugin;
        this.shopKey = new NamespacedKey(plugin, "shop_key");
        registerEntries();
    }

    private void registerEntries() {
        // ===== BLOKI =====
        entries.put("wool_16", new ShopEntry("wool_16", Material.WHITE_WOOL, 16, "Wełna x16", "Szybkie budowanie", Material.IRON_INGOT, 4));
        entries.put("stone_16", new ShopEntry("stone_16", Material.STONE, 16, "Kamień x16", "Twardszy blok", Material.IRON_INGOT, 12));
        entries.put("endstone_12", new ShopEntry("endstone_12", Material.END_STONE, 12, "End Stone x12", "Dobra osłona łóżka", Material.IRON_INGOT, 24));
        entries.put("planks_16", new ShopEntry("planks_16", Material.OAK_PLANKS, 16, "Drewno x16", "Solidny blok", Material.GOLD_INGOT, 4));
        entries.put("ladder_16", new ShopEntry("ladder_16", Material.LADDER, 16, "Drabiny x16", "Szybkie wejście", Material.IRON_INGOT, 4));
        entries.put("glass_4", new ShopEntry("glass_4", Material.GLASS, 4, "Szkło x4", "Przezroczyste (TNT-proof w BW bywa osobno)", Material.IRON_INGOT, 12));
        entries.put("obsidian_1", new ShopEntry("obsidian_1", Material.OBSIDIAN, 1, "Obsydian x1", "Mega twardy blok", Material.EMERALD, 4));

        // ===== RANGED =====
        entries.put("bow", new ShopEntry("bow", Material.BOW, 1, "Łuk", "Podstawowy łuk", Material.GOLD_INGOT, 12));
        entries.put("arrows_8", new ShopEntry("arrows_8", Material.ARROW, 8, "Strzały x8", "Amunicja", Material.GOLD_INGOT, 2));

        // ===== UTILITY =====
        entries.put("gapple_1", new ShopEntry("gapple_1", Material.GOLDEN_APPLE, 1, "Złote jabłko", "Szybki heal", Material.GOLD_INGOT, 3));
        entries.put("food_8", new ShopEntry("food_8", Material.COOKED_BEEF, 8, "Stek x8", "Jedzenie", Material.IRON_INGOT, 4));
        entries.put("fireball_1", new ShopEntry("fireball_1", Material.FIRE_CHARGE, 1, "Fireball", "Odrzut + wybuch", Material.IRON_INGOT, 40));
        entries.put("tnt_1", new ShopEntry("tnt_1", Material.TNT, 1, "TNT", "Klasyk", Material.GOLD_INGOT, 8));
        entries.put("enderpearl_1", new ShopEntry("enderpearl_1", Material.ENDER_PEARL, 1, "Ender Pearl", "Teleport", Material.EMERALD, 4));
        entries.put("water_1", new ShopEntry("water_1", Material.WATER_BUCKET, 1, "Wiadro wody", "Ratunek / obrona", Material.GOLD_INGOT, 6));
        entries.put("sponge_4", new ShopEntry("sponge_4", Material.SPONGE, 4, "Gąbki x4", "Na wodę", Material.GOLD_INGOT, 4));

        // ===== “SPECIAL” / WEAPONS (nie-upgrade) =====
        entries.put("stick_kb", new ShopEntry("stick_kb", Material.STICK, 1, "Kij odrzutowy", "Knockback I", Material.GOLD_INGOT, 6));
    }

    // ====== UI HELPERS ======

    private ItemStack pane(Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) { im.setDisplayName(" "); it.setItemMeta(im); }
        return it;
    }

    private ItemStack createBackItem() {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(ChatColor.YELLOW + "← Wróć");
            im.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, "back");
            im.setLore(List.of(ChatColor.GRAY + "Kliknij, aby wrócić do głównego sklepu"));
            it.setItemMeta(im);
        }
        return it;
    }

    private ItemStack createCategoryItem(Category cat, boolean active) {
        ItemStack it = new ItemStack(cat.icon);
        ItemMeta im = it.getItemMeta();
        if (im == null) return it;

        im.setDisplayName((active ? ChatColor.GREEN : ChatColor.YELLOW) + cat.name);
        im.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, cat.key);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Kliknij, aby otworzyć");
        if (active) lore.add(ChatColor.GREEN + "✔ Aktualna kategoria");
        im.setLore(lore);

        if (active) {
            im.addEnchant(Enchantment.DURABILITY, 1, true);
            im.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        it.setItemMeta(im);
        return it;
    }

    private ItemStack createShopItem(Player player, ShopEntry e, Material overrideDisplayMat, String overrideName) {
        Material mat = (overrideDisplayMat != null ? overrideDisplayMat : e.displayMat);
        String name = (overrideName != null ? overrideName : e.name);

        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im == null) return it;

        im.setDisplayName(ChatColor.GREEN + name);
        List<String> lore = new ArrayList<>();

        if (e.desc != null && !e.desc.isEmpty()) lore.add(ChatColor.GRAY + e.desc);

        lore.add("");
        lore.add(ChatColor.YELLOW + "Cena: " + ChatColor.WHITE + e.cost + " " + formatCurrency(e.currency));
        lore.add(ChatColor.DARK_GRAY + "PPM - kup");

        // QuickBuy hint (góra)
        if (!hasQuickBuy(player, e.key)) {
            lore.add("");
            lore.add(ChatColor.GRAY + "SHIFT + PPM");
            lore.add(ChatColor.GREEN + "Ustaw szybki zakup");
        }

        im.setLore(lore);
        im.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, e.key);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack createQuickBuyEmptySlot() {
        ItemStack red = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta rm = red.getItemMeta();
        if (rm != null) {
            rm.setDisplayName(ChatColor.RED + "Szybki zakup");
            rm.setLore(List.of(
                    ChatColor.GRAY + "SHIFT + PPM na item",
                    ChatColor.GRAY + "aby przypisać",
                    "",
                    ChatColor.DARK_GRAY + "SHIFT + PPM na slocie",
                    ChatColor.DARK_GRAY + "usuwa przypisanie"
            ));
            rm.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, "quickbuy_slot");
            red.setItemMeta(rm);
        }
        return red;
    }

    private ItemStack createItemFromKeyForQuickBuy(Player player, String key) {
        // Uwaga: QuickBuy pokazuje też instrukcję usuwania
        ItemStack it = createDisplayItemByKey(player, key);
        if (it == null) return null;

        ItemMeta im = it.getItemMeta();
        if (im != null) {
            List<String> lore = im.hasLore() ? new ArrayList<>(im.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "PPM - kup");
            lore.add(ChatColor.RED + "SHIFT + PPM - usuń");
            im.setLore(lore);
            it.setItemMeta(im);
        }
        return it;
    }

    private ItemStack createDisplayItemByKey(Player player, String key) {
        BedWarsPlugin.Team team = plugin.getArenaManager().getPlayerTeam(player);
        if (team == null) return null;

        // wełna w kolorze teamu
        if ("wool_16".equals(key)) {
            ShopEntry e = entries.get(key);
            if (e == null) return null;
            return createShopItem(player, e, team.getWoolMaterial(), e.name);
        }

        // upgrade items -> wyświetl “następny poziom”
        if ("sword_upg".equals(key)) return createSwordUpgradeDisplay(player);
        if ("pickaxe_upg".equals(key)) return createPickaxeUpgradeDisplay(player);
        if ("axe_upg".equals(key)) return createAxeUpgradeDisplay(player);
// --- ARMOR (display do QuickBuy) ---
        if ("armor_chain".equals(key)) {
            return createArmorShopItem(player, Material.CHAINMAIL_LEGGINGS, "Zbroja: Kolczuga", Material.IRON_INGOT, 8, "armor_chain");
        }
        if ("armor_iron".equals(key)) {
            return createArmorShopItem(player, Material.IRON_LEGGINGS, "Zbroja: Żelazo", Material.GOLD_INGOT, 12, "armor_iron");
        }
        if ("armor_diamond".equals(key)) {
            return createArmorShopItem(player, Material.DIAMOND_LEGGINGS, "Zbroja: Diament", Material.EMERALD, 6, "armor_diamond");
        }

// --- TOOLS (display do QuickBuy) ---
        if ("shears_1".equals(key)) {
            return createSimpleToolItem(player, Material.SHEARS, "Nożyce", "Szybkie cięcie wełny", Material.IRON_INGOT, 20, "shears_1");
        }
        ShopEntry e = entries.get(key);
        if (e == null) return null;
        return createShopItem(player, e, null, null);
    }

    // ====== GUI OPEN ======

    public void openItemShopGUI(Player player, BedWarsPlugin.Team team) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MAIN);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
        lastShopPage.put(player.getUniqueId(), "MAIN");
        // ramka
        fillFrame(inv, team);

        // kategorie
        placeCategories(inv, Category.QUICK);

        // “Szybki sklep” (trochę top)
        inv.setItem(10, createDisplayItemByKey(player, "wool_16"));
        inv.setItem(11, createSwordUpgradeDisplay(player));
        inv.setItem(12, createPickaxeUpgradeDisplay(player));
        inv.setItem(13, createDisplayItemByKey(player, "gapple_1"));
        inv.setItem(14, createDisplayItemByKey(player, "fireball_1"));
        inv.setItem(15, createDisplayItemByKey(player, "tnt_1"));
        inv.setItem(16, createDisplayItemByKey(player, "enderpearl_1"));
        inv.setItem(19, createDisplayItemByKey(player, "bow"));
        inv.setItem(20, createDisplayItemByKey(player, "arrows_8"));
        inv.setItem(21, createDisplayItemByKey(player, "obsidian_1"));

        // Quick Buy dolny pasek
        placeQuickBuyBar(player, inv);

        startGlassAnimation(player, inv);
        player.openInventory(inv);
    }

    private void openCategoryGUI(Player player, Category cat) {
        BedWarsPlugin.Team team = plugin.getArenaManager().getPlayerTeam(player);
        if (team == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_CAT_PREFIX + ChatColor.GREEN + cat.name);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);

        fillFrame(inv, team);
        placeCategories(inv, cat);
        lastShopPage.put(player.getUniqueId(), cat.key); // albo categoryKey
        // back
        inv.setItem(9, createBackItem());

        // itemy w siatce (10..43)
        switch (cat) {
            case BLOCKS -> {
                set(inv, 10, createDisplayItemByKey(player, "wool_16"));
                set(inv, 11, createDisplayItemByKey(player, "stone_16"));
                set(inv, 12, createDisplayItemByKey(player, "endstone_12"));
                set(inv, 13, createDisplayItemByKey(player, "planks_16"));
                set(inv, 14, createDisplayItemByKey(player, "ladder_16"));
                set(inv, 15, createDisplayItemByKey(player, "glass_4"));
                set(inv, 16, createDisplayItemByKey(player, "obsidian_1"));
            }
            case WEAPONS -> {
                set(inv, 10, createSwordUpgradeDisplay(player));
                set(inv, 11, createDisplayItemByKey(player, "stick_kb"));
                set(inv, 12, createDisplayItemByKey(player, "bow"));
                set(inv, 13, createDisplayItemByKey(player, "arrows_8"));
            }
            case ARMOR -> {
                set(inv, 10, createArmorShopItem(player, Material.CHAINMAIL_LEGGINGS, "Zbroja: Kolczuga", Material.IRON_INGOT, 8, "armor_chain"));
                set(inv, 11, createArmorShopItem(player, Material.IRON_LEGGINGS, "Zbroja: Żelazo", Material.GOLD_INGOT, 12, "armor_iron"));
                set(inv, 12, createArmorShopItem(player, Material.DIAMOND_LEGGINGS, "Zbroja: Diament", Material.EMERALD, 6, "armor_diamond"));
            }
            case TOOLS -> {
                set(inv, 10, createPickaxeUpgradeDisplay(player));
                set(inv, 11, createAxeUpgradeDisplay(player));
                set(inv, 12, createSimpleToolItem(player, Material.SHEARS, "Nożyce", "Szybkie cięcie wełny", Material.IRON_INGOT, 20, "shears_1"));
            }
            case RANGED -> {
                set(inv, 10, createDisplayItemByKey(player, "bow"));
                set(inv, 11, createDisplayItemByKey(player, "arrows_8"));
            }
            case UTILITY -> {
                set(inv, 10, createDisplayItemByKey(player, "gapple_1"));
                set(inv, 11, createDisplayItemByKey(player, "food_8"));
                set(inv, 12, createDisplayItemByKey(player, "fireball_1"));
                set(inv, 13, createDisplayItemByKey(player, "water_1"));
                set(inv, 14, createDisplayItemByKey(player, "sponge_4"));
                set(inv, 15, createDisplayItemByKey(player, "enderpearl_1"));
            }
            case EXPLOSIVES -> {
                set(inv, 10, createDisplayItemByKey(player, "tnt_1"));
                set(inv, 11, createDisplayItemByKey(player, "fireball_1"));
            }
            case POTIONS -> {
                // możesz tu później dodać potki (PotionMeta) – zostawiam miejsce
                ItemStack info = new ItemStack(Material.POTION);
                ItemMeta im = info.getItemMeta();
                if (im != null) {
                    im.setDisplayName(ChatColor.AQUA + "Mikstury (wkrótce)");
                    im.setLore(List.of(ChatColor.GRAY + " "));
                    info.setItemMeta(im);
                }
                set(inv, 13, info);
            }
            default -> { /* QUICK nie używa tego GUI */ }
        }

        placeQuickBuyBar(player, inv);
        startGlassAnimation(player, inv);
        player.openInventory(inv);
    }
    private boolean shouldCloseOnBuy(Player p) {
        if (lobbyMenu == null) return true; // domyślnie TAK
        SystemLobbyBWMenu.LobbySettings s = lobbyMenu.getSettings(p.getUniqueId());
        return s.shopCloseOnBuy;
    }

    private boolean shouldOpenLastPage(Player p) {
        if (lobbyMenu == null) return true; // domyślnie TAK
        SystemLobbyBWMenu.LobbySettings s = lobbyMenu.getSettings(p.getUniqueId());
        return s.shopOpenLastPage;
    }
    private void set(Inventory inv, int slot, ItemStack it) {
        if (it != null) inv.setItem(slot, it);
    }

    private void fillFrame(Inventory inv, BedWarsPlugin.Team team) {
        // tło
        ItemStack fill = pane(Material.GRAY_STAINED_GLASS_PANE);

        // wszystko wypełnij
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, fill);

        // górny pasek pod kategorie (0-8 zostanie nadpisane kategoriami)
        // pasek pod kategorie (9-17) zostaje szary, ale 9 robimy czasem back.
        // środek (18-44) zostaje “pusty” wizualnie – ale i tak wypełniony, my nadpisujemy itemami.

        // dolny pasek (quickbuy) nie ruszamy tutaj (ustawia go placeQuickBuyBar)

        // oddech: wyczyść środek pod itemy (10..43) na ciemniejsze szkło, żeby itemy się wyróżniały
        ItemStack mid = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 10; i <= 43; i++) inv.setItem(i, mid);
    }

    private void placeCategories(Inventory inv, Category active) {
        int idx = 0;
        for (Category c : Category.values()) {
            if (idx >= CATEGORY_SLOTS.length) break;
            inv.setItem(CATEGORY_SLOTS[idx], createCategoryItem(c, c == active));
            idx++;
        }
        // jeśli kiedyś dodasz więcej kategorii niż 9, można zrobić stronicowanie
    }

    private void placeQuickBuyBar(Player player, Inventory inv) {
        Map<Integer, String> qb = quickBuySlots.getOrDefault(player.getUniqueId(), new HashMap<>());
        for (int slot : QUICK_BUY_SLOTS) {
            if (qb.containsKey(slot)) {
                ItemStack it = createItemFromKeyForQuickBuy(player, qb.get(slot));
                inv.setItem(slot, it != null ? it : createQuickBuyEmptySlot());
            } else {
                inv.setItem(slot, createQuickBuyEmptySlot());
            }
        }
    }

    // ====== ANIMACJA SZKŁA (delikatna, nie dotyka quickbuy) ======

    private Material[] getAnimatedGlassForTeam(BedWarsPlugin.Team team) {
        if (team == null || team.getColor() == null) {
            return new Material[]{Material.GRAY_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS_PANE};
        }
        return switch (team.getColor()) {
            case RED -> new Material[]{Material.RED_STAINED_GLASS_PANE, Material.PINK_STAINED_GLASS_PANE};
            case BLUE -> new Material[]{Material.BLUE_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE};
            case GREEN -> new Material[]{Material.GREEN_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE};
            case YELLOW -> new Material[]{Material.YELLOW_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE};
            case AQUA -> new Material[]{Material.CYAN_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE};
            case GOLD -> new Material[]{Material.ORANGE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE};
            case WHITE -> new Material[]{Material.WHITE_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS_PANE};
            default -> new Material[]{Material.GRAY_STAINED_GLASS_PANE, Material.LIGHT_GRAY_STAINED_GLASS_PANE};
        };
    }

    private boolean canAnimate(int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        for (int qb : QUICK_BUY_SLOTS) if (slot == qb) return false;
        // animujemy tylko SZARE tła (żeby nie migały itemy)
        Material m = item.getType();
        return m == Material.GRAY_STAINED_GLASS_PANE || m == Material.LIGHT_GRAY_STAINED_GLASS_PANE;
    }

    private void startGlassAnimation(Player player, Inventory inv) {
        BedWarsPlugin.Team team = plugin.getArenaManager().getPlayerTeam(player);
        Material[] frames = getAnimatedGlassForTeam(team);

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                InventoryView view = player.getOpenInventory();
                if (view == null || view.getTopInventory() != inv) { cancel(); return; }

                Material cur = frames[tick % frames.length];
                ItemStack framePane = pane(cur);

                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack it = inv.getItem(i);
                    if (canAnimate(i, it)) inv.setItem(i, framePane);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    // ====== SHOP LOGIC ======

    private boolean hasQuickBuy(Player player, String key) {
        Map<Integer, String> map = quickBuySlots.get(player.getUniqueId());
        return map != null && map.containsValue(key);
    }

    private boolean isBuyableKey(String key) {
        if (key == null) return false;
        if (key.startsWith("cat")) return false;
        return !Set.of("back", "quickbuy_slot").contains(key);
    }

    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && it.getType() == material) count += it.getAmount();
        }
        return count;
    }

    private boolean hasEnough(Player player, Material mat, int amount) {
        return player.getInventory().containsAtLeast(new ItemStack(mat), amount);
    }

    private void removeItems(Player player, Material mat, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() != mat) continue;

            if (it.getAmount() > remaining) {
                it.setAmount(it.getAmount() - remaining);
                remaining = 0;
                break;
            } else {
                remaining -= it.getAmount();
                contents[i] = null;
                if (remaining <= 0) break;
            }
        }
        player.getInventory().setContents(contents);
    }

    private String formatCurrency(Material mat) {
        return switch (mat) {
            case IRON_INGOT -> "żelaza";
            case GOLD_INGOT -> "złota";
            case DIAMOND -> "diamentów";
            case EMERALD -> "szmaragdów";
            default -> mat.name().toLowerCase();
        };
    }

    private boolean handlePurchase(Player player, ItemStack itemToGive, Material currency, int cost) {
        int amount = countItems(player, currency);
        if (amount < cost) {
            player.sendMessage(ChatColor.RED + "Nie masz wystarczająco " + formatCurrency(currency) + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        removeItems(player, currency, cost);
        player.getInventory().addItem(itemToGive);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
        return true;
    }

    // ====== UPGRADE: SWORD ======

    private int getCurrentSwordLevel(Player player) {
        List<Material> swords = List.of(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD);
        int best = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it == null) continue;
            int idx = swords.indexOf(it.getType());
            if (idx > best) best = idx;
        }
        return Math.max(best, playerSwordLevel.getOrDefault(player.getUniqueId(), 0));
    }

    private static class UpgradeStep {
        final Material nextMat;
        final Material currency;
        final int cost;
        UpgradeStep(Material nextMat, Material currency, int cost) { this.nextMat = nextMat; this.currency = currency; this.cost = cost; }
    }

    private UpgradeStep nextSword(Player player) {
        int lvl = getCurrentSwordLevel(player);
        return switch (lvl) {
            case 0 -> new UpgradeStep(Material.STONE_SWORD, Material.IRON_INGOT, 8);
            case 1 -> new UpgradeStep(Material.IRON_SWORD, Material.GOLD_INGOT, 6);
            case 2 -> new UpgradeStep(Material.DIAMOND_SWORD, Material.EMERALD, 3);
            default -> null;
        };
    }

    private ItemStack createSwordUpgradeDisplay(Player player) {
        UpgradeStep step = nextSword(player);
        ItemStack it;
        ItemMeta im;

        if (step == null) {
            it = new ItemStack(Material.DIAMOND_SWORD);
            im = it.getItemMeta();
            if (im != null) {
                im.setDisplayName(ChatColor.GREEN + "Miecz (MAX)");
                im.setLore(List.of(ChatColor.GRAY + "Masz już najlepszy miecz"));
                im.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, "sword_upg");
                it.setItemMeta(im);
            }
            return it;
        }

        it = new ItemStack(step.nextMat);
        im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(ChatColor.GREEN + "Ulepsz miecz → " + prettyMat(step.nextMat));
            im.setLore(List.of(
                    ChatColor.GRAY + "Lepszy miecz do walki",
                    "",
                    ChatColor.YELLOW + "Cena: " + ChatColor.WHITE + step.cost + " " + formatCurrency(step.currency),
                    ChatColor.DARK_GRAY + "PPM - kup",
                    "",
                    ChatColor.GRAY + "SHIFT + PPM",
                    ChatColor.GREEN + "Ustaw szybki zakup"
            ));
            im.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, "sword_upg");
            it.setItemMeta(im);
        }
        return it;
    }

    private boolean handleSwordUpgrade(Player player) {
        UpgradeStep step = nextSword(player);
        if (step == null) {
            player.sendMessage(ChatColor.RED + "Masz już najlepszy miecz!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        if (!hasEnough(player, step.currency, step.cost)) {
            player.sendMessage(ChatColor.RED + "Nie masz wystarczająco " + formatCurrency(step.currency) + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }

        removeItems(player, step.currency, step.cost);

        // usuń gorsze miecze z inv (żeby nie duplikować)
        List<Material> swords = List.of(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD);
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null) continue;
            if (swords.contains(it.getType()) && swords.indexOf(it.getType()) < swords.indexOf(step.nextMat)) {
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);

        player.getInventory().addItem(new ItemStack(step.nextMat));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
        playerSwordLevel.put(player.getUniqueId(), swords.indexOf(step.nextMat));
        return true;
    }

    // ====== UPGRADE: PICKAXE ======

    private UpgradeStep nextPickaxe(Player player) {
        int lvl = playerPickaxeLevel.getOrDefault(player.getUniqueId(), 0); // 0=wood,1=stone,2=iron,3=diamond
        return switch (lvl) {
            case 0 -> new UpgradeStep(Material.STONE_PICKAXE, Material.IRON_INGOT, 10);
            case 1 -> new UpgradeStep(Material.IRON_PICKAXE, Material.GOLD_INGOT, 3);
            case 2 -> new UpgradeStep(Material.DIAMOND_PICKAXE, Material.EMERALD, 2);
            default -> null;
        };
    }

    private ItemStack createPickaxeUpgradeDisplay(Player player) {
        UpgradeStep step = nextPickaxe(player);
        if (step == null) {
            ItemStack it = new ItemStack(Material.DIAMOND_PICKAXE);
            ItemMeta im = it.getItemMeta();
            if (im != null) {
                im.setDisplayName(ChatColor.GREEN + "Kilof (MAX)");
                im.setLore(List.of(ChatColor.GRAY + "Masz już najlepszy kilof"));
                im.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, "pickaxe_upg");
                it.setItemMeta(im);
            }
            return it;
        }
        ItemStack it = new ItemStack(step.nextMat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(ChatColor.GREEN + "Ulepsz kilof → " + prettyMat(step.nextMat));
            im.setLore(List.of(
                    ChatColor.GRAY + "Szybciej kopiesz bloki",
                    "",
                    ChatColor.YELLOW + "Cena: " + ChatColor.WHITE + step.cost + " " + formatCurrency(step.currency),
                    ChatColor.DARK_GRAY + "PPM - kup",
                    "",
                    ChatColor.GRAY + "SHIFT + PPM",
                    ChatColor.GREEN + "Ustaw szybki zakup"
            ));
            im.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, "pickaxe_upg");
            it.setItemMeta(im);
        }
        return it;
    }

    private boolean handlePickaxeUpgrade(Player player) {
        UpgradeStep step = nextPickaxe(player);
        if (step == null) {
            player.sendMessage(ChatColor.RED + "Masz już najlepszy kilof!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        if (!hasEnough(player, step.currency, step.cost)) {
            player.sendMessage(ChatColor.RED + "Nie masz wystarczająco " + formatCurrency(step.currency) + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        removeItems(player, step.currency, step.cost);

        // usuń stare kilofy
        List<Material> picks = List.of(Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE);
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null) continue;
            if (picks.contains(it.getType()) && picks.indexOf(it.getType()) < picks.indexOf(step.nextMat)) {
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);

        player.getInventory().addItem(new ItemStack(step.nextMat));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);

        int newLvl = picks.indexOf(step.nextMat);
        playerPickaxeLevel.put(player.getUniqueId(), newLvl);
        return true;
    }

    // ====== UPGRADE: AXE ======

    private UpgradeStep nextAxe(Player player) {
        int lvl = playerAxeLevel.getOrDefault(player.getUniqueId(), 0); // 0=wood,1=stone,2=iron,3=diamond
        return switch (lvl) {
            case 0 -> new UpgradeStep(Material.STONE_AXE, Material.IRON_INGOT, 10);
            case 1 -> new UpgradeStep(Material.IRON_AXE, Material.GOLD_INGOT, 3);
            case 2 -> new UpgradeStep(Material.DIAMOND_AXE, Material.EMERALD, 2);
            default -> null;
        };
    }

    private ItemStack createAxeUpgradeDisplay(Player player) {
        UpgradeStep step = nextAxe(player);
        if (step == null) {
            ItemStack it = new ItemStack(Material.DIAMOND_AXE);
            ItemMeta im = it.getItemMeta();
            if (im != null) {
                im.setDisplayName(ChatColor.GREEN + "Siekiera (MAX)");
                im.setLore(List.of(ChatColor.GRAY + "Masz już najlepszą siekierę"));
                im.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, "axe_upg");
                it.setItemMeta(im);
            }
            return it;
        }
        ItemStack it = new ItemStack(step.nextMat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(ChatColor.GREEN + "Ulepsz siekierę → " + prettyMat(step.nextMat));
            im.setLore(List.of(
                    ChatColor.GRAY + "Szybciej niszczysz drewno",
                    "",
                    ChatColor.YELLOW + "Cena: " + ChatColor.WHITE + step.cost + " " + formatCurrency(step.currency),
                    ChatColor.DARK_GRAY + "PPM - kup",
                    "",
                    ChatColor.GRAY + "SHIFT + PPM",
                    ChatColor.GREEN + "Ustaw szybki zakup"
            ));
            im.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, "axe_upg");
            it.setItemMeta(im);
        }
        return it;
    }

    private boolean handleAxeUpgrade(Player player) {
        UpgradeStep step = nextAxe(player);
        if (step == null) {
            player.sendMessage(ChatColor.RED + "Masz już najlepszą siekierę!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        if (!hasEnough(player, step.currency, step.cost)) {
            player.sendMessage(ChatColor.RED + "Nie masz wystarczająco " + formatCurrency(step.currency) + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        removeItems(player, step.currency, step.cost);

        List<Material> axes = List.of(Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.DIAMOND_AXE);
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null) continue;
            if (axes.contains(it.getType()) && axes.indexOf(it.getType()) < axes.indexOf(step.nextMat)) {
                contents[i] = null;
            }
        }
        player.getInventory().setContents(contents);

        player.getInventory().addItem(new ItemStack(step.nextMat));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);

        int newLvl = axes.indexOf(step.nextMat);
        playerAxeLevel.put(player.getUniqueId(), newLvl);
        return true;
    }

    // ====== ARMOR ======

    private ItemStack createArmorShopItem(Player player, Material mat, String name, Material currency, int cost, String key) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(ChatColor.GREEN + name);
            im.setLore(List.of(
                    ChatColor.GRAY + "Ulepsza zbroję na stałe",
                    "",
                    ChatColor.YELLOW + "Cena: " + ChatColor.WHITE + cost + " " + formatCurrency(currency),
                    ChatColor.DARK_GRAY + "PPM - kup",
                    "",
                    ChatColor.GRAY + "SHIFT + PPM",
                    ChatColor.GREEN + "Ustaw szybki zakup"
            ));
            im.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, key);
            it.setItemMeta(im);
        }
        return it;
    }

    private boolean handleArmorPurchase(Player player, Material leggingsMat, Material bootsMat, Material currency, int cost) {
        ItemStack currentLeggings = player.getInventory().getLeggings();
        Material currentType = (currentLeggings != null ? currentLeggings.getType() : Material.LEATHER_LEGGINGS);

        List<Material> levels = List.of(Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS, Material.IRON_LEGGINGS, Material.DIAMOND_LEGGINGS);
        int currentLevel = levels.indexOf(currentType);
        int newLevel = levels.indexOf(leggingsMat);

        if (newLevel <= currentLevel) {
            player.sendMessage(ChatColor.RED + "Masz już taką lub lepszą zbroję!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        if (!hasEnough(player, currency, cost)) {
            player.sendMessage(ChatColor.RED + "Nie masz wystarczająco " + formatCurrency(currency) + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }

        removeItems(player, currency, cost);

        BedWarsPlugin.Team team = plugin.getArenaManager().getPlayerTeam(player);
        Color teamColor = (team != null ? team.getLeatherColor() : Color.WHITE);

        ItemStack helmet = createLeatherArmor(Material.LEATHER_HELMET, teamColor);
        ItemStack chest = createLeatherArmor(Material.LEATHER_CHESTPLATE, teamColor);

        ItemStack leggings = new ItemStack(leggingsMat);
        ItemStack boots = new ItemStack(bootsMat);

        // lekki enchant, żeby było “premium”
        leggings.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 1);
        boots.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 1);

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chest);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);

        playerArmorMap.put(player.getUniqueId(), new PlayerArmor(helmet, chest, leggings, boots));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        return true;
    }

    private ItemStack createLeatherArmor(Material mat, Color color) {
        ItemStack it = new ItemStack(mat);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        if (meta != null) {
            meta.setColor(color);
            it.setItemMeta(meta);
        }
        return it;
    }

    // ====== TOOLS SIMPLE ======

    private ItemStack createSimpleToolItem(Player player, Material mat, String name, String desc, Material currency, int cost, String key) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        if (im != null) {
            im.setDisplayName(ChatColor.GREEN + name);
            im.setLore(List.of(
                    ChatColor.GRAY + desc,
                    "",
                    ChatColor.YELLOW + "Cena: " + ChatColor.WHITE + cost + " " + formatCurrency(currency),
                    ChatColor.DARK_GRAY + "PPM - kup",
                    "",
                    ChatColor.GRAY + "SHIFT + PPM",
                    ChatColor.GREEN + "Ustaw szybki zakup"
            ));
            im.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, key);
            it.setItemMeta(im);
        }
        return it;
    }

    // ====== CLICK HANDLING ======

    @EventHandler
    public void onInventoryClick(InventoryClickEvent ev) {
        if (!(ev.getWhoClicked() instanceof Player player)) return;
        if (ev.getView() == null) return;

        String title = ev.getView().getTitle();
        Inventory top = ev.getView().getTopInventory();
        ItemStack clicked = ev.getCurrentItem();

        boolean isOurGUI = title != null && (title.equals(TITLE_MAIN) || title.startsWith(TITLE_CAT_PREFIX));
        if (!isOurGUI) return;

        // blokuj przenoszenie w górnym inv
        if (ev.getRawSlot() < top.getSize()) ev.setCancelled(true);

        if (clicked == null || clicked.getType() == Material.AIR) return;

        // anty-spam
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = recentClicks.get(uuid);
        if (last != null && now - last < 200L) return;
        recentClicks.put(uuid, now);
        Bukkit.getScheduler().runTaskLater(plugin, () -> recentClicks.remove(uuid), 6L);

        int slot = ev.getRawSlot();
        boolean isShift = ev.getClick().isShiftClick();
        boolean isRight = (ev.getClick() == ClickType.RIGHT || ev.getClick() == ClickType.SHIFT_RIGHT);

        ItemMeta meta = clicked.getItemMeta();
        String key = (meta != null) ? meta.getPersistentDataContainer().get(shopKey, PersistentDataType.STRING) : null;
        if (key == null) return;

        // 1) Kategorie
        if (key.startsWith("cat")) {
            Category cat = Category.fromKey(key);
            if (cat == Category.QUICK) {
                BedWarsPlugin.Team t = plugin.getArenaManager().getPlayerTeam(player);
                if (t != null) openItemShopGUI(player, t);
            } else {
                openCategoryGUI(player, cat);
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            return;
        }

        // 2) Back
        if ("back".equals(key)) {
            BedWarsPlugin.Team t = plugin.getArenaManager().getPlayerTeam(player);
            if (t != null) openItemShopGUI(player, t);
            return;
        }

        // 3) SHIFT + PPM na item (poza paskiem Quick Buy) -> ustaw pending quickbuy
        if (!isQuickBuySlot(slot) && isShift && isRight && isBuyableKey(key)) {
            pendingQuickBuy.put(uuid, key);
            player.sendMessage(ChatColor.GREEN + "✅ Kliknij teraz slot na dole, aby przypisać do Quick Buy!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            return;
        }

        // 4) Klik w pasek Quick Buy (45-53)
        if (isQuickBuySlot(slot)) {
            // SHIFT+PPM usuwa przypisanie
            if (isShift && isRight) {
                Map<Integer, String> map = quickBuySlots.get(uuid);
                if (map != null && map.containsKey(slot)) {
                    map.remove(slot);
                    player.sendMessage(ChatColor.YELLOW + "❌ Usunięto z Quick Buy.");
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
                    reopenSameShop(player);
                }
                return;
            }

            // jeżeli mamy pending -> przypisz do tego slota
            String pending = pendingQuickBuy.get(uuid);
            if (pending != null) {
                quickBuySlots.computeIfAbsent(uuid, k -> new HashMap<>()).put(slot, pending);
                pendingQuickBuy.remove(uuid);
                player.sendMessage(ChatColor.GREEN + "✅ Przypisano do Quick Buy!");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
                reopenSameShop(player);
                return;
            }

            // zakup z quickbuy
            String qbKey = quickBuySlots.getOrDefault(uuid, Collections.emptyMap()).get(slot);
            if (qbKey == null) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                return;
            }

            boolean bought = handleBuyByKey(player, qbKey);
            if (bought && shouldCloseOnBuy(player)) {
                player.closeInventory();
            } else if (bought) {
                reopenSameShop(player);
            }
            return;
        }

        // 5) Normalny zakup (klik w itemy w środku)
        // (klik w tło / quickbuy_slot ignorujemy)
        if (isBuyableKey(key)) {
            boolean bought = handleBuyByKey(player, key);
            if (bought && shouldCloseOnBuy(player)) {
                player.closeInventory();
            } else if (bought) {
                reopenSameShop(player);
            }
        }
    }
    public void clearQuickBuyIfNeeded(Player p) {
        if (lobbyMenu == null) return;

        SystemLobbyBWMenu.LobbySettings s = lobbyMenu.getSettings(p.getUniqueId());
        if (s.quickBuyPersist) return; // zapamiętaj zawsze -> nic nie robimy

        quickBuySlots.remove(p.getUniqueId());
        pendingQuickBuy.remove(p.getUniqueId());
    }
    private boolean handleBuyByKey(Player player, String key) {
        // upgrade items
        switch (key) {
            case "sword_upg" -> { return handleSwordUpgrade(player); }
            case "pickaxe_upg" -> { return handlePickaxeUpgrade(player); }
            case "axe_upg" -> { return handleAxeUpgrade(player); }

            case "armor_chain" -> { return handleArmorPurchase(player, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS, Material.IRON_INGOT, 8); }
            case "armor_iron" -> { return handleArmorPurchase(player, Material.IRON_LEGGINGS, Material.IRON_BOOTS, Material.GOLD_INGOT, 12); }
            case "armor_diamond" -> { return handleArmorPurchase(player, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS, Material.EMERALD, 6); }

            case "shears_1" -> {
                // nożyce: zwykły zakup
                return handlePurchase(player, new ItemStack(Material.SHEARS, 1), Material.IRON_INGOT, 20);
            }
            case "stick_kb" -> {
                if (!hasEnough(player, Material.GOLD_INGOT, 6)) {
                    player.sendMessage(ChatColor.RED + "Nie masz wystarczająco złota!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return false;
                }
                removeItems(player, Material.GOLD_INGOT, 6);
                ItemStack stick = new ItemStack(Material.STICK, 1);
                stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
                ItemMeta im = stick.getItemMeta();
                if (im != null) {
                    im.setDisplayName(ChatColor.GREEN + "Kij odrzutowy");
                    stick.setItemMeta(im);
                }
                player.getInventory().addItem(stick);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
                return true;
            }
        }

        // standard entries
        ShopEntry e = entries.get(key);
        if (e == null) {
            player.sendMessage(ChatColor.RED + "Nieobsługiwany item: " + key);
            return false;
        }

        // wełna w kolorze teamu
        if ("wool_16".equals(key)) {
            BedWarsPlugin.Team t = plugin.getArenaManager().getPlayerTeam(player);
            if (t == null) return false;
            return handlePurchase(player, new ItemStack(t.getWoolMaterial(), e.amountToGive), e.currency, e.cost);
        }

        return handlePurchase(player, new ItemStack(e.displayMat, e.amountToGive), e.currency, e.cost);
    }

    private void reopenSameShop(Player player) {
        // odczytaj gdzie jest gracz i wróć do tego samego GUI (main/cat)
        InventoryView view = player.getOpenInventory();
        if (view == null) return;
        String title = view.getTitle();
        if (title == null) return;

        if (title.equals(TITLE_MAIN)) {
            BedWarsPlugin.Team t = plugin.getArenaManager().getPlayerTeam(player);
            if (t != null) openItemShopGUI(player, t);
            return;
        }
        if (title.startsWith(TITLE_CAT_PREFIX)) {
            // spróbuj dopasować po nazwie (bezpiecznie)
            String stripped = ChatColor.stripColor(title.substring(TITLE_CAT_PREFIX.length()));
            for (Category c : Category.values()) {
                if (c.name.equalsIgnoreCase(stripped)) {
                    openCategoryGUI(player, c);
                    return;
                }
            }
            BedWarsPlugin.Team t = plugin.getArenaManager().getPlayerTeam(player);
            if (t != null) openItemShopGUI(player, t);
        }
    }

    private boolean isQuickBuySlot(int slot) {
        for (int s : QUICK_BUY_SLOTS) if (s == slot) return true;
        return false;
    }

    private String prettyMat(Material m) {
        // proste “ładniejsze” nazwy
        return switch (m) {
            case STONE_SWORD -> "Stone Sword";
            case IRON_SWORD -> "Iron Sword";
            case DIAMOND_SWORD -> "Diamond Sword";
            case STONE_PICKAXE -> "Stone Pickaxe";
            case IRON_PICKAXE -> "Iron Pickaxe";
            case DIAMOND_PICKAXE -> "Diamond Pickaxe";
            case STONE_AXE -> "Stone Axe";
            case IRON_AXE -> "Iron Axe";
            case DIAMOND_AXE -> "Diamond Axe";
            default -> m.name();
        };
    }

    // ====== VILLAGER SHOP ======

    @EventHandler
    public void onVillagerClick(PlayerInteractEntityEvent ev) {
        Entity ent = ev.getRightClicked();
        if (!(ent instanceof Villager villager)) return;

        VillagerShopInfo info = villagerTeams.get(villager.getUniqueId());
        if (info == null) return;

        ev.setCancelled(true);

        Player player = ev.getPlayer();
        BedWarsPlugin.Arena playerArena = plugin.getArenaManager().getArenaForPlayer(player);
        if (playerArena == null || !playerArena.equals(info.arena)) {
            player.sendMessage(ChatColor.RED + "Ten sklep nie należy do twojej areny!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        BedWarsPlugin.Team playerTeam = plugin.getArenaManager().getPlayerTeam(player);
        if (playerTeam == null || !playerTeam.equals(info.team)) {
            player.sendMessage(ChatColor.RED + "To nie jest sklep twojej drużyny!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        String last = lastShopPage.get(player.getUniqueId());

        if (shouldOpenLastPage(player) && last != null && last.startsWith("cat_")) {
            openCategoryGUI(player, Category.fromKey(last)); // jeśli masz enum Category
        } else {
            openItemShopGUI(player, playerTeam);
        }
    }

    public void spawnTeamShop(BedWarsPlugin.Arena arena, BedWarsPlugin.Team team, Location location) {
        Villager villager = location.getWorld().spawn(location, Villager.class);
        villager.setCustomName(ChatColor.GREEN + "Sklep " + team.getId());
        villager.setInvulnerable(true);
        villager.setAI(false);
        villagerTeams.put(villager.getUniqueId(), new VillagerShopInfo(arena, team));
    }

    public void removeAllShops() {
        for (World w : Bukkit.getWorlds()) {
            for (Villager v : w.getEntitiesByClass(Villager.class)) {
                String name = ChatColor.stripColor(v.getCustomName() == null ? "" : v.getCustomName());
                if (name.startsWith("Sklep ")) v.remove();
            }
        }
        villagerTeams.clear();
        plugin.getLogger().info("🛑 Usunięto wszystkie villagery sklepowe.");
    }

    public void respawnShopsFromArenas() {
        removeAllShops();
        for (BedWarsPlugin.Arena arena : plugin.getArenaManager().getArenas()) {
            for (BedWarsPlugin.Team team : arena.getTeams()) {
                Location loc = arena.getTeamShop(team);
                if (loc != null) spawnTeamShop(arena, team, loc);
            }
        }
    }

    public void clear() {
        removeAllShops();
        playerArmorMap.clear();
        playerSwordLevel.clear();
        playerPickaxeLevel.clear();
        playerAxeLevel.clear();
        recentClicks.clear();
        pendingQuickBuy.clear();
        quickBuySlots.clear();
        plugin.getLogger().info("🧹 Sklep wyczyszczony.");
    }

    // ====== DATA CLASSES ======

    public static class PlayerArmor {
        private final ItemStack helmet, chestplate, leggings, boots;
        public PlayerArmor(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots) {
            this.helmet = helmet; this.chestplate = chestplate; this.leggings = leggings; this.boots = boots;
        }
        public ItemStack getHelmet() { return helmet; }
        public ItemStack getChestplate() { return chestplate; }
        public ItemStack getLeggings() { return leggings; }
        public ItemStack getBoots() { return boots; }
    }

    public static class VillagerShopInfo {
        public final BedWarsPlugin.Arena arena;
        public final BedWarsPlugin.Team team;
        public VillagerShopInfo(BedWarsPlugin.Arena arena, BedWarsPlugin.Team team) { this.arena = arena; this.team = team; }
    }
}