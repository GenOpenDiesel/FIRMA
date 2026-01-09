package pl.twojanazwa.teamplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerDeathListener implements Listener {

    private final PlayerStatsManager playerStatsManager;
    private final Map<UUID, Map<UUID, Long>> lastKillTimestamps = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private static final long CLEANUP_INTERVAL_TICKS = 20L * 60 * 10; // 10 minut

    public PlayerDeathListener(PlayerStatsManager playerStatsManager, JavaPlugin plugin) {
        this.playerStatsManager = playerStatsManager;
        
        // Scheduler do czyszczenia starych timestamps - zapobiega wyciekowi pamięci
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupOldTimestamps, 
            CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
    }

    private void cleanupOldTimestamps() {
        long currentTime = System.currentTimeMillis();
        
        Iterator<Map.Entry<UUID, Map<UUID, Long>>> outerIterator = lastKillTimestamps.entrySet().iterator();
        while (outerIterator.hasNext()) {
            Map.Entry<UUID, Map<UUID, Long>> entry = outerIterator.next();
            Map<UUID, Long> victimMap = entry.getValue();
            
            // Usuń stare wpisy z wewnętrznej mapy
            victimMap.entrySet().removeIf(e -> currentTime - e.getValue() > COOLDOWN_MILLIS);
            
            // Jeśli mapa jest pusta, usuń cały wpis
            if (victimMap.isEmpty()) {
                outerIterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        PlayerStats victimStats = playerStatsManager.getPlayerStats(victim.getUniqueId());
        victimStats.addDeath();

        // Sprawdzenie czy zabójca to gracz i nie jest to samobójstwo
        if (killer != null && !killer.equals(victim)) {
            long currentTime = System.currentTimeMillis();
            Map<UUID, Long> victimKillHistory = lastKillTimestamps.computeIfAbsent(
                killer.getUniqueId(), k -> new ConcurrentHashMap<>());

            long lastKillTime = victimKillHistory.getOrDefault(victim.getUniqueId(), 0L);

            int pointsToDeduct = 5;
            int pointsToAdd = 10;

            // Zabezpieczenie: 30 minut cooldownu na tego samego gracza
            if (currentTime - lastKillTime < COOLDOWN_MILLIS) {
                // Jeśli jest cooldown: zerujemy punkty, ale kod wykonuje się dalej (wiadomość)
                pointsToDeduct = 0;
                pointsToAdd = 0;
            } else {
                // Jeśli nie ma cooldownu: aktualizujemy czas ostatniego zabicia
                victimKillHistory.put(victim.getUniqueId(), currentTime);
            }

            // Aktualizacja statystyk ofiary
            if (pointsToDeduct > 0) {
                victimStats.removePoints(pointsToDeduct);
            }
            
            // Aktualizacja statystyk zabójcy
            PlayerStats killerStats = playerStatsManager.getPlayerStats(killer.getUniqueId());
            killerStats.addKill();
            if (pointsToAdd > 0) {
                killerStats.addPoints(pointsToAdd);
            }

            // === TWORZENIE WIADOMOŚCI O ŚMIERCI (GLOBALNEJ) - ADVENTURE ===
            
            ItemStack weaponItem = killer.getInventory().getItemInMainHand();
            Component weaponNameComp;

            // Ustalenie nazwy broni (Component)
            if (weaponItem.getType() != Material.AIR) {
                ItemMeta meta = weaponItem.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    // ZMIANA: Pobieramy Component bezpośrednio z meta, aby zachować kolory Hex/RGB
                    weaponNameComp = meta.displayName();
                    
                    // Fallback gdyby displayName zwrócił null (rzadki przypadek przy hasDisplayName=true)
                    if (weaponNameComp == null) {
                         weaponNameComp = LegacyComponentSerializer.legacySection().deserialize(meta.getDisplayName());
                    }
                } else {
                    // Brak własnej nazwy -> używamy nazwy przedmiotu i kolorujemy na AQUA
                    weaponNameComp = Component.translatable(weaponItem.getType().getTranslationKey())
                            .color(NamedTextColor.AQUA);
                }
            } else {
                // Pusta ręka -> "Reka" na AQUA
                weaponNameComp = Component.text("Reka").color(NamedTextColor.AQUA);
            }

            // Budowanie treści Hovera (Dymka)
            Component hoverContent = Component.text("Uzyta bron: ", NamedTextColor.GRAY)
                    .append(weaponNameComp);

            Map<Enchantment, Integer> enchants = weaponItem.getEnchantments();
            if (!enchants.isEmpty()) {
                hoverContent = hoverContent.append(Component.newline())
                        .append(Component.text("Enchanty:", NamedTextColor.GRAY));
                
                for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                    hoverContent = hoverContent.append(Component.newline())
                            .append(Component.text("- ", NamedTextColor.DARK_GRAY))
                            .append(Component.translatable(entry.getKey().translationKey()).color(NamedTextColor.YELLOW))
                            .append(Component.text(" " + entry.getValue(), NamedTextColor.YELLOW));
                }
            }

            // Dodanie zdarzenia Hover do komponentu nazwy broni
            Component finalWeaponComponent = weaponNameComp.hoverEvent(HoverEvent.showText(hoverContent));

            // Budowanie finalnej wiadomości deathMessage
            Component deathMessage = Component.text("🗡 ", NamedTextColor.DARK_RED)
                    .append(Component.text(victim.getName(), NamedTextColor.RED))
                    .append(Component.text("[-" + pointsToDeduct + "pkt]", NamedTextColor.RED))
                    .append(Component.text(" został zabity przez ", NamedTextColor.GRAY))
                    .append(Component.text(killer.getName(), NamedTextColor.GREEN))
                    .append(Component.text("[+" + pointsToAdd + "pkt]", NamedTextColor.GREEN))
                    .append(Component.text(" używając ", NamedTextColor.GRAY))
                    .append(finalWeaponComponent);

            // Ustawienie wiadomości śmierci (podmienia domyślną wiadomość serwera)
            event.deathMessage(deathMessage);

        } else {
            // Śmierć inna (PvE, upadek itp.) - tylko strata punktów, brak zmiany wiadomości (zostaje domyślna)
            victimStats.removePoints(5);
        }
    }
}
