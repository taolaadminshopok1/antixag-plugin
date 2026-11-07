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
package vn.madoez.antixag;

import vn.madoez.antixag.command.AntiXagCommand;
import vn.madoez.antixag.config.AntiXagConfig;
import vn.madoez.antixag.config.WorldSettings;
import vn.madoez.antixag.lang.Messages;
import vn.madoez.antixag.listener.BlockChangeAdapter;
import vn.madoez.antixag.listener.BlockListener;
import vn.madoez.antixag.listener.ChunkPacketListener;
import vn.madoez.antixag.protocol.BlockStateRegistry;
import vn.madoez.antixag.protocol.ChunkObfuscator;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The entry point of the AntiXag plugin.
 *
 * <p>AntiXag hides ores from x-ray clients by rewriting the chunk data that
 * is sent to the player. The world itself is never modified, which means that
 * the server logic, other plugins and the players with a legitimate client
 * always see the real blocks.</p>
 *
 * @author Madoez Vn, BakiCode, Alexander Vn
 */
public final class AntiXagPlugin extends JavaPlugin {

    private final AntiXagConfig config = new AntiXagConfig();
    private final AtomicLong chunksProcessed = new AtomicLong();
    private final AtomicLong oresHidden = new AtomicLong();

    private Messages messages;
    private BlockStateRegistry registry;
    private ChunkObfuscator obfuscator;
    private ProtocolManager protocolManager;
    private ChunkPacketListener chunkListener;
    private BlockChangeAdapter blockChangeAdapter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages/en.yml");
        saveResourceIfMissing("messages/vi.yml");

        loadConfiguration();

        messages = new Messages(this);
        messages.load(config.language());

        AntiXagCommand.registerPermissions(this);

        if (!setupProtocolLib()) {
            getLogger().severe("ProtocolLib was not found. AntiXag cannot run without it.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            registry = BlockStateRegistry.create();
        } catch (RuntimeException exception) {
            getLogger().severe("Unable to resolve the block state registry: " + exception.getMessage());
            getLogger().severe("AntiXag requires a modern Paper or Spigot server (1.20.2+).");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        obfuscator = new ChunkObfuscator(registry, new Random());

        chunkListener = new ChunkPacketListener(this, obfuscator);
        blockChangeAdapter = new BlockChangeAdapter(this, registry);
        protocolManager.addPacketListener(chunkListener);
        protocolManager.addPacketListener(blockChangeAdapter);

        getServer().getPluginManager().registerEvents(new BlockListener(this), this);

        registerCommand();

        getLogger().info("AntiXag " + getDescription().getVersion() + " has been enabled.");
        getLogger().info("Block state registry contains " + registry.registrySize() + " states.");
    }

    @Override
    public void onDisable() {
        if (protocolManager != null) {
            protocolManager.removePacketListeners(this);
        }
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("AntiXag has been disabled.");
    }

    private boolean setupProtocolLib() {
        try {
            protocolManager = ProtocolLibrary.getProtocolManager();
            return true;
        } catch (Throwable throwable) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("ProtocolLib");
            if (plugin == null || !plugin.isEnabled()) {
                return false;
            }
            try {
                protocolManager = ProtocolLibrary.getProtocolManager();
                return true;
            } catch (Throwable nested) {
                protocolManager = null;
                return false;
            }
        }
    }

    private void registerCommand() {
        PluginCommand command = getCommand("antixag");
        if (command == null) {
            getLogger().warning("The command antixag is missing from plugin.yml.");
            return;
        }
        AntiXagCommand executor = new AntiXagCommand(this, messages);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void loadConfiguration() {
        reloadConfig();
        config.load(getConfig());
    }

    /**
     * Reloads the configuration, the language files and rebuilds the engine.
     */
    public void reloadEverything() {
        loadConfiguration();
        if (messages != null) {
            messages.load(config.language());
        }
        if (registry != null) {
            obfuscator = new ChunkObfuscator(registry, new Random());
            if (protocolManager != null) {
                protocolManager.removePacketListeners(this);
                chunkListener = new ChunkPacketListener(this, obfuscator);
                blockChangeAdapter = new BlockChangeAdapter(this, registry);
                protocolManager.addPacketListener(chunkListener);
                protocolManager.addPacketListener(blockChangeAdapter);
            }
            getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        }
    }

    private void saveResourceIfMissing(String path) {
        if (new java.io.File(getDataFolder(), path).exists()) {
            return;
        }
        try {
            saveResource(path, false);
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Unable to save " + path + ": " + exception.getMessage());
        }
    }

    /**
     * Returns the current configuration.
     *
     * @return the configuration
     */
    public AntiXagConfig config() {
        return config;
    }

    /**
     * Returns the message manager.
     *
     * @return the messages
     */
    public Messages messages() {
        return messages;
    }

    /**
     * Returns the obfuscator that rewrites the chunk data.
     *
     * @return the obfuscator
     */
    public ChunkObfuscator obfuscator() {
        return obfuscator;
    }

    /**
     * Returns the block state registry.
     *
     * @return the registry
     */
    public BlockStateRegistry registry() {
        return registry;
    }

    /**
     * Returns the adapter that rewrites the single block change packets.
     *
     * @return the adapter
     */
    public BlockChangeAdapter revealer() {
        return blockChangeAdapter;
    }

    /**
     * Enables or disables the debug mode.
     *
     * @param debug the new state
     */
    public void setDebug(boolean debug) {
        getConfig().set("settings.debug", debug);
        saveConfig();
        reloadConfig();
        config.load(getConfig());
    }

    /**
     * Writes a message to the console when the debug mode is enabled.
     *
     * @param message the message
     */
    public void debug(String message) {
        if (config.debug()) {
            getLogger().info("[debug] " + message);
        }
    }

    /**
     * Counts a rewritten chunk.
     */
    public void countChunk() {
        chunksProcessed.incrementAndGet();
    }

    /**
     * Counts the hidden ores.
     *
     * @param count the amount of ores that were hidden
     */
    public void countOres(int count) {
        oresHidden.addAndGet(count);
    }

    /**
     * Returns the amount of chunks that were rewritten.
     *
     * @return the chunk counter
     */
    public long chunksProcessed() {
        return chunksProcessed.get();
    }

    /**
     * Returns the amount of ores that were hidden.
     *
     * @return the ore counter
     */
    public long oresHidden() {
        return oresHidden.get();
    }

    /**
     * Returns the names of the worlds that are protected by the plugin.
     *
     * @return the world names
     */
    public List<String> protectedWorlds() {
        List<String> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            if (config.settingsFor(world) != null) {
                worlds.add(world.getName());
            }
        }
        return worlds;
    }

    /**
     * Resolves the settings of a world by its name.
     *
     * @param name the name of the world
     * @return the settings or {@code null} when the world is unknown
     */
    public WorldSettings settingsForWorld(String name) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().equalsIgnoreCase(name)) {
                return config.settingsFor(world);
            }
        }
        return null;
    }

    /**
     * Returns the version of the installed ProtocolLib.
     *
     * @return the version string
     */
    public String protocolLibVersion() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ProtocolLib");
        if (plugin == null) {
            return "not installed";
        }
        return plugin.getDescription().getVersion();
    }
}
