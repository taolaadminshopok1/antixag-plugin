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

import vn.madoez.antixag.util.BlockPos;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A connected group of ore blocks of the same type.
 *
 * <p>Veins are the unit of decision for the {@code FULL_VEIN} hide mode and
 * the {@code VEIN} reveal mode. Two ore blocks belong to the same vein when
 * they touch each other on any of the six axis aligned faces.</p>
 */
public final class OreVein {

    private final Material ore;
    private final Set<BlockPos> positions;

    OreVein(Material ore, Set<BlockPos> positions) {
        this.ore = ore;
        this.positions = Collections.unmodifiableSet(new LinkedHashSet<>(positions));
    }

    public Material ore() {
        return ore;
    }

    public Set<BlockPos> positions() {
        return positions;
    }

    public int size() {
        return positions.size();
    }

    /**
     * Checks whether the vein contains the given position.
     *
     * @param pos the position to test
     * @return {@code true} when the position belongs to this vein
     */
    public boolean contains(BlockPos pos) {
        return positions.contains(pos);
    }

    /**
     * Returns the positions as a mutable list.
     *
     * @return a new list holding every position of the vein
     */
    public List<BlockPos> positionList() {
        return new ArrayList<>(positions);
    }
}
