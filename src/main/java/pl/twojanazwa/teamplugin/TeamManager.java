package pl.twojanazwa.teamplugin;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;

public class TeamManager {

    private final Map<String, Team> teams = new HashMap<>();
    private final Map<UUID, String> invites = new HashMap<>();
    private final double creationCost = 50000.0;
    private final TeamPlugin plugin;

    public TeamManager(TeamPlugin plugin) {
        this.plugin = plugin;
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public void createTeam(Player player, String tag) {
        if (getTeamByPlayer(player) != null) {
            player.sendMessage(color("&9Team &8» &7Jestes juz w teamie."));
            return;
        }

        if (teams.containsKey(tag.toLowerCase())) {
            player.sendMessage(color("&9Team &8» &7Team o takim tagu juz istnieje."));
            return;
        }

        Economy econ = TeamPlugin.getEconomy();
        if (econ.getBalance(player) < creationCost) {
            player.sendMessage(color("&9Team &8» &7Nie masz wystarczajaco pieniedzy (&e50k&7)."));
            return;
        }

        EconomyResponse r = econ.withdrawPlayer(player, creationCost);
        if (r.transactionSuccess()) {
            Team team = new Team(tag, player.getUniqueId());
            teams.put(tag.toLowerCase(), team);
            player.sendMessage(color("&9Team &8» &7Stworzyles team o tagu: &e" + tag));
        } else {
            player.sendMessage(color("&9Team &8» &7Wystapil blad podczas pobierania pieniedzy."));
        }
    }

    public void invitePlayer(Player leader, String targetName) {
        Team team = getTeamByPlayer(leader);
        if (team == null || !team.isLeader(leader.getUniqueId())) {
            leader.sendMessage(color("&9Team &8» &7Nie jestes liderem teamu."));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            leader.sendMessage(color("&9Team &8» &7Gracz jest offline."));
            return;
        }

        if (getTeamByPlayer(target) != null) {
            leader.sendMessage(color("&9Team &8» &7Ten gracz jest juz w innym teamie."));
            return;
        }

        invites.put(target.getUniqueId(), team.getTag());
        leader.sendMessage(color("&9Team &8» &7Zaproszono gracza &e" + target.getName() + "&7 do teamu."));
        target.sendMessage(color("&9Team &8» &7Otrzymales zaproszenie do teamu &e" + team.getTag() + "&7."));
        target.sendMessage(color("&9Team &8» &7Wpisz &e/team akceptuj&7, aby dolaczyc."));
    }
    
    public void acceptInvite(Player player) {
        if (!invites.containsKey(player.getUniqueId())) {
            player.sendMessage(color("&9Team &8» &7Nie masz zadnych zaproszen."));
            return;
        }

        String teamTag = invites.get(player.getUniqueId());
        Team team = teams.get(teamTag.toLowerCase());

        if (team == null) {
            player.sendMessage(color("&9Team &8» &7Team, do ktorego zostales zaproszony, juz nie istnieje."));
            invites.remove(player.getUniqueId());
            return;
        }
        
        team.addMember(player.getUniqueId());
        invites.remove(player.getUniqueId());
        player.sendMessage(color("&9Team &8» &7Dolaczyles do teamu &e" + team.getTag() + "&7."));
        team.broadcast(color("&9Team &8» &7Gracz &e" + player.getName() + " &7dolaczyl do teamu."));
    }

    public void kickPlayer(Player leader, String targetName) {
        Team team = getTeamByPlayer(leader);
        if (team == null || !team.isLeader(leader.getUniqueId())) {
            leader.sendMessage(color("&9Team &8» &7Nie jestes liderem teamu."));
            return;
        }
        
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !team.isMember(target.getUniqueId())) {
            leader.sendMessage(color("&9Team &8» &7Tego gracza nie ma w Twoim teamie."));
            return;
        }

        if (target.getUniqueId().equals(team.getOwner())) {
             leader.sendMessage(color("&9Team &8» &7Nie mozesz wyrzucic zalozyciela."));
            return;
        }

        team.removeMember(target.getUniqueId());
        team.broadcast(color("&9Team &8» &7Gracz &e" + target.getName() + "&7 zostal wyrzucony."));
    }

