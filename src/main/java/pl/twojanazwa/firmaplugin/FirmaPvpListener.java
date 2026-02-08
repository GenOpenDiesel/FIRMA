package pl.twojanazwa.firmaplugin;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class FirmaPvpListener implements Listener {

    private final FirmaManager firmaManager;

    public FirmaPvpListener(FirmaManager firmaManager) {
        this.firmaManager = firmaManager;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = null;

        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                attacker = shooter;
            }
        }

        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        // O(1) lookup z cache
        Firma victimFirma = firmaManager.getFirmaByPlayer(victim);
        Firma attackerFirma = firmaManager.getFirmaByPlayer(attacker);

        if (victimFirma != null && attackerFirma != null
                && victimFirma.getName().equals(attackerFirma.getName())
                && !victimFirma.isPvpEnabled()) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "PVP w tej firmie jest wylaczone!");
        }
    }
}

