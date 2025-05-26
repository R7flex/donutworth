package com.r7flex.donutworth;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminGUI implements Listener {
    private final DonutWorth plugin;
    private final Player player;
    private Inventory gui;
    private int currentPage = 0;
    private final int itemsPerPage;
    private List<Map.Entry<String, Double>> items;

    public AdminGUI(DonutWorth plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.itemsPerPage = plugin.getConfig().getInt("admin-gui.items-per-page", 45);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadItems();
    }

    private void loadItems() {
        items = new ArrayList<>();
        for (String key : plugin.getConfig().getConfigurationSection("items").getKeys(false)) {
            items.add(Map.entry(key, plugin.getConfig().getDouble("items." + key)));
        }
    }

    public void open() {
        openPage(0);
    }

    private void openPage(int page) {
        currentPage = page;
        int totalPages = (int) Math.ceil((double) items.size() / itemsPerPage);
        gui = Bukkit.createInventory(player, 54, plugin.getConfig().getString("admin-gui.title", "&bDonutWorth Admin").replace('&', '§'));

        int startIndex = page * itemsPerPage;
        for (int i = 0; i < itemsPerPage && startIndex + i < items.size(); i++) {
            Map.Entry<String, Double> entry = items.get(startIndex + i);
            ItemStack item;
            try {
                item = new ItemStack(Material.valueOf(entry.getKey()));
            } catch (IllegalArgumentException e) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§e" + entry.getKey());
                List<String> lore = new ArrayList<>();
                for (String l : plugin.getConfig().getStringList("admin-gui.item-lore")) {
                    lore.add(l.replace("%price%", String.format("%.2f", entry.getValue()))
                            .replace("%blacklisted%", isBlacklisted(entry.getKey()) ? "§cYes" : "§aNo")
                            .replace('&', '§'));
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            gui.setItem(i, item);
        }

        // Navigation buttons
        if (page > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta meta = prevButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(plugin.getConfig().getString("admin-gui.previous-page-name", "&cPrevious Page").replace('&', '§'));
                meta.setLore(plugin.getConfig().getStringList("admin-gui.previous-page-lore").stream()
                        .map(s -> s.replace('&', '§')).toList());
                prevButton.setItemMeta(meta);
            }
            gui.setItem(45, prevButton);
        } else {
            gui.setItem(45, null);
        }

        if (page < totalPages - 1) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta meta = nextButton.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(plugin.getConfig().getString("admin-gui.next-page-name", "&aNext Page").replace('&', '§'));
                meta.setLore(plugin.getConfig().getStringList("admin-gui.next-page-lore").stream()
                        .map(s -> s.replace('&', '§')).toList());
                nextButton.setItemMeta(meta);
            }
            gui.setItem(53, nextButton);
        } else {
            gui.setItem(53, null);
        }

        player.openInventory(gui);
    }

    private boolean isBlacklisted(String item) {
        return plugin.getConfig().getStringList("sell-gui.sell-item-blacklist").contains(item);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(player)) return;
        if (!event.getInventory().equals(gui)) return;
        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;

        if (event.getSlot() == 45 && currentPage > 0) {
            openPage(currentPage - 1);
            return;
        }

        if (event.getSlot() == 53 && currentPage < (int) Math.ceil((double) items.size() / itemsPerPage) - 1) {
            openPage(currentPage + 1);
            return;
        }

        if (event.getSlot() < itemsPerPage) {
            int index = currentPage * itemsPerPage + event.getSlot();
            if (index < items.size()) {
                openEditGUI(items.get(index));
            }
        }
    }

    private void openEditGUI(Map.Entry<String, Double> item) {
        Inventory editGui = Bukkit.createInventory(player, 27, plugin.getConfig().getString("admin-gui.item-edit-title", "&bEdit Item: %item%")
                .replace("%item%", item.getKey())
                .replace('&', '§'));

        // Center item
        ItemStack displayItem;
        try {
            displayItem = new ItemStack(Material.valueOf(item.getKey()));
        } catch (IllegalArgumentException e) {
            displayItem = new ItemStack(Material.BARRIER);
        }
        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + item.getKey());
            List<String> lore = new ArrayList<>();
            for (String l : plugin.getConfig().getStringList("admin-gui.item-edit-lore")) {
                lore.add(l.replace("%price%", String.format("%.2f", item.getValue()))
                        .replace('&', '§'));
            }
            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }
        editGui.setItem(13, displayItem);

        // Blacklist buttons
        ItemStack blacklistButton;
        if (isBlacklisted(item.getKey())) {
            blacklistButton = new ItemStack(Material.LIME_WOOL);
            ItemMeta blacklistMeta = blacklistButton.getItemMeta();
            if (blacklistMeta != null) {
                blacklistMeta.setDisplayName(plugin.getConfig().getString("admin-gui.blacklist-remove-name", "&aRemove from Blacklist").replace('&', '§'));
                blacklistMeta.setLore(plugin.getConfig().getStringList("admin-gui.blacklist-remove-lore").stream()
                        .map(s -> s.replace('&', '§')).toList());
                blacklistButton.setItemMeta(blacklistMeta);
            }
        } else {
            blacklistButton = new ItemStack(Material.RED_WOOL);
            ItemMeta blacklistMeta = blacklistButton.getItemMeta();
            if (blacklistMeta != null) {
                blacklistMeta.setDisplayName(plugin.getConfig().getString("admin-gui.blacklist-add-name", "&cAdd to Blacklist").replace('&', '§'));
                blacklistMeta.setLore(plugin.getConfig().getStringList("admin-gui.blacklist-add-lore").stream()
                        .map(s -> s.replace('&', '§')).toList());
                blacklistButton.setItemMeta(blacklistMeta);
            }
        }
        editGui.setItem(11, blacklistButton);

        // Back button
        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName("§cBack");
            backButton.setItemMeta(backMeta);
        }
        editGui.setItem(15, backButton);

        player.openInventory(editGui);
    }

    @EventHandler
    public void onEditInventoryClick(InventoryClickEvent event) {
        if (!event.getWhoClicked().equals(player)) return;
        if (event.getCurrentItem() == null) return;
        if (!event.getView().getTitle().contains("Edit Item:")) return;
        event.setCancelled(true);

        String itemName = event.getView().getTitle().split(":")[1].trim();
        if (event.getSlot() == 11 || event.getRawSlot() == 11) {
            List<String> blacklist = plugin.getConfig().getStringList("sell-gui.sell-item-blacklist");
            boolean blacklisted = blacklist.contains(itemName);
            if (blacklisted) {
                blacklist.remove(itemName);
            } else {
                blacklist.add(itemName);
            }
            plugin.getConfig().set("sell-gui.sell-item-blacklist", blacklist);
            plugin.saveConfig();
            Inventory editGui = event.getInventory();
            editGui.setItem(11, null);
            player.updateInventory();
            Bukkit.getScheduler().runTask(plugin, () -> {
                ItemStack blacklistButton;
                if (!blacklisted) {
                    blacklistButton = new ItemStack(Material.LIME_WOOL);
                    ItemMeta blacklistMeta = blacklistButton.getItemMeta();
                    if (blacklistMeta != null) {
                        blacklistMeta.setDisplayName(plugin.getConfig().getString("admin-gui.blacklist-remove-name", "&aRemove from Blacklist").replace('&', '§'));
                        blacklistMeta.setLore(plugin.getConfig().getStringList("admin-gui.blacklist-remove-lore").stream()
                                .map(s -> s.replace('&', '§')).toList());
                        blacklistButton.setItemMeta(blacklistMeta);
                    }
                } else {
                    blacklistButton = new ItemStack(Material.RED_WOOL);
                    ItemMeta blacklistMeta = blacklistButton.getItemMeta();
                    if (blacklistMeta != null) {
                        blacklistMeta.setDisplayName(plugin.getConfig().getString("admin-gui.blacklist-add-name", "&cAdd to Blacklist").replace('&', '§'));
                        blacklistMeta.setLore(plugin.getConfig().getStringList("admin-gui.blacklist-add-lore").stream()
                                .map(s -> s.replace('&', '§')).toList());
                        blacklistButton.setItemMeta(blacklistMeta);
                    }
                }
                editGui.setItem(11, blacklistButton);
                player.updateInventory();
            });
            return;
        }
        if (event.getSlot() == 15) {
            // Back butonu
            openPage(currentPage);
            return;
        }
        if (event.getSlot() == 13) {
            double currentPrice = plugin.getConfig().getDouble("items." + itemName);
            double change = 0;
            if (event.isLeftClick() && !event.isShiftClick()) change = 1.0;
            if (event.isRightClick() && !event.isShiftClick()) change = -1.0;
            if (event.isLeftClick() && event.isShiftClick()) change = 10.0;
            if (event.isRightClick() && event.isShiftClick()) change = -10.0;
            double newPrice = Math.max(0, currentPrice + change);
            plugin.getConfig().set("items." + itemName, newPrice);
            plugin.saveConfig();
            plugin.reloadConfig();
            openEditGUI(Map.entry(itemName, newPrice));
        }
    }
} 