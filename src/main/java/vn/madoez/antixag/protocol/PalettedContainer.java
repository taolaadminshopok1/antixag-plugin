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

import java.util.Arrays;

/**
 * A decoded paletted container.
 *
 * <p>This mirrors {@code net.minecraft.world.level.chunk.PalettedContainer}
 * of the vanilla server. A container stores a list of entries. Every entry is
 * either</p>
 * <ul>
 *   <li>a single value (the whole container holds the same value),</li>
 *   <li>an index into a local palette that is transmitted with the
 *       container, or</li>
 *   <li>a global registry id (the global container variant).</li>
 * </ul>
 *
 * <p>The block state container of a chunk section holds 4096 entries, the
 * biome container holds 64 entries.</p>
 *
 * <h2>Wire format</h2>
 * <pre>
 *   bits == 0    : byte(0)  + VarInt(singleValue) + VarInt(0)
 *   bits == 1..4 : byte(4)  + VarInt(paletteSize) + VarInt[paletteSize] + VarInt(longCount) + long[longCount]
 *   bits == 5..8 : byte(bits)+ VarInt(paletteSize) + VarInt[paletteSize] + VarInt(longCount) + long[longCount]
 *   bits &gt;= 9    : byte(ceilLog2(registrySize)) + VarInt(longCount) + long[longCount]
 * </pre>
 *
 * <p>The first byte always carries the amount of bits that are used per
 * entry. The vanilla server never uses fewer than four bits for the block
 * state container of a section, even when the palette would fit into one or
 * two bits. The biomes container keeps the transmitted amount of bits.</p>
 */
public final class PalettedContainer {

    /** Number of entries of a block state container. */
    public static final int BLOCK_STATE_ENTRIES = 4096;

    /** Number of entries of a biome container. */
    public static final int BIOME_ENTRIES = 64;

    /** Largest amount of bits a local block state palette may use. */
    public static final int MAX_LOCAL_BLOCK_STATE_BITS = 8;

    /** Largest amount of bits a local biome palette may use. */
    public static final int MAX_LOCAL_BIOME_BITS = 3;

    /** Amount of bits that the vanilla server uses for a small block palette. */
    public static final int MIN_BLOCK_STATE_BITS = 4;

    private final int bits;
    private final boolean global;
    private int[] palette;
    private final int[] entries;

    private PalettedContainer(int bits, boolean global, int[] palette, int[] entries) {
        this.bits = bits;
        this.global = global;
        this.palette = palette;
        this.entries = entries;
    }

    /**
     * Decodes a block state container.
     *
     * @param cursor the cursor to read from
     * @return the decoded container
     */
    public static PalettedContainer decodeBlockStates(ByteCursor cursor) {
        return decode(cursor, BLOCK_STATE_ENTRIES, true);
    }

    /**
     * Decodes a biome container.
     *
     * @param cursor the cursor to read from
     * @return the decoded container
     */
    public static PalettedContainer decodeBiomes(ByteCursor cursor) {
        return decode(cursor, BIOME_ENTRIES, false);
    }

