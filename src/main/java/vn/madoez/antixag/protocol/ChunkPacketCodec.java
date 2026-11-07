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

import com.comphenix.protocol.events.PacketContainer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds the raw section buffer inside the chunk packet.
 *
 * <p>Since Minecraft 1.20.2 the chunk packet no longer uses the old
 * {@code WrappedLevelChunkData} structure. Instead it carries the sections
 * as a plain byte array. This class locates that array by reflection so
 * that the plugin keeps working on every modern server version without
 * having to ship version specific code.</p>
 */
public final class ChunkPacketCodec {

    private ChunkPacketCodec() {
    }

    /**
     * Locates the byte array that holds the chunk sections.
     *
     * @param packet the chunk packet
     * @return a handle that allows reading and writing the buffer
     */
    public static ChunkBuffer locate(PacketContainer packet) {
        Object handle = packet.getHandle();
        if (handle == null) {
            return null;
        }

        List<Field> candidates = new ArrayList<>();
        for (Field field : handle.getClass().getDeclaredFields()) {
            if (field.getType() != byte[].class) {
                continue;
            }
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            candidates.add(field);
        }

        // A field that is named like the chunk data is preferred.
        for (Field field : candidates) {
            String name = field.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("chunk") || name.contains("section") || name.contains("data")) {
                field.setAccessible(true);
                Object value = readField(field, handle);
                if (value instanceof byte[] && ((byte[]) value).length > 0) {
                    return new ChunkBuffer(field, handle, (byte[]) value);
                }
            }
        }

        // Otherwise fall back to the first array that looks like chunk data.
        for (Field field : candidates) {
            field.setAccessible(true);
            Object value = readField(field, handle);
            if (!(value instanceof byte[])) {
                continue;
            }
            byte[] bytes = (byte[]) value;
            if (bytes.length == 0 || !looksLikeChunkData(bytes)) {
                continue;
            }
            return new ChunkBuffer(field, handle, bytes);
        }

        return null;
    }

    private static Object readField(Field field, Object handle) {
        try {
            return field.get(handle);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    /**
     * Performs a cheap sanity check on a byte array.
     *
     * <p>The first section of a chunk starts with the number of non air
     * blocks as a short. That value is always positive and never larger than
     * 4096, which makes it a good signature.</p>
     */
    private static boolean looksLikeChunkData(byte[] bytes) {
        if (bytes.length < 4) {
            return false;
        }
        int count = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
        return count > 0 && count <= 4096;
    }

    /**
     * A writable view on the section buffer of a chunk packet.
     */
    public static final class ChunkBuffer {

        private final Field field;
        private final Object handle;
        private final byte[] original;

        ChunkBuffer(Field field, Object handle, byte[] original) {
            this.field = field;
            this.handle = handle;
            this.original = original;
        }

        /**
         * Returns the current content of the buffer.
         *
         * @return the raw section bytes
         */
        public byte[] read() {
            Object value = readField(field, handle);
            return value instanceof byte[] ? (byte[]) value : original;
        }

        /**
         * Writes a new buffer into the packet.
         *
         * @param bytes the new content
         * @return {@code true} when the value could be written
         */
        public boolean write(byte[] bytes) {
            try {
                field.set(handle, bytes);
                return true;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return false;
            }
        }
    }
}
