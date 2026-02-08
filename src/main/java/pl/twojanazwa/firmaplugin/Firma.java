package pl.twojanazwa.firmaplugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class Firma implements ConfigurationSerializable {

    private final String name;
    private final UUID owner;
    private final Set<UUID> members;
    private final Set<UUID> deputies; // zastępcy szefa
    private boolean pvpEnabled;
    private Location home;
    private double vault; // skarbiec firmy

    public Firma(String name, UUID owner) {
        this.name = name;
        this.owner = owner;
        this.members = new HashSet<>();
        this.deputies = new HashSet<>();
        this.members.add(owner);
        this.pvpEnabled = true;
        this.home = null;
        this.vault = 0.0;
    }

    @SuppressWarnings("unchecked")
    public Firma(Map<String, Object> map) {
        this.name = (String) map.get("name");
        this.owner = UUID.fromString((String) map.get("owner"));
        this.members = ((List<String>) map.get("members")).stream()
                .map(UUID::fromString).collect(Collectors.toCollection(HashSet::new));
        this.deputies = ((List<String>) map.getOrDefault("deputies", new ArrayList<>())).stream()
                .map(UUID::fromString).collect(Collectors.toCollection(HashSet::new));
        this.pvpEnabled = (boolean) map.get("pvpEnabled");
        this.vault = ((Number) map.getOrDefault("vault", 0.0)).doubleValue();
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
        map.put("deputies", deputies.stream().map(UUID::toString).collect(Collectors.toList()));
        map.put("pvpEnabled", pvpEnabled);
        map.put("vault", vault);
        if (home != null) {
            map.put("home", home);
        }
        return map;
    }

    // === Gettery ===
    public String getName() { return name; }
    public UUID getOwner() { return owner; }
    public Set<UUID> getMembers() { return members; }
    public Set<UUID> getDeputies() { return deputies; }
    public boolean isPvpEnabled() { return pvpEnabled; }
    public Location getHome() { return home; }
    public double getVault() { return vault; }

    // === Settery ===
    public void setPvpEnabled(boolean pvpEnabled) { this.pvpEnabled = pvpEnabled; }
    public void setHome(Location home) { this.home = home; }

    // === Sprawdzenia ról ===
    public boolean isMember(UUID uuid) { return members.contains(uuid); }
    public boolean isDeputy(UUID uuid) { return deputies.contains(uuid); }
    public boolean isOwner(UUID uuid) { return owner.equals(uuid); }
    public boolean isOwnerOrDeputy(UUID uuid) { return isOwner(uuid) || isDeputy(uuid); }

    // === Skarbiec (vault) ===
    public void depositVault(double amount) {
        this.vault += amount;
    }

    public boolean withdrawVault(double amount) {
        if (this.vault >= amount) {
            this.vault -= amount;
            return true;
        }
        return false;
    }

    // === Zarządzanie członkami ===
    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        deputies.remove(uuid);
    }

    public void addDeputy(UUID uuid) {
        if (members.contains(uuid) && !owner.equals(uuid)) {
            deputies.add(uuid);
        }
    }

    public void removeDeputy(UUID uuid) {
        deputies.remove(uuid);
    }

    // === Broadcast do członków ===
    public void broadcast(String message) {
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }
}

