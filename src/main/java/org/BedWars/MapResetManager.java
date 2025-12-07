package org.BedWars;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class MapResetManager {
    private final BedWarsPlugin plugin;
    private final Map<String, Location> pos1 = new HashMap();
    private final Map<String, Location> pos2 = new HashMap();
    private final Map<String, String> arenaStatus = new HashMap();
    private final Map<String, Integer> arenaProgress = new HashMap();

    public MapResetManager(BedWarsPlugin plugin) {
        this.plugin = plugin;
        File folder = new File(plugin.getDataFolder(), "maps");
        if (!folder.exists()) {
            folder.mkdirs();
        }

    }

    public void startArenaRestore(String arenaName) {
        this.setArenaStatus(arenaName, "Badanie terenu");
        this.setArenaProgress(arenaName, 0);
        this.restoreChangedBlocks(arenaName);
    }

    public void setArenaStatus(String arenaName, String status) {
        this.arenaStatus.put(arenaName, status);
    }

    public void setArenaProgress(String arenaName, int progress) {
        this.arenaProgress.put(arenaName, progress);
    }

    public void setPos1(Player player) {
        this.pos1.put(player.getName(), player.getLocation());
        player.sendMessage(String.valueOf(ChatColor.GREEN) + "✔ Ustawiono pierwszy punkt regeneracji!");
    }

    public void setPos2(Player player) {
        this.pos2.put(player.getName(), player.getLocation());
        player.sendMessage(String.valueOf(ChatColor.GREEN) + "✔ Ustawiono drugi punkt regeneracji!");
    }

    private boolean hasBoth(String name) {
        return this.pos1.containsKey(name) && this.pos2.containsKey(name);
    }

    public void saveRegion(final Player player, final String arenaName) {
        if (!this.hasBoth(player.getName())) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "❌ Najpierw ustaw oba punkty!");
        } else {
            Location l1 = (Location)this.pos1.get(player.getName());
            Location l2 = (Location)this.pos2.get(player.getName());
            final World world = l1.getWorld();
            final int minX = Math.min(l1.getBlockX(), l2.getBlockX());
            final int maxX = Math.max(l1.getBlockX(), l2.getBlockX());
            final int minY = Math.min(l1.getBlockY(), l2.getBlockY());
            final int maxY = Math.max(l1.getBlockY(), l2.getBlockY());
            final int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
            final int maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());
            final List<SavedBlock> blocks = Collections.synchronizedList(new ArrayList());
            final int totalBlocks = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
            String var10001 = String.valueOf(ChatColor.YELLOW);
            player.sendMessage(var10001 + "⏳ Trwa zapisywanie mapy " + String.valueOf(ChatColor.AQUA) + arenaName + String.valueOf(ChatColor.YELLOW) + "...");
            var10001 = String.valueOf(ChatColor.GRAY);
            player.sendMessage(var10001 + "Zostanie zapisanych ok. " + String.valueOf(ChatColor.GOLD) + totalBlocks + String.valueOf(ChatColor.GRAY) + " bloków (z powietrzem).");
            int batch = 20000;
            final long startTime = System.currentTimeMillis();
            (new BukkitRunnable() {
                int x = minX;
                int y = minY;
                int z = minZ;
                int saved = 0;
                int lastPercent = -1;
                boolean finished = false;

                public void run() {
                    if (!this.finished) {
                        int processed = 0;

                        while(processed < 20000 && this.x <= maxX) {
                            Block b = world.getBlockAt(this.x, this.y, this.z);
                            Material type = b.getType();

                            String dataString;
                            try {
                                dataString = b.getBlockData() != null ? b.getBlockData().getAsString() : "minecraft:air";
                            } catch (Exception var6) {
                                dataString = "minecraft:air";
                            }

                            blocks.add(new SavedBlock(this.x, this.y, this.z, type, dataString));
                            ++this.saved;
                            ++processed;
                            ++this.z;
                            if (this.z > maxZ) {
                                this.z = minZ;
                                ++this.y;
                                if (this.y > maxY) {
                                    this.y = minY;
                                    ++this.x;
                                }
                            }
                        }

                        int percent = (int)((double)this.saved / (double)totalBlocks * (double)100.0F);
                        if (percent % 5 == 0 && percent != this.lastPercent) {
                            this.lastPercent = percent;
                            player.sendActionBar(String.valueOf(ChatColor.AQUA) + "\ud83d\udcbe Zapisano " + percent + "% (" + this.saved + "/" + totalBlocks + ")");
                        }

                        if (this.x > maxX && !this.finished) {
                            this.finished = true;
                            this.cancel();
                            Bukkit.getScheduler().runTaskAsynchronously(MapResetManager.this.plugin, () -> {
                                try {
                                    File mapFile = new File(MapResetManager.this.plugin.getDataFolder(), "maps/" + arenaName + ".map");
                                    mapFile.getParentFile().mkdirs();
                                    MapResetManager.this.saveBlocksToFile(blocks, world.getName(), mapFile);
                                    long time = (System.currentTimeMillis() - startTime) / 1000L;
                                    Bukkit.getScheduler().runTask(MapResetManager.this.plugin, () -> player.sendMessage(String.valueOf(ChatColor.GREEN) + "✅ Zapisano " + this.saved + " bloków w " + time + "s."));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    player.sendMessage(String.valueOf(ChatColor.RED) + "❌ Błąd przy zapisie mapy!");
                                }

                            });
                        }

                    }
                }
            }).runTaskTimer(this.plugin, 0L, 1L);
        }
    }

    public void restoreChangedBlocks(String arenaName) {
        File mapFile = new File(this.plugin.getDataFolder(), "maps/" + arenaName + ".map");
        if (!mapFile.exists()) {
            this.plugin.getLogger().warning("❌ Plik mapy dla areny " + arenaName + " nie został znaleziony!");
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                try {
                    MapData mapData = this.loadBlocksFromFile(mapFile);
                    final World world = Bukkit.getWorld(mapData.worldName);
                    if (world == null) {
                        this.plugin.getLogger().warning("❌ Świat dla areny " + arenaName + " nie został znaleziony!");
                        return;
                    }

                    final List<SavedBlock> toRestore = new ArrayList();

                    for(SavedBlock b : mapData.blocks) {
                        Block current = world.getBlockAt(b.x, b.y, b.z);
                        if (current.getType() != b.type) {
                            toRestore.add(b);
                        }
                    }

                    final int total = toRestore.size();
                    if (total == 0) {
                        this.arenaStatus.put(arenaName, "Gotowe");
                        this.arenaProgress.put(arenaName, 100);
                        String var11 = String.valueOf(ChatColor.GREEN);
                        Bukkit.broadcastMessage(var11 + "✅ Arena " + arenaName + " nie wymaga odbudowy.");
                        return;
                    }

                    this.arenaStatus.put(arenaName, "Restart");
                    this.arenaProgress.put(arenaName, 0);
                    String var10000 = String.valueOf(ChatColor.GRAY);
                    Bukkit.broadcastMessage(var10000 + "\ud83e\uddf1 Odbudowa areny " + String.valueOf(ChatColor.YELLOW) + arenaName + String.valueOf(ChatColor.GRAY) + " (" + total + " bloków)");
                    (new BukkitRunnable() {
                        int restored = 0;
                        int lastPercent = -1;
                        final int batchSize = 100;

                        public void run() {
                            int count = 0;

                            while(!toRestore.isEmpty() && count < 100) {
                                SavedBlock b = (SavedBlock)toRestore.remove(0);

                                try {
                                    Block block = world.getBlockAt(b.x, b.y, b.z);
                                    block.setType(b.type, false);
                                    if (b.data != null) {
                                        block.setBlockData(Bukkit.createBlockData(b.data), false);
                                    }

                                    ++this.restored;
                                    ++count;
                                } catch (Exception var4) {
                                }
                            }

                            int percent = (int)((double)this.restored / (double)total * (double)100.0F);
                            if (percent != this.lastPercent) {
                                this.lastPercent = percent;
                                MapResetManager.this.arenaProgress.put(arenaName, percent);
                                if (percent % 10 == 0) {
                                    Bukkit.broadcastMessage(String.valueOf(ChatColor.AQUA) + "⏳ Odbudowa " + arenaName + ": " + percent + "%");
                                }
                            }

                            if (toRestore.isEmpty()) {
                                MapResetManager.this.arenaStatus.put(arenaName, "Gotowe");
                                MapResetManager.this.arenaProgress.put(arenaName, 100);
                                String var10000 = String.valueOf(ChatColor.GREEN);
                                Bukkit.broadcastMessage(var10000 + "✅ Arena " + arenaName + " została odbudowana!");
                                this.cancel();
                            }

                        }
                    }).runTaskTimer(this.plugin, 0L, 1L);
                } catch (Exception e) {
                    this.plugin.getLogger().severe("❌ Błąd przy odczycie mapy " + arenaName);
                    e.printStackTrace();
                }

            });
        }
    }

    private void saveBlocksToFile(List<SavedBlock> blocks, String worldName, File file) throws IOException {
        try (
                FileOutputStream fos = new FileOutputStream(file);
                GZIPOutputStream gzip = new GZIPOutputStream(fos);
                ObjectOutputStream out = new ObjectOutputStream(gzip);
        ) {
            MapData data = new MapData(worldName, blocks);
            out.writeObject(data);
        }

    }

    private MapData loadBlocksFromFile(File file) throws IOException, ClassNotFoundException {
        MapData var5;
        try (
                FileInputStream fis = new FileInputStream(file);
                GZIPInputStream gzip = new GZIPInputStream(fis);
                ObjectInputStream in = new ObjectInputStream(gzip);
        ) {
            var5 = (MapData)in.readObject();
        }

        return var5;
    }

    public String getArenaStatus(String arenaName) {
        return (String)this.arenaStatus.getOrDefault(arenaName, "Oczekiwanie");
    }

    public int getArenaProgress(String arenaName) {
        return (Integer)this.arenaProgress.getOrDefault(arenaName, 0);
    }

    private static class SavedBlock implements Serializable {
        int x;
        int y;
        int z;
        Material type;
        String data;

        public SavedBlock(int x, int y, int z, Material type, String data) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.data = data;
        }
    }

    private static class MapData implements Serializable {
        String worldName;
        List<SavedBlock> blocks;

        public MapData(String worldName, List<SavedBlock> blocks) {
            this.worldName = worldName;
            this.blocks = blocks;
        }
    }
}
