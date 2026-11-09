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
package vn.madoez.antixag.engine;

import vn.madoez.antixag.util.BlockPos;

import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Groups the ores into veins with a flood fill over the six face
 * neighbours.
 *
 * <p>Only blocks of the exact same material are joined. This keeps a gold
 * vein apart from an iron vein that happens to touch it.</p>
 *
 * <p>The class exists because this logic was implemented twice. One of the
 * two copies marked a neighbour as visited <em>before</em> its material was
 * known, which made the vein of that neighbour disappear completely. The
 * ore was then never hidden and an x-ray client could see it.</p>
 */
public final class VeinGrouper {

    /** The six face neighbours of a block. */
    private static final int[][] OFFSETS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    /**
     * Reads the material of a position.
     */
    @FunctionalInterface
    public interface MaterialLookup {

        /**
         * Returns the material at the given absolute position.
         *
         * @param pos the absolute position
         * @return the material, may be {@code null} when it is unknown
         */
        Material materialAt(BlockPos pos);
    }

    private VeinGrouper() {
    }

    /**
     * Collects the vein that contains the given position.
     *
     * <p>The positions that belong to the vein are added to the given set.
     * The set is used to remember which positions were already assigned to a
     * vein, so that the caller can start the next search at the next free
     * position.</p>
     *
     * @param start    the position to start at
     * @param material the material of the vein
     * @param lookup   the accessor for the block data
     * @param visited  the positions that already belong to a vein
     * @return every position of the vein
     */
    public static List<BlockPos> collect(BlockPos start,
                                         Material material,
                                         MaterialLookup lookup,
                                         Set<BlockPos> visited) {
        List<BlockPos> vein = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            vein.add(current);
            for (int[] offset : OFFSETS) {
                BlockPos neighbour = current.offset(offset[0], offset[1], offset[2]);
                Material other = lookup.materialAt(neighbour);
                if (other != material) {
                    // A different material. The neighbour must not be marked
                    // as visited, otherwise the vein that it belongs to would
                    // be skipped and its ores would stay visible.
                    continue;
                }
                if (!visited.add(neighbour)) {
                    continue;
                }
                queue.add(neighbour);
            }
        }
        return vein;
    }
}