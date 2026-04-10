package mcbesser.energy;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import org.bukkit.World;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ChargerListener implements Listener {

    private static final int BUFFER_SLOT = 4;
    private static final int BATTERY_SLOT = 11;
    private static final int FLAME_SLOT = 13;
    private static final int FUEL_SLOT = 15;
    private static final int EFFICIENCY_SLOT = 22;
    private static final int BASE_CHARGE_PER_SECOND = 5;
    private static final int BUFFER_CAPACITY = 250;

    private final EnergyPlugin plugin;
    private final BatteryService batteryService;
    private final Map<Location, Inventory> chargerInventories = new HashMap<>();
    private final Map<Location, BukkitTask> chargeTasks = new HashMap<>();
    private final Map<Location, Integer> remainingBurnTicks = new HashMap<>();
    private final Map<Location, Integer> totalBurnTicks = new HashMap<>();
    private final Map<Location, Integer> bufferCharge = new HashMap<>();
    private final Map<Location, UUID> holograms = new HashMap<>();
    private final File storageFile;
    private final YamlConfiguration storage;

    public ChargerListener(EnergyPlugin plugin, BatteryService batteryService) {
        this.plugin = plugin;
        this.batteryService = batteryService;
        this.storageFile = new File(plugin.getDataFolder(), "chargers.yml");
        this.storage = YamlConfiguration.loadConfiguration(storageFile);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!batteryService.isChargerItem(event.getItemInHand())) {
            return;
        }
        if (!(event.getBlockPlaced().getState() instanceof TileState tileState)) {
            return;
        }

        tileState.getPersistentDataContainer().set(batteryService.chargerKey(), PersistentDataType.BYTE, (byte) 1);
        tileState.update();
        Location location = event.getBlockPlaced().getLocation();
        chargerInventories.put(location, createMenu(location));
        saveCharger(location);
        spawnBatteryHologram(location);
        updateBatteryHologram(location);
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick() || event.getClickedBlock() == null) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block.getType() != Material.BLAST_FURNACE || !(block.getState() instanceof TileState tileState) || !isCharger(tileState)) {
            return;
        }

        event.setCancelled(true);
        spawnBatteryHologram(block.getLocation());
        updateBatteryHologram(block.getLocation());
        event.getPlayer().openInventory(getOrCreateMenu(block.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BLAST_FURNACE || !(block.getState() instanceof TileState tileState) || !isCharger(tileState)) {
            return;
        }

        Location location = block.getLocation();
        stopTask(location);
        removeBatteryHologram(location);
        Inventory menu = chargerInventories.remove(location);
        if (menu != null) {
            dropContents(block, menu);
        }
        deleteCharger(location);
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(location, batteryService.createChargerItem());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ChargerMenuHolder holder)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        if (Objects.equals(event.getClickedInventory(), top)) {
            handleTopClick(event, top);
        } else if (event.isShiftClick()) {
            handleShiftIntoMenu(event, top);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            restorePlaceholders(top);
            saveCharger(holder.location());
            tickCharger(holder.location());
            updateBatteryHologram(holder.location());
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ChargerMenuHolder holder)) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot == BATTERY_SLOT) {
                if (!batteryService.isBattery(event.getOldCursor())) {
                    event.setCancelled(true);
                    return;
                }
            } else if (rawSlot == FUEL_SLOT) {
                if (!isUsableFuel(event.getOldCursor())) {
                    event.setCancelled(true);
                    return;
                }
            } else if (rawSlot == EFFICIENCY_SLOT) {
                if (!isValidEfficiencyBook(event.getOldCursor())) {
                    event.setCancelled(true);
                    return;
                }
            } else if (rawSlot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            restorePlaceholders(event.getView().getTopInventory());
            saveCharger(holder.location());
            tickCharger(holder.location());
            updateBatteryHologram(holder.location());
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ChargerMenuHolder holder)) {
            return;
        }
        restorePlaceholders(event.getInventory());
        saveCharger(holder.location());
        tickCharger(holder.location());
        updateBatteryHologram(holder.location());
    }

    private void handleTopClick(InventoryClickEvent event, Inventory top) {
        int slot = event.getRawSlot();
        if (slot != BATTERY_SLOT && slot != EFFICIENCY_SLOT && slot != FUEL_SLOT) {
            event.setCancelled(true);
            return;
        }

        if (slot == BATTERY_SLOT) {
            handleSlotInteraction(event, top, slot, normalizeBattery(top.getItem(BATTERY_SLOT)), batteryPlaceholder(), SlotKind.BATTERY);
            return;
        }

        if (slot == FUEL_SLOT) {
            handleSlotInteraction(event, top, slot, normalizeFuel(top.getItem(FUEL_SLOT)), fuelPlaceholder(), SlotKind.FUEL);
            return;
        }

        handleSlotInteraction(event, top, slot, normalizeEfficiency(top.getItem(EFFICIENCY_SLOT)), efficiencyPlaceholder(), SlotKind.EFFICIENCY);
    }

    private void handleShiftIntoMenu(InventoryClickEvent event, Inventory top) {
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) {
            return;
        }

        if (batteryService.isBattery(current)) {
            if (normalizeBattery(top.getItem(BATTERY_SLOT)) == null) {
                top.setItem(BATTERY_SLOT, takeOne(current));
                reduceStack(current);
            }
            event.setCancelled(true);
            return;
        }

        if (isValidEfficiencyBook(current)) {
            if (normalizeEfficiency(top.getItem(EFFICIENCY_SLOT)) == null) {
                top.setItem(EFFICIENCY_SLOT, takeOne(current));
                reduceStack(current);
            }
            event.setCancelled(true);
            return;
        }

        if (isUsableFuel(current)) {
            if (normalizeFuel(top.getItem(FUEL_SLOT)) == null) {
                top.setItem(FUEL_SLOT, takeOne(current));
                reduceStack(current);
            }
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
    }

    private Inventory getOrCreateMenu(Location location) {
        return chargerInventories.computeIfAbsent(location, this::loadOrCreateMenu);
    }

    private Inventory loadOrCreateMenu(Location location) {
        Inventory inventory = createMenu(location);
        String path = path(location);
        ItemStack battery = storage.getItemStack(path + ".battery");
        ItemStack fuel = storage.getItemStack(path + ".fuel");
        ItemStack efficiency = storage.getItemStack(path + ".efficiency");

        if (battery != null) {
            inventory.setItem(BATTERY_SLOT, battery);
        }
        if (fuel != null) {
            inventory.setItem(FUEL_SLOT, fuel);
        }
        if (efficiency != null) {
            inventory.setItem(EFFICIENCY_SLOT, efficiency);
        }

        remainingBurnTicks.put(location, Math.max(0, storage.getInt(path + ".burn_ticks", 0)));
        totalBurnTicks.put(location, Math.max(0, storage.getInt(path + ".total_burn_ticks", 0)));
        bufferCharge.put(location, Math.max(0, Math.min(BUFFER_CAPACITY, storage.getInt(path + ".buffer_charge", 0))));
        restorePlaceholders(inventory);
        return inventory;
    }

    private Inventory createMenu(Location location) {
        Inventory inventory = Bukkit.createInventory(
            new ChargerMenuHolder(location),
            27,
            Component.text("Ladeger\u00e4t", NamedTextColor.GOLD)
        );

        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack efficiencyMarker = pane(Material.PURPLE_STAINED_GLASS_PANE, "Effizienz-Buch");
        ItemStack batteryMarker = pane(Material.LIME_STAINED_GLASS_PANE, "Batterie");
        ItemStack fuelMarker = pane(Material.ORANGE_STAINED_GLASS_PANE, "Brennstoff");
        ItemStack flameMarker = idleIcon();
        ItemStack bufferMarker = bufferIcon(0);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(BUFFER_SLOT, bufferMarker);
        inventory.setItem(BATTERY_SLOT, batteryMarker);
        inventory.setItem(FLAME_SLOT, flameMarker);
        inventory.setItem(FUEL_SLOT, fuelMarker);
        inventory.setItem(EFFICIENCY_SLOT, efficiencyMarker);
        return inventory;
    }

    private void tickCharger(Location location) {
        Block block = location.getBlock();
        if (block.getType() != Material.BLAST_FURNACE || !(block.getState() instanceof TileState tileState) || !isCharger(tileState)) {
            stopTask(location);
            chargerInventories.remove(location);
            return;
        }

        Inventory menu = getOrCreateMenu(location);
        ItemStack battery = normalizeBattery(menu.getItem(BATTERY_SLOT));
        ItemStack fuel = normalizeFuel(menu.getItem(FUEL_SLOT));
        int burnTicks = remainingBurnTicks.getOrDefault(location, 0);
        int buffer = bufferCharge.getOrDefault(location, 0);

        if (batteryService.isBattery(battery) && buffer > 0) {
            int freeSpace = Math.max(0, batteryService.getMaxCharge(battery) - batteryService.getCharge(battery));
            int fromBuffer = Math.min(buffer, freeSpace);
            if (fromBuffer > 0) {
                battery = batteryService.addCharge(battery, fromBuffer);
                menu.setItem(BATTERY_SLOT, battery);
                buffer -= fromBuffer;
                bufferCharge.put(location, buffer);
            }
        }

        if (burnTicks <= 0 && batteryService.isBattery(battery) && isUsableFuel(fuel)) {
            burnTicks = consumeFuel(menu, fuel);
            remainingBurnTicks.put(location, burnTicks);
            totalBurnTicks.put(location, burnTicks);
            if (burnTicks > 0) {
                ensureChargeTask(location);
            }
        }

        if (burnTicks <= 0) {
            setLit(block, false);
            setFlameState(menu, false);
            restorePlaceholders(menu);
            stopTask(location);
            updateBatteryHologram(location);
            return;
        }

        setLit(block, true);
        setFlameState(menu, true);
        if (batteryService.isBattery(battery)) {
            int produced = getChargePerSecond(menu);
            int batteryCharge = batteryService.getCharge(battery);
            int batteryMax = batteryService.getMaxCharge(battery);
            int freeSpace = Math.max(0, batteryMax - batteryCharge);

            int fromBuffer = Math.min(buffer, freeSpace);
            if (fromBuffer > 0) {
                battery = batteryService.addCharge(battery, fromBuffer);
                buffer -= fromBuffer;
                freeSpace -= fromBuffer;
            }

            int directToBattery = Math.min(produced, freeSpace);
            if (directToBattery > 0) {
                battery = batteryService.addCharge(battery, directToBattery);
            }

            int overflow = produced - directToBattery;
            if (overflow > 0) {
                buffer = Math.min(BUFFER_CAPACITY, buffer + overflow);
            }

            menu.setItem(BATTERY_SLOT, battery);
            bufferCharge.put(location, buffer);
        }

        burnTicks = Math.max(0, burnTicks - 20);
        remainingBurnTicks.put(location, burnTicks);
        restorePlaceholders(menu);
        menu.setItem(BUFFER_SLOT, bufferIcon(bufferCharge.getOrDefault(location, 0)));

        if (burnTicks <= 0) {
            setLit(block, false);
            setFlameState(menu, false);
            if (!batteryService.isBattery(normalizeBattery(menu.getItem(BATTERY_SLOT))) || !isUsableFuel(normalizeFuel(menu.getItem(FUEL_SLOT)))) {
                stopTask(location);
            }
        }
        saveCharger(location);
        updateBatteryHologram(location);
    }

    private int getChargePerSecond(Inventory menu) {
        int efficiencyLevel = getEfficiencyLevel(normalizeEfficiency(menu.getItem(EFFICIENCY_SLOT)));
        return BASE_CHARGE_PER_SECOND + (efficiencyLevel * 5);
    }

    private int getEfficiencyLevel(ItemStack item) {
        if (!(item != null && item.getItemMeta() instanceof EnchantmentStorageMeta meta)) {
            return 0;
        }
        return Math.max(0, Math.min(5, meta.getStoredEnchantLevel(Enchantment.EFFICIENCY)));
    }

    private void ensureChargeTask(Location location) {
        if (chargeTasks.containsKey(location)) {
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickCharger(location), 20L, 20L);
        chargeTasks.put(location, task);
    }

    private void stopTask(Location location) {
        BukkitTask task = chargeTasks.remove(location);
        if (task != null) {
            task.cancel();
        }
        remainingBurnTicks.remove(location);
        totalBurnTicks.remove(location);
        bufferCharge.remove(location);
    }

    private int consumeFuel(Inventory menu, ItemStack fuel) {
        int burnTicks = getFuelBurnTicks(fuel.getType());
        if (burnTicks <= 0) {
            return 0;
        }

        if (fuel.getType() == Material.LAVA_BUCKET) {
            menu.setItem(FUEL_SLOT, new ItemStack(Material.BUCKET));
            return burnTicks;
        }

        ItemStack updatedFuel = fuel.clone();
        updatedFuel.setAmount(updatedFuel.getAmount() - 1);
        menu.setItem(FUEL_SLOT, updatedFuel.getAmount() <= 0 ? fuelPlaceholder() : updatedFuel);
        return burnTicks;
    }

    private boolean isCharger(TileState tileState) {
        Byte value = tileState.getPersistentDataContainer().get(batteryService.chargerKey(), PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private boolean isUsableFuel(ItemStack item) {
        return item != null && item.getType() != Material.AIR && getFuelBurnTicks(item.getType()) > 0;
    }

    private boolean isValidEfficiencyBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !(item.getItemMeta() instanceof EnchantmentStorageMeta meta)) {
            return false;
        }
        return meta.hasStoredEnchant(Enchantment.EFFICIENCY);
    }

    private boolean isBatteryActionAllowed(ItemStack cursor, ItemStack current) {
        if (cursor == null || cursor.getType() == Material.AIR) {
            return true;
        }
        if (current == null) {
            return batteryService.isBattery(cursor);
        }
        return batteryService.isBattery(cursor) && batteryService.isBattery(current);
    }

    private boolean isFuelActionAllowed(ItemStack cursor, ItemStack current) {
        if (cursor == null || cursor.getType() == Material.AIR) {
            return true;
        }
        if (current == null) {
            return isUsableFuel(cursor) && !batteryService.isBattery(cursor);
        }
        return isUsableFuel(cursor) && !batteryService.isBattery(cursor);
    }

    private boolean isEfficiencyActionAllowed(ItemStack cursor, ItemStack current) {
        if (cursor == null || cursor.getType() == Material.AIR) {
            return true;
        }
        if (current == null) {
            return isValidEfficiencyBook(cursor);
        }
        return isValidEfficiencyBook(cursor);
    }

    private void handleSlotInteraction(
        InventoryClickEvent event,
        Inventory top,
        int slot,
        ItemStack current,
        ItemStack placeholder,
        SlotKind kind
    ) {
        event.setCancelled(true);

        ItemStack cursor = event.getCursor();
        if ((cursor == null || cursor.getType() == Material.AIR) && current != null) {
            event.getWhoClicked().setItemOnCursor(current.clone());
            top.setItem(slot, placeholder);
            return;
        }

        if (cursor == null || cursor.getType() == Material.AIR) {
            return;
        }

        boolean allowed = switch (kind) {
            case BATTERY -> isBatteryActionAllowed(cursor, current);
            case FUEL -> isFuelActionAllowed(cursor, current);
            case EFFICIENCY -> isEfficiencyActionAllowed(cursor, current);
        };
        if (!allowed) {
            return;
        }

        ItemStack one = takeOne(cursor);
        top.setItem(slot, one);
        reduceStack(cursor);
        event.getWhoClicked().setItemOnCursor(cursor.getAmount() <= 0 ? null : cursor);
        if (current != null) {
            event.getWhoClicked().getInventory().addItem(current);
        }
    }

    private ItemStack normalizeBattery(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getType() == Material.LIME_STAINED_GLASS_PANE) {
            return null;
        }
        return item;
    }

    private ItemStack normalizeFuel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getType() == Material.ORANGE_STAINED_GLASS_PANE) {
            return null;
        }
        return item;
    }

    private ItemStack normalizeEfficiency(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getType() == Material.PURPLE_STAINED_GLASS_PANE) {
            return null;
        }
        return item;
    }

    private ItemStack takeOne(ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        return copy;
    }

    private void reduceStack(ItemStack item) {
        item.setAmount(item.getAmount() - 1);
    }

    private void restorePlaceholders(Inventory menu) {
        ChargerMenuHolder holder = (ChargerMenuHolder) menu.getHolder();
        menu.setItem(BUFFER_SLOT, bufferIcon(bufferCharge.getOrDefault(holder.location(), 0)));
        if (normalizeBattery(menu.getItem(BATTERY_SLOT)) == null) {
            menu.setItem(BATTERY_SLOT, batteryPlaceholder());
        }
        if (normalizeEfficiency(menu.getItem(EFFICIENCY_SLOT)) == null) {
            menu.setItem(EFFICIENCY_SLOT, efficiencyPlaceholder());
        }
        if (normalizeFuel(menu.getItem(FUEL_SLOT)) == null) {
            menu.setItem(FUEL_SLOT, fuelPlaceholder());
        }
        if (menu.getItem(FLAME_SLOT) == null || menu.getItem(FLAME_SLOT).getType() == Material.AIR) {
            menu.setItem(FLAME_SLOT, idleIcon());
        }
    }

    private ItemStack batteryPlaceholder() {
        return pane(Material.LIME_STAINED_GLASS_PANE, "Batterie");
    }

    private ItemStack bufferIcon(int charge) {
        ItemStack item = new ItemStack(Material.HEAVY_CORE);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Buffer", NamedTextColor.AQUA));
            meta.lore(java.util.List.of(
                Component.text("Gespeichert: " + charge + " / " + BUFFER_CAPACITY, NamedTextColor.GRAY)
            ));
        });
        return item;
    }

    private ItemStack efficiencyPlaceholder() {
        return pane(Material.PURPLE_STAINED_GLASS_PANE, "Effizienz-Buch");
    }

    private ItemStack flameIcon(int remainingTicks, int totalTicks) {
        ItemStack item = new ItemStack(Material.BLAZE_POWDER);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Ladevorgang", NamedTextColor.GOLD));
            meta.lore(java.util.List.of(
                Component.text(progressBar(remainingTicks, totalTicks), NamedTextColor.GOLD),
                Component.text("Rest: " + Math.max(0, remainingTicks / 20) + "s", NamedTextColor.GRAY)
            ));
        });
        return item;
    }

    private ItemStack idleIcon() {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        item.editMeta(meta -> meta.displayName(Component.text("Kein Stromfluss", NamedTextColor.GRAY)));
        return item;
    }

    private ItemStack fuelPlaceholder() {
        return pane(Material.ORANGE_STAINED_GLASS_PANE, "Brennstoff");
    }

    private ItemStack pane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(Component.text(name, NamedTextColor.GRAY)));
        return item;
    }

    private void dropContents(Block block, Inventory menu) {
        ItemStack battery = normalizeBattery(menu.getItem(BATTERY_SLOT));
        ItemStack efficiency = normalizeEfficiency(menu.getItem(EFFICIENCY_SLOT));
        ItemStack fuel = normalizeFuel(menu.getItem(FUEL_SLOT));
        if (battery != null) {
            block.getWorld().dropItemNaturally(block.getLocation(), battery);
        }
        if (efficiency != null) {
            block.getWorld().dropItemNaturally(block.getLocation(), efficiency);
        }
        if (fuel != null) {
            block.getWorld().dropItemNaturally(block.getLocation(), fuel);
        }
    }

    private void setLit(Block block, boolean lit) {
        BlockData data = block.getBlockData();
        if (data instanceof Lightable lightable) {
            lightable.setLit(lit);
            block.setBlockData(lightable, true);
        }
    }

    private int getFuelBurnTicks(Material material) {
        String name = material.name();
        if (material == Material.LAVA_BUCKET) return 20000;
        if (material == Material.COAL_BLOCK) return 16000;
        if (material == Material.BLAZE_ROD) return 2400;
        if (material == Material.COAL || material == Material.CHARCOAL) return 1600;
        if (material == Material.DRIED_KELP_BLOCK) return 4000;
        if (material == Material.BAMBOO_MOSAIC_SLAB) return 150;
        if (material == Material.BAMBOO || material == Material.SCAFFOLDING) return 50;
        if (material == Material.STICK) return 100;
        if (name.endsWith("_SLAB")) return 150;
        if (name.endsWith("_PLANKS")
            || name.endsWith("_STAIRS")
            || name.endsWith("_FENCE")
            || name.endsWith("_FENCE_GATE")
            || name.endsWith("_TRAPDOOR")
            || name.endsWith("_PRESSURE_PLATE")
            || name.endsWith("_BUTTON")
            || name.endsWith("_SIGN")
            || name.endsWith("_HANGING_SIGN")
            || name.endsWith("_DOOR")
            || name.endsWith("_BOAT")
            || name.endsWith("_CHEST_BOAT")
            || name.endsWith("_LOG")
            || name.endsWith("_WOOD")
            || name.endsWith("_STEM")
            || name.endsWith("_HYPHAE")) return 300;
        if (material == Material.BOWL
            || material == Material.LADDER
            || material == Material.BOW
            || material == Material.FISHING_ROD
            || material == Material.CROSSBOW
            || material == Material.SHIELD
            || material == Material.WOODEN_SHOVEL
            || material == Material.WOODEN_SWORD
            || material == Material.WOODEN_AXE
            || material == Material.WOODEN_PICKAXE
            || material == Material.WOODEN_HOE
            || name.endsWith("_CARPET")
            || name.endsWith("_WOOL")) return 100;
        return 0;
    }

    private enum SlotKind {
        BATTERY,
        FUEL,
        EFFICIENCY
    }

    public void cleanup() {
        for (Location location : chargerInventories.keySet().toArray(Location[]::new)) {
            saveCharger(location);
        }
        saveStorage();
        for (Location location : holograms.keySet().toArray(Location[]::new)) {
            removeBatteryHologram(location);
        }
        for (Location location : chargeTasks.keySet().toArray(Location[]::new)) {
            stopTask(location);
        }
    }

    public void initializeSavedChargers() {
        if (!storageFile.exists()) {
            return;
        }

        for (String worldName : storage.getKeys(false)) {
            World world = Bukkit.getWorld(worldName);
            if (world == null || !storage.isConfigurationSection(worldName)) {
                continue;
            }
            for (String xKey : Objects.requireNonNull(storage.getConfigurationSection(worldName)).getKeys(false)) {
                if (!storage.isConfigurationSection(worldName + "." + xKey)) {
                    continue;
                }
                for (String yKey : Objects.requireNonNull(storage.getConfigurationSection(worldName + "." + xKey)).getKeys(false)) {
                    if (!storage.isConfigurationSection(worldName + "." + xKey + "." + yKey)) {
                        continue;
                    }
                    for (String zKey : Objects.requireNonNull(storage.getConfigurationSection(worldName + "." + xKey + "." + yKey)).getKeys(false)) {
                        try {
                            Location location = new Location(
                                world,
                                Integer.parseInt(xKey),
                                Integer.parseInt(yKey),
                                Integer.parseInt(zKey)
                            );
                            if (location.getBlock().getType() == Material.BLAST_FURNACE) {
                                getOrCreateMenu(location);
                                spawnBatteryHologram(location);
                                updateBatteryHologram(location);
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
    }

    private void spawnBatteryHologram(Location location) {
        removeBatteryHologram(location);
        Location displayLocation = location.clone().add(0.5, 1.02, 0.5);
        ItemDisplay display = location.getWorld().spawn(displayLocation, ItemDisplay.class, entity -> {
            entity.setItemStack(batteryService.createBatteryDisplay(false));
            entity.setRotation(0.0f, 0.0f);
            entity.setPersistent(false);
            entity.setTransformation(new Transformation(
                new Vector3f(0.0f, 0.0f, 0.0f),
                entity.getTransformation().getLeftRotation(),
                new Vector3f(0.25f, 0.25f, 0.25f),
                entity.getTransformation().getRightRotation()
            ));
        });
        holograms.put(location, display.getUniqueId());
    }

    private void updateBatteryHologram(Location location) {
        UUID uuid = holograms.get(location);
        if (uuid == null || location.getWorld() == null) {
            return;
        }

        Entity entity = location.getWorld().getEntity(uuid);
        if (!(entity instanceof ItemDisplay display)) {
            return;
        }

        Inventory menu = getOrCreateMenu(location);
        boolean glowing = batteryService.isBattery(normalizeBattery(menu.getItem(BATTERY_SLOT)));
        display.setItemStack(batteryService.createBatteryDisplay(glowing));
    }

    private void removeBatteryHologram(Location location) {
        UUID uuid = holograms.remove(location);
        if (uuid == null || location.getWorld() == null) {
            return;
        }

        Entity entity = location.getWorld().getEntity(uuid);
        if (entity != null) {
            entity.remove();
        }
    }

    private void setFlameState(Inventory menu, boolean active) {
        if (!active) {
            menu.setItem(FLAME_SLOT, idleIcon());
            return;
        }

        ChargerMenuHolder holder = (ChargerMenuHolder) menu.getHolder();
        Location location = holder.location();
        menu.setItem(
            FLAME_SLOT,
            flameIcon(
                remainingBurnTicks.getOrDefault(location, 0),
                Math.max(1, totalBurnTicks.getOrDefault(location, 1))
            )
        );
    }

    private void saveCharger(Location location) {
        Inventory menu = chargerInventories.get(location);
        if (menu == null) {
            return;
        }

        String path = path(location);
        storage.set(path + ".battery", normalizeBattery(menu.getItem(BATTERY_SLOT)));
        storage.set(path + ".fuel", normalizeFuel(menu.getItem(FUEL_SLOT)));
        storage.set(path + ".efficiency", normalizeEfficiency(menu.getItem(EFFICIENCY_SLOT)));
        storage.set(path + ".burn_ticks", remainingBurnTicks.getOrDefault(location, 0));
        storage.set(path + ".total_burn_ticks", totalBurnTicks.getOrDefault(location, 0));
        storage.set(path + ".buffer_charge", bufferCharge.getOrDefault(location, 0));
        saveStorage();
    }

    private void deleteCharger(Location location) {
        storage.set(path(location), null);
        saveStorage();
    }

    private String path(Location location) {
        return location.getWorld().getName() + "." + location.getBlockX() + "." + location.getBlockY() + "." + location.getBlockZ();
    }

    private void saveStorage() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            storage.save(storageFile);
        } catch (IOException ignored) {
        }
    }

    private String progressBar(int remainingTicks, int totalTicks) {
        int bars = 10;
        int filled = Math.max(0, Math.min(bars, (int) Math.round((remainingTicks / (double) Math.max(1, totalTicks)) * bars)));
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            builder.append(i < filled ? '|' : '.');
        }
        return builder.toString();
    }
}