    private static PalettedContainer decode(ByteCursor cursor,
                                            int entryCount,
                                            boolean blockStates) {
        int bits = cursor.readUnsignedByte();

        if (bits == 0) {
            // Single value palette. The value is stored directly, the storage
            // is an empty long array.
            int value = cursor.readVarInt();
            cursor.readVarInt();
            int[] single = new int[entryCount];
            // Every entry references palette index zero.
            Arrays.fill(single, 0);
            return new PalettedContainer(0, false, new int[]{value}, single);
        }

        int maxLocalBits = blockStates ? MAX_LOCAL_BLOCK_STATE_BITS : MAX_LOCAL_BIOME_BITS;
        if (bits > maxLocalBits) {
            // Global palette. The bits encode the size of the global registry
            // and the storage holds the registry ids themselves.
            long[] storage = cursor.readLongArray();
            int[] decodedEntries = unpack(storage, entryCount, bits);
            return new PalettedContainer(bits, true, new int[0], decodedEntries);
        }

        // Local palette. The vanilla server forces the storage of a block
        // state container to four bits when the palette is small.
        int storageBits = blockStates ? Math.max(bits, MIN_BLOCK_STATE_BITS) : bits;
        int paletteSize = cursor.readVarInt();
        int[] decodedPalette = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            decodedPalette[i] = cursor.readVarInt();
        }
        long[] storage = cursor.readLongArray();
        int[] decodedEntries = unpack(storage, entryCount, storageBits);
        return new PalettedContainer(storageBits, false, decodedPalette, decodedEntries);
    }

    private static int[] unpack(long[] data, int entryCount, int bits) {
        int[] result = new int[entryCount];
        if (bits <= 0 || data.length == 0) {
            return result;
        }
        long mask = (1L << bits) - 1L;
        int valuesPerLong = 64 / bits;
        for (int i = 0; i < entryCount; i++) {
            int longIndex = i / valuesPerLong;
            if (longIndex >= data.length) {
                break;
            }
            int bitOffset = (i - longIndex * valuesPerLong) * bits;
            result[i] = (int) ((data[longIndex] >>> bitOffset) & mask);
        }
        return result;
    }

    /**
     * Returns the amount of bits that are used per entry.
     *
     * @return the bits per entry
     */
    public int bits() {
        return bits;
    }

    /**
     * Checks whether the container uses the global registry.
     *
     * @return {@code true} for the global variant
     */
    public boolean global() {
        return global;
    }

    /**
     * Returns the local palette of the container.
     *
     * @return the palette entries, empty for the global variant
     */
    public int[] palette() {
        return palette;
    }

    /**
     * Returns the decoded entries of the container.
     *
     * <p>For a local container the values are indices into the palette, for
     * the global variant they are the registry ids themselves.</p>
     *
     * @return the entries
     */
    public int[] entries() {
        return entries;
    }

    /**
     * Returns the block state id that is stored at the given position.
     *
     * @param index the position inside the container
     * @return the registry id of the block state
     */
    public int valueAt(int index) {
        int entry = entries[index];
        if (global) {
            return entry;
        }
        if (entry < 0 || entry >= palette.length) {
            return -1;
        }
        return palette[entry];
    }

    /**
     * Replaces the block state at the given position.
     *
     * <p>For a local container the palette is extended automatically when the
     * value is not part of the palette yet.</p>
     *
     * @param index the position inside the container
     * @param value the new global registry id
     */
    public void replaceValue(int index, int value) {
        if (global) {
            entries[index] = value;
            return;
        }
        int paletteIndex = indexOf(value);
        if (paletteIndex < 0) {
            paletteIndex = palette.length;
            palette = Arrays.copyOf(palette, palette.length + 1);
            palette[paletteIndex] = value;
        }
        entries[index] = paletteIndex;
    }

    private int indexOf(int value) {
        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Rebuilds the container so that it can be written back to the wire.
     *
     * <p>The method selects the most compact variant that is able to hold all
     * values of the container while staying inside the limits that the
     * vanilla client accepts.</p>
     *
     * @param globalBits   the amount of bits of the global variant
     * @param maxLocalBits the largest amount of bits a local palette may use
     * @param blockStates  {@code true} for a block state container
     * @return the rebuilt container
     */
    public PalettedContainer rebuild(int globalBits, int maxLocalBits, boolean blockStates) {
        int[] values = new int[entries.length];
        for (int i = 0; i < entries.length; i++) {
            values[i] = valueAt(i);
        }

        IntSet unique = new IntSet(values);

        if (unique.size() == 1) {
            int only = unique.valueAt(0);
            int[] single = new int[entries.length];
            Arrays.fill(single, 0);
            return new PalettedContainer(0, false, new int[]{only}, single);
        }

        int localBits = BlockStateRegistry.ceilLog2(unique.size());
        if (localBits > maxLocalBits) {
            // Too many distinct values for a local palette, fall back to the
            // global variant.
            return new PalettedContainer(globalBits, true, new int[0], values);
        }

        int effectiveBits = blockStates ? Math.max(localBits, MIN_BLOCK_STATE_BITS) : localBits;
        int[] newPalette = unique.toArray();
        int[] newEntries = new int[entries.length];
        for (int i = 0; i < values.length; i++) {
            newEntries[i] = unique.indexOf(values[i]);
        }
        return new PalettedContainer(effectiveBits, false, newPalette, newEntries);
    }

    /**
     * Encodes the container into the wire format.
     *
     * @return the encoded bytes
     */
    public byte[] encode() {
        if (bits == 0) {
            ByteCursor cursor = new ByteCursor(new byte[16]);
            cursor.writeUnsignedByte(0);
            cursor.writeVarInt(palette[0]);
            cursor.writeVarInt(0);
            byte[] result = new byte[cursor.position()];
            System.arraycopy(cursor.data(), 0, result, 0, result.length);
            return result;
        }

        long[] storage = pack(entries, bits);
        int size = 32 + (global ? 0 : 1 + palette.length * 5) + storage.length * 8;
        ByteCursor cursor = new ByteCursor(new byte[size]);
        cursor.writeUnsignedByte(bits);
        if (!global) {
            cursor.writeVarInt(palette.length);
            for (int value : palette) {
                cursor.writeVarInt(value);
            }
        }
        cursor.writeVarInt(storage.length);
        for (long value : storage) {
            cursor.writeLong(value);
        }
        byte[] result = new byte[cursor.position()];
        System.arraycopy(cursor.data(), 0, result, 0, result.length);
        return result;
    }

    private static long[] pack(int[] values, int bits) {
        if (bits <= 0) {
            return new long[0];
        }
        int valuesPerLong = 64 / bits;
        long mask = (1L << bits) - 1L;
        long[] storage = new long[(values.length + valuesPerLong - 1) / valuesPerLong];
        for (int i = 0; i < values.length; i++) {
            int longIndex = i / valuesPerLong;
            int bitOffset = (i - longIndex * valuesPerLong) * bits;
            storage[longIndex] |= ((long) values[i] & mask) << bitOffset;
        }
        return storage;
    }

    /**
     * A tiny helper that keeps the distinct values of a container while
     * remembering their position.
     */
    private static final class IntSet {

        private int[] values;
        private int size;

        IntSet(int[] source) {
            this.values = new int[Math.max(16, source.length)];
            for (int value : source) {
                add(value);
            }
        }

        boolean add(int value) {
            for (int i = 0; i < size; i++) {
                if (values[i] == value) {
                    return false;
                }
            }
            if (size == values.length) {
                int[] grown = new int[values.length * 2];
                System.arraycopy(values, 0, grown, 0, size);
                values = grown;
            }
            values[size++] = value;
            return true;
        }

        int size() {
            return size;
        }

        int valueAt(int index) {
            return values[index];
        }

        int indexOf(int value) {
            for (int i = 0; i < size; i++) {
                if (values[i] == value) {
                    return i;
                }
            }
            return -1;
        }

        int[] toArray() {
            int[] result = new int[size];
            System.arraycopy(values, 0, result, 0, size);
            return result;
        }
    }
}
