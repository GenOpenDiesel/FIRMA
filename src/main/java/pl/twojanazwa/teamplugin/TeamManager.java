package pl.twojanazwa.teamplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TeamManager {

    public final TeamPlugin plugin;
    private final Map<String, Team> teams = new HashMap<>();
    private final Map<UUID, String> invites = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private File teamsFile;
    private FileConfiguration teamsConfig;
    private final PlayerStatsManager playerStatsManager;
    private List<Team> topTeamsCache = new ArrayList<>();

    public TeamManager(TeamPlugin plugin, PlayerStatsManager playerStatsManager) {
        this.plugin = plugin;
        this.playerStatsManager = playerStatsManager;
        createTeamsFile();
        // Uruchomienie schedulera do aktualizacji rankingu co 5 minut
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateTopTeams, 0L, 20L * 60 * 5);
    }

    private String getMessage(String path, String... replacements) {
        String message = plugin.getConfig().getString("messages." + path, "&cWiadomosc nie znaleziona: " + path);
        for (int i = 0; i < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private boolean isAlphanumeric(String str) {
        return str != null && str.matches("^[a-zA-Z0-9]+$");
    }

    private boolean checkCooldown(Player player) {
        if (cooldowns.containsKey(player.getUniqueId())) {
            long secondsLeft = ((cooldowns.get(player.getUniqueId()) / 1000) + 5) - (System.currentTimeMillis() / 1000);
            if (secondsLeft > 0) {
                player.sendMessage(getMessage("cooldown", "%seconds%", String.valueOf(secondsLeft)));
                return true;
            }
        }
        return false;
    }

    private void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public Map<String, Team> getTeams() {
        return Collections.unmodifiableMap(teams);
    }

    public void createTeam(Player player, String name) {
        if (getTeamByPlayer(player) != null) {
            player.sendMessage(getMessage("juz-w-teamie"));
            return;
        }

        if (teams.containsKey(name.toLowerCase())) {
            player.sendMessage(getMessage("nazwa-zajeta"));
            return;
        }

        if (!isAlphanumeric(name) || name.length() < 2 || name.length() > 16) {
            player.sendMessage(getMessage("nazwa-nieprawidlowa"));
            return;
        }


        List<ItemStack> requiredItems = new ArrayList<>();
        List<String> requiredItemsMsg = new ArrayList<>();
        for (String itemString : plugin.getConfig().getStringList("creation-cost.items")) {
            String[] parts = itemString.split(":");
            if (parts.length == 2) {
                try {
                    Material material = Material.valueOf(parts[0].toUpperCase());
                    int amount = Integer.parseInt(parts[1]);
                    requiredItems.add(new ItemStack(material, amount));
                    requiredItemsMsg.add(amount + "x " + material.name());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Nieprawidłowy materiał w config.yml: " + parts[0]);
                }
            }
        }

        if (!hasEnoughItems(player, requiredItems)) {
            player.sendMessage(getMessage("brak-przedmiotow"));
            player.sendMessage(ChatColor.GRAY + "Potrzebujesz: " + String.join(", ", requiredItemsMsg));
            return;
        }

        removeItems(player, requiredItems);
        Team team = new Team(name, player.getUniqueId(), playerStatsManager);
        teams.put(name.toLowerCase(), team);
        Bukkit.broadcastMessage(getMessage("team-stworzony-globalnie", "%player%", player.getName(), "%nazwa%", name));
    }

    private boolean hasEnoughItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (!player.getInventory().containsAtLeast(item, item.getAmount())) {
                return false;
            }
        }
        return true;
    }

    private void removeItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            player.getInventory().removeItem(item);
        }
    }

    public void invitePlayer(Player leader, String targetName) {
        if (checkCooldown(leader)) return;
        Team team = getTeamByPlayer(leader);
        if (team == null || !team.isLeader(leader.getUniqueId())) {
            leader.sendMessage(getMessage("nie-jestes-liderem"));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            leader.sendMessage(getMessage("gracz-offline"));
            return;
        }

        if (getTeamByPlayer(target) != null) {
            leader.sendMessage(getMessage("gracz-juz-w-teamie"));
            return;
        }

        invites.put(target.getUniqueId(), team.getName());
        leader.sendMessage(getMessage("zaproszono-gracza", "%player%", target.getName()));
        target.sendMessage(getMessage("otrzymano-zaproszenie", "%nazwa%", team.getName()));
        setCooldown(leader);
    }

    public void acceptInvite(Player player) {
        if (checkCooldown(player)) return;
        if (!invites.containsKey(player.getUniqueId())) {
            player.sendMessage(getMessage("brak-zaproszen"));
            return;
        }

        if (getTeamByPlayer(player) != null) {
            player.sendMessage(getMessage("juz-w-teamie"));
            invites.remove(player.getUniqueId());
            return;
        }

        String teamName = invites.get(player.getUniqueId());
        Team team = teams.get(teamName.toLowerCase());

        if (team == null) {
            player.sendMessage(getMessage("team-nie-istnieje"));
            invites.remove(player.getUniqueId());
            return;
        }

        team.addMember(player.getUniqueId());
        invites.remove(player.getUniqueId());
        Bukkit.broadcastMessage(getMessage("gracz-dolaczyl-globalnie", "%player%", player.getName(), "%nazwa%", team.getName()));
        setCooldown(player);
    }

    public void kickPlayer(Player leader, String targetName) {
        if (checkCooldown(leader)) return;
        Team team = getTeamByPlayer(leader);
        if (team == null || !team.isLeader(leader.getUniqueId())) {
            leader.sendMessage(getMessage("nie-jestes-liderem"));
            return;
        }

        UUID targetUUID = null;
        String targetRealName = targetName;

        // 1. Sprawdzamy czy gracz jest online (najdokładniejsze)
        Player targetOnline = Bukkit.getPlayer(targetName);
        if (targetOnline != null) {
            targetUUID = targetOnline.getUniqueId();
            targetRealName = targetOnline.getName();
        } else {
            // 2. Jeśli offline, szukamy go na liście członków teamu po nicku
            // To naprawia problem z wyrzucaniem graczy offline, których Bukkit nie może znaleźć
            for (UUID memberId : team.getMembers()) {
                OfflinePlayer offMember = Bukkit.getOfflinePlayer(memberId);
                if (offMember.getName() != null && offMember.getName().equalsIgnoreCase(targetName)) {
                    targetUUID = memberId;
                    targetRealName = offMember.getName();
                    break;
                }
            }
            
            // 3. Ostateczny fallback (standardowe pobieranie)
            if (targetUUID == null) {
                OfflinePlayer offTarget = Bukkit.getOfflinePlayer(targetName);
                if (team.isMember(offTarget.getUniqueId())) {
                    targetUUID = offTarget.getUniqueId();
                    targetRealName = offTarget.getName();
                }
            }
        }

        if (targetUUID == null || !team.isMember(targetUUID)) {
            leader.sendMessage(getMessage("gracz-nie-w-teamie"));
            return;
        }

        if (targetUUID.equals(team.getOwner())) {
            leader.sendMessage(getMessage("nie-mozna-wyrzucic-zalozyciela"));
            return;
        }

        team.removeMember(targetUUID);
        team.broadcast(getMessage("gracz-wyrzucony", "%player%", targetRealName));
        setCooldown(leader);
    }

    // NOWA FUNKCJA DLA ADMINA
    public void forceKickPlayer(CommandSender sender, String targetName) {
        if (!sender.hasPermission("teamplugin.admin")) {
            sender.sendMessage(ChatColor.RED + "Brak uprawnien! (teamplugin.admin)");
            return;
        }

        Team targetTeam = null;
        UUID targetUUID = null;
        String realName = targetName;

        // Szukamy gracza we wszystkich teamach
        for (Team t : teams.values()) {
            for (UUID memberId : t.getMembers()) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(memberId);
                // Sprawdzamy nick (działa dla offline i online)
                if (off.getName() != null && off.getName().equalsIgnoreCase(targetName)) {
                    targetTeam = t;
                    targetUUID = memberId;
                    realName = off.getName();
                    break;
                }
            }
            if (targetTeam != null) break;
        }

        if (targetTeam == null) {
            sender.sendMessage(ChatColor.RED + "Gracz " + targetName + " nie znajduje sie w zadnym teamie.");
            return;
        }

        targetTeam.removeMember(targetUUID);
        targetTeam.broadcast(getMessage("gracz-wyrzucony", "%player%", realName));
        sender.sendMessage(ChatColor.GREEN + "ADMIN: Wyrzucono gracza " + realName + " z teamu " + targetTeam.getName());
    }

    public void deleteTeam(Player owner) {
        Team team = getTeamByPlayer(owner);
        if (team == null || !team.getOwner().equals(owner.getUniqueId())) {
            owner.sendMessage(getMessage("tylko-zalozyciel-usuniecie"));
            return;
        }

        Bukkit.broadcastMessage(getMessage("team-rozwiazany-globalnie", "%nazwa%", team.getName()));
        teams.remove(team.getName().toLowerCase());
    }

    public void promotePlayer(Player owner, String targetName) {
        Team team = getTeamByPlayer(owner);
        if (team == null || !team.getOwner().equals(owner.getUniqueId())) {
            owner.sendMessage(getMessage("tylko-zalozyciel-lider"));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !team.isMember(target.getUniqueId())) {
            owner.sendMessage(getMessage("gracz-nie-w-teamie"));
            return;
        }

        if(team.isLeader(target.getUniqueId())) {
            owner.sendMessage(getMessage("gracz-juz-liderem"));
            return;
        }

        team.addLeader(target.getUniqueId());
        team.broadcast(getMessage("awans-na-lidera", "%player%", target.getName()));
    }

    public void demotePlayer(Player owner, String targetName) {
        Team team = getTeamByPlayer(owner);
        if (team == null || !team.getOwner().equals(owner.getUniqueId())) {
            owner.sendMessage(getMessage("tylko-zalozyciel-degrad"));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !team.isLeader(target.getUniqueId())) {
            owner.sendMessage(getMessage("gracz-nie-jest-liderem"));
            return;
        }

        team.removeLeader(target.getUniqueId());
        team.broadcast(getMessage("degrad-lidera", "%player%", target.getName()));
    }

    public void getTeamInfo(Player player, String name) {
        Team team = teams.get(name.toLowerCase());
        if(team == null) {
            player.sendMessage(getMessage("team-nie-istnieje"));
            return;
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(team.getOwner());

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&9&m----------------------------------"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eInformacje o teamie: &f" + team.getName()));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eZalozyciel: &f" + (owner.getName() != null ? owner.getName() : "Nieznany")));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&ePunkty: &f" + team.getPoints()));

        String leaders = team.getLeaders().stream()
                .map(Bukkit::getOfflinePlayer)
                .map(p -> p.getName() != null ? p.getName() : "Nieznany")
                .collect(Collectors.joining(", "));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eLiderzy: &f" + leaders));
        
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eCzlonkowie:"));
        team.getMembers().forEach(uuid -> {
            PlayerStats stats = playerStatsManager.getPlayerStats(uuid);
            String memberName = Bukkit.getOfflinePlayer(uuid).getName();
            if (memberName == null) memberName = "Nieznany";
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "  &f- " + memberName + " &7(Zabojstwa: &a" + stats.getKills() + "&7, Smierci: &c" + stats.getDeaths() + "&7, Pkt: &e" + stats.getPoints() + "&7)"));
        });
        
        String pvpStatus = team.isPvpEnabled() ? getMessage("status-pvp-wlaczone") : getMessage("status-pvp-wylaczone");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&ePVP: " + pvpStatus));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&9&m----------------------------------"));
    }

    public void togglePvp(Player player) {
        Team team = getTeamByPlayer(player);
        if (team == null || !team.isLeader(player.getUniqueId())) {
             player.sendMessage(getMessage("nie-jestes-liderem"));
            return;
        }

        team.setPvpEnabled(!team.isPvpEnabled());
        String pvpStatus = team.isPvpEnabled() ? getMessage("status-pvp-wlaczone") : getMessage("status-pvp-wylaczone");
        team.broadcast(getMessage("pvp-zmienione", "%status%", pvpStatus));
    }

    public void leaveTeam(Player player) {
        if (checkCooldown(player)) return;
        Team team = getTeamByPlayer(
