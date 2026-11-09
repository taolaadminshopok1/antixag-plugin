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

import vn.madoez.antixag.config.WorldSettings;

import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.Random;

/**
 * The single place that decides which block an ore is disguised with.
 *
 * <p>Earlier versions of AntiXag carried three copies of this logic. They
 * drifted apart, which made the same ore look different depending on the
 * code path that processed it. Every caller now goes through this class.</p>
 */
public final class Camouflage {

    private static final Random RANDOM = new Random();

    private Camouflage() {
    }

    /**
     * Returns the camouflage material for an ore.
     *
     * <p>The palette is picked based on the depth of the ore so that a diamond
     * vein at y = 12 looks like deepslate while a diamond vein at y = 60 looks
     * like stone. When the randomisation is enabled the palette is sampled for
     * every ore, otherwise the first entry is used.</p>
     *
     * @param settings    the world settings
     * @param environment the environment of the world
     * @param y           the y level of the ore
     * @return the material the ore should be disguised with
     */
    public static Material forOre(WorldSettings settings, World.Environment environment, int y) {
        return forOre(settings, environment, y, RANDOM);
    }

    /**
     * Returns the camouflage material for an ore.
     *
     * @param settings    the world settings
     * @param environment the environment of the world
     * @param y           the y level of the ore
     * @param random      the source of randomness
     * @return the material the ore should be disguised with
     */
    public static Material forOre(WorldSettings settings,
                                  World.Environment environment,
                                  int y,
                                  Random random) {
        List<Material> palette = paletteFor(settings, environment, y);
        if (palette.isEmpty()) {
            return Material.STONE;
        }
        if (!settings.randomizeCamouflage() || palette.size() == 1) {
            return palette.get(0);
        }
        return palette.get(random.nextInt(palette.size()));
    }

    /**
     * Returns the palette that matches the given environment and height.
     *
     * @param settings    the world settings
     * @param environment the environment of the world
     * @param y           the y level of the ore
     * @return the matching palette, never {@code null}
     */
    public static List<Material> paletteFor(WorldSettings settings,
                                            World.Environment environment,
                                            int y) {
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
}