package pl.twojanazwa.firmaplugin;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class FirmaChatListener implements Listener {

    private final FirmaManager firmaManager;

    public FirmaChatListener(FirmaManager firmaManager) {
        this.firmaManager = firmaManager;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // ZMIANA: Reagujemy na "!!" zamiast "!"
        if (message.startsWith("!!")) {
            Firma firma = firmaManager.getFirmaByPlayer(player);
            if (firma != null) {
                event.setCancelled(true);
                // ZMIANA: Wycinamy 2 znaki (!!) zamiast 1
                String firmaMessage = message.substring(2);
                String format = firmaManager.plugin.getConfig().getString("messages.firma-chat-format",
                        "&8[&2Firma Chat&8] &a%player%&8: &f%message%");
                format = format.replace("%player%", player.getName()).replace("%message%", firmaMessage);
                firma.broadcast(ChatColor.translateAlternateColorCodes('&', format));
            }
        }
    }
}
