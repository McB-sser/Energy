package mcbesser.energy;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class BatteryService {

    private static final int DEFAULT_MAX_CHARGE = 1000;
    private static final int CAPACITY_PER_UNBREAKING_LEVEL = 500;

    private final NamespacedKey batteryKey;
    private final NamespacedKey chargeKey;
    private final NamespacedKey maxChargeKey;
    private final NamespacedKey chargerKey;
    private final NamespacedKey batteryUpgradeLevelKey;

    public BatteryService(EnergyPlugin plugin) {
        this.batteryKey = plugin.key("battery");
        this.chargeKey = plugin.key("battery_charge");
        this.maxChargeKey = plugin.key("battery_max_charge");
        this.chargerKey = plugin.key("charger");
        this.batteryUpgradeLevelKey = plugin.key("battery_upgrade_level");
    }

    public ItemStack createBattery() {
        ItemStack item = new ItemStack(Material.CREAKING_HEART);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(batteryKey, PersistentDataType.BYTE, (byte) 1);
        container.set(chargeKey, PersistentDataType.INTEGER, 0);
        container.set(batteryUpgradeLevelKey, PersistentDataType.INTEGER, 0);
        container.set(maxChargeKey, PersistentDataType.INTEGER, DEFAULT_MAX_CHARGE);
        item.setItemMeta(meta);
        updateBatteryMeta(item);
        return item;
    }

    public ItemStack createBatteryDisplay(boolean glowing) {
        ItemStack item = new ItemStack(Material.CREAKING_HEART);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Batterie", NamedTextColor.AQUA));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        if (glowing) {
            meta.addEnchant(Enchantment.INFINITY, 1, true);
        } else {
            meta.removeEnchant(Enchantment.INFINITY);
        }
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createChargerItem() {
        ItemStack item = new ItemStack(Material.BLAST_FURNACE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Ladeger\u00e4t", NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("L\u00e4dt Batterien mit Brennstoff.", NamedTextColor.GRAY));
        lore.add(Component.text("Effizienz-Buch erh\u00f6ht Energie pro Sekunde.", NamedTextColor.LIGHT_PURPLE));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.getPersistentDataContainer().set(chargerKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBattery(ItemStack item) {
        if (item == null || item.getType() != Material.CREAKING_HEART || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer().get(batteryKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public boolean isChargerItem(ItemStack item) {
        if (item == null || item.getType() != Material.BLAST_FURNACE || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer().get(chargerKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public NamespacedKey chargerKey() {
        return chargerKey;
    }

    public int getCharge(ItemStack item) {
        if (!isBattery(item)) {
            return 0;
        }
        Integer charge = item.getItemMeta().getPersistentDataContainer().get(chargeKey, PersistentDataType.INTEGER);
        return charge == null ? 0 : charge;
    }

    public int getMaxCharge(ItemStack item) {
        if (!isBattery(item)) {
            return DEFAULT_MAX_CHARGE;
        }
        Integer maxCharge = item.getItemMeta().getPersistentDataContainer().get(maxChargeKey, PersistentDataType.INTEGER);
        return maxCharge == null ? DEFAULT_MAX_CHARGE : maxCharge;
    }

    public int getBatteryUpgradeLevel(ItemStack item) {
        if (!isBattery(item)) {
            return 0;
        }
        Integer level = item.getItemMeta().getPersistentDataContainer().get(batteryUpgradeLevelKey, PersistentDataType.INTEGER);
        return level == null ? 0 : level;
    }

    public ItemStack addCharge(ItemStack battery, int amount) {
        if (!isBattery(battery)) {
            return battery;
        }
        ItemStack copy = battery.clone();
        ItemMeta meta = copy.getItemMeta();
        int maxCharge = getMaxCharge(copy);
        int newCharge = Math.min(maxCharge, Math.max(0, getCharge(copy) + amount));
        meta.getPersistentDataContainer().set(chargeKey, PersistentDataType.INTEGER, newCharge);
        copy.setItemMeta(meta);
        updateBatteryMeta(copy);
        return copy;
    }

    public ItemStack applyBatteryUpgrade(ItemStack battery, int unbreakingLevel) {
        if (!isBattery(battery)) {
            return battery;
        }
        ItemStack copy = battery.clone();
        ItemMeta meta = copy.getItemMeta();
        int clampedLevel = Math.max(0, Math.min(5, unbreakingLevel));
        int newMaxCharge = DEFAULT_MAX_CHARGE + (CAPACITY_PER_UNBREAKING_LEVEL * clampedLevel);
        int currentCharge = Math.min(getCharge(copy), newMaxCharge);

        meta.getPersistentDataContainer().set(batteryUpgradeLevelKey, PersistentDataType.INTEGER, clampedLevel);
        meta.getPersistentDataContainer().set(maxChargeKey, PersistentDataType.INTEGER, newMaxCharge);
        meta.getPersistentDataContainer().set(chargeKey, PersistentDataType.INTEGER, currentCharge);
        copy.setItemMeta(meta);
        updateBatteryMeta(copy);
        return copy;
    }

    public ItemStack createBatteryUpgradeBook(int level) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
        meta.addStoredEnchant(Enchantment.UNBREAKING, Math.max(1, Math.min(5, level)), true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createEfficiencyBook(int level) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
        meta.addStoredEnchant(Enchantment.EFFICIENCY, Math.max(1, Math.min(5, level)), true);
        item.setItemMeta(meta);
        return item;
    }

    public void updateBatteryMeta(ItemStack item) {
        if (!isBattery(item)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        int charge = getCharge(item);
        int maxCharge = getMaxCharge(item);
        int upgradeLevel = getBatteryUpgradeLevel(item);

        meta.displayName(Component.text("Batterie", NamedTextColor.AQUA));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Ladung: " + charge + " / " + maxCharge, NamedTextColor.GRAY));
        lore.add(Component.text("Kapazit\u00e4t: Haltbarkeit " + upgradeLevel, NamedTextColor.DARK_PURPLE));
        lore.add(Component.text("Rechtsklick \u00f6ffnet Akku-Men\u00fc.", NamedTextColor.YELLOW));
        meta.lore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addEnchant(Enchantment.INFINITY, 1, true);

        item.setItemMeta(meta);
    }
}
