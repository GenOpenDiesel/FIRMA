package pl.twojanazwa.firmaplugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class FirmaPlugin extends JavaPlugin {

    private static Economy econ = null;
    private FirmaManager firmaManager;

    @Override
    public void onEnable() {
        // Rejestracja klasy do serializacji YAML
        ConfigurationSerialization.registerClass(Firma.class);

        if (!setupEconomy()) {
            getLogger().severe("Nie znaleziono Vault! Wylaczam plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        this.firmaManager = new FirmaManager(this);
        firmaManager.loadFirmy();

        FirmaCommand firmaCommand = new FirmaCommand(firmaManager);
        Objects.requireNonNull(this.getCommand("firma")).setExecutor(firmaCommand);
        Objects.requireNonNull(this.getCommand("firma")).setTabCompleter(firmaCommand);

        getServer().getPluginManager().registerEvents(new FirmaChatListener(firmaManager), this);
        getServer().getPluginManager().registerEvents(new FirmaPvpListener(firmaManager), this);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FirmaPlaceholders(this, firmaManager).register();
        }

        // Automatyczny zapis co 10 minut (async)
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            firmaManager.saveFirmy();
            getLogger().info("Automatycznie zapisano dane firm.");
        }, 0L, 20L * 60 * 10);

        getLogger().info("FirmaPlugin zostal wlaczony!");
    }

    @Override
    public void onDisable() {
        if (firmaManager != null) {
            firmaManager.saveFirmy();
        }
        getLogger().info("FirmaPlugin zostal wylaczony.");
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

