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
import vn.madoez.antixag.util.BlockPos;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The anti-xray algorithm.
 *
 * <p>The engine works in two phases:</p>
 * <ol>
 *   <li><b>Scan</b> - every ore block of a region is grouped into veins.</li>
 *   <li><b>Decide</b> - for every vein it is determined whether the vein is
 *       concealed from the client and which camouflage material is used.</li>
 * </ol>
 *
 * <p>The engine is completely free of Bukkit state, which makes it unit
 * testable and keeps the packet layer free of game logic.</p>
 */
public final class XrayEngine {

    /** The six face neighbours of a block. */
    private static final int[][] OFFSETS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    /**
     * Materials that count as "air" when checking whether an ore is exposed.
     * Transparent blocks such as glass or leaves also expose the ore, so they
     * are part of this set.
     */
    private static final Set<Material> TRANSPARENT = EnumSet.of(
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
            Material.WATER, Material.LAVA,
            Material.GLASS, Material.GLASS_PANE, Material.TINTED_GLASS,
            Material.OAK_LEAVES, Material.BIRCH_LEAVES, Material.SPRUCE_LEAVES,
            Material.JUNGLE_LEAVES, Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES,
            Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES, Material.AZALEA_LEAVES
    );

    private final Random random;

    public XrayEngine(Random random) {
        this.random = random;
    }

    /**
     * Decides the camouflage material for an ore.
     *
     * <p>The palette is picked based on the depth of the ore so that a diamond
     * vein at y=12 looks like deepslate and a diamond vein at y=60 looks like
     * stone. When {@code randomizeCamouflage} is enabled the palette is
     * shuffled for every ore, otherwise the first entry is used.</p>
     *
     * @param settings    the world settings
     * @param environment the environment of the world
     * @param y           the y level of the ore
     * @return the material the ore should be disguised with
     */
    public Material camouflageFor(WorldSettings settings, World.Environment environment, int y) {
        List<Material> palette = paletteFor(settings, environment, y);
        if (palette.isEmpty()) {
            return Material.STONE;
        }
        if (!settings.randomizeCamouflage() || palette.size() == 1) {
            return palette.get(0);
        }
        return palette.get(random.nextInt(palette.size()));
    }

    private List<Material> paletteFor(WorldSettings settings, World.Environment environment, int y) {
        if (environment == World.Environment.NETHER) {
            return settings.netherPalette().isEmpty() ? settings.overworldPalette() : settings.netherPalette();
        }
        if (environment == World.Environment.THE_END) {
            return settings.endPalette().isEmpty() ? settings.overworldPalette() : settings.endPalette();
        }
        if (y < 0) {
            return settings.deepslatePalette().isEmpty()
                    ? settings.overworldPalette()
                    : settings.deepslatePalette();
        }
        return settings.overworldPalette();
    }

    /**
     * Determines which ores of a region have to be concealed.
     *
     * @param source   the block source
     * @param settings the world settings
     * @param minX     minimum x of the region
     * @param minY     minimum y of the region
     * @param minZ     minimum z of the region
     * @param maxX     maximum x of the region
     * @param maxY     maximum y of the region
     * @param maxZ     maximum z of the region
     * @return a map from position to the camouflage material
     */
    public Map<BlockPos, Material> computeConcealment(BlockSource source,
                                                       WorldSettings settings,
                                                       int minX, int minY, int minZ,
                                                       int maxX, int maxY, int maxZ) {
        Map<BlockPos, Material> concealed = new HashMap<>();
        if (!settings.enabled() || settings.hiddenBlocks().isEmpty()) {
            return concealed;
        }

        List<OreVein> veins = VeinScanner.scan(source, settings.hiddenBlocks(),
                minX, minY, minZ, maxX, maxY, maxZ);

        for (OreVein vein : veins) {
            boolean veinBuried = false;
            for (BlockPos pos : vein.positions()) {
                if (isBuried(source, pos, minX, minY, minZ, maxX, maxY, maxZ)) {
                    veinBuried = true;
                    break;
                }
            }

            switch (settings.hideMode()) {
                case ALL:
                    concealAll(source, settings, vein, concealed);
                    break;
                case FULL_VEIN:
                    if (veinBuried) {
                        concealAll(source, settings, vein, concealed);
                    }
                    break;
                case GRADUALLY:
                default:
                    for (BlockPos pos : vein.positions()) {
                        if (isBuried(source, pos, minX, minY, minZ, maxX, maxY, maxZ)) {
                            concealed.put(pos, camouflageFor(settings, World.Environment.NORMAL, pos.y()));
                        }
                    }
                    break;
            }
        }
        return concealed;
    }

    private void concealAll(BlockSource source,
                            WorldSettings settings,
                            OreVein vein,
                            Map<BlockPos, Material> concealed) {
        for (BlockPos pos : vein.positions()) {
            concealed.put(pos, camouflageFor(settings, World.Environment.NORMAL, pos.y()));
        }
    }

    /**
     * Determines whether a vein has to be concealed from the client.
     *
     * @param source   the block source
     * @param settings the world settings
     * @param vein     the vein to inspect
     * @return {@code true} when the vein must be hidden
     */
    public boolean shouldConceal(BlockSource source, WorldSettings settings, OreVein vein) {
        switch (settings.hideMode()) {
            case ALL:
                return true;
            case FULL_VEIN:
            case GRADUALLY:
            default:
                for (BlockPos pos : vein.positions()) {
                    if (isBuried(source, pos)) {
                        return true;
                    }
                }
                return false;
        }
    }

    /**
     * Checks whether every neighbour of the given position is a solid block.
     *
     * <p>An ore is considered buried when none of its six face neighbours is
     * air, water, lava or another see-through block.</p>
     *
     * @param source the block source
     * @param pos    the position to test
     * @return {@code true} when the block is fully surrounded
     */
    public boolean isBuried(BlockSource source, BlockPos pos) {
        return isBuried(source, pos,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    private boolean isBuried(BlockSource source, BlockPos pos,
                              int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
        for (int[] offset : OFFSETS) {
            int nx = pos.x() + offset[0];
            int ny = pos.y() + offset[1];
            int nz = pos.z() + offset[2];
            if (nx < minX || nx > maxX || ny < minY || ny > maxY || nz < minZ || nz > maxZ) {
                // Outside of the scanned region. The caller decides whether
                // that counts as "hidden", so be conservative and report the
                // ore as buried.
                return true;
            }
            Material neighbour = source.materialAt(nx, ny, nz);
            if (isTransparent(neighbour)) {
                return false;
            }
        }
        return true;
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
        if (TRANSPARENT.contains(material)) {
            return true;
        }
        return material.isTransparent() && !material.isSolid();
    }
}
