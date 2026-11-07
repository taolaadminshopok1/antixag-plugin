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

import vn.madoez.antixag.config.WorldSettings;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Inspects the world to find out which ores are exposed and which ores are
 * completely buried.
 *
 * <p>The scanner is used by the block update listener. It only looks at the
 * direct neighbours of a changed block, which keeps the cost of a block
 * change constant.</p>
 */
public final class WorldScanner {

    /** The six face neighbours of a block. */
    private static final int[][] OFFSETS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private final World world;
    private final WorldSettings settings;

    public WorldScanner(World world, WorldSettings settings) {
        this.world = world;
        this.settings = settings;
    }

    /**
     * Checks whether the given ore is completely surrounded by solid blocks.
     *
     * @param x the absolute x coordinate
     * @param y the absolute y coordinate
     * @param z the absolute z coordinate
     * @return {@code true} when the ore is buried
     */
    public boolean isBuried(int x, int y, int z) {
        for (int[] offset : OFFSETS) {
            Block neighbour = world.getBlockAt(x + offset[0], y + offset[1], z + offset[2]);
            if (isTransparent(neighbour.getType())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether the given material is an ore that has to be concealed.
     *
     * @param material the material to test
     * @return {@code true} when the material is a hidden ore
     */
    public boolean isHiddenOre(Material material) {
        return settings.hiddenBlocks().contains(material);
    }

    /**
     * Returns the camouflage material for an ore at the given height.
     *
     * @param y the absolute y coordinate
     * @return the camouflage material
     */
    public Material camouflageAt(int y) {
        java.util.List<Material> palette;
        switch (world.getEnvironment()) {
            case NETHER:
                palette = settings.netherPalette().isEmpty()
                        ? settings.overworldPalette() : settings.netherPalette();
                break;
            case THE_END:
                palette = settings.endPalette().isEmpty()
                        ? settings.overworldPalette() : settings.endPalette();
                break;
            default:
                if (y < 0) {
                    palette = settings.deepslatePalette().isEmpty()
                            ? settings.overworldPalette() : settings.deepslatePalette();
                } else {
                    palette = settings.overworldPalette();
                }
                break;
        }
        if (palette.isEmpty()) {
            return Material.STONE;
        }
        return palette.get(0);
    }

    /**
     * Checks whether the given material lets light and sight through.
     *
     * @param material the material to test
     * @return {@code true} when the material is transparent
     */
    public static boolean isTransparent(Material material) {
        if (material == null) {
            return true;
        }
        if (material == Material.AIR || material == Material.CAVE_AIR
                || material == Material.VOID_AIR || material == Material.WATER
                || material == Material.LAVA) {
            return true;
        }
        return material.isTransparent() && !material.isSolid();
    }
}
