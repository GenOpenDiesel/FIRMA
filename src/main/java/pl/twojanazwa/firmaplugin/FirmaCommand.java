package pl.twojanazwa.firmaplugin;

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

public class FirmaCommand implements CommandExecutor, TabCompleter {

    private final FirmaManager firmaManager;

    public FirmaCommand(FirmaManager firmaManager) {
        this.firmaManager = firmaManager;
    }

    private void sendUsage(CommandSender sender, String usage) {
        String message = firmaManager.plugin.getConfig().getString("messages.poprawne-uzycie",
                "&2Firma &8» &7Poprawne uzycie: &e%usage%");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message.replace("%usage%", usage)));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // KOMENDY ADMINA (mogą być z konsoli)
        if (args.length > 0 && args[0].equalsIgnoreCase("adminwyrzuc")) {
            if (args.length < 2) {
                sendUsage(sender, "/firma adminwyrzuc <nick>");
                return true;
            }
            firmaManager.forceKickPlayer(sender, args[1]);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("adminusun")) {
            if (args.length < 2) {
                sendUsage(sender, "/firma adminusun <nazwa_firmy>");
                return true;
            }
            firmaManager.forceDeleteFirma(sender, args[1]);
            return true;
        }

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
                    sendUsage(player, "/firma stworz <nazwa>");
                    return true;
                }
                firmaManager.createFirma(player, args[1]);
                break;
            case "zapros":
                if (args.length < 2) {
                    sendUsage(player, "/firma zapros <nick>");
                    return true;
                }
                firmaManager.invitePlayer(player, args[1]);
                break;
            case "akceptuj":
                firmaManager.acceptInvite(player);
                break;
            case "wyrzuc":
                if (args.length < 2) {
                    sendUsage(player, "/firma wyrzuc <nick>");
                    return true;
                }
                firmaManager.kickPlayer(player, args[1]);
                break;
            case "usun":
                firmaManager.deleteFirma(player);
                break;
            case "zastepca":
                if (args.length < 2) {
                    sendUsage(player, "/firma zastepca <nick>");
                    return true;
                }
                firmaManager.promoteToDeputy(player, args[1]);
                break;
            case "degraduj":
                if (args.length < 2) {
                    sendUsage(player, "/firma degraduj <nick>");
                    return true;
                }
                firmaManager.demoteDeputy(player, args[1]);
                break;
            case "info":
                if (args.length < 2) {
                    Firma firma = firmaManager.getFirmaByPlayer(player);
                    if (firma == null) {
                        sendUsage(player, "/firma info <nazwa/nick>");
                        return true;
                    }
                    firmaManager.getFirmaInfo(player, firma.getName());
                    return true;
                }
                firmaManager.getFirmaInfo(player, args[1]);
                break;
            case "wplac":
                if (args.length < 2) {
                    sendUsage(player, "/firma wplac <kwota>");
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1]);
                    firmaManager.depositToVault(player, amount);
                } catch (NumberFormatException e) {
                    sendUsage(player, "/firma wplac <kwota>");
                }
                break;
            case "wyplac":
                if (args.length < 2) {
                    sendUsage(player, "/firma wyplac <kwota>");
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[1]);
                    firmaManager.withdrawFromVault(player, amount);
                } catch (NumberFormatException e) {
                    sendUsage(player, "/firma wyplac <kwota>");
                }
                break;
            case "wyplata":
                if (args.length < 2) {
                    sendUsage(player, "/firma wyplata <procent>");
                    return true;
                }
                try {
                    double percent = Double.parseDouble(args[1]);
                    firmaManager.payWorkers(player, percent);
                } catch (NumberFormatException e) {
                    sendUsage(player, "/firma wyplata <procent>");
                }
                break;
            case "pvp":
                firmaManager.togglePvp(player);
                break;
            case "opusc":
                firmaManager.leaveFirma(player);
                break;
            case "top":
                firmaManager.showTopFirmy(player);
                break;
            case "sethome":
                if (!player.hasPermission("firma.home")) {
                    player.sendMessage(ChatColor.RED + "Brak uprawnien! (firma.home)");
                    return true;
                }
                firmaManager.setHome(player);
                break;
            case "home":
                if (!player.hasPermission("firma.home")) {
                    player.sendMessage(ChatColor.RED + "Brak uprawnien! (firma.home)");
                    return true;
                }
                firmaManager.teleportHome(player);
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&2&m----------------------------------"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma stworz <nazwa> &7- Tworzy nowa firme."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma zapros <nick> &7- Zaprasza gracza do firmy."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma akceptuj &7- Akceptuje zaproszenie."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma wyrzuc <nick> &7- Wyrzuca gracza z firmy."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma usun &7- Usuwa Twoja firme."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma zastepca <nick> &7- Mianuje zastepce szefa."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma degraduj <nick> &7- Degraduje zastepce."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma info [nazwa/nick] &7- Informacje o firmie."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma wplac <kwota> &7- Wplaca pieniadze do skarbca."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma wyplac <kwota> &7- Wyplaca pieniadze ze skarbca."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma wyplata <procent> &7- Wyplata dla pracownikow (% budzetu)."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma pvp &7- Wlacza/wylacza pvp w firmie."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma opusc &7- Opuszcza firme."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma top &7- Top 10 najbogatszych firm."));

        if (player.hasPermission("firma.home")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma sethome &7- Ustawia dom firmy."));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/firma home &7- Teleportuje do domu firmy."));
        }

        if (player.hasPermission("firmaplugin.admin")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c/firma adminwyrzuc <nick> &7- Wymusza wyrzucenie."));
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c/firma adminusun <nazwa> &7- Wymusza usuniecie firmy."));
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&2&m----------------------------------"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList(
                    "stworz", "zapros", "akceptuj", "wyrzuc", "usun",
                    "zastepca", "degraduj", "info", "wplac", "wyplac",
                    "wyplata", "pvp", "opusc", "top"
            ));

            if (sender.hasPermission("firma.home")) {
                completions.add("sethome");
                completions.add("home");
            }

            if (sender.hasPermission("firmaplugin.admin")) {
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
                case "zastepca":
                case "degraduj":
                case "adminwyrzuc":
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                case "info":
                    // Podpowiadaj nazwy firm + nicki online graczy
                    List<String> infoCompletions = new ArrayList<>();
                    infoCompletions.addAll(firmaManager.getFirmy().keySet());
                    Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .forEach(infoCompletions::add);
                    return infoCompletions.stream()
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .distinct()
                            .collect(Collectors.toList());
                case "adminusun":
                    return firmaManager.getFirmy().keySet().stream()
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }
        return new ArrayList<>();
    }
}

