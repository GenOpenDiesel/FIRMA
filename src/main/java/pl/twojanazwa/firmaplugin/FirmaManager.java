package pl.twojanazwa.firmaplugin;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FirmaManager {

    public final FirmaPlugin plugin;
    private final Map<String, Firma> firmy = new ConcurrentHashMap<>();
    // Cache dla szybkiego dostępu O(1) — gracz → firma
    private final Map<UUID, Firma> playerFirmaCache = new ConcurrentHashMap<>();

    private final Map<UUID, String> invites = new ConcurrentHashMap<>();
    private final Map<UUID, Long> inviteTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private File firmyFile;
    private FileConfiguration firmyConfig;
    private volatile List<Firma> topFirmyCache = new ArrayList<>();

    private static final long COOLDOWN_SECONDS = 5;
    private static final long INVITE_EXPIRE_MINUTES = 5;
    private static final long CLEANUP_INTERVAL_TICKS = 20L * 60 * 5; // 5 minut

    public FirmaManager(FirmaPlugin plugin) {
        this.plugin = plugin;
        createFirmyFile();

        // Aktualizacja rankingu co 5 minut (async)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateTopFirmy, 0L, 20L * 60 * 5);

        // Czyszczenie cooldowns i zaproszeń co 5 minut (async)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpiredData,
                CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
    }

    // === Czyszczenie wygasłych danych ===
    private void cleanupExpiredData() {
        long currentTime = System.currentTimeMillis();
        long cooldownExpireTime = TimeUnit.SECONDS.toMillis(COOLDOWN_SECONDS);
        long inviteExpireTime = TimeUnit.MINUTES.toMillis(INVITE_EXPIRE_MINUTES);

        cooldowns.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > cooldownExpireTime);

        Iterator<Map.Entry<UUID, Long>> iterator = inviteTimestamps.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (currentTime - entry.getValue() > inviteExpireTime) {
                invites.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    // === Wiadomości z config ===
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
        Long cooldownTime = cooldowns.get(player.getUniqueId());
        if (cooldownTime != null) {
            long secondsLeft = ((cooldownTime / 1000) + COOLDOWN_SECONDS) - (System.currentTimeMillis() / 1000);
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

    public Map<String, Firma> getFirmy() {
        return Collections.unmodifiableMap(firmy);
    }

    // ============================
    //  TWORZENIE FIRMY
    // ============================
    public void createFirma(Player player, String name) {
        if (getFirmaByPlayer(player) != null) {
            player.sendMessage(getMessage("juz-w-firmie"));
            return;
        }

        if (firmy.containsKey(name.toLowerCase())) {
            player.sendMessage(getMessage("nazwa-zajeta"));
            return;
        }

        if (!isAlphanumeric(name) || name.length() < 2 || name.length() > 16) {
            player.sendMessage(getMessage("nazwa-nieprawidlowa"));
            return;
        }

        // Koszt w monetach Vault
        double creationCost = plugin.getConfig().getDouble("creation-cost", 75000);
        Economy econ = FirmaPlugin.getEconomy();
        if (!econ.has(player, creationCost)) {
            player.sendMessage(getMessage("brak-pieniedzy-na-firme", "%kwota%", String.format("%.0f", creationCost)));
            return;
        }

        EconomyResponse response = econ.withdrawPlayer(player, creationCost);
        if (!response.transactionSuccess()) {
            player.sendMessage(getMessage("blad-transakcji"));
            return;
        }
        Firma firma = new Firma(name, player.getUniqueId());
        firmy.put(name.toLowerCase(), firma);
        playerFirmaCache.put(player.getUniqueId(), firma);

        Bukkit.broadcastMessage(getMessage("firma-stworzona-globalnie", "%player%", player.getName(), "%nazwa%", name));
    }


    // ============================
    //  ZAPROSZENIA
    // ============================
    public void invitePlayer(Player leader, String targetName) {
        if (checkCooldown(leader)) return;
        Firma firma = getFirmaByPlayer(leader);
        if (firma == null || !firma.isOwnerOrDeputy(leader.getUniqueId())) {
            leader.sendMessage(getMessage("nie-jestes-szefem-ani-zastepca"));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            leader.sendMessage(getMessage("gracz-offline"));
            return;
        }

        if (getFirmaByPlayer(target) != null) {
            leader.sendMessage(getMessage("gracz-juz-w-firmie"));
            return;
        }

        invites.put(target.getUniqueId(), firma.getName());
        inviteTimestamps.put(target.getUniqueId(), System.currentTimeMillis());

        leader.sendMessage(getMessage("zaproszono-gracza", "%player%", target.getName()));
        target.sendMessage(getMessage("otrzymano-zaproszenie", "%nazwa%", firma.getName()));
        setCooldown(leader);
    }

    public void acceptInvite(Player player) {
        if (checkCooldown(player)) return;
        if (!invites.containsKey(player.getUniqueId())) {
            player.sendMessage(getMessage("brak-zaproszen"));
            return;
        }

        if (getFirmaByPlayer(player) != null) {
            player.sendMessage(getMessage("juz-w-firmie"));
            invites.remove(player.getUniqueId());
            inviteTimestamps.remove(player.getUniqueId());
            return;
        }

        String firmaName = invites.get(player.getUniqueId());
        Firma firma = firmy.get(firmaName.toLowerCase());

        if (firma == null) {
            player.sendMessage(getMessage("firma-nie-istnieje"));
            invites.remove(player.getUniqueId());
            inviteTimestamps.remove(player.getUniqueId());
            return;
        }

        firma.addMember(player.getUniqueId());
        playerFirmaCache.put(player.getUniqueId(), firma);
        invites.remove(player.getUniqueId());
        inviteTimestamps.remove(player.getUniqueId());
        Bukkit.broadcastMessage(getMessage("gracz-dolaczyl-globalnie", "%player%", player.getName(), "%nazwa%", firma.getName()));
        setCooldown(player);
    }

    // ============================
    //  WYRZUCANIE
    // ============================
    public void kickPlayer(Player leader, String targetName) {
        if (checkCooldown(leader)) return;
        Firma firma = getFirmaByPlayer(leader);
        if (firma == null || !firma.isOwnerOrDeputy(leader.getUniqueId())) {
            leader.sendMessage(getMessage("nie-jestes-szefem-ani-zastepca"));
            return;
        }

        UUID targetUUID = null;
        String targetRealName = targetName;

        Player targetOnline = Bukkit.getPlayer(targetName);
        if (targetOnline != null) {
            targetUUID = targetOnline.getUniqueId();
            targetRealName = targetOnline.getName();
        } else {
            for (UUID memberId : firma.getMembers()) {
                OfflinePlayer offMember = Bukkit.getOfflinePlayer(memberId);
                if (offMember.getName() != null && offMember.getName().equalsIgnoreCase(targetName)) {
                    targetUUID = memberId;
                    targetRealName = offMember.getName();
                    break;
                }
            }

            if (targetUUID == null) {
                OfflinePlayer offTarget = Bukkit.getOfflinePlayer(targetName);
                if (firma.isMember(offTarget.getUniqueId())) {
                    targetUUID = offTarget.getUniqueId();
                    targetRealName = offTarget.getName();
                }
            }
        }

        if (targetUUID == null || !firma.isMember(targetUUID)) {
            leader.sendMessage(getMessage("gracz-nie-w-firmie"));
            return;
        }

        if (targetUUID.equals(firma.getOwner())) {
            leader.sendMessage(getMessage("nie-mozna-wyrzucic-szefa"));
            return;
        }

        // Zastępca nie może wyrzucić innego zastępcy
        if (firma.isDeputy(leader.getUniqueId()) && firma.isDeputy(targetUUID)) {
            leader.sendMessage(getMessage("zastepca-nie-moze-wyrzucic-zastepcy"));
            return;
        }

        firma.removeMember(targetUUID);
        playerFirmaCache.remove(targetUUID);
        firma.broadcast(getMessage("gracz-wyrzucony", "%player%", targetRealName));
        setCooldown(leader);
    }

    // ============================
    //  ADMIN: WYRZUCANIE / USUWANIE
    // ============================
    public void forceKickPlayer(CommandSender sender, String targetName) {
        if (!sender.hasPermission("firmaplugin.admin")) {
            sender.sendMessage(ChatColor.RED + "Brak uprawnien! (firmaplugin.admin)");
            return;
        }

        Firma targetFirma = null;
        UUID targetUUID = null;
        String realName = targetName;

        for (Firma f : firmy.values()) {
            for (UUID memberId : f.getMembers()) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(memberId);
                if (off.getName() != null && off.getName().equalsIgnoreCase(targetName)) {
                    targetFirma = f;
                    targetUUID = memberId;
                    realName = off.getName();
                    break;
                }
            }
            if (targetFirma != null) break;
        }

        if (targetFirma == null) {
            sender.sendMessage(ChatColor.RED + "Gracz " + targetName + " nie znajduje sie w zadnej firmie.");
            return;
        }

        targetFirma.removeMember(targetUUID);
        playerFirmaCache.remove(targetUUID);
        targetFirma.broadcast(getMessage("gracz-wyrzucony", "%player%", realName));
        sender.sendMessage(ChatColor.GREEN + "ADMIN: Wyrzucono gracza " + realName + " z firmy " + targetFirma.getName());
    }

    public void forceDeleteFirma(CommandSender sender, String firmaName) {
        if (!sender.hasPermission("firmaplugin.admin")) {
            sender.sendMessage(ChatColor.RED + "Brak uprawnien! (firmaplugin.admin)");
            return;
        }

        Firma firma = firmy.get(firmaName.toLowerCase());
        if (firma == null) {
            sender.sendMessage(ChatColor.RED + "Firma o nazwie " + firmaName + " nie istnieje.");
            return;
        }

        Bukkit.broadcastMessage(getMessage("firma-rozwiazana-globalnie", "%nazwa%", firma.getName()));

        for (UUID memberId : firma.getMembers()) {
            playerFirmaCache.remove(memberId);
        }

        firmy.remove(firma.getName().toLowerCase());
        sender.sendMessage(ChatColor.GREEN + "ADMIN: Usunieto firme " + firma.getName());
    }

    // ============================
    //  USUWANIE FIRMY
    // ============================
    public void deleteFirma(Player owner) {
        Firma firma = getFirmaByPlayer(owner);
        if (firma == null || !firma.isOwner(owner.getUniqueId())) {
            owner.sendMessage(getMessage("tylko-szef-usuniecie"));
            return;
        }

        Bukkit.broadcastMessage(getMessage("firma-rozwiazana-globalnie", "%nazwa%", firma.getName()));

        for (UUID memberId : firma.getMembers()) {
            playerFirmaCache.remove(memberId);
        }

        firmy.remove(firma.getName().toLowerCase());
    }

    // ============================
    //  ZASTĘPCY SZEFA
    // ============================
    public void promoteToDeputy(Player owner, String targetName) {
        Firma firma = getFirmaByPlayer(owner);
        if (firma == null || !firma.isOwner(owner.getUniqueId())) {
            owner.sendMessage(getMessage("tylko-szef-zastepca"));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !firma.isMember(target.getUniqueId())) {
            owner.sendMessage(getMessage("gracz-nie-w-firmie"));
            return;
        }

        if (firma.isDeputy(target.getUniqueId())) {
            owner.sendMessage(getMessage("gracz-juz-zastepca"));
            return;
        }

        firma.addDeputy(target.getUniqueId());
        firma.broadcast(getMessage("awans-na-zastepce", "%player%", target.getName()));
    }

    public void demoteDeputy(Player owner, String targetName) {
        Firma firma = getFirmaByPlayer(owner);
        if (firma == null || !firma.isOwner(owner.getUniqueId())) {
            owner.sendMessage(getMessage("tylko-szef-degrad"));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !firma.isDeputy(target.getUniqueId())) {
            owner.sendMessage(getMessage("gracz-nie-jest-zastepca"));
            return;
        }

        firma.removeDeputy(target.getUniqueId());
        firma.broadcast(getMessage("degrad-zastepcy", "%player%", target.getName()));
    }

    // ============================
    //  SKARBIEC (VAULT) — tylko szef + zastępca
    // ============================
    public void depositToVault(Player player, double amount) {
        if (checkCooldown(player)) return;
        Firma firma = getFirmaByPlayer(player);
        if (firma == null || !firma.isOwnerOrDeputy(player.getUniqueId())) {
            player.sendMessage(getMessage("nie-jestes-szefem-ani-zastepca"));
            return;
        }

        if (amount <= 0) {
            player.sendMessage(getMessage("kwota-nieprawidlowa"));
            return;
        }

        Economy econ = FirmaPlugin.getEconomy();
        if (!econ.has(player, amount)) {
            player.sendMessage(getMessage("brak-pieniedzy"));
            return;
        }

        EconomyResponse response = econ.withdrawPlayer(player, amount);
        if (!response.transactionSuccess()) {
            player.sendMessage(getMessage("blad-transakcji"));
            return;
        }

        firma.depositVault(amount);
        firma.broadcast(getMessage("wplacono-do-skarbca",
                "%player%", player.getName(),
                "%kwota%", String.format("%.2f", amount),
                "%saldo%", String.format("%.2f", firma.getVault())));
        setCooldown(player);
    }

    public void withdrawFromVault(Player player, double amount) {
        if (checkCooldown(player)) return;
        Firma firma = getFirmaByPlayer(player);
        if (firma == null || !firma.isOwnerOrDeputy(player.getUniqueId())) {
            player.sendMessage(getMessage("nie-jestes-szefem-ani-zastepca"));
            return;
        }

        if (amount <= 0) {
            player.sendMessage(getMessage("kwota-nieprawidlowa"));
            return;
        }

        if (!firma.withdrawVault(amount)) {
            player.sendMessage(getMessage("brak-srodkow-w-skarbcu"));
            return;
        }

        Economy econ = FirmaPlugin.getEconomy();
        EconomyResponse response = econ.depositPlayer(player, amount);
        if (!response.transactionSuccess()) {
            // Cofnij wypłatę ze skarbca jeśli nie udało się dodać graczowi
            firma.depositVault(amount);
            player.sendMessage(getMessage("blad-transakcji"));
            return;
        }

        firma.broadcast(getMessage("wyplacono-ze-skarbca",
                "%player%", player.getName(),
                "%kwota%", String.format("%.2f", amount),
                "%saldo%", String.format("%.2f", firma.getVault())));
        setCooldown(player);
    }

    // ============================
    //  WYPŁATA DLA PRACOWNIKÓW
    // ============================
    public void payWorkers(Player boss, double percent) {
        if (checkCooldown(boss)) return;
        Firma firma = getFirmaByPlayer(boss);
        if (firma == null || !firma.isOwner(boss.getUniqueId())) {
            boss.sendMessage(getMessage("tylko-szef-wyplata"));
            return;
        }

        if (percent <= 0 || percent > 100) {
            boss.sendMessage(getMessage("procent-nieprawidlowy"));
            return;
        }

        // Pracownicy = wszyscy członkowie oprócz szefa
        List<UUID> workers = firma.getMembers().stream()
                .filter(uuid -> !firma.isOwner(uuid))
                .collect(Collectors.toList());

        if (workers.isEmpty()) {
            boss.sendMessage(getMessage("brak-pracownikow"));
            return;
        }

        double totalPayout = firma.getVault() * (percent / 100.0);
        if (totalPayout <= 0) {
            boss.sendMessage(getMessage("brak-srodkow-w-skarbcu"));
            return;
        }

        double perWorker = totalPayout / workers.size();

        // Wypłać ze skarbca
        if (!firma.withdrawVault(totalPayout)) {
            boss.sendMessage(getMessage("brak-srodkow-w-skarbcu"));
            return;
        }

        Economy econ = FirmaPlugin.getEconomy();
        int successCount = 0;
        double failedAmount = 0;

        for (UUID workerUUID : workers) {
            OfflinePlayer worker = Bukkit.getOfflinePlayer(workerUUID);
            EconomyResponse response = econ.depositPlayer(worker, perWorker);
            if (response.transactionSuccess()) {
                successCount++;
                // Powiadom online gracza
                Player onlineWorker = Bukkit.getPlayer(workerUUID);
                if (onlineWorker != null && onlineWorker.isOnline()) {
                    onlineWorker.sendMessage(getMessage("otrzymano-wyplate",
                            "%kwota%", String.format("%.2f", perWorker)));
                }
            } else {
                failedAmount += perWorker;
            }
        }

        // Jeśli jakieś transakcje się nie udały, zwróć pieniądze do skarbca
        if (failedAmount > 0) {
            firma.depositVault(failedAmount);
        }

        firma.broadcast(getMessage("wyplata-wykonana",
                "%player%", boss.getName(),
                "%procent%", String.format("%.1f", percent),
                "%kwota%", String.format("%.2f", perWorker),
                "%total%", String.format("%.2f", totalPayout - failedAmount),
                "%pracownicy%", String.valueOf(successCount),
                "%saldo%", String.format("%.2f", firma.getVault())));
        setCooldown(boss);
    }

    // ============================
    //  INFO O FIRMIE
    // ============================
    public void getFirmaInfo(Player player, String name) {
        // Najpierw szukamy po nazwie firmy
        Firma found = firmy.get(name.toLowerCase());

        // Jeśli nie znaleziono po nazwie firmy, szukamy po nicku gracza
        if (found == null) {
            Player targetOnline = Bukkit.getPlayer(name);
            if (targetOnline != null) {
                found = getFirmaByPlayer(targetOnline);
            }
            // Szukaj offline gracza
            if (found == null) {
                for (Firma f : firmy.values()) {
                    for (UUID memberId : f.getMembers()) {
                        OfflinePlayer off = Bukkit.getOfflinePlayer(memberId);
                        if (off.getName() != null && off.getName().equalsIgnoreCase(name)) {
                            found = f;
                            break;
                        }
                    }
                    if (found != null) break;
                }
            }
        }

        if (found == null) {
            player.sendMessage(getMessage("firma-nie-istnieje"));
            return;
        }

        final Firma firma = found;

        OfflinePlayer owner = Bukkit.getOfflinePlayer(firma.getOwner());
        boolean ownerOnline = owner.isOnline();
        String ownerColor = ownerOnline ? "&a" : "&c";

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&2&m----------------------------------"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eInformacje o firmie: &f" + firma.getName()));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eSzef: " + ownerColor + (owner.getName() != null ? owner.getName() : "Nieznany")));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eSkarbiec: &a$" + String.format("%.2f", firma.getVault())));

        String deputies = firma.getDeputies().stream()
                .map(Bukkit::getOfflinePlayer)
                .map(p -> {
                    String deputyName = p.getName() != null ? p.getName() : "Nieznany";
                    String color = p.isOnline() ? "&a" : "&c";
                    return color + deputyName;
                })
                .collect(Collectors.joining("&7, "));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&eZastepcy szefa: " + (deputies.isEmpty() ? "&fBrak" : deputies)));

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&eCzlonkowie (&f" + firma.getMembers().size() + "&e):"));
        firma.getMembers().forEach(uuid -> {
            OfflinePlayer member = Bukkit.getOfflinePlayer(uuid);
            String memberName = member.getName() != null ? member.getName() : "Nieznany";
            boolean online = member.isOnline();
            String nameColor = online ? "&a" : "&c";
            String role;
            if (firma.isOwner(uuid)) role = "&4[Szef]";
            else if (firma.isDeputy(uuid)) role = "&6[Zastepca]";
            else role = "&7[Czlonek]";
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "  " + nameColor + "● " + memberName + " " + role));
        });

        String pvpStatus = firma.isPvpEnabled() ? getMessage("status-pvp-wlaczone") : getMessage("status-pvp-wylaczone");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&ePVP: " + pvpStatus));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&2&m----------------------------------"));
    }

    // ============================
    //  PVP TOGGLE
    // ============================
    public void togglePvp(Player player) {
        Firma firma = getFirmaByPlayer(player);
        if (firma == null || !firma.isOwnerOrDeputy(player.getUniqueId())) {
            player.sendMessage(getMessage("nie-jestes-szefem-ani-zastepca"));
            return;
        }

        firma.setPvpEnabled(!firma.isPvpEnabled());
        String pvpStatus = firma.isPvpEnabled() ? getMessage("status-pvp-wlaczone") : getMessage("status-pvp-wylaczone");
        firma.broadcast(getMessage("pvp-zmienione", "%status%", pvpStatus));
    }

    // ============================
    //  OPUSZCZANIE FIRMY
    // ============================
    public void leaveFirma(Player player) {
        if (checkCooldown(player)) return;
        Firma firma = getFirmaByPlayer(player);
        if (firma == null) {
            player.sendMessage(getMessage("brak-firmy"));
            return;
        }

        if (firma.isOwner(player.getUniqueId())) {
            player.sendMessage(getMessage("szef-nie-moze-opuscic"));
            return;
        }

        firma.removeMember(player.getUniqueId());
        playerFirmaCache.remove(player.getUniqueId());
        Bukkit.broadcastMessage(getMessage("gracz-opuscil-firme-globalnie",
                "%player%", player.getName(), "%nazwa%", firma.getName()));
        setCooldown(player);
    }

    // ============================
    //  HOME
    // ============================
    public void setHome(Player player) {
        Firma firma = getFirmaByPlayer(player);
        if (firma == null || !firma.isOwnerOrDeputy(player.getUniqueId())) {
            player.sendMessage(getMessage("nie-jestes-szefem-ani-zastepca"));
            return;
        }
        firma.setHome(player.getLocation());
        player.sendMessage(getMessage("ustawiono-dom"));
    }

    public void teleportHome(Player player) {
        Firma firma = getFirmaByPlayer(player);
        if (firma == null) {
            player.sendMessage(getMessage("brak-firmy"));
            return;
        }
        if (firma.getHome() == null) {
            player.sendMessage(getMessage("dom-nie-ustawiony"));
            return;
        }
        player.teleport(firma.getHome());
        player.sendMessage(getMessage("teleportowano-do-domu"));
    }

    // ============================
    //  CACHE — szybki dostęp O(1)
    // ============================
    public Firma getFirmaByPlayer(Player player) {
        return getFirmaByPlayer(player.getUniqueId());
    }

    public Firma getFirmaByPlayer(UUID uuid) {
        return playerFirmaCache.get(uuid);
    }

    public Firma getFirmaByName(String name) {
        return firmy.get(name.toLowerCase());
    }

    // ============================
    //  TOP FIRMY
    // ============================
    public void showTopFirmy(Player player) {
        List<Firma> sortedFirmy = getTopFirmy();

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&2&m----------------------------------"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&eTop 10 firm:"));
        for (int i = 0; i < sortedFirmy.size(); i++) {
            Firma firma = sortedFirmy.get(i);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&e" + (i + 1) + ". &f" + firma.getName()
                            + " &7- &a$" + String.format("%.2f", firma.getVault())
                            + " &7(&f" + firma.getMembers().size() + " czl.&7)"));
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&2&m----------------------------------"));
    }

    public List<Firma> getTopFirmy() {
        return topFirmyCache;
    }

    public void updateTopFirmy() {
        List<Firma> newCache = firmy.values().stream()
                .sorted(Comparator.comparingDouble(Firma::getVault).reversed())
                .limit(10)
                .collect(Collectors.toList());
        topFirmyCache = newCache;
    }

    // ============================
    //  ZAPIS / ODCZYT
    // ============================
    private void createFirmyFile() {
        firmyFile = new File(plugin.getDataFolder(), "firmy.yml");
        if (!firmyFile.exists()) {
            try {
                firmyFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nie mozna stworzyc pliku firmy.yml!");
            }
        }
        firmyConfig = YamlConfiguration.loadConfiguration(firmyFile);
    }

    public void saveFirmy() {
        firmyConfig.set("firmy", null);

        for (Map.Entry<String, Firma> entry : firmy.entrySet()) {
            firmyConfig.set("firmy." + entry.getKey(), entry.getValue());
        }
        try {
            firmyConfig.save(firmyFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie mozna zapisac firm do pliku firmy.yml!");
        }
    }

    public void loadFirmy() {
        if (!firmyConfig.isConfigurationSection("firmy")) {
            return;
        }

        playerFirmaCache.clear();

        firmyConfig.getConfigurationSection("firmy").getKeys(false).forEach(key -> {
            Firma firma = (Firma) firmyConfig.get("firmy." + key);
            if (firma != null) {
                firmy.put(key, firma);

                for (UUID memberId : firma.getMembers()) {
                    playerFirmaCache.put(memberId, firma);
                }
            }
        });
        updateTopFirmy();
    }
}

