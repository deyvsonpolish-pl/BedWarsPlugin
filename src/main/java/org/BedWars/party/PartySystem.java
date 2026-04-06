package org.BedWars.party;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.BedWars.BedWarsPlugin;
import org.BedWars.BedWarsPlugin.Arena;
import org.BedWars.RankedSystem;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.BedWars.BedWarsPlugin.Team;
/**
 * PartySystem - 100% GUI (bez komend)
 * - item "Party" w GUI aren
 * - Party Main GUI: członkowie, zaproś, wyszukaj, ustawienia, wyjdź
 * - Invite GUI: lista graczy online bez party
 * - Pending Invites GUI: przyjmij / odrzuć
 * - Settings GUI: tryb, prywatność, allow randoms (placeholder pod przyszłość)
 *
 * + Integracja dołączania do areny:
 *   - gdy lider kliknie arenę w GUI "BedWars - Areny", dobieramy resztę party do tej samej areny
 *
 * + MATCHMAKING (szukanie areny):
 *   - lider klika "Szukaj gry" (slot 22)
 *   - przez 30s próbujemy znaleźć najlepszą arenę (normal i ranked)
 *   - jeśli arena ranked i ktoś nie spełnia wymagań -> informacja i szukamy dalej
 */
public class PartySystem implements Listener {

    // ======================
    // Public API
    // ======================

