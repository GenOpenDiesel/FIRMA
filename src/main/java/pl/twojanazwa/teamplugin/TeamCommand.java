package pl.twojanazwa.teamplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TeamCommand implements CommandExecutor, TabCompleter {

    private final TeamManager teamManager;

    public TeamCommand(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    private void sendUsage(CommandSender sender, String usage) {
        String message = teamManager.plugin.getConfig().getString("messages.poprawne-uzycie", "&9Team &8» &7Poprawne uzycie: &e%usage%");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message.replace("%usage%", usage)));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // KOMENDA ADMINA: WYRZUCANIE GRACZA
        if (args.length > 0 && args[0].equalsIgnoreCase("adminwyrzuc")) {
            if (args.length < 2) {
                sendUsage(sender, "/team adminwyrzuc <nick>");
                return true;
            }
            teamManager.forceKickPlayer(sender, args[1]);
            return true;
        }

        // KOMENDA ADMINA: USUWANIE TEAMU
        if (args.length > 0 && args[0].equalsIgnoreCase("adminusun")) {
            if (args.length < 2) {
                sendUsage(sender, "/team adminusun <nazwa_teamu>");
                return true;
            }
            teamManager.forceDeleteTeam(sender, args[1]);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda moze byc uzyta tylko przez gracza (oprocz /team adminwyrzuc i /team adminusun).");
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
                    sendUsage(player, "/team stworz <nazwa>");
                    return true;
                }
                teamManager.createTeam(player, args[1]);
                break;
            case "zapros":
                if (args.length < 2) {
                    sendUsage(player, "/team zapros <nick>");
                    return true;
                }
                teamManager.invitePlayer(player, args[1]);
                break;
            case "akceptuj":
                teamManager.acceptInvite(player);
                break;
            case "wyrzuc":
                if (args.length < 2) {
                    sendUsage(player, "/team wyrzuc <nick>");
                    return true;
                }
                teamManager.kickPlayer(player, args[1]);
                break;
            case "usun":
                teamManager.deleteTeam(player);
                break;
            case "lider":
                if (args.length < 2) {
                    sendUsage(player, "/team lider <nick>");
                    return true;
                }
                teamManager.promotePlayer(player, args[1]);
                break;
            case "degrad":
                 if (args.length < 2) {
                    sendUsage(player, "/team degrad <nick>");
                    return true;
                }
                teamManager.demotePlayer(player, args[1]);
                break;
            case "info":
                 if (args.length < 2) {
                    Team team = teamManager.getTeamByPlayer(player);
                    if(team == null) {
                        sendUsage(player, "/team info <nazwa>");
                        return true;
                    }
                    teamManager.getTeamInfo(player, team.getName());
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
            case "top":
                teamManager.showTopTeams(player);
                break;
            case "sethome":
                teamManager.setHome(player);
                break;
            case "home":
                teamManager.teleportHome(player);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&9&m----------------------------------"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/team stworz <nazwa> &7- Tworzy nowy team."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/team zapros <nick> &7- Zaprasza gracza do teamu."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/team akceptuj &7- Akceptuje zaproszenie do teamu."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/team wyrzuc <nick> &7- Wyrzuca gracza z teamu."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/team usun &7- Usuwa Twoj team."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/team lider <nick> &7- Awansuje gracza na lidera."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/team degrad <nick> &7- Degraduje lidera do czlonka."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/team info [nazwa] &7- Wyswietla informacje o teamie."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/team pvp &7- Wlacza/wylacza pvp w teamie."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/team opusc &7- Opuszcza obecny team."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/team top &7- Wyswietla top 10 teamow."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/team sethome &7- Ustawia dom teamu."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/team home &7- Teleportuje do domu teamu."));
        if (player.hasPermission("teamplugin.admin")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c/team adminwyrzuc <nick> &7- Wymusza wyrzucenie gracza."));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c/team adminusun <nazwa> &7- Wymusza usuniecie teamu."));
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&9&m----------------------------------"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("stworz", "zapros", "akceptuj", "wyrzuc", "usun", "lider", "degrad", "info", "pvp", "opusc", "top", "sethome", "home"));
            if (sender.hasPermission("teamplugin.admin")) {
                completions.add("adminwyrzuc");
                completions.add("adminusun");
            }
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "zapros":
                case "wyrzuc":
                case "lider":
                case "degrad":
                case "adminwyrzuc":
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                case "info":
                case "adminusun":
                    return teamManager.getTeams().keySet().stream()
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }
}
