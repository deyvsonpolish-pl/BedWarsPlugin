//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.BedWars;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

public class SklepDruzyn implements Listener {
    private final Map<UUID, Long> recentClicks = new HashMap();
    private final BedWarsPlugin plugin;
    private final Map<UUID, VillagerShopInfo> villagerTeams = new HashMap();
    private final Map<UUID, Integer> playerSwordLevel = new HashMap();
    private final NamespacedKey shopKey;
    public final Map<UUID, PlayerArmor> playerArmorMap = new HashMap();

    public SklepDruzyn(BedWarsPlugin plugin) {
        this.plugin = plugin;
        this.shopKey = new NamespacedKey(plugin, "shop_key");
    }

    public void openShopMainGUI(Player player, BedWarsPlugin.Team team) {
        String var10002 = String.valueOf(ChatColor.GOLD);
        Inventory inv = Bukkit.createInventory((InventoryHolder)null, 27, var10002 + "Sklep drużyny - " + team.getId());
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        fMeta.setDisplayName(" ");
        filler.setItemMeta(fMeta);

        for(int i = 0; i < inv.getSize(); ++i) {
            inv.setItem(i, filler);
        }

        ItemStack wool = new ItemStack(team.getWoolMaterial(), 16);
        ItemMeta wMeta = wool.getItemMeta();
        wMeta.setDisplayName(String.valueOf(ChatColor.GREEN) + "Wełna (16x za 4 żelaza)");
        wool.setItemMeta(wMeta);
        inv.setItem(13, wool);
        ItemStack itemShop = new ItemStack(Material.NETHER_STAR);
        ItemMeta isMeta = itemShop.getItemMeta();
        isMeta.setDisplayName(String.valueOf(ChatColor.AQUA) + "Item Shop");
        isMeta.setLore(Arrays.asList(String.valueOf(ChatColor.GRAY) + "Otwórz pełny sklep z przedmiotami"));
        isMeta.getPersistentDataContainer().set(this.shopKey, PersistentDataType.STRING, "open_item_shop");
        itemShop.setItemMeta(isMeta);
        inv.setItem(11, itemShop);
        ItemStack armor = new ItemStack(Material.CHAINMAIL_CHESTPLATE);
        ItemMeta aMeta = armor.getItemMeta();
        aMeta.setDisplayName(String.valueOf(ChatColor.AQUA) + "Zbroje");
        armor.setItemMeta(aMeta);
        inv.setItem(15, armor);
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = close.getItemMeta();
        cMeta.setDisplayName(String.valueOf(ChatColor.RED) + "Zamknij sklep");
        close.setItemMeta(cMeta);
        inv.setItem(26, close);
        player.openInventory(inv);
    }

    private boolean handleSwordUpgrade(Player player) {
        BedWarsPlugin.Team team = this.plugin.getArenaManager().getPlayerTeam(player);
        if (team == null) {
            return false;
        } else {
            int currentLevel = this.getCurrentSwordLevel(player);
            List<Material> swords = Arrays.asList(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD);
            if (currentLevel >= swords.size() - 1) {
                player.sendMessage(String.valueOf(ChatColor.RED) + "Masz już najlepszy miecz!");
                return false;
            } else {
                int nextLevel = currentLevel + 1;
                Material nextSword = (Material)swords.get(nextLevel);
                Material var10000;
                switch (nextLevel) {
                    case 1 -> var10000 = Material.IRON_INGOT;
                    case 2 -> var10000 = Material.GOLD_INGOT;
                    case 3 -> var10000 = Material.DIAMOND;
                    default -> var10000 = Material.AIR;
                }

                Material currency = var10000;
                byte var12;
                switch (nextLevel) {
                    case 1 -> var12 = 6;
                    case 2 -> var12 = 8;
                    case 3 -> var12 = 4;
                    default -> var12 = 0;
                }

                int cost = var12;
                if (!this.hasEnough(player, currency, cost)) {
                    String var13 = String.valueOf(ChatColor.RED);
                    player.sendMessage(var13 + "Nie masz wystarczająco " + this.formatCurrency(currency) + "!");
                    return false;
                } else {
                    this.removeItems(player, currency, cost);
                    ItemStack[] contents = player.getInventory().getContents();

                    for(int i = 0; i < contents.length; ++i) {
                        ItemStack item = contents[i];
                        if (item != null && swords.contains(item.getType()) && swords.indexOf(item.getType()) == currentLevel) {
                            contents[i] = new ItemStack(nextSword);
                            break;
                        }
                    }

                    player.getInventory().setContents(contents);
                    String var10001 = String.valueOf(ChatColor.GREEN);
                    player.sendMessage(var10001 + "Kupiono: " + String.valueOf(ChatColor.YELLOW) + nextSword.name());
                    this.playerSwordLevel.put(player.getUniqueId(), nextLevel);
                    return true;
                }
            }
        }
    }

