package pl.twojanazwa.teamplugin;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TeamPlaceholders extends PlaceholderExpansion {

    private final TeamPlugin plugin;
    private final TeamManager teamManager;
    private final PlayerStatsManager playerStatsManager;

    public TeamPlaceholders(TeamPlugin plugin, TeamManager teamManager, PlayerStatsManager playerStatsManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.playerStatsManager = playerStatsManager;
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

        // %team_gracz_tag%
        if (params.equals("gracz_tag")) {
            Team team = teamManager.getTeamByPlayer(player);
            return (team != null) ? team.getName() : "Brak";
        }

        // %team_points%
        if (params.equals("points")) {
            Team team = teamManager.getTeamByPlayer(player);
            return (team != null) ? String.valueOf(team.getPoints()) : "Brak";
        }

        // %team_player_points%
        if (params.equals("player_points")) {
            PlayerStats stats = playerStatsManager.getPlayerStats(player.getUniqueId());
            return String.valueOf(stats.getPoints());
        }

        // Nowy placeholder %team_gracz_punkty%
        if (params.equals("gracz_punkty")) {
            PlayerStats stats = playerStatsManager.getPlayerStats(player.getUniqueId());
            return String.valueOf(stats.getPoints());
        }

        // Placeholders for top teams
        if (params.startsWith("top_name_")) {
            try {
                int rank = Integer.parseInt(params.substring("top_name_".length()));
                if (rank > 0 && rank <= 10) {
                    List<Team> topTeams = teamManager.getTopTeams();
                    if (topTeams.size() >= rank) {
                        return topTeams.get(rank - 1).getName();
                    }
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        if (params.startsWith("top_points_")) {
            try {
                int rank = Integer.parseInt(params.substring("top_points_".length()));
                if (rank > 0 && rank <= 10) {
                    List<Team> topTeams = teamManager.getTopTeams();
                    if (topTeams.size() >= rank) {
                        return String.valueOf(topTeams.get(rank - 1).getPoints());
                    }
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return "Brak";
    }
}
