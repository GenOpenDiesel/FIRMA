package pl.twojanazwa.teamplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Team {
    private final String tag;
    private final UUID owner;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> leaders = new HashSet<>();
    private boolean pvpEnabled = true;

    public Team(String tag, UUID owner) {
        this.tag = tag;
        this.owner = owner;
        this.members.add(owner);
        this.leaders.add(owner);
    }

    public String getTag() {
        return tag;
    }

    public UUID getOwner() {
        return owner;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Set<UUID> getLeaders() {
        return leaders;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvp(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }
    
    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isLeader(UUID uuid) {
        return leaders.contains(uuid);
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        leaders.remove(uuid); // If they were a leader, demote them on leave/kick
    }
    
    public void addLeader(UUID uuid) {
        if(members.contains(uuid)) {
            leaders.add(uuid);
        }
    }
    
    public void removeLeader(UUID uuid) {
        if(!uuid.equals(owner)) { // Owner cannot be demoted
            leaders.remove(uuid);
        }
    }

    public void broadcast(String message) {
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        }
    }
}