    public void deleteTeam(Player owner) {
        Team team = getTeamByPlayer(owner);
        if (team == null || !team.getOwner().equals(owner.getUniqueId())) {
            owner.sendMessage(color("&9Team &8» &7Tylko zalozyciel moze usunac team."));
            return;
        }
        
        team.broadcast(color("&9Team &8» &7Team zostal rozwiazany."));
        teams.remove(team.getTag().toLowerCase());
    }
    
    public void promotePlayer(Player owner, String targetName) {
        Team team = getTeamByPlayer(owner);
        if (team == null || !team.getOwner().equals(owner.getUniqueId())) {
            owner.sendMessage(color("&9Team &8» &7Tylko zalozyciel moze nadawac lidera."));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !team.isMember(target.getUniqueId())) {
            owner.sendMessage(color("&9Team &8» &7Tego gracza nie ma w Twoim teamie."));
            return;
        }

        if(team.isLeader(target.getUniqueId())) {
            owner.sendMessage(color("&9Team &8» &7Ten gracz jest juz liderem."));
            return;
        }
        
        team.addLeader(target.getUniqueId());
        team.broadcast(color("&9Team &8» &7Gracz &e" + target.getName() + "&7 zostal nowym liderem."));
    }

    public void demotePlayer(Player owner, String targetName) {
        Team team = getTeamByPlayer(owner);
        if (team == null || !team.getOwner().equals(owner.getUniqueId())) {
            owner.sendMessage(color("&9Team &8» &7Tylko zalozyciel moze degradowac."));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !team.isLeader(target.getUniqueId())) {
            owner.sendMessage(color("&9Team &8» &7Ten gracz nie jest liderem."));
            return;
        }
        
        team.removeLeader(target.getUniqueId());
        team.broadcast(color("&9Team &8» &7Gracz &e" + target.getName() + "&7 nie jest juz liderem."));
    }

    public void getTeamInfo(Player player, String tag) {
        Team team = teams.get(tag.toLowerCase());
        if(team == null) {
            player.sendMessage(color("&9Team &8» &7Team o takim tagu nie istnieje."));
            return;
        }

        Player owner = Bukkit.getPlayer(team.getOwner());

        player.sendMessage(color("&9&m----------------------------------"));
        player.sendMessage(color("&eInformacje o teamie: &f" + team.getTag()));
        player.sendMessage(color("&eZalozyciel: &f" + (owner != null ? owner.getName() : "Offline")));
        
        List<String> leaderNames = new ArrayList<>();
        team.getLeaders().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if(p != null) leaderNames.add(p.getName());
        });
        player.sendMessage(color("&eLiderzy: &f" + String.join(", ", leaderNames)));

        List<String> memberNames = new ArrayList<>();
        team.getMembers().forEach(uuid -> {
             Player p = Bukkit.getPlayer(uuid);
             if(p != null && !team.isLeader(uuid) && !team.getOwner().equals(uuid)) memberNames.add(p.getName());
        });
        player.sendMessage(color("&eCzlonkowie: &f" + String.join(", ", memberNames)));
        player.sendMessage(color("&ePVP: " + (team.isPvpEnabled() ? "&aWlaczone" : "&cWylaczone")));
        player.sendMessage(color("&9&m----------------------------------"));
    }
    
    public void togglePvp(Player player) {
        Team team = getTeamByPlayer(player);
        if (team == null || !team.isLeader(player.getUniqueId())) {
             player.sendMessage(color("&9Team &8» &7Nie jestes liderem teamu."));
            return;
        }
        
        team.setPvp(!team.isPvpEnabled());
        team.broadcast(color("&9Team &8» &7PVP w teamie zostalo " + (team.isPvpEnabled() ? "&awlaczone" : "&cwylaczone") + "&7."));
    }

    public void leaveTeam(Player player) {
        Team team = getTeamByPlayer(player);
        if(team == null) {
            player.sendMessage(color("&9Team &8» &7Nie jestes w zadnym teamie."));
            return;
        }

        if(team.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(color("&9Team &8» &7Nie mozesz opuscic teamu, jestes zalozycielem. Uzyj /team usun."));
            return;
        }
        
        team.removeMember(player.getUniqueId());
        team.broadcast(color("&9Team &8» &7Gracz &e" + player.getName() + " &7opuscil team."));
        player.sendMessage(color("&9Team &8» &7Opusciles team."));
    }
    
    public Team getTeamByPlayer(Player player) {
        for (Team team : teams.values()) {
            if (team.isMember(player.getUniqueId())) {
                return team;
            }
        }
        return null;
    }
}
