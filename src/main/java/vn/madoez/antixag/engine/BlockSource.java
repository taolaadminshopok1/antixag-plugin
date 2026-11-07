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

import org.bukkit.Material;

/**
 * Read-only view over the blocks of a region.
 *
 * <p>The anti-xray engine never reads the world directly. Instead it asks a
 * {@code BlockSource} for the material of a position. This indirection makes
 * it possible to expose the <em>original</em> material of a block that is
 * currently disguised, and it allows unit testing the algorithm without a
 * running server.</p>
 *
 * <p>All coordinates passed to {@link #materialAt(int, int, int)} are
 * absolute world coordinates.</p>
 */
@FunctionalInterface
public interface BlockSource {

    /**
     * Returns the material at the given absolute world position.
     *
     * @param x absolute x
     * @param y absolute y
     * @param z absolute z
     * @return the material, never {@code null}
     */
    Material materialAt(int x, int y, int z);
}
