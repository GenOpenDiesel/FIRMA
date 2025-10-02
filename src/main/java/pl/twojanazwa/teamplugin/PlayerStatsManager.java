package pl.twojanazwa.teamplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerStatsManager {

    private final TeamPlugin plugin;
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();
    private File statsFile;
    private FileConfiguration statsConfig;

    public PlayerStatsManager(TeamPlugin plugin) {
        this.plugin = plugin;
        createStatsFile();
    }

    public PlayerStats getPlayerStats(UUID uuid) {
        return playerStats.computeIfAbsent(uuid, k -> new PlayerStats());
    }

    private void createStatsFile() {
        statsFile = new File(plugin.getDataFolder(), "playerstats.yml");
        if (!statsFile.exists()) {
            try {
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nie mozna stworzyc pliku playerstats.yml!");
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
    }

    public void saveStats() {
        playerStats.forEach((uuid, stats) -> {
            statsConfig.set("stats." + uuid.toString(), stats);
        });
        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie mozna zapisac statystyk do pliku playerstats.yml!");
        }
    }

    public void loadStats() {
        if (!statsConfig.isConfigurationSection("stats")) {
            return;
        }
        statsConfig.getConfigurationSection("stats").getKeys(false).forEach(key -> {
            PlayerStats stats = (PlayerStats) statsConfig.get("stats." + key);
            if (stats != null) {
                playerStats.put(UUID.fromString(key), stats);
            }
        });
    }
}