    public void openItemShopGUI(Player player, BedWarsPlugin.Team team) {
        Inventory inv = Bukkit.createInventory((InventoryHolder)null, 54, String.valueOf(ChatColor.DARK_GREEN) + "Item shop");
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        fMeta.setDisplayName(" ");
        filler.setItemMeta(fMeta);

        for(int i = 0; i < inv.getSize(); ++i) {
            inv.setItem(i, filler);
        }

        inv.setItem(0, this.createShopItem(Material.NETHER_STAR, "Kategorie", "Główne kategorie sklepu", (Material)null, 0, "cat"));
        inv.setItem(1, this.createShopItem(Material.LIGHT_BLUE_WOOL, "Bloki", "Wełna, bloki itp.", (Material)null, 0, "cat_blocks"));
        inv.setItem(2, this.createShopItem(Material.GOLDEN_SWORD, "Bronie", "Miecze, łuki", (Material)null, 0, "cat_weapons"));
        inv.setItem(3, this.createShopItem(Material.IRON_BOOTS, "Zbroje", "Ulepszenia zbroi", (Material)null, 0, "cat_armor"));
        inv.setItem(4, this.createShopItem(Material.BOW, "Łuki & strzały", "Łuki, strzały", (Material)null, 0, "cat_bows"));
        inv.setItem(5, this.createShopItem(Material.TNT, "Wybuchem", "TNT i pułapki", (Material)null, 0, "cat_explosives"));
        inv.setItem(8, this.createMenuItem(Material.ARROW, String.valueOf(ChatColor.RED) + "← Wróć", String.valueOf(ChatColor.GRAY) + "Powrót do menu głównego"));
        inv.setItem(9, this.createShopItem(Material.LIGHT_BLUE_WOOL, "Wełna 16x", "16x wełny", Material.IRON_INGOT, 4, "wool_16"));
        SwordUpgrade upgrade = this.getNextSword(player);
        if (upgrade != null) {
            inv.setItem(10, this.createShopItem(upgrade.nextSword, upgrade.nextSword.name(), "Ulepsz swój miecz", upgrade.currency, upgrade.cost, "sword_wood"));
        } else {
            inv.setItem(10, this.createShopItem(Material.DIAMOND_SWORD, "Najlepszy miecz", "Masz już najlepszy miecz!", (Material)null, 0, "sword_wood"));
        }

        inv.setItem(11, this.createShopItem(Material.IRON_PICKAXE, "Kilof (żelazo)", "Przydatny do kucia", Material.GOLD_INGOT, 8, "pick_iron"));
        inv.setItem(12, this.createShopItem(Material.BOW, "Łuk", "Zasięg ataku", Material.GOLD_INGOT, 6, "bow"));
        inv.setItem(13, this.createShopItem(Material.ARROW, "Strzały x8", "Ammo", Material.GOLD_INGOT, 1, "arrows_8"));
        inv.setItem(14, this.createShopItem(Material.GOLDEN_APPLE, "Złote jabłko x1", "Leczenie", Material.IRON_INGOT, 6, "gapple_1"));
        inv.setItem(15, this.createShopItem(Material.TNT, "TNT x1", "Wybuchem", Material.DIAMOND, 1, "tnt_1"));
        inv.setItem(16, this.createShopItem(Material.OBSIDIAN, "Obsidian", "Trudny do zniszczenia", Material.GOLD_INGOT, 16, "obsidian_1"));
        inv.setItem(18, this.createShopItem(Material.CHAINMAIL_LEGGINGS, "Kolczugowa zbroja", "8 żelaza", Material.IRON_INGOT, 8, "armor_chain"));
        inv.setItem(19, this.createShopItem(Material.IRON_LEGGINGS, "Żelazna zbroja", "12 złota", Material.GOLD_INGOT, 12, "armor_iron"));
        inv.setItem(20, this.createShopItem(Material.DIAMOND_LEGGINGS, "Diamentowa zbroja", "6 diamentów", Material.DIAMOND, 6, "armor_diamond"));
        inv.setItem(21, this.createShopItem(Material.ENDER_PEARL, "Ender Pearl", "Teleport", Material.DIAMOND, 2, "enderpearl"));
        inv.setItem(22, this.createShopItem(Material.COOKED_BEEF, "Jedzenie x8", "Stek", Material.IRON_INGOT, 2, "food_8"));
        inv.setItem(23, this.createShopItem(Material.GOLD_INGOT, "Złoto x1", "Waluta", Material.EMERALD, 1, "gold_1"));
        inv.setItem(36, this.createMenuItem(Material.PAPER, String.valueOf(ChatColor.YELLOW) + "Ekwipunek", String.valueOf(ChatColor.GRAY) + "Twoje przedmioty"));
        player.openInventory(inv);
    }

