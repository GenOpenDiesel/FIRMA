package pl.twojanazwa.teamplugin;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TeamPlaceholders extends PlaceholderExpansion {

    private final TeamPlugin plugin;
    private final TeamManager teamManager;

    public TeamPlaceholders(TeamPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "team";
    }

    @Override
    public @NotNull String getAuthor() {
        return "TwojaNazwa";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        // %team_points%
        if (params.equals("points")) {
            Team team = teamManager.getTeamByPlayer(player);
            return (team != null) ? String.valueOf(team.getPoints()) : "Brak";
        }

        return null;
    }
}
