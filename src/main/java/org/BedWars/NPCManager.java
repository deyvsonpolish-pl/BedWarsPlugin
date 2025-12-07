//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.BedWars;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public class NPCManager implements Listener {
    private final JavaPlugin plugin;
    private Location npcLocation;

    public NPCManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void spawnArenaNPC(Location loc) {
        for(Entity e : loc.getWorld().getNearbyEntities(loc, (double)2.0F, (double)3.0F, (double)2.0F)) {
            if (e.hasMetadata("arenaNPC")) {
                e.remove();
            }
        }

        Villager npc = (Villager)loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        npc.setAI(false);
        npc.setInvulnerable(true);
        npc.setCustomName(String.valueOf(ChatColor.YELLOW) + "Wybór Areny");
        npc.setCustomNameVisible(true);
        npc.setProfession(Profession.NONE);
        npc.setMetadata("arenaNPC", new FixedMetadataValue(this.plugin, true));
        this.npcLocation = loc;
        this.save();
    }

    private void save() {
        this.plugin.getConfig().set("npc.world", this.npcLocation.getWorld().getName());
        this.plugin.getConfig().set("npc.x", this.npcLocation.getX());
        this.plugin.getConfig().set("npc.y", this.npcLocation.getY());
        this.plugin.getConfig().set("npc.z", this.npcLocation.getZ());
        this.plugin.getConfig().set("npc.yaw", this.npcLocation.getYaw());
        this.plugin.getConfig().set("npc.pitch", this.npcLocation.getPitch());
        this.plugin.saveConfig();
    }

    public void loadNPC() {
        if (this.plugin.getConfig().contains("npc.world")) {
            World w = Bukkit.getWorld(this.plugin.getConfig().getString("npc.world"));
            double x = this.plugin.getConfig().getDouble("npc.x");
            double y = this.plugin.getConfig().getDouble("npc.y");
            double z = this.plugin.getConfig().getDouble("npc.z");
            float yaw = (float)this.plugin.getConfig().getDouble("npc.yaw");
            float pitch = (float)this.plugin.getConfig().getDouble("npc.pitch");
            Location loc = new Location(w, x, y, z, yaw, pitch);
            this.spawnArenaNPC(loc);
        }
    }

    @EventHandler
    public void onNPCRightClick(PlayerInteractEntityEvent e) {
        Entity en = e.getRightClicked();
        if (en instanceof Villager) {
            if (en.hasMetadata("arenaNPC")) {
                e.setCancelled(true);
                this.openNPCGUI(e.getPlayer());
            }
        }
    }

    @EventHandler
    public void onNPCLeftClick(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Villager) {
            if (e.getEntity().hasMetadata("arenaNPC")) {
                Entity var3 = e.getDamager();
                if (var3 instanceof Player) {
                    Player p = (Player)var3;
                    e.setCancelled(true);
                    this.openNPCGUI(p);
                }
            }
        }
    }

    private void openNPCGUI(Player player) {
        BedWarsPlugin.getInstance().getArenaManager().openArenaSelectGUI(player);
    }
}