    public PartySystem(BedWarsPlugin plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "bw_party");
        this.KEY_ACTION = new NamespacedKey(plugin, "bw_party_action");
        this.KEY_TARGET = new NamespacedKey(plugin, "bw_party_target");
        this.KEY_PAGE = new NamespacedKey(plugin, "bw_party_page");
        this.KEY_TEAM_ID = new NamespacedKey(plugin, "bw_team_id"); // ✅ TU
    }
    private final NamespacedKey KEY_TEAM_ID;
    public void openPartyMain(Player p) {
        Party party = getPartyOf(p.getUniqueId());
        if (party == null) {
            party = createParty(p.getUniqueId());
        }
        openPartyMainInternal(p, party);
    }

    public void openInvitesInbox(Player p) {
        openInvitesInboxInternal(p, 0);
    }

    /**
     * Wstaw przycisk PARTY do Twojego GUI (np. slot 24)
     */
    public void placePartyButton(Inventory inv, int slot) {
        inv.setItem(slot, makeActionItem(
                Material.NETHER_STAR,
                "§bParty",
                Arrays.asList(
                        "§7Zarządzaj drużyną",
                        "§7Zapraszaj graczy",
                        "§7Grajcie razem!",
                        "",
                        "§eKliknij aby otworzyć"
                ),
                "OPEN_MAIN",
                null,
                0
        ));
    }

    /**
     * Czy gracz ma party?
     */
    public boolean hasParty(UUID player) {
        return partyByMember.containsKey(player);
    }

    /**
     * Pobierz party gracza
     */
    public Party getPartyOf(UUID player) {
        UUID partyId = partyByMember.get(player);
        if (partyId == null) return null;
        return parties.get(partyId);
    }

    // ======================
    // Party model
    // ======================

    public enum PartyPrivacy {
        INVITE_ONLY("Invite Only"),
        PUBLIC("Public"),
        CLOSED("Closed");

        private final String display;

        PartyPrivacy(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }

        public PartyPrivacy next() {
            PartyPrivacy[] v = values();
            int idx = Arrays.asList(v).indexOf(this);
            return v[(idx + 1) % v.length];
        }
    }

    public enum PartyModePref {
        AUTO("Auto"),
        SOLO("Solo"),
        DUO("Duo"),
        TRIO("Trio"),
        V4("4v4");

        private final String display;

        PartyModePref(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }

        public PartyModePref next() {
            PartyModePref[] v = values();
            int idx = Arrays.asList(v).indexOf(this);
            return v[(idx + 1) % v.length];
        }
    }

    public static class PartySettings {
        private PartyPrivacy privacy = PartyPrivacy.INVITE_ONLY;
        private PartyModePref modePref = PartyModePref.AUTO;
        private boolean allowRandoms = true;

        public PartyPrivacy getPrivacy() {
            return privacy;
        }

        public void setPrivacy(PartyPrivacy privacy) {
            this.privacy = privacy;
        }

        public PartyModePref getModePref() {
            return modePref;
        }

        public void setModePref(PartyModePref modePref) {
            this.modePref = modePref;
        }

        public boolean isAllowRandoms() {
            return allowRandoms;
        }

        public void setAllowRandoms(boolean allowRandoms) {
            this.allowRandoms = allowRandoms;
        }
    }

    public static class Party {
        private final UUID id;
        private UUID leader;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        private final PartySettings settings = new PartySettings();

        public Party(UUID id, UUID leader) {
            this.id = id;
            this.leader = leader;
            members.add(leader);
        }
        public UUID getId() {
            return id;
        }

        public UUID getLeader() {
            return leader;
        }

        public void setLeader(UUID leader) {
            this.leader = leader;
        }

        public LinkedHashSet<UUID> getMembers() {
            return members;
        }

        public PartySettings getSettings() {
            return settings;
        }

        public boolean isLeader(UUID u) {
            return leader != null && leader.equals(u);
        }

        public int size() {
            return members.size();
        }
    }

    // ======================
    // Storage
    // ======================

    // === MATCHMAKING PARTY ===
    private static class PartyJoinBlock {
        final UUID member;
        final String arenaName;

        PartyJoinBlock(UUID member, String arenaName) {
            this.member = member;
            this.arenaName = arenaName;
        }
    }

    private PartyJoinBlock findPartyJoinBlock(Party party) {
        if (party == null) return null;
        if (plugin.getArenaManager() == null) return null;

        // sprawdzamy wszystkich członków party
        for (UUID u : party.getMembers()) {
            Player pl = Bukkit.getPlayer(u);
            if (pl == null || !pl.isOnline()) continue;

            Arena a = getArenaOfPlayer(pl);
            if (a != null) {
                return new PartyJoinBlock(u, a.getName());
            }
        }
        return null;
    }

    private final Map<UUID, BukkitRunnable> partyQueueTasks = new HashMap<>();
    private final Map<UUID, Long> partyQueueEndAt = new HashMap<>(); // leader -> ms end
    private final Map<UUID, Long> partyQueueStartAt = new HashMap<>(); // leader -> ms start
    private final Map<UUID, String> partyQueueMode = new HashMap<>(); // leader -> "AUTO" / "SOLO" / "DUO" etc (opcjonalnie)

    private final BedWarsPlugin plugin;

    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> partyByMember = new ConcurrentHashMap<>();

    /**
     * invites[target] = list of invites (from leader)
     */
    private final Map<UUID, LinkedHashSet<Invite>> invites = new ConcurrentHashMap<>();

    private static class Invite {
        final UUID fromLeader;
        final UUID partyId;
        final long createdAt;

        Invite(UUID fromLeader, UUID partyId) {
            this.fromLeader = fromLeader;
            this.partyId = partyId;
            this.createdAt = System.currentTimeMillis();
        }
    }

    private Party createParty(UUID leader) {
        Party p = new Party(UUID.randomUUID(), leader);
        parties.put(p.getId(), p);
        partyByMember.put(leader, p.getId());
        return p;
    }

    private void disbandParty(Party party) {
        // jeśli lider ma kolejkę -> stop
        if (party.getLeader() != null) {
            stopQueue(party.getLeader(), false);
        }

        for (UUID m : new HashSet<>(party.getMembers())) {
            partyByMember.remove(m);
            Player pl = Bukkit.getPlayer(m);
            if (pl != null && pl.isOnline()) {
                pl.sendMessage("§cParty zostało rozwiązane.");
                pl.playSound(pl.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1.2f);
            }
        }
        parties.remove(party.getId());

        // usuń zaproszenia do tej party
        for (Map.Entry<UUID, LinkedHashSet<Invite>> e : invites.entrySet()) {
            e.getValue().removeIf(inv -> inv.partyId.equals(party.getId()));
        }
    }

    private void leaveParty(UUID player) {
        Party party = getPartyOf(player);
        if (party == null) return;

        // jeśli to lider i szuka -> stop
        if (party.isLeader(player)) {
            stopQueue(player, false);
        }

        party.getMembers().remove(player);
        partyByMember.remove(player);

        Player pl = Bukkit.getPlayer(player);
        if (pl != null && pl.isOnline()) {
            pl.sendMessage("§eOpuściłeś party.");
            pl.playSound(pl.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        }

        // jeśli leader wyszedł -> nowy leader
        if (party.getLeader().equals(player)) {
            UUID newLeader = party.getMembers().stream().findFirst().orElse(null);
            if (newLeader == null) {
                disbandParty(party);
                return;
            }
            party.setLeader(newLeader);
            broadcast(party, "§6Nowy lider party: §e" + name(newLeader));
        }
    }

    private void kickMember(UUID leader, UUID target) {
        Party party = getPartyOf(leader);
        if (party == null) return;
        if (!party.isLeader(leader)) return;
        if (leader.equals(target)) return;
        if (!party.getMembers().contains(target)) return;

        party.getMembers().remove(target);
        partyByMember.remove(target);

        Player t = Bukkit.getPlayer(target);
        if (t != null && t.isOnline()) {
            t.sendMessage("§cZostałeś wyrzucony z party.");
            t.playSound(t.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }
        broadcast(party, "§c" + name(target) + " został wyrzucony z party.");
    }

    private void broadcast(Party party, String msg) {
        for (UUID m : party.getMembers()) {
            Player pl = Bukkit.getPlayer(m);
            if (pl != null && pl.isOnline()) pl.sendMessage(msg);
        }
    }

    private String name(UUID u) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
        return (op.getName() != null) ? op.getName() : u.toString().substring(0, 6);
    }

    // ======================
    // GUI constants
    // ======================

    private final NamespacedKey KEY;
    private final NamespacedKey KEY_ACTION;
    private final NamespacedKey KEY_TARGET;
    private final NamespacedKey KEY_PAGE;

    private static final String GUI_MAIN = "§bParty";
    private static final String GUI_INVITE = "§aZaproś gracza";
    private static final String GUI_INBOX = "§eZaproszenia do Party";
    private static final String GUI_SETTINGS = "§dUstawienia Party";

    private static final int MAIN_SIZE = 27;
    private static final int LIST_SIZE = 54;

    private static final int SLOT_INVITE = 14;
    private static final int SLOT_SEARCH = 15; // w tej wersji = Inbox/Invites
    private static final int SLOT_SETTINGS = 16;
    private static final int SLOT_QUEUE = 22;   // MATCHMAKING
    private static final int SLOT_LEAVE = 26;

    // ======================
    // GUI builders
    // ======================

    private void openPartyMainInternal(Player viewer, Party party) {
        Inventory inv = Bukkit.createInventory(null, MAIN_SIZE, GUI_MAIN + " §7(" + party.size() + ")");

        // ramka
        fillBorder(inv);

        // leader head
        inv.setItem(4, makePlayerHead(
                party.getLeader(),
                "§6Lider: §e" + name(party.getLeader()),
                Arrays.asList(
                        "§7Kliknij aby (lider):",
                        "§7- wejść w ustawienia",
                        "§7- zarządzać party"
                )
        ));

        // members heads slots 10-13
        List<Integer> memberSlots = Arrays.asList(10, 11, 12, 13);
        List<UUID> mem = new ArrayList<>(party.getMembers());
        int idx = 0;
        for (int slot : memberSlots) {
            if (idx < mem.size()) {
                UUID m = mem.get(idx++);
                boolean isLeader = party.getLeader().equals(m);

                List<String> lore = new ArrayList<>();
                lore.add("§7Status: " + (Bukkit.getPlayer(m) != null ? "§aOnline" : "§cOffline"));
                lore.add("");
                if (party.isLeader(viewer.getUniqueId()) && !isLeader) {
                    lore.add("§cPPM: wyrzuć");
                }
                lore.add("§7LPP: profil (wkrótce)");

                inv.setItem(slot, makeMemberHeadWithAction(m, (isLeader ? "§6" : "§b") + name(m), lore));
            } else {
                inv.setItem(slot, makeItem(Material.GRAY_DYE, "§7Wolne miejsce", Collections.singletonList("§7Zaproś kogoś!")));
            }
        }

        // invite button
        inv.setItem(SLOT_INVITE, makeActionItem(
                Material.EMERALD,
                "§aZaproś gracza",
                Arrays.asList("§7Otwiera listę graczy", "§7i pozwala zaprosić", "", "§eKliknij"),
                "OPEN_INVITE",
                null,
                0
        ));

        // inbox / zaproszenia
        int pending = invites.getOrDefault(viewer.getUniqueId(), new LinkedHashSet<>()).size();
        inv.setItem(SLOT_SEARCH, makeActionItem(
                Material.CHEST,
                "§eZaproszenia §7(" + pending + ")",
                Arrays.asList("§7Twoje zaproszenia do party", "", "§eKliknij"),
                "OPEN_INBOX",
                null,
                0
        ));

        // settings
        PartySettings s = party.getSettings();
        inv.setItem(SLOT_SETTINGS, makeActionItem(
                Material.REDSTONE,
                "§dUstawienia Party",
                Arrays.asList(
                        "§7Tryb: §f" + s.getModePref().getDisplay(),
                        "§7Prywatność: §f" + s.getPrivacy().getDisplay(),
                        "§7Randomy: §f" + (s.isAllowRandoms() ? "TAK" : "NIE"),
                        "",
                        party.isLeader(viewer.getUniqueId()) ? "§eKliknij" : "§cTylko lider"
                ),
                "OPEN_SETTINGS",
                null,
                0
        ));

        // queue (MATCHMAKING)
// queue (MATCHMAKING)
        boolean inQueue = isInQueue(party.getLeader());

// sprawdzamy czy ktoś z party jest już na arenie
        PartyJoinBlock block = findPartyJoinBlock(party);

        if (!inQueue) {

            List<String> lore = new ArrayList<>();
            lore.add("§7Wyszuka dostępne areny");
            lore.add("§7dla Twojego party (Normal + Ranked)");
            lore.add("");
            lore.add("§7Tryb: §f" + s.getModePref().getDisplay());
            lore.add("§7Członków: §f" + party.size() + "/4");
            lore.add("");

            if (block != null) {
                lore.add("§cNie można dołączyć!");
                lore.add("§7" + name(block.member) + " §7aktualnie gra na mapie:");
                lore.add("§b" + block.arenaName);
                lore.add("");
                lore.add("§8(Gracz musi wyjść z areny)");
            } else {
                lore.add("§aStatus: §fOnline");
                lore.add("");
                lore.add(party.isLeader(viewer.getUniqueId()) ? "§eKliknij aby rozpocząć" : "§cTylko lider");
            }

            inv.setItem(SLOT_QUEUE, makeActionItem(
                    Material.NETHER_STAR,
                    "§aSzukaj gry",
                    lore,
                    "QUEUE_START",
                    null,
                    0
            ));

        } else {

            int elapsed = getQueueElapsedSeconds(party.getLeader());
            int max = 30;

            inv.setItem(SLOT_QUEUE, makeActionItem(
                    Material.CLOCK,
                    "§eSzukanie aren...",
                    Arrays.asList(
                            "§7Szukanie dostępnych aren dla was",
                            "",
                            "§e" + elapsed + "s §7/ §e" + max + "s",
                            "",
                            party.isLeader(viewer.getUniqueId()) ? "§cKliknij aby anulować" : "§7Tylko lider może anulować"
                    ),
                    "QUEUE_STOP",
                    null,
                    0
            ));
        }

        // leave/disband
        boolean isLeader = party.isLeader(viewer.getUniqueId());
        inv.setItem(SLOT_LEAVE, makeActionItem(
                Material.BARRIER,
                isLeader ? "§cRozwiąż Party" : "§cOpuść Party",
                Arrays.asList(
                        isLeader ? "§7Rozwiązuje party dla wszystkich" : "§7Opuszczasz party",
                        "",
                        "§cKliknij"
                ),
                isLeader ? "DISBAND" : "LEAVE",
                null,
                0
        ));

        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
    }

    private void openInviteListInternal(Player viewer, int page) {
        Party party = getPartyOf(viewer.getUniqueId());
        if (party == null) {
            viewer.sendMessage("§cNie masz party.");
            return;
        }
        if (!party.isLeader(viewer.getUniqueId())) {
            viewer.sendMessage("§cTylko lider może zapraszać.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, LIST_SIZE, GUI_INVITE + " §7(str. " + (page + 1) + ")");

        fillBorder(inv);

        // gracze online w lobby (prosto: wszyscy online, bez siebie, bez party)
        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(viewer.getUniqueId()))
                .filter(t -> canBeInvited(t.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        int perPage = 28; // środek 4 rzędy po 7 (od 10 do 43 bez bordera) - uproszczenie
        int maxPage = candidates.isEmpty() ? 0 : (candidates.size() - 1) / perPage;
        page = Math.max(0, Math.min(page, maxPage));

        int start = page * perPage;
        int end = Math.min(start + perPage, candidates.size());
        List<Player> slice = candidates.subList(start, end);

        List<Integer> slots = centerSlots54();
        int i = 0;
        for (Player t : slice) {
            if (i >= slots.size()) break;
            inv.setItem(slots.get(i++), makeActionHead(
                    t.getUniqueId(),
                    "§a" + t.getName(),
                    Arrays.asList("§7Kliknij aby zaprosić", "§7Do twojego party"),
                    "INVITE",
                    t.getUniqueId().toString(),
                    page
            ));
        }

        // pager
        inv.setItem(45, makeActionItem(Material.ARROW, "§e◀ Poprzednia", Collections.singletonList("§7Zmień stronę"), "INV_PAGE_PREV", null, page));
        inv.setItem(49, makeActionItem(Material.PAPER, "§7Strona: §f" + (page + 1) + "§7/§f" + (maxPage + 1),
                Arrays.asList("§7Kliknij aby zmienić stronę", "§7PPM w prawo, LPP wstecz"), "NOOP", null, page));
        inv.setItem(53, makeActionItem(Material.ARROW, "§eNastępna ▶", Collections.singletonList("§7Zmień stronę"), "INV_PAGE_NEXT", null, page));

        // back
        inv.setItem(48, makeActionItem(Material.BARRIER, "§cPowrót", Collections.singletonList("§7Wróć do Party"), "BACK_MAIN", null, page));

        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
    }

    private boolean canBeInvited(UUID u) {
        Party party = getPartyOf(u);
        // brak party -> ok
        if (party == null) return true;

        // solo-party (1 osoba, lider = on sam) -> też ok
        return party.size() == 1 && party.getLeader().equals(u);
    }

    private void openInvitesInboxInternal(Player viewer, int page) {
        Inventory inv = Bukkit.createInventory(null, LIST_SIZE, GUI_INBOX + " §7(str. " + (page + 1) + ")");

        fillBorder(inv);

        LinkedHashSet<Invite> set = invites.getOrDefault(viewer.getUniqueId(), new LinkedHashSet<>());
        List<Invite> list = set.stream().limit(200).collect(Collectors.toList());

        int perPage = 28;
        int maxPage = list.isEmpty() ? 0 : (list.size() - 1) / perPage;
        page = Math.max(0, Math.min(page, maxPage));

        int start = page * perPage;
        int end = Math.min(start + perPage, list.size());
        List<Invite> slice = list.subList(start, end);

        List<Integer> slots = centerSlots54();
        int i = 0;
        for (Invite invt : slice) {
            Party party = parties.get(invt.partyId);
            if (party == null) continue;

            UUID leader = invt.fromLeader;

            List<String> lore = new ArrayList<>();
            lore.add("§7Od: §e" + name(leader));
            lore.add("§7Członków: §f" + party.size());
            lore.add("§7Tryb: §f" + party.getSettings().getModePref().getDisplay());
            lore.add("§7Prywatność: §f" + party.getSettings().getPrivacy().getDisplay());
            lore.add("");
            lore.add("§aLPP: Dołącz");
            lore.add("§cPPM: Odrzuć");

            if (i >= slots.size()) break;
            inv.setItem(slots.get(i++), makeInboxInviteHead(leader, "§eZaproszenie od " + name(leader), lore, invt.partyId.toString(), page));
        }

        // pager
        inv.setItem(45, makeActionItem(Material.ARROW, "§e◀ Poprzednia", Collections.singletonList("§7Zmień stronę"), "INBOX_PAGE_PREV", null, page));
        inv.setItem(49, makeActionItem(Material.PAPER, "§7Strona: §f" + (page + 1) + "§7/§f" + (maxPage + 1),
                Arrays.asList("§7Kliknij aby zmienić stronę", "§7PPM w prawo, LPP wstecz"), "NOOP", null, page));
        inv.setItem(53, makeActionItem(Material.ARROW, "§eNastępna ▶", Collections.singletonList("§7Zmień stronę"), "INBOX_PAGE_NEXT", null, page));

        inv.setItem(48, makeActionItem(Material.BARRIER, "§cZamknij", Collections.singletonList("§7Zamknij"), "CLOSE", null, page));

        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
    }

    private void openSettingsInternal(Player viewer) {
        Party party = getPartyOf(viewer.getUniqueId());
        if (party == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, GUI_SETTINGS);

        fillBorder(inv);

        PartySettings s = party.getSettings();
        boolean leader = party.isLeader(viewer.getUniqueId());

        inv.setItem(11, makeActionItem(
                Material.COMPARATOR,
                "§eTryb: §f" + s.getModePref().getDisplay(),
                Arrays.asList(
                        "§7Ustaw preferowany tryb party",
                        "",
                        leader ? "§eKliknij aby zmienić" : "§cTylko lider"
                ),
                "SET_MODE",
                null,
                0
        ));

        inv.setItem(13, makeActionItem(
                Material.IRON_DOOR,
                "§ePrywatność: §f" + s.getPrivacy().getDisplay(),
                Arrays.asList(
                        "§7INVITE_ONLY - tylko zaproszenia",
                        "§7PUBLIC - inni mogą dołączyć (w przyszłości)",
                        "§7CLOSED - nic nie działa",
                        "",
                        leader ? "§eKliknij aby zmienić" : "§cTylko lider"
                ),
                "SET_PRIVACY",
                null,
                0
        ));

        inv.setItem(15, makeActionItem(
                Material.PLAYER_HEAD,
                "§eRandomy: §f" + (s.isAllowRandoms() ? "TAK" : "NIE"),
                Arrays.asList(
                        "§7Czy party pozwala dobierać randomów",
                        "§7gdy tryb ma większy skład",
                        "",
                        leader ? "§eKliknij aby przełączyć" : "§cTylko lider"
                ),
                "TOGGLE_RANDOMS",
                null,
                0
        ));

        inv.setItem(26, makeActionItem(Material.BARRIER, "§cPowrót", Collections.singletonList("§7Wróć do Party"), "BACK_MAIN", null, 0));

        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
    }

    // ======================
    // Events
    // ======================
    private boolean isModeAllowedForParty(PartyModePref mode, int partySize, PartySettings s) {
        if (mode == PartyModePref.AUTO) return true;

        int required = switch (mode) {
            case SOLO -> 1;
            case DUO  -> 2;
            case TRIO -> 3;
            case V4   -> 4;
            default -> 1;
        };

        // Najprościej: party nie może być większe niż team
        if (partySize > required) return false;

        // Opcjonalnie: jeśli kiedyś allowRandoms=true, to party mniejsze może wejść (dobierze randomów)
        // Jeśli chcesz SZTYWNO (bez randomów), odkomentuj to:
        // if (!s.isAllowRandoms() && partySize != required) return false;

        return true;
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = e.getView().getTitle();
        if (title == null) return;

        boolean isPartyGui = title.startsWith(GUI_MAIN)
                || title.startsWith(GUI_INVITE)
                || title.startsWith(GUI_INBOX)
                || title.startsWith(GUI_SETTINGS);

        if (!isPartyGui) return;

        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType().isAir()) return;

        ItemMeta meta = it.getItemMeta();
        if (meta == null) return;

        if (!meta.getPersistentDataContainer().has(KEY_ACTION, PersistentDataType.STRING)) return;
        String action = meta.getPersistentDataContainer().get(KEY_ACTION, PersistentDataType.STRING);

        String target = meta.getPersistentDataContainer().getOrDefault(KEY_TARGET, PersistentDataType.STRING, "");
        int page = meta.getPersistentDataContainer().getOrDefault(KEY_PAGE, PersistentDataType.INTEGER, 0);

        switch (action) {

            // ---- MAIN ----
            case "OPEN_MAIN" -> {
                openPartyMain(p);
            }
            case "OPEN_INVITE" -> {
                openInviteListInternal(p, 0);
            }
            case "OPEN_INBOX" -> {
                openInvitesInboxInternal(p, 0);
            }
            case "OPEN_SETTINGS" -> {
                Party party = getPartyOf(p.getUniqueId());
                if (party == null) return;
                if (!party.isLeader(p.getUniqueId())) {
                    p.sendMessage("§cTylko lider może otworzyć ustawienia.");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                openSettingsInternal(p);
            }
            case "LEAVE" -> {
                leaveParty(p.getUniqueId());
                // po wyjściu: twórz solo-party, żeby GUI dalej działało
                openPartyMain(p);
            }
            case "DISBAND" -> {
                Party party = getPartyOf(p.getUniqueId());
                if (party == null) return;
                if (!party.isLeader(p.getUniqueId())) return;
                disbandParty(party);
                openPartyMain(p); // od razu tworzy nowe solo-party
            }

            // ---- MATCHMAKING ----
            case "QUEUE_START" -> {
                Party party = getPartyOf(p.getUniqueId());
                if (party == null) {
                    p.sendMessage("§cNie masz party.");
                    return;
                }
                if (!party.isLeader(p.getUniqueId())) {
                    p.sendMessage("§cTylko lider może szukać gry.");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                startQueue(p, party);
                openPartyMain(p);
            }
            case "QUEUE_STOP" -> {
                Party party = getPartyOf(p.getUniqueId());
                if (party == null) return;
                if (!party.isLeader(p.getUniqueId())) return;
                stopQueue(p.getUniqueId(), true);
                openPartyMain(p);
            }

            // ---- MEMBER HEAD (kick) ----
            case "MEMBER" -> {
                Party party = getPartyOf(p.getUniqueId());
                if (party == null) return;

                // PPM = kick (lider)
                if (e.isRightClick() && party.isLeader(p.getUniqueId())) {
                    if (target == null || target.isEmpty()) return;
                    UUID u = safeUUID(target);
                    if (u == null) return;
                    kickMember(p.getUniqueId(), u);
                    openPartyMainInternal(p, party);
                } else {
                    p.sendMessage("§7Profil wkrótce :)");
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.3f);
                }
            }

            // ---- INVITE LIST ----
            case "INVITE" -> {
                Party party = getPartyOf(p.getUniqueId());
                if (party == null) return;
                if (!party.isLeader(p.getUniqueId())) {
                    p.sendMessage("§cTylko lider może zapraszać.");
                    return;
                }
                UUID t = safeUUID(target);
                if (t == null) return;

                Player tp = Bukkit.getPlayer(t);
                if (tp == null || !tp.isOnline()) {
                    p.sendMessage("§cTen gracz nie jest online.");
                    return;
                }
                if (!canBeInvited(t)) {
                    p.sendMessage("§cTen gracz jest już w party.");
                    return;
                }
                if (party.size() >= 4) {
                    p.sendMessage("§cParty jest pełne (max 4).");
                    return;
                }

                sendInvite(p.getUniqueId(), tp.getUniqueId(), party.getId());
                p.sendMessage("§aWysłano zaproszenie do §e" + tp.getName());
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

                // odśwież
                openInviteListInternal(p, page);
            }
            case "INV_PAGE_PREV" -> openInviteListInternal(p, Math.max(0, page - 1));
            case "INV_PAGE_NEXT" -> openInviteListInternal(p, page + 1);

            // ---- INBOX ----
            case "INBOX_INVITE" -> {
                UUID partyId = safeUUID(target);
                if (partyId == null) return;

                // LPP join / PPM reject
                if (e.isLeftClick()) {
                    acceptInvite(p, partyId);
                } else if (e.isRightClick()) {
                    rejectInvite(p, partyId);
                    p.sendMessage("§cOdrzucono zaproszenie.");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    openInvitesInboxInternal(p, page);
                }
            }
            case "INBOX_PAGE_PREV" -> openInvitesInboxInternal(p, Math.max(0, page - 1));
            case "INBOX_PAGE_NEXT" -> openInvitesInboxInternal(p, page + 1);

            // ---- SETTINGS ----
            case "SET_MODE" -> {
                Party party = getPartyOf(p.getUniqueId());
                if (party == null) return;
                if (!party.isLeader(p.getUniqueId())) return;

                int size = party.size();
                PartySettings s = party.getSettings();

                PartyModePref cur = s.getModePref();
                PartyModePref next = cur;

                // spróbuj max 10 razy przeskoczyć po enumie aż znajdziesz dozwolony
                for (int i = 0; i < 10; i++) {
                    next = next.next();
                    if (isModeAllowedForParty(next, size, s)) break;
                }

                // jeśli nawet po skoku nie pasuje (teoretycznie nie powinno), zostaw auto
                if (!isModeAllowedForParty(next, size, s)) {
                    next = PartyModePref.AUTO;
                }

                s.setModePref(next);
                broadcast(party, "§dTryb party ustawiony na: §f" + next.getDisplay());
                openSettingsInternal(p);
            }
            case "SET_PRIVACY" -> {
                Party party = getPartyOf(p.getUniqueId());
                if (party == null) return;
                if (!party.isLeader(p.getUniqueId())) return;

                PartyPrivacy next = party.getSettings().getPrivacy().next();
                party.getSettings().setPrivacy(next);
                broadcast(party, "§dPrywatność party: §f" + next.getDisplay());
                openSettingsInternal(p);
            }
            case "TOGGLE_RANDOMS" -> {
                Party party = getPartyOf(p.getUniqueId());
                if (party == null) return;
                if (!party.isLeader(p.getUniqueId())) return;

                boolean v = !party.getSettings().isAllowRandoms();
                party.getSettings().setAllowRandoms(v);
                broadcast(party, "§dRandomy: §f" + (v ? "TAK" : "NIE"));
                openSettingsInternal(p);
            }

            // ---- common ----
            case "BACK_MAIN" -> openPartyMain(p);
            case "CLOSE" -> p.closeInventory();
            case "NOOP" -> {
                // nic
            }
            default -> {
                // unknown
            }
        }
    }

    /**
     * ✅ Party-join do areny:
     * Gdy lider kliknie arenę w GUI "BedWars - Areny", dobieramy resztę party do tej samej areny.
     * <p>
     * UWAGA: Nie blokujemy standardowego join lidera (to robi ArenaManager), tylko dokładamy resztę.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onArenaGuiClickPartyJoin(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player leader)) return;

        String title = e.getView().getTitle();
        if (title == null) return;

        if (!ChatColor.stripColor(title).equalsIgnoreCase("BedWars - Areny")) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String arenaName = ChatColor.stripColor(meta.getDisplayName());
        if (arenaName == null || arenaName.isEmpty()) return;

        if (plugin.getArenaManager() == null) return;
        Arena arena = plugin.getArenaManager().getArena(arenaName);
        if (arena == null) return;

        Party party = getPartyOf(leader.getUniqueId());
        if (party == null) return;
        if (!party.isLeader(leader.getUniqueId())) return;
        if (party.size() <= 1) return;

        List<Player> others = party.getMembers().stream()
                .filter(u -> !u.equals(leader.getUniqueId()))
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .collect(Collectors.toList());

        if (others.isEmpty()) return;

        // pre-check: czy istnieje team z miejscem dla CAŁEGO party
        int partySize = party.size();
        String team = findFreeTeamForParty(arena, partySize);
        if (team == null) {
            leader.sendMessage("§cBrak drużyny z miejscem dla całego party.");
            leader.sendMessage("§7Party musi wejść do jednej drużyny.");
            leader.playSound(leader.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        PartyJoinBlock block = findPartyJoinBlock(party);
        if (block != null) {
            // jeśli blokujący to ktoś już na arenie != ta arena, to i tak jest problem
            leader.sendMessage("§cNie można dołączyć, bo ktoś z party jest już na arenie.");
            leader.sendMessage("§7Gracz §e" + name(block.member) + " §7jest na mapie: §b" + block.arenaName);
            leader.playSound(leader.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // pre-check ranked
        if (arena.isRanked()) {
            RankedSystem rs = plugin.getRankedSystem();
            if (rs == null) {
                leader.sendMessage("§cRanked jest wyłączony.");
                return;
            }

            String req = arena.getRankedMin();
            List<Player> allToCheck = new ArrayList<>();
            allToCheck.add(leader);
            allToCheck.addAll(others);

            for (Player pl : allToCheck) {
                if (!rs.hasAtLeastRank(pl.getUniqueId(), req)) {
                    leader.sendMessage("§cNie każdy spełnia wymagania Ranked na tej arenie.");
                    leader.sendMessage("§7Gracz §e" + pl.getName() + " §7nie ma wymaganej rangi: §b" + prettyReq(req));
                    leader.playSound(leader.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
            }
        }

        // join reszty party
        for (Player m : others) {
            try {
                plugin.getArenaManager().joinArena(m, arena);
            } catch (Throwable t) {
                leader.sendMessage("§cNie udało się dołączyć gracza §e" + m.getName() + "§c do areny.");
                t.printStackTrace();
                return; // STOP: nie robimy team-assign jak join nie wyszedł
            }
        }

        // po 2 tickach przypisz CAŁE party do jednego teamu (zawsze, nie tylko w catch)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String targetTeam = findFreeTeamForParty(arena, party.size());
            if (targetTeam == null) {
                leader.sendMessage("§cNie udało się przypisać drużyny party.");
                return;
            }

            boolean ok = moveWholePartyToTeam(party, arena, targetTeam);
            if (!ok) {
                leader.sendMessage("§cNie udało się przenieść party do jednej drużyny.");
                return;
            }

            broadcast(party, "§aParty dołączyło do drużyny §e" + targetTeam);
        }, 2L);

        leader.sendMessage("§aDołączono party do areny: §e" + arena.getName());
        leader.playSound(leader.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.15f);
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent e) {
        // nic wymagane – system bez timerów
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Party party = getPartyOf(p.getUniqueId());
        if (party == null) return;

        // jeśli jest liderem i szuka -> stop
        if (party.isLeader(p.getUniqueId())) {
            stopQueue(p.getUniqueId(), false);
        }

        // jeśli solo-party to pomijamy
        if (party.size() <= 1) {
            leaveParty(p.getUniqueId());
            return;
        }

        // leave (wywoła transfer lidera jeśli trzeba)
        leaveParty(p.getUniqueId());
    }

    // ======================
    // MATCHMAKING / QUEUE
    // ======================

    private boolean isInQueue(UUID leader) {
        return leader != null && partyQueueTasks.containsKey(leader);
    }

    private int getQueueElapsedSeconds(UUID leader) {
        Long start = partyQueueStartAt.get(leader);
        if (start == null) return 0;
        long now = System.currentTimeMillis();
        long ms = Math.max(0, now - start);
        return (int) Math.min(30, ms / 1000L);
    }

    private void startQueue(Player leader, Party party) {
        if (leader == null || party == null) return;

        if (isInQueue(leader.getUniqueId())) {
            leader.sendMessage("§eJuż szukasz areny.");
            return;
        }

        // check: wszyscy online
        List<Player> membersOnline = party.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .collect(Collectors.toList());

        if (membersOnline.size() != party.size()) {
            leader.sendMessage("§cNie wszyscy członkowie party są online.");
            leader.sendMessage("§7Aby szukać gry, cała ekipa musi być na serwerze.");
            leader.playSound(leader.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        PartyJoinBlock block = findPartyJoinBlock(party);
        if (block != null) {
            String nick = name(block.member);

            leader.sendMessage("§cNie można szukać gry, bo ktoś z party jest już na arenie.");
            leader.sendMessage("§7Gracz §e" + nick + " §7jest na mapie: §b" + block.arenaName);
            leader.playSound(leader.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // privacy check (opcjonalnie)
        if (party.getSettings().getPrivacy() == PartyPrivacy.CLOSED) {
            leader.sendMessage("§cTwoje party jest ustawione na CLOSED.");
            leader.playSound(leader.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int durationSeconds = 30;
        long now = System.currentTimeMillis();
        partyQueueStartAt.put(leader.getUniqueId(), now);
        partyQueueEndAt.put(leader.getUniqueId(), now + (durationSeconds * 1000L));
        partyQueueMode.put(leader.getUniqueId(), party.getSettings().getModePref().name());

        broadcast(party, "§a🔎 Rozpoczynam szukanie dostępnych aren dla was! §7(0/" + durationSeconds + "s)");
        leader.playSound(leader.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);

        BukkitRunnable task = new BukkitRunnable() {
            int lastSecondAnnounced = -1;

            @Override
            public void run() {
                Player leaderNow = Bukkit.getPlayer(party.getLeader());
                if (leaderNow == null || !leaderNow.isOnline()) {
                    stopQueue(party.getLeader(), false);
                    cancel();
                    return;
                }

                Party partyNow = getPartyOf(party.getLeader());
                if (partyNow == null || !partyNow.isLeader(party.getLeader())) {
                    stopQueue(party.getLeader(), false);
                    cancel();
                    return;
                }

                long endAt = partyQueueEndAt.getOrDefault(party.getLeader(), 0L);
                long startAt = partyQueueStartAt.getOrDefault(party.getLeader(), System.currentTimeMillis());
                long nowMs = System.currentTimeMillis();

                int elapsed = (int) Math.max(0, Math.min(durationSeconds, (nowMs - startAt) / 1000L));
                int remaining = (int) Math.max(0, (endAt - nowMs) / 1000L);

                // ActionBar: 0/30
                sendActionBar(leaderNow, "§aSzukanie aren dla was: §e" + elapsed + "§7/§e" + durationSeconds + "s");

                // co 5 sekund info do chatu (ładnie, nie spam)
                if (elapsed != lastSecondAnnounced && (elapsed == 0 || elapsed == 5 || elapsed == 10 || elapsed == 15 || elapsed == 20 || elapsed == 25)) {
                    lastSecondAnnounced = elapsed;
                    leaderNow.sendMessage("§7🔎 Szukanie dostępnych aren: §e" + elapsed + "§7/§e" + durationSeconds + "s");
                }

                // timeout
                if (nowMs >= endAt) {
                    broadcast(partyNow, "§c❌ Nie znaleziono areny w czasie §e" + durationSeconds + "s§c. Spróbuj ponownie.");
                    leaderNow.playSound(leaderNow.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    stopQueue(partyNow.getLeader(), false);
                    cancel();
                    return;
                }

                // próbuj znaleźć arenę
                Arena found = findBestArenaForParty(leaderNow, partyNow);

                if (found != null) {
                    // join całej party
                    boolean ok = joinWholePartyToArena(leaderNow, partyNow, found);
                    if (ok) {
                        broadcast(partyNow, "§a✅ Znaleziono arenę: §e" + found.getName() + "§a! Dołączamy...");
                        leaderNow.playSound(leaderNow.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                        stopQueue(partyNow.getLeader(), false);
                        cancel();
                    }
                }
            }
        };

        // co 1s (20 ticków) dla płynnego 0/30
        task.runTaskTimer(plugin, 0L, 20L);
        partyQueueTasks.put(leader.getUniqueId(), task);
    }

    private void stopQueue(UUID leader, boolean showMsg) {
        if (leader == null) return;

        BukkitRunnable t = partyQueueTasks.remove(leader);
        if (t != null) {
            try {
                t.cancel();
            } catch (Exception ignored) {
            }
        }
        partyQueueEndAt.remove(leader);
        partyQueueStartAt.remove(leader);
        partyQueueMode.remove(leader);

        if (showMsg) {
            Player pl = Bukkit.getPlayer(leader);
            Party party = getPartyOf(leader);
            if (pl != null && pl.isOnline()) {
                pl.sendMessage("§eAnulowano szukanie areny.");
                pl.playSound(pl.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.9f);
                sendActionBar(pl, "§eSzukanie anulowane.");
            }
            if (party != null) {
                broadcast(party, "§e⏹ Szukanie areny zostało anulowane.");
            }
        }
    }

    private void sendActionBar(Player p, String msg) {
        try {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(msg));
        } catch (Throwable ignored) {
            // jakby stara wersja spigota -> pomijamy
        }
    }

    private boolean joinWholePartyToArena(Player leader, Party party, Arena arena) {
        if (plugin.getArenaManager() == null) return false;

        List<Player> members = party.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .collect(Collectors.toList());

        if (members.isEmpty()) return false;

        // pre-check miejsc (żeby nie było pół-join)
        int free = arena.getMaxPlayers() - arena.getPlayersInArena().size();
        if (free < members.size()) {
            leader.sendMessage("§cArena nagle się zapełniła. Wolne: §e" + free + "§c, potrzebne: §e" + members.size());
            return false;
        }

        // pre-check ranked (pełny)
        if (arena.isRanked()) {
            RankedSystem rs = plugin.getRankedSystem();
            if (rs == null) return false;
            String req = arena.getRankedMin();
            for (Player pl : members) {
                if (!rs.hasAtLeastRank(pl.getUniqueId(), req)) {
                    leader.sendMessage("§cPrzykro mi, szukam dalej — ktoś z twojej Party ma za niską rangę dla tej mapy.");
                    leader.sendMessage("§7Gracz §e" + pl.getName() + " §7nie spełnia wymagań: §b" + prettyReq(req));
                    return false;
                }
            }
        }

        // join (jak coś walnie w trakcie, spróbuj rollback)
        List<Player> joined = new ArrayList<>();
        for (Player pl : members) {
            try {
                plugin.getArenaManager().joinArena(pl, arena);

                joined.add(pl);
            } catch (Throwable ex) {
                // rollback: wyrzuć tych co weszli (na razie tylko komunikat; jeśli masz metodę leave/teleport, tu ją zawołaj)
                leader.sendMessage("§cNie udało się dołączyć całego party. Próbuję szukać dalej...");
                ex.printStackTrace();
                return false;
            }
        }
        return true;
    }

    /**
     * Szuka najlepszej areny dla party.
     * - bierze pod uwagę NORMAL i RANKED
     * - jeśli ranked i ktoś nie ma rangi: leader dostaje info i arena jest pomijana
     */
    private Arena findBestArenaForParty(Player leader, Party party) {
        if (party == null || leader == null) return null;
        if (plugin.getArenaManager() == null) return null;

        List<Player> members = party.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .collect(Collectors.toList());

        if (members.size() != party.size()) return null;

        int partySize = party.size();
        PartyModePref pref = party.getSettings().getModePref();

        List<Arena> candidates = new ArrayList<>(plugin.getArenaManager().getArenas());
        if (candidates.isEmpty()) return null;

        List<Arena> filtered = new ArrayList<>();
        for (Arena a : candidates) {
            if (a == null) continue;
            if (!a.isEnabled()) continue;
            if (a.isInGame()) continue;
            // możesz dopuścić countingDown = true, jeśli chcesz, ale tu zostawiamy ok
            // status mapy
            String st = plugin.getMapResetManager() != null ? plugin.getMapResetManager().getArenaStatus(a.getName()) : null;
            if (st == null) st = "Gotowa";
            if ("Badanie terenu".equalsIgnoreCase(st)) continue;
            if ("Restart".equalsIgnoreCase(st)) continue;

            int free = a.getMaxPlayers() - a.getPlayersInArena().size();
            if (free < partySize) continue;

            if (!modeMatches(pref, partySize, a)) continue;

            // ranked check (ale nie blokujemy wyszukiwania — jeśli ktoś nie ma rangi, pomijamy tylko tę arenę i INFO)
            if (a.isRanked()) {
                RankedSystem rs = plugin.getRankedSystem();
                if (rs == null) continue;

                String req = a.getRankedMin();
                Player bad = null;
                for (Player pl : members) {
                    if (!rs.hasAtLeastRank(pl.getUniqueId(), req)) {
                        bad = pl;
                        break;
                    }
                }
                if (bad != null) {
                    leader.sendMessage("§cPrzykro mi, szukam dalej — ktoś z twojej Party ma za niską rangę dla tej mapy.");
                    leader.sendMessage("§7Mapa §e" + a.getName() + "§7 wymaga: §b" + prettyReq(req) + " §7(a gracz §e" + bad.getName() + "§7 nie ma)");
                    continue;
                }
            }

            filtered.add(a);
        }

        if (filtered.isEmpty()) return null;

        // sort: countingDown first, potem alive desc, potem nazwa
        filtered.sort((a1, a2) -> {
            int cd1 = a1.isCountingDown() ? 1 : 0;
            int cd2 = a2.isCountingDown() ? 1 : 0;
            if (cd1 != cd2) return Integer.compare(cd2, cd1);

            int alive1 = countAliveApprox(a1);
            int alive2 = countAliveApprox(a2);
            if (alive1 != alive2) return Integer.compare(alive2, alive1);

            return String.CASE_INSENSITIVE_ORDER.compare(a1.getName(), a2.getName());
        });

        return filtered.get(0);
    }

    private int countAliveApprox(Arena a) {
        try {
            int total = a.getPlayersInArena().size();
            int elim = a.getEliminated().size();
            int alive = total - elim;
            return Math.max(0, alive);
        } catch (Throwable t) {
            return a.getPlayersInArena().size();
        }
    }
    /**
     * Dopasowanie trybu:
     * - AUTO:
     *    partySize=1 -> SOLO
     *    partySize=2 -> DUO
     *    partySize=3 -> TRIO
     *    partySize=4 -> 4v4
     *
     * - SOLO/DUO/TRIO/4v4:
     *    szukamy aren dokładnie w tym trybie (playersPerTeam)
     *
     * UWAGA:
     * - jeśli w przyszłości chcesz allowRandoms, możesz dopuścić np. TRIO dla party=2 itd.
     *   Na razie jest SZTYWNO (najczyściej i bez randomów).
     */
    private boolean modeMatches(PartyModePref pref, int partySize, Arena arena) {
        if (arena == null) return false;

        PartyModePref effective = pref;

        if (pref == PartyModePref.AUTO) {
            if (partySize <= 1) effective = PartyModePref.SOLO;
            else if (partySize == 2) effective = PartyModePref.DUO;
            else if (partySize == 3) effective = PartyModePref.TRIO;
            else effective = PartyModePref.V4;
        }

        int perTeam = getPlayersPerTeamSafe(arena);
        if (perTeam <= 0) return false;

        return switch (effective) {
            case SOLO -> perTeam == 1;
            case DUO  -> perTeam == 2;
            case TRIO -> perTeam == 3;
            case V4   -> perTeam == 4;
            case AUTO -> true; // safety (AUTO mapujemy wyżej)
        };
    }

    /**
     * Bezpieczne pobranie playersPerTeam z areny.
     * Dostosuj jeśli Twoje API nazywa się inaczej.
     */
    private int getPlayersPerTeamSafe(Arena arena) {
        try {
            // Najczęstszy wariant:
            // return arena.getArenaMode().getPlayersPerTeam();

            // U Ciebie NA SCREENIE masz "ArenaMode" w paczce org.BedWars.arena,
            // więc prawdopodobnie jest: arena.getArenaMode()
            // i tam jest liczba perTeam.
            // ZROBIMY 3 próby w kolejności (żeby pasowało do Twojego API):

            // 1) getArenaMode().getPlayersPerTeam()
            try {
                Object mode = arena.getArenaMode();
                if (mode != null) {
                    try {
                        return (int) mode.getClass().getMethod("getPlayersPerTeam").invoke(mode);
                    } catch (Throwable ignored) {}
                    try {
                        return (int) mode.getClass().getMethod("getPerTeam").invoke(mode);
                    } catch (Throwable ignored) {}
                    try {
                        return (int) mode.getClass().getMethod("getTeamSize").invoke(mode);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            // 2) arena.getPlayersPerTeam()
            try {
                return (int) arena.getClass().getMethod("getPlayersPerTeam").invoke(arena);
            } catch (Throwable ignored) {}

            // 3) arena.getMode() -> coś
            try {
                Object mode2 = arena.getClass().getMethod("getMode").invoke(arena);
                if (mode2 != null) {
                    try {
                        return (int) mode2.getClass().getMethod("getPlayersPerTeam").invoke(mode2);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            return -1;
        } catch (Throwable t) {
            return -1;
        }
    }
    // ======================
// Item builders (PDC)
// ======================

    private ItemStack makeActionItem(Material mat, String name, List<String> lore, String action, String target, int page) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;

        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);

        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, "1");
        meta.getPersistentDataContainer().set(KEY_ACTION, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(KEY_TARGET, PersistentDataType.STRING, target == null ? "" : target);
        meta.getPersistentDataContainer().set(KEY_PAGE, PersistentDataType.INTEGER, page);

        it.setItemMeta(meta);
        return it;
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack makePlayerHead(UUID who, String name, List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        OfflinePlayer op = Bukkit.getOfflinePlayer(who);
        meta.setOwningPlayer(op);
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);

        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack makeMemberHeadWithAction(UUID who, String displayName, List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        meta.setOwningPlayer(Bukkit.getOfflinePlayer(who));
        meta.setDisplayName(displayName);
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, "1");
        meta.getPersistentDataContainer().set(KEY_ACTION, PersistentDataType.STRING, "MEMBER");
        meta.getPersistentDataContainer().set(KEY_TARGET, PersistentDataType.STRING, who.toString());
        meta.getPersistentDataContainer().set(KEY_PAGE, PersistentDataType.INTEGER, 0);

        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack makeActionHead(UUID who, String name, List<String> lore, String action, String target, int page) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        meta.setOwningPlayer(Bukkit.getOfflinePlayer(who));
        meta.setDisplayName(name);
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, "1");
        meta.getPersistentDataContainer().set(KEY_ACTION, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(KEY_TARGET, PersistentDataType.STRING, target == null ? "" : target);
        meta.getPersistentDataContainer().set(KEY_PAGE, PersistentDataType.INTEGER, page);

        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack makeInboxInviteHead(UUID leader, String name, List<String> lore, String partyId, int page) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        meta.setOwningPlayer(Bukkit.getOfflinePlayer(leader));
        meta.setDisplayName(name);
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, "1");
        meta.getPersistentDataContainer().set(KEY_ACTION, PersistentDataType.STRING, "INBOX_INVITE");
        meta.getPersistentDataContainer().set(KEY_TARGET, PersistentDataType.STRING, partyId);
        meta.getPersistentDataContainer().set(KEY_PAGE, PersistentDataType.INTEGER, page);

        skull.setItemMeta(meta);
        return skull;
    }

// ======================
// GUI helpers
// ======================

    private void fillBorder(Inventory inv) {
        ItemStack border = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        int size = inv.getSize();
        int rows = size / 9;
        for (int i = 0; i < size; i++) {
            int r = i / 9;
            int c = i % 9;
            boolean isBorder = (r == 0 || r == rows - 1 || c == 0 || c == 8);
            if (isBorder) inv.setItem(i, border);
        }
    }

    private List<Integer> centerSlots54() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + col);
            }
        }
        return slots;
    }

    private UUID safeUUID(String s) {
        try {
            return UUID.fromString(s);
        } catch (Exception ignored) {
            return null;
        }
    }

// ======================
// Invite logic
// ======================

    private void sendInvite(UUID fromLeader, UUID targetPlayer, UUID partyId) {
        invites.computeIfAbsent(targetPlayer, k -> new LinkedHashSet<>()).add(new Invite(fromLeader, partyId));

        Player t = Bukkit.getPlayer(targetPlayer);
        if (t != null && t.isOnline()) {
            t.sendMessage("§aMasz zaproszenie do party od §e" + name(fromLeader));
            t.sendMessage("§eOtwórz GUI Party → §6Zaproszenia");
            t.playSound(t.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
        }
    }

    private void acceptInvite(Player p, UUID partyId) {
        // jeśli już ma party -> blok
        if (hasParty(p.getUniqueId())) {
            // pozwolimy: jeśli jest solo-party (1 osoba) -> opuść i dołącz.
            Party current = getPartyOf(p.getUniqueId());
            if (current != null && current.size() == 1 && current.getLeader().equals(p.getUniqueId())) {
                disbandParty(current); // usuń solo party
            } else {
                p.sendMessage("§cMasz już party.");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
        }

        Party party = parties.get(partyId);
        if (party == null) {
            p.sendMessage("§cTo zaproszenie jest nieaktualne.");
            rejectInvite(p, partyId);
            openInvitesInboxInternal(p, 0);
            return;
        }

        // privacy check (jak CLOSED to nie wpuszczamy)
        if (party.getSettings().getPrivacy() == PartyPrivacy.CLOSED) {
            p.sendMessage("§cTo party jest zamknięte.");
            rejectInvite(p, partyId);
            return;
        }

        if (party.size() >= 4) {
            p.sendMessage("§cParty jest pełne.");
            rejectInvite(p, partyId);
            return;
        }

        // dołącz
        party.getMembers().add(p.getUniqueId());
        partyByMember.put(p.getUniqueId(), party.getId());

        // usuń invite do tej party
        rejectInvite(p, partyId);

        broadcast(party, "§a" + p.getName() + " dołączył do party.");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);

        openPartyMainInternal(p, party);
    }

    private void rejectInvite(Player p, UUID partyId) {
        LinkedHashSet<Invite> set = invites.get(p.getUniqueId());
        if (set == null) return;
        set.removeIf(inv -> inv.partyId.equals(partyId));
        if (set.isEmpty()) invites.remove(p.getUniqueId());
    }

// ======================
// Helpers pod Ranked / format
// ======================

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
    // ======================
// PARTY -> TEAM LOGIC
// ======================

    // ======================
// PARTY -> TEAM (ARENA API)
// ======================

    /**
     * Znajduje drużynę, która pomieści CAŁE party
     */
    private String findFreeTeamForParty(Arena arena, int partySize) {
        int perTeam = arena.getArenaMode().getPlayersPerTeam();

        for (Team team : arena.getTeams()) {
            int count = arena.countTeam(team, plugin.getPlayerTeam());
            int free = perTeam - count;
            if (free >= partySize) {
                return team.getId(); // np. RED / BLUE
            }
        }
        return null;
    }
    private boolean isPlayerInArena(Arena arena, UUID u, Player p) {
        try { if (arena.getPlayersInArena().contains(u)) return true; } catch (Throwable ignored) {}
        try { if (arena.getPlayersInArena().contains(p)) return true; } catch (Throwable ignored) {}
        return false;
    }
    /**
     * Przenosi CAŁE party do jednej drużyny
     */
    private boolean moveWholePartyToTeam(Party party, Arena arena, String teamId) {
        if (party == null || arena == null || teamId == null) return false;

        Team team = arena.getTeam(teamId);
        if (team == null) return false;

        Map<UUID, Team> map = getPlayerTeamMap(arena);
        int perTeam = arena.getArenaMode().getPlayersPerTeam();

        List<Player> members = party.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .filter(pl -> isPlayerInArena(arena, pl.getUniqueId(), pl))
                .toList();

        if (members.isEmpty()) return false;

        // policz ilu trzeba faktycznie przenieść (nie liczymy tych którzy już są w teamie)
        long needMove = members.stream()
                .filter(pl -> {
                    Team cur = map.get(pl.getUniqueId());
                    return cur == null || !cur.getId().equalsIgnoreCase(teamId);
                })
                .count();

        int current = arena.countTeam(team, map);
        int free = perTeam - current;

        if (free < needMove) return false;

        for (Player p : members) {
            setPlayerTeam(arena, p, team);
        }
        return true;
    }


    /**
     * Ustawia gracza do drużyny (REALNA LOGIKA)
     */
    private void setPlayerTeam(Arena arena, Player player, Team team) {
        Map<UUID, Team> map = getPlayerTeamMap(arena);

        map.put(player.getUniqueId(), team);
        team.setEverHadPlayer(true);

        // teleport na spawn teamu (jeśli istnieje)
        if (!team.getSpawns().isEmpty()) {
            Location spawn = team.getSpawns().get(0);
            if (spawn != null) {
                player.teleport(spawn);
            }
        }
    }
// --- kompatybilność pod BedWarsPlugin ---
// Jeśli masz już własne nazwy metod, tutaj tylko deleguj.

    public Party getParty(Player p) {
        return getParty(p.getUniqueId());
    }

    // jeśli masz np. Map<UUID, Party> parties;
    public Party getParty(UUID playerUuid) {
        return getPartyOf(playerUuid);
    }
    // partyId -> (arenaName -> teamId)
    private final Map<UUID, Map<String, String>> partySelectedTeam = new ConcurrentHashMap<>();

    private void setPartyTeamSelection(Party party, Arena arena, String teamId) {
        partySelectedTeam
                .computeIfAbsent(party.getId(), k -> new ConcurrentHashMap<>())
                .put(arena.getName(), teamId);
    }

    private String getPartyTeamSelection(Party party, Arena arena) {
        Map<String, String> m = partySelectedTeam.get(party.getId());
        return (m == null) ? null : m.get(arena.getName());
    }

    private void clearPartySelectionForArena(Party party, Arena arena) {
        Map<String, String> m = partySelectedTeam.get(party.getId());
        if (m != null) {
            m.remove(arena.getName());
            if (m.isEmpty()) partySelectedTeam.remove(party.getId());
        }
    }
    private boolean canMovePartyToTeam(Party party, Arena arena, Team team) {
        if (party == null || arena == null || team == null) return false;

        int perTeam = arena.getArenaMode().getPlayersPerTeam();
        int partySize = (int) party.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .filter(pl -> arena.getPlayersInArena().contains(pl)/* jeśli masz UUID listę */)
                .count();

        if (partySize <= 0) return false;

        // party nie może być większe niż team
        if (partySize > perTeam) return false;

        // ile osób już jest w teamie
        int current = arena.countTeam(team, getPlayerTeamMap(arena));
        int free = perTeam - current;

        // UWAGA: jeśli część party już jest w tym teamie, możesz policzyć “do przeniesienia” dokładniej.
        // wersja prosta: wymagaj pełnej wolnej puli
        return free >= partySize;
    }
    private void enforcePartyTeamNow(Arena arena, Party party) {
        if (arena == null || party == null) return;

        // wybór teamu jeśli był
        String selected = getPartyTeamSelection(party, arena);
        String teamId = selected;

        int partySize = party.size();
        if (teamId == null) {
            teamId = findFreeTeamForParty(arena, partySize);
            if (teamId != null) setPartyTeamSelection(party, arena, teamId);
        }

        if (teamId == null) return;

        boolean ok = moveWholePartyToTeam(party, arena, teamId);
        if (!ok) {
            // jeśli wybrany team nie ma miejsca, spróbuj znaleźć inny
            String alt = findFreeTeamForParty(arena, partySize);
            if (alt != null && !alt.equals(teamId)) {
                if (moveWholePartyToTeam(party, arena, alt)) {
                    setPartyTeamSelection(party, arena, alt);
                }
            }
        }
    }
    private boolean hasAtLeastTwoTeamsWithPlayers(Arena arena) {
        Map<UUID, Team> map = getPlayerTeamMap(arena);

        Set<String> nonEmptyTeams = new HashSet<>();
        for (UUID u : arena.getPlayersInArena()) {
            Team t = map.get(u);
            if (t != null) nonEmptyTeams.add(t.getId());
        }
        return nonEmptyTeams.size() >= 2;
    }
    private boolean canArenaStart(Arena arena) {
        // minPlayers też warto
        int players = arena.getPlayersInArena().size();
        if (players < 2) return false;

        return hasAtLeastTwoTeamsWithPlayers(arena);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTeamSelectGui(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = ChatColor.stripColor(e.getView().getTitle());
        if (title == null) return;

        // <-- DOPASUJ do Twojego GUI teamów
        if (!title.equalsIgnoreCase("Wybór drużyny")) return;

        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType().isAir()) return;

        e.setCancelled(true);

        String teamId = extractTeamIdFromItem(it);
        if (teamId == null) return;

        Party party = getPartyOf(p.getUniqueId());
        if (party == null || !party.isLeader(p.getUniqueId())) {
            p.sendMessage("§cTylko lider party może zmieniać drużynę.");
            return;
        }

        Arena arena = getArenaOfPlayer(p);
        if (arena == null) {
            p.sendMessage("§cNie jesteś na arenie.");
            return;
        }

        if (arena.isInGame()) { // jeśli nie chcesz zmian po starcie
            p.sendMessage("§cNie można zmieniać drużyny po starcie.");
            return;
        }

        Team team = arena.getTeam(teamId);
        if (team == null) {
            p.sendMessage("§cNieznana drużyna: §e" + teamId);
            return;
        }

        // wszyscy online i w arenie
        List<Player> members = party.getMembers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .filter(pl -> isPlayerInArena(arena, pl.getUniqueId(), pl))
                .toList();

        if (members.size() != party.size()) {
            p.sendMessage("§cNie wszyscy członkowie party są online / na arenie.");
            return;
        }

        if (!canMovePartyToTeam(party, arena, team)) {
            p.sendMessage("§cBrak miejsca w tej drużynie dla całego party.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        setPartyTeamSelection(party, arena, teamId);

        boolean ok = moveWholePartyToTeam(party, arena, teamId);
        if (ok) {
            broadcast(party, "§aLider zmienił drużynę party na §e" + teamId);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        } else {
            p.sendMessage("§cNie udało się przenieść party.");
        }
    }

    private String extractTeamIdFromItem(ItemStack it) {
        if (it == null) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return null;

        // 1) PDC (pewne)
        String teamId = meta.getPersistentDataContainer().get(KEY_TEAM_ID, PersistentDataType.STRING);
        if (teamId != null && !teamId.isEmpty()) return teamId.toUpperCase(Locale.ROOT);

        // 2) fallback: nazwa itemu
        if (!meta.hasDisplayName()) return null;
        String dn = ChatColor.stripColor(meta.getDisplayName());
        if (dn == null) return null;

        // przykłady: "RED", "Team: RED", "Drużyna: RED"
        dn = dn.trim().toUpperCase(Locale.ROOT);
        if (dn.contains(":")) dn = dn.substring(dn.lastIndexOf(':') + 1).trim();

        // jeśli masz w teamach ID typu RED/BLUE itd.
        return dn.isEmpty() ? null : dn;
    }

    private Arena getArenaOfPlayer(Player p) {
        if (plugin.getArenaManager() == null) return null;

        UUID u = p.getUniqueId();
        for (Arena a : plugin.getArenaManager().getArenas()) {
            if (a == null) continue;

            // zakładam, że getPlayersInArena() to kolekcja UUID
            try {
                if (a.getPlayersInArena().contains(u)) return a;
            } catch (Throwable ignored) {
            }

            // jeśli jednak trzyma Player-y, to backup:
            try {
                if (a.getPlayersInArena().contains(p)) return a;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * Dostęp do prywatnego playerTeam (BEZ REFLECTION)
     */
    @SuppressWarnings("unchecked")
    private Map<UUID, Team> getPlayerTeamMap(Arena arena) {
        try {
            var f = Arena.class.getDeclaredField("playerTeam");
            f.setAccessible(true);
            return (Map<UUID, Team>) f.get(arena);
        } catch (Exception e) {
            throw new IllegalStateException("Nie mogę pobrać playerTeam z Arena", e);
        }
    }
}