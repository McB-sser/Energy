package mcbesser.energy;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;

public final class RecipeRegistrar {

    private RecipeRegistrar() {
    }

    public static NamespacedKey batteryRecipeKey(EnergyPlugin plugin) {
        return new NamespacedKey(plugin, "battery_recipe");
    }

    public static NamespacedKey chargerRecipeKey(EnergyPlugin plugin) {
        return new NamespacedKey(plugin, "charger_recipe");
    }

    public static void register(EnergyPlugin plugin, BatteryService batteryService) {
        ShapedRecipe batteryRecipe = new ShapedRecipe(
            batteryRecipeKey(plugin),
            batteryService.createBattery()
        );
        batteryRecipe.shape(" I ", " Q ", " R ");
        batteryRecipe.setIngredient('I', Material.IRON_INGOT);
        batteryRecipe.setIngredient('Q', Material.QUARTZ);
        batteryRecipe.setIngredient('R', Material.REDSTONE);
        plugin.getServer().addRecipe(batteryRecipe);

        ShapedRecipe chargerRecipe = new ShapedRecipe(
            chargerRecipeKey(plugin),
            batteryService.createChargerItem()
        );
        chargerRecipe.shape("CCC", "CBC", "CCC");
        chargerRecipe.setIngredient('C', Material.COPPER_INGOT);
        chargerRecipe.setIngredient('B', Material.BLAST_FURNACE);
        plugin.getServer().addRecipe(chargerRecipe);
    }
}
