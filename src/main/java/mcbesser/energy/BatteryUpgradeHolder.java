package mcbesser.energy;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class BatteryUpgradeHolder implements InventoryHolder {

    private final UUID playerId;

    public BatteryUpgradeHolder(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
