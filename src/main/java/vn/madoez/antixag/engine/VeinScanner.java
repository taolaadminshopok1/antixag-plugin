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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Groups ore blocks into {@link OreVein veins}.
 *
 * <p>The scanner performs a flood fill over the six axis aligned neighbours of
 * every ore block. Only blocks of the exact same material are joined, which
 * keeps a gold vein separate from an iron vein that happens to touch it.</p>
 */
public final class VeinScanner {

    private VeinScanner() {
    }

    /**
     * Scans the given region for veins.
     *
     * @param source       the block source to read from
     * @param hiddenBlocks the ore materials that should be grouped
     * @param minX         minimum x of the region
     * @param minY         minimum y of the region
     * @param minZ         minimum z of the region
     * @param maxX         maximum x of the region
     * @param maxY         maximum y of the region
     * @param maxZ         maximum z of the region
     * @return every vein found inside the region
     */
    public static List<OreVein> scan(BlockSource source,
                                      Set<Material> hiddenBlocks,
                                      int minX, int minY, int minZ,
                                      int maxX, int maxY, int maxZ) {
        List<OreVein> veins = new ArrayList<>();
        if (hiddenBlocks.isEmpty()) {
            return veins;
        }

        Set<BlockPos> visited = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos start = new BlockPos(x, y, z);
                    if (visited.contains(start)) {
                        continue;
                    }
                    Material material = source.materialAt(x, y, z);
                    if (!hiddenBlocks.contains(material)) {
                        continue;
                    }

                    List<BlockPos> group = VeinGrouper.collect(start, material,
                            pos -> source.materialAt(pos.x(), pos.y(), pos.z()), visited);
                    veins.add(new OreVein(material, new HashSet<>(group)));
                }
            }
        }
        return veins;
    }
}