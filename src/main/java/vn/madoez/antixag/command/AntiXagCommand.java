/*
 * AntiXag - Anti X-Ray protection for Paper/Spigot servers.
 * Copyright (C) 2026 Madoez Vn, BakiCode, Alexander Vn
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package vn.madoez.antixag.command;

import vn.madoez.antixag.AntiXagPlugin;
import vn.madoez.antixag.config.WorldSettings;
import vn.madoez.antixag.lang.Messages;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles the {@code /antixag} command.
 */
public final class AntiXagCommand implements CommandExecutor, TabCompleter {

    private final AntiXagPlugin plugin;
    private final Messages messages;

    public AntiXagCommand(AntiXagPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("antixag.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }

        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload":
                reload(sender);
                return true;
            case "status":
                status(sender);
                return true;
            case "debug":
                debug(sender);
                return true;
            case "worlds":
                worlds(sender);
                return true;
            case "help":
            default:
                help(sender);
                return true;
        }
    }

    private void reload(CommandSender sender) {
        long start = System.currentTimeMillis();
        plugin.reloadEverything();
        long elapsed = System.currentTimeMillis() - start;
        messages.send(sender, "reloaded", elapsed);
    }

    private void status(CommandSender sender) {
        messages.send(sender, "status-header");
        messages.send(sender, "status-version", plugin.getDescription().getVersion());
        messages.send(sender, "status-server", Bukkit.getVersion());
        messages.send(sender, "status-protocol", plugin.protocolLibVersion());
        messages.send(sender, "status-language", messages.language());
        messages.send(sender, "status-enabled", plugin.config().debug() ? "debug" : "normal");
        messages.send(sender, "status-mode", plugin.config().defaults().hideMode().name());
        messages.send(sender, "status-reveal", plugin.config().defaults().revealMode().name());
        messages.send(sender, "status-chunks", plugin.chunksProcessed());
        messages.send(sender, "status-ores", plugin.oresHidden());
        messages.send(sender, "status-worlds", plugin.protectedWorlds().size());
        messages.send(sender, "status-hidden", plugin.config().defaults().hiddenBlocks().size());
    }

    private void debug(CommandSender sender) {
        boolean enabled = !plugin.config().debug();
        plugin.setDebug(enabled);
        messages.send(sender, enabled ? "debug-enabled" : "debug-disabled");
    }

    private void worlds(CommandSender sender) {
        messages.send(sender, "world-list-header");
        for (String world : plugin.protectedWorlds()) {
            WorldSettings settings = plugin.settingsForWorld(world);
            String mode = settings == null ? "unknown" : settings.hideMode().name();
            if (settings != null && settings.enabled()) {
                messages.send(sender, "world-list-entry", world, mode);
            } else {
                messages.send(sender, "world-list-entry-disabled", world, mode);
            }
        }
    }

    private void help(CommandSender sender) {
        messages.send(sender, "help-header");
        messages.send(sender, "help-reload");
        messages.send(sender, "help-status");
        messages.send(sender, "help-debug");
        messages.send(sender, "help-worlds");
        messages.send(sender, "help-help");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("antixag.admin")) {
            return List.of();
        }
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        for (String option : List.of("reload", "status", "debug", "worlds", "help")) {
            if (option.startsWith(prefix)) {
                options.add(option);
            }
        }
        return options;
    }

    /**
     * Registers the permissions of the plugin.
     *
     * @param plugin the plugin instance
     */
    public static void registerPermissions(Plugin plugin) {
        register(plugin, "antixag.admin", "Allows managing the AntiXag plugin");
        register(plugin, "antixag.bypass", "Allows bypassing the AntiXag protection");
    }

    private static void register(Plugin plugin, String name, String description) {
        if (plugin.getServer().getPluginManager().getPermission(name) != null) {
            return;
        }
        plugin.getServer().getPluginManager().addPermission(new Permission(name, description,
                PermissionDefault.OP));
    }

    /**
     * Sends a colored message without using the message manager.
     *
     * @param player the receiver
     * @param text   the text
     */
    public static void sendRaw(Player player, String text) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
    }
}