    private ItemStack createShopItem(Material mat, String name, String desc, Material currency, int cost, String key) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String var10001 = String.valueOf(ChatColor.GREEN);
        meta.setDisplayName(var10001 + name);
        List<String> lore = new ArrayList();
        if (desc != null && !desc.isEmpty()) {
            var10001 = String.valueOf(ChatColor.GRAY);
            lore.add(var10001 + desc);
        }

        if (currency != null && currency != Material.AIR && cost > 0) {
            lore.add("");
            var10001 = String.valueOf(ChatColor.YELLOW);
            lore.add(var10001 + "Cena: " + String.valueOf(ChatColor.WHITE) + cost + " " + this.formatCurrency(currency));
        }

        meta.setLore(lore);
        if (key != null) {
            meta.getPersistentDataContainer().set(this.shopKey, PersistentDataType.STRING, key);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMenuItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null && lore.length > 0) {
            meta.setLore(Arrays.asList(lore));
        }

        item.setItemMeta(meta);
        return item;
    }

    private int countItems(Player player, Material material) {
        int count = 0;

        for(ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }

        return count;
    }

    public void respawnShopsFromArenas() {
        this.removeAllShops();

        for(BedWarsPlugin.Arena arena : this.plugin.getArenaManager().getArenas()) {
            for(BedWarsPlugin.Team team : arena.getTeams()) {
                Location loc = arena.getTeamShop(team);
                if (loc != null) {
                    this.spawnTeamShop(arena, team, loc);
                }
            }
        }

    }

    public void spawnTeamShop(BedWarsPlugin.Arena arena, BedWarsPlugin.Team team, Location location) {
        Villager villager = (Villager)location.getWorld().spawn(location, Villager.class);
        String var10001 = String.valueOf(ChatColor.GREEN);
        villager.setCustomName(var10001 + "Sklep " + team.getId());
        villager.setInvulnerable(true);
        villager.setAI(false);
        this.villagerTeams.put(villager.getUniqueId(), new VillagerShopInfo(arena, team));
    }

    public void removeAllShops() {
        for(World world : Bukkit.getWorlds()) {
            for(Villager villager : world.getEntitiesByClass(Villager.class)) {
                String name = ChatColor.stripColor(villager.getCustomName() == null ? "" : villager.getCustomName());
                if (name.startsWith("Sklep ")) {
                    villager.remove();
                }
            }
        }

        this.villagerTeams.clear();
        this.plugin.getLogger().info("\ud83e\uddf9 Usunięto wszystkie villagery sklepowe.");
    }

    public boolean handlePurchase(Player player, ItemStack item, Material currency, int cost) {
        int amount = this.countItems(player, currency);
        if (amount < cost) {
            String var6 = String.valueOf(ChatColor.RED);
            player.sendMessage(var6 + "Nie masz wystarczająco " + currency.name().toLowerCase().replace("_", " ") + "!");
            return false;
        } else {
            this.removeItems(player, currency, cost);
            player.getInventory().addItem(new ItemStack[]{item});
            String var10001 = String.valueOf(ChatColor.GREEN);
            player.sendMessage(var10001 + "Zakupiono: " + String.valueOf(ChatColor.YELLOW) + item.getType().name());
            return true;
        }
    }

    private SwordUpgrade getNextSword(Player player) {
        int level = this.getCurrentSwordLevel(player);
        SwordUpgrade var10000;
        switch (level) {
            case 0 -> var10000 = new SwordUpgrade(Material.STONE_SWORD, Material.IRON_INGOT, 6);
            case 1 -> var10000 = new SwordUpgrade(Material.IRON_SWORD, Material.GOLD_INGOT, 8);
            case 2 -> var10000 = new SwordUpgrade(Material.DIAMOND_SWORD, Material.DIAMOND, 4);
            default -> var10000 = null;
        }

        return var10000;
    }

    private void handleItemShopClick(Player player, ItemStack clicked, InventoryClickEvent ev) {
        if (clicked != null && clicked.hasItemMeta()) {
            ItemMeta meta = clicked.getItemMeta();
            String key = (String)meta.getPersistentDataContainer().get(this.shopKey, PersistentDataType.STRING);
            boolean success = false;
            if (key != null) {
                switch (key) {
                    case "wool_16":
                        success = this.handlePurchase(player, new ItemStack(this.plugin.getArenaManager().getPlayerTeam(player).getWoolMaterial(), 16), Material.IRON_INGOT, 4);
                        break;
                    case "sword_wood":
                    case "sword_stone":
                    case "sword_iron":
                        BedWarsPlugin.Team team = this.plugin.getArenaManager().getPlayerTeam(player);
                        if (team == null) {
                            return;
                        }

                        success = this.handleSwordUpgrade(player);
                        if (success) {
                            this.openItemShopGUI(player, team);
                        }
                        break;
                    case "pick_iron":
                        success = this.handlePurchase(player, new ItemStack(Material.IRON_PICKAXE), Material.GOLD_INGOT, 8);
                        break;
                    case "bow":
                        success = this.handlePurchase(player, new ItemStack(Material.BOW), Material.GOLD_INGOT, 6);
                        break;
                    case "arrows_8":
                        success = this.handlePurchase(player, new ItemStack(Material.ARROW, 8), Material.GOLD_INGOT, 1);
                        break;
                    case "gapple_1":
                        success = this.handlePurchase(player, new ItemStack(Material.GOLDEN_APPLE, 1), Material.IRON_INGOT, 6);
                        break;
                    case "tnt_1":
                        success = this.handlePurchase(player, new ItemStack(Material.TNT, 1), Material.DIAMOND, 1);
                        break;
                    case "obsidian_1":
                        success = this.handlePurchase(player, new ItemStack(Material.OBSIDIAN, 1), Material.GOLD_INGOT, 16);
                        break;
                    case "armor_chain":
                        success = this.handleArmorPurchase(player, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS, Material.IRON_INGOT, 8);
                        break;
                    case "armor_iron":
                        success = this.handleArmorPurchase(player, Material.IRON_LEGGINGS, Material.IRON_BOOTS, Material.GOLD_INGOT, 12);
                        break;
                    case "armor_diamond":
                        success = this.handleArmorPurchase(player, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS, Material.DIAMOND, 6);
                        break;
                    case "enderpearl":
                        success = this.handlePurchase(player, new ItemStack(Material.ENDER_PEARL, 1), Material.DIAMOND, 2);
                        break;
                    case "food_8":
                        success = this.handlePurchase(player, new ItemStack(Material.COOKED_BEEF, 8), Material.IRON_INGOT, 2);
                        break;
                    case "gold_1":
                        success = this.handlePurchase(player, new ItemStack(Material.GOLD_INGOT, 1), Material.EMERALD, 1);
                        break;
                    case "open_item_shop":
                        BedWarsPlugin.Team playerTeam = this.plugin.getArenaManager().getPlayerTeam(player);
                        if (playerTeam != null) {
                            this.openItemShopGUI(player, playerTeam);
                        }

                        return;
                    case "cat":
                    case "cat_blocks":
                    case "cat_weapons":
                    case "cat_armor":
                    case "cat_bows":
                    case "cat_explosives":
                        String var10001 = String.valueOf(ChatColor.GRAY);
                        player.sendMessage(var10001 + "Kategoria: " + String.valueOf(ChatColor.WHITE) + (meta.getDisplayName() == null ? "" : ChatColor.stripColor(meta.getDisplayName())));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
                        return;
                }
            } else {
                switch (ChatColor.stripColor(meta.getDisplayName()).toLowerCase()) {
                    case "wełna 16x" -> success = this.handlePurchase(player, new ItemStack(this.plugin.getArenaManager().getPlayerTeam(player).getWoolMaterial(), 16), Material.IRON_INGOT, 4);
                    case "drewniany miecz" -> success = this.handlePurchase(player, new ItemStack(Material.WOODEN_SWORD), Material.IRON_INGOT, 4);
                }
            }

            if (success) {
                player.closeInventory();
            }

        }
    }

    private int getCurrentSwordLevel(Player player) {
        List<Material> swords = Arrays.asList(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD);
        int highestLevel = 0;

        for(ItemStack item : player.getInventory().getContents()) {
            if (item != null && swords.contains(item.getType())) {
                int level = swords.indexOf(item.getType());
                if (level > highestLevel) {
                    highestLevel = level;
                }
            }
        }

        return highestLevel;
    }

    private void upgradeSword(Player player) {
        int level = this.getCurrentSwordLevel(player);
        Material nextSword;
        Material currency;
        int cost;
        switch (level) {
            case 0:
                nextSword = Material.STONE_SWORD;
                currency = Material.IRON_INGOT;
                cost = 4;
                break;
            case 1:
                nextSword = Material.IRON_SWORD;
                currency = Material.GOLD_INGOT;
                cost = 6;
                break;
            case 2:
                nextSword = Material.DIAMOND_SWORD;
                currency = Material.DIAMOND;
                cost = 2;
                break;
            default:
                player.sendMessage(String.valueOf(ChatColor.RED) + "Masz już najlepszy miecz!");
                return;
        }

        if (!this.hasEnough(player, currency, cost)) {
            String var6 = String.valueOf(ChatColor.RED);
            player.sendMessage(var6 + "Nie masz wystarczająco " + this.formatCurrency(currency) + "!");
        } else {
            this.removeItems(player, currency, cost);
            player.getInventory().setItemInMainHand(new ItemStack(nextSword));
            String var10001 = String.valueOf(ChatColor.GREEN);
            player.sendMessage(var10001 + "Kupiono: " + nextSword.name());
        }
    }

    @EventHandler
    public void onVillagerClick(PlayerInteractEntityEvent ev) {
        Entity var3 = ev.getRightClicked();
        if (var3 instanceof Villager villager) {
            if (this.villagerTeams.containsKey(villager.getUniqueId())) {
                ev.setCancelled(true);
                Player player = ev.getPlayer();
                VillagerShopInfo info = (VillagerShopInfo)this.villagerTeams.get(villager.getUniqueId());
                BedWarsPlugin.Arena shopArena = info.arena;
                BedWarsPlugin.Team shopTeam = info.team;
                BedWarsPlugin.Arena playerArena = this.plugin.getArenaManager().getArenaForPlayer(player);
                if (playerArena != null && playerArena.equals(shopArena)) {
                    BedWarsPlugin.Team playerTeam = this.plugin.getArenaManager().getPlayerTeam(player);
                    if (playerTeam != null && playerTeam.equals(shopTeam)) {
                        this.openItemShopGUI(player, playerTeam);
                    } else {
                        player.sendMessage(String.valueOf(ChatColor.RED) + "To nie jest sklep twojej drużyny!");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                    }
                } else {
                    player.sendMessage(String.valueOf(ChatColor.RED) + "Ten sklep nie należy do twojej areny!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent ev) {
        HumanEntity var3 = ev.getWhoClicked();
        if (var3 instanceof Player player) {
            if (ev.getView() != null && ev.getView().getTitle() != null) {
                String title = ChatColor.stripColor(ev.getView().getTitle());
                Inventory top = ev.getView().getTopInventory();
                boolean isPluginGUI = title.equalsIgnoreCase("Item shop") || title.startsWith("Sklep drużyny") || title.startsWith("Zbroje -");
                if (isPluginGUI && ev.getRawSlot() < top.getSize()) {
                    ev.setCancelled(true);
                }

                ItemStack clicked = ev.getCurrentItem();
                if (clicked != null && clicked.getType() != Material.AIR) {
                    UUID uuid = player.getUniqueId();
                    long now = System.currentTimeMillis();
                    Long last = (Long)this.recentClicks.get(uuid);
                    if (last == null || now - last >= 300L) {
                        this.recentClicks.put(uuid, now);
                        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.recentClicks.remove(uuid), 6L);
                        if (title.equalsIgnoreCase("Item shop")) {
                            this.handleItemShopClick(player, clicked, ev);
                        } else {
                            if (title.startsWith("Sklep drużyny")) {
                                if (ev.getRawSlot() >= top.getSize()) {
                                    return;
                                }

                                if (!clicked.hasItemMeta()) {
                                    return;
                                }

                                String itemName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                                if (itemName.equalsIgnoreCase("Item Shop")) {
                                    BedWarsPlugin.Team team = this.plugin.getArenaManager().getPlayerTeam(player);
                                    if (team != null) {
                                        this.openItemShopGUI(player, team);
                                    }

                                    return;
                                }

                                if (itemName.equalsIgnoreCase("Wełna (16x za 4 żelaza)")) {
                                    BedWarsPlugin.Arena arena = this.plugin.getArenaManager().getArenaForPlayer(player);
                                    if (arena == null) {
                                        return;
                                    }

                                    BedWarsPlugin.Team team = arena.getTeam(title.substring("Sklep drużyny - ".length()).trim());
                                    this.handlePurchase(player, new ItemStack(team.getWoolMaterial(), 16), Material.IRON_INGOT, 4);
                                    return;
                                }

                                if (clicked.getType() == Material.BARRIER) {
                                    player.closeInventory();
                                    return;
                                }
                            }

                            if (title.startsWith("Zbroje -")) {
                                if (ev.getRawSlot() >= top.getSize()) {
                                    return;
                                }

                                if (!clicked.hasItemMeta()) {
                                    return;
                                }

                                String itemName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                                if (itemName.equalsIgnoreCase("← Wróć")) {
                                    BedWarsPlugin.Team team = this.plugin.getArenaManager().getPlayerTeam(player);
                                    if (team != null) {
                                        this.openShopMainGUI(player, team);
                                    }

                                    return;
                                }

                                boolean ok = false;
                                switch (itemName.toLowerCase()) {
                                    case "kolczugowa zbroja" -> ok = this.handleArmorPurchase(player, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS, Material.IRON_INGOT, 8);
                                    case "żelazna zbroja" -> ok = this.handleArmorPurchase(player, Material.IRON_LEGGINGS, Material.IRON_BOOTS, Material.GOLD_INGOT, 12);
                                    case "diamentowa zbroja" -> ok = this.handleArmorPurchase(player, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS, Material.DIAMOND, 6);
                                }

                                if (ok) {
                                    player.closeInventory();
                                }
                            }

                        }
                    }
                }
            }
        }
    }

    private boolean handleArmorPurchase(Player player, Material leggingsMat, Material bootsMat, Material currency, int cost) {
        ItemStack currentLeggings = player.getInventory().getLeggings();
        Material currentType = currentLeggings != null ? currentLeggings.getType() : Material.LEATHER_LEGGINGS;
        List<Material> armorLevels = Arrays.asList(Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS, Material.IRON_LEGGINGS, Material.DIAMOND_LEGGINGS);
        int currentLevel = armorLevels.indexOf(currentType);
        int newLevel = armorLevels.indexOf(leggingsMat);
        if (newLevel <= currentLevel) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "Masz już taką lub lepszą zbroję!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            return false;
        } else if (currency != Material.AIR && !this.hasEnough(player, currency, cost)) {
            String var17 = String.valueOf(ChatColor.RED);
            player.sendMessage(var17 + "Nie masz wystarczająco " + this.formatCurrency(currency) + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            return false;
        } else {
            if (currency != Material.AIR) {
                this.removeItems(player, currency, cost);
            }

            BedWarsPlugin.Team team = this.plugin.getArenaManager().getPlayerTeam(player);
            Color teamColor = team != null ? team.getLeatherColor() : Color.WHITE;
            ItemStack helmet = this.createLeatherArmor(Material.LEATHER_HELMET, teamColor);
            ItemStack chest = this.createLeatherArmor(Material.LEATHER_CHESTPLATE, teamColor);
            ItemStack leggings = new ItemStack(leggingsMat);
            ItemStack boots = new ItemStack(bootsMat);
            leggings.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 1);
            boots.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 1);
            player.getInventory().setHelmet(helmet);
            player.getInventory().setChestplate(chest);
            player.getInventory().setLeggings(leggings);
            player.getInventory().setBoots(boots);
            this.playerArmorMap.put(player.getUniqueId(), new PlayerArmor(helmet, chest, leggings, boots));
            String var10001 = String.valueOf(ChatColor.GREEN);
            player.sendMessage(var10001 + "✅ Ulepszono zbroję na " + String.valueOf(ChatColor.YELLOW) + leggingsMat.name());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0F, 1.0F);
            return true;
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

    private boolean hasEnough(Player player, Material mat, int amount) {
        return player.getInventory().containsAtLeast(new ItemStack(mat), amount);
    }

    private String formatCurrency(Material mat) {
        String var10000;
        switch (mat) {
            case IRON_INGOT -> var10000 = "żelaza";
            case GOLD_INGOT -> var10000 = "złota";
            case DIAMOND -> var10000 = "diamentów";
            case EMERALD -> var10000 = "szmaragdów";
            default -> var10000 = mat.name().toLowerCase();
        }

        return var10000;
    }

    private void removeItems(Player player, Material mat, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();

        for(int i = 0; i < contents.length; ++i) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == mat) {
                if (item.getAmount() > remaining) {
                    item.setAmount(item.getAmount() - remaining);
                    break;
                }

                remaining -= item.getAmount();
                contents[i] = null;
                if (remaining <= 0) {
                    break;
                }
            }
        }

        player.getInventory().setContents(contents);
    }

    public static class PlayerArmor {
        private final ItemStack helmet;
        private final ItemStack chestplate;
        private final ItemStack leggings;
        private final ItemStack boots;

        public PlayerArmor(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots) {
            this.helmet = helmet;
            this.chestplate = chestplate;
            this.leggings = leggings;
            this.boots = boots;
        }

        public ItemStack getHelmet() {
            return this.helmet;
        }

        public ItemStack getChestplate() {
            return this.chestplate;
        }

        public ItemStack getLeggings() {
            return this.leggings;
        }

        public ItemStack getBoots() {
            return this.boots;
        }
    }

    public static class VillagerShopInfo {
        public final BedWarsPlugin.Arena arena;
        public final BedWarsPlugin.Team team;

        public VillagerShopInfo(BedWarsPlugin.Arena arena, BedWarsPlugin.Team team) {
            this.arena = arena;
            this.team = team;
        }
    }

    private class SwordUpgrade {
        public final Material nextSword;
        public final Material currency;
        public final int cost;

        public SwordUpgrade(Material nextSword, Material currency, int cost) {
            this.nextSword = nextSword;
            this.currency = currency;
            this.cost = cost;
        }
    }
}
