package pl.twojanazwa.teamplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TeamCommand implements CommandExecutor {

    private final TeamManager teamManager;

    public TeamCommand(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda moze byc uzyta tylko przez gracza.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "stworz":
                if (args.length < 2) {
                    player.sendMessage("&9Team &8» &7Poprawne uzycie: &e/team stworz <tag>");
                    return true;
                }
                teamManager.createTeam(player, args[1]);
                break;
            case "zapros":
                if (args.length < 2) {
                    player.sendMessage("&9Team &8» &7Poprawne uzycie: &e/team zapros <nick>");
                    return true;
                }
                teamManager.invitePlayer(player, args[1]);
                break;
            case "akceptuj":
                teamManager.acceptInvite(player);
                break;
            case "wyrzuc":
                if (args.length < 2) {
                    player.sendMessage("&9Team &8» &7Poprawne uzycie: &e/team wyrzuc <nick>");
                    return true;
                }
                teamManager.kickPlayer(player, args[1]);
                break;
            case "usun":
                teamManager.deleteTeam(player);
                break;
            case "lider":
                if (args.length < 2) {
                    player.sendMessage("&9Team &8» &7Poprawne uzycie: &e/team lider <nick>");
                    return true;
                }
                teamManager.promotePlayer(player, args[1]);
                break;
            case "degrad":
                 if (args.length < 2) {
                    player.sendMessage("&9Team &8» &7Poprawne uzycie: &e/team degrad <nick>");
                    return true;
                }
                teamManager.demotePlayer(player, args[1]);
                break;
            case "info":
                 if (args.length < 2) {
                    player.sendMessage("&9Team &8» &7Poprawne uzycie: &e/team info <tag>");
                    return true;
                }
                teamManager.getTeamInfo(player, args[1]);
                break;
            case "pvp":
                teamManager.togglePvp(player);
                break;
            case "opusc":
                teamManager.leaveTeam(player);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("&9&m----------------------------------");
        player.sendMessage("&e/team stworz <tag> &7- Tworzy nowy team.");
        player.sendMessage("&e/team zapros <nick> &7- Zaprasza gracza do teamu.");
        player.sendMessage("&e/team akceptuj &7- Akceptuje zaproszenie do teamu.");
        player.sendMessage("&e/team wyrzuc <nick> &7- Wyrzuca gracza z teamu.");
        player.sendMessage("&e/team usun &7- Usuwa Twoj team.");
        player.sendMessage("&e/team lider <nick> &7- Awansuje gracza na lidera.");
        player.sendMessage("&e/team degrad <nick> &7- Degraduje lidera do czlonka.");
        player.sendMessage("&e/team info <tag> &7- Wyswietla informacje o teamie.");
        player.sendMessage("&e/team pvp &7- Wlacza/wylacza pvp w teamie.");
        player.sendMessage("&e/team opusc &7- Opuszcza obecny team.");
        player.sendMessage("&9&m----------------------------------");
    }
}
