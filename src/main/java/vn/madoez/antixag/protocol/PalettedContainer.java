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
 *   <li>a single value (the whole container holds the same value), or</li>
 *   <li>an index into a local palette that is transmitted with the
 *       container.</li>
 * </ul>
 *
 * <p>Since Minecraft 1.13 the chunk sections no longer use the "global
 * palette" variant: every section is transmitted with a <em>local</em>
 * palette. Only the amount of bits per entry varies. Decoding a global
 * variant would misread the palette size and therefore corrupt the whole
 * packet, so this implementation deliberately supports the local format
 * only.</p>
 *
 * <h2>Wire format</h2>
 * <pre>
 *   bits == 0    : byte(0)  + VarInt(singleValue) + VarInt(0)
 *   bits == 1..8 : byte(bits) + VarInt(paletteSize) + VarInt[paletteSize]
 *                        + VarInt(longCount) + long[longCount]
 * </pre>
 *
 * <p>The vanilla server uses four bits for the block state container of a
 * section, even when the palette would fit into one or two bits. The biome
 * container uses as many bits as its palette requires.</p>
 */
public final class PalettedContainer {

    /** Number of entries of a block state container. */
    public static final int BLOCK_STATE_ENTRIES = 4096;

    /** Number of entries of a biome container. */
    public static final int BIOME_ENTRIES = 64;

    /** Amount of bits that the vanilla server uses for a small block palette. */
    public static final int MIN_BLOCK_STATE_BITS = 4;

    /**
     * Largest amount of bits a local palette may use.
     *
     * <p>A palette that needs more bits than this cannot be represented
     * without falling back to a format that the vanilla client does not
     * understand, so the rewrite is aborted instead.</p>
     */
    public static final int MAX_PALETTE_BITS = 8;

    private final int bits;
    private int[] palette;
    private final int[] entries;

    private PalettedContainer(int bits, int[] palette, int[] entries) {
        this.bits = bits;
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
        return decode(cursor, BLOCK_STATE_ENTRIES, MAX_PALETTE_BITS);
    }

    /**
     * Decodes a container with the given amount of entries.
     *
     * @param cursor     the cursor to read from
     * @param entryCount the amount of entries of the container
     * @return the decoded container
     */
    public static PalettedContainer decode(ByteCursor cursor, int entryCount) {
        return decode(cursor, entryCount, MAX_PALETTE_BITS);
    }

    /**
     * Decodes a container with the given amount of entries.
     *
     * @param cursor     the cursor to read from
     * @param entryCount the amount of entries of the container
     * @param maxBits    the largest amount of bits that the container may use
     * @return the decoded container
     */
    public static PalettedContainer decode(ByteCursor cursor, int entryCount, int maxBits) {
        int bits = cursor.readUnsignedByte();

        if (bits == 0) {
            // Single value variant. The value is stored directly and the
            // storage is an empty long array.
            int value = cursor.readVarInt();
            cursor.readVarInt();
            int[] single = new int[entryCount];
            // Every entry references palette index zero.
            return new PalettedContainer(0, new int[]{value}, single);
        }

        if (bits > maxBits) {
            throw new IllegalStateException("Unsupported amount of palette bits: " + bits
                    + ", at most " + maxBits + " are allowed");
        }

        int paletteSize = cursor.readVarInt();
        if (paletteSize < 0 || paletteSize > (1 << bits)) {
            throw new IllegalStateException("Invalid palette size " + paletteSize
                    + " for " + bits + " bits");
        }
        int[] decodedPalette = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            decodedPalette[i] = cursor.readVarInt();
        }
        long[] storage = cursor.readLongArray();
        int[] decodedEntries = unpack(storage, entryCount, bits);
        return new PalettedContainer(bits, decodedPalette, decodedEntries);
    }

    /**
     * Reads the raw bytes of a biome container without decoding it.
     *
     * <p>The biome container is never modified by AntiXag. Decoding and
     * re-encoding it would risk changing the amount of bits and therefore
     * corrupting the biome data of the chunk, so the original bytes are kept
     * and copied back verbatim.</p>
     *
     * @param cursor the cursor to read from
     * @return the original bytes of the container
     */
    public static byte[] readRawBiomes(ByteCursor cursor) {
        int start = cursor.position();
        int bits = cursor.readUnsignedByte();
        if (bits == 0) {
            cursor.readVarInt();
            cursor.readVarInt();
        } else {
            int paletteSize = cursor.readVarInt();
            for (int i = 0; i < paletteSize; i++) {
                cursor.readVarInt();
            }
            int longCount = cursor.readVarInt();
            cursor.skipBytes(longCount * 8);
        }
        int end = cursor.position();
        byte[] raw = new byte[end - start];
        System.arraycopy(cursor.data(), start, raw, 0, raw.length);
        return raw;
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
     * Returns the local palette of the container.
     *
     * @return the palette entries
     */
    public int[] palette() {
        return palette;
    }

    /**
     * Returns the decoded entries of the container.
     *
     * <p>Every entry is an index into the local palette.</p>
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
     * @param minBits the smallest amount of bits the container may use
     * @return the rebuilt container
     * @throws IllegalStateException when the palette does not fit into the
     *                               supported amount of bits
     */
    public PalettedContainer rebuild(int minBits) {
        int[] values = new int[entries.length];
        for (int i = 0; i < entries.length; i++) {
            values[i] = valueAt(i);
        }

        IntSet unique = new IntSet(values);

        if (unique.size() == 1) {
            int only = unique.valueAt(0);
            int[] single = new int[entries.length];
            return new PalettedContainer(0, new int[]{only}, single);
        }

        int localBits = Math.max(minBits, ceilLog2(unique.size()));
        if (localBits > MAX_PALETTE_BITS) {
            throw new IllegalStateException("The palette needs " + localBits
                    + " bits, but only " + MAX_PALETTE_BITS + " bits are supported");
        }

        int[] newPalette = unique.toArray();
        int[] newEntries = new int[entries.length];
        for (int i = 0; i < values.length; i++) {
            newEntries[i] = unique.indexOf(values[i]);
        }
        return new PalettedContainer(localBits, newPalette, newEntries);
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
        int size = 32 + 1 + palette.length * 5 + storage.length * 8;
        ByteCursor cursor = new ByteCursor(new byte[size]);
        cursor.writeUnsignedByte(bits);
        cursor.writeVarInt(palette.length);
        for (int value : palette) {
            cursor.writeVarInt(value);
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
     * Computes the ceiling of the binary logarithm.
     *
     * @param value the value to inspect
     * @return {@code ceil(log2(value))}, at least 1
     */
    public static int ceilLog2(int value) {
        if (value <= 1) {
            return 1;
        }
        return 32 - Integer.numberOfLeadingZeros(value - 1);
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