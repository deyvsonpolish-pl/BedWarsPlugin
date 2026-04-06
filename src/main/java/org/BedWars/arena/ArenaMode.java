package org.BedWars.arena;

import org.bukkit.ChatColor;

/**
 * Enum reprezentujący tryby gry w BedWars.
 */
public enum ArenaMode {
    SOLO(1, "Solo"),
    DUO(2, "Duo"),
    TRIO(3, "Trójki"),
    QUAD(4, "Czwórki");

    private final int playersPerTeam;
    private final String display;

    ArenaMode(int playersPerTeam, String display) {
        this.playersPerTeam = playersPerTeam;
        this.display = display;
    }

    public int getPlayersPerTeam() {
        return playersPerTeam;
    }

    public String getDisplay() {
        return display;
    }

    public ArenaMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }
}

