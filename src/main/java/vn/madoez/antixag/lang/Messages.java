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
package vn.madoez.antixag.lang;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads and serves the localized messages of the plugin.
 *
 * <p>The messages live in {@code messages/<language>.yml} inside the plugin
 * jar. When the file of the configured language does not exist, the English
 * file is used as a fallback so that the plugin never ends up without
 * messages.</p>
 */
public final class Messages {

    /** Prefix that is prepended to every message. */
    private static final String PREFIX = "&8[&cAntiXag&8] &r";

    private final Plugin plugin;
    private final Map<String, String> messages = new HashMap<>();
    private String language = "en";

    public Messages(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Reloads the messages of the given language.
     *
     * @param language the language code, for example {@code en} or {@code vi}
     */
    public void load(String language) {
        this.language = language == null ? "en" : language.toLowerCase(Locale.ROOT);
        messages.clear();

        FileConfiguration configuration = loadFile("messages/" + this.language + ".yml");
        if (configuration == null) {
            configuration = loadFile("messages/en.yml");
        }
        if (configuration == null) {
            plugin.getLogger().warning("No language file could be loaded, falling back to built-in defaults.");
            installDefaults();
            return;
        }

        for (String key : configuration.getKeys(true)) {
            if (configuration.isString(key)) {
                messages.put(key, configuration.getString(key));
            }
        }
    }

    private FileConfiguration loadFile(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (file.exists()) {
            try {
                return YamlConfiguration.loadConfiguration(file);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Failed to read " + path + ": " + exception.getMessage());
            }
        }

        try (InputStream stream = plugin.getResource(path)) {
            if (stream == null) {
                return null;
            }
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return configuration;
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException
                | RuntimeException exception) {
            plugin.getLogger().warning("Failed to read " + path + ": " + exception.getMessage());
            return null;
        }
    }

    private void installDefaults() {
        messages.put("prefix", "&8[&cAntiXag&8] &r");
        messages.put("no-permission", "&cYou do not have permission to use this command.");
        messages.put("reloaded", "&aConfiguration reloaded in &f{0}ms&a.");
        messages.put("unknown-command", "&cUnknown sub command. Use &f/antixag help&c.");
        messages.put("status-header", "&8&m-----------------&r &cAntiXag &8&m-----------------");
        messages.put("status-version", "&fVersion: &a{0}");
        messages.put("status-server", "&fServer: &a{0}");
        messages.put("status-protocol", "&fProtocolLib: &a{0}");
        messages.put("status-language", "&fLanguage: &a{0}");
        messages.put("status-worlds", "&fProtected worlds: &a{0}");
        messages.put("status-hidden", "&fHidden blocks: &a{0}");
        messages.put("status-chunks", "&fChunks rewritten: &a{0}");
        messages.put("status-ores", "&fOres hidden: &a{0}");
        messages.put("status-enabled", "&fProtection: &a{0}");
        messages.put("debug-enabled", "&aDebug mode has been enabled.");
        messages.put("debug-disabled", "&eDebug mode has been disabled.");
        messages.put("player-only", "&cThis command can only be used by a player.");
        messages.put("world-list-header", "&8&m-----------------&r &cAntiXag worlds &8&m-----------------");
        messages.put("world-list-entry", "&f{0} &7- &aenabled &7({1})");
        messages.put("world-list-entry-disabled", "&f{0} &7- &cdisabled &7({1})");
        messages.put("help-header", "&8&m-----------------&r &cAntiXag help &8&m-----------------");
        messages.put("help-reload", "&f/antixag reload &7- reloads the configuration");
        messages.put("help-status", "&f/antixag status &7- shows the plugin status");
        messages.put("help-debug", "&f/antixag debug &7- toggles the debug mode");
        messages.put("help-worlds", "&f/antixag worlds &7- lists the protected worlds");
        messages.put("help-help", "&f/antixag help &7- shows this help");
    }

    /**
     * Returns the raw message of the given key.
     *
     * @param key the message key
     * @return the raw message or {@code null} when the key is unknown
     */
    public String raw(String key) {
        return messages.get(key);
    }

    /**
     * Returns a formatted and colored message.
     *
     * @param key    the message key
     * @param values the placeholders that should be replaced
     * @return the formatted message
     */
    public String get(String key, Object... values) {
        String message = messages.get(key);
        if (message == null) {
            return ChatColor.RED + "Missing message: " + key;
        }
        for (int i = 0; i < values.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(values[i]));
        }
        return colorize(message);
    }

    /**
     * Sends a message to the given target.
     *
     * @param sender the receiver of the message
     * @param key    the message key
     * @param values the placeholders that should be replaced
     */
    public void send(CommandSender sender, String key, Object... values) {
        sender.sendMessage(get(key, values));
    }

    /**
     * Translates the color codes of a message.
     *
     * @param message the raw message
     * @return the colored message
     */
    public static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Returns the prefix of the plugin.
     *
     * @return the colored prefix
     */
    public String prefix() {
        return colorize(PREFIX);
    }

    /**
     * Returns the language that is currently loaded.
     *
     * @return the language code
     */
    public String language() {
        return language;
    }

    /**
     * Returns the known message keys.
     *
     * @return an immutable view of the message keys
     */
    public List<String> keys() {
        return List.copyOf(messages.keySet());
    }
}
