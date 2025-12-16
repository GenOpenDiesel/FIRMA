package pl.twojanazwa.teamplugin;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class TeamPvpListener implements Listener {

    private final TeamManager teamManager;

    public TeamPvpListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Sprawdzamy, czy ofiara to gracz
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Player attacker = null;

        // Sprawdzamy atakującego (bezpośredni atak lub strzał)
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        // Jeśli nie ma atakującego gracza lub gracz bije samego siebie, ignorujemy
        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        // Pobieramy teamy (korzystając z szybkiego cache w TeamManager)
        Team victimTeam = teamManager.getTeamByPlayer(victim);
        Team attackerTeam = teamManager.getTeamByPlayer(attacker);

        // Jeśli obaj mają team
        if (victimTeam != null && attackerTeam != null) {
            // Jeśli to ten sam team
            if (victimTeam.getName().equals(attackerTeam.getName())) {
                // Jeśli PVP w teamie jest wyłączone -> anuluj obrażenia
                if (!victimTeam.isPvpEnabled()) {
                    event.setCancelled(true);
                    attacker.sendMessage(ChatColor.RED + "PVP w tym teamie jest wylaczone!");
                }
            }
        }
    }
}
