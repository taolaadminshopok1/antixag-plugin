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
package vn.madoez.antixag.util;

/**
 * Immutable integer block coordinate triple.
 *
 * <p>Used as the key of every spatial lookup table in AntiXag. Implements
 * {@link #hashCode()} and {@link #equals(Object)} so it can be used inside
 * hash based collections without allocating boxed values.</p>
 */
public final class BlockPos {

    private final int x;
    private final int y;
    private final int z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    /**
     * Returns a copy of this position offset by the given deltas.
     *
     * @param dx x offset
     * @param dy y offset
     * @param dz z offset
     * @return the offset position
     */
    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    /**
     * Checks whether this position is within the inclusive bounds.
     *
     * @param minX minimum x
     * @param minY minimum y
     * @param minZ minimum z
     * @param maxX maximum x
     * @param maxY maximum y
     * @param maxZ maximum z
     * @return {@code true} when the position is inside the box
     */
    public boolean isWithin(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockPos)) {
            return false;
        }
        BlockPos that = (BlockPos) other;
        return x == that.x && y == that.y && z == that.z;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        result = 31 * result + z;
        return result;
    }

    @Override
    public String toString() {
        return "BlockPos{x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
