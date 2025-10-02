package pl.twojanazwa.teamplugin;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class PlayerStats implements ConfigurationSerializable {

    private int kills;
    private int deaths;
    private int points;

    public PlayerStats() {
        this.kills = 0;
        this.deaths = 0;
        this.points = 1000;
    }

    public PlayerStats(Map<String, Object> map) {
        this.kills = (int) map.get("kills");
        this.deaths = (int) map.get("deaths");
        this.points = (int) map.getOrDefault("points", 1000);
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("kills", kills);
        map.put("deaths", deaths);
        map.put("points", points);
        return map;
    }

    public int getKills() { return kills; }
    public void addKill() { this.kills++; }
    public int getDeaths() { return deaths; }
    public void addDeath() { this.deaths++; }
    public int getPoints() { return points; }
    public void addPoints(int amount) { this.points += amount; }
    public void removePoints(int amount) { this.points -= amount; }
}
