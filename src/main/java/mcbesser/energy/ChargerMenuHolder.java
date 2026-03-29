package mcbesser.energy;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ChargerMenuHolder implements InventoryHolder {

    private final Location location;

    public ChargerMenuHolder(Location location) {
        this.location = location;
    }

    public Location location() {
        return location;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
