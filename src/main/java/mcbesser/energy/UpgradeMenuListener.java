package mcbesser.energy;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

public final class UpgradeMenuListener implements Listener {

    private static final int BOOK_SLOT = 13;

    private final BatteryService batteryService;

    public UpgradeMenuListener(EnergyPlugin plugin, BatteryService batteryService) {
        this.batteryService = batteryService;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBatteryUse(PlayerInteractEvent event) {
        if (event.getHand() == null || event.getItem() == null || !batteryService.isBattery(event.getItem())) {
            return;
        }
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
                event.setCancelled(true);
                openMenu(event.getPlayer(), event.getItem());
            }
            default -> {
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BatteryUpgradeHolder)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        if (Objects.equals(event.getClickedInventory(), top)) {
            int slot = event.getRawSlot();
            if (slot != BOOK_SLOT) {
                event.setCancelled(true);
                return;
            }

            ItemStack cursor = event.getCursor();
            ItemStack current = normalize(top.getItem(BOOK_SLOT));
            event.setCancelled(true);

            if ((cursor == null || cursor.getType() == Material.AIR) && current != null) {
                event.getWhoClicked().setItemOnCursor(current.clone());
                top.setItem(BOOK_SLOT, placeholder());
                return;
            }

            if (cursor == null || cursor.getType() == Material.AIR) {
                return;
            }
            if (!isValidUpgradeBook(cursor)) {
                return;
            }
            if (current != null && current.getType() != Material.AIR) {
                event.getWhoClicked().getInventory().addItem(current);
            }
            top.setItem(BOOK_SLOT, singleItem(cursor));
            reduceStack(cursor);
            event.getWhoClicked().setItemOnCursor(cursor.getAmount() <= 0 ? null : cursor);
            return;
        }

        if (event.isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            if (current != null && isValidUpgradeBook(current) && normalize(top.getItem(BOOK_SLOT)) == null) {
                top.setItem(BOOK_SLOT, singleItem(current));
                reduceStack(current);
            }
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BatteryUpgradeHolder holder)) {
            return;
        }

        Player player = Bukkit.getPlayer(holder.playerId());
        if (player == null) {
            return;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (!batteryService.isBattery(handItem)) {
            return;
        }

        ItemStack book = normalize(event.getInventory().getItem(BOOK_SLOT));
        int level = book == null ? 0 : getUnbreakingLevel(book);
        player.getInventory().setItemInMainHand(batteryService.applyBatteryUpgrade(handItem, level));
        player.sendMessage(Component.text(
            "Batterie-Kapazitaet: " + batteryService.getMaxCharge(player.getInventory().getItemInMainHand()),
            NamedTextColor.LIGHT_PURPLE
        ));
    }

    private void openMenu(Player player, ItemStack battery) {
        Inventory inventory = Bukkit.createInventory(
            new BatteryUpgradeHolder(player.getUniqueId()),
            27,
            Component.text("Batterie", NamedTextColor.DARK_PURPLE)
        );

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.text(" ")));

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        int level = batteryService.getBatteryUpgradeLevel(battery);
        inventory.setItem(BOOK_SLOT, level > 0 ? batteryService.createBatteryUpgradeBook(level) : placeholder());
        player.openInventory(inventory);
    }

    private boolean isValidUpgradeBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !(item.getItemMeta() instanceof EnchantmentStorageMeta meta)) {
            return false;
        }
        return meta.hasStoredEnchant(Enchantment.UNBREAKING);
    }

    private int getUnbreakingLevel(ItemStack item) {
        if (!(item.getItemMeta() instanceof EnchantmentStorageMeta meta)) {
            return 0;
        }
        return Math.max(0, Math.min(5, meta.getStoredEnchantLevel(Enchantment.UNBREAKING)));
    }

    private ItemStack singleItem(ItemStack item) {
        ItemStack copy = item.clone();
        copy.setAmount(1);
        return copy;
    }

    private void reduceStack(ItemStack item) {
        item.setAmount(item.getAmount() - 1);
    }

    private ItemStack normalize(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getType() == Material.PURPLE_STAINED_GLASS_PANE) {
            return null;
        }
        return item;
    }

    private ItemStack placeholder() {
        ItemStack item = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.text("Haltbarkeit-Buch", NamedTextColor.LIGHT_PURPLE)));
        return item;
    }
}
