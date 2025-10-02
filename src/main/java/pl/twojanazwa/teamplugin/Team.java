package pl.twojanazwa.teamplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class Team implements ConfigurationSerializable {
    private final String name;
    private final UUID owner;
    private final Set<UUID> members;
    private final Set<UUID> leaders;
    private boolean pvpEnabled;
    private transient PlayerStatsManager playerStatsManager;
    private Location home;


    public Team(String name, UUID owner, PlayerStatsManager playerStatsManager) {
        this.name = name;
        this.owner = owner;
        this.members = new HashSet<>();
        this.leaders = new HashSet<>();
        this.members.add(owner);
        this.leaders.add(owner);
        this.pvpEnabled = true;
        this.playerStatsManager = playerStatsManager;
        this.home = null;
    }

    // Konstruktor do wczytywania danych
    @SuppressWarnings("unchecked")
    public Team(Map<String, Object> map) {
        this.name = (String) map.get("name");
        this.owner = UUID.fromString((String) map.get("owner"));
        this.members = ((List<String>) map.get("members")).stream().map(UUID::fromString).collect(Collectors.toSet());
        this.leaders = ((List<String>) map.get("leaders")).stream().map(UUID::fromString).collect(Collectors.toSet());
        this.pvpEnabled = (boolean) map.get("pvpEnabled");
        if (map.containsKey("home")) {
            this.home = (Location) map.get("home");
        }
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("owner", owner.toString());
        map.put("members", members.stream().map(UUID::toString).collect(Collectors.toList()));
        map.put("leaders", leaders.stream().map(UUID::toString).collect(Collectors.toList()));
        map.put("pvpEnabled", pvpEnabled);
        if (home != null) {
            map.put("home", home);
        }
        return map;
    }

    public String getName() { return name; }
    public UUID getOwner() { return owner; }
    public Set<UUID> getMembers() { return members; }
    public Set<UUID> getLeaders() { return leaders; }
    public boolean isPvpEnabled() { return pvpEnabled; }
    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }
    public boolean isMember(UUID uuid) { return members.contains(uuid); }
    public boolean isLeader(UUID uuid) { return leaders.contains(uuid); }
    public Location getHome() { return home; }
    public void setHome(Location home) { this.home = home; }
    public void setPlayerStatsManager(PlayerStatsManager playerStatsManager) { this.playerStatsManager = playerStatsManager; }


    public int getPoints() {
        if (members.isEmpty() || playerStatsManager == null) {
            return 0;
        }
        int totalPoints = 0;
        for (UUID memberId : members) {
            totalPoints += playerStatsManager.getPlayerStats(memberId).getPoints();
        }
        return totalPoints / members.size();
    }


    public void addMember(UUID uuid) {
        members.add(uuid);
        playerStatsManager.getPlayerStats(uuid);
    }

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
