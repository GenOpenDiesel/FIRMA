package pl.twojanazwa.teamplugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class TeamPlugin extends JavaPlugin {

    private static Economy econ = null;
    private TeamManager teamManager;
    private PlayerStatsManager playerStatsManager;

    @Override
    public void onEnable() {
        // Rejestracja naszych klas do zapisu
        ConfigurationSerialization.registerClass(Team.class);
        ConfigurationSerialization.registerClass(PlayerStats.class);

        if (!setupEconomy()) {
            getLogger().severe("Nie znaleziono Vault! Wylaczam plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Zapisz domyslny config.yml, jesli nie istnieje
        saveDefaultConfig();

        this.playerStatsManager = new PlayerStatsManager(this);
        playerStatsManager.loadStats();

        this.teamManager = new TeamManager(this, playerStatsManager);
        teamManager.loadTeams(); // Wczytaj teamy z pliku

        TeamCommand teamCommand = new TeamCommand(teamManager);
        Objects.requireNonNull(this.getCommand("team")).setExecutor(teamCommand);
        Objects.requireNonNull(this.getCommand("team")).setTabCompleter(teamCommand);
        
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(playerStatsManager), this);
        getServer().getPluginManager().registerEvents(new TeamChatListener(teamManager), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TeamPlaceholders(this, teamManager, playerStatsManager).register();
        }

        // Uruchomienie schedulera do automatycznego zapisu co 10 minut
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            teamManager.saveTeams();
            playerStatsManager.saveStats();
            getLogger().info("Automatycznie zapisano dane teamow i statystyki graczy.");
        }, 0L, 20L * 60 * 10); // 20L * 60 * 10 = 10 minut
        
        getLogger().info("TeamPlugin zostal wlaczony!");
    }

    @Override
    public void onDisable() {
        if (teamManager != null) {
            teamManager.saveTeams(); // Zapisz teamy do pliku
        }
        if (playerStatsManager != null) {
            playerStatsManager.saveStats();
        }
        getLogger().info("TeamPlugin zostal wylaczony.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }
}
