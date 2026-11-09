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
import vn.madoez.antixag.protocol.BlockStateRegistry;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rewrites the single block change packets.
 *
 * <p>Whenever the server changes a single block it sends a block change
 * packet that contains the real block state. When that block is an ore the
 * packet would reveal the ore to every x-ray client, so it is replaced with
 * the camouflage state.</p>
 *
 * <p>The adapter is also responsible for the progressive reveal. When a
 * player digs into the rock and exposes an ore, the chunk data of that chunk
 * still shows the camouflage. The adapter therefore sends an explicit block
 * change with the real ore state to the player that caused the change, so
 * that mining feels natural.</p>
 */
public final class BlockChangeAdapter extends PacketAdapter {

    /** How long a revealed block stays visible, in milliseconds. */
    private static final long REVEAL_WINDOW = 300_000L;

    /** The maximum amount of blocks that a single vein reveal may touch. */
    private static final int MAX_VEIN_SIZE = 256;

    /** The six face neighbours of a block. */
    private static final int[][] OFFSETS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

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
        if (plugin.config().hideOnlyInSurvival()
                && player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
            return;
        }
        if (player.hasPermission("antixag.bypass")) {
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
            // The changed block is not an ore, so the packet is passed through
            // unchanged. The reveal state is intentionally kept, because the
            // ore next to the removed block stays visible.
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
     * Reveals the ores that became visible because the player dug into them.
     *
     * <p>The method is called after a block was broken. Every hidden ore that
     * touches the broken block is sent to the player with its real state, so
     * that the player can mine it. Depending on the reveal mode the whole
     * vein is revealed as well.</p>
     *
     * @param player the player that caused the change
     * @param x      the x coordinate of the broken block
     * @param y      the y coordinate of the broken block
     * @param z      the z coordinate of the broken block
     */
    public void revealAround(Player player, int x, int y, int z) {
        WorldSettings settings = plugin.config().settingsFor(player.getWorld());
        if (settings == null || !settings.enabled()) {
            return;
        }
        if (plugin.config().hideOnlyInSurvival()
                && player.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
            return;
        }
        if (player.hasPermission("antixag.bypass")) {
            return;
        }

        World world = player.getWorld();
        Set<Long> targets = new HashSet<>();

        // The broken block itself and its direct neighbours may be ores.
        collectOre(world, settings, x, y, z, targets);
        for (int[] offset : OFFSETS) {
            collectOre(world, settings, x + offset[0], y + offset[1], z + offset[2], targets);
        }

        if (settings.revealMode() == WorldSettings.RevealMode.VEIN) {
            // Reveal the complete vein of every exposed ore.
            List<Long> seeds = new ArrayList<>(targets);
            for (long seed : seeds) {
                collectVein(world, settings, seed, targets);
            }
        }

        for (long key : targets) {
            reveal(player, key);
        }
    }

    /**
     * Adds the ore at the given position to the target set.
     */
    private void collectOre(World world, WorldSettings settings, int x, int y, int z,
                            Set<Long> targets) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return;
        }
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return;
        }
        Material material = world.getBlockAt(x, y, z).getType();
        if (!settings.hiddenBlocks().contains(material)) {
            return;
        }
        targets.add(key(x, y, z));
    }

    /**
     * Collects the complete vein that contains the given position.
     */
    private void collectVein(World world, WorldSettings settings, long seed, Set<Long> targets) {
        if (!targets.contains(seed)) {
            return;
        }
        Material material = materialOf(world, settings, seed);
        if (material == null) {
            return;
        }
        int[] start = unpack(seed);
        List<int[]> queue = new ArrayList<>();
        queue.add(start);
        int processed = 0;
        while (!queue.isEmpty() && processed < MAX_VEIN_SIZE) {
            int[] current = queue.remove(queue.size() - 1);
            processed++;
            for (int[] offset : OFFSETS) {
                int nx = current[0] + offset[0];
                int ny = current[1] + offset[1];
                int nz = current[2] + offset[2];
                if (ny < world.getMinHeight() || ny >= world.getMaxHeight()) {
                    continue;
                }
                if (!world.isChunkLoaded(nx >> 4, nz >> 4)) {
                    continue;
                }
                if (world.getBlockAt(nx, ny, nz).getType() != material) {
                    continue;
                }
                long neighbour = key(nx, ny, nz);
                if (targets.add(neighbour)) {
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }
    }

    private Material materialOf(World world, WorldSettings settings, long key) {
        int[] position = unpack(key);
        if (position[1] < world.getMinHeight() || position[1] >= world.getMaxHeight()) {
            return null;
        }
        if (!world.isChunkLoaded(position[0] >> 4, position[2] >> 4)) {
            return null;
        }
        Material material = world.getBlockAt(position[0], position[1], position[2]).getType();
        return settings.hiddenBlocks().contains(material) ? material : null;
    }

    /**
     * Sends the real block state of a revealed position to the player.
     */
    private void reveal(Player player, long key) {
        World world = player.getWorld();
        int[] position = unpack(key);
        int x = position[0];
        int y = position[1];
        int z = position[2];
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return;
        }
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return;
        }
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() == Material.AIR) {
            return;
        }
        int id = registry.idOf(block.getType());
        if (id < 0) {
            return;
        }
        // The position has to be marked before the packet is sent. The
        // packet travels through this very adapter again, and without the
        // mark the ore would be disguised a second time.
        markRevealed(player.getUniqueId(), key);
        try {
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.BLOCK_CHANGE);
            packet.getBlockPositionModifier().write(0, new BlockPosition(x, y, z));
            packet.getBlockData().write(0, WrappedBlockData.createData(block.getType()));
            plugin.protocolManager().sendServerPacket(player, packet);
        } catch (RuntimeException exception) {
            plugin.debug("Unable to reveal the block at " + x + "/" + y + "/" + z
                    + ": " + exception.getMessage());
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
        markRevealed(player, key(x, y, z));
    }

    private void markRevealed(UUID player, long key) {
        Map<Long, Long> positions = revealed.computeIfAbsent(player,
                uuid -> new ConcurrentHashMap<>());
        positions.put(key, System.currentTimeMillis());
    }

    /**
     * Forgets every revealed position inside the given chunk.
     *
     * <p>The chunk is sent again after a teleport or a respawn. The fresh
     * chunk data contains the camouflage, so the old reveals have to be
     * dropped to keep the client and the plugin in sync.</p>
     *
     * @param player the player that receives the chunk
     * @param chunkX the x coordinate of the chunk
     * @param chunkZ the z coordinate of the chunk
     */
    public void clearRevealed(Player player, int chunkX, int chunkZ) {
        Map<Long, Long> positions = revealed.get(player.getUniqueId());
        if (positions == null) {
            return;
        }
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        positions.keySet().removeIf(key -> {
            int[] position = unpack(key);
            return position[0] >= minX && position[0] < minX + 16
                    && position[2] >= minZ && position[2] < minZ + 16;
        });
    }

    private boolean isRevealed(UUID player, int x, int y, int z) {
        Map<Long, Long> positions = revealed.get(player);
        if (positions == null) {
            return false;
        }
        long key = key(x, y, z);
        Long timestamp = positions.get(key);
        if (timestamp == null) {
            return false;
        }
        if (System.currentTimeMillis() - timestamp > REVEAL_WINDOW) {
            positions.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Packs a position into a single long.
     *
     * <p>The y coordinate is shifted by 2048 so that the negative heights of
     * the modern worlds do not collide with the positive ones.</p>
     */
    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | ((y + 2048) & 0xFFF);
    }

    /**
     * Unpacks a position that was packed by {@link #key(int, int, int)}.
     */
    private static int[] unpack(long key) {
        int x = (int) ((key >> 38) & 0x3FFFFFF);
        int z = (int) ((key >> 12) & 0x3FFFFFF);
        int y = (int) (key & 0xFFF) - 2048;
        // Restore the sign of the coordinates.
        if ((x & 0x2000000) != 0) {
            x -= 0x4000000;
        }
        if ((z & 0x2000000) != 0) {
            z -= 0x4000000;
        }
        return new int[]{x, y, z};
    }
}