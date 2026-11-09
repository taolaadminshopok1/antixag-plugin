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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the wire format of the paletted container.
 */
class PalettedContainerTest {

    /**
     * Builds a container that holds the given values.
     *
     * @param values the values of the container
     * @param bits   the amount of bits per entry
     * @return the encoded bytes
     */
    private static byte[] encode(int[] values, int bits) {
        ByteCursor cursor = new ByteCursor(new byte[8192]);
        cursor.writeUnsignedByte(bits);
        // A container with a single palette entry per value.
        java.util.List<Integer> palette = new java.util.ArrayList<>();
        for (int value : values) {
            if (!palette.contains(value)) {
                palette.add(value);
            }
        }
        cursor.writeVarInt(palette.size());
        for (int value : palette) {
            cursor.writeVarInt(value);
        }
        long[] storage = new long[(values.length + 64 / bits - 1) / (64 / bits)];
        int valuesPerLong = 64 / bits;
        for (int i = 0; i < values.length; i++) {
            int longIndex = i / valuesPerLong;
            int bitOffset = (i - longIndex * valuesPerLong) * bits;
            storage[longIndex] |= ((long) palette.indexOf(values[i]) & ((1L << bits) - 1L)) << bitOffset;
        }
        cursor.writeVarInt(storage.length);
        for (long value : storage) {
            cursor.writeLong(value);
        }
        byte[] result = new byte[cursor.position()];
        System.arraycopy(cursor.data(), 0, result, 0, result.length);
        return result;
    }

    @Test
    void decodesALocalPalette() {
        int[] values = new int[4096];
        for (int i = 0; i < values.length; i++) {
            values[i] = i % 5;
        }
        byte[] encoded = encode(values, 4);

        ByteCursor cursor = new ByteCursor(encoded);
        PalettedContainer container = PalettedContainer.decodeBlockStates(cursor);

        assertEquals(4, container.bits());
        assertEquals(5, container.palette().length);
        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], container.valueAt(i), "entry " + i);
        }
    }

    @Test
    void rebuildKeepsEveryValue() {
        int[] values = new int[4096];
        for (int i = 0; i < values.length; i++) {
            values[i] = i % 7;
        }
        byte[] encoded = encode(values, 4);

        ByteCursor cursor = new ByteCursor(encoded);
        PalettedContainer container = PalettedContainer.decodeBlockStates(cursor);

        // Replace one entry with a value that is not part of the palette yet.
        container.replaceValue(0, 999);

        PalettedContainer rebuilt = container.rebuild(PalettedContainer.MIN_BLOCK_STATE_BITS);
        assertEquals(999, rebuilt.valueAt(0));
        assertEquals(1, rebuilt.valueAt(1));

        // The rebuilt container has to survive another encode/decode cycle.
        byte[] reEncoded = rebuilt.encode();
        ByteCursor reCursor = new ByteCursor(reEncoded);
        PalettedContainer decoded = PalettedContainer.decodeBlockStates(reCursor);
        assertEquals(999, decoded.valueAt(0));
        for (int i = 1; i < values.length; i++) {
            assertEquals(values[i], decoded.valueAt(i), "entry " + i);
        }
    }

    @Test
    void singleValueContainerRoundTrips() {
        ByteCursor cursor = new ByteCursor(new byte[16]);
        cursor.writeUnsignedByte(0);
        cursor.writeVarInt(42);
        cursor.writeVarInt(0);
        byte[] encoded = new byte[cursor.position()];
        System.arraycopy(cursor.data(), 0, encoded, 0, encoded.length);

        ByteCursor readCursor = new ByteCursor(encoded);
        PalettedContainer container = PalettedContainer.decodeBlockStates(readCursor);
        assertEquals(0, container.bits());
        assertEquals(42, container.valueAt(0));
        assertEquals(42, container.valueAt(4095));
    }

    @Test
    void biomeContainerIsCopiedVerbatim() {
        // A biome container with three palette entries and one long.
        ByteCursor cursor = new ByteCursor(new byte[64]);
        cursor.writeUnsignedByte(2);
        cursor.writeVarInt(3);
        cursor.writeVarInt(1);
        cursor.writeVarInt(2);
        cursor.writeVarInt(3);
        cursor.writeVarInt(1);
        cursor.writeLong(0x0000000000000001L);
        byte[] encoded = new byte[cursor.position()];
        System.arraycopy(cursor.data(), 0, encoded, 0, encoded.length);

        ByteCursor readCursor = new ByteCursor(encoded);
        byte[] raw = PalettedContainer.readRawBiomes(readCursor);

        assertArrayEquals(encoded, raw);
        assertEquals(encoded.length, readCursor.position());
    }

    @Test
    void cursorGrowsWhenItIsNeeded() {
        // A write that is far beyond the initial capacity has to work.
        ByteCursor cursor = new ByteCursor(new byte[4]);
        for (int i = 0; i < 1000; i++) {
            cursor.writeVarInt(i);
        }
        assertTrue(cursor.position() > 4);
        assertTrue(cursor.data().length >= cursor.position());
    }

    @Test
    void aTooLargePaletteIsRejected() {
        // Nine bits are more than the vanilla client accepts for a section.
        byte[] encoded = new byte[]{
                9,        // the amount of bits
                2,        // the palette size
                1,        // the first palette entry
                2,        // the second palette entry
                0         // an empty storage
        };

        ByteCursor cursor = new ByteCursor(encoded);
        assertThrows(IllegalStateException.class,
                () -> PalettedContainer.decodeBlockStates(cursor),
                "A palette of nine bits has to be rejected");
    }

    @Test
    void anInvalidPaletteSizeIsRejected() {
        // The palette claims to hold five entries but only two bits are used,
        // so the values 0 to 3 would be the only possible ones.
        byte[] encoded = new byte[]{
                2,        // the amount of bits
                5,        // the palette size, far too large for two bits
                1,
                2,
                0
        };

        ByteCursor cursor = new ByteCursor(encoded);
        assertThrows(IllegalStateException.class,
                () -> PalettedContainer.decodeBlockStates(cursor),
                "A palette size that does not fit into the bits has to be rejected");
    }

    @Test
    void aTruncatedContainerIsRejected() {
        // The container promises four palette entries but the buffer ends
        // after two of them. This has to fail with a controlled exception.
        byte[] encoded = new byte[]{
                2,        // the amount of bits
                4,        // the palette size
                1,        // the first palette entry
                2         // the second palette entry, and then nothing
        };

        ByteCursor cursor = new ByteCursor(encoded);
        assertThrows(IllegalStateException.class,
                () -> PalettedContainer.decodeBlockStates(cursor),
                "A truncated container has to be rejected");
    }
}