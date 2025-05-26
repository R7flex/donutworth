package com.r7flex.donutworth;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.block.ShulkerBox;

public class SellGui implements Listener {
    private final DonutWorth plugin;
    private final Player player;
    private Inventory gui;
    private final int size;
    private final int paperSlot;

    public SellGui(DonutWorth plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.size = plugin.getConfig().getInt("sell-gui.sell-menu-size", 54);
        this.paperSlot = plugin.getConfig().getInt("sell-gui.sell-paper-slot", 53);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open() {
        double totalValue = calculateTotalValue();
        String baseTitle = plugin.getConfig().getString("sell-gui.sell-paper-name", "&eTotal Value").replace('&', '§');
        String titleWithValue = baseTitle + " §7($" + formatBalance(totalValue) + ")";
        gui = Bukkit.createInventory(player, size, plugin.getConfig().getString("sell-gui.title", "&bSell Items").replace('&', '§'));
        // Menüdeki slotları doldur
        for (int i = 0; i < size; i++) {
            if (i == paperSlot) continue;
            gui.setItem(i, null);
        }
        // Paper slotu
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.setDisplayName(titleWithValue);
        List<String> lore = new ArrayList<>();
        double value = getTotalItemValue(paper);
        for (String l : plugin.getConfig().getStringList("sell-gui.sell-paper-lore")) {
            lore.add(l.replace("%value%", formatBalance(value)).replace('&', '§'));
        }
        meta.setLore(lore);
        paper.setItemMeta(meta);
        gui.setItem(paperSlot, paper);
        player.openInventory(gui);
    }

    private double getTotalItemValue(ItemStack item) {
        String type = item.getType().name();
        double base = plugin.getConfig().getDouble("items." + type, 0.0) * item.getAmount();
        // Enchantments
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : item.getItemMeta().getEnchants().entrySet()) {
                String enchName = entry.getKey().getKey().getKey().toUpperCase(Locale.ENGLISH);
                int level = entry.getValue();
                double enchValue = plugin.getConfig().getDouble("enchantments." + enchName, 0.0) * level;
                base += enchValue;
            }
        }
        // Shulker kutusu ise içindekileri de ekle
        if (item.getType().name().endsWith("SHULKER_BOX") && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof BlockStateMeta) {
                BlockStateMeta bsm = (BlockStateMeta) meta;
                if (bsm.getBlockState() instanceof ShulkerBox) {
                    ShulkerBox shulker = (ShulkerBox) bsm.getBlockState();
                    double inside = 0;
                    for (ItemStack insideItem : shulker.getInventory().getContents()) {
                        if (insideItem != null && insideItem.getType() != Material.AIR) {
                            inside += getTotalItemValue(insideItem);
                        }
                    }
                    return base + inside;
                }
            }
        }
        return base;
    }

    private double calculateTotalValue() {
        double total = 0;
        double multiplier = plugin.getConfig().getDouble("multiplier", 1.0);
        for (int i = 0; i < size; i++) {
            if (i == paperSlot) continue;
            ItemStack item = gui != null ? gui.getItem(i) : null;
            if (item == null || item.getType() == Material.AIR) continue;
            String type = item.getType().name();
            if (!plugin.getConfig().contains("items." + type)) continue;
            if (plugin.getConfig().getStringList("sell-gui.sell-item-blacklist").contains(type)) continue;
            double value = getTotalItemValue(item) * multiplier;
            total += value;
        }
        return total;
    }

    private String formatBalance(double value) {
        if (value >= 1_000_000_000) {
            return String.format("%.1fb", value / 1_000_000_000);
        } else if (value >= 1_000_000) {
            return String.format("%.1fm", value / 1_000_000);
        } else if (value >= 1_000) {
            return String.format("%.1fk", value / 1_000);
        }
        return String.format("%.2f", value);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!event.getInventory().equals(gui)) return;
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onInventoryClose(InventoryCloseEvent e) {
                // unregister
                InventoryCloseEvent.getHandlerList().unregister(this);
            }
        }, plugin));
        double total = 0;
        double multiplier = plugin.getConfig().getDouble("multiplier", 1.0);
        Economy econ = DonutWorth.getEconomy();
        Map<Integer, ItemStack> toRemove = new HashMap<>();
        List<ItemStack> unsellable = new ArrayList<>();
        List<ItemStack> blacklisted = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (i == paperSlot) continue;
            ItemStack item = gui.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            String type = item.getType().name();
            if (!plugin.getConfig().contains("items." + type)) {
                unsellable.add(item);
                continue;
            }
            if (plugin.getConfig().getStringList("sell-gui.sell-item-blacklist").contains(type)) {
                blacklisted.add(item);
                continue;
            }
            double value = getTotalItemValue(item) * multiplier;
            total += value;
            toRemove.put(i, item);
        }
        if (total > 0) {
            econ.depositPlayer(player, total);
            player.sendMessage(plugin.getConfig().getString("prefix", "&8[&bDonutWorth&8]").replace('&', '§') + " " +
                plugin.getConfig().getString("messages.sell-success", "&aYou sold your items for &6$%value%!")
                    .replace("%value%", formatBalance(total)).replace('&', '§'));
            player.playSound(player.getLocation(), 
                org.bukkit.Sound.valueOf(plugin.getConfig().getString("sounds.sell-success", "ENTITY_EXPERIENCE_ORB_PICKUP")), 
                1.0f, 1.0f);
        }
        if (!unsellable.isEmpty()) {
            for (ItemStack item : unsellable) {
                player.getInventory().addItem(item);
            }
            player.sendMessage(plugin.getConfig().getString("prefix", "&8[&bDonutWorth&8]").replace('&', '§') + " " +
                plugin.getConfig().getString("messages.sell-unsellable-items", "&cSome items could not be sold and were returned to you.").replace('&', '§'));
            player.playSound(player.getLocation(), 
                org.bukkit.Sound.valueOf(plugin.getConfig().getString("sounds.sell-fail", "ENTITY_VILLAGER_NO")), 
                1.0f, 1.0f);
        }
        if (!blacklisted.isEmpty()) {
            for (ItemStack item : blacklisted) {
                player.getInventory().addItem(item);
            }
            player.sendMessage(plugin.getConfig().getString("prefix", "&8[&bDonutWorth&8]").replace('&', '§') + " " +
                plugin.getConfig().getString("messages.sell-blacklist-items", "&cSome items are in the blacklist and were returned to you.").replace('&', '§'));
            player.playSound(player.getLocation(), 
                org.bukkit.Sound.valueOf(plugin.getConfig().getString("sounds.sell-fail", "ENTITY_VILLAGER_NO")), 
                1.0f, 1.0f);
        }
        // Satılan itemleri sil
        for (int i : toRemove.keySet()) {
            gui.setItem(i, null);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(player)) return;
        if (!event.getInventory().equals(gui)) return;
        if (event.getSlot() == paperSlot) {
            event.setCancelled(true);
            return;
        }
        // Tıklama sonrası kağıdın başlığını güncelle
        Bukkit.getScheduler().runTaskLater(plugin, this::updatePaperTitle, 1L);
    }

    private void updatePaperTitle() {
        double totalValue = calculateTotalValue();
        String baseTitle = plugin.getConfig().getString("sell-gui.sell-paper-name", "&eTotal Value").replace('&', '§');
        String titleWithValue = baseTitle + " §7($" + formatBalance(totalValue) + ")";
        ItemStack paper = gui.getItem(paperSlot);
        if (paper != null && paper.getType() == Material.PAPER) {
            ItemMeta meta = paper.getItemMeta();
            meta.setDisplayName(titleWithValue);
            List<String> lore = new ArrayList<>();
            double value = getTotalItemValue(paper);
            for (String l : plugin.getConfig().getStringList("sell-gui.sell-paper-lore")) {
                lore.add(l.replace("%value%", formatBalance(value)).replace('&', '§'));
            }
            meta.setLore(lore);
            paper.setItemMeta(meta);
            gui.setItem(paperSlot, paper);
        }
    }
} 