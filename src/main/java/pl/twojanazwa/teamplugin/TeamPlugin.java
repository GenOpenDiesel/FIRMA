package pl.twojanazwa.teamplugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class TeamPlugin extends JavaPlugin {

    private static Economy econ = null;
    private TeamManager teamManager;

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Nie znaleziono Vault! Wylaczam plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.teamManager = new TeamManager(this);
        Objects.requireNonNull(this.getCommand("team")).setExecutor(new TeamCommand(teamManager));
        getLogger().info("TeamPlugin zostal wlaczony!");
    }

    @Override
    public void onDisable() {
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
