package pl.twojanazwa.teamplugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathListener implements Listener {

    private final TeamManager teamManager;

    public PlayerDeathListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        Team victimTeam = teamManager.getTeamByPlayer(victim);
        Team killerTeam = (killer != null) ? teamManager.getTeamByPlayer(killer) : null;

        if (victimTeam != null) {
            victimTeam.removePoints(5);
            victimTeam.getPlayerStats(victim.getUniqueId()).addDeath();
        }

        if (killerTeam != null) {
            killerTeam.addPoints(10);
            killerTeam.getPlayerStats(killer.getUniqueId()).addKill();
        }
    }
}
