package org.BedWars;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutoUpdater {

    private final BedWarsPlugin plugin;
    private final String repoOwner;
    private final String repoName;
    private final File versionFile;

    private boolean debugEnabled;

    private volatile String lastDownloadedJarName = null;
    private volatile String lastDownloadedVersion = null;
    private final AtomicBoolean downloadInProgress = new AtomicBoolean(false);
    public boolean isDebug() {
        return debugEnabled;
    }

    public void setDebug(boolean value) {
        this.debugEnabled = value;
        plugin.getConfig().set("updater.debug", value);
        plugin.saveConfig();
    }
    public AutoUpdater(BedWarsPlugin plugin, String repoOwner, String repoName) {
        this.plugin = plugin;
        this.repoOwner = repoOwner;
        this.repoName = repoName;

        this.versionFile = new File(plugin.getDataFolder(), "version.txt");

        plugin.saveDefaultConfig();
        this.debugEnabled = plugin.getConfig().getBoolean("updater.debug", false);

        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

        if (!versionFile.exists()) {
            try (FileWriter writer = new FileWriter(versionFile)) {
                writer.write(plugin.getDescription().getVersion());
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć version.txt");
            }
        }
    }

    // ================= DEBUG =================
    private void debug(String msg) {
        if (debugEnabled) plugin.getLogger().info("[Updater DEBUG] " + msg);
    }

    // ================= START =================
    public void startPeriodicCheck() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkAndUpdate, 0L, 6000L);
    }

    public void manualCheck(Player sender) {
        sender.sendMessage("§7Sprawdzanie aktualizacji...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::checkAndUpdate);
    }

    // ================= MAIN =================
    public void checkAndUpdate() {

        if (!downloadInProgress.compareAndSet(false, true)) {
            debug("Już trwa sprawdzanie.");
            return;
        }

        try {
            debug("Łączenie z GitHub...");

            URL url = new URL("https://api.github.com/repos/" + repoOwner + "/" + repoName + "/releases/latest");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestProperty("User-Agent", "BedWars-Updater");
            connection.setRequestProperty("Accept", "application/vnd.github+json");

            // 🔐 TOKEN Z CONFIGU
            String token = plugin.getConfig().getString("github-token");
            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "token " + token);
            }

            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int code = connection.getResponseCode();

            if (code != 200) {
                plugin.getLogger().warning("GitHub API error: " + code);
                return;
            }

            JsonObject json = JsonParser.parseReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)
            ).getAsJsonObject();

            String latestVersion = json.get("tag_name").getAsString();
            JsonArray assets = json.getAsJsonArray("assets");

            String downloadUrl = null;
            String jarName = null;

            for (int i = 0; i < assets.size(); i++) {
                JsonObject asset = assets.get(i).getAsJsonObject();
                String name = asset.get("name").getAsString();

                if (name.endsWith(".jar")) {
                    downloadUrl = asset.get("browser_download_url").getAsString();
                    jarName = name;
                    break;
                }
            }

            String localVersion = readLocalVersion();

            if (!latestVersion.equals(localVersion)) {
                plugin.getLogger().info("Nowa wersja: " + latestVersion);

                lastDownloadedJarName = jarName;
                lastDownloadedVersion = latestVersion;

                if (downloadToTemp(downloadUrl, jarName)) {
                    notifyPlayersClickable(latestVersion);
                }
            } else {
                debug("Brak aktualizacji.");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Błąd update: " + e.getMessage());
        } finally {
            downloadInProgress.set(false);
        }
    }

    // ================= DOWNLOAD =================
    private boolean downloadToTemp(String downloadUrl, String jarName) {
        try {
            File temp = new File("plugins", jarName + ".new");

            HttpURLConnection connection = (HttpURLConnection) new URL(downloadUrl).openConnection();
            connection.setRequestProperty("User-Agent", "BedWars-Updater");

            try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream out = new FileOutputStream(temp)) {

                byte[] buffer = new byte[4096];
                int count;

                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                }
            }

            plugin.getLogger().info("Pobrano update.");
            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Błąd pobierania: " + e.getMessage());
            return false;
        }
    }

    // ================= APPLY (PLUGMAN) =================
    public void applyUpdateWithPlugman() {

        if (lastDownloadedJarName == null) {
            plugin.getLogger().warning("Brak pobranego update.");
            return;
        }

        Plugin plugman = Bukkit.getPluginManager().getPlugin("PlugMan");

        if (plugman == null) {
            plugin.getLogger().warning("Brak PlugMan!");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {

            String pluginName = plugin.getDescription().getName();

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "plugman disable " + pluginName);

            File pluginsFolder = new File("plugins");
            File temp = new File(pluginsFolder, lastDownloadedJarName + ".new");
            File finalJar = new File(pluginsFolder, lastDownloadedJarName);

            try {
                Files.move(temp.toPath(), finalJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                plugin.getLogger().warning("Nie udało się podmienić pliku: " + e.getMessage());
            }

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "plugman enable " + pluginName);

            writeLocalVersion(lastDownloadedVersion);

            plugin.getLogger().info("Plugin został zaktualizowany!");
        });
    }

    // ================= MESSAGE =================
    private void notifyPlayersClickable(String version) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            TextComponent msg = new TextComponent("§aNowa wersja (" + version + ") ");
            TextComponent click = new TextComponent("§e[Kliknij aby zaktualizować]");
            click.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bwupdate apply"));
            msg.addExtra(click);
            p.spigot().sendMessage(msg);
        }
    }

    // ================= VERSION =================
    private String readLocalVersion() {
        try (BufferedReader reader = new BufferedReader(new FileReader(versionFile))) {
            return reader.readLine();
        } catch (Exception e) {
            return plugin.getDescription().getVersion();
        }
    }

    private void writeLocalVersion(String version) {
        try (FileWriter writer = new FileWriter(versionFile)) {
            writer.write(version);
        } catch (Exception ignored) {}
    }
}