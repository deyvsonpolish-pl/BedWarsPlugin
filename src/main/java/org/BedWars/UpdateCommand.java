package org.BedWars;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class UpdateCommand implements CommandExecutor {

    private final BedWarsPlugin plugin;

    public UpdateCommand(BedWarsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) return true;

        AutoUpdater updater = plugin.getUpdater();

        if (args.length == 0) {
            p.sendMessage("§e/bwupdate check");
            p.sendMessage("§e/bwupdate toggledebug");
            p.sendMessage("§e/bwupdate apply");
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "check":
                updater.manualCheck(p);
                break;

            case "toggledebug":
                boolean state = !updater.isDebug();
                updater.setDebug(state);
                p.sendMessage("Debug: " + (state ? "ON" : "OFF"));
                break;

            case "apply":
                updater.applyUpdateWithPlugman();
                p.sendMessage("§aAktualizowanie...");
                break;
        }

        return true;
    }
}