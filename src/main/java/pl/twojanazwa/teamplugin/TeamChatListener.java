package pl.twojanazwa.teamplugin;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class TeamChatListener implements Listener {

    private final TeamManager teamManager;

    public TeamChatListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        if (message.startsWith("!")) {
            Team team = teamManager.getTeamByPlayer(player);
            if (team != null) {
                event.setCancelled(true);
                String teamMessage = message.substring(1);
                String format = teamManager.plugin.getConfig().getString("messages.team-chat-format", "&8[&eTeam Chat&8] &a%player%&8: &f%message%");
                format = format.replace("%player%", player.getName()).replace("%message%", teamMessage);
                team.broadcast(ChatColor.translateAlternateColorCodes('&', format));
            }
        }
    }
}
