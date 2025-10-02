package pl.twojanazwa.teamplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class Team implements ConfigurationSerializable {
    private final String tag;
    private final UUID owner;
    private final Set<UUID> members;
    private final Set<UUID> leaders;
    private boolean pvpEnabled;

    public Team(String tag, UUID owner) {
        this.tag = tag;
        this.owner = owner;
        this.members = new HashSet<>();
        this.leaders = new HashSet<>();
        this.members.add(owner);
        this.leaders.add(owner);
        this.pvpEnabled = true;
    }

    // Konstruktor do wczytywania danych
    public Team(Map<String, Object> map) {
        this.tag = (String) map.get("tag");
        this.owner = UUID.fromString((String) map.get("owner"));
        this.members = ((List<String>) map.get("members")).stream().map(UUID::fromString).collect(Collectors.toSet());
        this.leaders = ((List<String>) map.get("leaders")).stream().map(UUID::fromString).collect(Collectors.toSet());
        this.pvpEnabled = (boolean) map.get("pvpEnabled");
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("tag", tag);
        map.put("owner", owner.toString());
        map.put("members", members.stream().map(UUID::toString).collect(Collectors.toList()));
        map.put("leaders", leaders.stream().map(UUID::toString).collect(Collectors.toList()));
        map.put("pvpEnabled", pvpEnabled);
        return map;
    }

    public String getTag() { return tag; }
    public UUID getOwner() { return owner; }
    public Set<UUID> getMembers() { return members; }
    public Set<UUID> getLeaders() { return leaders; }
    public boolean isPvpEnabled() { return pvpEnabled; }
    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }
    public boolean isMember(UUID uuid) { return members.contains(uuid); }
    public boolean isLeader(UUID uuid) { return leaders.contains(uuid); }

    public void addMember(UUID uuid) { members.add(uuid); }
    public void removeMember(UUID uuid) {
        members.remove(uuid);
        leaders.remove(uuid);
    }
    public void addLeader(UUID uuid) {
        if(members.contains(uuid)) leaders.add(uuid);
    }
    public void removeLeader(UUID uuid) {
        if(!uuid.equals(owner)) leaders.remove(uuid);
    }

    public void broadcast(String message) {
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }
}
