package com.r7flex.donutworth;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.event.inventory.InventoryType;

public class PacketHandler implements Listener {
    private final DonutWorth plugin;
    private FileConfiguration config;
    private static final Set<java.util.UUID> playersWithItemsInCursor = new HashSet<>();

    public PacketHandler(DonutWorth plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        register();
    }

    private void register() {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.SET_SLOT, PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                Player player = event.getPlayer();

                if (playersWithItemsInCursor.contains(player.getUniqueId()) || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                    return;
                }

                try {
                    InventoryView view = player.getOpenInventory();
                    Inventory topInventory = view.getTopInventory();
                    
                    if (topInventory.getType() == InventoryType.CHEST || 
                        topInventory.getType() == InventoryType.ENDER_CHEST || 
                        topInventory.getType() == InventoryType.BARREL) {
                        
                        //plugin.getLogger().info("Inventory Type: " + topInventory.getType());
                        
                        if (topInventory.getLocation() == null) {
                            //plugin.getLogger().info("Inventory atlandı - Location: null");
                            return;
                        }
                    }
                } catch (Exception ignored) {}

                if (packet.getType() == PacketType.Play.Server.SET_SLOT) {
                    ItemStack item = packet.getItemModifier().read(0);
                    if (item != null && item.getType() != Material.AIR) {
                        ItemStack modifiedItem = injectPriceLore(item.clone());
                        packet.getItemModifier().write(0, modifiedItem);
                    }
                } else if (packet.getType() == PacketType.Play.Server.WINDOW_ITEMS) {
                    List<ItemStack> items = packet.getItemListModifier().read(0);
                    List<ItemStack> newItems = new ArrayList<>();
                    
                    for (ItemStack item : items) {
                        if (item != null && item.getType() != Material.AIR) {
                            ItemStack modifiedItem = injectPriceLore(item.clone());
                            newItems.add(modifiedItem);
                        } else {
                            newItems.add(item);
                        }
                    }
                    packet.getItemListModifier().write(0, newItems);
                }
            }
        });
    }

    private double getTotalItemValue(ItemStack item) {
        String type = item.getType().name();
        double base = config.getDouble("items." + type, 0.0) * item.getAmount();
        // Enchantments
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : item.getItemMeta().getEnchants().entrySet()) {
                String enchName = entry.getKey().getKey().getKey().toUpperCase(Locale.ENGLISH);
                int level = entry.getValue();
                //System.out.println("ENCHANT DEBUG: " + enchName + " (level: " + level + ")");
                double enchValue = config.getDouble("enchantments." + enchName, 0.0) * level;
                base += enchValue;
            }
        }
        if (item.getType().name().endsWith("SHULKER_BOX") && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof org.bukkit.inventory.meta.BlockStateMeta) {
                org.bukkit.inventory.meta.BlockStateMeta bsm = (org.bukkit.inventory.meta.BlockStateMeta) meta;
                if (bsm.getBlockState() instanceof org.bukkit.block.ShulkerBox) {
                    org.bukkit.block.ShulkerBox shulker = (org.bukkit.block.ShulkerBox) bsm.getBlockState();
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

    private ItemStack injectPriceLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String itemType = item.getType().name();
        if (!config.contains("items." + itemType)) {
            return item;
        }
        double value = getTotalItemValue(item) * config.getDouble("multiplier", 1.0);
        if (value <= 0.0) {
            return item;
        }
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        String worthMessage = config.getString("messages.worth-format", "&7Worth: &a$%value%")
                .replace("%value%", formatBalance(value)).replace('&', '§');
        lore.add(0, worthMessage);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void reloadConfig() {
        this.config = plugin.getConfig();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        
        switch (event.getAction()) {
            case PICKUP_ALL:
            case PICKUP_HALF:
            case PICKUP_SOME:
            case PICKUP_ONE:
            case PLACE_ALL:
            case PLACE_SOME:
            case PLACE_ONE:
            case SWAP_WITH_CURSOR:
            case MOVE_TO_OTHER_INVENTORY:
            case HOTBAR_SWAP:
                playersWithItemsInCursor.add(player.getUniqueId());
                break;
            case DROP_ALL_CURSOR:
            case DROP_ONE_CURSOR:
                if (player.getItemOnCursor().getType() == Material.AIR) {
                    playersWithItemsInCursor.remove(player.getUniqueId());
                }
                break;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack newCursor = player.getItemOnCursor();
            if (newCursor.getType() == Material.AIR) {
                playersWithItemsInCursor.remove(player.getUniqueId());
            } else {
                playersWithItemsInCursor.add(player.getUniqueId());
            }
            player.updateInventory();
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        
        playersWithItemsInCursor.add(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack newCursor = player.getItemOnCursor();
            if (newCursor.getType() == Material.AIR) {
                playersWithItemsInCursor.remove(player.getUniqueId());
            }
            player.updateInventory();
        });
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        playersWithItemsInCursor.remove(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, player::updateInventory, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playersWithItemsInCursor.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> event.getPlayer().updateInventory(), 10L);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            ((Player) event.getPlayer()).updateInventory();
        }
    }
} 