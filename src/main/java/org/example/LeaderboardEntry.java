package org.example;

public class LeaderboardEntry {
    private final String playerName;
    private final double value;

    public LeaderboardEntry(String playerName, double value) {
        this.playerName = playerName;
        this.value = value;
    }

    public String getPlayerName() { return playerName; }
    public double getValue() { return value; }
}