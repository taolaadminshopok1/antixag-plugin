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
package vn.madoez.antixag.listener;

import vn.madoez.antixag.AntiXagPlugin;
import vn.madoez.antixag.config.WorldSettings;
import vn.madoez.antixag.protocol.ChunkObfuscator;
import vn.madoez.antixag.protocol.ChunkPacketCodec;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedLevelChunkData;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Intercepts the chunk packet and replaces the hidden ores with their
 * camouflage block state.
 *
 * <p>The packet is modified before it is sent to the client, so the world
 * itself stays untouched.</p>
 */
public final class ChunkPacketListener extends PacketAdapter {

    private final AntiXagPlugin plugin;
    private final ChunkObfuscator obfuscator;

    public ChunkPacketListener(AntiXagPlugin plugin, ChunkObfuscator obfuscator) {
        super(plugin, ListenerPriority.LOW, PacketType.Play.Server.MAP_CHUNK);
        this.plugin = plugin;
        this.obfuscator = obfuscator;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        World world = player.getWorld();
        WorldSettings settings = plugin.config().settingsFor(world);
        if (settings == null || !settings.enabled()) {
            return;
        }
        if (plugin.config().hideOnlyInSurvival() && player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (player.hasPermission("antixag.bypass")) {
            return;
        }

        PacketContainer packet = event.getPacket();

        int chunkX;
        int chunkZ;
        try {
            chunkX = packet.getIntegers().read(0);
            chunkZ = packet.getIntegers().read(1);
        } catch (RuntimeException exception) {
            return;
        }

        try {
            if (!rewrite(packet, chunkX, chunkZ, world, settings, player)) {
                plugin.getLogger().fine("Unable to rewrite chunk packet for " + player.getName());
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to obfuscate chunk " + chunkX + "/" + chunkZ
                    + ": " + exception.getMessage());
            if (plugin.config().debug()) {
                plugin.getLogger().warning("Stack trace: " + stackTrace(exception));
            }
        }
    }

    /**
     * Rewrites the chunk packet.
     *
     * <p>Two strategies are used. The first one relies on the ProtocolLib
     * wrapper, the second one falls back to plain reflection for server
     * versions where the wrapper is not able to locate the packet data.</p>
     */
    private boolean rewrite(PacketContainer packet,
                            int chunkX,
                            int chunkZ,
                            World world,
                            WorldSettings settings,
                            Player player) {
        byte[] buffer = readBuffer(packet);
        if (buffer == null || buffer.length == 0) {
            return false;
        }

        ChunkObfuscator.Result result = obfuscator.obfuscate(buffer, chunkX, chunkZ,
                world.getMinHeight(), world, settings);

        plugin.countChunk();

        if (result.hiddenCount() <= 0) {
            return true;
        }

        if (!writeBuffer(packet, result.buffer())) {
            return false;
        }

        plugin.countOres(result.hiddenCount());

        // The chunk was rewritten, so the ores that were revealed inside it
        // are hidden again. The reveal state has to be dropped.
        plugin.revealer().clearRevealed(player, chunkX, chunkZ);

        if (plugin.config().debug()) {
            plugin.debug("Hid " + result.hiddenCount() + " ores in chunk "
                    + chunkX + "/" + chunkZ + " for " + player.getName());
        }
        return true;
    }

    private byte[] readBuffer(PacketContainer packet) {
        try {
            WrappedLevelChunkData.ChunkData chunkData = packet.getLevelChunkData().read(0);
            if (chunkData != null) {
                byte[] buffer = chunkData.getBuffer();
                if (buffer != null && buffer.length > 0) {
                    return buffer;
                }
            }
        } catch (Throwable throwable) {
            plugin.debug("ProtocolLib chunk wrapper unavailable: " + throwable.getMessage());
        }

        ChunkPacketCodec.ChunkBuffer fallback = ChunkPacketCodec.locate(packet);
        return fallback == null ? null : fallback.read();
    }

    private boolean writeBuffer(PacketContainer packet, byte[] buffer) {
        try {
            WrappedLevelChunkData.ChunkData chunkData = packet.getLevelChunkData().read(0);
            if (chunkData != null) {
                chunkData.setBuffer(buffer);
                return true;
            }
        } catch (Throwable throwable) {
            plugin.debug("ProtocolLib chunk wrapper unavailable: " + throwable.getMessage());
        }

        ChunkPacketCodec.ChunkBuffer fallback = ChunkPacketCodec.locate(packet);
        return fallback != null && fallback.write(buffer);
    }

    private static String stackTrace(Throwable throwable) {
        java.io.StringWriter writer = new java.io.StringWriter();
        throwable.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }
}