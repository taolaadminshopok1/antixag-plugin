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
package vn.madoez.antixag.config;

import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable per-world configuration snapshot.
 */
public final class WorldSettings {

    /** Strategy used to decide which ores are concealed from clients. */
    public enum HideMode {
        /**
         * Only ores that are fully buried (no open neighbour) are concealed.
         * Ores that are already exposed inside a cave stay visible, which keeps
         * the world looking natural.
         */
        GRADUALLY,
        /**
         * If a single member of an ore vein is buried, the entire vein is
         * concealed. Prevents "half a vein" from leaking the position of the
         * remaining ores.
         */
        FULL_VEIN,
        /**
         * Every configured ore is concealed, even when naturally exposed.
         */
        ALL
    }

    /** Strategy used to decide how many ores are revealed at once. */
    public enum RevealMode {
        /** Only the ore that became exposed is revealed. */
        SINGLE,
        /** The whole vein is revealed as soon as one member becomes exposed. */
        VEIN
    }

    private final boolean enabled;
    private final Set<Material> hiddenBlocks;
    private final HideMode hideMode;
    private final RevealMode revealMode;
    private final boolean randomizeCamouflage;
    private final boolean hidePlacedBlocks;
    private final boolean restoreOnUnload;
    private final List<Material> overworldPalette;
    private final List<Material> deepslatePalette;
    private final List<Material> netherPalette;
    private final List<Material> endPalette;

    WorldSettings(boolean enabled,
                  Set<Material> hiddenBlocks,
                  HideMode hideMode,
                  RevealMode revealMode,
                  boolean randomizeCamouflage,
                  boolean hidePlacedBlocks,
                  boolean restoreOnUnload,
                  List<Material> overworldPalette,
                  List<Material> deepslatePalette,
                  List<Material> netherPalette,
                  List<Material> endPalette) {
        this.enabled = enabled;
        this.hiddenBlocks = hiddenBlocks.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(hiddenBlocks));
        this.hideMode = hideMode;
        this.revealMode = revealMode;
        this.randomizeCamouflage = randomizeCamouflage;
        this.hidePlacedBlocks = hidePlacedBlocks;
        this.restoreOnUnload = restoreOnUnload;
        this.overworldPalette = List.copyOf(overworldPalette);
        this.deepslatePalette = List.copyOf(deepslatePalette);
        this.netherPalette = List.copyOf(netherPalette);
        this.endPalette = List.copyOf(endPalette);
    }

    public static WorldSettings defaults() {
        return new WorldSettings(true, EnumSet.noneOf(Material.class), HideMode.GRADUALLY,
                RevealMode.SINGLE, true, true, true,
                List.of(), List.of(), List.of(), List.of());
    }

    public boolean enabled() {
        return enabled;
    }

    public Set<Material> hiddenBlocks() {
        return hiddenBlocks;
    }

    public HideMode hideMode() {
        return hideMode;
    }

    public RevealMode revealMode() {
        return revealMode;
    }

    public boolean randomizeCamouflage() {
        return randomizeCamouflage;
    }

    public boolean hidePlacedBlocks() {
        return hidePlacedBlocks;
    }

    public boolean restoreOnUnload() {
        return restoreOnUnload;
    }

    public List<Material> overworldPalette() {
        return overworldPalette;
    }

    public List<Material> deepslatePalette() {
        return deepslatePalette;
    }

    public List<Material> netherPalette() {
        return netherPalette;
    }

    public List<Material> endPalette() {
        return endPalette;
    }
}
