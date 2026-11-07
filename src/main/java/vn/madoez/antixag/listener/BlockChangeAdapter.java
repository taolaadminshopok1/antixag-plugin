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

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;

import vn.madoez.antixag.AntiXagPlugin;
import vn.madoez.antixag.config.WorldSettings;
import vn.madoez.antixag.protocol.BlockStateRegistry;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Rewrites the single block change packets.
 *
 * <p>Whenever the server changes a single block it sends a block change
 * packet that contains the real block state. When that block is an ore the
 * packet would reveal the ore to every x-ray client, so it is replaced with
 * the camouflage state.</p>
 *
 * <p>When a player breaks an ore the ore has to become visible again. The
 * {@link RevealTracker} remembers the positions that were revealed and this
 * adapter lets those packets pass through unchanged.</p>
 */
public final class BlockChangeAdapter extends PacketAdapter {

    /** How long a revealed block stays visible, in milliseconds. */
    private static final long REVEAL_WINDOW = 5000L;

    private final AntiXagPlugin plugin;
    private final BlockStateRegistry registry;

    /**
     * The positions that were revealed, per player.
     */
    private final Map<UUID, Map<Long, Long>> revealed = new ConcurrentHashMap<>();

    public BlockChangeAdapter(AntiXagPlugin plugin, BlockStateRegistry registry) {
        super(plugin, ListenerPriority.LOW, PacketType.Play.Server.BLOCK_CHANGE);
        this.plugin = plugin;
        this.registry = registry;
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

        WorldSettings settings = plugin.config().settingsFor(player.getWorld());
        if (settings == null || !settings.enabled()) {
            return;
        }

        PacketContainer packet = event.getPacket();

        BlockPosition position;
        try {
            position = packet.getBlockPositionModifier().read(0);
        } catch (RuntimeException exception) {
            return;
        }
        if (position == null) {
            return;
        }

        Material material;
        try {
            WrappedBlockData data = packet.getBlockData().read(0);
            material = data == null ? null : data.getType();
        } catch (RuntimeException exception) {
            return;
        }
        if (material == null) {
            return;
        }

        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();

        if (!settings.hiddenBlocks().contains(material)) {
            // The changed block is not an ore. When it used to hide an ore,
            // that ore becomes visible, so the position has to be released.
            release(player.getUniqueId(), x, y, z);
            return;
        }

        if (isRevealed(player.getUniqueId(), x, y, z)) {
            // The player is allowed to see this ore.
            return;
        }

        Material camouflage = plugin.obfuscator().camouflageFor(settings,
                player.getWorld().getEnvironment(), y);
        int camouflageId = registry.idOf(camouflage);
        if (camouflageId < 0) {
            return;
        }
        try {
            packet.getBlockData().write(0, WrappedBlockData.createData(camouflage));
        } catch (RuntimeException exception) {
            plugin.getLogger().fine("Unable to replace the block change packet: "
                    + exception.getMessage());
        }
    }

    /**
     * Marks a position as revealed for the given player.
     *
     * @param player the player that is allowed to see the block
     * @param x      the x coordinate
     * @param y      the y coordinate
     * @param z      the z coordinate
     */
    public void reveal(UUID player, int x, int y, int z) {
        Map<Long, Long> positions = revealed.computeIfAbsent(player,
                uuid -> new ConcurrentHashMap<>());
        positions.put(key(x, y, z), System.currentTimeMillis());
    }

    private void release(UUID player, int x, int y, int z) {
        Map<Long, Long> positions = revealed.get(player);
        if (positions == null) {
            return;
        }
        positions.remove(key(x, y, z));
    }

    private boolean isRevealed(UUID player, int x, int y, int z) {
        Map<Long, Long> positions = revealed.get(player);
        if (positions == null) {
            return false;
        }
        Long timestamp = positions.get(key(x, y, z));
        if (timestamp == null) {
            return false;
        }
        if (System.currentTimeMillis() - timestamp > REVEAL_WINDOW) {
            positions.remove(key(x, y, z));
            return false;
        }
        return true;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
