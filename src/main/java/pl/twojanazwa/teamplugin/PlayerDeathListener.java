package pl.twojanazwa.teamplugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathListener implements Listener {

    private final PlayerStatsManager playerStatsManager;

    public PlayerDeathListener(PlayerStatsManager playerStatsManager) {
        this.playerStatsManager = playerStatsManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        PlayerStats victimStats = playerStatsManager.getPlayerStats(victim.getUniqueId());
        victimStats.addDeath();
        victimStats.removePoints(5);


        if (killer != null) {
            PlayerStats killerStats = playerStatsManager.getPlayerStats(killer.getUniqueId());
            killerStats.addKill();
            killerStats.addPoints(10);
        }
    }
}
