package mcbesser.energy;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class EnergyPlugin extends JavaPlugin {

    private BatteryService batteryService;
    private UpgradeMenuListener upgradeMenuListener;
    private ChargerListener chargerListener;

    @Override
    public void onEnable() {
        batteryService = new BatteryService(this);
        upgradeMenuListener = new UpgradeMenuListener(this, batteryService);

        RecipeRegistrar.register(this, batteryService);

        chargerListener = new ChargerListener(this, batteryService);
        Bukkit.getPluginManager().registerEvents(chargerListener, this);
        Bukkit.getPluginManager().registerEvents(upgradeMenuListener, this);
        Bukkit.getPluginManager().registerEvents(new RecipeUnlockListener(this), this);
        chargerListener.initializeSavedChargers();
        Bukkit.getScheduler().runTaskTimer(this, chargerListener::syncBatteryHolograms, 20L, 20L);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.discoverRecipe(RecipeRegistrar.batteryRecipeKey(this));
            player.discoverRecipe(RecipeRegistrar.chargerRecipeKey(this));
        }
    }

    @Override
    public void onDisable() {
        if (chargerListener != null) {
            chargerListener.cleanup();
        }
    }

    public NamespacedKey key(String value) {
        return new NamespacedKey(this, value);
    }

    public UpgradeMenuListener getUpgradeMenuListener() {
        return upgradeMenuListener;
    }
}
