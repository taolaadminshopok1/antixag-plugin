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

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests the grouping of the ores into veins.
 */
class VeinScannerTest {

    /**
     * A block source that is backed by a plain map.
     */
    private static final class MapSource implements BlockSource {

        private final Map<String, Material> blocks = new HashMap<>();

        MapSource put(int x, int y, int z, Material material) {
            blocks.put(x + "/" + y + "/" + z, material);
            return this;
        }

        @Override
        public Material materialAt(int x, int y, int z) {
            Material material = blocks.get(x + "/" + y + "/" + z);
            return material == null ? Material.STONE : material;
        }
    }

    @Test
    void groupsConnectedOresOfTheSameMaterial() {
        MapSource source = new MapSource()
                .put(0, 0, 0, Material.DIAMOND_ORE)
                .put(1, 0, 0, Material.DIAMOND_ORE)
                .put(2, 0, 0, Material.DIAMOND_ORE);

        Set<Material> ores = EnumSet.of(Material.DIAMOND_ORE);
        List<OreVein> veins = VeinScanner.scan(source, ores, -5, -5, -5, 5, 5, 5);

        assertEquals(1, veins.size());
        assertEquals(3, veins.get(0).positions().size());
    }

    @Test
    void keepsDifferentMaterialsApart() {
        // A diamond ore touches an iron ore. They must not end up in the same
        // vein, because the camouflage of the two materials is different.
        MapSource source = new MapSource()
                .put(0, 0, 0, Material.DIAMOND_ORE)
                .put(1, 0, 0, Material.IRON_ORE)
                .put(2, 0, 0, Material.IRON_ORE);

        Set<Material> ores = EnumSet.of(Material.DIAMOND_ORE, Material.IRON_ORE);
        List<OreVein> veins = VeinScanner.scan(source, ores, -5, -5, -5, 5, 5, 5);

        assertEquals(2, veins.size());
        for (OreVein vein : veins) {
            assertTrue(vein.positions().size() == 1 || vein.positions().size() == 2);
        }
    }

    @Test
    void findsEveryVeinOfTheRegion() {
        MapSource source = new MapSource()
                // The first vein.
                .put(0, 0, 0, Material.GOLD_ORE)
                .put(1, 0, 0, Material.GOLD_ORE)
                // The second vein, far away.
                .put(20, 0, 20, Material.GOLD_ORE)
                .put(21, 0, 20, Material.GOLD_ORE)
                .put(22, 0, 20, Material.GOLD_ORE);

        Set<Material> ores = EnumSet.of(Material.GOLD_ORE);
        List<OreVein> veins = VeinScanner.scan(source, ores, -5, -5, -5, 30, 5, 30);

        assertEquals(2, veins.size());
    }

    @Test
    void anEmptyOreSetProducesNoVeins() {
        MapSource source = new MapSource().put(0, 0, 0, Material.DIAMOND_ORE);
        List<OreVein> veins = VeinScanner.scan(source, EnumSet.noneOf(Material.class),
                -5, -5, -5, 5, 5, 5);
        assertTrue(veins.isEmpty());
    }
}