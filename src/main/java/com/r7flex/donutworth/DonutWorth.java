package com.r7flex.donutworth;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.plugin.Plugin;

public class DonutWorth extends JavaPlugin implements TabCompleter {
    private PacketHandler packetHandler;
    private static Economy econ = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault not found! Disabling DonutWorth.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("ProtocolLib not found! Disabling DonutWorth.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.packetHandler = new PacketHandler(this);
        getCommand("donutworth").setTabCompleter(this);
        getCommand("sell").setExecutor(this);
        setupEconomy();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault not found! /sell is disabled.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("Vault economy provider does not exist!");
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    @Override
    public void onDisable() {

    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        if (packetHandler != null) {
            packetHandler.reloadConfig();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sell")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(getConfig().getString("prefix", "&8[&bDonutWorth&8]").replace('&', '§') + " " + getConfig().getString("messages.only-player", "&cOnly players can use this command!").replace('&', '§'));
                return true;
            }
            Player player = (Player) sender;
            String perm = getConfig().getString("permissions.sell", "donutworth.sell");
            if (!player.hasPermission(perm)) {
                player.sendMessage(getConfig().getString("prefix", "&8[&bDonutWorth&8]").replace('&', '§') + " " + getConfig().getString("messages.sell-no-permission", "&cYou don't have permission to sell!").replace('&', '§'));
                return true;
            }
            Bukkit.getScheduler().runTask(this, () -> {
                new SellGui(this, player).open();
            });
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(getConfig().getString("prefix", "&8[&bDonutWorth&8]").replace('&', '§') + " " + getConfig().getString("messages.unknown-command", "&cUnknown command!").replace('&', '§'));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "gui":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(getConfig().getString("prefix", "&8[&bDonutWorth&8]").replace('&', '§') + " " + getConfig().getString("messages.only-player", "&cOnly players can use this command!").replace('&', '§'));
                    return true;
                }
                if (!sender.hasPermission(getConfig().getString("permissions.gui", "donutworth.gui"))) {
                    sender.sendMessage(getConfig().getString("prefix", "&8[&bDonutWorth&8]").replace('&', '§') + " " + getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command!").replace('&', '§'));
                    return true;
                }
                new AdminGUI(this, (Player) sender).open();
                break;
            case "reload":
                if (!sender.hasPermission(getConfig().getString("permissions.reload", "donutworth.reload"))) {
                    sender.sendMessage(getConfig().getString("prefix", "&8[&bDonutWorth&8]").replace('&', '§') + " " + getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command!").replace('&', '§'));
                    return true;
                }
                reloadConfig();
                packetHandler.reloadConfig();
                sender.sendMessage(getConfig().getString("prefix", "&8[&bDonutWorth&8]").replace('&', '§') + " " + getConfig().getString("messages.reload-success", "&aConfiguration reloaded successfully!").replace('&', '§'));
                break;
            case "version":
                if (!sender.hasPermission(getConfig().getString("permissions.version", "donutworth.version"))) {
                    sender.sendMessage(getConfig().getString("prefix", "&8[&bDonutWorth&8]").replace('&', '§') + " " + getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command!").replace('&', '§'));
                    return true;
                }
                sender.sendMessage(getConfig().getString("prefix", "&8[&bDonutWorth&8]").replace('&', '§') + " " + getConfig().getString("messages.version", "&aRunning version: &e%version%").replace('&', '§').replace("%version%", getDescription().getVersion()));
                break;
            default:
                sender.sendMessage(getConfig().getString("prefix", "&8[&bDonutWorth&8]").replace('&', '§') + " " + getConfig().getString("messages.unknown-command", "&cUnknown command!").replace('&', '§'));
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission(getConfig().getString("permissions.reload", "donutworth.reload"))) {
                completions.add("reload");
            }
            if (sender.hasPermission(getConfig().getString("permissions.version", "donutworth.version"))) {
                completions.add("version");
            }
            if (sender.hasPermission(getConfig().getString("permissions.gui", "donutworth.gui"))) {
                completions.add("gui");
            }
            return completions;
        }
        return new ArrayList<>();
    }
} 