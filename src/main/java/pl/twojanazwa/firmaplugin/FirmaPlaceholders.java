package pl.twojanazwa.firmaplugin;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FirmaPlaceholders extends PlaceholderExpansion {

    private final FirmaPlugin plugin;
    private final FirmaManager firmaManager;

    public FirmaPlaceholders(FirmaPlugin plugin, FirmaManager firmaManager) {
        this.plugin = plugin;
        this.firmaManager = firmaManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "firma";
    }

    @Override
    public @NotNull String getAuthor() {
        return "TwojaNazwa";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        // %firma_gracz_tag%
        if (params.equals("gracz_tag")) {
            Firma firma = firmaManager.getFirmaByPlayer(player);
            return (firma != null) ? "&8[&f" + firma.getName() + "&8]" : "";
        }

        // %firma_vault% — saldo skarbca
        if (params.equals("vault")) {
            Firma firma = firmaManager.getFirmaByPlayer(player);
            return (firma != null) ? String.format("%.2f", firma.getVault()) : "0.00";
        }

        // %firma_members% — liczba członków
        if (params.equals("members")) {
            Firma firma = firmaManager.getFirmaByPlayer(player);
            return (firma != null) ? String.valueOf(firma.getMembers().size()) : "0";
        }

        // %firma_role% — rola gracza w firmie
        if (params.equals("role")) {
            Firma firma = firmaManager.getFirmaByPlayer(player);
            if (firma == null) return "Brak";
            if (firma.isOwner(player.getUniqueId())) return "Szef";
            if (firma.isDeputy(player.getUniqueId())) return "Zastepca";
            return "Czlonek";
        }

        // %firma_top_name_1% ... %firma_top_name_10%
        if (params.startsWith("top_name_")) {
            try {
                int rank = Integer.parseInt(params.substring("top_name_".length()));
                if (rank > 0 && rank <= 10) {
                    List<Firma> topFirmy = firmaManager.getTopFirmy();
                    if (topFirmy.size() >= rank) {
                        return topFirmy.get(rank - 1).getName();
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        // %firma_top_vault_1% ... %firma_top_vault_10%
        if (params.startsWith("top_vault_")) {
            try {
                int rank = Integer.parseInt(params.substring("top_vault_".length()));
                if (rank > 0 && rank <= 10) {
                    List<Firma> topFirmy = firmaManager.getTopFirmy();
                    if (topFirmy.size() >= rank) {
                        return String.format("%.2f", topFirmy.get(rank - 1).getVault());
                    }
                }
            } catch (NumberFormatException ignored) {}
        }

        return "Brak";
    }
}

