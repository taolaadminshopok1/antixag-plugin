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

/**
 * A cursor over a byte array that reads and writes the primitive types of the
 * Minecraft network protocol.
 *
 * <p>All methods operate on the same underlying array. The cursor keeps track
 * of the current position, which makes it possible to decode a packet
 * section by section and re-encode it afterwards.</p>
 */
public final class ByteCursor {

    private final byte[] data;
    private int position;

    public ByteCursor(byte[] data) {
        this(data, 0);
    }

    public ByteCursor(byte[] data, int position) {
        this.data = data;
        this.position = position;
    }

    /**
     * Returns the current read/write position.
     *
     * @return the position inside the backing array
     */
    public int position() {
        return position;
    }

    /**
     * Sets the current read/write position.
     *
     * @param position the new position
     */
    public void position(int position) {
        this.position = position;
    }

    /**
     * Returns the number of bytes that are still available.
     *
     * @return the remaining byte count
     */
    public int remaining() {
        return data.length - position;
    }

    /**
     * Checks whether at least one byte is still available.
     *
     * @return {@code true} when more bytes can be read
     */
    public boolean hasRemaining() {
        return position < data.length;
    }

    /**
     * Reads a single unsigned byte.
     *
     * @return the value between 0 and 255
     */
    public int readUnsignedByte() {
        return data[position++] & 0xFF;
    }

    /**
     * Reads a big endian signed short.
     *
     * @return the signed short value
     */
    public short readShort() {
        int value = ((data[position] & 0xFF) << 8) | (data[position + 1] & 0xFF);
        position += 2;
        return (short) value;
    }

    /**
     * Reads a big endian long.
     *
     * @return the long value
     */
    public long readLong() {
        long value = 0L;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (data[position++] & 0xFFL);
        }
        return value;
    }

    /**
     * Returns the backing array of the cursor.
     *
     * @return the raw byte array
     */
    public byte[] data() {
        return data;
    }

    /**
     * Reads a variable length integer.
     *
     * @return the decoded value
     */
    public int readVarInt() {
        int value = 0;
        int shift = 0;
        while (true) {
            if (shift >= 35) {
                throw new IllegalStateException("VarInt is too big");
            }
            int current = data[position++] & 0xFF;
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return value;
            }
            shift += 7;
        }
    }

    /**
     * Reads a length prefixed array of longs.
     *
     * @return the decoded longs
     */
    public long[] readLongArray() {
        int count = readVarInt();
        long[] result = new long[count];
        for (int i = 0; i < count; i++) {
            result[i] = readLong();
        }
        return result;
    }

    /**
     * Skips over the given amount of bytes.
     *
     * @param count the number of bytes to skip
     */
    public void skipBytes(int count) {
        position += count;
    }

    /**
     * Reads the given amount of bytes.
     *
     * @param count the number of bytes to read
     * @return a copy of the read bytes
     */
    public byte[] readBytes(int count) {
        byte[] result = new byte[count];
        System.arraycopy(data, position, result, 0, count);
        position += count;
        return result;
    }

    /**
     * Writes a single unsigned byte.
     *
     * @param value the value to write
     */
    public void writeUnsignedByte(int value) {
        data[position++] = (byte) value;
    }

    /**
     * Writes a big endian signed short.
     *
     * @param value the value to write
     */
    public void writeShort(int value) {
        data[position++] = (byte) (value >>> 8);
        data[position++] = (byte) value;
    }

    /**
     * Writes a big endian long.
     *
     * @param value the value to write
     */
    public void writeLong(long value) {
        for (int i = 7; i >= 0; i--) {
            data[position++] = (byte) (value >>> (i * 8));
        }
    }

    /**
     * Writes a variable length integer.
     *
     * @param value the value to write
     */
    public void writeVarInt(int value) {
        int remaining = value;
        while (true) {
            if ((remaining & ~0x7F) == 0) {
                data[position++] = (byte) remaining;
                return;
            }
            data[position++] = (byte) ((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
    }

    /**
     * Writes a raw byte array.
     *
     * @param bytes the bytes to write
     */
    public void writeBytes(byte[] bytes) {
        System.arraycopy(bytes, 0, data, position, bytes.length);
        position += bytes.length;
    }
}
