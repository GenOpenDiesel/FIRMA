package pl.twojanazwa.teamplugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerDeathListener implements Listener {

    private final PlayerStatsManager playerStatsManager;
    private final Map<UUID, Map<UUID, Long>> lastKillTimestamps = new HashMap<>();

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


        if (killer != null && !killer.equals(victim)) {
            long currentTime = System.currentTimeMillis();
            lastKillTimestamps.putIfAbsent(killer.getUniqueId(), new HashMap<>());
            Map<UUID, Long> victimKillHistory = lastKillTimestamps.get(killer.getUniqueId());

            long lastKillTime = victimKillHistory.getOrDefault(victim.getUniqueId(), 0L);

            if (currentTime - lastKillTime < TimeUnit.HOURS.toMillis(3)) {
                // Minęło mniej niż 3 godziny, więc nie przyznajemy punktów.
                return;
            }

            // Aktualizuj czas ostatniego zabójstwa i przyznaj punkty.
            victimKillHistory.put(victim.getUniqueId(), currentTime);
            PlayerStats killerStats = playerStatsManager.getPlayerStats(killer.getUniqueId());
            killerStats.addKill();
            killerStats.addPoints(10);
        }
    }
}
