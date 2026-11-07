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
package vn.madoez.antixag.protocol;

import vn.madoez.antixag.config.WorldSettings;
import vn.madoez.antixag.util.BlockPos;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Rewrites the chunk buffer so that hidden ores are replaced by their
 * camouflage block state.
 *
 * <p>The obfuscator works directly on the data that is sent to the client.
 * This has two big advantages:</p>
 * <ul>
 *   <li>the world itself is never modified, so other plugins and the server
 *       logic keep working with the real ores, and</li>
 *   <li>the ores that are visible to the client are exactly the ores that
 *       the server believes to be hidden.</li>
 * </ul>
 *
 * <p>The buffer of the chunk packet contains one section after another,
 * starting at the bottom of the world. Every section consists of</p>
 *
 * <pre>
 *   short blockStateCount
 *   palettedContainer blockStates
 *   palettedContainer biomes
 * </pre>
 */
public final class ChunkObfuscator {

    /** The six face neighbours of a block. */
    private static final int[][] OFFSETS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private final BlockStateRegistry registry;
    private final int globalBits;
    private final Random random;

    public ChunkObfuscator(BlockStateRegistry registry, Random random) {
        this.registry = registry;
        this.globalBits = registry.globalBits();
        this.random = random;
    }

    /**
     * The result of a chunk rewrite.
     */
    public static final class Result {

        private final byte[] buffer;
        private final int hiddenCount;

        Result(byte[] buffer, int hiddenCount) {
            this.buffer = buffer;
            this.hiddenCount = hiddenCount;
        }

        public byte[] buffer() {
            return buffer;
        }

        public int hiddenCount() {
            return hiddenCount;
        }
    }

    /**
     * Rewrites the given section buffer.
     *
     * @param buffer   the raw section buffer of the chunk packet
     * @param chunkX   the x coordinate of the chunk
     * @param chunkZ   the z coordinate of the chunk
     * @param minY     the minimum y coordinate of the world
     * @param world    the world the chunk belongs to
     * @param settings the settings of the world
     * @return the rewritten buffer
     */
    public Result obfuscate(byte[] buffer,
                            int chunkX,
                            int chunkZ,
                            int minY,
                            World world,
                            WorldSettings settings) {
        if (buffer == null || buffer.length == 0 || !settings.enabled()
                || settings.hiddenBlocks().isEmpty()) {
            return new Result(buffer, 0);
        }

        ByteCursor reader = new ByteCursor(buffer);

        List<Section> sections = new ArrayList<>();
        int sectionIndex = 0;
        while (reader.hasRemaining()) {
            int start = reader.position();
            short blockStateCount = reader.readShort();
            PalettedContainer blockStates = PalettedContainer.decodeBlockStates(reader);
            PalettedContainer biomes = PalettedContainer.decodeBiomes(reader);
            Section section = new Section(start, blockStateCount, blockStates, biomes);
            section.baseY = minY + sectionIndex * 16;
            sectionIndex++;
            sections.add(section);
        }

        if (sections.isEmpty()) {
            return new Result(buffer, 0);
        }

        Lookup lookup = new Lookup(sections, chunkX, chunkZ, minY, world);
        int hidden = conceal(sections, lookup, settings);

        if (hidden == 0) {
            return new Result(buffer, 0);
        }

        byte[] output = new byte[buffer.length + 2048];
        ByteCursor cursor = new ByteCursor(output);
        for (Section section : sections) {
            cursor.writeShort(section.blockStateCount);
            PalettedContainer rebuilt = section.blockStates.rebuild(globalBits,
                    PalettedContainer.MAX_LOCAL_BLOCK_STATE_BITS, true);
            byte[] encoded = rebuilt.encode();
            cursor.writeBytes(encoded);
            // The biome container is never modified, so it is copied verbatim.
            byte[] biomes = section.biomes.encode();
            cursor.writeBytes(biomes);
        }

        byte[] result = new byte[cursor.position()];
        System.arraycopy(output, 0, result, 0, result.length);
        return new Result(result, hidden);
    }

