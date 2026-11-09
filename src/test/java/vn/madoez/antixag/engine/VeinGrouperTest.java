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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import vn.madoez.antixag.util.BlockPos;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests the grouping of the ores into veins.
 */
class VeinGrouperTest {

    /**
     * A lookup that is backed by a plain map.
     */
    private static final class MapLookup implements VeinGrouper.MaterialLookup {

        private final Map<BlockPos, Material> blocks = new HashMap<>();

        MapLookup put(int x, int y, int z, Material material) {
            blocks.put(new BlockPos(x, y, z), material);
            return this;
        }

        @Override
        public Material materialAt(BlockPos pos) {
            Material material = blocks.get(pos);
            return material == null ? Material.STONE : material;
        }
    }

    @Test
    void collectsAWholeVein() {
        MapLookup lookup = new MapLookup()
                .put(0, 0, 0, Material.DIAMOND_ORE)
                .put(1, 0, 0, Material.DIAMOND_ORE)
                .put(2, 0, 0, Material.DIAMOND_ORE);

        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> vein = VeinGrouper.collect(new BlockPos(0, 0, 0),
                Material.DIAMOND_ORE, lookup, visited);

        assertEquals(3, vein.size());
        assertEquals(3, visited.size());
    }

    /**
     * This is the regression test for the bug that leaked ores to x-ray
     * clients.
     *
     * <p>A gold ore touches a diamond ore. The flood fill used to mark the
     * diamond ore as visited before it knew that its material was different.
     * The vein of the diamond ore was then skipped completely and the diamond
     * ore stayed visible.</p>
     */
    @Test
    void aNeighbourOfADifferentMaterialIsNotSwallowed() {
        MapLookup lookup = new MapLookup()
                .put(0, 0, 0, Material.GOLD_ORE)
                .put(1, 0, 0, Material.DIAMOND_ORE)
                .put(2, 0, 0, Material.DIAMOND_ORE);

        Set<BlockPos> visited = new HashSet<>();

        // The first vein is the single gold ore.
        List<BlockPos> gold = VeinGrouper.collect(new BlockPos(0, 0, 0),
                Material.GOLD_ORE, lookup, visited);
        assertEquals(1, gold.size());

        // The diamond ore must not have been marked as visited, so that its
        // own vein can still be found.
        assertTrue(visited.size() == 1,
                "Only the gold ore may be visited, but " + visited.size() + " were");

        List<BlockPos> diamond = VeinGrouper.collect(new BlockPos(1, 0, 0),
                Material.DIAMOND_ORE, lookup, visited);
        assertEquals(2, diamond.size());
    }

    @Test
    void everyVeinOfARegionIsFound() {
        MapLookup lookup = new MapLookup()
                .put(0, 0, 0, Material.GOLD_ORE)
                .put(1, 0, 0, Material.GOLD_ORE)
                .put(20, 0, 20, Material.GOLD_ORE)
                .put(21, 0, 20, Material.GOLD_ORE)
                .put(22, 0, 20, Material.GOLD_ORE);

        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> first = VeinGrouper.collect(new BlockPos(0, 0, 0),
                Material.GOLD_ORE, lookup, visited);
        assertEquals(2, first.size());

        List<BlockPos> second = VeinGrouper.collect(new BlockPos(20, 0, 20),
                Material.GOLD_ORE, lookup, visited);
        assertEquals(3, second.size());
        assertEquals(5, visited.size());
    }
}