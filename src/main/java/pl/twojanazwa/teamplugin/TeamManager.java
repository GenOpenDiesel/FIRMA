package pl.twojanazwa.teamplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!team.isMember(target.getUniqueId())) {
             leader.sendMessage(getMessage("gracz-nie-w-teamie"));
            return;
        }

        if (target.getUniqueId().equals(team.getOwner())) {
             leader.sendMessage(getMessage("nie-mozna-wyrzucic-zalozyciela"));
            return;
        }

        team.removeMember(target.getUniqueId());
        team.broadcast(getMessage("gracz-wyrzucony", "%player%", target.getName()));
        setCooldown(leader);
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

        Player owner = Bukkit.getPlayer(team.getOwner());

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&9&m----------------------------------"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eInformacje o teamie: &f" + team.getName()));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eZalozyciel: &f" + (owner != null ? owner.getName() : "Offline")));
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
        Team team = getTeamByPlayer(player);
        if(team == null) {
            player.sendMessage(getMessage("brak-teamu"));
            return;
        }

        if(team.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(getMessage("zalozyciel-nie-moze-opuscic"));
            return;
        }

        team.removeMember(player.getUniqueId());
        Bukkit.broadcastMessage(getMessage("gracz-opuscil-team-globalnie", "%player%", player.getName(), "%nazwa%", team.getName()));
        setCooldown(player);
    }

    public void setHome(Player player) {
        Team team = getTeamByPlayer(player);
        if (team == null || !team.isLeader(player.getUniqueId())) {
            player.sendMessage(getMessage("nie-jestes-liderem"));
            return;
        }
        team.setHome(player.getLocation());
        player.sendMessage(getMessage("ustawiono-dom"));
    }

    public void teleportHome(Player player) {
        Team team = getTeamByPlayer(player);
        if (team == null) {
            player.sendMessage(getMessage("brak-teamu"));
            return;
        }
        if (team.getHome() == null) {
            player.sendMessage(getMessage("dom-nie-ustawiony"));
            return;
        }
        player.teleport(team.getHome());
        player.sendMessage(getMessage("teleportowano-do-domu"));
    }

    public Team getTeamByPlayer(Player player) {
        for (Team team : teams.values()) {
            if (team.isMember(player.getUniqueId())) {
                return team;
            }
        }
        return null;
    }
    
    private void createTeamsFile() {
        teamsFile = new File(plugin.getDataFolder(), "teams.yml");
        if (!teamsFile.exists()) {
            try {
                teamsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nie mozna stworzyc pliku teams.yml!");
            }
        }
        teamsConfig = YamlConfiguration.loadConfiguration(teamsFile);
    }

    public void saveTeams() {
        for(Map.Entry<String, Team> entry : teams.entrySet()){
            teamsConfig.set("teams." + entry.getKey(), entry.getValue());
        }
        try {
            teamsConfig.save(teamsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie mozna zapisac teamow do pliku teams.yml!");
        }
    }
    
    public void loadTeams() {
        if (!teamsConfig.isConfigurationSection("teams")) {
            return;
        }
        teamsConfig.getConfigurationSection("teams").getKeys(false).forEach(key -> {
            Team team = (Team) teamsConfig.get("teams." + key);
            if (team != null) {
                team.setPlayerStatsManager(playerStatsManager);
                teams.put(key, team);
            }
        });
        updateTopTeams();
    }

    public Team getTeamByName(String name) {
        return teams.get(name.toLowerCase());
    }

    public void showTopTeams(Player player) {
        List<Team> sortedTeams = getTopTeams();

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&9&m----------------------------------"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eTop 10 teamow:"));
        for (int i = 0; i < sortedTeams.size(); i++) {
            Team team = sortedTeams.get(i);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e" + (i + 1) + ". &f" + team.getName() + " &7- &e" + team.getPoints() + " pkt"));
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&9&m----------------------------------"));
    }

    public List<Team> getTopTeams() {
        return topTeamsCache;
    }

    public void updateTopTeams() {
        topTeamsCache = teams.values().stream()
                .sorted(Comparator.comparingInt(Team::getPoints).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }
}