    private int conceal(List<Section> sections, Lookup lookup, WorldSettings settings) {
        // Collect every ore of the chunk first.
        Map<BlockPos, Material> ores = new HashMap<>();
        for (Section section : sections) {
            int[] entries = section.blockStates.entries();
            for (int i = 0; i < entries.length; i++) {
                int id = section.blockStates.valueAt(i);
                if (id < 0) {
                    continue;
                }
                Material material = lookup.materialOfId(id);
                if (material == null || !settings.hiddenBlocks().contains(material)) {
                    continue;
                }
                int x = i & 15;
                int z = (i >> 4) & 15;
                int y = section.baseY + ((i >> 8) & 15);
                ores.put(new BlockPos(lookup.originX + x, y, lookup.originZ + z), material);
            }
        }

        if (ores.isEmpty()) {
            return 0;
        }

        int hidden = 0;
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos start : ores.keySet()) {
            if (!visited.add(start)) {
                continue;
            }
            List<BlockPos> vein = collectVein(start, ores, visited, lookup);

            boolean veinBuried = false;
            for (BlockPos pos : vein) {
                if (lookup.isBuried(pos)) {
                    veinBuried = true;
                    break;
                }
            }

            switch (settings.hideMode()) {
                case ALL:
                    hidden += concealPositions(vein, lookup, settings);
                    break;
                case FULL_VEIN:
                    if (veinBuried) {
                        hidden += concealPositions(vein, lookup, settings);
                    }
                    break;
                case GRADUALLY:
                default:
                    for (BlockPos pos : vein) {
                        if (lookup.isBuried(pos)) {
                            hidden += concealPositions(List.of(pos), lookup, settings);
                        }
                    }
                    break;
            }
        }
        return hidden;
    }

    private int concealPositions(List<BlockPos> positions, Lookup lookup, WorldSettings settings) {
        int count = 0;
        for (BlockPos pos : positions) {
            Material camouflage = lookup.camouflageAt(pos, settings);
            if (camouflage == null) {
                continue;
            }
            int camouflageId = registry.idOf(camouflage);
            if (camouflageId < 0) {
                continue;
            }
            Section section = lookup.sectionOf(pos.y());
            if (section == null) {
                continue;
            }
            int index = section.indexOf(pos.x() - lookup.originX, pos.y() - section.baseY,
                    pos.z() - lookup.originZ);
            if (index < 0) {
                continue;
            }
            if (section.blockStates.valueAt(index) == camouflageId) {
                continue;
            }
            section.blockStates.replaceValue(index, camouflageId);
            count++;
        }
        return count;
    }

    private List<BlockPos> collectVein(BlockPos start,
                                       Map<BlockPos, Material> ores,
                                       Set<BlockPos> visited,
                                       Lookup lookup) {
        List<BlockPos> vein = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        Material material = ores.get(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            vein.add(current);
            for (int[] offset : OFFSETS) {
                BlockPos neighbour = current.offset(offset[0], offset[1], offset[2]);
                if (!visited.add(neighbour)) {
                    continue;
                }
                Material other = ores.get(neighbour);
                if (other == null) {
                    // The neighbour may be an ore of a neighbouring chunk.
                    other = lookup.materialAt(neighbour);
                }
                if (other == material) {
                    queue.add(neighbour);
                }
            }
        }
        return vein;
    }

    /**
     * A decoded section of the chunk.
     */
    private static final class Section {

        private final int start;
        private final short blockStateCount;
        private final PalettedContainer blockStates;
        private final PalettedContainer biomes;
        private int baseY;

        Section(int start, short blockStateCount, PalettedContainer blockStates,
                PalettedContainer biomes) {
            this.start = start;
            this.blockStateCount = blockStateCount;
            this.blockStates = blockStates;
            this.biomes = biomes;
            this.baseY = 0;
        }

        int indexOf(int x, int y, int z) {
            if (x < 0 || x > 15 || y < 0 || y > 15 || z < 0 || z > 15) {
                return -1;
            }
            return (y << 8) | (z << 4) | x;
        }
    }

    /**
     * Provides fast access to the materials of a chunk.
     */
    private final class Lookup {

        private final List<Section> sections;
        private final int originX;
        private final int originZ;
        private final int minY;
        private final World world;
        private final Map<Integer, Material> materialCache = new HashMap<>();

        Lookup(List<Section> sections, int chunkX, int chunkZ, int minY, World world) {
            this.sections = sections;
            this.originX = chunkX * 16;
            this.originZ = chunkZ * 16;
            this.minY = minY;
            this.world = world;
        }

        Material materialOfId(int id) {
            Material cached = materialCache.get(id);
            if (cached != null) {
                return cached;
            }
            Material material = registry.materialOf(id);
            if (material != null) {
                materialCache.put(id, material);
            }
            return material;
        }

        Section sectionOf(int y) {
            int index = (y - minY) / 16;
            if (index < 0 || index >= sections.size()) {
                return null;
            }
            return sections.get(index);
        }

        /**
         * Returns the material of an absolute position. Positions outside of
         * the chunk are read from the world.
         */
        Material materialAt(BlockPos pos) {
            if (isInsideChunk(pos)) {
                Section section = sectionOf(pos.y());
                if (section != null) {
                    int index = section.indexOf(pos.x() - originX, pos.y() - section.baseY,
                            pos.z() - originZ);
                    if (index >= 0) {
                        return materialOfId(section.blockStates.valueAt(index));
                    }
                }
                return Material.STONE;
            }
            if (!world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)) {
                return Material.STONE;
            }
            return world.getBlockAt(pos.x(), pos.y(), pos.z()).getType();
        }

        private boolean isInsideChunk(BlockPos pos) {
            return pos.x() >= originX && pos.x() < originX + 16
                    && pos.z() >= originZ && pos.z() < originZ + 16
                    && pos.y() >= minY && pos.y() < minY + sections.size() * 16;
        }

        /**
         * Checks whether the given position is completely surrounded by solid
         * blocks.
         */
        boolean isBuried(BlockPos pos) {
            for (int[] offset : OFFSETS) {
                BlockPos neighbour = pos.offset(offset[0], offset[1], offset[2]);
                Material material = materialAt(neighbour);
                if (material == null || isTransparent(material)) {
                    return false;
                }
            }
            return true;
        }

        Material camouflageAt(BlockPos pos, WorldSettings settings) {
            return camouflageFor(settings, world.getEnvironment(), pos.y());
        }
    }

    /**
     * Determines the camouflage material for an ore.
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
            return settings.netherPalette().isEmpty()
                    ? settings.overworldPalette()
                    : settings.netherPalette();
        }
        if (environment == World.Environment.THE_END) {
            return settings.endPalette().isEmpty()
                    ? settings.overworldPalette()
                    : settings.endPalette();
        }
        if (y < 0) {
            return settings.deepslatePalette().isEmpty()
                    ? settings.overworldPalette()
                    : settings.deepslatePalette();
        }
        return settings.overworldPalette();
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
