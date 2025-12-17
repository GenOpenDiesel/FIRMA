package pl.twojanazwa.teamplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerDeathListener implements Listener {

    private final PlayerStatsManager playerStatsManager;
    private final Map<UUID, Map<UUID, Long>> lastKillTimestamps = new HashMap<>();

    public PlayerDeathListener(PlayerStatsManager playerStatsManager) {
        this.playerStatsManager = playerStatsManager;
    }

    // Ustawienie priorytetu na HIGHEST, aby plugin "miał ostatnie słowo" i nie gryzł się z innymi
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        PlayerStats victimStats = playerStatsManager.getPlayerStats(victim.getUniqueId());
        victimStats.addDeath();

        // Sprawdzenie czy zabójca to gracz i nie jest to samobójstwo
        if (killer != null && !killer.equals(victim)) {
            long currentTime = System.currentTimeMillis();
            lastKillTimestamps.putIfAbsent(killer.getUniqueId(), new HashMap<>());
            Map<UUID, Long> victimKillHistory = lastKillTimestamps.get(killer.getUniqueId());

            long lastKillTime = victimKillHistory.getOrDefault(victim.getUniqueId(), 0L);

            // Zabezpieczenie: 30 minut cooldownu na tego samego gracza
            if (currentTime - lastKillTime < TimeUnit.MINUTES.toMillis(30)) {
                return;
            }

            // Aktualizacja czasu i statystyk
            victimKillHistory.put(victim.getUniqueId(), currentTime);
            victimStats.removePoints(5);
            
            PlayerStats killerStats = playerStatsManager.getPlayerStats(killer.getUniqueId());
            killerStats.addKill();
            killerStats.addPoints(10);

            // === TWORZENIE WIADOMOŚCI ADVENTURE API ===
            
            ItemStack weaponItem = killer.getInventory().getItemInMainHand();
            Component weaponNameComp;

            // Ustalenie nazwy broni (Component)
            if (weaponItem.getType() != Material.AIR) {
                ItemMeta meta = weaponItem.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    // Jeśli przedmiot ma własną nazwę (np. z kowadła), konwertujemy ją na Component
                    weaponNameComp = LegacyComponentSerializer.legacyAmpersand().deserialize(meta.getDisplayName());
                } else {
                    // Jeśli nie ma nazwy, używamy klucza tłumaczenia (automatyczne tłumaczenie klienta)
                    weaponNameComp = Component.translatable(weaponItem.getType().getTranslationKey());
                }
            } else {
                weaponNameComp = Component.text("Reka");
            }
            
            // Kolor nazwy broni na turkusowy
            weaponNameComp = weaponNameComp.color(NamedTextColor.AQUA);

            // Budowanie treści Hovera (Dymka)
            Component hoverContent = Component.text("Uzyta bron: ", NamedTextColor.GRAY)
                    .append(weaponNameComp.color(NamedTextColor.WHITE)); // W dymku nazwa na biało

            Map<Enchantment, Integer> enchants = weaponItem.getEnchantments();
            if (!enchants.isEmpty()) {
                hoverContent = hoverContent.append(Component.newline())
                        .append(Component.text("Enchanty:", NamedTextColor.GRAY));
                
                for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                    hoverContent = hoverContent.append(Component.newline())
                            .append(Component.text("- ", NamedTextColor.DARK_GRAY))
                            // Translatable Component dla nazwy enchantu (np. "Ostrosc" / "Sharpness")
                            .append(Component.translatable(entry.getKey().translationKey()).color(NamedTextColor.YELLOW))
                            .append(Component.text(" " + entry.getValue(), NamedTextColor.YELLOW));
                }
            }

            // Dodanie zdarzenia Hover do komponentu nazwy broni
            Component finalWeaponComponent = weaponNameComp.hoverEvent(HoverEvent.showText(hoverContent));

            // Prefiks wiadomości
            Component prefix = Component.text("Team ", NamedTextColor.BLUE)
                    .append(Component.text("» ", NamedTextColor.DARK_GRAY));

            // Wiadomość dla zabójcy
            Component killerMsg = prefix.append(Component.text("Zabiles gracza ", NamedTextColor.GRAY))
                    .append(Component.text(victim.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" (+10 pkt). Bron: ", NamedTextColor.GRAY))
                    .append(finalWeaponComponent);

            // Wiadomość dla ofiary
            Component victimMsg = prefix.append(Component.text("Zostales zabity przez ", NamedTextColor.GRAY))
                    .append(Component.text(killer.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" (-5 pkt). Bron: ", NamedTextColor.GRAY))
                    .append(finalWeaponComponent);

            // Wysłanie wiadomości (Paper API)
            killer.sendMessage(killerMsg);
            victim.sendMessage(victimMsg);

        } else {
            // Śmierć inna (PvE, upadek itp.) - tylko strata punktów
            victimStats.removePoints(5);
        }
    }
}
