package org.BedWars;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AutoUpdater {

    private final BedWarsPlugin plugin;
    private final String repoOwner;
    private final String repoName;
    private final String fileName;

    public AutoUpdater(BedWarsPlugin plugin, String repoOwner, String repoName, String fileName) {
        this.plugin = plugin;
        this.repoOwner = repoOwner;
        this.repoName = repoName;
        this.fileName = fileName;
    }

    // Wywoływane w onEnable()
    public void checkAndUpdate() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("https://api.github.com/repos/" + repoOwner + "/" + repoName + "/releases/latest");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.connect();

                JsonObject json = JsonParser.parseReader(new InputStreamReader(connection.getInputStream())).getAsJsonObject();
                String latestVersion = json.get("tag_name").getAsString();
                JsonArray assets = json.getAsJsonArray("assets");
                String downloadUrl = null;

                for (int i = 0; i < assets.size(); i++) {
                    JsonObject asset = assets.get(i).getAsJsonObject();
                    if (asset.get("name").getAsString().equals(fileName)) {
                        downloadUrl = asset.get("browser_download_url").getAsString();
                        break;
                    }
                }

                if (downloadUrl == null) {
                    plugin.getLogger().warning("Nie znaleziono pliku JAR w release!");
                    return;
                }

                String currentVersion = plugin.getDescription().getVersion();
                if (!currentVersion.equals(latestVersion)) {
                    plugin.getLogger().info("Dostępna nowa wersja: " + latestVersion);

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        TextComponent message = new TextComponent("Nowa wersja BedWarsPlugin (" + latestVersion + ") dostępna! Kliknij tutaj, aby pobrać.");
                        message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bedwars update"));
                        player.spigot().sendMessage(message);
                    }

                    // Automatyczne pobranie i nadpisanie pliku
                    downloadUpdate(downloadUrl);
                } else {
                    plugin.getLogger().info("Posiadasz najnowszą wersję BedWarsPlugin.");
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Nie udało się sprawdzić aktualizacji: " + e.getMessage());
            }
        });
    }

    private void downloadUpdate(String downloadUrl) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getLogger().info("Pobieranie nowej wersji...");
                URL url = new URL(downloadUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.connect();

                try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream("plugins/" + fileName)) {

                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                    }
                }

                plugin.getLogger().info("Plugin został zaktualizowany! Proszę wykonać /reload lub restart serwera.");

            } catch (Exception e) {
                plugin.getLogger().warning("Nie udało się pobrać aktualizacji: " + e.getMessage());
            }
        });
    }
}
