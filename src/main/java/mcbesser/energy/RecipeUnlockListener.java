package mcbesser.energy;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class RecipeUnlockListener implements Listener {

    private final EnergyPlugin plugin;

    public RecipeUnlockListener(EnergyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(RecipeRegistrar.batteryRecipeKey(plugin));
        event.getPlayer().discoverRecipe(RecipeRegistrar.chargerRecipeKey(plugin));
    }
}
